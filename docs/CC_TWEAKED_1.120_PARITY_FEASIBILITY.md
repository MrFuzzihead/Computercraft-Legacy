# CC:Tweaked 1.120.2 → Minecraft 1.7.10 Parity Feasibility Audit

> Follow-up to `docs/TWEAKEDCC_COVERAGE.md`.
>
> Target reference: **CC:Tweaked 1.120.2** (`mc-1.20.x`).
> Local runtime: **Cobalt 0.9.9** (Lua 5.2), **Forge 1.7.10-10.13.4.1614**.
>
> GTNH stack checked: **Hodgepodge**, **lwjgl3ify 3.x (SDL3)**, **GTNHLib**.
>
> Scope: Lua API surface, ROM programs/modules, peripheral surface, and QoL behaviour.
> Rendering/backend-only changes (VBO monitor renderer, lecterns, Create compat, EMI/JEI,
> 1.20 item/block registries, etc.) are explicitly out of scope.

---

## 1. Executive summary

The previous audit listed gaps, but did not separate **“CC:T has this”** from **“1.7.10 can
actually express this”**. After checking the 1.7.10/Forge APIs, the list splits roughly as:

| Class | Approx. share | Meaning |
|---|---:|---|
| ✅ Pure port | ~60% | Pure Lua or ordinary Java; no platform limitation. |
| 🟡 1.7.10 adaptation | ~20% | Possible, but semantics must be approximated (metadata instead of block states, OreDictionary instead of tags, etc.). |
| 🔵 Optional integration | ~10% | Only available when ETFR/HogUtils, GTNHLib, or lwjgl3ify is installed. |
| ❌ Platform-blocked | ~2% | Cannot be matched without a new native/client hook or a different UX. |
| ⏭ Not meaningful | ~5% | Modern feature is deprecated/empty, or cross-version behaviour does not matter on 1.7.10. |
| ✅ Already done | remainder | Do not re-implement. |

The most important corrections to the first pass:

1. **`itemGroups` is not a real gap.** CC:T 1.120’s `ItemDetails.fill` always writes an
   empty list for the deprecated `itemGroups` field. Add `itemGroups = {}` for shape
   compatibility; there is no 1.7.10 “item group” work to do.
2. **Block states are available through GTNHLib, not vanilla 1.7.10.** Vanilla only has
   metadata, and ETFR’s `BlockStateConverter` is structure-NBT-only. GTNHLib’s
   `BlockPropertyRegistry` provides a real block-state API; it registers many vanilla
   blocks, ETFR registers at least pointed dripstone, and mods can register more. Use it
   when present and fall back to `state = { metadata = n }` otherwise.
3. **`tags` is not necessarily impossible.** ETFR depends on **HogUtils**, which provides
   `ItemTags` / `BlockTags`. When ETFR/HogUtils is present, real modern-style tags can be
   exposed via reflection. Without it, OreDictionary is the only 1.7.10 analogue.
   Note: ETFR bundles a specific HogUtils nightly; its API is `Set<String>`-based, while
   newer HogUtils main may use `CachedSupplier`. Target the ETFR-bundled shape first.
4. **`commands.getEntities` is only partially portable.** 1.7.10 has
   `net.minecraft.command.PlayerSelector` (players only), not 1.8+ `@e` entity selectors,
   and it has no 1.11+ entity tags/NBT selectors. A player-only subset is possible; full
   modern selector parity is not.
5. **`file_transfer` works through lwjgl3ify, which is now a declared dependency.**
   Forge 1.7.10’s `GuiScreen` has no `handleFileDrop`, and LWJGL2 has no OS drop callback.
   lwjgl3ify runs the client on SDL3, which exposes `SDL_AddEventWatch` and
   `SDL_EVENT_DROP_FILE`, so a client bridge turns those into the modern `file_transfer` upload —
   **done in Batch J**, against the `org.lwjgl.sdl` API directly. Since the mod ships Java 17+
   bytecode, lwjgl3ify was already an implicit requirement; it is now explicit
   (`required-after:lwjgl3ify`). The AWT `FileDialog` alternative was considered and rejected as a
   different feature.
6. **Some previously “missing” fields are feasible natively:** `nbt` hash, `maxCount`,
   `damage`/`maxDamage`, `displayName`, `durability`, `lore`, `unbreakable`,
   `potionEffects`, and `mapColor` all have 1.7.10 APIs.
7. **SNBT is portable:** the upstream `cc.internal.snbt` module is pure Lua and can be
   ported directly. As a fallback, 1.7.10 also has `JsonToNBT` for basic SNBT-like
   parsing. Full 1.20 SNBT syntax should still be tested, not assumed.
8. **Dimension names are native:** `WorldProvider.getDimensionName()` exists, so
   `commands.getDimension()` and dimension-name arguments are portable.
9. **GPS fixed-channel/dedupe is low priority.** Local `gps host` and `gps locate` are
   internally consistent; the fixed channel matters for cross-version interoperability
   (irrelevant here) and multi-host accuracy. Treat as an optional accuracy fix.
10. **`rednet` ID wrapping is parity, not urgency.** 1.7.10 computer IDs are normally
    small; the 65,500-channel wrap only matters for very large ID ranges.
11. **Hodgepodge is not a direct gap filler.** It is mostly fixes/tweaks and requires
    GTNHLib; the useful piece is the transitive GTNHLib block-state API. It does not add
    tags, entity selectors, or file drop.

---

## 2. Feasibility legend

| Symbol | Meaning |
|---|---|
| ✅ Native | Implementable with 1.7.10/Forge APIs, no new dependency. |
| 🟡 Approximate | Implementable, but 1.7.10 semantics differ; document the difference. |
| 🔵 Optional | Only possible with ETFR/HogUtils or another mod; keep optional/soft. |
| ❌ Blocked | Not feasible without a new native hook or a substantially different UX. |
| ⏭ No-op | Modern behaviour is empty/deprecated, or the outcome does not matter on 1.7.10. |
| ✅ Done | Already at parity; do not redo. |

Batch IDs are defined in §5. Effort: **S** < 1 day, **M** 1–3 days, **L** > 3 days.

---

## 3. Verified platform facts

### Minecraft / Forge 1.7.10

| Fact | Evidence |
|---|---|
| Vanilla has no named block states; only `Block` + `int` metadata. | `World.getBlockMetadata`, `Block` API. |
| `Block.getMapColor(int)` returns `MapColor` with `colorValue`. | `net.minecraft.block.Block`, `net.minecraft.block.material.MapColor`. |
| `ItemStack` has NBT, damage, max damage, max stack, display name, tooltip. | `ItemStack.getTagCompound`, `getMaxDamage`, `getMaxStackSize`, `getDisplayName`, `getTooltip`. |
| Item potion effects are readable. | `ItemPotion.getEffects(ItemStack)`, `PotionEffect`. |
| `JsonToNBT` exists for SNBT-like parsing. | `net.minecraft.nbt.JsonToNBT.func_150315_a(String)`. |
| `PlayerSelector` exists, but only for players. | `net.minecraft.command.PlayerSelector.matchPlayers` returns `EntityPlayerMP[]`. |
| No 1.8+ `@e` entity selector and no 1.11+ entity tags/NBT selectors. | No `EntitySelector` class; no entity `Tags` NBT. |
| `WorldProvider.getDimensionName()` exists. | `net.minecraft.world.WorldProvider`. |
| `CreativeTabs` exists, but 1.13+ item groups do not. | `net.minecraft.creativetab.CreativeTabs`. |
| `OreDictionary` exists. | `net.minecraftforge.oredict.OreDictionary`. |
| No 1.12+ Forge capabilities; use `IInventory` / `IFluidHandler` / CoFH RF interfaces. | Local `InventoryPeripheral`, `GenericFluidPeripheral`, RF adapters already do this. |
| `GuiScreen` has no `handleFileDrop`. | Verified by `javap` on the Forge-patched `GuiScreen`; no file/drop method. |
| LWJGL2 has no GLFW-style file-drop callback. | 1.7.10 client uses LWJGL2; no equivalent API. |

### Cobalt 0.9.9

| Fact | Evidence |
|---|---|
| `_VERSION == "Lua 5.2"`. | Cobalt `BaseLib` sets `_VERSION` to `"Lua 5.2"`. |
| `string.pack` / `string.unpack` exist. | Cobalt `StringLib` registers them; required by the modern `speaker` program. |
| `dofile` is **not** installed by the local BIOS on 5.2. | Local `bios.lua` defines `dofile` only in the `_VERSION == "Lua 5.1"` branch. |
| `table.create` and other 1.119-era Cobalt features are available. | Cobalt 0.9.9 changelog / source. |

### Et Futurum Requiem / HogUtils

