-- SPDX-FileCopyrightText: 2026 The Computercraft-Legacy Developers
--
-- SPDX-License-Identifier: MPL-2.0

--[[- Text-to-speech helper for the speaker peripheral ("Option A").

The ComputerCraft speaker can only ingest **raw signed 8-bit PCM** (via
`speaker.playAudio`) or DFPWM (via [`cc.audio.dfpwm`]). Text-to-speech services
return compressed audio (MP3/Opus), which this stack cannot decode in-game.

This module therefore talks to an **external transcoding endpoint** that:

1. accepts the text as an HTTP `GET` query parameter,
2. calls a TTS provider, and
3. returns audio in one of the speaker-ready formats below.

Supported formats (in order of compatibility):
- `pcm_u8`: Unsigned 8-bit PCM (default). Universal support, works with any ffmpeg.
- `pcm_s8`: Signed 8-bit PCM. Works with any ffmpeg.
- `dfpwm`: DFPWM codec. Only if the transcoder's DFPWM encoder is compatible
  with the CC decoder; some ffmpeg builds produce incompatible output, so use
  `pcm_u8` if you see silence with DFPWM.

See `docs/SPEAKER_TTS.md` for the endpoint contract and a reference
implementation.

@module cc.audio.tts
@usage Speak a phrase through the nearest speaker.

    local tts = require "cc.audio.tts"
    tts.endpoint = "http://my-tts.example/say"

    local speaker = peripheral.find("speaker")
    assert(tts.speak(speaker, "Hello from ComputerCraft"))
]]

local expect = require "cc.expect"
local expect = expect.expect

local dfpwm = require "cc.audio.dfpwm"

-- The speaker accepts at most 128*1024 samples per playAudio call. One DFPWM
-- byte expands to 8 PCM samples, so 16*1024 DFPWM bytes decode to exactly the
-- maximum-sized buffer.
local MAX_SAMPLES   = 128 * 1024
local DFPWM_CHUNK   = 16 * 1024
local SUPPORTED     = { dfpwm = true, pcm_u8 = true, pcm_s8 = true }

local tts = {}

--- The default transcoder endpoint. May be overridden per-call via `opts.endpoint`.
-- @field endpoint
tts.endpoint = nil
--- Enable debug prints from the module. When true the module prints progress
--- and error messages to the current computer's stdout to aid debugging.
-- @field debug
tts.debug = false
--- Number of signed-8 PCM samples to send to `speaker.playAudio` at once.
-- If a server or client drops large bursts this can be reduced to avoid
-- packet/line buffer overflow. Defaults to nil (no splitting). Set to e.g.
-- 4096 to send smaller chunks.
-- @field play_chunk_samples
tts.play_chunk_samples = 4096
--- Optional small sleep (seconds) between playAudio() chunks when splitting.
-- Useful to smooth bursts; default nil (no sleep). Typical values: 0.01.
-- @field play_inter_chunk_sleep
tts.play_inter_chunk_sleep = nil

-- ── Pure helpers (no I/O — safe to unit test) ────────────────────────────────

--- Percent-encode a string for use in a URL query (RFC 3986 unreserved set).
-- @tparam string str The value to encode.
-- @treturn string The percent-encoded value.
local function url_encode(str)
    return (str:gsub("[^%w%-_%.~]", function(c)
        return string.format("%%%02X", string.byte(c))
    end))
end

--- Append a `key=value` pair to a URL, choosing `?` or `&` automatically.
local function append_query(url, key, value)
    local sep = url:find("?", 1, true) and "&" or "?"
    return url .. sep .. key .. "=" .. url_encode(value)
end

--- Build the transcoder request URL for some text.
--
-- @tparam string text The text to synthesise.
-- @tparam[opt] table opts Options: `endpoint`, `lang`, `voice`, `format`.
--   - `format`: One of `"pcm_u8"` (default), `"pcm_s8"`, or `"dfpwm"`.
-- @treturn string The fully-formed request URL.
local function build_url(text, opts)
    expect(1, text, "string")
    opts = opts or {}
    expect(2, opts, "table")

    local endpoint = opts.endpoint or tts.endpoint
    if type(endpoint) ~= "string" then
        error("No transcoder endpoint configured (set tts.endpoint or opts.endpoint)", 2)
    end

    local format = opts.format or "pcm_u8"
    if not SUPPORTED[format] then
        error("Unsupported format '" .. tostring(format) .. "'", 2)
    end

    local url = append_query(endpoint, "text", text)
    url = append_query(url, "lang", opts.lang or "en")
    url = append_query(url, "format", format)
    if opts.voice ~= nil then
        expect(2, opts.voice, "string")
        url = append_query(url, "voice", opts.voice)
    end
    return url
