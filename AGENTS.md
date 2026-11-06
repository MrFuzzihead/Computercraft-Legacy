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
- **Compile against LWJGL3 only where lwjgl3ify guarantees it, and mark the class
  `@Lwjgl3Aware`.** The one such place is `org.lwjgl:lwjgl-sdl` (`compileOnly`), used by
  `client/upload/SdlFileDropWatcher` for SDL3 drag-and-drop. lwjgl3ify rewrites every
  `org/lwjgl/` reference in mod bytecode to `org/lwjglx/` (that is how legacy LWJGL2 calls reach
  its shims); LWJGL3 shares the prefix, so an un-annotated class is redirected somewhere that has
  no SDL and the client crashes with `NoClassDefFoundError: org/lwjglx/sdl/SDL_EventFilter`.
  The opt-out is **per class file** and anonymous classes cannot be annotated — use a named nested
  class. Never put `Lwjgl3ify-Aware: true` on the jar manifest: the rest of the mod *needs* the
  rewrite. `Lwjgl3AwareCoverageTest` enforces all of this. Nothing from LWJGL may be shaded or
  shipped: lwjgl3ify provides it at runtime.
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

- **Bytecode targets Java 8, via JVM Downgrader.** `enableModernJavaSyntax = jvmDowngrader`,
  `downgradeTargetVersion = 8`. Source may use modern syntax *and* modern stdlib APIs; the
  toolchain compiles at Java 25 and the downgrader rewrites both down to class file version 52.
  The reason is downstream, not player-facing: a Java-8-target consumer (CCTweaks, Jabel-era
  addons, anything compiled with `--release 8`) cannot read a class file above its own version,
  and Gradle refuses to match a Java 25 variant for a Java 8 consumer at all.
- **The mod still requires Java 17+ at runtime**, because `@Mod(dependencies =
  "required-after:lwjgl3ify")` is load-bearing: `SdlFileDropWatcher` compiles against
  `org.lwjgl:lwjgl-sdl`, and lwjgl3ify is what puts LWJGL3 and its natives on the classpath.
  Downgrading buys class-file compatibility, *not* Java 8 runnability. lwjgl3ify and HodgePodge
  are injected by the convention for `runClient17/21/25` and `runServer17/21/25` — do *not* add
  them to `dependencies.gradle`. Plain `runServer`/`runClient` run without them and will stop at
  `MissingModsException: lwjgl3ify`; that is expected.
- **Do not reintroduce a JVM Downgrader multi-release overlay.** FML 1.7.10's `JarDiscoverer`
  feeds *every* `.class` entry to ASM 5.0.3, which cannot read class files above Java 8. It
  walks the whole jar, so a `META-INF/versions/N` overlay makes it throw and FML discards the
  entire mod — silently, with one `failed to read properly` line in the log, and computers that
  never turn on. The build cannot be asked to omit the overlay: an empty
  `jvmDowngraderMultiReleaseVersions` falls back to `21,25`, and the convention rejects anything
  below 9. `multiReleaseVersions = 9` therefore stays in `gradle.properties` and
  `addon.gradle.kts` strips `META-INF/versions/**` from every shipped archive and asserts the
  result. The unit tests do **not** catch a regression here, because they run on
  `testRuntimeClasspath` and never touch the packaged jar; if you change the jar pipeline, verify
  with `unzip -l` and a real `runServer25`.
