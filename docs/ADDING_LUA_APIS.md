# Adding a New Built-in Lua API to ComputerCraft-Legacy

This guide explains how to add a new Java-backed Lua API (like `bit`, `redstone`, or `http`) that is available in every Lua program as a global table. Use `BitAPI` (stateless) and `OSAPI` (stateful, tick-driven) as reference implementations.

---

## Overview

A Lua API is a Java class that implements `ILuaAPI`. When a computer starts, every registered API is:

1. Instantiated once per `Computer` instance in `createAPIs()`.
2. Exposed to the Lua runtime as one or more global tables (the names returned by `getNames()`).
3. Called on each game tick via `advance(dt)`.
4. Torn down via `shutdown()` when the computer stops or reboots.

**Package:** `dan200.computercraft.core.apis`

---

## Step 1 — Implement `ILuaAPI`

`ILuaAPI` extends `ILuaObject` (which provides `getMethodNames` and `callMethod`) and adds three lifecycle hooks:

```java
package dan200.computercraft.core.apis;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;

public class MyAPI implements ILuaAPI {

    private final IAPIEnvironment m_environment;

    public MyAPI(IAPIEnvironment environment) {
        this.m_environment = environment;
    }

    // --- ILuaAPI lifecycle ---

    /** The global Lua table name(s) this API is bound to. */
    @Override
    public String[] getNames() {
        return new String[] { "myapi" };
    }

    /** Called once when the computer boots (before bios.lua runs). Use for initialization. */
    @Override
    public void startup() {}

    /**
     * Called once per game tick. dt is always 0.05 (one tick = 50 ms).
     * Use for polling, timeouts, or anything that needs to run periodically.
     */
    @Override
    public void advance(double dt) {}

    /** Called when the computer shuts down or reboots. Release any resources here. */
    @Override
    public void shutdown() {}

    // --- ILuaObject methods ---

    /**
     * Declares all Lua-callable methods on this API.
     * The index of each name here is the `method` value passed to callMethod.
     */
    @Override
    public String[] getMethodNames() {
        return new String[] { "doThing", "getCount" };
    }

    /**
     * Dispatches a Lua method call. `method` is the 0-based index into getMethodNames().
     * Return null for void methods; return new Object[]{ value } otherwise.
     */
    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
        switch (method) {
            case 0: { // doThing(name: string)
                if (args.length < 1 || !(args[0] instanceof String)) {
                    throw new LuaException("Expected string");
                }
                String name = (String) args[0];
                // ... do something ...
                return null;
            }
            case 1: // getCount() -> number
                return new Object[] { 42.0 };
            default:
                return null;
        }
    }
}
```

### Argument type mapping

Arguments arrive as Java `Object[]`. Apply the same type rules as events:

| Java type | Lua type |
|---|---|
| `String` | `string` |
| `Double` / `double` | `number` |
| `Boolean` / `boolean` | `boolean` |
| `Map<Object,Object>` | `table` |
| `byte[]` | `string` (binary) |
| `null` | `nil` |

Always validate arguments and throw `LuaException` with a descriptive message on bad input. See `RedstoneAPI.parseSide` for a clean helper-method pattern.

### Using `advance(double dt)` for periodic work

`advance` is called with `dt = 0.05` once per Minecraft tick. Use it to check state and fire events when something changes. Always guard queued events inside the advance check — never fire from `startup` or the constructor.

```java
@Override
public void advance(double dt) {
    if (/* something happened */) {
        m_environment.queueEvent("my_event", new Object[] { "detail" });
    }
}
```

Reference: `OSAPI.advance` decrements timer ticks and fires `"timer"` / `"alarm"` events.

### Name aliasing

`getNames()` can return multiple strings. Each name becomes a separate global Lua table pointing to the same API object. Use this to provide both a short alias and a descriptive full name:

```java
@Override
public String[] getNames() {
    // "rs" is the short alias; "redstone" is the full descriptive name
    return new String[] { "rs", "redstone" };
}
```

The first name in the array is conventionally the primary name. List it first in `getMethodNames()` documentation as well.

Reference: `RedstoneAPI` exposes both `rs` and `redstone`; `OSAPI` exposes only `os`.

---

## Step 2 — Register in `Computer.createAPIs()`

Open [`Computer.java`](../src/main/java/dan200/computercraft/core/computer/Computer.java) and add your API to `createAPIs()`:

```java
private void createAPIs() {
    // ...existing code...
    this.m_apis.add(new MyAPI(this.m_apiEnvironment));
    // ...existing code...
}
```

If your API is controlled by a config flag, wrap it conditionally (same pattern as `HTTPAPI`):

```java
if (ComputerCraft.my_feature_enable) {
    this.m_apis.add(new MyAPI(this.m_apiEnvironment));
}
```

The config flag itself would be a `public static boolean` field on `ComputerCraft` loaded in `ComputerCraft.syncConfig()`.

---

## Step 3 — Use `IAPIEnvironment`

Your API receives `IAPIEnvironment` in its constructor. It provides access to:

| Method | Use |
|---|---|
| `queueEvent(String, Object[])` | Fire a Lua event on this computer |
| `getTerminal()` | Read/write the terminal surface |
| `getFileSystem()` | Access the virtual file system |
| `getComputerID()` | The computer's unique ID |
| `getComputerEnvironment()` | World time, day, hostname |
| `getOutput(side)` / `setOutput(side, val)` | Redstone I/O |
| `getPeripheral(side)` | Access attached peripherals |
| `shutdown()` / `reboot()` | Programmatically stop/restart |

> **Important:** `core` must not import from `shared`. If you need Minecraft/Forge types, the feature belongs in `shared` (e.g. `CommandAPI`, `PocketAPI`) rather than `core`.

---

## Step 4 — Optional: Add a ROM Lua wrapper

If your Java API is low-level and a Lua convenience layer would help users, add a pure-Lua file at:

```
src/main/resources/assets/computercraft/lua/rom/apis/myapi
```

This file is loaded automatically by `bios.lua` and can wrap, extend, or document the Java-backed global. See `rom/apis/rednet` (wraps the `modem` peripheral) for a complex example, or `rom/apis/colors` (thin alias) for a simple one.

> If your API adds new terminal-surface methods, you **must** also add a corresponding delegation in `rom/apis/window`. See `AGENTS.md`.

---

## Step 5 — Optional: Add a `rom/help` file

Add a plain-text help file for in-game `help myapi` access:

```
src/main/resources/assets/computercraft/lua/rom/help/myapi
```

Keep it brief: one-line summary, then a list of method signatures with descriptions. See `rom/help/bit` for the format.

---

## Reference: Existing APIs

| API class | Lua global(s) | Stateful? | Uses `advance`? |
|---|---|---|---|
| `BitAPI` | `bit` | No | No |
| `RedstoneAPI` | `rs`, `redstone` | No | No |
| `TermAPI` | `term` | Yes (terminal ref) | No |
| `FSAPI` | `fs` | No | No |
| `OSAPI` | `os` | Yes (timers/alarms) | Yes — fires `timer`, `alarm` |
| `HTTPAPI` | `http` | Yes (request map) | Yes — polls pending requests |
| `PeripheralAPI` | `peripheral` | Yes (side map) | No |

---

## Checklist for Adding a New API

1. **Create `MyAPI.java`** in `dan200.computercraft.core.apis`, implementing `ILuaAPI`.
2. **Define `getNames()`** — choose a snake_case primary name; add aliases if useful.
3. **Define `getMethodNames()`** — list all Lua-callable method names. The count must exactly match the number of `case` blocks in `callMethod`.
4. **Implement `callMethod`** — validate all arguments, throw `LuaException` on bad input, return `null` for void or `new Object[]{ result }` for values.
5. **Implement lifecycle hooks** — no-op `startup`/`advance`/`shutdown` unless your API needs them.
6. **Register in `Computer.createAPIs()`** — unconditionally, or behind a `ComputerCraft` config flag.
7. **Add a ROM wrapper** (optional) — if the raw Java API benefits from a Lua convenience layer.
8. **Add a help file** (optional) — if the API is user-facing.
9. **Add tests** — use a stub `IAPIEnvironment` (see `TermAPITest` for the pattern). Verify that `getMethodNames().length` matches the highest `case` index + 1, and cover success and error paths for each method.

---

*Reference implementations: `BitAPI` (minimal, stateless), `RedstoneAPI` (argument parsing, aliasing), `OSAPI` (stateful, tick-driven events), `HTTPAPI` (conditional registration, background-thread polling).*