| Fact | Evidence |
|---|---|
| ETFR is a 1.7.10 content backport; it does not add its own general block-state API. | ETFR `BlockStateContainer` / `BlockStateConverter` are in `core/utils/structurenbt`, for structure NBT conversion only; ETFR uses GTNHLib block properties where it needs real states. |
| ETFR depends on HogUtils. | ETFR `README.md`. |
| HogUtils provides `ItemTags` and `BlockTags`. | ETFR-bundled path: `roadhog360.hogutils.api.hogtags.helpers.ItemTags` / `BlockTags`; newer main may use `handlers.impl.TagHandlerMeta`. |
| Tag query shape in the ETFR-bundled HogUtils: `ItemTags.getTags(Item, meta)` / `BlockTags.getTags(Block, meta)`, returning `Set<String>`. | ETFR `libs/hogutils-*-sources.jar`, `helpers/ItemTags.java`, `helpers/BlockTags.java`. |
| Newer HogUtils main uses a `CachedSupplier`-based API. | Do not assume one shape; reflect defensively or pin to the ETFR-bundled version. |
| HogUtils is LGPL-3.0. | HogUtils `LICENSE`. |
| ETFR does not add `@e` entity selectors or file drag-and-drop. | No `EntitySelector`/`handleFileDrop` equivalents in ETFR. |

### GTNH stack

| Capability | Hodgepodge | lwjgl3ify | GTNHLib |
|---|---|---|---|
| Block states | No | No | **Yes** — `BlockPropertyRegistry` / `BlockState` |
| OS file drag-and-drop | No | **Platform yes** — SDL3 drop events; no ready MC hook | No |
| Item/block tags | No | No | No — use ETFR/HogUtils |
| Forge-style capabilities | No | No | No — `ItemIO` / `ItemSource` / `ItemSink` is GTNH’s own abstraction |
| NBT helpers | No | No | `ItemStackNBT`, `NBTJson`, FNV hashes — not MD5 NBT hash or SNBT |
| Entity selectors (`@e`) | No | No | No — Brigadier support exists, but no entity selector arguments |
| Brigadier commands | No | No | Yes |
| Java 17+ / LWJGL3 | No | Yes | No |

**Hodgepodge** is a patch/tweak collection. It requires GTNHLib 0.2.2+, but does not
provide block states, tags, selectors, or drag-and-drop itself. GTNH packs normally
include GTNHLib, but standalone installs may not; keep the bridge optional.

**GTNHLib** has a real block-state registry:

- `BlockPropertyRegistry.getBlockState(IBlockAccess world, int x, int y, int z)`
- `BlockPropertyRegistry.getBlockState(Block block, int meta)`
- `BlockState.toMap()` → `Map<String, String>` (stringified values)
- `BlockState.forEachValue(...)` → typed values (`Boolean`, `Number`, enums, strings)
- `BlockStateCommand` (`/blockstate`) for in-game inspection

GTNHLib registers properties for many vanilla blocks; ETFR registers at least pointed
dripstone, and other mods can register their own. This is the best available source for
modern-style `state` tables. It is **opt-in/optional**: blocks with no registered
properties must still fall back to `state = { metadata = n }`. Check the GTNHLib version
shipped by the pack — `blockstate` may be newer than Hodgepodge’s 0.2.2 minimum.

**lwjgl3ify** uses SDL3 (not GLFW) and exposes the SDL bindings on the classpath:

- `SDL_AddEventWatch` / `SDL_RemoveEventWatch`
- `SDL_EVENT_DROP_FILE` / `SDL_EVENT_DROP_TEXT`
- `SDL_DropEvent.dataString()` → dropped path
- `Display.getWindow()` → SDL window handle
- `DisplayEvents.addPostWindowCreateListener(...)`

There is no ready Minecraft file-drop hook, but the platform event exists. A small
optional client bridge plus a GUI drop target can provide CC:T-style upload when
lwjgl3ify is installed.

**Licensing note:** ETFR, HogUtils, Hodgepodge, lwjgl3ify, and GTNHLib are LGPL-3.0.
If you compile against them, add the appropriate notice and keep the dependency optional.
Reflection-only bridges avoid hard dependencies and avoid copying LGPL code. The root
project remains CCPL.

---

## 4. Feasibility matrix

### 4.1 Core runtime, globals, and standard libraries

| Gap | 1.7.10 / Forge | ETFR / other | Verdict | Batch | Effort |
|---|---|---|---|---|---|
| `print` uses no separator and drops `nil` holes | Native Lua; fix in `bios.lua` | — | ✅ Native | A | S |
| `dofile` undefined on Lua 5.2 | Native Lua; define unconditionally | — | ✅ Native | A | S |
| `loadfile(path, env)` only | Native Lua; add `mode` + compatibility | — | ✅ Native | A | S |
| `os.run` prints error, returns only `false` | Native Lua | — | ✅ Native | A | S |
| `os.version()` returns `"CraftOS 1.7"` | Native Lua | — | ✅ Native | A | S |
| `require`/`package` global-only; no `cc.require` | Native Lua/`loadfile` | — | ✅ Native | C | M |
| Missing settings defaults in `bios.lua` | Native Lua | — | ✅ Native | A | S |
| `textutils` `parse_null` / `json_null` / `nbt_style` | Native Lua; port `cc.internal.snbt`, or use `JsonToNBT` as fallback | GTNHLib `NBTJson` is NBT↔JSON, not SNBT | ✅ Native (test SNBT edge cases) | A | S/M |
| `textutils.formatTime(0)` / `urlEncode` / inf-nan | Native Lua | — | ✅ Native | A | S |
| `io` legacy subset | Native over existing fs handles | — | ✅ Native | B | M |
| `fs.complete` ignores `include_hidden` | Native Lua | — | ✅ Native | A | S |
| `keys` missing modern aliases | Native Lua | — | ✅ Native | A | S |
| `term.setPaletteColour(colour, rgb8)` | Native Java | — | ✅ Native | E | S |
| `term.blit` case-sensitive | Native Java | — | ✅ Native | E | S |
| `colors.toBlit` invalid fallback | Native Lua | — | ✅ Native | E | S |
| `paintutils.drawLine` arg-check bug | Native Lua | — | ✅ Native | E | S |
| Missing `cc.base64` | Native Lua | — | ✅ Native | A | S |
| `cc.shell.completion.build` lacks `many`/new signature | Native Lua | — | ✅ Native | C | S |
| Missing `cc.internal.*` | Native Lua | — | ✅ Native | C | M |
| `commands.getDimension()` | `WorldProvider.getDimensionName()` | — | ✅ Native | G | S |
| `commands.getBlockInfo` dimension string | Native `WorldProvider` mapping | — | ✅ Native | G | S |
| `commands.getBlockInfos` order | Native Java | — | ✅ Native | G | S |
| `commands.list` semantics | Native Java | — | ✅ Native | G | S |
| `commands.getEntities` | Only `PlayerSelector` (players) | No ETFR `@e`; GTNHLib Brigadier has no entity selector args | 🟡 Partial | G | M/L |
| `turtle`/`inventory` `nbt` hash | Native NBT | GTNHLib has FNV/NBTJson, not MD5; implement locally | ✅ Native | F | M |
| `turtle`/`inventory` `maxCount`, `damage`, `maxDamage`, `displayName` | Native `ItemStack` | — | ✅ Native | F | S |
| `turtle`/`inventory` `durability` | Native damage/max damage | — | 🟡 Approximate | F | S |
| `turtle`/`inventory` `lore` | Native NBT `display.Lore` | — | 🟡 Approximate (plain strings, not JSON components) | F | S |
| `turtle`/`inventory` `unbreakable` | Native NBT flag | — | ✅ Native | F | S |
| `turtle`/`inventory` `potionEffects` | Native `ItemPotion.getEffects` | — | 🟡 Approximate (names/localisation) | F | M |
| `turtle`/`inventory` `mapColor` | Native `Block.getMapColor` | — | ✅ Native | F | S |
| `turtle`/`inventory` `tags` | No native tags | HogUtils via ETFR; OreDict fallback | 🔵 Optional | F | M |
| `turtle`/`inventory` `itemGroups` | No 1.13 item groups | Modern field is always empty | ⏭ No-op | F | S |
| `turtle.inspect` named block `state` | Vanilla is metadata-only | GTNHLib `BlockPropertyRegistry`; ETFR uses it for some blocks | 🔵 Optional / ✅ with GTNHLib | F | M |
| `commands.getBlockInfo` named block `state` | Vanilla is metadata-only | GTNHLib `BlockPropertyRegistry` | 🔵 Optional / ✅ with GTNHLib | F/G | M |
| `turtle.inspect` `tags` | No native block tags | HogUtils | 🔵 Optional | F | M |
| `inventory.getItemLimit` per-item capacity | Native `ItemStack.getMaxStackSize` + `IInventory` limit | — | ✅ Native | F | S |
| `pocket.equipBack` only modems | Local pocket upgrades exist | — | ✅ Native | M | M |
| `peripheral_hub` type | Native Java/`IMultiTypePeripheral` | — | ✅ Native | M | S |
| `rednet.lookup` timeout | Native Lua | — | ✅ Native | H | S |
| `rednet` ID wrapping above 65,500 | Native Lua | — | ✅ Native | H | S |
| `parallel.spawn` / empty-arg hang / error source | Native Lua | — | ✅ Native | H | M |
| `http` `redirect` option | Native Java | — | ✅ Native | H | S |
| `http.websocket` options/timeout | Native Java | — | ✅ Native | H | S |
| `http` private-IP filtering | Native Java `InetAddress` checks | — | ✅ Native | H | M |
| GPS fixed channel / dedupe | Native Lua | — | ✅ Native | L | S |
| `shell` hashbang | Native Lua | — | ✅ Native | C | M |
| `shell` per-program `require`/`package` | Native Lua + `cc.require` | — | ✅ Native | C | M |
| `shell.openTab` / `switchTab` | Native Lua | — | ✅ Native | C | S |
| `shell` hidden completion / `.lua` stripping | Native Lua | — | ✅ Native | C | S |
| `shell` startup settings / `/startup` directory | Native Lua | — | ✅ Native | C/D | M |
| `shell` error reporting / strict globals | Native Lua + `cc.internal.exception` | — | ✅ Native | C | M |
| `edit` syntax highlighting / multi-line strings | Native Lua | — | ✅ Native | I | L |
| `edit.default_extension` / `edit.autocomplete` | Native Lua | — | ✅ Native | I | S |
| `help` markdown rendering | Native Lua | — | ✅ Native | I | M |
| `lua` REPL pretty printing/settings/relative require | Native Lua | — | ✅ Native | I | M |
| Missing `about` / `motd` / `set` / `peripherals` / `wget` | Native Lua | — | ✅ Native | D | S each |
| Missing `pocket/equip` / `pocket/unequip` | Native Lua | — | ✅ Native | D | S each |
| Missing `fun/speaker` | Native Lua + Cobalt `string.pack` + local `cc.audio.dfpwm` | — | ✅ Native | D | M |
| `clear palette/all` | Native Lua | — | ✅ Native | D | S |
| `monitor scale` | Native Lua | — | ✅ Native | D | S |
| `list.show_hidden` | Native Lua | — | ✅ Native | D | S |
| `copy`/`move`/`delete`/`mkdir` safety/multi-path | Native Lua | — | ✅ Native | D | S/M |
| Missing help docs / `motd.txt` / `modules/turtle` | Native resources | — | ✅ Native | D | S |
| `/computercraft` admin command | Native Forge command | — | ✅ Native | K | M |
| `file_transfer` / native drag-drop import | No Forge/LWJGL2 API | SDL3 drop events via lwjgl3ify (now a declared dependency) | ✅ with lwjgl3ify | J | — |
| Full `commands.getEntities` selector parity | No `@e`, entity tags, NBT selectors | No ETFR `@e`; GTNHLib Brigadier has no entity selector args | ❌/🟡 Partial | G | L |

