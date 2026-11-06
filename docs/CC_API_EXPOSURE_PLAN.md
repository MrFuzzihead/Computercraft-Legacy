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

See §8 for parity staging — Bucket labels added for traceability.

| Phase | CC work | CCTweaks deletions | CCTweaks mixin count | Bucket |
|---|---|---|---|---|
| PR #28 + tag | — (already authored) | — | 20 (currently *broken*) | — |
| 1 | 9 additive getters + 2 widenings | 9 accessor/invoker files | 20 → 11 | 1 (do now) |
| 2a | `ICableNode` + `isCable` hook | `BlockCable_Mixin`, `CableBlockRenderingHandler_Mixin` | 11 → 9 | 2 (CC-optional, small) |
| 2b | fuel registry + `execute` consult | `TurtleRefuelCommand_Mixin` | 9 → 8 | 2 (keep internal) |
| 2c | placement handler hook | `ItemCable_Mixin` | 8 → 7 | 2 (CC-optional, small) |
| 2d | lifecycle seam | `PeripheralAPI_Mixin` | 7 → 6 | 2 (keep internal) |
| 3 | network merge (large) *or* interim repair | `TileCable_Mixin` | 6 → 5 (OP only) | 3 (never in CC) |

After Phase 1 every remaining CC mixin has a defined CC-side replacement; after
Phases 2a–2d, **14 of the 15 CC mixins are gone**, with `TileCable_Mixin` as the
sole holdout pending the merge-vs-interim decision. The 5 OpenPeripheral mixins
are untouched throughout.

---

## 2. Peripherals++ — what can be deprecated

**Fork, not upstream.** The workspace fork `PeripheralsPlusPlus` (pinned to
`Computercraft-Legacy:3.2.0:dev`) has already been ported to GTNH mixins — the
upstream ASM transformer, `AccessTransformer`, and reflection helpers are gone.
What remains is: **3 late (CC-targeting) mixins** + **4 early (vanilla-accessor)
mixins** + `hooks/ComputerCraftRegistry`. All analysis below is against the fork.

### P++-1 — `ItemPocketComputerMixin` (3 hooks) → retire via pocket-upgrade seam

Replaces the old ASM transformer: `createServerComputer` @RETURN attach,
`onUpdate`/`func_77663_a` modify-expression update, `getItemStackDisplayName`/
`func_77653_i` display-name override — all delegating to `ComputerCraftHooks`.
**No CC change can remove the hooks of logic itself**, but CC can replace the
mixin indirection with a seam, exactly as upstream analysis recommended:

- add `IPocketComputerUpgrade` / `PocketComputerUpgradeEvent` fired from
  `ItemPocketComputer` (create/refresh/name points) so P++ registers a listener.
  The mixin then deletes; offsets/`@Local` args no longer drive the design.

Precedent already in CC: `ServerComputer.setPeripheral(int, IPeripheral)` is public
and is the real attachment point P++ writes through.

### P++-2 — `PocketAPIMixin` (behaviour guard) → CC bug fix, not API exposure

`@Shadow ItemStack m_stack` + `@Inject` on the **private** `PocketAPI#doEquipBack`
to refuse overwriting any installed upgrade. The mixin's javadoc records the CC
bug: `PocketAPI#doEquipBack` only refuses when `PocketUpgrades.isUpgrade(id)`
recognises *its own* upgrade (ids 1–6), so it would orphan P++'s id 7+ upgrades.
Our copy has the same guard (checked: `PocketAPI:146` refuses only when
`PocketUpgrades.isUpgrade(tag.getInteger("upgrade"))` is true). **Fix the check in
CC** (refuse whenever any `upgrade` key is present) and the mixin deletes — no
new API surface. This is the correct route: route 2 (implement the fix in CC).

### P++-3 — `CCTurtleProxyCommonAccessor` → public CC getter

