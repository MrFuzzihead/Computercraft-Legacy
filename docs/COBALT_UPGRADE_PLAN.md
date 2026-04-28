# Plan: Upgrade Cobalt Dependency from 0.2 to 0.7.3

Replace the local `libs/Cobalt-0.2.jar` file dependency with the `org.squiddev:Cobalt:0.7.3` Maven artifact,
then update all source files in the `cobalt` package to conform to the 0.7.3 API. The Cobalt library underwent
major breaking changes between these versions, so this is a significant migration.

## Steps

### 1. ✅ Update dependency in `dependencies.gradle`
Replace:
```groovy
shadowImplementation(project.files("libs/Cobalt-0.2.jar"))
```
With:
```groovy
shadowImplementation("org.squiddev:Cobalt:0.7.3")
```
Also remove the old commented-out `//shadowImplementation("org.squiddev:Cobalt:0.5.0-SNAPSHOT")` line.
Optionally delete `libs/Cobalt-0.2.jar` from the repository.

> Note: `repositories.gradle` already registers `https://maven.squiddev.cc`, so no repository changes are needed.

---

### 2. ✅ Rebuild API surface map
Run `./gradlew compileJava` (expecting failures) to get a full compiler error list, documenting every broken
API call across the five affected source files:

- `src/main/java/dan200/computercraft/core/lua/lib/cobalt/CobaltMachine.java`
- `src/main/java/dan200/computercraft/core/lua/lib/cobalt/CobaltConverter.java`
- `src/main/java/dan200/computercraft/core/lua/lib/cobalt/BigIntegerValue.java`
- `src/main/java/dan200/computercraft/core/lua/lib/cobalt/BitOpLib.java`
- `src/main/java/dan200/computercraft/core/lua/lib/cobalt/CobaltArguments.java`

#### ⚠️ Java 8 Compatibility — Version Adjusted to 0.6.0
Cobalt **0.7.0 and above require JVM 17**. This project targets Java 8 (Minecraft 1.7.10), so the highest
usable version is **0.6.0** (JVM 8). The dependency in `dependencies.gradle` has been updated accordingly.

Version compatibility summary:
| Version | JVM Required |
|---------|-------------|
| 0.5.12  | 8 ✅        |
| 0.6.0   | 8 ✅ ← **target** |
| 0.7.0+  | 17 ❌       |

#### Compiler Errors Found (12 errors, all in `CobaltMachine.java`)

| # | Line | Error |
|---|------|-------|
| 1 | 24 | `import org.squiddev.cobalt.OrphanedThread` — class no longer exists |
| 2 | 87 | `new LuaState(new AbstractResourceManipulator() {...})` — constructor signature changed; now uses a builder pattern |
| 3 | 95 | `state.debug = ...` — `debug` field is now `final`, cannot be reassigned |
| 4 | 95 | `new DebugHandler(state)` — constructor no longer takes a `LuaState` argument |
| 5 | 100 | `@Override` on `poll()` — method no longer exists in the `DebugHandler` supertype |
| 6 | 110 | `super.onInstruction(ds, di, pc, extras, top)` — `onInstruction` signature changed; no longer takes `Varargs extras, int top` |
| 7 | 147 | `LibFunction.bind(state, globals, ...)` — first argument changed; `LuaState` cannot be passed where `LuaTable` is expected |
| 8 | 229 | `mainThread.resume(args)` — `LuaThread.resume()` signature changed; now requires `(LuaState, LuaThread, Varargs)` |
| 9 | 322 | `throw new OrphanedThread()` — class no longer exists |
| 10 | 408 | `catch (OrphanedThread e)` — class no longer exists |
| 11 | 497 | `BaseLib.loadStream(state, stream, chunkname)` — method is now private |
| 12 | 508 | `BaseLib.loadStream(state, script.toInputStream(), chunkname)` — method is now private |

Only `CobaltMachine.java` produced errors; `CobaltConverter.java`, `BigIntegerValue.java`, `BitOpLib.java`,
and `CobaltArguments.java` compiled without issues against 0.6.0.

---

### 3. ✅ Rewrite `CobaltMachine.java`
Address the major structural changes in 0.7.3:

- **`LuaState` construction** — now uses a builder pattern: `LuaState.builder().build()` instead of
  `new LuaState(new AbstractResourceManipulator() { ... })`. Remove the `AbstractResourceManipulator`
  import and usage.
- **`DebugHandler` hook** — `onInstruction` signature changed; it no longer takes `Varargs extras, int top`
  parameters. Update the override accordingly.
- **`state.debug` assignment** — the way a custom debug handler is attached to the state may have changed;
  verify the new API (e.g., via `LuaState.Builder`).
- **`LuaThread` resume/yield** — now throws checked `LuaError` instead of unchecked; update all call sites
  that catch `OrphanedThread` / `Throwable` (note: `OrphanedThread` may no longer exist in 0.7.3).