### 4.2 Terminal, text, and formatting

| Item | Verdict | Notes |
|---|---|---|
| `term.setPaletteColour(colour, rgb8)` | ✅ Native | Add two-argument overload; same for monitor/window forwarding. |
| `term.blit` uppercase hex | ✅ Native | Lowercase/validate case-insensitively. |
| `colors.toBlit` invalid values | ✅ Native | Match modern error behaviour. |
| `paintutils.drawLine` type check | ✅ Native | Fix `startY` validation. |
| `textutils.formatTime` midnight | ✅ Native | 0 → `12:00 AM`, not `0:00 AM`. |
| `textutils.urlEncode` escaping set | ✅ Native | Modern leaves `-_.` unescaped; local escapes them. |
| `textutils.serialize` inf/nan | ✅ Native | Emit `1/0`, `-1/0`, `0/0`. |
| `textutils` `json_null` / `empty_json_array` | ✅ Native | Use immutable sentinels with `__tostring`/`__newindex`. |
| `textutils.unserialiseJSON(parse_null)` | ✅ Native | Rename/alias local `null` option; preserve backwards compatibility. |
| `textutils.unserialiseJSON(nbt_style)` | ✅ Native | Port pure-Lua `cc.internal.snbt`; `JsonToNBT` is a fallback. Test modern SNBT edge cases. |
| `keys` modern aliases | ✅ Native | Add `f16`–`f25`, `leftSuper`, `rightSuper`, `menu`, `printScreen`, `scrollLock`, `semicolon`, `world1`, `world2`, `numPadMultiply`, `numPadEqual`. Keep legacy spellings. |

### 4.3 Commands and block/item detail

| Item | Verdict | Notes |
|---|---|---|
| `commands.getDimension()` | ✅ Native | Map dimension ID 0/-1/1 to modern names; mod dimensions use provider name. |
| Dimension string argument | ✅ Native | Accept `"minecraft:overworld"`, `"minecraft:the_nether"`, `"minecraft:the_end"`, provider name, and legacy integer. |
| `getBlockInfos` order | ✅ Native | Modern order is y→z→x; fix loop order. |
| `commands.list` subcommand path | ✅ Native | Change from prefix filter to child-command listing. |
| `commands.getEntities` | 🟡 Partial | Player selectors only with `PlayerSelector`; a custom scanner can add simple `@e[type=...]` but not `tag=`, `nbt=`, `scores=`. |
| `turtle.inspect` state | 🔵 Optional / 🟡 fallback | Use GTNHLib `BlockPropertyRegistry.getBlockState(world, x, y, z)` and `forEachValue` for typed values. Fallback: `state = { metadata = n }`. |
| `commands.getBlockInfo` state | 🔵 Optional / 🟡 fallback | Same GTNHLib bridge; fallback `{ metadata = n }`. |
| `turtle.inspect` tags | 🔵 Optional | HogUtils `BlockTags` when present; otherwise empty. |
| Item `tags` | 🔵 Optional | HogUtils when present; OreDict fallback or empty otherwise. |
| `itemGroups` | ⏭ No-op | Modern 1.120 always writes an empty list; add `{}` for shape. |
| `nbt` hash | ✅ Native | Sorted NBT + MD5, encoded as lowercase hex (matches CC:T `BaseEncoding.base16().lowerCase()`). GTNHLib’s FNV/NBTJson helpers are not a substitute. |
| `lore` | 🟡 Approximate | 1.7.10 lore strings are plain text, not JSON components. |
| `potionEffects` | 🟡 Approximate | `ItemPotion.getEffects`; localise names from `Potion`; convert ticks to seconds (`/20`). |
| `mapColor` | ✅ Native | Only for block items. Modern `ARGB32.transparent(...)` strips alpha, so the result is a 24-bit RGB value; return `MapColor.colorValue` directly. |
| `durability` | 🟡 Approximate | Compute remaining fraction (`1 - Item.getDurabilityForDisplay(stack)`); modern uses bar width/13. |
| `unbreakable` | ✅ Native | NBT `Unbreakable` boolean. |

### 4.4 Peripherals

| Gap | Verdict | Notes |
|---|---|---|
| `inventory.getItemLimit` | ✅ Native | `min(stack.getMaxStackSize(), inv.getInventoryStackLimit())` for occupied slots; inventory limit for empty. |
| `inventory` beyond `IInventory` | 🔵 Optional | GTNHLib `ItemUtil.getItemIO` can wrap `IInventory`, MFR DSU, and CoFH item ducts. This is a GTNH extension, not a modern `IItemHandler` capability. |
| `inventory`/`turtle` item detail fields | ✅/🟡 | See §4.3. |
| `peripheral_hub` | ✅ Native | Wired modem reports `"modem"` + `"peripheral_hub"`. |
| Remote multi-type | ✅ Native | Propagate all types through `getTypeRemote`; keep primary type first for compatibility. |
| `speaker` program | ✅ Native | Local speaker peripheral + `cc.audio.dfpwm` + Cobalt `string.pack`/`string.unpack`. |
| `monitor`/`printer`/`disk`/`modem` | ✅ Done | No significant Lua surface gaps found. |
| Generic `fluid_storage`/`energy_storage` | ✅ Done | 1.7.10 `IFluidHandler` / CoFH RF adapters already exist. |

### 4.5 Programs, modules, and QoL

| Gap | Verdict | Notes |
|---|---|---|
| `shell` hashbang | ✅ Native | Read first line, resolve interpreter, recurse with depth limit. |
| `shell` per-program `require` | ✅ Native | Port `cc.require.make`; env-local `package`. |
| `shell.openTab`/`switchTab` | ✅ Native | Wrappers over `multishell.launch`/focus. |
| `shell` hidden completion / `.lua` stripping | ✅ Native | `fs.complete` `include_hidden`; trim `.lua` in `shell.programs`. |
| `shell` startup settings / `/startup` directory | ✅ Native | Add `settings.define` defaults; scan `/startup` file or directory. |
| `shell` error reporting / strict globals | ✅ Native | Port `cc.internal.exception`/`error_printer`; optional `bios.strict_globals`. |
| `edit` syntax highlighting | ✅ Native | Port `cc.internal.syntax` lexer/parser; otherwise metadata-only. |
| `edit` multi-line strings/comments | ✅ Native | Pure Lua parser/editor work. |
| `edit.default_extension` / `edit.autocomplete` | ✅ Native | Settings + path resolution. |
| `help` markdown | ✅ Native | Pure Lua markdown section/menu renderer. |
| `lua` REPL | ✅ Native | `cc.pretty`, settings, relative `require`. |
| `about` / `motd` / `set` / `peripherals` / `wget` | ✅ Native | Pure Lua; `wget` requires `http`. |
| `pocket/equip` / `pocket/unequip` | ✅ Native | Pure Lua wrappers. |
| `fun/speaker` | ✅ Native | Pure Lua; uses `string.pack` and DFPWM module. |
| `clear palette/all` | ✅ Native | Pure Lua. |
| `monitor scale` | ✅ Native | Pure Lua. |
| `list.show_hidden` | ✅ Native | Pure Lua + settings default. |
| `copy`/`move`/`delete`/`mkdir` | ✅ Native | Multi-path, read-only/mount-root guards. |
| Help docs / `motd.txt` / `modules/turtle` | ✅ Native | Resource files. |
| `/computercraft` admin command | ✅ Native | Forge `ICommand`; skip 1.20-only subcommands. |
| `file_transfer`/drag-drop | ✅ lwjgl3ify | SDL3 `SDL_AddEventWatch` + `SDL_EVENT_DROP_FILE`, direct API (**done**); lwjgl3ify declared as a required dependency; no AWT fallback, by choice. |
| Full `commands.getEntities` | ❌/🟡 | See §4.3. |