`@Accessor("m_turtleUpgrades") Map<Integer, ITurtleUpgrade> ppp$getTurtleUpgrades()`
read side of `hooks.ComputerCraftRegistry.getTurtleUpgrades()` (NEI recipe handler
iterates the map). CC already exposes `CCTurtleProxyCommon#getTurtleUpgrade(int)`
and `#registerTurtleUpgrade(ITurtleUpgrade)` (public) — only whole-map iteration
is missing. CC fix: publish `CCTurtleProxyCommon#getTurtleUpgrades()` (unmodifiable
`Collection`/`Map` view). Deprecates the accessor with a 1-line change on each side.
The old upstream reflective unwrapping of `turtleProxy`/`m_turtleUpgrades` is
already gone from the fork — this accessor is the only remnant.

### P++-4 — Early (vanilla) accessors — NOT deprecable by CC

`EntityLivingAccessor`, `EntityLivingBaseAccessor`, `ItemBucketAccessor`,
`GuiAccessor` replace the old `ppp_at.cfg` access transformer (the cfg file is
deleted from the fork; `EarlyMixinsLoader.getASmTransformerClass()` returns
`null`). Zero CC targets. No action.

### P++-5 — `IPocketComputerUpgrade` — already clean

P++'s own pocket-upgrade contract (ids ≥ 7, `createPeripheral`/`update`/
`onRightClick`). It's the *consumer's* interface, not a hack — nothing to expose.
If CC adopts a pocket-upgrade registry (P++-1), align the upgrade-id ranges so
they don't collide (CC reserves 1–6; P++ starts at 7).

---

## 3. OpenPeripheralCore — what can be deprecated

**Fork:** workspace `OpenPeripheral` (`MrFuzzihead/OpenPeripheral`), upstream
`OpenMods/OpenPeripheral`. Commits since upstream are build-only (GTNH convention
dsl, OpenModsLib 0.10.15 downgrade). Mechanism surface identical to upstream —
verified against the fork.

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

**Fork:** workspace `OpenPeripheral-Addons` (`MrFuzzihead/OpenPeripheral-Addons`),
upstream `OpenMods/OpenPeripheral-Addons` — build bump to CC 3.2.0 / OP 2.0.0.
Mechanism surface identical to upstream — verified against the fork.

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

**Fork:** workspace `OpenPeripheral-Integration` (singular), upstream
`OpenMods/OpenPeripheral-Integration` — build bump to CC 3.2.0. Mechanism surface
identical to upstream — verified against the fork.

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
| Phase-1 getters/setters + widenings (see §1) | 9 CCTweaks accessors |
| `IPocketComputerUpgrade` / upgrade-event seam in `ItemPocketComputer` | P++ `ItemPocketComputerMixin` (P++-1) |
| Fix `PocketAPI#doEquipBack` to refuse *any* installed upgrade | P++ `PocketAPIMixin` (P++-2) — behaviour fix, no new API |
| `CCTurtleProxyCommon#getTurtleUpgrades()` (public read view) | P++ `CCTurtleProxyCommonAccessor` (P++-3) |
| `TileCable#isPeripheralAccessAllowed()` (or `getPeripheralAccessAllowed()`) | OPA `TileEntityPeripheralProxy` field reflection (OPA-1) |
| **No CC action** — keep existing public statics public | OP-Integrations' 4 `MethodAccess` providers keep resolving |
| **No CC action** | P++ early (vanilla) accessors, all Core reflection (own/adapter classes) |

---

## 7. Suggested order of work (additive API work; parity staging in §8)

1. PR #28 → tag (prerequisite for everything downstream).
2. Phase 1 getters/setters/widenings + `TileCable#isPeripheralAccessAllowed()`
   (one branch, all additive). Retires 9 CCTweaks accessors and shrinks OPA + P++.
3. `ICableNode` hook (2a) — small, high-value for multipart.
4. Pocket-upgrade seam + `PocketAPI` any-upgrade fix + `CCTurtleProxyCommon#
   getTurtleUpgrades()` (P++-1/2/3) — small surface, retires all 3 P++ late mixins.
5. Fuel registry (2b), placement hook (2c), lifecycle seam (2d) per CCTweaks appetite.
6. `TileCable_Mixin` merge-vs-interim decision (Phase 3) — only if CCTweaks still
   wants the extended network after 2a–2d land. **Do not merge Phase 3 into CC (§8).**

