# CC:Tweaked API Coverage Plan

> Audited against [tweaked.cc](https://tweaked.cc/) on 2026-03-30.
> This document tracks what is implemented in this 1.7.10 legacy fork versus the
> CC:Tweaked reference, and provides a prioritized roadmap to close the gaps.

---

## ✅ Currently Implemented

| API / Peripheral | Source | Methods Covered |
|---|---|---|
| `os` | `OSAPI.java` + `bios.lua` | `queueEvent`, `startTimer`, `cancelTimer`, `setAlarm`, `cancelAlarm`, `shutdown`, `reboot`, `computerID`, `getComputerID`, `setComputerLabel`, `computerLabel`, `getComputerLabel`, `clock`, **`time`** (locale string + table→timestamp), **`day`** (locale string), `version`, `pullEvent`, `pullEventRaw`, `run`, `loadAPI`, `unloadAPI`, `sleep`, **`epoch`**, **`date`** |
| `fs` | `FSAPI.java` + `bios.lua` | `list`, `combine` (variadic), `getName`, `getSize`, `exists`, `isDir`, `isReadOnly`, `makeDir`, `move`, `copy`, `delete`, `open` (modes `r`/`w`/`a`/`rb`/`wb`/`ab`/`r+`/`w+`), `getDrive`, `getFreeSpace`, `find` (`*` + `?` wildcards), `getDir`, `complete` (options table), **`getCapacity`**, **`attributes`**, **`isDriveRoot`**; all text handles: **`read([count])`**, **`readLine([withTrailing])`**, **`seek`** |
| `term` | `TermAPI.java` + `rom/apis/term` | `write`, `blit`, `scroll`, `clear`, `clearLine`, `setCursorPos`, `getCursorPos`, `setCursorBlink`, **`getCursorBlink`**, `getSize`, `setTextColor/Colour`, `setBackgroundColor/Colour`, `getTextColor/Colour`, `getBackgroundColor/Colour`, `isColor/Colour`, `redirect`, `current`, `native`, **`nativePaletteColor/Colour`**, **`setPaletteColor/Colour`**, **`getPaletteColor/Colour`** |
| `redstone` / `rs` | `RedstoneAPI.java` | `getSides`, `setOutput`, `getOutput`, `getInput`, `setBundledOutput`, `getBundledOutput`, `getBundledInput`, `testBundledInput`, `setAnalogOutput/Analogue`, `getAnalogOutput/Analogue`, `getAnalogInput/Analogue` |
| `http` | `HTTPAPI.java` + `bios.lua` | `request`, `checkURL`, `get` (sync), `post` (sync); **binary flag** (`get`/`post`/`request`); **table argument form** (`get`/`post`/`request`); **custom timeout** (seconds, table or positional); **PATCH** verb; response: `readLine`, `readAll`, `read`, `close`, `getResponseCode`, **`getResponseHeaders`**; **response handle on error**; **raw bytes** (1.109.0); **`websocket`**, **`websocketAsync`**; handle: **`receive`**, **`send`**, **`close`** |
| `turtle` | `TurtleAPI.java` | All movement, dig, place, drop, suck, detect, compare, attack, fuel, inspect, equip, `getItemDetail` |
| `commands` | `CommandAPI.java` | `exec` (+ affected count), `execAsync`, `list` (+ prefix filter), `getBlockPosition`, `getBlockInfo` (+ `state`, `nbt`, dimension arg), `getBlockInfos` |
| `bit` | `BitAPI.java` | `bnot`, `band`, `bor`, `bxor`, `brshift`, `blshift`, `blogic_rshift` |
| `buffer` | `BufferAPI.java` | `new` |
| `colors` / `colours` | `rom/apis/colors` + `rom/apis/colours` | Constants + `combine`, `subtract`, `test`, **`packRGB`**, **`unpackRGB`**, **`toBlit`**, **`fromBlit`** |
| `rednet` | `rom/apis/rednet` | `open`, `close`, `isOpen`, `send`, `receive`, `broadcast`, `host`, `unhost`, `lookup` |
| `textutils` | `rom/apis/textutils` | `slowWrite`, `slowPrint`, `formatTime`, `pagedPrint`, `tabulate`, `pagedTabulate`, **`serialize/ise`** (+ `opts.compact`, `opts.allow_repetitions`), `unserialize/ise`, **`serializeJSON/iseJSON`** (+ `opts.unicode_strings`, `opts.allow_repetitions`), **`unserializeJSON/iseJSON`** (+ `opts.null`, `opts.parse_empty_array`), `urlEncode`, `complete`, `empty_json_array`, `json_null` |
| `peripheral` | `rom/apis/peripheral` | `getNames`, `isPresent`, `getType` (string side **or** wrapped table), `getMethods`, `call`, `wrap`, `find`, **`getName`**, **`hasType`** |
| `settings` | `rom/apis/settings` | `define`, `undefine`, `set`, `get`, `unset`, `clear`, `getNames`, `getDetails`, `load`, `save`; auto-loaded from `.settings` at boot via `bios.lua` |
| `gps` | `rom/apis/gps` | `locate` |
| `paintutils` | `rom/apis/paintutils` | **`parseImage`**, `loadImage`, `drawPixel`, `drawLine`, `drawBox`, `drawFilledBox`, `drawImage` |
| `keys` | `rom/apis/keys` | All Minecraft key constants + `getName` |
| `help` | `rom/apis/help` | `path`, `setPath`, `lookup` (exact → `.md` → `.txt` priority), `topics` (strips `.md`/`.txt` extensions, deduplicates), `completeTopic` |
| `window` | `rom/apis/window` | `create` (full window object with all term-surface methods); **`getLine`**, **`isVisible`**, **`reposition`** (optional 5th `newParent` arg) |
| `parallel` | `rom/apis/parallel` | `waitForAny`, `waitForAll` |
| `io` | `rom/apis/io` | `close`, `flush`, `input`, `output`, `lines`, `open`, `read`, `write` |
| `vector` | `rom/apis/vector` | `new` + full vector math (`add`, `sub`, `mul`, `div`, `unm`, `dot`, `cross`, `length`, `normalize`, `round`, `tostring`) |
| `disk` | `rom/apis/disk` | `isPresent`, `getLabel`, `setLabel`, `hasData`, `getMountPath`, `hasAudio`, `getAudioTitle`, `playAudio`, `stopAudio`, `eject`, `getID` |
| `multishell` | `rom/programs/advanced/multishell` | `launch`, `getFocus`, `setFocus`, `getTitle`, `setTitle`, `getCurrent`, `getCount` |
| `shell` | `rom/programs/shell` | `execute`, `run`, `exit`, `dir`, `setDir`, `path`, `setPath`, `resolve`, `resolveProgram` (with `.lua` extension fallback), `getRunningProgram`, `setAlias`, `clearAlias`, `aliases`, `programs`, `complete`, `getCompletionInfo`, `setCompletionInfo`; per-program env isolation; `arg` table injection |
| Modem peripheral | `ModemPeripheral.java` | `open`, `isOpen`, `close`, `closeAll`, `transmit`, `isWireless` + cable remote methods (`getNamesRemote`, `isPresentRemote`, `getTypeRemote`, `getMethodsRemote`, `callRemote`) |
| Monitor peripheral | `MonitorPeripheral.java` | Full term-compatible surface + `setTextScale` |
| Printer peripheral | `PrinterPeripheral.java` | `write`, `setCursorPos`, `getCursorPos`, `getPageSize`, `newPage`, `endPage`, `getInkLevel`, `setPageTitle`, `getPaperLevel` |
| Drive peripheral | `DiskDrivePeripheral.java` | `isDiskPresent`, `getDiskLabel`, `setDiskLabel`, `hasData`, `getMountPath`, `hasAudio`, `getAudioTitle`, `playAudio`, `stopAudio`, `ejectDisk`, `getDiskID` |

---

## ❌ Missing / Gaps vs CC:Tweaked

### 1. ~~`settings` API~~ ✅ Done

All ten methods are implemented as a pure-Lua file at `rom/apis/settings`.
Settings are auto-loaded from `.settings` at boot via `bios.lua`.

Behavioral fidelity fixes applied:
- `define` now validates `options.type` against the allowed-type set (`"number"`, `"string"`, `"boolean"`, `"table"`); passing any other string throws a descriptive error.
- `set` and `define` default both call `reserialize` (serialize → unserialize round-trip) on table values, ensuring the stored copy is independent of the caller's table.
- `get` and `getDetails` both return a `copy` (lightweight recursive clone) of table values, preventing callers from mutating stored state through the returned reference.
- `load` intentionally returns `true` for a missing file (fresh-install behaviour); upstream CC:Tweaked returns `false, msg` here. This divergence is by design — `bios.lua` ignores the return value either way.

**Tests**: `src/test/java/dan200/computercraft/core/lua/SettingsAPITest.java` — 40 cases, all green.

---

### 2. ~~`pocket` API~~ ✅ Done

`PocketAPI.java` now implements all three methods.

| Method | Notes |
|---|---|
| `pocket.equipBack()` | ✅ Searches the carrying player's inventory for a `WirelessModem` item, consumes one, sets `upgrade = 1` in the pocket computer's NBT, and attaches a `PocketModemPeripheral` to peripheral slot 2. Returns `true` on success or `false, reason` on failure. Runs on the main thread via `ILuaContext.executeMainThreadTask`. |
| `pocket.unequipBack()` | ✅ Detaches the modem peripheral from slot 2, clears the `upgrade` key from the pocket computer's NBT, and returns a `WirelessModem` item to the player's inventory (or drops it if full). Returns `true` on success or `false, reason` on failure. |
| `pocket.isEquipped()` | ✅ Returns `true` if `upgrade == 1` in the pocket computer's NBT (i.e., a modem is currently equipped). Reads the volatile stack reference directly — no main-thread dispatch required. |

The player/stack/inventory references used by `equipBack` and `unequipBack` are refreshed every game tick by `ItemPocketComputer.onUpdate` via `PocketAPI.update(player, stack, inventory)`. A static `Map<instanceID, PocketAPI>` in `ItemPocketComputer` keeps the mapping between `ServerComputer` instances and their `PocketAPI` objects.

---

### 3. `os` — ~~Missing 2 methods~~ ✅ Done

| Method | Notes |
|---|---|
| `os.epoch(timezone)` | ✅ Implemented in `OSAPI.java` (cases `"utc"`, `"local"`, `"ingame"`; defaults to `"ingame"`) |
| `os.date(format, time)` | ✅ Implemented in `OSAPI.java` (`strftime`-style tokens + `"*t"` table format; leading `"!"` for UTC) |

**Tests**: `src/test/java/dan200/computercraft/core/apis/OSAPITest.java` — 29 cases, all green.

---

### 4. `fs` — ~~Missing 2 methods~~ ✅ Done → ~~FS parity gaps~~ ✅ Done (2026-04-13)

| Method / Feature | Notes |
|---|---|
| `fs.attributes(path)` | ✅ Implemented in `FSAPI.java` (method index 17); returns `{size, isDir, isReadOnly, created, modified, modification}`. Timestamps use `java.nio.file.attribute.BasicFileAttributes` on `FileMount` paths (falls back to `File.lastModified()`); read-only mounts return `0`. |
| `fs.getCapacity(path)` | ✅ Implemented in `FSAPI.java` (method index 16); returns total drive capacity in bytes, or `nil` for read-only / unlimited mounts. |
| `fs.isDriveRoot(path)` | ✅ Implemented in `FSAPI.java` (method index 18); delegates to `FileSystem.isDriveRoot()` which checks `m_mounts.containsKey(sanitizedPath)`. Root `""` always returns `true`. (CC:Tweaked 1.87.0) |
| `fs.combine(path, ...)` variadic | ✅ Case 1 now accepts 2+ string arguments and folds each through `FileSystem.combine()`. Previously required exactly 2 args. (CC:Tweaked 1.80pr1) |
| `fs.find` `?` wildcard | ✅ `sanitizePath(path, true)` no longer strips `?`; `find()` pattern now converts `?` → `[^\\/]` (exactly one non-separator char). (CC:Tweaked 1.80pr1) |
| `ReadHandle.read([count])` | ✅ `ReaderObject` case 0 now accepts optional count (default 1); backed by `IMountedFileNormal.read(int)` which is properly implemented in both the RAF-backed (`openForReadSeekable`) and stream-based fallback paths. |
| `ReadHandle.readLine([withTrailing])` | ✅ `ReaderObject` case 1 now accepts optional boolean; backed by `IMountedFileNormal.readLine(boolean)`. When `true`, the terminating `\n` byte is included (normalised from `\r\n`). |
| `ReadHandle.seek([whence[, offset]])` | ✅ `ReaderObject` case 4. `FileMount`-backed files use a `RandomAccessFile`; non-seekable mounts (JarMount/ROM) use stream fallback and return `nil, "seek not supported..."`. |
| `WriteHandle.seek([whence[, offset]])` | ✅ `WriterObject` case 4. Modes `"w"` and `"a"` now use `openForWriteSeekable` (RAF-backed via `FileMount.openForWriteRandom`). |
| `ReadWriteHandle.read([count])` | ✅ `wrapReadWrite` case 0 (new); delegates to `IMountedFileReadWrite.read(int)` implemented in `FileSystem.openForReadWrite` anonymous class. |
| `ReadWriteHandle.readLine([withTrailing])` | ✅ `wrapReadWrite` case 1 updated; delegates to `IMountedFileReadWrite.readLine(boolean)`. |
| `fs.complete` options table | ✅ `bios.lua` `fs.complete` now accepts arg 3 as either a boolean (legacy) or an options table with `include_files` / `include_dirs` keys (CC:Tweaked 1.101.0). |

**Tests**: `src/test/java/dan200/computercraft/core/apis/FSAPITest.java` — **34 cases**, all green.

**In-game test**: `run/saves/Test World/computer/37/test_fs_parity.lua`

---

### 5. `http` — ~~Missing response method gaps~~ ✅ Done ~~(WebSocket planned)~~ ✅ Done ~~(1.80–1.109 feature set)~~ ✅ Done

| Feature | Notes |
|---|---|
| `Response.getResponseHeaders()` | ✅ Returns `{ [string]: string }` — multiple values for the same header are joined with `", "`, and the HTTP status-line pseudo-header (null key from `HttpURLConnection`) is filtered out. |
| `Response.readLine([withTrailingNewline])` | ✅ Optional boolean parameter added. When `true`, the terminator bytes (`\n`, `\r`, or `\r\n`) are included in the returned string. |
| `Response.read([count])` | ✅ Now returns a Lua string (`byte[]`) instead of a raw number, and accepts an optional `count` to read multiple bytes at once. |
| `Response.readAll()` | ✅ Already correct — returns the remaining body as a string. |
| `Response.close()` | ✅ Already correct. |
| `Response.getResponseCode()` | ✅ Now returns two values: `(code, message)` — the numeric HTTP status code and the HTTP status message string (e.g. `200, "OK"` or `404, "Not Found"`). `HTTPRequest` captures `HttpURLConnection.getResponseMessage()` and passes it through to `HTTPResponse`. A `null` message (unusual but possible) is normalised to `""`. |
| Response handle returned on error (1.80pr1) | ✅ `wrapRequest` now captures the third event argument from `http_failure` and returns it as the third return value: `nil, errMsg, responseHandle`. The Java `advance()` method already emitted the handle as the third event arg; only the Lua side needed updating. |
| Binary handle flag (1.80pr1) | ✅ `http.get(url, headers, binary)`, `http.post(url, body, headers, binary)`, `http.request(url, body, headers, method, timeout, binary)` all accept a boolean `binary` parameter forwarded to the native as arg[6]. `HTTPRequest` stores it for future text/binary mode differentiation; `HTTPResponse` already returns raw bytes regardless. |
| Table argument form (1.80pr1.6) | ✅ All three functions accept a table as the first argument. Fields: `url`, `body` (post only), `headers`, `method` (request only), `timeout`, `binary`. |
| PATCH verb (1.86.0) | ⚠️ Added `"PATCH"` to the allowed-methods validation array in `HTTPRequest.java` so the Lua layer accepts the verb. However, Java 8's `HttpURLConnection.setRequestMethod("PATCH")` throws `ProtocolException: Invalid HTTP method: PATCH` — PATCH was not added to `HttpURLConnection`'s allowed-methods set until **Java 11**. On Java 8 (e.g. 1.8.0_202) the connection setup fails silently (caught as `IOException`), `wasSuccessful()` returns `false`, and `http_failure` is queued instead of `http_success`. The in-game smoke test (`test_http_all_changes`) skips the live PATCH round-trip on Java 8 and logs a `[SKIP]` note. **Fix**: a reflection fallback in `HTTPRequest.java` (`Field#set(connection, verb)` after catching `ProtocolException`) is the only viable solution; deferred until Java upgrade or explicit approval. |
| Custom timeouts (1.105.0) | ✅ `timeout` (seconds) accepted as positional arg[5] or table field. `HTTPAPI` converts to milliseconds and `HTTPRequest` calls `connection.setConnectTimeout` / `connection.setReadTimeout` when non-zero. |
| Raw bytes (1.109.0) | ✅ `HTTPResponse` already returns `byte[]` for all read operations; no change needed. |
| `http.websocket(url [, headers])` | ✅ Implemented — synchronous Lua wrapper in `bios.lua`; returns handle on success, `false, err` on failure. |
| `http.websocketAsync(url [, headers])` | ✅ Implemented — async Lua wrapper in `bios.lua`; queues `websocket_failure` on immediate rejection. |
| `http.checkURLAsync(url)` | ✅ Implemented in `bios.lua` — calls the native synchronous `http.checkURL` (whitelist + format check) and queues `check_url_success(url)` or `check_url_failure(url, err)`. No Java changes needed since the check is already instant. |

The WebSocket handle returned by `websocket_success` is a Java `ILuaObject` (`WebSocketHandle`) with three methods:

| Method | Notes |
|---|---|
| `handle.receive([timeout])` | Loops on `ILuaContext.pullEventRaw` filtering for `websocket_message(url, msg, isBinary)` and `websocket_closed(url)` events. Returns `(message, isBinary)` on success (1.80pr1.13). Optional wall-clock deadline (1.87.0). On connection close returns `nil, nil, "Connection closed"`; on timeout returns `nil, nil, "Timeout"` (1.117.0). |
| `handle.send(message [, binary])` | Sends a text frame by default; `binary = true` sends a binary frame (`ByteBuffer`) (1.81.0). Throws `LuaException` if the connection is closed. |
| `handle.close()` | Initiates a WebSocket close handshake. Safe to call multiple times. |
| `handle.getResponseHeaders()` | ✅ Returns a flat `{string: string}` map of the HTTP response headers received during the WebSocket opening handshake. Headers are captured in `WebSocketRequest.onOpen` from the `ServerHandshake` object provided by the Java-WebSocket library. |

Background events queued by `WebSocketRequest` (extends `org.java_websocket.client.WebSocketClient`):
- `websocket_message(url, message, isBinary)` — fired on `onMessage(String)` / `onMessage(ByteBuffer)`.
- `websocket_closed(url)` — fired on `onClose` after a successful connection.
- `websocket_success(url, handle)` / `websocket_failure(url, err)` — fired from `HTTPAPI.advance()` once the initial connect attempt settles.

URL validation uses `HTTPRequest.checkWebSocketURL(String)` — permits `ws`/`wss` schemes, applies the same `ComputerCraft.http_whitelist` hostname check. TLS (`wss://`) is initialised from `SSLContext.getDefault()`.

The WebSocket library (`org.java-websocket:Java-WebSocket:1.5.6`) is added as a `shadowImplementation` dependency, bundled into the mod JAR exactly like Cobalt.

**Tests**: `src/test/java/dan200/computercraft/core/apis/WebSocketHandleTest.java` — 18 cases, all green.

---

### 6. `term` — ~~Missing `getCursorBlink`~~ ✅ Done ~~(palette still pending)~~ ✅ Done

| Method | Notes |
|---|---|
| `term.getCursorBlink()` | ✅ Implemented in `TermAPI.java` (method index 19); reads `Terminal.m_cursorBlink` under the terminal lock. `window.getCursorBlink()` added to `rom/apis/window` so redirect targets expose the getter without an error stub. |
| `term.nativePaletteColor(color)` | ✅ Implemented in `TermAPI.java` (method index 20/21). Returns factory-default `{r,g,b}` from the static `Terminal.DEFAULT_PALETTE_HEX` table; never reflects custom palette changes. `*Colour` British variant at index 21. |
| `term.setPaletteColor(color, r, g, b)` | ✅ Implemented in `TermAPI.java` (method index 22/23). Validates r/g/b in [0,1]; stores in `Terminal.m_palette`; marks terminal changed so the state is flushed to NBT and synced to the client. `*Colour` British variant at index 23. |
| `term.getPaletteColor(color)` | ✅ Implemented in `TermAPI.java` (method index 24/25). Returns current palette entry as `{r,g,b}` copy. `*Colour` British variant at index 25. |

Palette is serialized via a packed `int[16]` NBT array (`"term_palette"`) in `Terminal.writeToNBT`/`readFromNBT`, flowing automatically through the existing `ServerTerminal.writeDescription` → `ClientTerminal.readDescription` sync path. Old terminals without the key fall back to factory defaults.

Client-side rendering: `FixedWidthFontRenderer.drawString` has a new overload accepting `double[][] palette`; colour changes now flush the draw-buffer and call `GL11.glColor3f` inline rather than using the baked Colour display lists. Both `WidgetTerminal` and `TileEntityMonitorRenderer` pass `terminal.getPalette()`. The `window` Lua API forwards all six palette methods to `parent`.

Terminal `reset()` restores the palette to factory defaults.

**Tests**: `src/test/java/dan200/computercraft/core/apis/TermAPITest.java` — 17 cases, all green.


---

### 7. `colors` — ~~Missing 4 utility functions~~ ✅ Done

| Method | Notes |
|---|---|
| `colors.packRGB(r, g, b)` | ✅ Implemented in `rom/apis/colors` — pure arithmetic, no `bit32` dependency |
| `colors.unpackRGB(rgb)` | ✅ Implemented in `rom/apis/colors` — pure arithmetic |
| `colors.toBlit(color)` | ✅ Implemented in `rom/apis/colors` — O(1) pre-built lookup table (matching CC:Tweaked); loop fallback for non-power-of-two inputs |
| `colors.fromBlit(char)` | ✅ Implemented in `rom/apis/colors` — enforces `#char == 1` to prevent multi-char strings (e.g. `"10"`) from returning a wrong value |
| `colors.rgb8(r,g,b \| rgb)` | ✅ Implemented in `rom/apis/colors` — deprecated CC:Tweaked dispatcher to `packRGB`/`unpackRGB` |

All five functions are automatically mirrored into `colours` via its existing `for k,v in pairs(colors)` copy loop.

**Tests**: `src/test/java/dan200/computercraft/core/lua/ColorsAPITest.java` — 35 cases, all green.

---

### 8. `cc.*` module system — ✅ Done

Pure-Lua modules loaded via `require("cc.module")`. The `require` / `package` bootstrap is
implemented in `bios.lua` and resolves paths under `rom/modules/main/`.

| Module | Status | Notes |
|---|---|---|
| `cc.expect` | ✅ Done | `rom/modules/main/cc/expect.lua` — `expect`, `field`, `range` |
| `cc.completion` | ✅ Done | `rom/modules/main/cc/completion.lua` — `choice`, `peripheral`, `side`, `file`, `dir`, `program`, `setting` |
| `cc.audio.dfpwm` | ✅ Done | `rom/modules/main/cc/audio/dfpwm.lua` — `make_encoder`, `make_decoder`, `encode`, `decode` |
| `cc.shell.completion` | ✅ Done | `rom/modules/main/cc/shell/completion.lua` — `file`, `dir`, `dirOrFile`, `program`, `programWithArgs`, `help`, `choice`, `peripheral`, `side`, `setting`, `command`, `build` |
| `cc.strings` | ✅ Done | `rom/modules/main/cc/strings.lua` — `wrap`, `ensure_width`, `split` |
| `cc.pretty` | ✅ Done | `rom/modules/main/cc/pretty.lua` — full port of CC:Tweaked's pretty-printer |
| `cc.image.nft` | ✅ Done | `rom/modules/main/cc/image/nft.lua` — `parse`, `load`, `draw` |

**`cc.pretty` details**: Ported directly from CC:Tweaked. Exposes `empty`, `space`, `line`,
`space_line`, `text`, `concat`, `nest`, `group`, `write`, `print`, `render`, `pretty`, and
`pretty_print`. The `debug.getinfo` / `debug.getlocal` calls are guarded against `nil` (the
debug library is not loaded), so function display falls back to plain `tostring(fn)`.

**`cc.image.nft` details**: Faithful port of CC:Tweaked's NFT image module. Supports `\31`
(foreground color token) and `\30` (background color token), each followed by one blit-format
hex digit. Colors reset to `"0"`/`"f"` at every newline. `load()` is adapted to use `fs.open`
instead of upstream's `io.open`, which correctly returns a `nil, error` pair on failure in
this environment.

**Tests**: `src/test/java/dan200/computercraft/core/lua/CcPrettyTest.java` — 35 cases, all green.
**Tests**: `src/test/java/dan200/computercraft/core/lua/CcImageNftTest.java` — 22 cases, all green.

---

### 8b. `cc.audio.dfpwm` — ✅ Done

Pure-Lua port of CC:Tweaked's DFPWM audio codec module.

| Function | Notes |
|---|---|
| `make_encoder()` | Returns a stateful encoder function. Each call accepts a `{number...}` table of signed 8-bit PCM samples ([-128, 127]) and returns a `{number...}` table of DFPWM bytes. Filter state (predictor `q`, strength `s`, low-pass charge) is preserved across calls, enabling incremental encoding of a stream. |
| `make_decoder()` | Returns a stateful decoder function. Each call accepts a `{number...}` table of DFPWM bytes and returns a `{number...}` table of signed 8-bit PCM samples. Filter state is preserved across calls. |
| `encode(input)` | Convenience wrapper: creates a fresh encoder, encodes `input` in one call, returns the result. |
| `decode(input)` | Convenience wrapper: creates a fresh decoder, decodes `input` in one call, returns the result. |

Algorithm constants match upstream exactly: `PREC_SHIFT = 10`, `PREC_CEIL = 1024`,
`LPF_STRENGTH = 140`. Predictor strength adapts on each sample using the same decay
(`floor(s * (PREC_CEIL - LPF_STRENGTH) / PREC_CEIL)`) and error-floor rules as CC:Tweaked.

**Tests**: `src/test/java/dan200/computercraft/core/lua/CcAudioDfpwmTest.java` — 22 cases, all green.

---

### 8c. `cc.shell.completion` — ✅ Done

Pure-Lua module at `rom/modules/main/cc/shell/completion.lua`.  Provides
completion functions whose signature (`shell, index, text, previous`) matches
the contract of [`shell.setCompletionFunction`].  Each function delegates to
[`fs.complete`] (implemented in `bios.lua`) using legacy parameter semantics:

| Parameter 3 (`bIncludeFiles`) | Parameter 4 (`bIncludeDirs`) | Meaning in this codebase |
|---|---|---|
| `true` | — | Include regular files in results |
| — | `false` | Directories appear **with** trailing `"/"` only |
| — | `true` | Directories appear **both** with `"/"` and as bare names |

| Function | `bIncludeFiles` | `bIncludeDirs` | Notes |
|---|---|---|---|
| `file(shell, index, text, previous)` | `true` | `false` | Files + dirs (with `"/"` only). Matches `completeFile` in `startup`. |
| `dir(shell, index, text, previous)` | `false` | `true` | Directories only, shown with `"/"` and as bare names. Matches `completeDir` in `startup`. |
| `dirOrFile(shell, index, text, previous)` | `true` | `true` | Files + directories (both forms). Matches `completeEither` in `startup`. |
| `program(shell, index, text, previous)` | — | — | Calls `shell.completeProgram(text)` at index 1; returns `nil` otherwise. Mirrors the local `completeProgram` in `startup`. |
| `programWithArgs(shell, index, text, previous)` | — | — | Index 1 = program name completion via `shell.completeProgram`. Index > 1 = resolves `previous[2]` as a program, looks up `shell.getCompletionInfo()[resolved]`, and delegates to its `fnComplete` with `index-1` and a shifted `previous` table (nested program name at `[1]`). Returns `nil` if the sub-program is unresolvable or has no registered completion. |
| `help(shell, index, text, previous)` | — | — | Calls `help.completeTopic(text)` at index 1; returns `nil` otherwise. The global `help` API is captured as `_help_api` before the local function is declared to avoid a Lua naming-collision bug. |
| `choice(choices [, add_space])` | — | — | **Factory** — returns a completion function that calls `cc.completion.choice(text, choices, add_space)`. Validates args with `cc.expect`. |
| `peripheral(shell, index, text, previous)` | — | — | Delegates to `cc.completion.peripheral(text)`. |
| `side(shell, index, text, previous)` | — | — | Delegates to `cc.completion.side(text)`. |
| `setting(shell, index, text, previous)` | — | — | Delegates to `cc.completion.setting(text)`. |
| `command(shell, index, text, previous)` | — | — | Delegates to `cc.completion.command(text)`. |
| `build(...)` | — | — | **Factory** — takes per-argument specs (nil / function / `{fn}` / `{factory, arg…}`), resolves factory specs once at call time, returns a dispatch function. The dispatch normalises index to 1 before calling the stored completion function so all single-argument helpers work correctly inside `build`. |

**Tests**: `src/test/java/dan200/computercraft/core/lua/CcShellCompletionTest.java` — 63 cases, all green.

---

### 20. ~~`commands` — Method parity gaps~~ ✅ Done (2026-04-14)

| Method / Feature | Notes |
|---|---|
| `exec(command)` 3rd return value | ✅ `doCommand` now returns `(boolean success, { string } output, number affected)`. `affected` is the raw integer returned by `ICommandManager.executeCommand`. All three return paths (success, caught exception, disabled server) consistently return three values. |
| `list([prefix...])` prefix filter | ✅ Accepts zero or more string arguments. When at least one is provided, only commands whose name starts with any of the given prefixes are included. Non-string arguments are silently ignored (consistent with CC:Tweaked leniency). |
| `getBlockInfo(x, y, z [, dimension])` — `state` table | ✅ The returned table now includes a `state` sub-table with key `metadata` (the raw 1.7.10 block metadata). The old `metadata` top-level key is removed; callers use `result.state.metadata`. |
| `getBlockInfo` — `nbt` field | ✅ If a tile entity exists at the coordinates, its NBT is serialized via `TileEntity.writeToNBT` and converted to a Lua table using the new `NBTUtil.toObject`. Numeric types become `double`; strings stay as-is; lists and arrays become 1-indexed tables; nested compounds become nested maps. |
| `getBlockInfo` — optional `dimension` argument | ✅ 4th argument (integer) selects the target world via `MinecraftServer.getServer().worldServerForDimension(dim)`. Omitting it uses the command computer's own world. Throws `"No server available"` / `"Unknown dimension"` on lookup failure. |
| `getBlockInfos(minX, minY, minZ, maxX, maxY, maxZ [, dimension])` | ✅ New method (case 5). Validates 6 coordinate arguments; normalises min/max so either corner-order is accepted. Throws `"Too many blocks"` if the region volume exceeds 4096 (matching CC:Tweaked's cap). Returns a 1-indexed array of block-info tables (same shape as `getBlockInfo`). Y-coordinates outside the world's height range return an `"minecraft:air"` entry with no `nbt` key. Optional 7th dimension argument uses same lookup as `getBlockInfo`. |
| `NBTUtil.toObject(NBTTagCompound)` | ✅ New public utility method in `NBTUtil`. Recursively converts an arbitrary `NBTTagCompound` to a `Map<Object,Object>`. Handles all standard NBT types; list element types without a public MCP getter (byte, short, int, long) are silently omitted. |

**Tests**: `src/test/java/dan200/computercraft/shared/computer/apis/CommandAPITest.java` — **30 cases**, all green.

---

### 21. ~~`redstone_relay` peripheral~~ ✅ Done (2026-04-14)

New standalone block that exposes the full redstone API surface as an `IPeripheral`, letting
computers interact with redstone through wired or wireless modems without the computer block
itself needing to be adjacent to the circuit.

**Reference**: [tweaked.cc/peripheral/redstone_relay](https://tweaked.cc/peripheral/redstone_relay.html)
and upstream [`RedstoneMethods.java`](https://github.com/cc-tweaked/CC-Tweaked/blob/db32ddfec5e8c2bdefb3232b471328a3e92cc43f/projects/core/src/main/java/dan200/computercraft/core/apis/RedstoneMethods.java).

#### Peripheral methods (same surface as `redstone` / `rs` global API)

| Method | Notes |
|---|---|
| `getSides()` | Returns a table of the six side-name strings. |
| `setOutput(side, bool)` | Digital write: sets the specified side to signal strength 15 (true) or 0 (false). |
| `getOutput(side)` | Returns `true` if the current output on `side` is > 0. |
| `getInput(side)` | Returns `true` if the current redstone input on `side` is > 0. |
| `setBundledOutput(side, number)` | Sets the 16-bit bundled-cable output mask on `side`. |
| `getBundledOutput(side)` | Returns the current bundled output mask on `side`. |
| `getBundledInput(side)` | Returns the current bundled input mask on `side`. |
| `testBundledInput(side, mask)` | Returns `true` if all bits of `mask` are set in the bundled input on `side`. |
| `setAnalogOutput(side, level)` / `setAnalogueOutput` | Sets analog output (0–15) on `side`. British alias shares the same case body. |
| `getAnalogOutput(side)` / `getAnalogueOutput` | Returns the analog output level (0–15) on `side`. |
| `getAnalogInput(side)` / `getAnalogueInput` | Returns the analog input level (0–15) on `side`. |

Sides are in local coordinates (relative to the relay's facing direction): `"bottom"`, `"top"`,
`"back"`, `"front"`, `"right"`, `"left"`. This mirrors the computer-block `redstone` API exactly.

Input changes (detected on neighbor-change and processed on the next server tick) fire a
`"redstone"` event on all computers attached to the relay via modem.

Output state is persisted in NBT (`"output"` and `"bundledOutput"` int arrays). Direction is
stored in block metadata (2–5) and propagated to the client via `writeDescription`.

Recipe: 4 × stone (corners) + 4 × redstone dust (cardinal sides) + 1 × wired modem (centre),
shaped 3×3 (`S R S / R W R / S R S` where S=stone, R=redstone, W=wired modem).

**Textures**: four PNGs already present under `assets/computercraft/textures/blocks/`:
`redstoneRelayTop.png`, `redstoneRelayBottom.png`, `redstoneRelayFront.png`,
`redstoneRelaySide.png`.

**Tests**: `src/test/java/dan200/computercraft/shared/peripheral/redstone/RedstoneRelayPeripheralTest.java` — 21 cases, all green.

---

CC:Tweaked adds a `speaker` peripheral (no equivalent in 1.7.10 base):
- `speaker.playNote(instrument, volume, pitch)`
- `speaker.playSound(id, volume, pitch)`
- `speaker.playAudio(chunk, volume)` *(newer CC:Tweaked)*
- `speaker.stop()`

**Implementation**: Requires a new `TileSpeaker` block + `SpeakerPeripheral.java` wired to
Minecraft's sound engine. Significant scope — flag as out-of-scope until explicitly requested.

---

### 10. ~~`shell` — Missing CC:Tweaked additions~~ ✅ Done

| Feature | Notes |
|---|---|
| `shell.execute(_sCommand, ...)` | ✅ New CC:Tweaked 1.87.0 method. Resolves the program path, runs it, and returns `true` on success or `false, "No such program"` on failure. Does **not** call `printError` — the caller is responsible for error display. |
| `shell.run(...)` | ✅ Now delegates to `execute`; tokenises its arguments, calls `execute`, and forwards any error string to `printError`. Returns `false` for empty input. |
| Per-program environment isolation | ✅ Each `execute` call creates a fresh `tRunEnv = setmetatable({}, {__index = tEnv})` table. Globals written in one run do not bleed into the next. The base `tEnv` (containing `shell`, `multishell`) is still reachable via `__index`. |
| `arg` table injection | ✅ CC:Tweaked 1.83.0. `env.arg` is set to `{[0] = resolvedPath, ...args}` before each `os.run` call so programs can inspect their own path and arguments. |
| `shell.resolveProgram` `.lua` fallback | ✅ When a bare program name is not found on the path, `resolveProgram` also tries the name with a `.lua` suffix appended. The bare name is preferred when both exist. |

**Tests**: `src/test/java/dan200/computercraft/core/lua/ShellAPITest.java` — 37 cases, all green.

---

### 11. ~~`window` — Missing 3 methods~~ ✅ Done

| Method | Notes |
|---|---|
| `window.getLine(y)` | ✅ Returns `text, textColor, backgroundColor` strings for line `y` (1-indexed). Throws if `y` is not a number or is out of the window's height range. |
| `window.isVisible()` | ✅ Returns the current boolean visibility state. Counterpart to the existing `setVisible`. |
| `window.reposition(nX, nY [, nW, nH [, newParent]])` | ✅ Optional 5th argument `newParent` swaps the window's underlying parent terminal surface. Throws if `newParent` is non-nil and non-table. Passing `nil` explicitly is a no-op for the parent. |

**Tests**: `src/test/java/dan200/computercraft/core/lua/WindowAPITest.java` — 15 cases, all green.

---

### 12. ~~`help` — Extension-aware lookup~~ ✅ Done

| Change | Notes |
|---|---|
| `lookup` extension search | ✅ Now checks `<topic>` (exact), then `<topic>.md`, then `<topic>.txt`, returning the first match. Priority is exact > `.md` > `.txt`. |
| `topics` extension stripping | ✅ Strips `.md` and `.txt` suffixes before inserting into the result set. Duplicate topic names (e.g. both `intro.md` and `intro.txt` on disk) appear only once. Extension-less files are still included unchanged. |

**Tests**: `src/test/java/dan200/computercraft/core/lua/HelpAPITest.java` — 16 cases, all green.

---

### 13. ~~`paintutils` — Missing `parseImage`~~ ✅ Done

| Method | Notes |
|---|---|
| `paintutils.parseImage(s)` | ✅ New CC:Tweaked function. Parses an NFP-formatted string into a `{{color, ...}, ...}` pixel table. Each hex character maps to the corresponding `2^n` color value; spaces map to `0` (transparent). `loadImage` now delegates to `parseImage` internally. Throws if the argument is not a string. |

**Tests**: `src/test/java/dan200/computercraft/core/lua/PaintutilsAPITest.java` — 29 cases, all green.

---

### 14. ~~`bios.lua` `require` sentinel bug~~ ✅ Fixed

**Problem**: When `package.loaded[name]` was set during module loading, the sentinel value was
`true`. Any module that returned `nil` would cause the slot to be overwritten with `true`, and
any circular `require` would hand the caller a boolean instead of a usable value.

**Fix**: The sentinel is now an empty table `{}` assigned before the module body runs. If the
module returns `nil`, the sentinel table is kept. Circular requires receive the (empty) sentinel
table rather than `true`.

```lua
local loading = {}
package.loaded[_name] = loading
-- ...
package.loaded[_name] = (result ~= nil) and result or loading
```

**Tests**: `src/test/java/dan200/computercraft/core/lua/BiosRequireTest.java` — 4 cases, all green.

---

### 15. ~~`cc.expect` `table.pack.n` compatibility bug~~ ✅ Fixed

**Problem**: `get_type_names` in `cc/expect.lua` iterated over its type-list argument using
`table.pack` and then indexed `.n` to get the count. The `table.pack` polyfill installed by
`bios.lua` does not include the `n` field (Lua 5.1 limitation), so `types.n` was `nil` and
iteration over multiple allowed types would silently truncate or error.

**Fix**: `get_type_names` now uses `select("#", ...)` to count varargs and `select(i, ...)` to
retrieve each one, with no dependency on `table.pack.n`.

**Tests**: `src/test/java/dan200/computercraft/core/lua/CcExpectTest.java` — 6 cases, all green.

---

### 16. ~~`rednet` — Test coverage~~ ✅ Done

No functional changes to the `rednet` Lua API. A full unit-test suite has been added covering:
`open`/`close`/`isOpen` (with and without modems), `send` (loopback, open modem, no modem),
`broadcast`, `host`/`unhost`/`lookup` (including reserved-hostname and wrong-type errors).

**Tests**: `src/test/java/dan200/computercraft/core/lua/RednetAPITest.java` — 26 cases, all green.

---

### 17. ~~`peripheral` — `getType` wrapped-table support + test coverage~~ ✅ Done

**Change**: `peripheral.getType` now accepts either a string side name or a wrapped peripheral
table (looked up via the weak-keyed `wrapNames` registry, the same mechanism used by `getName`
and `hasType`). Passing a plain table that was not created by `wrap` throws
`"value is not a peripheral"`. Passing a non-string/non-table throws `"Expected string or table"`.

This completes the CC:Tweaked contract for `getType`, `getName`, and `hasType` — all three now
consistently accept both string sides and wrapped tables.

**Tests**: `src/test/java/dan200/computercraft/core/lua/PeripheralAPITest.java` — 18 cases, all green.

---

### 18. ~~`_G` — `_HOST`, `_CC_DEFAULT_SETTINGS`, `read` default param~~ ✅ Done

| Feature | Notes |
|---|---|
| `_HOST` | ✅ Set by `CobaltMachine` via the existing `getHostString()` reflection hook on `IComputerEnvironment`. `IComputerEnvironment` now declares `default String getHostString()` (returns `""` so anonymous test stubs compile unchanged). `ServerComputer` overrides it to return `"ComputerCraft <version> (Minecraft 1.7.10)"`. |
| `_CC_DEFAULT_SETTINGS` | ✅ A new `public static String cc_default_settings = ""` field in `ComputerCraft.java`, loaded from the `"cc_default_settings"` config key. `CobaltMachine` sets `_CC_DEFAULT_SETTINGS` as a Lua global when the field is non-empty. `bios.lua` processes the global at boot (before `settings.load(".settings")`) by iterating comma-separated `key=value` pairs and calling `settings.set`. `textutils.unserialise` is used to deserialise each value; falls back to the raw string on failure. |
| `read(replaceChar?, history?, completeFn?, default?)` | ✅ Added optional 4th argument `_sDefault`. When non-nil, `sLine` is pre-populated with `tostring(_sDefault)` and `nPos` is set to `string.len(sLine)` before the input loop begins. The cursor is therefore placed at the end of the default text, and the user can edit or accept it immediately. |

**Tests**: `src/test/java/dan200/computercraft/core/lua/GlobalsTest.java` — 7 cases, all green.

---

### 19. ~~`textutils` — `serialize` opts + `serializeJSON` opts + `unserializeJSON` opts~~ ✅ Done

| Feature | Notes |
|---|---|
| `textutils.serialize(t, opts?)` | ✅ `opts.compact` (boolean) — when `true`, emits no indentation, newlines, or spaces around `=`/`,`. Array output: `{1,2,3,}`. Key-value output: `{key=value,}`. Nested tables collapse to a single line. `opts.allow_repetitions` (boolean) — when `true`, the duplicate-table guard is relaxed: after a table is fully serialised its tracking entry is cleared (`tTracking[t] = nil`), so the same table may appear in multiple sibling positions. True cycles (a table that contains itself at any depth) still always error. |
| `textutils.serializeJSON(t, opts?)` | ✅ `opts.unicode_strings` (boolean, default `false`) — when `true`, all bytes ≥ 0x80 in string values are escaped as `\uXXXX`, producing pure-ASCII JSON output. ASCII control characters (`\b`, `\f`, `\n`, `\r`, `\t`, and `\0`–`\31`) are always escaped regardless of this flag. `opts.allow_repetitions` (boolean, default `false`) — when `true`, the same table reference may appear in multiple sibling positions without error; true self-referential cycles still always error. British alias `serialiseJSON` is provided. |
| `textutils.unserializeJSON(s, opts?)` | ✅ `opts.parse_empty_array` (boolean, default `true`) — when `true` (or omitted), an empty JSON array `[]` returns the `empty_json_array` sentinel, matching the CC:Tweaked round-trip contract. When `false`, `[]` returns a fresh empty table `{}`. Non-empty arrays are unaffected. Existing `opts.null` handling is unchanged. British alias `unserialiseJSON` is provided. |

**Tests**: `src/test/java/dan200/computercraft/core/lua/TextUtilsSerializeTest.java` — 14 cases, all green.
**Tests**: `src/test/java/dan200/computercraft/core/lua/TextUtilsJsonTest.java` — 42 cases, all green.

---

## Prioritized Implementation Roadmap

| Priority | Item                                                            | Effort | Type |
|---|-----------------------------------------------------------------|---|---|
| ✅ Done | `settings` API                                                  | Small | Lua |
| ✅ Done | `colors.packRGB/unpackRGB/toBlit/fromBlit`                      | Small | Lua |
| ✅ Done | `os.epoch` / `os.date`                                          | Small | Java |
| ✅ Done | `fs.attributes` / `fs.getCapacity`                              | Medium | Java |
| ✅ Done | `cc.completion` module                                          | Small | Lua |
| ✅ Done | `cc.audio.dfpwm` module                                         | Small | Lua |
| ✅ Done | `cc.shell.completion` module (`file`, `dir`, `dirOrFile`, `program`, `programWithArgs`, `help`, `choice`, `peripheral`, `side`, `setting`, `command`, `build`) | Small | Lua |
| ✅ Done | `cc.strings` module                                             | Small | Lua |
| ✅ Done | `term.setPaletteColor/getPaletteColor/nativePaletteColor`       | Large | Java + Client |
| ✅ Done | HTTP WebSocket support                                          | Large | Java + Lua |
| ✅ Done | `shell.execute` / per-program env isolation / `arg` table       | Medium | Lua |
| ✅ Done | `window.getLine` / `window.isVisible` / `reposition` newParent  | Small | Lua |
| ✅ Done | `help` `.md`/`.txt` lookup + `topics` extension stripping       | Small | Lua |
| ✅ Done | `paintutils.parseImage`                                         | Small | Lua |
| ✅ Done | `bios.lua` `require` sentinel table fix                         | Small | Lua |
| ✅ Done | `cc.expect` `table.pack.n` compatibility fix                    | Small | Lua |
| ✅ Done | `peripheral.getType` wrapped-table support                      | Small | Lua |
| ✅ Done | `_HOST`, `_CC_DEFAULT_SETTINGS`, `read` default param           | Small | Java + Lua |
| ✅ Done | `textutils.serialize` opts + `serializeJSON` opts + `unserializeJSON` opts | Small | Lua |
| ✅ Done | `commands` method parity (`exec` affected count, `list` prefix filter, `getBlockInfo` state/nbt/dimension, `getBlockInfos`) | Medium | Java |
| ✅ Done | `redstone_relay` peripheral                                     | Medium | Java |
| Deferred | Speaker peripheral                                              | Large | Java + Client |

---

## Further Considerations

1. **`bit32` vs `bit`**: This fork exposes `bit` (LuaJ/BitAPI) and shims `bit32` in `bios.lua`.
   CC:Tweaked on Lua 5.2+ dropped `bit` entirely. For 1.7.10/Lua 5.1 compatibility the current
   approach is correct — no change needed.

2. **Speaker**: Requires new async Java infrastructure (`TileSpeaker` + `SpeakerPeripheral`) wired
   to Minecraft's sound engine with no clear 1.7.10 equivalent. Mark as out-of-scope unless
   explicitly requested.
