# CC:Tweaked 1.120.2 → Minecraft 1.7.10 Parity Feasibility Audit

> Follow-up to `docs/TWEAKEDCC_COVERAGE.md`.
>
> Target reference: **CC:Tweaked 1.120.2** (`mc-1.20.x`).
> Local runtime: **Cobalt 0.9.9** (Lua 5.2), **Forge 1.7.10-10.13.4.1614**.
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
| 🔵 Optional integration | ~5% | Only available when ETFR/HogUtils or another mod is installed. |
| ❌ Platform-blocked | ~5% | Cannot be matched without a new native/client hook or a different UX. |
| ⏭ Not meaningful | ~5% | Modern feature is deprecated/empty, or cross-version behaviour does not matter on 1.7.10. |
| ✅ Already done | remainder | Do not re-implement. |

The most important corrections to the first pass:

1. **`itemGroups` is not a real gap.** CC:T 1.120’s `ItemDetails.fill` always writes an
   empty list for the deprecated `itemGroups` field. Add `itemGroups = {}` for shape
   compatibility; there is no 1.7.10 “item group” work to do.
2. **Block states cannot be matched generically.** 1.7.10 has metadata, not named block
   states. ETFR’s `BlockStateConverter` is only for reading structure NBT; it is not a
   general state API. Use `state = { metadata = n }` and document the limitation.
3. **`tags` is not necessarily impossible.** ETFR depends on **HogUtils**, which provides
   `ItemTags` / `BlockTags`. When ETFR/HogUtils is present, real modern-style tags can be
   exposed via reflection. Without it, OreDictionary is the only 1.7.10 analogue.
   Note: ETFR bundles a specific HogUtils nightly; its API is `Set<String>`-based, while
   newer HogUtils main may use `CachedSupplier`. Target the ETFR-bundled shape first.
4. **`commands.getEntities` is only partially portable.** 1.7.10 has
   `net.minecraft.command.PlayerSelector` (players only), not 1.8+ `@e` entity selectors,
   and it has no 1.11+ entity tags/NBT selectors. A player-only subset is possible; full
   modern selector parity is not.
5. **`file_transfer` is platform-blocked, not just missing.** Forge 1.7.10’s `GuiScreen`
   has no `handleFileDrop`, and LWJGL2 has no GLFW-style OS drop callback. ETFR does not
   add one. An AWT `FileDialog` alternative is possible, but it is not the modern UX.
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
| No named block states; only `Block` + `int` metadata. | `World.getBlockMetadata`, `Block` API. |
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
| ETFR is a 1.7.10 content backport; it does not add a general block-state API. | ETFR `BlockStateContainer` / `BlockStateConverter` are in `core/utils/structurenbt`, for structure NBT conversion only. |
| ETFR depends on HogUtils. | ETFR `README.md`. |
| HogUtils provides `ItemTags` and `BlockTags`. | ETFR-bundled path: `roadhog360.hogutils.api.hogtags.helpers.ItemTags` / `BlockTags`; newer main may use `handlers.impl.TagHandlerMeta`. |
| Tag query shape in the ETFR-bundled HogUtils: `ItemTags.getTags(Item, meta)` / `BlockTags.getTags(Block, meta)`, returning `Set<String>`. | ETFR `libs/hogutils-*-sources.jar`, `helpers/ItemTags.java`, `helpers/BlockTags.java`. |
| Newer HogUtils main uses a `CachedSupplier`-based API. | Do not assume one shape; reflect defensively or pin to the ETFR-bundled version. |
| HogUtils is LGPL-3.0. | HogUtils `LICENSE`. |
| ETFR does not add `@e` entity selectors or file drag-and-drop. | No `EntitySelector`/`handleFileDrop` equivalents in ETFR. |