Items 2–5 are the additive-API candidates; which land in `Computercraft-Legacy`
depends on the parity buckets in §8. Under the harmonious-first staging, only
Bucket 1 (+ the `PocketAPI` fix) goes into the next CC tag; Bucket 2 stays
internal to CCTweaks/P++ until the clean-room merge, and Bucket 3 never goes in.

---

## 8. Parity order of operations (harmonious-first, then clean-room merge)

**Ultimate goal:** clean-room merge P++, CCTweaks, and OpenPeripheral (Core +
Addons + Integrations) into one new mod. With that end-state in mind, CC-side
work should be staged so `Computercraft-Legacy`'s 1:1 CC:T parity is not
traded for API surface the merged mod would internalise anyway. Once the four
mods are one codebase, it can ship its own accessors/mixins internally and
needs no CC seam for its own convenience.

### Bucket 1 — zero parity impact. Do in CC now.

- All Phase-1 getters/setters + widenings (`TileCable` getters, `Computer#getLuaMachine`,
  `ServerComputer#getComputer`, `CobaltMachine#getLuaState`, `PeripheralAPI#getEnvironment`,
  `setMoved`/`transferStateFrom`/`createServerComputer` widenings).
- `TileCable#isPeripheralAccessAllowed()`, `CCTurtleProxyCommon#getTurtleUpgrades()` read view.
- `PocketAPI#doEquipBack` any-upgrade fix — restores the check's documented intent
  ("Any supported id counts"); a parity bug-fix, not a divergence.
- OPA body (no CC work), OPI body (keep public statics public), Core body (no CC work).

### Bucket 2 — functionally neutral if designed carefully. Low parity risk.

Default path bit-for-bit identical to upstream — new interface/event/registry
consulted, falls back when no consumer is present. Observable gameplay with no
addon installed remains 1:1; divergence is source-tree API surface only.

- `ICableNode` check in `BlockCable.isCable` (2a), cable-placement hook (2c) —
  small, high-value for multipart; reasonable to do in CC.
- Pocket-upgrade event (P++-1), fuel-provider registry (2b), lifecycle seam (2d) —
  keep CCTweaks/P++-internal until the merge unless harmony testing demands
  them. 2b inserts a branch into the `TurtleRefuelCommand` execution path, so
  fallback must be proven to be today's logic exactly.

### Bucket 3 — direct parity threat. Do *not* put in CC.

- Phase 3: merge the extended `TileCable` network into CC — real behavioural
  divergence (new networking / tile behaviour, non-upstream by definition).
  Keep it in the merged mod.
- Any unconditional change to vanilla block/item/peripheral behaviour.

### Recommended sequencing

1. **CC gets Bucket 1 + the `PocketAPI` fix only** (one small additive branch).
2. **Harmonise the four mods against that tag** — CCTweaks drops its 9 accessors,
   OPA drops field reflection, P++ drops `CCTurtleProxyCommonAccessor`. Keep
   remaining behaviour mixins alive and tested.
3. **Then clean-room merge.** In the merged mod, decide per-hook whether to
   adopt Bucket 2 seams or keep internal accessors — by then the seams have
   proven their value.
4. **Bucket 2/3 CC additions only if the merged mod proves it needs them.**
   At that point they are optional upstream proposals, not parity work.

---

## References

- CCTweaks inventory: `CCTweaks-Legacy/docs/remove-cc-mixins.md`
- Peripherals++ **fork**: `MrFuzzihead/PeripheralsPlusPlus` @ `main` (pinned to `Computercraft-Legacy:3.2.0:dev`); upstream `austinv11/PeripheralsPlusPlus` cited only to note the ASM/reflection that was already ported away
- OpenPeripheralCore fork: `MrFuzzihead/OpenPeripheral` (upstream `OpenMods/OpenPeripheral`)
- OpenPeripheralAddons fork: `MrFuzzihead/OpenPeripheral-Addons` (upstream `OpenMods/OpenPeripheral-Addons`)
- OpenPeripheralIntegrations fork: `MrFuzzihead/OpenPeripheral-Integration` (upstream `OpenMods/OpenPeripheral-Integration`)
