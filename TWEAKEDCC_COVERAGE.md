# CC:Tweaked API Coverage Plan

> Audited against [tweaked.cc](https://tweaked.cc/) on 2026-03-30.
> This document tracks what is implemented in this 1.7.10 legacy fork versus the
> CC:Tweaked reference, and provides a prioritized roadmap to close the gaps.

---

## ✅ Currently Implemented

| API / Peripheral | Source | Methods Covered |
|---|---|---|
| `os` | `OSAPI.java` + `bios.lua` | `queueEvent`, `startTimer`, `cancelTimer`, `setAlarm`, `cancelAlarm`, `shutdown`, `reboot`, `computerID`, `getComputerID`, `setComputerLabel`, `computerLabel`, `getComputerLabel`, `clock`, `time`, `day`, `version`, `pullEvent`, `pullEventRaw`, `run`, `loadAPI`, `unloadAPI`, `sleep`, **`epoch`**, **`date`** |
| `fs` | `FSAPI.java` + `bios.lua` | `list`, `combine`, `getName`, `getSize`, `exists`, `isDir`, `isReadOnly`, `makeDir`, `move`, `copy`, `delete`, `open`, `getDrive`, `getFreeSpace`, `find`, `getDir`, `complete` |
| `term` | `TermAPI.java` + `rom/apis/term` | `write`, `blit`, `scroll`, `clear`, `clearLine`, `setCursorPos`, `getCursorPos`, `setCursorBlink`, `getSize`, `setTextColor/Colour`, `setBackgroundColor/Colour`, `getTextColor/Colour`, `getBackgroundColor/Colour`, `isColor/Colour`, `redirect`, `current`, `native` |
| `redstone` / `rs` | `RedstoneAPI.java` | `getSides`, `setOutput`, `getOutput`, `getInput`, `setBundledOutput`, `getBundledOutput`, `getBundledInput`, `testBundledInput`, `setAnalogOutput/Analogue`, `getAnalogOutput/Analogue`, `getAnalogInput/Analogue` |
| `http` | `HTTPAPI.java` + `bios.lua` | `request`, `checkURL`, `get` (sync), `post` (sync); response: `readLine`, `readAll`, `close`, `getResponseCode` |
| `turtle` | `TurtleAPI.java` | All movement, dig, place, drop, suck, detect, compare, attack, fuel, inspect, equip, `getItemDetail` |
| `commands` | `CommandAPI.java` | `exec`, `execAsync`, `list`, `getBlockPosition`, `getBlockInfo` |
| `bit` | `BitAPI.java` | `bnot`, `band`, `bor`, `bxor`, `brshift`, `blshift`, `blogic_rshift` |
| `buffer` | `BufferAPI.java` | `new` |
| `colors` / `colours` | `rom/apis/colors` + `rom/apis/colours` | Constants + `combine`, `subtract`, `test`, **`packRGB`**, **`unpackRGB`**, **`toBlit`**, **`fromBlit`** |
| `rednet` | `rom/apis/rednet` | `open`, `close`, `isOpen`, `send`, `receive`, `broadcast`, `host`, `unhost`, `lookup` |
| `textutils` | `rom/apis/textutils` | `slowWrite`, `slowPrint`, `formatTime`, `pagedPrint`, `tabulate`, `pagedTabulate`, `serialize/ise`, `unserialize/ise`, `serializeJSON/iseJSON`, `unserializeJSON/iseJSON`, `urlEncode`, `complete`, `empty_json_array`, `json_null` |
| `peripheral` | `rom/apis/peripheral` | `getNames`, `isPresent`, `getType`, `getMethods`, `call`, `wrap`, `find` |
| `settings` | `rom/apis/settings` | `define`, `undefine`, `set`, `get`, `unset`, `clear`, `getNames`, `getDetails`, `load`, `save`; auto-loaded from `.settings` at boot via `bios.lua` |
| `gps` | `rom/apis/gps` | `locate` |
| `paintutils` | `rom/apis/paintutils` | `loadImage`, `drawPixel`, `drawLine`, `drawBox`, `drawFilledBox`, `drawImage` |
| `keys` | `rom/apis/keys` | All Minecraft key constants + `getName` |
| `help` | `rom/apis/help` | `path`, `setPath`, `lookup`, `topics`, `completeTopic` |
| `window` | `rom/apis/window` | `create` (full window object with all term-surface methods) |
| `parallel` | `rom/apis/parallel` | `waitForAny`, `waitForAll` |
| `io` | `rom/apis/io` | `close`, `flush`, `input`, `output`, `lines`, `open`, `read`, `write` |
| `vector` | `rom/apis/vector` | `new` + full vector math (`add`, `sub`, `mul`, `div`, `unm`, `dot`, `cross`, `length`, `normalize`, `round`, `tostring`) |
| `disk` | `rom/apis/disk` | `isPresent`, `getLabel`, `setLabel`, `hasData`, `getMountPath`, `hasAudio`, `getAudioTitle`, `playAudio`, `stopAudio`, `eject`, `getID` |
| `multishell` | `rom/programs/advanced/multishell` | `launch`, `getFocus`, `setFocus`, `getTitle`, `setTitle`, `getCurrent`, `getCount` |
| Modem peripheral | `ModemPeripheral.java` | `open`, `isOpen`, `close`, `closeAll`, `transmit`, `isWireless` + cable remote methods (`getNamesRemote`, `isPresentRemote`, `getTypeRemote`, `getMethodsRemote`, `callRemote`) |
| Monitor peripheral | `MonitorPeripheral.java` | Full term-compatible surface + `setTextScale` |
| Printer peripheral | `PrinterPeripheral.java` | `write`, `setCursorPos`, `getCursorPos`, `getPageSize`, `newPage`, `endPage`, `getInkLevel`, `setPageTitle`, `getPaperLevel` |
| Drive peripheral | `DiskDrivePeripheral.java` | `isDiskPresent`, `getDiskLabel`, `setDiskLabel`, `hasData`, `getMountPath`, `hasAudio`, `getAudioTitle`, `playAudio`, `stopAudio`, `ejectDisk`, `getDiskID` |

---

## ❌ Missing / Gaps vs CC:Tweaked

### 1. ~~`settings` API~~ ✅ Done

All ten methods are implemented as a pure-Lua file at `rom/apis/settings`.
Settings are auto-loaded from `.settings` at boot via `bios.lua`.

**Tests**: `src/test/java/dan200/computercraft/core/lua/SettingsAPITest.java` — 33 cases, all green.

---

### 2. `pocket` API — **Empty stub**

`PocketAPI.java` returns zero methods. CC:Tweaked specifies:

- `pocket.equipBack()`
- `pocket.unequipBack()`
- `pocket.isEquipped()`

**Implementation**: Java-side in `PocketAPI.java`; requires hooking into pocket computer item/upgrade logic.

---

### 3. `os` — ~~Missing 2 methods~~ ✅ Done

| Method | Notes |
|---|---|
| `os.epoch(timezone)` | ✅ Implemented in `OSAPI.java` (cases `"utc"`, `"local"`, `"ingame"`; defaults to `"ingame"`) |
| `os.date(format, time)` | ✅ Implemented in `OSAPI.java` (`strftime`-style tokens + `"*t"` table format; leading `"!"` for UTC) |

**Tests**: `src/test/java/dan200/computercraft/core/apis/OSAPITest.java` — 29 cases, all green.

---

### 4. `fs` — **Missing 2 methods**

| Method | Notes |
|---|---|
| `fs.attributes(path)` | Returns `{size, isDir, isReadOnly, created, modified}` |
| `fs.capacity(path)` | Returns total drive capacity in bytes (or `"unlimited"`) |

**Implementation**: Java-side additions to `FSAPI.java`; backing data from `FileSystem` and `FileMount`. The `created`/`modified` timestamps require `java.nio.file` or `File.lastModified()`.

---

### 5. `http` — **Missing WebSocket support, async check, and response headers**

| Feature | Notes |
|---|---|
| `http.websocket(url, headers)` | Synchronous WebSocket open; returns WebSocket handle |
| `http.websocketAsync(url, headers)` | Async; fires `websocket_success` / `websocket_failure` events |
| `http.checkURLAsync(url)` | Async URL validation; fires `check_url_success` / `check_url_failure` |
| Response `getResponseHeaders()` | Missing from the `wrapBufferedReader` response object in `HTTPAPI.java` |

**Implementation**: `getResponseHeaders()` is low-effort (store headers map in `HTTPResponse`). WebSocket requires a new async handler class (e.g. `HTTPWebSocket.java`) using `java.net.http.WebSocket` (JVM 11+, available via Jabel syntax). Evaluate scope before starting.

---

### 6. `term` — **Missing palette and blink-get**

| Method | Notes |
|---|---|
| `term.getCursorBlink()` | Read the current blink state (write-only today) |
| `term.setPaletteColor(color, r, g, b)` | Set one of 16 palette entries to an RGB value |
| `term.getPaletteColor(color)` | Read a palette entry back as `r, g, b` |

**Implementation**: `getCursorBlink()` is trivial — read `Terminal.m_cursorBlink`. Palette support requires `Terminal.java` to carry a `int[16]` RGB array and all client-side rendering to consume it — significant scope; treat as a separate feature.

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

### 8. `cc.*` module system — **Not present**

All absent. These are pure-Lua modules intended to be loaded via `require("cc.module")`.

| Module | Key Functions |
|---|---|
| `cc.completion` | `choice`, `peripheral`, `side`, `file`, `dir`, `program`, `setting` |
| `cc.expect` | `expect(n, val, ...)`, `field(tbl, key, ...)`, `range(val, min, max)` |
| `cc.pretty` | `pretty(val)`, `pretty_print(val)`, `document(...)` |
| `cc.strings` | `wrap(text, width)`, `ensure_width(str, width)`, `split(str, sep)` |
| `cc.image.nft` | `load(path)`, `draw(image, term, x, y)` |

**Implementation**: Pure Lua files placed under `rom/modules/main/cc/`. Requires adding a `require` implementation (or `cc.require`) to the bios/shell bootstrap so the `require` global resolves `rom/modules/main/`.

---

### 9. Speaker peripheral — **Not implemented**

CC:Tweaked adds a `speaker` peripheral (no equivalent in 1.7.10 base):

- `speaker.playNote(instrument, volume, pitch)`
- `speaker.playSound(id, volume, pitch)`
- `speaker.playAudio(chunk, volume)` *(newer CC:Tweaked)*
- `speaker.stop()`

**Implementation**: Requires a new `TileSpeaker` block + `SpeakerPeripheral.java` wired to Minecraft's sound engine. Significant scope — flag as out-of-scope until explicitly requested.

---

## Prioritized Implementation Roadmap

| Priority | Item | Effort | Type |
|---|---|---|---|
| ✅ Done | `settings` API | Small | Lua |
| ✅ Done | `colors.packRGB/unpackRGB/toBlit/fromBlit` | Small | Lua |
| ✅ Done | `os.epoch` / `os.date` | Small | Java |
| 🟡 Medium | `fs.attributes` / `fs.capacity` | Medium | Java |
| 🟡 Medium | `term.getCursorBlink` | Trivial | Java |
| 🟡 Medium | `http` response `getResponseHeaders()` | Small | Java |
| 🟡 Medium | `cc.expect` module | Small | Lua |
| 🟡 Medium | `cc.completion` module | Small | Lua |
| 🟡 Medium | `cc.strings` module | Small | Lua |
| 🟡 Medium | `cc.pretty` module | Medium | Lua |
| 🟡 Medium | `cc.image.nft` module | Small | Lua |
| 🟠 Low | `pocket` API methods | Medium | Java |
| 🟠 Low | `http.checkURLAsync` | Small | Java |
| 🔵 Deferred | `term.setPaletteColor/getPaletteColor` | Large | Java + Client |
| 🔵 Deferred | HTTP WebSocket support | Large | Java |
| 🔵 Deferred | Speaker peripheral | Large | Java + Client |

---

## Further Considerations

1. **`cc.*` module `require` bootstrap**: The `cc.*` modules use `require("cc.expect")` etc. This needs
   a `require` implementation added to `bios.lua` (or the shell) that resolves paths under
   `rom/modules/main/`. Confirm whether `os.loadAPI` compatibility must be preserved alongside it.

2. **Palette support (`setPaletteColor`)**: Requires `Terminal.java` to carry a 16-entry RGB palette
   and all rendering code (client-side `TileMonitor`, computer GUI) to consume it. Worth a dedicated
   planning session before starting.

3. **WebSocket and Speaker**: Both require new async Java infrastructure or sound engine hooks with
   no clear 1.7.10 equivalent. Mark as out-of-scope unless explicitly requested.

4. **`bit32` vs `bit`**: This fork exposes `bit` (LuaJ/BitAPI) and shims `bit32` in `bios.lua`.
   CC:Tweaked on Lua 5.2+ dropped `bit` entirely. For 1.7.10/Lua 5.1 compatibility the current
   approach is correct — no change needed.