---

## 5. Batching plan

The batches are ordered by dependency, not just priority.

### Batch A — Core BIOS / Lua runtime (P0, ~1–2 days)

> **Status: implemented.** See [`PARITY_BATCH_LOG.md`](PARITY_BATCH_LOG.md) for the public
> API impact, deviations and in-game validation list.
>
> **Version note:** port against the audit's stated target (`mc-1.20.x`, `version=1.120.2`),
> not the local `migrate/CC-Tweaked-mc-26.3` copy — that copy is the newer 1.120.3 / MC 26.3
> line, which inlined the JSON/SNBT parsers into `textutils.lua` and deleted
> `cc/internal/json.lua` + `cc/internal/snbt.lua`. Both files exist at 1.120.2.

Files: `bios.lua`, `rom/apis/textutils`, `rom/apis/keys`,
`rom/modules/main/cc/base64.lua` (new), `rom/modules/main/cc/internal/snbt.lua` (new).

- `print` separator + nil holes.
- `dofile` always defined; `loadfile(path, mode, env)`.
- `os.run` returns `false, err`.
- `os.version` → `"CraftOS 1.9"`.
- `settings.define(...)` defaults.
- `fs.complete` `include_hidden`.
- `textutils` midnight/urlEncode/inf-nan/json_null/parse_null/`nbt_style`
  (port pure-Lua `cc.internal.snbt`).
- `keys` modern aliases.
- `cc.base64`.

**Why first:** everything else (programs, shell, modules) depends on these defaults and
global behaviours.

### Batch B — `io` library (P0, ~1–2 days)

Files: `rom/apis/io`.

- Port upstream `io.lua` onto the existing `fs` handles.
- Add `stdin`/`stdout`/`stderr`, numeric/multi-format reads, `*L`, closed-handle errors,
  `close`/`flush` return values, `input`/`output` returns, `lines` formats, `seek`.
- Keep `r+`/`w+` support already present in `fs`.

**Why second:** independent, but user programs commonly need it.

### Batch C — `cc.require`, shell environment, and shell QoL (P0/P1, ~3–5 days)

Files: `bios.lua`, `rom/modules/main/cc/require.lua`,
remaining `rom/modules/main/cc/internal/*`, `rom/programs/shell`, `rom/startup`.

- Port `cc.require` and `package.searchpath`/`preload`/loaders.
- Give each program an env-local `require`/`package` rooted at its directory.
- Hashbang support.
- `shell.openTab` / `shell.switchTab`.
- Hidden completion, `.lua` stripping, `completion.build(..., many = true)`.
- Shell startup settings, `/startup` directory, MOTD hook.
- Add remaining `cc.internal.*` modules (`exception`, `json`, `error_hints`,
  `error_printer`, `import`, `menu`, `event`, `tiny_require`, `syntax` as needed).
- Modern error reporting/strict globals (needs `cc.internal.exception`).

**Why here:** depends on Batch A; unlocks modern program/module ports.

### Batch D — ROM programs and resources (P1, ~3–5 days)

Files: `rom/programs/*`, `rom/help/*`, `rom/motd.txt`, `rom/modules/turtle`.

- `about`, `motd`, `set`, `peripherals`, `http/wget`, `pocket/equip`,
  `pocket/unequip`, `fun/speaker`.
- `clear palette/all`, `monitor scale`, `list.show_hidden`.
- `copy`/`move`/`delete`/`mkdir` multi-path and read-only/mount guards.
- Missing help topics + `motd.txt` + empty `modules/turtle` folder.

**Why here:** depends on A/C for settings, `io`, `require`, and completion.

### Batch E — Terminal/API polish (P1, ~1 day)

Files: `TermAPI.java`, `MonitorPeripheral`, `rom/apis/window`, `rom/apis/colors`,
`rom/apis/paintutils`.

- `term.setPaletteColour(colour, rgb8)` (term/monitor/window).
- Case-insensitive `term.blit`.
- `colors.toBlit` validation.
- `paintutils.drawLine` arg check.

**Why here:** independent; low risk.

### Batch F — Item/block detail and inventory (P1, ~3–5 days)

Files: `TurtleAPI.java`, `InventoryPeripheral.java`, `TurtleInspectCommand.java`,
`CommandAPI.java`, `NBTUtil.java`, optional `HogUtilsBridge`,
optional `GTNHLibBlockStateBridge`.

- `nbt` hash, `maxCount`, `damage`/`maxDamage`, `displayName`, `durability`, `lore`,
  `unbreakable`, `potionEffects`, `mapColor`.
- `itemGroups = {}` (modern shape).
- `inventory.getItemLimit` per-item capacity.
- `turtle.inspect`/`commands.getBlockInfo` `state`: GTNHLib `BlockPropertyRegistry` when
  present; fallback `{ metadata = n }`.
- Optional HogUtils reflection bridge for `tags`; OreDict fallback.
- Optional GTNHLib `ItemIO` bridge for richer inventory wrappers (not required for parity).

**Why here:** depends on NBT utility; independent of shell.

### Batch G — Commands (P1, ~2–4 days)

Files: `CommandAPI.java`, dimension mapping helper.

- `getDimension`.
- Dimension string arguments (accept integer for compatibility).
- `getBlockInfos` y→z→x order.
- `commands.list` subcommand semantics.
- `getEntities` player-only subset or a custom `@e` scanner with a documented subset.

**Why here:** independent; `getEntities` is the only large piece.

### Batch H — Networking and concurrency (P1, ~2–4 days)

Files: `HTTPRequest.java`, `HTTPAPI.java`, `bios.lua` HTTP wrappers, `rednet`,
`parallel`.

- `http` `redirect` option.
- `http.websocket` options table/timeout.
- Private/CGNAT/link-local IP blocking.
- `rednet.lookup` timeout; `MAX_ID_CHANNELS` wrapping.
- `parallel.spawn`; fix empty-argument hang; add error source info.

**Why here:** independent; `http` IP filtering is security-sensitive.

### Batch I — Editor/REPL/Help (P2, ~1–2 weeks)

Files: `rom/programs/edit`, `rom/programs/lua`, `rom/programs/help`,
`rom/modules/main/cc/internal/syntax/*`, `cc/internal/edit_runner`, `error_hints`,
`error_printer`.

- Full syntax highlighting and multi-line strings/comments in `edit`.
- `edit.default_extension`, `edit.autocomplete`.
- `lua` pretty output/settings/relative `require`.
- `help` markdown sections/menu.

**Why here:** large pure-Lua work; depends on Batch C.

### Batch J — File transfer/import (P3; conditional on lwjgl3ify) — ✅ **DONE**

Implemented with SDL3, which is also what modern ComputerCraft effectively uses:

- `SDL_AddEventWatch` + `SDL_EVENT_DROP_FILE`, called directly through `org.lwjgl:lwjgl-sdl`
  (`compileOnly`) and `SDL_Event.ndrop(...).dataString()`. lwjgl3ify is a **hard** dependency,
  declared in `@Mod` (`required-after:lwjgl3ify`) and `mcmod.info`, which is sound because the mod
  already ships Java 17+ bytecode and so is never run without it.
- Rejected: AWT `FileDialog`. It works everywhere but is a different feature ("choose a file", not
  "drag onto the computer"), and parity is the point.
- Per-screen drop target (`GuiComputer`/`GuiTurtle`/`GuiPocketComputer` via a one-method
  `IComputerGui`), upload limits (`upload_size_limit`, 512 KiB default), checksummed chunked
  upload, `file_transfer` event, ported `rom/programs/import` + `cc/internal/import.lua`, and the
  shell's mid-prompt handler.
- Authorisation: an upload may only target a computer the sender has open, via
  `IContainerComputer` on the legacy containers.
- Recorded limits: no upload toast, drop coordinates ignored, and nothing at all on vanilla
  LWJGL2.

### Batch K — Admin command (P2, ~1 day)

- `/computercraft turn-on|shutdown|track|dump`.
- Skip 1.20-only subcommands (`computer-folder`, etc.) or provide an AWT/OS opener.

