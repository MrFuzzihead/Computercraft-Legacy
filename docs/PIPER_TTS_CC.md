# Piper TTS for ComputerCraft

A self-hosted **text-to-speech bridge** that lets ComputerCraft speakers talk.
It wraps [Piper](https://github.com/rhasspy/piper) (a free, offline, neural TTS
engine) behind a tiny HTTP service that returns audio in a format the in-game
speaker can play directly.

> **TL;DR** — Run this service, whitelist its host in your CC config, then in Lua:
> ```lua
> local tts = require "tts"
> tts.endpoint = "http://localhost:8080/say"
> tts.speak(peripheral.find("speaker"), "Hello from ComputerCraft")
> ```

---

## 1. Why this exists

ComputerCraft / CC:Tweaked speakers can **only** ingest:

- **raw signed 8-bit PCM** at **48 kHz, mono**, via `speaker.playAudio(table)`, or
- **DFPWM**, via the built-in `cc.audio.dfpwm` module.

There is **no MP3/Opus/WAV decoder available in-game**. Every real TTS engine
emits compressed or high-bitrate audio (MP3, Opus, 16-bit WAV), and decoding
those in pure Lua is impractical.

So a small external service must sit between the in-game computer and the TTS
engine to **synthesize → resample → re-encode** into a speaker-ready stream.

```
┌────────────┐   HTTP GET    ┌──────────────────────────────┐
│ CC computer│ ────────────▶ │  piper-tts-cc service        │
│  (Lua)     │   /say?text=  │                              │
│            │               │   piper  → raw s16le PCM     │
│  speaker.  │ ◀──────────── │   ffmpeg → 48 kHz mono       │
│  playAudio │  raw bytes    │           → dfpwm / pcm_*    │
└────────────┘               └──────────────────────────────┘
```

Piper is recommended because it is **free, runs fully offline, needs no API key
or billing, and has no rate limits or Terms-of-Service restrictions** — unlike
cloud TTS or the unofficial Google Translate endpoint.

---

## 2. HTTP contract

The in-game client talks to this service with a single `GET` request.

|                |                                                                  |
|----------------|------------------------------------------------------------------|
| Method         | `GET`                                                            |
| Path           | `/say`                                                           |
| Query `text`   | **Required.** Text to synthesize (URL-encoded by the client).    |
| Query `lang`   | Optional. Language key. Default `en`.                            |
| Query `format` | Optional. One of `pcm_u8` (default), `pcm_s8`, `dfpwm`.          |
| Query `voice`  | Optional. Voice id within the language.                          |
| Response       | **Raw audio bytes** in the requested format. No JSON, no base64. |
| Status         | `200` on success; any non-2xx is treated as failure.             |

Also expose `GET /healthz` → `200 {"ok":true}` for liveness checks.

### Format semantics (must be exact — the speaker is unforgiving)

