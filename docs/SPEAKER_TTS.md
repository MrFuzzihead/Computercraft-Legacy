# Speaker Text-to-Speech (Option A) — Transcoder Spec

This document describes the **external transcoding endpoint** used by the
`cc.audio.tts` ROM module (`rom/modules/main/cc/audio/tts.lua`).

## Why a transcoder is required

The ComputerCraft speaker pipeline can only ingest:

- **raw signed 8-bit PCM** at 48 kHz mono, via `speaker.playAudio(table)`, or
- **DFPWM**, via [`cc.audio.dfpwm`].

There is **no MP3/Opus/WAV decoder** anywhere in the mod (client or server).
TTS providers (Google, Azure, Polly, Piper, etc.) return compressed audio, so a
small HTTP service must sit between the in-game computer and the TTS provider to
**decode + resample + re-encode** into a speaker-ready format.

```
in-game computer ──HTTP GET──▶ transcoder ──▶ TTS provider (MP3/Opus)
                                   │
                                   ├─ ffmpeg: decode → 48 kHz mono
                                   ├─ encode to dfpwm / pcm_u8 / pcm_s8
                                   ◀──── raw audio bytes ────────────
```

## HTTP contract

|                 |                                                                    |
|-----------------|--------------------------------------------------------------------|
| Method          | `GET`                                                              |
| Query: `text`   | Required. The text to synthesise (URL-encoded by the module).      |
| Query: `lang`   | Optional. BCP-47 / provider language code. Default `en`.           |
| Query: `format` | Optional. One of `dfpwm` (default), `pcm_u8`, `pcm_s8`.            |
| Query: `voice`  | Optional. Provider-specific voice id.                              |
| Response body   | **Raw audio bytes** in the requested `format`. No JSON, no base64. |
| Status          | `200` on success; any non-2xx is treated as failure by `http.get`. |

The module streams the body in chunks and feeds it to the speaker, so the
service may stream the response (chunked transfer encoding) for low latency.

### Format semantics (must match exactly)

| `format` | Bytes mean                            | Speaker target              |
|----------|---------------------------------------|-----------------------------|
| `dfpwm`  | DFPWM1a, 1 bit/sample, 8 samples/byte | decoded by `cc.audio.dfpwm` |
| `pcm_u8` | unsigned 8-bit PCM, `0..255`          | module subtracts 128        |
| `pcm_s8` | signed 8-bit PCM, two's complement    | used as-is                  |

**All formats must be 48 kHz, mono.** `dfpwm` is recommended — it is ~8× smaller
on the wire than `pcm_*` and matches the codec the client already decodes.

## ffmpeg recipes

Decode a provider's MP3 (`in.mp3`) to each speaker format:

```bash
# 48 kHz mono unsigned 8-bit PCM (pcm_u8)
ffmpeg -i in.mp3 -ar 48000 -ac 1 -f u8 -acodec pcm_u8 out.raw

# 48 kHz mono signed 8-bit PCM (pcm_s8)
ffmpeg -i in.mp3 -ar 48000 -ac 1 -f s8 -acodec pcm_s8 out.raw

# DFPWM (ffmpeg >= 5.1 has a native dfpwm encoder)
ffmpeg -i in.mp3 -ar 48000 -ac 1 -f dfpwm out.dfpwm
```

## Reference implementation (Node + Express + ffmpeg)

```js
import express from "express";
import { spawn } from "node:child_process";

const app = express();

// Replace fetchTts() with your provider call; it must resolve to an MP3 stream.
async function fetchTts(text, lang, voice) { /* ... returns a Readable ... */ }

const FORMAT_ARGS = {
  dfpwm:  ["-f", "dfpwm"],
  pcm_u8: ["-f", "u8", "-acodec", "pcm_u8"],
  pcm_s8: ["-f", "s8", "-acodec", "pcm_s8"],
};

app.get("/say", async (req, res) => {
  const text = String(req.query.text ?? "");
  const lang = String(req.query.lang ?? "en");
  const voice = req.query.voice ? String(req.query.voice) : undefined;
  const format = String(req.query.format ?? "dfpwm");
  if (!text) return res.status(400).end("missing text");
  if (!FORMAT_ARGS[format]) return res.status(400).end("bad format");

  const mp3 = await fetchTts(text, lang, voice);
  const ff = spawn("ffmpeg", [
    "-hide_banner", "-loglevel", "error",
    "-i", "pipe:0",
    "-ar", "48000", "-ac", "1",
    ...FORMAT_ARGS[format],
    "pipe:1",
  ]);

  res.setHeader("content-type", "application/octet-stream");
  mp3.pipe(ff.stdin);
  ff.stdout.pipe(res);
  ff.on("error", () => res.destroy());
});

app.listen(8080);
```

## Recommended provider: Piper (free, offline, no API key)

[Piper](https://github.com/rhasspy/piper) is a self-hosted neural TTS engine.
A ready-to-run transcoder that pairs Piper with ffmpeg and serves the contract
above lives in [`tools/tts-piper`](../tools/tts-piper/README.md) — Docker or
local Node, with a one-line `tts.endpoint` to point the module at it.

## Using a public TTS endpoint directly (no transcode)

The **Google Translate `translate_tts` endpoint** is sometimes used:

```
https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=en&q=hello
```

Caveats:

- It returns **MP3**, which the speaker **cannot** play — you still need a
  transcoder. It is *not* a drop-in `format=pcm_*`/`dfpwm` source.
- It is an **undocumented, unofficial** endpoint. It is **not a free, supported
  API**: it is rate-limited, requires a `client`/token-style parameter, may break
  without notice, and using it programmatically is outside Google's Terms of
  Service. For anything real, use the official **Google Cloud Text-to-Speech**
  API (paid, with a monthly free tier) or a self-hosted engine like **Piper**
  (free, offline) behind the transcoder above.

## In-game configuration

1. Enable HTTP and **whitelist your endpoint host** in the mod config
   (`http_whitelist`, `;`-separated, `*` wildcards allowed), e.g.
   `*.example;my-tts-proxy.example`.
2. Point the module at your endpoint and speak:

```lua
local tts = require "cc.audio.tts"
tts.endpoint = "https://my-tts-proxy.example/say"

local speaker = peripheral.find("speaker")
assert(tts.speak(speaker, "All systems nominal", { lang = "en", format = "dfpwm" }))
```

## Related

- `rom/modules/main/cc/audio/tts.lua` — the module.
- `rom/modules/main/cc/audio/dfpwm.lua` — DFPWM codec used for `format=dfpwm`.
- `docs/TWEAKEDCC_COVERAGE.md` — speaker peripheral coverage.


