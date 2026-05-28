# Adding a New Lua ROM API to ComputerCraft-Legacy

This guide explains how to add a new Lua-side API that is available in every Lua program automatically. ROM APIs require no Java changes unless they wrap a Java-backed global.

---

## How ROM APIs Are Loaded

`bios.lua` calls `os.loadAPI` on every file inside `rom/apis/` at startup. Each file is executed in its own environment, and its globals are collected into a table named after the **filename**. That table becomes a global in every running Lua program.

> The filename **is** the global name. A file named `myapi` is accessible as `myapi.doThing()`. There is no registry or registration step — placing the file in the correct directory is sufficient.

---

## The Three Archetypes

### Archetype 1 — Thin Alias

Use this when you want to re-export an existing global under a different name (with optional cosmetic changes).

**When:** Adding British/American spelling variants, or renaming a Java-backed global.

**File:** `src/main/resources/assets/computercraft/lua/rom/apis/myalias`

```lua
-- myalias: British-spelling alias for myapi
local myalias = _ENV
for k, v in pairs(myapi) do
    myalias[k] = v
end

-- Override any name differences
myalias.myColour = myapi.myColor
myalias.myColor = nil
```

**Reference:** `rom/apis/colours` (aliases `colors`, swaps `gray`→`grey`).

---

### Archetype 2 — Pure Lua Utility

Use this when adding new functionality built entirely from existing Lua or CC globals, with no Java backing.

**When:** High-level protocols, convenience wrappers around peripherals or events.

**File:** `src/main/resources/assets/computercraft/lua/rom/apis/myapi`

```lua
-- Module-level state: local to this computer, not shared between computers.
local tSessions = {}
local bRunning = false

function open(sTarget)
    if type(sTarget) ~= "string" then
        error("expected string", 2)
    end
    -- use existing globals: peripheral, os, etc.
    peripheral.call(sTarget, "open", os.getComputerID())
end

function receive(nTimeout)
    local timer = nTimeout and os.startTimer(nTimeout) or nil
    while true do
        local event, p1, p2 = os.pullEvent()
        if event == "my_event" then
            return p1, p2
        elseif event == "timer" and p1 == timer then
            return nil
        end
    end
end
```

**Reference:** `rom/apis/rednet` — wraps `peripheral`, `modem`, and `os.queueEvent`; uses local state for deduplication and hostname tables.

Key conventions:
- Use `local` for all module-level state. Each computer has its own environment, so locals are safely isolated.
- Validate arguments with `error("expected ...", 2)` — the `2` points the error at the caller's line, not the API's.
- Use `os.pullEvent` (not `os.pullEventRaw`) unless your API must handle `"terminate"` itself.

---

### Archetype 3 — Wrapper for a Java-Backed Global

Use this when a Java API (registered via `ILuaAPI`) is low-level, and a Lua convenience layer improves usability. The file wraps the Java-injected global by name.

**When:** The Java API exposes raw operations but users need higher-level helpers.

**File:** `src/main/resources/assets/computercraft/lua/rom/apis/myapi`

```lua
-- Wraps the Java-backed "myapi" global with convenience helpers.
-- The Java global is accessible by its getNames() value.

local _native = myapi  -- capture the Java-backed table

function myapi.doThingSafely(arg)
    if type(arg) ~= "string" then
        error("expected string", 2)
    end
    local ok, err = _native.doThing(arg)
    if not ok then
        error(err, 2)
    end
end
```

**Reference:** `rom/apis/colors` extends the Java-backed color constants with pure-Lua `combine`, `subtract`, `test`, `toBlit`, `fromBlit`, and `packRGB`/`unpackRGB` helpers.

---

## Mandatory: `window` Delegation for Term-Surface Methods

> **From `AGENTS.md`:** When adding new term-surface methods to `TermAPI`, add a corresponding delegation in `rom/apis/window`.

`rom/apis/window` wraps a parent terminal surface and must implement every method that a terminal surface can expose. If you add a method to `TermAPI` (Java), you **must** also add it to the `window` table in `rom/apis/window`, otherwise programs using `window.create(...)` will crash when they call your new method.

The delegation pattern is always the same — forward directly to `parent`:

```lua
-- Inside window.create(), after the existing delegations:
function window.myNewMethod(arg1, arg2)
    parent.myNewMethod(arg1, arg2)
end
```

If the method returns values, forward the return too:

```lua
function window.getMyValue()
    return parent.getMyValue()
end
```

**Reference:** `rom/apis/window` lines 342–365 — `setPaletteColor`, `getPaletteColor`, `nativePaletteColor` and their `Colour` variants are all simple `parent.*` delegations.

---

## British / American Spelling Convention

Per `AGENTS.md`: always register both American (`Color`) and British (`Colour`) variants; American first.

For a ROM API this means:

1. Create the primary file with the American spelling: `rom/apis/colors` (or `rom/apis/myapi` if not color-related).
2. Create a thin-alias file for the British variant: `rom/apis/colours` — copy all keys, swap the name differences.

Both `window.setTextColor` and `window.setTextColour` exist and call the same internal function. Follow this pattern for any new term-surface method.

---

## Help Files

Add an in-game help file at:

```
src/main/resources/assets/computercraft/lua/rom/help/myapi
```

Format: one-line summary, then one method signature per line with a short comment. Keep it brief — this is what players see when they type `help myapi` in the shell.

```
My API — brief description of what it does.
myapi.open( target )        -- open a connection to target peripheral
myapi.receive( [timeout] )  -- wait for a message; returns sender, message
myapi.close()               -- close all connections
```

**Reference:** `rom/help/bit` — eight lines total, one per method.

---

## Reference: Existing ROM APIs

| File | Global | Archetype | Wraps |
|---|---|---|---|
| `rom/apis/colors` | `colors` | Java wrapper + pure Lua | Java color constants + Lua helpers |
| `rom/apis/colours` | `colours` | Thin alias | `colors` |
| `rom/apis/rednet` | `rednet` | Pure Lua | `peripheral`, `modem`, `os` |
| `rom/apis/window` | `window` | Pure Lua | Parent terminal surface |
| `rom/apis/keys` | `keys` | Pure Lua | Key-code constants |
| `rom/apis/textutils` | `textutils` | Pure Lua | String/serialization helpers |
| `rom/apis/parallel` | `parallel` | Pure Lua | `coroutine`, `os.pullEventRaw` |
| `rom/apis/term` | `term` | Java wrapper | `TermAPI` |

---

## Checklist for Adding a New ROM API

1. **Choose the archetype** — thin alias, pure Lua utility, or Java wrapper.
2. **Create the file** at `src/main/resources/assets/computercraft/lua/rom/apis/<name>`. The filename becomes the Lua global name.
3. **Add a British-spelling alias file** if the API has color/colour-style variants.
4. **Add `window` delegations** if you added any new term-surface methods to `TermAPI`.
5. **Validate arguments** with `error("expected ...", 2)` at the start of each public function.
6. **Add a help file** at `rom/help/<name>` — one-line summary plus method signatures.
7. **No registration needed** — `bios.lua` picks up all files in `rom/apis/` automatically.
8. **Test** — verify the global is available in a running computer and that `help <name>` displays correctly.

---

*References: `rom/apis/colours` (thin alias), `rom/apis/rednet` (pure Lua with local state and event loop), `rom/apis/colors` (Java wrapper with Lua helpers), `rom/apis/window` (term-surface delegation).*