| `format` | Body bytes                            | CC side does                 |
|----------|---------------------------------------|------------------------------|
| `pcm_u8` | unsigned 8-bit PCM, `0..255`          | subtracts 128 → signed       |
| `pcm_s8` | signed 8-bit PCM (two's complement)   | uses as-is                   |
| `dfpwm`  | DFPWM1a, 1 bit/sample, 8 samples/byte | decodes via `cc.audio.dfpwm` |

**All formats MUST be 48 kHz, mono.**

**Format recommendations:**
- Use `pcm_u8` (default) for universal compatibility. It works with any ffmpeg version and has no known compatibility issues.
- Use `pcm_s8` if you prefer signed PCM and have it available.
- Use `dfpwm` **only** if your ffmpeg's DFPWM encoder is known to produce output compatible with the CC DFPWM decoder. Some builds have encoding issues that result in silence; when in doubt, use `pcm_u8` (~8× more bandwidth but rock-solid).

---

## 3. Reference implementation (Node + Express + Piper + ffmpeg)

### `server.js`

```js
// piper-tts-cc — Piper TTS transcoder for ComputerCraft speakers.
//
// Pipeline:  text → piper (raw s16le @ model rate) → ffmpeg (48 kHz mono,
//            dfpwm / pcm_u8 / pcm_s8) → HTTP response body.

import express from "express";
import { spawn } from "node:child_process";
import { readFileSync } from "node:fs";
import path from "node:path";

const PORT = Number(process.env.PORT || 8080);
const PIPER_BIN = process.env.PIPER_BIN || "piper";
const FFMPEG_BIN = process.env.FFMPEG_BIN || "ffmpeg";
const MODEL_DIR = process.env.PIPER_MODEL_DIR || "./models";
const MAX_TEXT = Number(process.env.MAX_TEXT || 1000);

// Map "lang" or "lang/voice" → Piper model filename (relative to MODEL_DIR).
// Download voices from https://huggingface.co/rhasspy/piper-voices
const VOICES = {
  en: "en_US-lessac-medium.onnx",
  "en/lessac": "en_US-lessac-medium.onnx",
  "en/ryan": "en_US-ryan-high.onnx",
  de: "de_DE-thorsten-medium.onnx",
  fr: "fr_FR-siwis-medium.onnx",
  es: "es_ES-davefx-medium.onnx",
};

// Speaker-ready encodings. All resample to 48 kHz mono first.
const FORMAT_ARGS = {
  dfpwm: ["-f", "dfpwm"],
  pcm_u8: ["-f", "u8", "-acodec", "pcm_u8"],
  pcm_s8: ["-f", "s8", "-acodec", "pcm_s8"],
};

function resolveModel(lang, voice) {
  const key = voice ? `${lang}/${voice}` : lang;
  const file = VOICES[key] || VOICES[lang];
  return file ? path.resolve(MODEL_DIR, file) : null;
}

// Piper ships "<model>.onnx.json" next to each model; read its sample rate.
function modelSampleRate(modelPath) {
  try {
    const cfg = JSON.parse(readFileSync(modelPath + ".json", "utf8"));
    return cfg?.audio?.sample_rate || 22050;
  } catch {
    return 22050;
  }
}

const app = express();
app.get("/healthz", (_req, res) => res.json({ ok: true }));

app.get("/say", (req, res) => {
   const text = String(req.query.text ?? "").slice(0, MAX_TEXT);
   const lang = String(req.query.lang ?? "en");
   const voice = req.query.voice ? String(req.query.voice) : undefined;
   const format = String(req.query.format ?? "pcm_u8");

  if (!text) return res.status(400).end("missing text");
  if (!FORMAT_ARGS[format]) return res.status(400).end("bad format");

  const model = resolveModel(lang, voice);
  if (!model) return res.status(404).end(`no voice for ${lang}${voice ? "/" + voice : ""}`);
  const rate = modelSampleRate(model);

  // Piper streams raw signed-16-bit mono PCM at the model's sample rate.
  const piper = spawn(PIPER_BIN, ["--model", model, "--output-raw"], {
    stdio: ["pipe", "pipe", "inherit"],
  });

  // ffmpeg resamples to 48 kHz mono and re-encodes to the requested format.
  const ff = spawn(
    FFMPEG_BIN,
    [
      "-hide_banner", "-loglevel", "error",
      "-f", "s16le", "-ar", String(rate), "-ac", "1", "-i", "pipe:0",
      "-ar", "48000", "-ac", "1",
      ...FORMAT_ARGS[format],
      "pipe:1",
    ],
    { stdio: ["pipe", "pipe", "inherit"] },
  );

  res.setHeader("content-type", "application/octet-stream");
  piper.stdout.pipe(ff.stdin);
  ff.stdout.pipe(res);

  const cleanup = () => { piper.kill("SIGKILL"); ff.kill("SIGKILL"); };
  req.on("close", cleanup);
  res.on("close", cleanup);
  piper.on("error", (e) => res.destroyed || res.destroy(e));
  ff.on("error", (e) => res.destroyed || res.destroy(e));

  piper.stdin.write(text + "\n");
  piper.stdin.end();
});

app.listen(PORT, () => console.log(`piper-tts-cc listening on :${PORT}`));
```

### `package.json`

```json
{
  "name": "piper-tts-cc",
  "version": "1.0.0",
  "type": "module",
  "main": "server.js",
  "scripts": { "start": "node server.js" },
  "engines": { "node": ">=18" },
  "dependencies": { "express": "^4.19.2" }
}
```

### `Dockerfile`

```dockerfile
FROM node:20-slim

RUN apt-get update \
 && apt-get install -y --no-install-recommends ffmpeg ca-certificates curl python3 python3-pip \
 && pip3 install --no-cache-dir --break-system-packages piper-tts \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Default voice (model + config). Add more voices the same way.
RUN mkdir -p models \
 && BASE=https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium \
 && curl -L -o models/en_US-lessac-medium.onnx      $BASE/en_US-lessac-medium.onnx \
 && curl -L -o models/en_US-lessac-medium.onnx.json $BASE/en_US-lessac-medium.onnx.json

COPY package.json ./
RUN npm install --omit=dev
COPY server.js ./

ENV PORT=8080 PIPER_MODEL_DIR=/app/models
EXPOSE 8080
CMD ["node", "server.js"]
```

---

## 4. Running it

### Docker (easiest)

```bash
docker build -t piper-tts-cc .
docker run --rm -p 8080:8080 piper-tts-cc
curl "http://localhost:8080/say?text=hello%20world&format=dfpwm" --output hello.dfpwm
```

### Local

```bash
# 1. ffmpeg (>= 5.1 for the native dfpwm encoder)
#    Debian/Ubuntu: sudo apt-get install -y ffmpeg
#    macOS:         brew install ffmpeg
#    Windows:       winget install Gyan.FFmpeg

# 2. Piper
pip install piper-tts

# 3. A voice (model + its .onnx.json config) into ./models
mkdir -p models
BASE=https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium
curl -L -o models/en_US-lessac-medium.onnx      $BASE/en_US-lessac-medium.onnx
curl -L -o models/en_US-lessac-medium.onnx.json $BASE/en_US-lessac-medium.onnx.json

# 4. Start
npm install && npm start
```

---

## 5. The ComputerCraft client

Ship a Lua client in this repo so users can install it straight onto a computer.
It depends only on stock CC:Tweaked APIs (`http`, `cc.audio.dfpwm`, `peripheral`),
so it works whether or not the host mod bundles a built-in `cc.audio.tts` module.

### `cc/tts.lua` (installable client library)

```lua
-- tts.lua — speak text through a ComputerCraft speaker via a piper-tts-cc server.
local dfpwm = require "cc.audio.dfpwm"

local MAX_SAMPLES = 128 * 1024
local DFPWM_CHUNK = 16 * 1024
local SUPPORTED   = { dfpwm = true, pcm_u8 = true, pcm_s8 = true }

local tts = { endpoint = nil }

local function url_encode(s)
  return (s:gsub("[^%w%-_%.~]", function(c) return string.format("%%%02X", c:byte()) end))
end

local function append(url, k, v)
  return url .. (url:find("?", 1, true) and "&" or "?") .. k .. "=" .. url_encode(v)
end

function tts.url(text, opts)
   opts = opts or {}
   local endpoint = opts.endpoint or tts.endpoint
   assert(type(endpoint) == "string", "set tts.endpoint or opts.endpoint")
   local format = opts.format or "pcm_u8"
   assert(SUPPORTED[format], "unsupported format " .. tostring(format))
  local url = append(endpoint, "text", text)
  url = append(url, "lang", opts.lang or "en")
  url = append(url, "format", format)
  if opts.voice then url = append(url, "voice", opts.voice) end
  return url
end

local function pcm_samples(str, format)
  local out = {}
  if format == "pcm_u8" then
    for i = 1, #str do out[i] = str:byte(i) - 128 end
  else -- pcm_s8
    for i = 1, #str do local b = str:byte(i); out[i] = b < 128 and b or b - 256 end
  end
  return out
end

function tts.stream(speaker, handle, opts)
   opts = opts or {}
   local format = opts.format or "pcm_u8"
   local volume = opts.volume
  local read_size, decoder
  if format == "dfpwm" then
    read_size = math.min(opts.chunk_size or DFPWM_CHUNK, DFPWM_CHUNK)
    decoder = dfpwm.make_decoder()
  else
    read_size = math.min(opts.chunk_size or MAX_SAMPLES, MAX_SAMPLES)
  end
  while true do
    local chunk = handle.read(read_size)
    if not chunk or #chunk == 0 then break end
    local samples = format == "dfpwm"
      and decoder({ chunk:byte(1, #chunk) })
      or  pcm_samples(chunk, format)
    while not speaker.playAudio(samples, volume) do
      os.pullEvent("speaker_audio_empty")
    end
  end
  return true
end

function tts.speak(speaker, text, opts)
  opts = opts or {}
  assert(http, "HTTP API is disabled")
  local handle, err = http.get(tts.url(text, opts), nil, true)
  if not handle then return nil, err end
  local ok, res = pcall(tts.stream, speaker, handle, opts)
  handle.close()
  if not ok then return nil, res end
  return res
end

return tts
```

### `speak.lua` (optional CLI program)

```lua
-- Usage: speak "Hello world"
local tts = require "tts"
tts.endpoint = settings.get("tts.endpoint") or "http://localhost:8080/say"

local text = table.concat({ ... }, " ")
if text == "" then print("Usage: speak <text>") return end

local speaker = peripheral.find("speaker")
if not speaker then printError("No speaker attached") return end

local ok, err = tts.speak(speaker, text)
if not ok then printError("TTS failed: " .. tostring(err)) end
```

### In-game setup

1. **Whitelist the host** in the mod config `http_whitelist` (`;`-separated,
   `*` wildcards allowed) — e.g. add `localhost` or your server's hostname.
2. Install the client (e.g. `wget <raw-url>/cc/tts.lua tts.lua`), then:

```lua
local tts = require "tts"
tts.endpoint = "http://localhost:8080/say"

local speaker = peripheral.find("speaker")
assert(tts.speak(speaker, "Reactor temperature nominal", { voice = "ryan" }))
```

---

## 6. Format note: PCM_U8 vs DFPWM

The default format is **`pcm_u8`**: it is universally compatible, works with any ffmpeg version, and has no known encoder-decoder compatibility issues.

**DFPWM** is ~8× more bandwidth-efficient (1 bit/sample vs 8 bits), but ffmpeg's DFPWM encoder can produce output that the CC DFPWM decoder does not process correctly, resulting in silence. If you wish to use DFPWM:

```bash
# Verify your ffmpeg supports it (requires ≥ 5.1):
ffmpeg -hide_banner -encoders | grep dfpwm
```

If present and you get silence from DFPWM output, the encoder variant is incompatible — fall back to `format=pcm_u8`.

---

## 7. Configuration

| Env var           | Default    | Meaning                    |
|-------------------|------------|----------------------------|
| `PORT`            | `8080`     | HTTP listen port           |
| `PIPER_BIN`       | `piper`    | Piper executable           |
| `FFMPEG_BIN`      | `ffmpeg`   | ffmpeg executable          |
| `PIPER_MODEL_DIR` | `./models` | Where `.onnx` models live  |
| `MAX_TEXT`        | `1000`     | Max characters per request |

### Adding voices

Drop the `.onnx` + `.onnx.json` pair into `models/`, then add a line to `VOICES`:

```js
"en/ryan": "en_US-ryan-high.onnx",   // → ?lang=en&voice=ryan
de: "de_DE-thorsten-medium.onnx",    // → ?lang=de
```

Lookup order is `lang/voice` first, then `lang`. Browse voices at
<https://huggingface.co/rhasspy/piper-voices> (samples:
<https://rhasspy.github.io/piper-samples/>).

---

## 8. Testing

```bash
# Service health
curl -s http://localhost:8080/healthz

# Save a clip and inspect it (default pcm_u8 format)
curl "http://localhost:8080/say?text=testing" --output test.u8
ffplay -f u8 -ar 48000 -ac 1 test.u8     # should sound like "testing"
```

In-game smoke test:

```lua
local tts = require "tts"
tts.endpoint = "http://localhost:8080/say"
print(tts.url("hi"))   -- verify the URL builds correctly first
tts.speak(peripheral.find("speaker"), "one two three")
```

---

## 9. Troubleshooting

| Symptom                           | Cause / fix                                                                                                                    |
|-----------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `404 no voice for en`             | Model missing from `models/` or not in `VOICES`.                                                                               |
| Silence when using `format=dfpwm` | ffmpeg's DFPWM encoder is incompatible with the CC decoder. Use `format=pcm_u8` (default) instead.                             |
| `Unknown encoder 'dfpwm'`         | ffmpeg < 5.1 doesn't have DFPWM — use `format=pcm_u8` or upgrade.                                                              |
| `Domain not permitted` in-game    | Add the host to `http_whitelist` in the mod config.                                                                            |
| Audio too fast/slow or garbled    | Sample-rate mismatch — keep each model's `.onnx.json` beside its `.onnx`.                                                      |
| Long first-call latency           | Piper loads the model per request; keep a warm process or pre-load.                                                            |
| Choppy playback                   | Client back-pressure loop required — always wrap `playAudio` in the `speaker_audio_empty` loop (the bundled client does this). |

---

## 10. Roadmap / extension ideas

- **`format=wav`** passthrough + a tiny in-Lua WAV header stripper, to stay
  codec-agnostic.
- **Response caching** keyed by `(text, lang, voice, format)` to avoid
  re-synthesizing common phrases.
- **Phrase length / rate limiting** and per-IP quotas for shared servers.
- **WebSocket streaming** for lower-latency long passages (CC has
  `http.websocket`).
- **Multi-engine support** (espeak-ng for ultra-fast, Piper for quality) behind
  the same `/say` contract.

---

## 11. License

Recommend **MIT** for this bridge. Note that Piper voice models carry their own
licenses (mostly permissive, some non-commercial) — check each model card on
Hugging Face before redistributing.
```









