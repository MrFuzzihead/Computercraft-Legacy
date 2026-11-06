# CC API Exposure Plan — deprecating mixins / reflection / AT / ASM in downstream mods

**Goal:** remove all CC-targeting mixins/accessors/invokers/reflections/ATs/ASM from
CCTweaks-Legacy by either (1) removing them outright, (2) implementing the fix in
Computercraft, or (3) exposing public getters/setters in Computercraft. This file
extends the analysis to Peripherals++, OpenPeripheralCore/OpenPeripheral (here
"OP-Core"), OpenPeripheralAddons (OP-Addons) and OpenPeripheralIntegrations
(OP-Integrations). OP mixins in CCTweaks-Legacy are explicitly out of scope unless
noted.

**Status:** branch `feature/api-exposure-for-mixin-removal` cut from
`feature/wired-modem-full-cleanroom` (PR #28). CCTweaks is pinned to
`3.2.0:dev`; until PR #28 merges and a new Computercraft tag is cut (e.g. `3.3.0`),
CCTweaks cannot rebase (six `TileCable_Mixin` `@Overwrite`s target deleted methods).

---

## 1. CCTweaks-Legacy — checklist before CCTweaks work can begin

Everything in Phases 1–2 is **additive API surface in Computercraft** plus two
visibility widenings — no behaviour changes to existing paths. CCTweaks only starts
once these are merged and tagged.

### Prerequisite

Merge PR #28 and cut a new Computercraft tag.

### Phase 1 — accessor replacements (additive only)

Add these to Computercraft; each lets CCTweaks delete the matching accessor with no
behaviour change.

| CC change | Current state | CCTweaks deletion |
|---|---|---|
| `TileCable#getAttachedPeripheralID()` (public) | `private int m_attachedPeripheralID = -1` | `TileCable_Accessor` (part) |
| `TileCable#getCableIcons()` / `getModemIcons()` (public static) | `private static IIcon[] s_cableIcons/s_modemIcons` | `TileCable_Accessor` (part) |
| `Computer#getLuaMachine()` (public) | `private ILuaMachine m_machine` | `Computer_Accessor` |
| `ServerComputer#getComputer()` (public) | `private final Computer m_computer` | `ServerComputer_Accessor` |
| `TileTurtle#setMoved(boolean)` (public/public) | visibility widening | `TileTurtle_Accessor` (part) |
| `transferStateFrom(...)` widen to public | `private` | `TileTurtle_Accessor` (part) |
| `createServerComputer(...)` widen to public | `private static` | `TileTurtle_Accessor` (part) |
| `PeripheralAPI#getEnvironment()` + wrapper `getEnvironment()` | `private` | `PeripheralAPI_Accessor` (part) |
| `CobaltMachine#getLuaState()` | `private final LuaState state` | `CobaltMachine_Accessor` |
| `TileCable#setAttachedPeripheralID(int)` (public) — if any writer needs it | `private` field | covers setter side of `TileCable_Accessor` |

After Phase 1: **9 accessor/invoker files deleted in CCTweaks**, mixin count
20 → 11. Every remaining CC mixin has a defined CC-side replacement.

### Phase 2a — cable-node extensibility point

Add a cable-node hook to the mod (e.g. an `ICableNode` marker interface checked in
`BlockCable.isCable`, or an `IsCableEvent`). CCTweaks' `PartCable` implements it;
vanilla rendering then works for free. Deprecates **2 mixins**:
`BlockCable_Mixin`, `CableBlockRenderingHandler_Mixin`. No CodeChicken dependency
in CC. CCTweaks 11 → 9.

### Phase 2b — fuel-provider registry

CC grows a fuel-provider registry (so EU/RF registers instead of overwriting).
Deprecates **1 mixin**: `TurtleRefuelCommand_Mixin` (delete outright — its
`@Overwrite` body is stale: drops the disabled-actions check, container items, and
the test-only branch). CCTweaks 9 → 8.

### Phase 2c — cable placement handler hook

Add a placement handler hook in `ItemCable` (keeps multipart without a hard
dependency), or route-1 drop. The `nativePlace` copy is never ported — it's already
CC's code. Deprecates **1 mixin**: `ItemCable_Mixin`. CCTweaks 8 → 7.

### Phase 2d — peripheral lifecycle seam

CC adds an `attach`/`detach` callback or event to `PeripheralAPI`. The synthetic
network view moves into CCTweaks' real controller. Uses Phase-1 `getEnvironment()`
until it lands. Deprecates **1 mixin**: `PeripheralAPI_Mixin`. CCTweaks 7 → 6.

### Phase 3 — `TileCable_Mixin` (hard case)

Options: (a) merge the extended network into CC (large), (b) interim repair —
delete 6 dead `@Overwrite`s, rebase, exclude `TileWiredModemFull`,
(c) route-1 drop: revert to vanilla `TileCable` (loses the extended network +
wireless-bridge usefulness). Decision deferred until Phase 2 lands.

### Depth / deprecation total (CCTweaks)

| Phase | CC work | CCTweaks deletions | CCTweaks mixin count |
|---|---|---|---|
| PR #28 + tag | — (already authored) | — | 20 (currently *broken*) |
| 1 | 9 additive getters + 2 widenings | 9 accessor/invoker files | 20 → 11 |
| 2a | `ICableNode` + `isCable` hook | `BlockCable_Mixin`, `CableBlockRenderingHandler_Mixin` | 11 → 9 |
| 2b | fuel registry + `execute` consult | `TurtleRefuelCommand_Mixin` | 9 → 8 |
| 2c | placement handler hook | `ItemCable_Mixin` | 8 → 7 |
| 2d | lifecycle seam | `PeripheralAPI_Mixin` | 7 → 6 |
| 3 | network merge (large) *or* interim repair | `TileCable_Mixin` | 6 → 5 (OP only) |

After Phase 1 every remaining CC mixin has a defined CC-side replacement; after
Phases 2a–2d, **14 of the 15 CC mixins are gone**, with `TileCable_Mixin` as the
sole holdout pending the merge-vs-interim decision. The 5 OpenPeripheral mixins
are untouched throughout.

---

## 2. Peripherals++ — what can be deprecated

Repo analysed: `austinv11/PeripheralsPlusPlus` @ `master`. Mechanisms: one ASM
`IClassTransformer`, one `AccessTransformer`, and Java reflection (`ReflectionHelper`
+ `ComputerCraftRegistry`).

### P++-1 — ASM transformer (hardest; bytecode-level, cannot be fully deprecated)

`Transformer#transformPocketComputerCreateServerComputer` patches
`ItemPocketComputer` at instruction offsets (11th `INVOKEVIRTUAL` in
`createServerComputer`; first `ASTORE` in `getItemStackDisplayName`/`onUpdate`).
Injections into `ComputerCraftHooks.{onPocketComputerCreate,getName,update}`.
**No CC change can remove this** — the hook *is* the feature (pocket-peripheral
gate). What CC **can** do is replace the fragile offset scheme with a seam:

- add `IPocketComputerUpgrade` / `PocketComputerUpgradeEvent` fired from
  `ItemPocketComputer.createServerComputer` (and display-name/update tick points)
  so P++ registers a listener instead of bytecode-patching. Offsets drift — this is
  where the mod breaks on every CC release.

### P++-2 — reflection on `ComputerCraft` provider/registry lists

`ComputerCraftRegistry` reflects `ComputerCraft.peripheralProviders`,
`bundledRedstoneProviders`, `mediaProviders` (private static lists; `ComputerCraft.
serverComputerRegistry` is already public) and `CCTurtleProxyCommon.
m_turtleUpgrades` (private) to register P++'s providers. CC fix: expose public
`registerPeripheralProvider` / `registerBundledRedstoneProvider` /
`registerMediaProvider` (we already have the `registerPeripheralProvider`-style
helper precedent used by `getPeripheralAt`) and a public
`CCTurtleProxyCommon#registerTurtleUpgrade(ITurtleUpgrade)` —
`registerTurtleUpgradeInternal` is private today; widen or publish a public
delegate. Deprecates the reflective registry entirely; P++ registers via API.

### P++-3 — reflection crawling (`ReflectionHelper` → `Computer`)

To reach Lua globals, P++ crawls `ServerComputer.m_computer` (private) →
`Computer.m_machine` (private) → `CobaltMachine.globals` (private `LuaTable`)
and reflectively calls `CobaltMachine.toValue(Object)` (2-arity; the public
`toValue(Object, Map)` exists). CC fix: publish `ServerComputer#getComputer()`
and `Computer#getLuaMachine()` (same as Phase 1 — shared dep) plus
`CobaltMachine#getGlobals()` / `getLuaState()` (same Phase-1 `CobaltMachine_
Accessor` successors). Deprecates this chain; P++ walks the public graph.
`fn = toValue(...)` remains a P++-side choice unless CC publishes a 1-arg
`toValue(Object)` overload.

### P++-4 — AccessTransformer — NOT deprecable by CC

`ppp_at.cfg` widens vanilla `ItemBucket`, `Gui`, `EntityLiving`, `EntityLivingBase`,
`BlockDispenser` members. Zero CC targets. No action.

### P++-5 — `Language.hashLanguage` reflection

`ReflectionHelper` reflects vanilla `net.minecraft.client.resources.Language.hash
Language`. Zero CC targets. No action.

---

## 3. OpenPeripheralCore (`OpenMods/OpenPeripheral`) — what can be deprecated

Mechanisms: `openmods.reflection` (OpenModsLib `FieldAccess`/`MethodAccess`/
`ReflectionHelper`) + plain `java.lang.reflect`. Finding up front: **most of Core's
reflection never touches CC internals** — it reflects *adapter targets and its own
classes* (peripheral detection, Lua reflection, Lua method maps). Specifically:

- `LuaReflectionHelper`, `IndexedMethodMap`, `NamedMethodMap`, `PropertyListBuilder`,
  `AdapterWrapper`, `CommonMethodsBuilder` — reflection over Lua adapter classes and
  Lua target objects, not CC internals. Not deprecable by CC API work.
- `PeripheralProvider implements IPeripheralProvider` — the *public* CC API; the
  only reflective call is on its own proxy constructor (`ctor.setAccessible(true)`
  on the generated `IPeripheral` proxy). Not deprecable; it is the correct public
  contract already.

**So Core has nothing CC-exposing to deprecate.** The one CC-adjacent knob is
already public (`IPeripheralProvider`). No CC change is needed or useful for Core.

---

## 4. OpenPeripheralAddons — what can be deprecated

Mechanisms: `openmods.reflection.FieldAccess` / `MethodAccess`. Only one file
reflects CC internals: `TileEntityPeripheralProxy`:

### OPA-1 — `m_peripheralAccessAllowed` / `togglePeripheralAccess` (direct overlap with Phase 1)

`FieldAccess.create(cableClass, "m_peripheralAccessAllowed")` (private boolean in
`TileCable`) + `MethodAccess.create(..., "togglePeripheralAccess")` (already
**public** at `TileCable:578`). The field read can go public as
`TileCable#isPeripheralAccessAllowed()` (or the getter `getPeripheralAccessAllowed()`).
That plus `ComputerCraft#getPeripheralAt` (already public) lets
`TileEntityPeripheralProxy` drop both the field reflective access and the
`MethodAccess` for `togglePeripheralAccess` entirely, leaving only a direct call.
**Recommended:** add the boolean getter in Phase 1 alongside the other `TileCable`
getters — zero extra design cost, removes OPA's only CC-privates touchpoint.

Everything else in Addons (peripherals themselves, GUI, OC bridge) uses public
`dan200.computercraft.api.*` only — nothing further to deprecate.

---

## 5. OpenPeripheralIntegrations — what can be deprecated

Mechanisms: `openmods.reflection.MethodAccess` over **public-static item helpers**
on CC classes — i.e. fragile string-based lookup, not access to privates. All four
CC-facing meta providers resolve via `ReflectionHelper.getClass(...)` +
`MethodAccess.create(...)` at runtime. Every target is already public:

| Integration provider | Target class (reflected) | Methods | Our visibility today |
|---|---|---|---|
| `ComputerMetaProvider` | `dan200.computercraft.shared.computer.items.IComputerItem` | `getComputerID`, `getLabel`, `getFamily` | interface methods (public) |
| `MediaMetaProvider` | `dan200.computercraft.ComputerCraft` | `getMedia` | `public static` (ComputerCraft:604) |
| `PrintoutMetaProvider` | `dan200.computercraft.shared.media.items.ItemPrintout` | `getTitle`, `getPageCount`, `getText` | `public static` (:138/143/148) |
| `TurtleMetaProvider` | `dan200.computercraft.shared.turtle.items.ITurtleItem` | `getFuelLevel`, `getUpgrade` | interface methods (public) |

CC fix: there is **no behavioural gap** — these targets are public already. The
reflection exists only because OpenPeripheral can't compile against CC's internals
without a hard dependency. Deprecation path is *downstream*, not in CC: once
OP-Integrations is (re)compiled against a CC release where these members are
`public static`, the `MethodAccess` shims compile to direct calls and the reflective
indirection disappears. Net: **nothing required of CC** beyond *not making these
members private* — keep them public in future refactors so the string lookups keep
resolving. (Note `ComputerMetaProvider` returns an opaque `Object` for `getFamily`
because `ComputerFamily` is CC-internal; a future API enum would let it type the
return.)

Other Integrations reflection (`AdapterInterface` appeng, `AdapterFrequencyOwner`
enderstorage, `AdapterElecticMachine` ic2) targets *non-CC* mod classes — not
deprecable by CC work.

---

## 6. Consolidated CC-side catalogue

| CC addition | Deprecates (consumer side) |
|---|---|
| Phase-1 getters/setters + widenings (see §1) | 9 CCTweaks accessors; P++ `ReflectionHelper` chain (partially); exposes P++ `ComputerCraftRegistry` |
| `IPocketComputerUpgrade` / upgrade-event seam in `ItemPocketComputer` | P++ ASM transformer's offset surgery (P++-1) |
| Public `registerPeripheralProvider` / `registerBundledRedstoneProvider` / `registerMediaProvider` + public `CCTurtleProxyCommon#registerTurtleUpgrade` | P++ `ComputerCraftRegistry` reflection (P++-2) |
| `CobaltMachine#getGlobals()` (+ `getLuaState()` already in Phase 1) | P++ globals crawl (P++-3) |
| `TileCable#isPeripheralAccessAllowed()` (or `getPeripheralAccessAllowed()`) | OPA `TileEntityPeripheralProxy` field reflection (OPA-1) |
| **No CC action** — keep existing public statics public | OP-Integrations' 4 `MethodAccess` providers keep resolving |
| **No CC action** | P++ AT (vanilla targets), P++ `Language.hashLanguage`, all Core reflection (own/adapter classes) |

---

## 7. Suggested order of work

1. PR #28 → tag (prerequisite for everything downstream).
2. Phase 1 getters/setters/widenings + `TileCable#isPeripheralAccessAllowed()`
   (one branch, all additive). Retires 9 CCTweaks accessors and shrinks OPA + P++.
3. `ICableNode` hook (2a) — small, high-value for multipart.
4. Pocket-upgrade seam + `register*Provider` publics (P++-1/2) — small surface,
   retires a whole coremod transformer and a reflective registry.
5. Fuel registry (2b), placement hook (2c), lifecycle seam (2d) per CCTweaks appetite.
6. `TileCable_Mixin` merge-vs-interim decision (Phase 3) — only if CCTweaks still
   wants the extended network after 2a–2d land.

---

## References

- CCTweaks inventory: `CCTweaks-Legacy/docs/remove-cc-mixins.md`
- Peripherals++ source: `https://github.com/austinv11/PeripheralsPlusPlus`
- OpenPeripheralCore: `https://github.com/OpenMods/OpenPeripheral`
- OpenPeripheralAddons: `https://github.com/OpenMods/OpenPeripheral-Addons`
- OpenPeripheralIntegrations: `https://github.com/OpenMods/OpenPeripheral-Integration`
