# AGENTS.md — Computercraft-Legacy

Decompiled and patched ComputerCraft for Minecraft 1.7.10, built on the
GT New Horizons Gradle convention plugin. Primary fix: computers no longer
shut down when outside the 64-block TileEntity tick range.

---

## Architecture

```
dan200.computercraft
├── api/           Public API for companion mods (IPeripheralProvider, ITurtleUpgrade, …)
├── core/          Platform-agnostic Lua runtime (no Minecraft imports)
│   ├── computer/  Computer, ComputerThread, MainThread — lifecycle & threading
│   ├── apis/      ILuaAPI implementations (OSAPI, FSAPI, TermAPI, RedstoneAPI, …)
│   ├── lua/       ILuaMachine interface → CobaltMachine (sole Lua VM)
│   ├── terminal/  Terminal, TextBuffer — core terminal state and palette
│   └── filesystem/
├── shared/        Minecraft-coupled code (blocks, items, network packets)
│   ├── computer/core/  ServerComputer / ClientComputer bridge Minecraft ↔ core
│   ├── network/   ComputerCraftPacket — FML event channel, byte-type dispatch
│   ├── common/    ServerTerminal, ClientTerminal — NBT terminal sync helpers
│   └── pocket/    ItemPocketComputer + PocketAPI (equip/unequip modem)
├── client/        Client-only rendering / GUI
│   ├── gui/       FixedWidthFontRenderer, WidgetTerminal, GuiComputer, …
│   └── render/    TileEntityMonitorRenderer, TileEntityTurtleRenderer
└── server/        Server-side proxy
```

**Data flow**: `TileComputer` (Minecraft tick) → `ServerComputer` (implements `IComputerEnvironment`) →
`Computer` (core) → `ILuaMachine` (`CobaltMachine`) → `ILuaAPI` instances.

**Client/server split** uses `@SidedProxy` pointing to `IComputerCraftProxy` / `ICCTurtleProxy`
implementations. No Mixins; no Access Transformers — all changes are made directly to the
decompiled source.

---

## Feature Status

See **[TWEAKEDCC_COVERAGE.md](TWEAKEDCC_COVERAGE.md)** for the canonical list of implemented,
planned, and deferred CC:Tweaked API features. Consult it before starting any new feature to
avoid duplicating work or conflicting with existing decisions.

---

## Lua Runtime

The sole Lua VM is **Cobalt 0.6.0** (`org.squiddev:Cobalt:0.6.0`), embedded via shadow JAR.
Entry point: `CobaltMachine` in `core/lua/lib/cobalt/`.

- **Cobalt 0.7+ requires JVM 17** — this project targets JVM 8; stay on 0.6.x.
- `ILuaMachine` is the abstraction; `CobaltMachine` is its only implementation.
- `COBALT_UPGRADE_PLAN.md` documents the completed 0.2 → 0.6.0 migration; consult it for
  Cobalt API change rationale.

### Features available after upgrading to Java 17+ (Cobalt 0.7.x → 0.9.x)

The latest published release is **`cc.tweaked:cobalt:0.9.7`** (note: group ID changed from
`org.squiddev:Cobalt` to `cc.tweaked:cobalt` starting with 0.8.0). All versions 0.7.0 and above
require **JVM 17**. The key gains over 0.6.0, grouped by release:

| Version | Notable changes |
|---|---|
| **0.7.0** | Threadless coroutines — coroutines unwind/restore the stack instead of using one Java thread each; `DebugHandler` removed, replaced by `InterruptHandler`; debug hooks now inherited across coroutines; stdlib aligned toward Lua 5.4. |
| **0.7.1** | Stricter number lexing (rejects previously-accepted malformed literals); most `LibFunction` subclasses replaced by lambdas in the Java API. |
| **0.7.2** | Hex floating-point literals (`0x1p4`); hex floats in `string.format`; stricter `string.format` checks; fix Lua patterns misclassifying non-ASCII bytes as letters. |
| **0.7.3** | String pattern fixes: `%g` frontier pattern added; `%p` fixed for upper character ranges. |
| **0.8.0** | **Lua 5.2 language update** — `goto` and labels; updated stdlib semantics; `string.dump` removed by default; bytecode verifier removed; Cobalt published as a JPMS module. |
| **0.8.1** | `math.random` now follows Lua 5.4-style seeding behavior. |
| **0.8.2** | Table hash presizing and hash-size calculation fixes. |
| **0.9.0** | Internal table storage changed to flat arrays (performance); `debug.getinfo` simplified and corrected; call/return hooks fire correctly. |
| **0.9.2** | Fix yielding not handled in several edge-case paths. |
| **0.9.3** | Better source positions in error messages; poll interrupted state when reading from `InputStream`s. |
| **0.9.4** | Lua 5.3-style multi-argument `math.atan(y, x)`. |
| **0.9.5** | Metatable cache invalidation fix; large-number handling fixes in `LuaDouble`. |

**Java API impact** (beyond the `CobaltMachine` rewrites already documented in
`COBALT_UPGRADE_PLAN.md`): the `LibFunction` convenience subclasses (`OneArgFunction`,
`TwoArgFunction`, etc.) are fully gone by 0.7.1 — they must be replaced with `JavaFunction`
lambdas. `BigIntegerValue`'s direct `LuaValue` subclassing may need to become a `LuaUserdata`
or table-with-metatable approach.

---

## Adding a Lua API

Implement `ILuaAPI` (extends `ILuaObject`):

