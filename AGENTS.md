# AGENTS.md — Computercraft-Legacy (Optimized for Agentic Use)

Decompiled ComputerCraft 1.75 for Minecraft 1.7.10, with a backport of the 1.8-line
maintenance work and a set of original additions. All changes are made directly to
the decompiled source (no Mixins/Access Transformers).

The base is decompiled binary, not upstream source: ComputerCraft's 1.7.10 source
was closed, and public source only begins with the 1.8 line. Do not assume a
decompiled file matches a modern CC: Tweaked file, or vice versa. See
[NOTICE](NOTICE) for the full provenance record.

---

## Licensing (read before adding or copying code)

- The **root `LICENSE` is the CCPL** and must stay that way. Do not add an MIT
  licence, a `LICENSE.md`, or any per-file licence header asserting a different
  term. CCPL section 5 requires every distribution of this mod to remain CCPL, and
  the ComputerCraft code here is Daniel Ratcliffe's.
- The CCPL is **non-commercial** and restricts reuse to other Minecraft mods.
  Do not describe the project as MIT/permissive, in the README, `mcmod.info`, or
  anywhere else.
- **There are deliberately no per-file licence headers.** Provenance is recorded
  centrally in `NOTICE`. Do not add SPDX headers to source files; the project
  owner has considered and declined that, and scattered headers invite
  contradictory claims. Attribution lives in `NOTICE`.
- `LICENSE*` and `NOTICE` are **duplicated into `src/main/resources`** so they ship
  in the jar. If you change a root licence file, copy it to `src/main/resources`
  too — `LicenseDistributionTest` fails the build if the copies drift, or if a root
  licence has no copy.
- When copying code from **CC: Tweaked** or other MPL 2.0 sources, record it in
  `NOTICE` and keep the file out of any claim of CCPL-only provenance. Note that
  files under `dan200.computercraft.api` historically carried a stricter ComputerCraft
  API notice ("may be redistributed unmodified and in full only") whose relationship
  to MPL 2.0 is unresolved; `NOTICE` tracks this as an open question.
- Removing an upstream copyright notice is acceptable **only** because `NOTICE`
  carries that attribution. Never drop a notice without confirming `NOTICE` names
  the source.

---

## Architecture

```
dan200.computercraft
├── api/           Public API for companion mods
├── core/          Platform-agnostic Lua runtime
│   ├── computer/  Computer, ComputerThread, MainThread
│   ├── apis/      ILuaAPI implementations
│   ├── lua/       ILuaMachine interface → CobaltMachine
│   ├── terminal/  Terminal, TextBuffer
│   └── filesystem/
├── shared/        Minecraft-coupled code
│   ├── computer/core/  ServerComputer / ClientComputer bridge
│   ├── network/   ComputerCraftPacket
│   ├── common/    ServerTerminal, ClientTerminal
│   └── pocket/    ItemPocketComputer + PocketAPI
├── client/        Client-only rendering / GUI
│   ├── gui/       FixedWidthFontRenderer, WidgetTerminal, GuiComputer
│   └── render/    TileEntityMonitorRenderer, TileEntityTurtleRenderer
└── server/        Server-side proxy
```

**Data flow:** `TileComputer` → `ServerComputer` → `Computer` → `ILuaMachine` → `ILuaAPI`.

---

## Key Conventions (for Agents)

- Compiles with a JDK 25 toolchain but ships **Java 8 bytecode**, via JVM Downgrader
  (`enableModernJavaSyntax = jvmDowngrader`, `downgradeTargetVersion = 8`). Modern syntax
  *and* newer stdlib APIs are both available, unlike the old Jabel setup. The jar is
  multi-release: Java 7/8 classes sit at the root (all entries must stay <= class version
  52) with a `META-INF/versions/21/` overlay that Java 21+ prefers. Java 8 ignores the
  overlay entirely.
- **JvmDowngrader's limit:** it rewrites language features (records, sealed types, `indy`
  string concatenation) and can supply stub *classes* absent from Java 8, but it cannot add
  *methods* to JDK classes that exist on Java 8 and merely lack them — e.g.
  `Arrays.mismatch` (Java 9) or `Collection.toArray(T[])` (Java 11). Such calls compile into
  a clean Java 8 jar and then fail with `NoSuchMethodError` at runtime. This matters most
  for shaded dependencies, which are downgraded without any source-level fixup available.
