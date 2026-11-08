# ROM parity gaps against CC:Tweaked 1.120.2

> Audited 2026-11-07 against `migrate/CC-Tweaked-1.20.1-1.120.2/projects/core/src/main/resources/data/computercraft/lua/`
> (the vendored upstream reference in this repo). Line numbers are for the tree at that
> commit; the local side is `src/main/resources/assets/computercraft/lua/`.
>
> Scope: the *ROM* (Lua) layer only — `bios.lua`, `rom/startup`, `rom/programs/*`,
> `rom/modules/*`, `rom/apis/*`. The Java API surface is covered by
> [`TWEAKEDCC_COVERAGE.md`](TWEAKEDCC_COVERAGE.md) and the batch history by
> [`PARITY_BATCH_LOG.md`](PARITY_BATCH_LOG.md).
>
> **Note on ROM naming.** This fork ships `rom/programs/<name>` **without** the `.lua`
> extension, and programs are found through `shell.resolveProgram`. Upstream ships
> `<name>.lua`. That difference is intentional and is *not* counted as a gap — but it is
> what made gap 4 (below) and the `startup.lua` regression possible, so it is worth
> keeping in mind when comparing paths.

---

## Summary

| # | Gap | Area | Impact |
|---|---|---|---|
| 1 | `bios.use_multishell` defined but never read | BIOS | Setting has no effect |
| 2 | `lua.warn_against_use_of_local` defined but never read | Lua REPL | Warning never printed |
| 3 | `paint.default_extension` defined but never read | `paint` | `paint foo` writes `foo`, not `foo.nfp` |
| 4 | Multishell tab title keeps `.lua` | `shell` | Cosmetic |
| 5 | `rename` is the unported 1.75 original | `rename` | Validation lost; errors are tracebacks |
| 6 | `cc/internal/menu.lua` was never shipped | `edit`, `paint` | No mouse-driven menus |
| 7 | `arg[0]` is the resolved path, not the command | `shell` | `Usage: rom/programs/copy …` |
| 8 | `id` does not report audio discs | `id` | Missing output |
| 9 | Editor QoL deltas (indent, backspace, numpad, status) | `edit` | See the matrix below |
| 10 | Minor: hashbang depth `8` vs `100`; no `expect()` in `shell.execute` | `shell` | Edge cases only |

Already fixed (kept here for context): **`startup.lua` never ran on boot** — `shell.resolveProgram`
only tried the `.lua` fallback on the `PATH` branch, not for absolute paths.
Fixed in PR #30.

---

## 1. `bios.use_multishell` is dead

`bios.lua:1120` defines the setting (inside `if term.isColour()`), but the shell launcher
never reads it:

```lua
-- bios.lua:1169-1177 (local)
parallel.waitForAny(
    function()
        if term.isColour() then
            os.run( {}, "rom/programs/advanced/multishell" )
        else
            os.run( {}, "rom/programs/shell" )
        end
        os.run( {}, "rom/programs/shutdown" )
```

Upstream consults the setting (`bios.lua:733-738`):

```lua
local sShell
if term.isColour() and settings.get("bios.use_multishell") then
    sShell = "rom/programs/advanced/multishell.lua"
else
    sShell = "rom/programs/shell.lua"
end
```

**Effect:** `set bios.use_multishell false` (or `cc_default_settings=bios.use_multishell=false`)
does nothing. Users who want the single-shell experience cannot get it short of removing the
program from the ROM.

**Fix:** read the setting at launch. The BIOS loads `.settings` *before* the launcher
(`bios.lua:1165`), so the value is available; `settings.load` already tolerates a missing
file, so no `fs.exists` guard is needed.

---

## 2. `lua.warn_against_use_of_local` is dead

`rom/programs/lua` reads only three settings:

```
53:        if settings.get("lua.autocomplete", true) then
123:                    function_args = settings.get("lua.function_args"),
124:                    function_source = settings.get("lua.function_source"),
```

Upstream prints a warning when the input starts with `local`, because a local declared at the
REPL prompt is gone on the next prompt:

```lua
if settings.get("lua.warn_against_use_of_local") and input:match("^%s*local%s+") then
    print("To access local variables in later inputs, remove the local keyword.")
end
```

**Effect:** `local x = 1` followed by `x` silently produces `nil`, with no hint why.

**Fix:** add the check after `readChunk()` returns, before `load`. Keep the yellow colouring used
by the rest of the REPL header.

---

## 3. `paint.default_extension` is dead

`rom/programs/fun/advanced/paint` contains **no** `settings.get` calls at all. Upstream appends
the extension for a new, extensionless path:

```lua
-- paint.lua:54-59
if not fs.exists(sPath) and not string.find(sPath, "%.") then
    local sExtension = settings.get("paint.default_extension")
    if sExtension ~= "" and type(sExtension) == "string" then
        sPath = sPath .. "." .. sExtension
    end
end
```

(`edit` *does* implement the equivalent block, at `rom/programs/edit:17-23`.)

**Effect:** `paint screenshot` writes a file called `screenshot`; upstream writes
`screenshot.nfp`.

**Fix:** copy the block after the `Cannot edit a directory` check, before `load(sPath)`.

---

## 4. Multishell tab title keeps the `.lua` extension

`rom/programs/shell:74` and `:82` set the tab title from the raw file name:

```lua
multishell.setTitle( multishell.getCurrent(), fs.getName( sPath ) )
```

Upstream strips a trailing `.lua` in both places (`shell.lua:219-223`, `:231-235`):

```lua
local sTitle = fs.getName(sPath)
if sTitle:sub(-4) == ".lua" then sTitle = sTitle:sub(1, -5) end
multishell.setTitle(multishell.getCurrent(), sTitle)
```

**Effect:** a program saved as `startup.lua` shows as `startup.lua` in the tab bar. Cosmetic only —
`multishell.launchProcess` uses the raw name in *both* versions, so this is the only title path
that differs.

---

## 5. `rename` is the unported 1.75 original

`rom/programs/rename` is nine lines with no validation:

```lua
local sSource = shell.resolve( tArgs[1] )
local sDest = shell.resolve( tArgs[2] )
fs.move( sSource, sDest )
```

Upstream (`rename.lua:19-31`) checks all of these first:

| Check | Upstream message |
|---|---|
| source missing | `No matching files` |
| source is a mount root | `Can't rename mounts` |
| source read-only | `Source is read-only` |
| destination exists | `Destination exists` |
| destination read-only | `Destination is read-only` |

`FileSystem.move` (Java) still refuses missing sources, existing destinations, read-only paths
and self-contained moves — so nothing is lost — but it does so by throwing
`FileSystemException`, which `FSAPI` converts to `LuaException`. In the shell that surfaces as a
red traceback (`File exists`) rather than a one-line message.

The `fs.isDriveRoot` guard has **no** Java equivalent, so renaming a mount root is attempted
(`move` → `copy` + `delete` on the drive root).

**Fix:** port the upstream validation block. `fs.isDriveRoot`, `fs.isReadOnly` and `fs.exists`
are all available.

---

## 6. `cc/internal/menu.lua` was never shipped