### Batch L — GPS accuracy (P2, optional)

- Fixed GPS channel for request/reply.
- Dedupe fixes within 1 block.
- Low priority because local host/locate already interoperate.

### Batch M — Pocket/peripheral type polish (P1, ~1–2 days)

- General `pocket.equipBack` for all local pocket upgrades.
- Wired modem `peripheral_hub` type.
- Remote multi-type propagation.

---

## 6. Revised priority

### P0 — “modern programs actually run”

1. Batch A (BIOS/globals/settings/textutils/keys/base64).
2. Batch B (`io`).
3. Batch C (`cc.require`, shell env, hashbang, openTab, completion).
4. Batch D core programs (`set`, `wget`, `about`, `peripherals`, `motd`,
   `pocket/equip`, `pocket/unequip`).

### P1 — API surface and common user expectations

5. Batch E (term palette/blit/keys).
6. Batch F (item details / inventory limit / optional tags).
7. Batch G (commands dimension/order/list).
8. Batch H (HTTP/rednet/parallel).
9. Batch M (pocket upgrades/peripheral_hub).
10. Batch D remaining QoL (`clear`, `monitor scale`, `list hidden`, file-program safety,
    `speaker`).

### P2 — polish

11. Batch I (edit/lua/help).
12. Batch K (admin command).
13. Batch L (GPS accuracy).

### P3 — deferred/conditional

14. ~~Batch J (file transfer/import).~~ **DONE** — SDL3 via lwjgl3ify, see §5.

---

## 7. Explicit “do not port / do not fake” list

| Item | Why |
|---|---|
| Named block-state properties without GTNHLib | Vanilla 1.7.10 has only metadata. With GTNHLib, use `BlockPropertyRegistry`; otherwise expose `state.metadata`. |
| `itemGroups` contents | CC:T 1.120 always writes an empty list; add `{}` only. |
| Full `@e` selector parity | 1.7.10 lacks `@e`, entity tags, and NBT selectors; a subset is acceptable, full parity is not. |
| Native OS file drag-and-drop on vanilla LWJGL2 | Forge 1.7.10 `GuiScreen` has no `handleFileDrop`; LWJGL2 has no drop callback. **Implemented with SDL3 via lwjgl3ify (Batch J)**; without lwjgl3ify the feature is absent by design rather than replaced with a file chooser. |
| Cross-version GPS channel compatibility | Irrelevant for 1.7.10↔1.20; only multi-host accuracy matters. |
| `rednet` ID wrapping as urgent work | 1.7.10 IDs are normally small; parity-only. |
| 1.20 lecterns/Create/EMI/JEI integrations | Out of scope/platform-specific. |

---

## 8. Optional integrations

### GTNHLib block states

- Soft-depend or reflect on
  `com.gtnewhorizon.gtnhlib.blockstate.registry.BlockPropertyRegistry`.
- `BlockPropertyRegistry.getBlockState(world, x, y, z)` → `BlockState`.
- `BlockState.forEachValue(...)` gives typed values; `toMap()` gives stringified values.
- Use `BlockStatePool` for repeated calls and `close()` states after use.
- Fallback to `state = { metadata = n }` when no properties are registered.
- GTNHLib registers many vanilla blocks; ETFR registers some of its own; other mods can
  register more.
- Optionally use GTNHLib `ItemUtil.getItemIO` to wrap MFR DSUs / CoFH item ducts for the
  generic inventory peripheral. This is an enhancement, not modern capability parity.
- Licensing: GTNHLib is LGPL-3.0; reflection preferred, or optional `compileOnly`.

### lwjgl3ify file drop — ✅ implemented (Batch J)

- Direct use of `org.lwjgl.sdl.*` through a `compileOnly` dependency on
  `org.lwjgl:lwjgl-sdl` (nothing shipped; lwjgl3ify provides it at runtime), plus a hard mod
  dependency on lwjgl3ify in `@Mod` and `mcmod.info`. The mod ships Java 17+ bytecode, so lwjgl3ify
  was already required in practice; this only makes it explicit.
- The callback is an anonymous `SDL_EventFilter` overriding `invoke`. `SDL_AddEventWatch` filters
  `SDL_EVENT_DROP_FILE`; the path comes from `SDL_Event.ndrop(event).dataString()`.
- **`@Lwjgl3Aware` is required on every class that touches real LWJGL3.** lwjgl3ify rewrites
  `org/lwjgl/` to `org/lwjglx/` in mod bytecode so legacy LWJGL2 calls reach its shims; LWJGL3 shares
  that prefix, so an un-annotated class is redirected into a namespace with no SDL and the client
  dies with `NoClassDefFoundError: org/lwjglx/sdl/SDL_EventFilter`. The annotation is per class file,
  so the SDL callback is a named nested class (`DropFilter`) rather than an anonymous one.
  `Lwjgl3AwareCoverageTest` enforces this over the compiled classes.
- A failure to install (broken SDL, uninitialised display) is caught and logged at both the watcher
  and the call site, so a broken native — or a linkage error — cannot take the client tick down.
- Paths are queued from the SDL thread and drained on the client tick, then routed to the open
  `GuiComputer`/`GuiTurtle`/`GuiPocketComputer` screen (`IComputerGui`).
- Sizes are limited by `upload_size_limit` (512 KiB default) on both sides, files are checksummed
  with SHA-256 and uploaded in slices, and the target must be a computer the sender has open.
- Licensing: lwjgl3ify is LGPL-3.0; none of its code was copied, only the public SDL binding is
  called.

### ETFR/HogUtils tags

If the goal is “populate modern `tags` fields when ETFR is installed”:

- Detect `roadhog360.hogutils.api.hogtags.helpers.ItemTags` and `BlockTags` reflectively.
- In the ETFR-bundled HogUtils:
  - `ItemTags.getTags(stack.getItem(), stack.getItemDamage())` → `Set<String>`.
  - `BlockTags.getTags(block, meta)` → `Set<String>`.
- In newer HogUtils main, call `.get()` on the returned supplier if present. Handle both
  shapes defensively, or pin to the ETFR-bundled API.
- Convert to a Lua table `{ ["tag:id"] = true }`.
- Fallback when absent:
  - Items: empty table, or optionally expose OreDictionary names under a documented
    non-standard key.
  - Blocks: empty table.
- Keep the bridge optional; do not hard-depend on ETFR/HogUtils.
- If compiling against HogUtils, add its LGPL-3.0 notice to `NOTICE` and do not copy its
  code. Prefer reflection.

This makes `turtle.getItemDetail`/`inventory.getItemDetail`/`turtle.inspect`/`commands`
`tags` fields useful for ETFR content without making the base mod dependent on ETFR.

---

## 9. Testing notes

- Unit tests:
  - `textutils` `parse_null`, `nbt_style` (SNBT edge cases), `json_null` serialization,
    `formatTime(0)`, `urlEncode` reserved characters, inf/nan.
  - `io` read formats, multi-format reads, closed-handle errors, `input`/`output`
    returns, `lines` formats.
  - `parallel` empty args, `spawn`, error propagation.
  - `fs.complete` `include_hidden`.
  - `term.setPaletteColour(colour, rgb8)`.
  - `keys` aliases.
- In-game scripts:
  - `commands.getDimension`, dimension-name block lookup, `getBlockInfos` ordering.
  - `turtle.getItemDetail` detailed fields, `inventory.getItemLimit`.
  - `pocket.equipBack` with a speaker/ender modem.
  - `fun/speaker` WAV/DFPWM playback.
  - `edit` multi-line strings/syntax highlighting.
  - GTNHLib installed: `turtle.inspect`/`commands.getBlockInfo` state properties;
    without it: metadata fallback.
- Optional integration:
  - ETFR + HogUtils installed: verify `tags` fields; without ETFR/HogUtils, verify empty
    tables and no class-loading errors.
- File transfer (Batch J): the ROM half and the whole server half run headlessly
  (`TransferEventBridgeTest`, `ImportProgramTest`, `docs/ingame-tests/batch-j/j1_transfer.lua`).
  - Manual, lwjgl3ify installed: drag a file onto an open computer GUI, and onto it *while the shell
    prompt is open*; verify the overwrite prompt, the size limit and that a drop with no computer
    open does nothing. Full drill in `docs/ingame-tests/batch-j/README.md`.
  - Without lwjgl3ify: `SdlFileDropWatcherTest` pins that installing is a no-op; confirm in game that
    the log has exactly one "unavailable" line and the mod otherwise behaves normally.

---

## 10. Open decisions

1. **ETFR/HogUtils:** optional reflection bridge or hard optional dependency?
   Recommendation: reflection, optional.
2. **`commands.getEntities`:** player-only subset, custom limited `@e` scanner, or skip?
   Recommendation: implement `@a/@p/@r` + simple `@e[type=...,distance=...]` only if a
   real use case appears.
3. **File import:** implement the lwjgl3ify SDL3 drop path when present, AWT fallback
   otherwise, or defer both?
4. **`os.version`:** return `"CraftOS 1.9"` even though the mod is 1.75-based? Yes, for
   program compatibility.
