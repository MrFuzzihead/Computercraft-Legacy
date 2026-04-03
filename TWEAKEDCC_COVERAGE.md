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
| `term` | `TermAPI.java` + `rom/apis/term` | `write`, `blit`, `scroll`, `clear`, `clearLine`, `setCursorPos`, `getCursorPos`, `setCursorBlink`, **`getCursorBlink`**, `getSize`, `setTextColor/Colour`, `setBackgroundColor/Colour`, `getTextColor/Colour`, `getBackgroundColor/Colour`, `isColor/Colour`, `redirect`, `current`, `native`, **`nativePaletteColor/Colour`**, **`setPaletteColor/Colour`**, **`getPaletteColor/Colour`** |
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

### 4. `fs` — ~~Missing 2 methods~~ ✅ Done

| Method | Notes |
|---|---|
| `fs.attributes(path)` | ✅ Implemented in `FSAPI.java` (method index 17); returns `{size, isDir, isReadOnly, created, modified, modification}`. Timestamps use `java.nio.file.attribute.BasicFileAttributes` on `FileMount` paths (falls back to `File.lastModified()`); read-only mounts return `0`. |
| `fs.capacity(path)` | ✅ Implemented in `FSAPI.java` (method index 16); returns total drive capacity in bytes, or `nil` for read-only / unlimited mounts. `FileMount` overrides `IWritableMount.getCapacity()` to expose `m_capacity`; read-only `IMount` implementations inherit the default `-1` sentinel which is translated to `nil` in Lua. |

**Tests**: `src/test/java/dan200/computercraft/core/apis/FSAPITest.java` — 10 cases, all green.

---

### 5. `http` — ~~Missing response method gaps~~ ✅ Done (WebSocket still pending)

| Feature | Notes |
|---|---|
| `Response.getResponseHeaders()` | ✅ Returns `{ [string]: string }` — multiple values for the same header are joined with `", "`, and the HTTP status-line pseudo-header (null key from `HttpURLConnection`) is filtered out. |
| `Response.readLine([withTrailingNewline])` | ✅ Optional boolean parameter added. When `true`, the terminator bytes (`\n`, `\r`, or `\r\n`) are included in the returned string. |
| `Response.read([count])` | ✅ Now returns a Lua string (`byte[]`) instead of a raw number, and accepts an optional `count` to read multiple bytes at once. |
| `Response.readAll()` | ✅ Already correct — returns the remaining body as a string. |
| `Response.close()` | ✅ Already correct. |
| `Response.getResponseCode()` | ✅ Already correct. |
| `http.websocket(url, headers)` | Requires a new async handler class — significant scope; deferred. |
| `http.websocketAsync(url, headers)` | Same dependency as above. |
| `http.checkURLAsync(url)` | ✅ Implemented in `bios.lua` — calls the native synchronous `http.checkURL` (whitelist + format check) and queues `check_url_success(url)` or `check_url_failure(url, err)`. No Java changes needed since the check is already instant. |

**Tests**: `src/test/java/dan200/computercraft/core/apis/HTTPResponseTest.java` — 17 cases, all green.

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
| ✅ Done | `fs.attributes` / `fs.capacity` | Medium | Java |
| ✅ Done | `term.getCursorBlink` | Trivial | Java |
| ✅ Done | `http` response `getResponseHeaders()` + `read`/`readLine` gaps | Small | Java |
| ✅ Done | `cc.expect` module | Small | Lua |
| ✅ Done | `cc.completion` module | Small | Lua |
| ✅ Done | `cc.strings` module | Small | Lua |
| ✅ Done | `cc.pretty` module | Medium | Lua |
| ✅ Done | `cc.image.nft` module | Small | Lua |
| ✅ Done | `pocket` API methods | Medium | Java |
| ✅ Done | `http.checkURLAsync` | Trivial | Lua |
| ✅ Done | `term.setPaletteColor/getPaletteColor/nativePaletteColor` | Large | Java + Client |
| 🔵 Deferred | HTTP WebSocket support | Large | Java |
| 🔵 Deferred | Speaker peripheral | Large | Java + Client |

---

## Further Considerations

1. **`cc.*` module `require` bootstrap**: The `cc.*` modules use `require("cc.expect")` etc. This needs
   a `require` implementation added to `bios.lua` (or the shell) that resolves paths under
   `rom/modules/main/`. Confirm whether `os.loadAPI` compatibility must be preserved alongside it.

2. **Palette support (`setPaletteColor`)**: Full implementation plan documented in `PALETTE_PLAN.md`.
   Touches `Terminal.java`, `TermAPI.java`, `FixedWidthFontRenderer.java`, both rendering call sites,
   and the `window` Lua API.

3. **WebSocket and Speaker**: Both require new async Java infrastructure or sound engine hooks with
   no clear 1.7.10 equivalent. Mark as out-of-scope unless explicitly requested.

4. **`bit32` vs `bit`**: This fork exposes `bit` (LuaJ/BitAPI) and shims `bit32` in `bios.lua`.
   CC:Tweaked on Lua 5.2+ dropped `bit` entirely. For 1.7.10/Lua 5.1 compatibility the current
   approach is correct — no change needed.