`docs/PARITY_BATCH_LOG.md:531` lists it as planned ("`import` for `cc.pretty`-era programs,
`menu`/`edit_runner` for Batch I"). `edit_runner` landed; `menu` did not. `rom/modules/main/cc/internal/`
has no `menu.lua`, and nothing references `cc.internal.menu`.

Both `edit` and `paint` therefore keep bespoke menus, which costs the following (upstream gets
them from `menu.handle_event`):

* **Click the footer/status bar to open the menu** — upstream `edit.lua:746-748`
  (`if cy < h then … else current_menu = menu.create(menu_items)`),
  `paint.lua:377-379`.
* **Click a menu item to choose it** — upstream `menu.lua:107-118` walks the drawn item ranges.
* **Click anywhere else to dismiss the menu** — upstream `menu.lua:111` (`return false`).
* **Numpad Enter confirms a menu item** — upstream `menu.lua:94`
  (`key == keys.enter or key == keys.numPadEnter`).

Locally, mouse clicks while the menu is open do nothing (`edit:856`,`paint:334`), and clicking
the status bar row does nothing at all.

**Fix:** ship `cc/internal/menu.lua` (CCPL, already in the reference tree), then replace the
bespoke menu loops in `edit` and `paint` with `menu.create` / `menu.draw` / `menu.handle_event`.
Both programs already track an item list and a selected index, so the change is mechanical.

---

## 7. `arg[0]` is the resolved path, not the command

`rom/programs/shell:77` sets:

```lua
local tArgs = { ... }
tArgs[0] = sPath        -- sPath == shell.resolveProgram(_sCommand), e.g. "rom/programs/copy"
```

Upstream passes the command as typed (`shell.lua:226`):

```lua
local result = executeProgram(100, sPath, { [0] = command, ... })
```

This is the CC 1.83 change ("`arg[0]` now contains the command as typed") that was never ported.

**Effect:** the programs that print usage from `arg[0]` — `clear`, `copy`, `delete`, `mkdir`,
`move`, `fun/speaker`, `http/wget` — all render:

```
Usage: rom/programs/copy <source> <destination>
```

instead of `Usage: copy <source> <destination>`. (`cd`, `edit`, `paint`, `eject` and `alias` were
hand-edited during the port to hardcode their own names, which is why they are unaffected.)

**Fix:** set `tArgs[0]` to the command argument of `run()`, not the resolved path. Note the tests
in `ShellAPITest` currently pin the old behaviour
(`executeRunsProgramAtResolvedPath` asserts `_prog_arg0 == "rom/programs/id"`), so they need
updating with it.

---

## 8. `id` does not report audio discs

`rom/programs/id` handles only data disks. Upstream (`id.lua:19-30`) also covers:

```lua
if disk.hasAudio(sDrive) then
    local title = disk.getAudioTitle(sDrive)
    if title then print("Has audio track \"" .. title .. "\"")
    else print("Has untitled audio") end
    return
end
...
local id = disk.getID(sDrive)
if id then print("The disk is #" .. id)
else print("Non-disk data source") end
```

Local always prints `The disk is #N` (would show `nil` for a non-disk data source) and says
`No disk in drive X` for an audio disc.

**Fix:** port the two branches.

---

## 9. Editor QoL matrix (`rom/programs/edit` vs `edit.lua`)

Local `edit` is **ahead** of upstream on: common-prefix completion acceptance, menu letter
hotkeys, Alt-to-open-menu, and it already has the lexer-driven syntax highlighting. What it is
missing:

| QoL | Upstream | Local |
|---|---|---|
| Click the status bar to open the menu | `edit.lua:746-748` | nothing (`edit:856+`) |
| Click a menu item to pick it | `menu.lua:107-118` | nothing while `bMenu` |
| Click elsewhere closes the menu | `menu.lua:111` | nothing |
| Numpad Enter confirms a menu item | `menu.lua:94` | `keys.enter` only |
| Numpad Enter inserts a newline | `edit.lua:694` | **not handled** (0 occurrences) |
| Indent width | 4 spaces, `edit.lua:597` | **2 spaces**, `edit:665` |
| Backspace removes a whole indent level in leading whitespace | `edit.lua:677-681` | one character, `edit:778-796` |
| Errors drawn in red | `set_status(text, ok)` + `errorColour` | single yellow status, `edit:379` |
| `File is read only` on open | `edit.lua:74-75` | status still says "Press Ctrl…" |
| `Disk is low on space` on open (`fs.getFreeSpace(sPath) < 1024`) | `edit.lua:76-77` | no check |
| Save failure shows the reason | `"Error saving: " .. fileerr` | `"Error saving to " .. path` |
| Paste while the menu is open | closes menu, then pastes, `edit.lua:722-727` | silently dropped, `edit:848` |
| Usage message | `arg[0]` (`edit`/`paint`) | hardcoded `"edit"` |

`paint` has the same menu gap plus no `default_extension` (gap 3); its `accessMenu` pulls
`os.pullEvent("key")` only, and has no `term_resize` handling inside the menu.

**Highest-value fix:** the two editing-behaviour items — 4-space indent and backspace dedent.
They change what the user's files look like, and a 2/4-space mismatch makes pasted upstream
examples look broken. The status-colour and open-time warnings are cheap follow-ups.

---

## 10. Minor

* **Hashbang recursion depth.** `shell:79` calls `executeProgram(8, …)`; upstream uses `100`.
  Eight nested `#!` interpreters is unrealistic, so this is cosmetic.
* **`shell.execute` has no `expect()`.** Upstream validates that the command and every argument
  is a string and errors with a typed message; locally a non-string reaches `tokenise` and fails
  with whatever `string.gmatch` raises.

---

## Verified as clean (for the next audit)

These were checked and **do not** differ; do not re-audit them without cause.

* **API surfaces.** Every `function <api>.<method>` in `rom/apis/*` matches upstream, including
  `keys`, `colors`/`colours`, `textutils`, `rednet`, `paintutils`, `window`, `vector`,
  `parallel`, `disk`. Local `keys` is a superset.
* **File inventory.** Every upstream program and module exists locally except
  `cc/internal/menu.lua` (gap 6). `fs` and `http/http` have no local file because those
  surfaces live in `FSAPI.java` / `bios.lua`.
* **Settings inventory.** All 16 settings upstream defines are defined locally; the other 13
  (outside gaps 1-3) all have live consumers.
* **Programs verified equivalent:** `cd`, `drive`, `eject`, `label`, `time`, `type`, `monitor`
  (including `scale`), `gps`, `pastebin` (put/get/run), `multishell` (all seven methods),
  `copy`, `move`, `delete`, `mkdir` (these four already carry upstream's validation).
* **`settings.load` on a missing `.settings`** already returns `true`, so the unconditional call
  in `bios.lua` is fine even though upstream guards it with `fs.exists`.