5. **GPS:** adopt the fixed channel/dedupe now, or leave until multi-host accuracy is a
   reported problem?
6. **`itemGroups`:** add `itemGroups = {}` to match modern shape? Yes, cheap.
7. **Block state:** use GTNHLib `BlockPropertyRegistry` when present, fall back to
   `state.metadata`. Recommendation: optional GTNHLib bridge first; no hand-written
   per-block mappings.
8. **GTNHLib pin:** compileOnly against a version that contains `blockstate`, or reflect
   so no version constraint is added? Recommendation: reflection for runtime, optional
   compileOnly for development.

---

## 11. Bottom line

The previous list was directionally correct but over-weighted a few items that do not
matter on 1.7.10 and under-weighted a few that are feasible:

- **Do first:** BIOS/global fixes, `io`, `cc.require`/shell, settings defaults, missing
  programs. These unlock most modern user code.
- **Do next:** item details (most fields are native), commands dimension/order, HTTP
  hardening, rednet/parallel, pocket upgrades, `peripheral_hub`.
- **Optional:** item/block `tags` via ETFR/HogUtils; block states via GTNHLib; file drop
  via lwjgl3ify; GPS accuracy.
- **Do not promise:** named block states without GTNHLib, full `@e` selectors, native file
  drag-and-drop on vanilla LWJGL2, non-empty `itemGroups`.

---

## 12. Parity audit — terminal, networking, registry, Lua contracts

Re-audit of four areas against `migrate/CC-Tweaked-1.20.1-1.120.2`, done after Batches
A–L and M. Method sets were compared mechanically (`tools/apidiff.js`, which extracts every
`function api.name` / `api.name =` definition from both ROM trees), then every difference was
read by hand — the script alone produces false positives, and two of them are recorded below
precisely because trusting it would have produced wrong claims.

**Method:** `node tools/apidiff.js` for the Lua surface; `@LuaFunction`/`getMethodNames`
extraction for the Java surface; `ModRegistry` for the registry; targeted reading of every
remaining delta.

### 12.1 Terminal

The terminal API is at parity, including the parts that were ported deliberately in earlier
batches. Ours is a superset in one place (legacy palette names) and a subset in two small ones.

| Feature | CC:T 1.120 | Ours | Verdict |
|---|---|---|---|
| `write, scroll, setCursorPos, getCursorPos, setCursorBlink, getCursorBlink, getSize, clear, clearLine` | ✔ | ✔ | parity |
| `setTextColour/Color, getTextColour/Color, setBackgroundColour/Color, getBackgroundColour/Color, isColour/Color` | ✔ | ✔ | parity — both accept a combined bitmask and resolve it to the highest bit |
| `blit(text, textColour, backgroundColour)` | ✔ | ✔ | parity — same-length check, hex digits accepted in either case, cursor advanced |
| `setPaletteColour(colour, rgb24)` and `(colour, r, g, b)` | ✔ | ✔ | parity — ours implements both forms |
| `getPaletteColour(colour)` → floats 0–1 | ✔ | ✔ | parity |
| `nativePaletteColour/nativePaletteColor` | kept on `term`, **not** redirected | kept and **routed through the redirect** | see T1 |
| Invalid colour characters in `blit` | rendered with the default colour | `LuaException("Invalid colour")` | see T2 |
| `window.create(parent, x, y, w, h, visible)` | visible defaults true | same | parity |
| `window`: `write, blit, clear, clearLine, get/setCursorPos, get/setCursorBlink, getSize, getPosition, scroll, set/getTextColour, set/getBackgroundColour, isColour, set/getPaletteColour, getLine, setVisible, isVisible, redraw, restoreCursor, reposition` | ✔ | ✔ | parity |
| `monitor` term surface (25 methods incl. `setTextScale`, `getTextScale`, palette) | inherited from `TermMethods` | explicit list, same 25 | parity |
| `Terminal.write/scroll/clear/clearLine/blit/resize` semantics | — | line-for-line port | parity |
| `term_resize`, cursor blink, 16-colour-on-greyscale, palette persistence | ✔ | ✔ | parity |

**T1 — `term.nativePaletteColour` is redirected in ours.** Upstream pins the two
`nativePalette*` functions onto `term` and deletes them from the native table, so
`term.nativePaletteColour(colour)` always reads the *computer's* palette even while output is
redirected to a monitor. Ours wraps them like any other term function, so after
`term.redirect(monitor)` they act on the monitor. This is arguably more useful, but it is a
behavioural difference: a program that redirects and then restores the computer's palette
shade will get the monitor's.

**T2 — `blit` is stricter than upstream.** `TermAPI.checkColour` rejects anything outside
`0–9a–fA–F`; 1.120 stores the byte and `Terminal.getColour(c, def)` maps unknown characters
to the default (white). A program that blits colour strings containing e.g. `\0`, a space, or
`g` renders in upstream and throws here. Low impact — but it is a throw where upstream has
none, which is the kind of difference that turns into a bug report.

Also noted, not a defect: colour capability comes from `ITerminal.isColour()` for monitors
(correct, colour monitors report true regardless of the computer family) and from
`IAPIEnvironment.isColour()` for the computer's own terminal (also correct).

### 12.2 Networking, peripheral hub, multi-type

This area is at parity after Batch M, with three real divergences in `rednet` and one
cosmetic one in `peripheral`.

| Feature | CC:T 1.120 | Ours | Verdict |
|---|---|---|---|
| `modem.open/isOpen/close/closeAll/transmit/isWireless` | ✔ | ✔ | parity |
| `transmit(channel, replyChannel, payload)` accepting tables | ✔ | ✔ (payload carried as `Object` through `INetwork`) | parity in-process |
| Wired modem: `getNamesRemote, isPresentRemote, getTypeRemote, getMethodsRemote, callRemote, hasTypeRemote, getNameLocal` | ✔ | ✔ | parity |
| Wired modem types | `"modem"` + `"peripheral_hub"` | same (Batch M) | parity |
| `peripheral.getType` returns every type as separate values | ✔ | ✔ | parity |
| `peripheral.hasType` / `peripheral.find` multi-type aware | ✔ | ✔ | parity |
| Remote multi-type (`getTypeRemote` one value per type, `hasTypeRemote` any type) | ✔ | ✔ (Batch M) | parity |
| Peripheral names `type_<id>`, persisted across world loads | ✔ | ✔ (`computer/lastid_<type>.txt`) | parity |
| Wireless range: weather-dependent, altitude-scaled, ender modem interdimensional | ✔ | ✔ | parity |
| `rednet.send/broadcast/receive/host/unhost/open/close/isOpen` | ✔ | ✔ | parity |
| `rednet.MAX_ID_CHANNELS` | **65500**, id → `id % 65500` | **65535**, id → `(id-1) % 65535 + 1` | see N1 |
| `rednet.lookup(protocol, hostname, timeout)` | timeout optional (default 2 s) | hardcoded 2 s timer, no argument | see N2 |
| `peripheral.hasType(side, type)` for an unknown side | `nil` | `false` | see N3 |

**N1 — `MAX_ID_CHANNELS` differs, and ours can collide with the broadcast channel.**
Upstream reserves 65500 channels for computer IDs, so ID 65535 (or 0) never shares a channel
with `CHANNEL_BROADCAST` (65535). Ours reserves 65535 and maps `id → (id-1) % 65535 + 1`, so a
computer whose ID is 65535 computes channel 65535 — the broadcast channel — and would receive
(and could be confused by) every rednet broadcast as if it were addressed to it. IDs that high
are rare (they need 65535 computer creations), but the arithmetic is wrong, not just different:
two different IDs cannot share a channel, and no ID may alias a reserved channel.

**N2 — `rednet.lookup` cannot be given a timeout.** Upstream's third argument overrides the
2 s default; ours starts `os.startTimer(2)` unconditionally. A program that wants a longer
scan for a repeater-hosted service cannot ask for one.

**N3 — `peripheral.hasType` returns `false` where upstream returns `nil`.** Upstream's
`PeripheralAPI.hasType` returns nil when the side name resolves to no peripheral;
ours returns false. Only visible to code that tests the result for nil.