- Legacy instance fields use `m_` prefix; follow in existing classes.
- Do not refactor `ComputerCraftAPI` reflection logic.
- Do not create/edit `dan200.computercraft.Tags` (auto-generated).
- For Lua-exposed color methods, always register both American (`Color`) and British (`Colour`) variants; American first in `getMethodNames()`.
- When adding `case:` blocks to a `switch` with bare variable declarations, use unique names or wrap in `{}`.
- `Terminal` palette: 16×{r,g,b} in blit order; persisted as packed `int[16]` NBT key `term_palette`.
- When adding new term-surface methods to `TermAPI`, add a corresponding delegation in `rom/apis/window`.
- `core` must not import from `shared` (except for existing `Terminal.java` violation).

---

## Adding a Lua API

Implement `ILuaAPI` (extends `ILuaObject`):

```java
public class MyAPI implements ILuaAPI {
    @Override public String[] getNames() { return new String[]{ "myapi" }; }
    @Override public String[] getMethodNames() { return new String[]{ "doThing" }; }
    @Override public Object[] callMethod(ILuaContext ctx, int method, Object[] args) throws LuaException { /* ... */ }
    @Override public void startup() {}
    @Override public void advance(double dt) {}
    @Override public void shutdown() {}
}
```
Register in `Computer.java` alongside existing APIs.

---

## Developer Workflows (Summary)

- Build: `./gradlew build`
- Test: `./gradlew test`
- Run server: `./gradlew runServer`
- Run on modern Java: `./gradlew runServer25` / `runClient25`. lwjgl3ify and HodgePodge are
  injected automatically by the convention for these tasks — do **not** add them to
  `dependencies.gradle`.
- Format: `./gradlew spotlessApply`
- Checkstyle: `./gradlew checkstyleMain`

Tests use JUnit 5 (`useJUnitPlatform()`).

**Known papercut:** with `jvmDowngrader`, the first build after `build/tmp` is cleared fails
in `verifyTestSuiteExecuted` rather than running tests, because `Test`'s `@SkipWhenEmpty`
input is snapshotted before `downgradeTestClasses` produces its output. Just re-run the
build. The guard turns what would otherwise be a green build with zero tests executed into
a loud failure; see the comment in `addon.gradle.kts` for the full explanation.
See `TWEAKEDCC_COVERAGE.md` for test coverage and `COBALT_UPGRADE_PLAN.md` for Lua runtime migration details.

---

## External Dependencies

| Dependency | Role |
|---|---|
| com.gtnewhorizons.gtnhconvention | Build plugin |
| org.squiddev:Cobalt:0.6.0 | Lua 5.1/5.2 runtime (shadowed, MIT) |
| org.java-websocket:Java-WebSocket:1.5.6 | WebSocket client (shadowed, MIT) |
| org.slf4j:slf4j-api:2.0.6 | Transitive of Java-WebSocket; present in the shipped jar but **undeclared** in `dependencies.gradle` (MIT) |
| com.github.GTNewHorizons:ForgeMultipart | Multipart peripheral support |
| com.github.GTNewHorizons:CodeChickenCore | Required by ForgeMultipart |
| com.github.GTNewHorizons:NotEnoughItems | Dev-only runtime for testing |
| org.junit.jupiter:junit-jupiter:5.8.2 | Unit testing |
| org.mockito:mockito-core:4.8.0 | Unit testing |

Cobalt Maven repository: `https://maven.squiddev.cc` (see `repositories.gradle`).
Shadowed dependencies must stay listed in `NOTICE` section 3, with their licence
texts present in the repository root.

---

## References & Further Reading

- [TWEAKEDCC_COVERAGE.md](docs/TWEAKEDCC_COVERAGE.md): API/features coverage
- [COBALT_UPGRADE_PLAN.md](docs/COBALT_UPGRADE_PLAN.md): Lua runtime migration
- [NOTICE](NOTICE): provenance and licensing record
- [CODEBASE_ANALYSIS.md](docs/CODEBASE_ANALYSIS.md): decompiler-artifact review
- For legacy/historical notes and upgrade logs, see `COBALT_UPGRADE_PLAN.md` or project history.
