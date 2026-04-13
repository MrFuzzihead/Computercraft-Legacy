-- SPDX-FileCopyrightText: 2021 The CC: Tweaked Developers
--
-- SPDX-License-Identifier: MPL-2.0

--- Provides utilities for working with DFPWM audio files.
--
-- DFPWM (Dynamic Filter Pulse Width Modulation) is an audio codec originally
-- developed by Ben "GreaseMonkey" Russell, and is the audio format used by
-- speakers in ComputerCraft. It can be produced using programs such as
-- [BIMG](https://github.com/SkyTheCodeMaster/bimg/) or [FFmpeg](https://ffmpeg.org).
--
-- ## Encoding and decoding files
--
-- This module exposes two pairs of functions. Each pair provides a stateful
-- encoder/decoder factory and a one-shot convenience wrapper:
--
-- - [`make_encoder`] and [`encode`]: Encode audio into DFPWM.
-- - [`make_decoder`] and [`decode`]: Decode DFPWM into audio.
--
-- ### Converting audio to DFPWM
--
-- PCM data supplied to the encoder is a table of signed 8-bit integers in
-- the range [-128, 127].  DFPWM output is a table of unsigned bytes (each in
-- [0, 255]) where every byte encodes 8 consecutive audio samples.
--
-- @module cc.audio.dfpwm
-- @since 1.100.0

local expect = require "cc.expect"
local expect = expect.expect

-- ── Internal constants ───────────────────────────────────────────────────────

local PREC_SHIFT   = 10
local PREC_CEIL    = 2 ^ PREC_SHIFT  -- 1024
local LPF_STRENGTH = 140

-- ── Encoder ──────────────────────────────────────────────────────────────────

--- Create a new encoder for converting PCM audio data into DFPWM.
--
-- The returned encoder is itself a function. This function accepts a table of
-- audio data, where each value is a signed 8-bit integer between -128 and 127,
-- and returns the encoded DFPWM data as a list of bytes.
--
-- The encoder maintains its filter state between calls, allowing a long audio
-- stream to be encoded incrementally in fixed-size chunks.
--
-- @treturn function(input: {number...}):{number...} The encoder function
-- @see encode A helper function for encoding an entire file of audio at once.
-- @usage
--     local dfpwm = require "cc.audio.dfpwm"
--     local encoder = dfpwm.make_encoder()
--     local data = encoder({ 0, 10, 20, 30, 20, 10, 0, -10 })
local function make_encoder()
    local q         = 0
    local s         = 0
    local lp_charge = 0

    return function(input)
        expect(1, input, "table")

        local result = {}
        for i = 1, #input, 8 do
            local byte = 0
            for j = 0, 7 do
                local level         = input[i + j] or 0
                local current_charge = lp_charge

                -- Clamp delta for the LPF update to signed 8-bit range.
                local delta_t = level - current_charge
                if     delta_t >  127 then delta_t =  127
                elseif delta_t < -128 then delta_t = -128
                end

                -- Determine output bit and advance the predictor.
                local new_q
                local current_s = s
                if level > q or (level == -128 and q == -128) then
                    new_q  = math.min(q + current_s, 127)
                    byte   = byte + 2 ^ j
                else
                    new_q  = math.max(q - current_s, -128)
                end

                -- Adapt predictor strength (decays toward 1; rises toward |error|).
                local next_s
                if current_s ~= 1 then
                    next_s = math.floor(current_s * (PREC_CEIL - LPF_STRENGTH) / PREC_CEIL)
                else
                    next_s = 1
                end
                if math.abs(level - new_q) > 0 then
                    next_s = math.max(next_s, math.min(math.abs(level - new_q), 255))
                end

                q  = new_q
                s  = next_s

                -- Advance the low-pass filter using the actual input level.
                lp_charge = math.floor(current_charge + delta_t * LPF_STRENGTH / PREC_CEIL)
            end
            result[#result + 1] = byte
        end
        return result
    end
end

-- ── Decoder ──────────────────────────────────────────────────────────────────

--- Create a new decoder for converting DFPWM audio data into PCM.
--
-- The returned decoder is itself a function. This function accepts a table of
-- bytes (each value in [0, 255], one byte per 8 PCM samples) and returns the
-- decoded audio as a table of signed 8-bit integers between -128 and 127.
--
-- The decoder maintains its filter state between calls, so it can be used to
-- decode an audio stream incrementally without stitching artifacts.
--
-- @treturn function(input: {number...}):{number...} The decoder function
-- @see decode A helper function for decoding an entire file of audio at once.
-- @usage
--     local dfpwm = require "cc.audio.dfpwm"
--     local decoder = dfpwm.make_decoder()
--     local speaker = peripheral.find("speaker")
--     -- Assume `data` is a list of bytes loaded from a DFPWM file.
--     speaker.playAudio(decoder(data))
local function make_decoder()
    local q         = 0
    local s         = 0
    local lp_charge = 0

    return function(input)
        expect(1, input, "table")

        local result = {}
        for i = 1, #input do
            local byte = input[i]
            for j = 0, 7 do
                local current_q      = q
                local current_s      = s
                local current_charge = lp_charge

                -- Extract bit j from the byte.
                local bit = math.floor(byte / 2 ^ j) % 2 ~= 0

                -- Advance the predictor.
                local new_q
                if bit then
                    new_q = math.min(q + current_s, 127)
                else
                    new_q = math.max(q - current_s, -128)
                end

                -- Adapt predictor strength (same decay rule as encoder;
                -- uses |new_q - old_q| as the step proxy in place of |level - new_q|).
                local next_s
                if current_s ~= 1 then
                    next_s = math.floor(current_s * (PREC_CEIL - LPF_STRENGTH) / PREC_CEIL)
                else
                    next_s = 1
                end
                if math.abs(new_q - current_q) > 0 then
                    next_s = math.max(next_s, math.min(math.abs(new_q - current_q), 255))
                end

                q = new_q
                s = next_s

                -- Advance the low-pass filter using the reconstructed predictor level.
                local delta_t = new_q - current_charge
                if     delta_t >  127 then delta_t =  127
                elseif delta_t < -128 then delta_t = -128
                end
                lp_charge = math.floor(current_charge + delta_t * LPF_STRENGTH / PREC_CEIL)

                result[#result + 1] = lp_charge
            end
        end
        return result
    end
end

-- ── Convenience wrappers ──────────────────────────────────────────────────────

--- A convenience function for decoding a complete file of DFPWM audio at once.
--
-- This creates a new stateless decoder, runs it on the supplied bytes, and
-- returns the PCM result.  For streaming use (where filter state must be
-- preserved across calls), use [`make_decoder`] instead.
--
-- @tparam {number...} input The DFPWM data to decode as a list of bytes.
-- @treturn {number...} The decoded PCM audio as a list of signed 8-bit numbers.
-- @see make_decoder
local function decode(input)
    expect(1, input, "table")
    return make_decoder()(input)
end

--- A convenience function for encoding a complete audio file into DFPWM at once.
--
-- This creates a new stateless encoder, runs it on the supplied PCM samples, and
-- returns the DFPWM bytes.  For streaming use (where filter state must be
-- preserved across calls), use [`make_encoder`] instead.
--
-- @tparam {number...} input The PCM audio as a list of signed 8-bit integers
-- between -128 and 127.
-- @treturn {number...} The encoded DFPWM data as a list of bytes.
-- @see make_encoder
local function encode(input)
    expect(1, input, "table")
    return make_encoder()(input)
end

return {
    make_encoder = make_encoder,
    make_decoder = make_decoder,
    encode       = encode,
    decode       = decode,
}