**Deliberate superset:** ours resolves *wired remote names* transparently in
`peripheral.getNames`, `isPresent`, `getType`, `getMethods`, `call`, `wrap` and `hasType` —
1.75 behaviour that 1.120 dropped (upstream only accepts local sides there, and remote access
goes through the modem's `*Remote` methods). Consequences: `peripheral.getNames()` also lists
remotes, and `peripheral.find("monitor")` returns wired monitors. Modern programs still work;
programs written against ours may rely on something modern does not offer. Worth keeping, but
it is a documented difference rather than parity.

`getNamesRemote` ordering is unspecified in both (ours `HashMap`, upstream
`ConcurrentHashMap`); no parity claim is possible or needed.

### 12.3 Blocks and items

The registries match one-for-one. 1.7.10 collapses what 1.20 splits into separate registry
entries into metadata on a handful of blocks, so the comparison is by *concept*, not by
registry name.

| CC:T 1.120 registry entry | Ours | Notes |
|---|---|---|
| `computer_normal`, `computer_advanced` | `computer` block (metadata) | `ItemComputer.getFamily`: bit 16384 = advanced |
| `computer_command` | `command_computer` | separate block |
| `turtle_normal`, `turtle_advanced` | `CC-TurtleExpanded`, `CC-TurtleAdvanced` (+ legacy `CC-Turtle`) | |
| `monitor_normal`, `monitor_advanced` | `peripheral` metadata 10 / 12 | |
| `disk_drive` | `peripheral` metadata 2–5 | facing in metadata |
| `printer` | `peripheral` metadata 11 | |
| `wireless_modem_normal`, `wireless_modem_advanced` | `peripheral` metadata 0/1, `CC-AdvancedWirelessModem` | |
| `wired_modem`, `cable` | `CC-Cable` (WiredModem / WiredModemWithCable / Cable) | |
| `wired_modem_full` | `CC-WiredModemFull` + `TileWiredModemFull` | full cube, six-face modem/hub |
| `speaker`, `redstone_relay` | `speaker`, `CC-RedstoneRelay` | |
| `lectern` | — | N/A: upstream extends the vanilla 1.14+ lectern |
| `pocket_computer_normal`, `pocket_computer_advanced` | `pocketComputer` (damage 0/1) | upgrades are NBT, as upstream |
| `disk`, `treasure_disk` | `disk`, `treasureDisk` | |
| `printed_page`, `printed_pages`, `printed_book` | `printout` (damage 0/1/2) | |
| — | `diskExpanded`, `chat_box`, `npc_detector`, `npc_interface`, `npc_trader` | ours-only: custom disk size, the chat box (removed upstream in 1.99), CustomNPCs compat |

No upstream block or item is missing now that `wired_modem_full` is ported. The only upstream
block with no 1.7.10 counterpart is `lectern`: CC:Tweaked's `CustomLecternBlock` extends the
vanilla 1.14+ lectern, which this platform does not have. Porting it would mean adding a new
lectern block, not restoring parity; the pocket-computer-on-a-lectern behaviour is a separate
feature, not a missing block.

#### Full wired modem

`CC-WiredModemFull` is a full cube with no facing. Its tile entity is a `TileCable` subtype that
owns one `ModemState`, six per-face `TileCable.Peripheral` modems and six
`WiredModemLocalPeripheral`s, mirroring CC:Tweaked's `WiredModemFullBlockEntity`. Right-clicking
toggles all six local peripherals; the names are `type_id` and persist as `PeripheralIdN` /
`PeripheralTypeN` NBT, with the enabled state in `PeripheralEnabled`. The block reports
`WiredModemWithCable` as its peripheral type, participates in the cable BFS through
`BlockCable.isCable`, and uses the four existing `wiredModemFace*` textures for the
modem-off/modem-on/peripheral states. The wall modem and full modem convert shapelessly in both
directions.

Block-peripheral surface: the wired modem's peripheral now implements `IPeripheralTargeted`
(`getTarget()`), matching upstream's `getTarget`, and `ComputerPeripheral` gained `getLabel()`.
`pocket_computer_no_term`, `pocket_computer_lectern`, `computer_upgrade` and
`pocket_computer_upgrade` are menu types and recipe serialisers in 1.120, not items, so they have
no counterpart to check.

### 12.4 Lua API contracts

Method sets are identical across `colors/colours`, `fs`, `io`, `keys`, `parallel`,
`peripheral`, `paintutils`, `rednet`, `settings`, `term`, `textutils`, `turtle`, `vector`,
`window`, `command/commands` and `help`. Verified in detail:

- **`fs`** — `list, combine, getName, getSize, exists, isDir, isReadOnly, makeDir, move, copy,
  delete, open, getDrive, getFreeSpace, find, getDir, getCapacity, attributes, isDriveRoot`
  plus `fs.complete` (ours lives in `bios.lua`, upstream in `fs.lua`; ours already accepts the
  1.101 `{ include_files, include_dirs, include_hidden }` options table). `fs.attributes`
  returns `size, isDir, isReadOnly, created, modified` and keeps the deprecated
  `modification` alias, exactly as upstream.
- **`textutils`** — all twelve functions, including `serialize`/`serializeJSON` options
  (`compact`, `allow_repetitions`, `nbt_style`, `unicode_strings`) and
  `unserializeJSON` options (`parse_null`, `parse_empty_array`). `slowWrite`'s `rate` is in
  characters per second with a default of 20 in both.
- **`settings`** — `define/set/get(name, default)/unset/clear/load/save/getDetails/changed`,
  with the same `{ default, description, type, changed }` option fields.
- **`io`** — handle metatable, `open` modes, `output/input`, `lines`, `read` format list;
  ours is a port of the same Lua implementation.
- **`parallel`, `vector`, `gps`, `turtle`, `disk`, `paintutils`, `colors`** — identical
  method sets (`colors.rgb8`, `packRGB`, `unpackRGB`, `toBlit`, `fromBlit` all present).
- **`commands`** — same eight methods (`exec`, `execAsync`, `list`, `getBlockPosition`,
  `getBlockInfo`, `getBlockInfos`, `getDimension`, `getEntities`). Upstream's block methods
  take an optional trailing `dimension` argument; 1.7.10 has a single world, so ours omits it.

Two contract differences stand out, both in `http`:

**L1 — `http.request` positional arguments disagree, and there is no options table.**
Upstream's positional form is `request(url, body, headers, binary)` and it also accepts a
single options table `{ url, body, headers, binary, method, redirect, timeout }`. Ours kept the
legacy-extended positional form `request(url, data, headers, verb, timeout, binary, ...)`, so:

- a modern program calling `http.request(url, body, headers, true)` intending **binary**
  instead sets our **verb** (a boolean is not a String, so it is ignored and the argument is
  silently lost) and receives a text handle instead of a binary one;
- the options-table form is not supported at all — `http.request{ url = ... }` fails with
  "Expected string".

This is the one place found where a modern program is likely to break silently rather than
with an error, which makes it the highest-value fix in this audit.

**L2 — `http.fetch` is ours-only.** Harmless extra (an alias for `request`); no upstream
program can depend on it, and nothing in the ROM uses it.

### 12.5 What the audit did *not* find

For the record, since a parity audit that only lists gaps is misleading:

- No missing terminal API method, window method, monitor method or palette feature.
- No missing `modem`, wired-modem or `rednet` function.
- No missing block or item after the `wired_modem_full` port; `lectern` is excluded as N/A because
  it extends a vanilla block this platform does not have.
- No missing Lua API function in any ROM API.
- Keyboard key *names* differ only because of the platform (`F17`–`F25`, `leftSuper`,
  `rightSuper`, `menu`, `printScreen`, `world1`, `world2` are GLFW/LWJGL3 codes that 1.7.10
  never produces; ours instead carries the LWJGL2 punctuation names `at`, `colon`,
  `semiColon`, `multiply`, `kana`, …). This is a platform limit, not a code gap.

### 12.6 False positives worth recording

The mechanical diff initially reported `fs.complete`, `fs.find`, `fs.isDriveRoot`,
`keys.cimcumflex` and `keys.scollLock` as missing. All five exist:

- `fs.complete` is defined in `bios.lua`, not in an `apis/` file;
- `fs.find` and `fs.isDriveRoot` are Java methods (`FSAPI`), not Lua;
- the misspelled key constants are table *values* (`scollLock = "scrollLock"`) in our
  constructor rather than `keys.scollLock = keys.scrollLock` assignments.

Upstream keeps the misspellings deliberately, and so do we. Two audits in a row have now been
saved by checking a "missing" claim before writing it down; the lesson from Batch L applies to
tooling as much as to test skips.

### 12.7 Suggested follow-up

Ranked by user impact per unit of risk:

1. **`http.request` options table + binary in the 4th positional slot** (L1). The only
   silent-misbehaviour finding. Keep the legacy arguments working by detecting the argument
   shape (boolean in slot 4 = binary) and accepting a table first argument.
2. **`rednet` channel arithmetic** (N1). Three lines: `MAX_ID_CHANNELS = 65500` and
   `id % MAX_ID_CHANNELS`. Fixes a real, if rare, collision with the broadcast channel.
3. **`rednet.lookup(protocol, hostname, timeout)`** (N2). One optional argument.
4. **`blit` colour tolerance** (T2). Match upstream by mapping unknown characters to the
   default colour instead of throwing.
5. **`term.nativePaletteColour` pinning** (T1) and **`hasType` nil-vs-false** (N3).
   Cosmetic; do them only alongside something else.

Items 1–4 are small enough to form a single "Batch N — contract alignment" with unit tests and
an in-game script; none of them touches the Lua runtime, persistence or rendering.

---

## 13. Error reporting on the terminal

Reported from a side-by-side run (`migrate/screenshots/ModernCCTError.png` against
`migrate/screenshots/CC1710Error.png`): the same REPL mistake produced two different screens.

Upstream:

```
lua> x = peripheral.wrap("right")
lua> x.getType()
lua[3]:1: attempt to call field 'getType' (a nil value)
Line 1
x.getType()
    ‾‾‾‾
lua> _
```

Here, before the fix:

```
lua> x = peripheral.wrap("right")
lua> x.getType()
lua:1: attempt to call field 'getType' (a nil value)
lua>
```

Two differences, one cause each — and both turned out to be wiring, not missing code.

### 13.1 Cause: the REPL threw away its own source text

`cc.internal.exception.report` is what draws the `Line N` block, the echoed source and the
caret. It needs to recover the user's text for the line that failed, and it has exactly two
ways to do that:

1. a **source map** entry for the failing chunk (`{ contents = ..., offset = ... }`), used by
   the REPL, where the code never existed as a file;
2. otherwise, a chunk whose name starts with `@/`, in which case it **re-reads the file from
   disk** — the path a crashing program takes.

`rom/programs/lua` was doing neither: every input was compiled as the chunk name `"lua"`, and
`report` was called with an empty table. So `report` looked for `source_map["=lua"]`, did not
find it, then checked for a `@/` source (no — a `load()`ed REPL chunk is `=lua`), and returned
early without printing anything. Only the plain `printError` line survived.

The chunk name also explains the lost `[3]`: upstream compiles each input as `=lua[N]`, so the
error is prefixed `lua[3]:1:` and a short program's output can be traced back to its input.

Fixed by adopting upstream's shape exactly:

```lua
local name, offset = "=lua[" .. chunk_idx .. "]", 0
local func, e = load(input, name, "t", tEnv)
if load("return " .. input) then
    func = load("return _echo(" .. input .. "\n)", name, "t", tEnv)
    offset = 13                      -- length of "return _echo("
end
if func then
    chunk_map[name] = { contents = input, offset = offset }
    chunk_idx = chunk_idx + 1
    ...
        printError(results[2])
        exception.report(results[2], results[3], chunk_map)
```

The `offset` is the reason the caret lands under the user's token rather than 13 columns to the
right: `report` subtracts it from the column Cobalt reports, because the failing expression was
really compiled inside `return _echo(`.

The same change fixes the **syntax error** branch for free. Upstream routes it through
`cc.internal.syntax.parse_repl`, which prints the parser's own annotation; ours echoed the
loader's message. Our `cc/internal/syntax/init.lua` is byte-identical to upstream, so this was a
one-line call-site change, not a port.

### 13.2 `edit` Run was reporting against the wrong file

`edit`'s Run saved the buffer to `/.temp` and opened a tab on it. A runtime error was therefore
reported by `shell` as `/.temp:3: ...`, the tab closed immediately, and the user had no chance
to read it. Upstream writes a one-line bootstrap instead and lets `cc.internal.edit_runner` load
the **buffer** under the **real path**, report against it, and then wait for a key:

```lua
local runHandler = [[return require("cc.internal.edit_runner")(%q, %q, %q)]]
file.write(runHandler:format(sTitle, "@/" .. sPath, table.concat(tLines, "\n")))
```

`cc/internal/edit_runner.lua` was the one module in `cc/internal/` we had never ported (upstream
added it in 2025). It is now ported, and `edit`'s Run writes the bootstrap to
`.temp.<title>` next to the file (or `.temp.<title>` in the current directory for a read-only
file), deletes it, and switches to the tab.

Result: a failing script now reports against its own name and line, with the caret, and the tab
stays open until a key is pressed.

### 13.3 Re-check of every other error surface

The question "is this the only place?" was answered by reading every path that prints an error,
not by assuming:

| Surface | Path | Verdict |
|---|---|---|
| program crash | `shell.run` → `printError` + `exception.report(err, co)` | **parity** — identical to upstream, reads the source from disk |
| program syntax error | `shell.run` → `cc.internal.syntax.parse_program` | **parity** |
| `lua` runtime error | `rom/programs/lua` | **fixed** (§13.1) |
| `lua` syntax error | `rom/programs/lua` | **fixed** (§13.1) |
| `edit` Run | `cc.internal.edit_runner` | **fixed** (§13.2) |
| `os.run`, `dofile`, `loadfile` | `bios.lua` | **parity** — same structure and messages |
| API load failure (`os.loadAPI`) | `bios.lua` | **parity** |
| BIOS/startup failure | `bios.lua` | **parity** (`printError`) |
| `cc.internal.error_printer` | module | **byte-identical** to upstream |
| `cc.internal.error_hints` | module | **byte-identical** |
| `cc.internal.syntax` | module | **byte-identical** |
| `cc.internal.exception` | module | **identical except one local fix**: upstream never closes the file handle it opens to read the failing line, leaking a handle (and blocking deletion) on every reported error. Ours closes it. |
| `cc.internal.event` | module | ported; comments differ only |
| `multishell` | — | no error handling by design; the shell inside the tab reports |

### 13.4 Verification

* `ReplErrorDisplayTest` drives the **real BIOS, real ROM and real terminal** with simulated
  keystrokes, and pins the four shapes that the screenshots compare:
  - the chunk is named `lua[2]` (not `lua`), and the annotated block is drawn;
  - the block echoes the input that failed (`t.getType()`);
  - the caret lands **inside** the echoed line, which is the offset arithmetic an empty source
    map used to skip entirely;
  - a statement (offset 0 path) is annotated too, and a **program crash** is annotated from the
    file on disk (`boom:3:` / `Line 3` / the line's text);
  - a syntax error goes through the ROM parser.
* `EditRunnerBootstrapTest` reads the `runHandler` literal **out of `rom/programs/edit`** (so it
  cannot drift from production), formats it the way Run does, and checks the bootstrap loads and
  carries the buffer through unchanged — including quotes, tabs, newlines and a `%`.
* The existing ROM compile test already loads every ROM file with `loadstring`, so `edit`,
  `lua` and the new `edit_runner.lua` are all syntax-checked on every build.

### 13.5 One more false alarm, recorded

The first version of the bootstrap test failed with
`nesting of [[...]] is deprecated near '['`. That was **the test's fault**, not the code's: it
built the bootstrap as `([[...]]):format(...)`, and it put a buffer containing `[[long string]]`
inside a `[[...]]` literal. Cobalt's lexer rejects nested long brackets. Production assigns the
handler to a local first (exactly as upstream does) and never nests, so there was nothing to fix —
but the test now reads the production literal directly, which removes the possibility of testing
a different construct than the one that ships.

That is the third time in this project a "difference" has survived a check it should not have
(Batch H's swallowed `LuaException`, Batch L's skip hiding a broken assertion, and now a test
construct masquerading as a code defect). The rule that keeps catching them: reproduce from the
screenshots, then read the code that produces the output before believing the output.

### 13.6 Follow-up: the annotation glyphs printed as `?`

With the annotation wired up, the block appeared but the rule and the indicator rendered as
`?`:

```
lua[2]:1: attempt to call field 'getType' (a nil value)
?Line 1
?t.getType()
?
```

`cc.internal.error_printer` draws with two characters from **CC:Tweaked's own font**:

| Code | What CC:T's font has there | Used for |
|---|---|---|
| `0x95` | a solid bar three pixels wide, full cell height, at the **left** edge | the cyan rule beside each annotated line |
| `0x83` | a solid bar filling the cell width and the **top** third of its height | the mark under the token that failed — being at the top of its cell, it touches the source line above and reads as a thick underline |

Our renderer does not use a code-indexed font: it maps characters through a CP437 table into
**Minecraft's `ascii.png`**, so a character with no slot there resolves to `?`. `0x95` and `0x83`
have no slot, which is the whole bug. (The two other high characters the printer uses, `\xab`
and `\xbb`, are `«`/`»` and do render.)

Checked against the actual atlases — CC:T's `term_font.png` decoded glyph by glyph, and
Minecraft's `ascii.png` for the substitutes (`tools/fontglyph.js` reads both) — the closest
drawable glyphs are:

* `0x95` → **`|`** — a vertical bar, left-aligned in its cell, exactly where CC:T's bar sits
  (thinner, and broken at the baseline, but a rule);
* `0x83` → **`^`** — anchored at the **top** of its cell like CC:T's bar, so it still sits against
  the source line above. `_` was the alternative and is worse: it is drawn at the *bottom* of the
  cell, one full line below the token, which reads as an underline for the next line.

Both live in one place, `FixedWidthFontRenderer.buildCharIndex`, so the ROM stays byte-identical
to upstream — the substitution is a font-coverage decision, not a change to what the ROM emits.

The result:

```
lua[2]:1: attempt to call field 'getType' (a nil value)
|Line 1
|t.getType()
|        ^
```

Structurally identical to upstream; the rule is thinner and the indicator is a caret rather than
a solid bar. That is the honest limit of a vanilla-font build: matching the thickness would mean
shipping CC:T's 256-glyph font and re-indexing every character, which would turn the accented
Latin text the legacy ROM uses (and the CP437 set) into box-drawing glyphs.

**Guard:** `FixedWidthFontRendererGlyphTest` pins the two mappings *and* scans
`cc.internal.error_printer` for the high characters it emits, asserting each one has a glyph. If
upstream ever starts using a third font-only character, that test fails instead of the terminal
quietly printing `?`.