- **Stubs are shaded, not taken from GTNHLib** (`jvmDowngraderStubsProvider = shade`).
  GTNHLib does not ship `jvmdowngrader-java-api`; it downloads it at runtime from
  `maven.wagyourtail.xyz` via its dependency loader, and its system-classloader mirroring is
  skipped whenever the system class loader is not a `URLClassLoader` — always true on Java 9+.
  That would put a network fetch in `LuaString.compareTo`. Shading costs about 5 KB (currently
  `J_L_Record` and `J_U_Arrays`) and is documented in `NOTICE` section 3 with the text in
  `LICENSE-jvmdowngrader`. Every root licence file needs a byte-identical copy in
  `src/main/resources`; `LicenseDistributionTest` enforces it.
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
- Run server: `./gradlew runServer` or `runServer25` — they are the same thing. `runServer` is a
  no-op alias that depends on `runServer25` (Java 25 + lwjgl3ify + HodgePodge), wired up in
  `addon.gradle.kts`, because the GTNH CI workflow hardcodes `runServer` and RFG's Java 8 task
  cannot start a mod that requires lwjgl3ify. Before that alias existed, `runServer` loaded the
  `-dev-preshadow` jar, FML discarded it as unreadable, and the server booted *without*
  ComputerCraft — so the CI smoke test was passing vacuously. `runClient` is **not** aliased; use
  `runClient25`.
- Format: `./gradlew spotlessApply`
- Checkstyle: `./gradlew checkstyleMain`

Tests use JUnit 5 (`useJUnitPlatform()`).
See `TWEAKEDCC_COVERAGE.md` for test coverage and `COBALT_UPGRADE_PLAN.md` for Lua runtime migration details.

---

## External Dependencies

| Dependency | Role |
|---|---|
| com.gtnewhorizons.gtnhconvention | Build plugin |
| cc.tweaked:cobalt:0.9.9 | Lua 5.1/5.2 runtime (shadowed, MIT; published as org.squiddev:Cobalt before the rename) |
| org.java-websocket:Java-WebSocket:1.5.6 | WebSocket client (shadowed, MIT) |
| org.slf4j:slf4j-api:2.0.6 | Transitive of Java-WebSocket; present in the shipped jar but **undeclared** in `dependencies.gradle` (MIT) |
| com.github.GTNewHorizons:NotEnoughItems | Dev-only runtime for testing |
| org.lwjgl:lwjgl-sdl:3.4.2 | SDL3 event API for file drag-and-drop (**compileOnly**: provided at runtime by lwjgl3ify, never shipped; BSD-3-Clause) |
| xyz.wagyourtail.jvmdowngrader:jvmdowngrader-java-api | JDK API stubs, added by the convention as `compileOnly` + `testRuntimeOnly`; the ~3 reachable stubs are shaded under `dan200.computercraft.shadow` (**LGPL-2.1**, `LICENSE-jvmdowngrader`) |
| org.junit.jupiter:junit-jupiter:5.8.2 | Unit testing |
| org.mockito:mockito-core:4.8.0 | Unit testing |

Cobalt Maven repository: `https://maven.squiddev.cc` (see `repositories.gradle`).
Shadowed dependencies must stay listed in `NOTICE` section 3, with their licence
texts present in the repository root.

The JVM Downgrader stubs are the one **LGPL-2.1** component in the jar. They are not
declared in `dependencies.gradle` — the convention pulls
`jvmdowngrader-java-api:downgraded-8` in, and `jvmDowngraderStubsProvider = shade` copies the
minimised set into the jar. Keep `LICENSE-jvmdowngrader` byte-identical at the root and in
`src/main/resources`, and keep the `NOTICE` section 3 entry in step with whatever the downgrader
actually ends up shading: the set changes when the source starts or stops using a Java 9+ API.
`unzip -l` the shipped jar and look for `shadow/xyz/wagyourtail/jvmdg/` to see it.

---

## References & Further Reading

- [TWEAKEDCC_COVERAGE.md](docs/TWEAKEDCC_COVERAGE.md): API/features coverage
- [COBALT_UPGRADE_PLAN.md](docs/COBALT_UPGRADE_PLAN.md): Lua runtime migration
- [NOTICE](NOTICE): provenance and licensing record
- [CODEBASE_ANALYSIS.md](docs/CODEBASE_ANALYSIS.md): decompiler-artifact review
- For legacy/historical notes and upgrade logs, see `COBALT_UPGRADE_PLAN.md` or project history.