**Licensing note:** ETFR and HogUtils are LGPL-3.0. If you compile against them, add the
appropriate notice and keep the dependency optional. A reflection-only bridge avoids a
hard dependency and avoids copying LGPL code. The root project remains CCPL.

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
| `textutils` `parse_null` / `json_null` / `nbt_style` | Native Lua; port `cc.internal.snbt`, or use `JsonToNBT` as fallback | — | ✅ Native (test SNBT edge cases) | A | S/M |
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
| `commands.getEntities` | Only `PlayerSelector` (players) | No ETFR `@e` | 🟡 Partial | G | M/L |
| `turtle`/`inventory` `nbt` hash | Native NBT | — | ✅ Native | F | M |
| `turtle`/`inventory` `maxCount`, `damage`, `maxDamage`, `displayName` | Native `ItemStack` | — | ✅ Native | F | S |
| `turtle`/`inventory` `durability` | Native damage/max damage | — | 🟡 Approximate | F | S |
| `turtle`/`inventory` `lore` | Native NBT `display.Lore` | — | 🟡 Approximate (plain strings, not JSON components) | F | S |
| `turtle`/`inventory` `unbreakable` | Native NBT flag | — | ✅ Native | F | S |
| `turtle`/`inventory` `potionEffects` | Native `ItemPotion.getEffects` | — | 🟡 Approximate (names/localisation) | F | M |
| `turtle`/`inventory` `mapColor` | Native `Block.getMapColor` | — | ✅ Native | F | S |
| `turtle`/`inventory` `tags` | No native tags | HogUtils via ETFR; OreDict fallback | 🔵 Optional | F | M |
| `turtle`/`inventory` `itemGroups` | No 1.13 item groups | Modern field is always empty | ⏭ No-op | F | S |
| `turtle.inspect` named block `state` | Metadata only | ETFR converter is structure-only | 🟡 Approximate | F | S/M |
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
| `file_transfer` / native drag-drop import | No Forge/LWJGL2 API | No ETFR equivalent | ❌ Blocked | J | L |
| Full `commands.getEntities` selector parity | No `@e`, entity tags, NBT selectors | No ETFR equivalent | ❌/🟡 Partial | G | L |
| Named block states in inspect/commands | No block-state API | ETFR only structure NBT | 🟡 Metadata only | F | S/M |

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
| `turtle.inspect` state | 🟡 Metadata | `state = { metadata = n }`; never fake named properties generically. |
| `turtle.inspect` tags | 🔵 Optional | HogUtils `BlockTags` when present; otherwise empty. |
| Item `tags` | 🔵 Optional | HogUtils when present; OreDict fallback or empty otherwise. |
| `itemGroups` | ⏭ No-op | Modern 1.120 always writes an empty list; add `{}` for shape. |
| `nbt` hash | ✅ Native | Sorted NBT + MD5, encoded as lowercase hex (matches CC:T `BaseEncoding.base16().lowerCase()`). |
| `lore` | 🟡 Approximate | 1.7.10 lore strings are plain text, not JSON components. |
| `potionEffects` | 🟡 Approximate | `ItemPotion.getEffects`; localise names from `Potion`; convert ticks to seconds (`/20`). |
| `mapColor` | ✅ Native | Only for block items. Modern `ARGB32.transparent(...)` strips alpha, so the result is a 24-bit RGB value; return `MapColor.colorValue` directly. |
| `durability` | 🟡 Approximate | Compute remaining fraction (`1 - Item.getDurabilityForDisplay(stack)`); modern uses bar width/13. |
| `unbreakable` | ✅ Native | NBT `Unbreakable` boolean. |

### 4.4 Peripherals

| Gap | Verdict | Notes |
|---|---|---|
| `inventory.getItemLimit` | ✅ Native | `min(stack.getMaxStackSize(), inv.getInventoryStackLimit())` for occupied slots; inventory limit for empty. |
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
| `file_transfer`/drag-drop | ❌ Blocked | No Forge/LWJGL2 API; AWT `FileDialog` is an alternative, not parity. |
| Full `commands.getEntities` | ❌/🟡 | See §4.3. |

---

## 5. Batching plan