- **`state.setupThread(globals)`** — verify this method still exists or find its replacement.
- **`globals.load(state, lib)`** — verify the library-loading API is unchanged.
- **`LibFunction.bind(...)`** — verify the static bind helper signature is unchanged.
- **`_LUAJ_VERSION` string** — update the value from `"Cobalt 0.2"` to `"Cobalt 0.7.3"`.
- **`BaseLib.loadStream(...)`** — verify this static helper still exists in 0.7.3.
- **`OperationHelper.call(...)` / `OperationHelper.concat(...)`** — verify these still exist and have the
  same signatures.
- **`LoadState.load(...)`** — verify the compiler entry point is unchanged.

---

### 4. ✅ Rewrite `BigIntegerValue.java`
`LuaValue` subclassing changed significantly in 0.7.3:

- **Type constants** (`TUSERDATA`, `TSTRING`, `TINT`, `TNUMBER`, etc.) — verify they still exist in
  `Constants` or find replacements.
- **`opt*` / `check*` method overrides** — many of these were removed from the `LuaValue` base class in
  0.7.x. Remove or replace all overrides that no longer exist in the base class
  (`optDouble`, `optInteger`, `optLuaInteger`, `optLong`, `optNumber`, `checkInteger`, `checkLuaInteger`,
  `checkLong`, `checkNumber`, `checkString`, `checkLuaString`).
- **`ThreeArgFunction`** — removed in 0.7.x; migrate `BigIntegerFunction` to the new function abstraction
  (`JavaFunction` / `RegisteredFunction` pattern). The `opcode` / `name` / `env` fields on function
  subclasses are also gone; redesign the dispatch mechanism accordingly.
- **`LuaState.random`** — the public `random` field on `LuaState` may no longer exist; find the replacement
  accessor.
- **`ErrorFactory.argError(...)`** — verify this still exists or find the replacement (may be
  `LuaError.argError` or similar).
- **`LuaDouble.NAN`** — verify this constant still exists.
- **Extending `LuaValue` directly** — if direct subclassing is no longer viable, refactor to use
  `LuaUserdata` or a table-based approach with a metatable.

---

### 5. ✅ Rewrite `BitOpLib.java` and `CobaltArguments.java`
- **`OneArgFunction`, `TwoArgFunction`, `VarArgFunction`** — these convenience base classes were removed in
  0.7.x and replaced by `JavaFunction` (a functional interface). Rewrite all inner classes as `JavaFunction`
  lambdas or named implementations.
- **`opcode` / `name` / `env` fields** — these no longer exist on function objects; redesign the multi-op
  dispatch in `BitOpLib` (e.g., use separate lambdas per operation, or a switch on a captured int).
- **`LuaString` internal fields** (`bytes`, `offset`, `length`) — became private in 0.7.x. Replace all
  direct field access in `CobaltArguments` and elsewhere with the appropriate accessor methods
  (e.g., `LuaString.toByteArray()` or equivalent).

---

### 6. ✅ Rewrite `CobaltConverter.java`
- **`LuaString` field access** (`string.bytes`, `string.offset`, `string.length`) — replace with safe
  accessor methods as described in Step 5.
- **`LuaTable.next()` / `Varargs.first()` / `Varargs.arg()`** — verify these method signatures are
  unchanged.
- **`value.checkTable()`** — verify this method still exists on `LuaValue`.
- **`Constants.TINT`** — this integer type constant may have been merged into `TNUMBER`; update the
  `switch` in `toObject` accordingly.

---

## Further Considerations

1. **Cobalt 0.7.3 API reference** — the Cobalt changelog and full source are at
   [https://github.com/cc-tweaked/Cobalt](https://github.com/cc-tweaked/Cobalt). Reviewing `CHANGELOG.md`
   and the 0.7.x source directly is essential before beginning Step 3, as the API changes are extensive
   and not all are documented.

2. **`LuaThread` coroutine model** — in 0.7.x, coroutine resume/yield throws checked `LuaError` instead
   of unchecked exceptions. All call sites in `CobaltMachine` that catch `OrphanedThread` / `Throwable`
   need restructuring. `OrphanedThread` may no longer exist and its role may be replaced by a different
   mechanism.

3. **`BigIntegerValue` viability** — extending `LuaValue` directly became much more restricted in 0.7.x.
   It may be necessary to use a `LuaUserdata` subclass or a pure table/metatable approach instead, which
   could require a broader redesign of `BigIntegerValue` and how it integrates with `BigIntegerFunction`.

4. **Shadow JAR** — after all source changes compile successfully, verify the shadow JAR still correctly
   includes and relocates the Cobalt classes, and that no Cobalt classes leak into the public API
   unexpectedly.

5. **Run tests** — execute `./gradlew test` to confirm no regressions in the unit test suite after the
   migration is complete.

