# Plan: Decommission LuaJ and Make Cobalt the Sole Lua Runtime

Remove all LuaJ source files, adapter classes, and configuration, then simplify `LuaHelpers` so
it unconditionally returns a `CobaltMachine`. The `org.luaj:luaj-jse` shadow dependency is dropped
from the build, and the `cobalt` config toggle is retired with a startup log warning to inform
existing server operators. The `_LUAJ_VERSION` global in `CobaltMachine` is optionally renamed for
clarity.

---

## Steps

### Step 1 — Delete the three LuaJ source files
Remove the following files, then delete the now-empty `luaj/` package directory:
- `src/main/java/dan200/computercraft/core/lua/LuaJLuaMachine.java`
- `src/main/java/dan200/computercraft/core/lua/lib/luaj/LuaJArguments.java`
- `src/main/java/dan200/computercraft/core/lua/lib/luaj/LuaJConverter.java`

### Step 2 — Delete the LuaJ test file
Remove `src/test/java/dan200/computercraft/core/lua/LuaJLuaMachineTest.java`.
Its coroutine lifecycle and coroutine-abandon tests are already covered by the equivalent cases
in `CobaltMachineTest`.

### Step 3 — Clean up `LuaHelpers.java`
In `src/main/java/dan200/computercraft/core/lua/lib/LuaHelpers.java`:
- Remove imports: `org.luaj.vm2.LuaTable`, `org.luaj.vm2.Varargs`, `LuaJLuaMachine`,
  `LuaJArguments`
- Delete the `private static Field getGlobals` field and its `static {}` initialiser block
- Delete the `delegateLuaObject(ILuaObject, ILuaContext, int, Varargs)` overload (only ever
  called from the now-deleted `LuaJLuaMachine`)
- Simplify `createMachine()` to unconditionally `return new CobaltMachine(computer);`

### Step 4 — Retire the `cobalt` toggle in `ComputerCraft.java`
In `src/main/java/dan200/computercraft/ComputerCraft.java`:
- Remove the `public static boolean cobalt = false;` field
- Remove the `config.get("general", "cobalt", cobalt)` block from `preInit`
- Add a `ComputerCraft.logger.warn(...)` after config load stating that the `cobalt` config key
  is no longer recognised and that Cobalt is now always the active Lua engine

### Step 5 — Remove the LuaJ shadow dependency
In `dependencies.gradle`, delete:
```groovy
shadowImplementation("org.luaj:luaj-jse:2.0.3")
```

### Step 6 — Rename `_LUAJ_VERSION` → `_COBALT_VERSION` in `CobaltMachine.java`
In `src/main/java/dan200/computercraft/core/lua/lib/cobalt/CobaltMachine.java`:
- Change `globals.rawset("_LUAJ_VERSION", valueOf("Cobalt 0.6"))` to
  `globals.rawset("_COBALT_VERSION", valueOf("Cobalt 0.6"))`
- Update the `@see` Javadoc tag that still references `LuaJLuaMachine`

---

## Further Considerations

1. **Breaking change for scripts using `_LUAJ_VERSION`** — Step 6 is a Lua-API-level breaking
   change. If backward compatibility for existing Lua scripts is required, keep `_LUAJ_VERSION` as
   a deprecated alias alongside `_COBALT_VERSION` rather than replacing it outright.

2. **Existing `ComputerCraft.cfg` files** — the startup warning in Step 4 is the only migration
   path for server operators. Consider documenting this in `CHANGELOG` or `README.md` so ops know
   to remove the stale `cobalt=false` key.

3. **Shadow JAR size** — after removing `luaj-jse`, verify the final shadow JAR is smaller and
   that no other class in the codebase imports `org.luaj.*` before merging. A `grep` for
   `org.luaj` across `src/` should return zero hits after Steps 1–3.