end

--- Convert a binary PCM string into a signed-8-bit sample table.
--
-- @tparam string str   Raw PCM bytes.
-- @tparam string format Either `"pcm_u8"` (unsigned 0..255) or `"pcm_s8"`
-- (signed two's-complement).
-- @treturn {number...} Signed 8-bit samples in `[-128, 127]`.
local function pcm_samples(str, format)
    expect(1, str, "string")
    local out = {}
    if format == "pcm_u8" then
        for i = 1, #str do out[i] = string.byte(str, i) - 128 end
    elseif format == "pcm_s8" then
        for i = 1, #str do
            local b = string.byte(str, i)
            out[i] = b < 128 and b or b - 256
        end
    else
        error("Unsupported PCM format '" .. tostring(format) .. "'", 2)
    end
    return out
end

-- ── Streaming (requires `http`, `speaker`, and the event loop) ───────────────

--- Stream an already-open HTTP response handle to a speaker.
--
-- Honours `speaker.playAudio` back-pressure by waiting on `speaker_audio_empty`.
-- Does **not** close the handle — callers own its lifetime.
--
-- @tparam table speaker A wrapped speaker peripheral.
-- @tparam table handle  An open `http` response handle (must support `read`).
-- @tparam[opt] table opts Options: `format`, `volume`, `chunk_size`.
-- @treturn[1] true On success.
-- @treturn[2] nil On failure.
-- @treturn[2] string An error message.
local function stream(speaker, handle, opts)
    expect(1, speaker, "table")
    expect(2, handle, "table")
    opts = opts or {}
    expect(3, opts, "table")

    local format = opts.format or "pcm_u8"
    if not SUPPORTED[format] then
        return nil, "Unsupported format '" .. tostring(format) .. "'"
    end
    local volume = opts.volume

    local read_size, decoder
    if format == "dfpwm" then
        read_size = math.min(opts.chunk_size or DFPWM_CHUNK, DFPWM_CHUNK)
        decoder = dfpwm.make_decoder()
    else
        -- pcm_u8 or pcm_s8: stream in reasonable chunks (not too large)
        read_size = math.min(opts.chunk_size or MAX_SAMPLES, MAX_SAMPLES)
    end

    local total_bytes = 0
    local total_samples = 0
    while true do
        if tts.debug then print("tts: reading chunk (size=", read_size, ")") end
        local chunk = handle.read(read_size)

        if not chunk or #chunk == 0 then
            -- Treat an empty read as EOF. With chunked playAudio this is reliable
            -- and avoids long retry loops that previously masked audio dispatch
            -- pacing issues.
            if tts.debug then
                print(string.format("tts: EOF — total_bytes=%d total_samples=%d (~%.2fs at 48kHz)",
                    total_bytes, total_samples, total_samples / 48000))
            end
            break
        else
            total_bytes = total_bytes + #chunk

            local samples
            if tts.debug then print("tts: got chunk bytes=", #chunk) end
            if format == "dfpwm" then
                samples = decoder({ string.byte(chunk, 1, #chunk) })
            else
                samples = pcm_samples(chunk, format)
            end

            total_samples = total_samples + #samples
            if tts.debug then
                print("tts: decoded samples=", #samples)
                -- compute basic stats (min, max, non-zero count, first samples)
                local mn, mx, nz, sa = 32767, -32768, 0, 0
                local first = {}
                for i = 1, #samples do
                    local v = samples[i]
                    if v < mn then mn = v end
                    if v > mx then mx = v end
                    if v ~= 0 then nz = nz + 1 end
                    sa = sa + math.abs(v)
                    if i <= 8 then first[i] = v end
                end
                local avgabs = (#samples > 0) and (sa / #samples) or 0
                print(string.format("tts: stats min=%d max=%d nonzero=%d avgabs=%.2f first=%s", mn, mx, nz, avgabs, table.concat(first, ",")))
            end
            -- To avoid sending very large bursts that may overflow client buffers
            -- or be dropped by the audio path, split into smaller sub-chunks if
            -- requested by opts.play_chunk_samples or the global tts.play_chunk_samples.
            local play_chunk = opts.play_chunk_samples or tts.play_chunk_samples
            local inter_sleep = opts.play_inter_chunk_sleep or tts.play_inter_chunk_sleep
            if play_chunk and play_chunk > 0 and #samples > play_chunk then
                local i = 1
                while i <= #samples do
                    local to = math.min(i + play_chunk - 1, #samples)
                    -- copy slice [i..to]
                    local slice = {}
                    for j = i, to do slice[#slice + 1] = samples[j] end
                    while not speaker.playAudio(slice, volume) do
                        if tts.debug then print("tts: playAudio back-pressure (slice), waiting for speaker_audio_empty") end
                        os.pullEvent("speaker_audio_empty")
                    end
                    if tts.debug then print("tts: queued slice samples=", #slice) end
                    if inter_sleep and inter_sleep > 0 then os.sleep(inter_sleep) end
                    i = to + 1
                end
            else
                while not speaker.playAudio(samples, volume) do
                    if tts.debug then print("tts: playAudio back-pressure, waiting for speaker_audio_empty") end
                    os.pullEvent("speaker_audio_empty")
                end
                if tts.debug then print("tts: queued samples to speaker") end
            end
        end
    end
    return true
end

--- Synthesise `text` and play it through `speaker`.
--
-- Fetches transcoded audio from the configured endpoint and streams it.
-- Blocks until playback has been fully queued.
--
-- @tparam table speaker A wrapped speaker peripheral (e.g. `peripheral.find("speaker")`).
-- @tparam string text   The text to speak.
-- @tparam[opt] table opts Options: `endpoint`, `lang`, `voice`, `format`,
-- `volume`, `chunk_size`.
--   - `format`: One of `"pcm_u8"` (default), `"pcm_s8"`, or `"dfpwm"`.
-- @treturn[1] true On success.
-- @treturn[2] nil On failure.
-- @treturn[2] string An error message.
local function speak(speaker, text, opts)
    expect(1, speaker, "table")
    expect(2, text, "string")
    opts = opts or {}
    expect(3, opts, "table")

    if not http then error("HTTP API is disabled", 2) end

    local url = build_url(text, opts)
    if tts.debug then print("tts: speak requesting URL=", url) end
    local handle, err = http.get(url, nil, true)
    if not handle then
        local msg = tostring(err or "Could not connect")
        return nil, ("Request to %s failed: %s"):format(url, msg)
    end

    local ok, result = pcall(stream, speaker, handle, opts)
    handle.close()
    if not ok then return nil, result end
    return result
end

--- Play a short test tone through the speaker to verify end-to-end audio.
-- @tparam table speaker The speaker peripheral.
-- @tparam number freq Frequency in Hz (default 440).
-- @tparam number dur  Duration in seconds (default 0.25).
-- @tparam number volume Optional volume (0..3)
local function testTone(speaker, freq, dur, volume)
    expect(1, speaker, "table")
    freq = freq or 440
    dur = dur or 0.25
    volume = volume or 1.0

    local sr = 48000
    local n = math.floor(dur * sr)
    if n <= 0 then return nil, "invalid duration" end
    if n > MAX_SAMPLES then n = MAX_SAMPLES end
    local samples = {}
    for i = 1, n do
        local v = math.sin(2 * math.pi * freq * (i - 1) / sr)
        -- scale to signed 8-bit [-128,127]
        samples[i] = math.floor(v * 60)
    end

    while not speaker.playAudio(samples, volume) do
        os.pullEvent("speaker_audio_empty")
    end
    return true
end

tts.testTone = testTone

tts.url         = build_url
tts.pcm_samples = pcm_samples
tts.stream      = stream
tts.speak       = speak

return tts









