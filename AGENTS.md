decompiled source.

# AGENTS.md — Computercraft-Legacy (Optimized for Agentic Use)

Decompiled and patched ComputerCraft for Minecraft 1.7.10. All changes are made directly to the decompiled source (no Mixins/Access Transformers).

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

- Use modern Java syntax (Java 11–17 via Jabel; target JVM 8).
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
- Format: `./gradlew spotlessApply`
- Checkstyle: `./gradlew checkstyleMain`

Tests use JUnit 5 (`useJUnitPlatform()`).
See `TWEAKEDCC_COVERAGE.md` for test coverage and `COBALT_UPGRADE_PLAN.md` for Lua runtime migration details.

---

## External Dependencies

| Dependency | Role |
|---|---|
| com.gtnewhorizons.gtnhconvention | Build plugin |
| org.squiddev:Cobalt:0.6.0 | Lua 5.1 runtime (shadowed) |
| org.java-websocket:Java-WebSocket:1.5.6 | WebSocket client (shadowed) |
| com.github.GTNewHorizons:ForgeMultipart | Multipart peripheral support |
| com.github.GTNewHorizons:CodeChickenCore | Required by ForgeMultipart |
| com.github.GTNewHorizons:NotEnoughItems | Dev-only runtime for testing |
| org.junit.jupiter:junit-jupiter:5.8.2 | Unit testing |

Cobalt Maven repository: `https://maven.squiddev.cc` (see `repositories.gradle`).

---

## References & Further Reading

- [TWEAKEDCC_COVERAGE.md](TWEAKEDCC_COVERAGE.md): API/features coverage
- [COBALT_UPGRADE_PLAN.md](COBALT_UPGRADE_PLAN.md): Lua runtime migration
- For legacy/historical notes and upgrade logs, see `COBALT_UPGRADE_PLAN.md` or project history.