The batches are ordered by dependency, not just priority.

### Batch A — Core BIOS / Lua runtime (P0, ~1–2 days)

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
`CommandAPI.java`, `NBTUtil.java`, optional `HogUtilsBridge`.

- `nbt` hash, `maxCount`, `damage`/`maxDamage`, `displayName`, `durability`, `lore`,
  `unbreakable`, `potionEffects`, `mapColor`.
- `itemGroups = {}` (modern shape).
- `inventory.getItemLimit` per-item capacity.
- `turtle.inspect` `state = { metadata = n }` and optional `tags`.
- Optional HogUtils reflection bridge for `tags`; OreDict fallback.

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

### Batch J — File transfer/import (P3, blocked)

- No Forge/LWJGL2 drag-drop API; ETFR does not add one.
- Option A: defer.
- Option B: AWT `FileDialog`-based import (different UX), plus `file_transfer`-shaped
  event/API only if we define our own contract.
- Do not promise modern `file_transfer` parity.

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

### P3 — blocked/deferred

14. Batch J (file transfer/import).

---

## 7. Explicit “do not port / do not fake” list

| Item | Why |
|---|---|
| Named block-state properties | 1.7.10 has only metadata; ETFR’s converter is structure-NBT-only. Expose `state.metadata` and document. |
| `itemGroups` contents | CC:T 1.120 always writes an empty list; add `{}` only. |
| Full `@e` selector parity | 1.7.10 lacks `@e`, entity tags, and NBT selectors; a subset is acceptable, full parity is not. |
| Native OS file drag-and-drop | Forge 1.7.10 `GuiScreen` has no `handleFileDrop`; LWJGL2 has no drop callback. Use AWT or skip. |
| Cross-version GPS channel compatibility | Irrelevant for 1.7.10↔1.20; only multi-host accuracy matters. |
| `rednet` ID wrapping as urgent work | 1.7.10 IDs are normally small; parity-only. |
| 1.20 lecterns/Create/EMI/JEI integrations | Out of scope/platform-specific. |

---

## 8. Optional ETFR/HogUtils integration

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
- Optional integration:
  - ETFR + HogUtils installed: verify `tags` fields; without ETFR/HogUtils, verify empty
    tables and no class-loading errors.
- Manual:
  - `file_transfer` has no automated test; if an AWT alternative is added, test on
    Windows/Linux/macOS clients.

---

## 10. Open decisions

1. **ETFR/HogUtils:** optional reflection bridge or hard optional dependency?
   Recommendation: reflection, optional.
2. **`commands.getEntities`:** player-only subset, custom limited `@e` scanner, or skip?
   Recommendation: implement `@a/@p/@r` + simple `@e[type=...,distance=...]` only if a
   real use case appears.
3. **File import:** defer, or add AWT file chooser as a non-parity alternative?
4. **`os.version`:** return `"CraftOS 1.9"` even though the mod is 1.75-based? Yes, for
   program compatibility.
5. **GPS:** adopt the fixed channel/dedupe now, or leave until multi-host accuracy is a
   reported problem?
6. **`itemGroups`:** add `itemGroups = {}` to match modern shape? Yes, cheap.
7. **Block state:** document `state.metadata` as the only supported state field, or add
   ETFR-specific mappings for a few blocks? Recommendation: document first.

---

## 11. Bottom line

The previous list was directionally correct but over-weighted a few items that do not
matter on 1.7.10 and under-weighted a few that are feasible:

- **Do first:** BIOS/global fixes, `io`, `cc.require`/shell, settings defaults, missing
  programs. These unlock most modern user code.
- **Do next:** item details (most fields are native), commands dimension/order, HTTP
  hardening, rednet/parallel, pocket upgrades, `peripheral_hub`.
- **Optional:** item/block `tags` via ETFR/HogUtils; GPS accuracy.
- **Do not promise:** named block states, full `@e` selectors, native file drag-and-drop,
  non-empty `itemGroups`.