```java
public class MyAPI implements ILuaAPI {
    @Override public String[] getNames() { return new String[]{ "myapi" }; }
    @Override public String[] getMethodNames() { return new String[]{ "doThing" }; }
    @Override public Object[] callMethod(ILuaContext ctx, int method, Object[] args) throws LuaException { … }
    @Override public void startup() {}
    @Override public void advance(double dt) {}
    @Override public void shutdown() {}
}
```

Register it in `Computer.java` alongside the existing APIs (e.g., `OSAPI`, `TermAPI`).

---

## Key Conventions

- **Modern Java syntax via Jabel** — Java 11–17 syntax is allowed; the compiler targets JVM 8.
  (`enableModernJavaSyntax = true` in `gradle.properties`).
- **`m_` prefix** — legacy instance fields use Hungarian `m_` notation; follow it inside existing classes.
- **`ComputerCraftAPI`** uses reflection to resolve the main mod class — this is intentional legacy
  design for companion-mod compatibility; do not refactor it.
- **Shadow JAR** — `minimizeShadowedDependencies = false`, `relocateShadowedDependencies = false`;
  Cobalt and Java-WebSocket classes are included as-is.
- **`dan200.computercraft.Tags`** is auto-generated by Gradle from `generateGradleTokenClass` —
  do not create or edit it manually.
- **British/American method variants** — methods exposed to Lua that deal with colour are always
  registered twice: once with `Color` (American) and once with `Colour` (British). In
  `getMethodNames()` the American variant comes first; the British variant immediately follows and
  shares the same `case` body in `callMethod`. Follow this pattern for all new term methods.
- **Switch-scope variable collisions** — variables declared in a bare `case:` block (without `{}`)
  are scoped to the entire `switch`. When adding new `case` blocks to an existing switch that
  already declares bare variables (e.g. `int colour`), use distinct local names (e.g.
  `paletteIdx22`) or wrap in a `{ }` block and use a name not declared in any outer case.
- **Terminal palette** — `Terminal` carries a `double[][] m_palette` (16 × {r,g,b}, blit index
  ordering: 0 = white … 15 = black matching `FixedWidthFontRenderer`'s display-list layout).
  Default values are in the static `Terminal.DEFAULT_PALETTE_HEX` array. The palette is persisted
  via a packed `int[16]` NBT key `"term_palette"` and flows to the client automatically through
  `ServerTerminal.writeDescription` → `ComputerCraftPacket` → `ClientTerminal.readDescription`.
  Rendering consumers (`WidgetTerminal`, `TileEntityMonitorRenderer`) pass `terminal.getPalette()`
  to the `FixedWidthFontRenderer.drawString(..., double[][] palette)` overload; colour indices are
  resolved via `GL11.glColor3f` inline rather than the baked `Colour`-enum display lists.
- **`window` Lua API forwarding** — when a new term-surface method is added to `TermAPI`, add a
  corresponding delegation function to `rom/apis/window` that calls `parent.<method>`. See the
  `setPaletteColor`, `getPaletteColor`, `nativePaletteColor` entries as the reference pattern.
- **`core` must not import from `shared`** — `Terminal.java` already violates this for
  `NBTTagCompound`; do not introduce further `shared` imports into `core`. Inline constant values
  (e.g. default palette hex values) rather than referencing `Colour` from `shared/util`.

---

## Developer Workflows

```powershell
# Build (produces shadow JAR in build/libs/)
./gradlew build

# Run tests (JUnit 5)
./gradlew test

# Start a local server for manual testing
./gradlew runServer

# Force Spotless formatting fixes
./gradlew spotlessApply

# Check for wildcard-import violations
./gradlew checkstyleMain
```

Tests are split across two packages:

| Package | Test classes |
|---|---|
| `core/lua/` | `CobaltMachineTest`, `FileSystemReadLineTest`, `TextUtilsJsonTest`, `TextUtilsSerializeTest`, `SettingsAPITest`, `ColorsAPITest`, `CcPrettyTest`, `CcImageNftTest`, `CcExpectTest`, `BiosRequireTest`, `GlobalsTest`, `HelpAPITest`, `IOAPITest`, `PaintutilsAPITest`, `PeripheralAPITest`, `RednetAPITest`, `ShellAPITest`, `WindowAPITest` |
| `core/apis/` | `TermAPITest`, `OSAPITest`, `FSAPITest`, `HTTPResponseTest`, `WebSocketHandleTest` |
| `shared/peripheral/modem/` | `WiredModemPeripheralTest` |
| `shared/pocket/apis/` | `PocketAPITest` |
| `shared/turtle/apis/` | `TurtleAPITest` |

All use JUnit 5 with `useJUnitPlatform()` (configured in `addon.gradle.kts`).

In-game Lua test scripts for manual verification live in
`run/saves/Test World/computer/37/` and follow the `test_<feature>.lua` naming convention
(e.g. `test_palette.lua`, `test_colors.lua`, `test_epoch_date.lua`).

---

## External Dependencies

| Dependency | Role |
|---|---|
| `com.gtnewhorizons.gtnhconvention` | Build plugin (ForgeGradle wrapper, Spotless, Jabel, shadow) |
| `org.squiddev:Cobalt:0.6.0` | Active Lua 5.1 runtime (shadowed) |
| `org.java-websocket:Java-WebSocket:1.5.6` | WebSocket client support (shadowed) |
| `com.github.GTNewHorizons:ForgeMultipart` | Multipart peripheral support |
| `com.github.GTNewHorizons:CodeChickenCore` | Required by ForgeMultipart |
| `com.github.GTNewHorizons:NotEnoughItems` | Dev-only runtime for testing |
| `org.junit.jupiter:junit-jupiter:5.8.2` | Unit testing |

Cobalt Maven repository (`https://maven.squiddev.cc`) is already declared in `repositories.gradle`.
