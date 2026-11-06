# CC API Exposure Plan — removing every CC mechanism from the three forks

**Objective.** **Zero** ComputerCraft-targeting mechanisms — mixin, accessor, invoker,
ASM transformer, reflection, access transformer — in any of the three maintained forks
(`CCTweaks-Legacy`, `PeripheralsPlusPlus`, `OpenPeripheral` + `-Addons` +
`-Integration`).

**Constraint.** `Computercraft-Legacy` stays **1:1 with upstream CC: Tweaked**. Parity
constrains *how* a mechanism is removed; it never decides *whether* it is removed.

**Status.** Branch `feature/api-exposure-for-mixin-removal`, cut from
`feature/wired-modem-full-cleanroom` (PR #28). All fork facts below are verified
against the workspace forks at `main`, not against upstream addon sources.

**Non-goals.** Porting addon features into CC; restructuring the addons beyond what a
mechanism removal requires.

---

## 0. How a mechanism gets removed

The ladder is about the *method*. Always take the highest applicable rung.

| Rung | Method | CC change | Parity risk |
|---|---|---|---|
| **M1** | Delete — unused, or something already public does the job. | none | none |
| **M2** | Replace with an existing public CC member. | none | none |
| **M3** | CC **visibility**: new getter/setter over existing state, or `private`/`protected` → `public`. Verbatim value, no new logic. | additive, no new call sites | none — bytecode for every existing path is unchanged |
| **M4** | CC **additive seam**: a new interface/registry/hook that CC consults, with the no-consumer path identical to today. | additive, *new call sites* | low, and must be proven by test |
| **M5** | **Addon owns the code** — reimplement the addon side so it no longer patches CC (own tile/block, own renderer, direct calls). | none | none — the work lands in the addon |
| **M6** | CC **internals behaviour change** to absorb the mechanism. | behavioural divergence | rejected |
| **M7** | Keep the mechanism. **Interim only**, with a named endgame rung. | none | none |

M3 is what "preserve CC behaviour (getters/setters)" means; M4 is your "OK to add to
the API where necessary". M4 must earn its place: it is the only rung that puts new
code *inside* a CC execution path, so each seam needs a fallback assertion.

**A note on `@Overwrite` → cancellable `@Inject`.** This trick preserves CC's logic
while the addon keeps its hook. It is a *behaviour* improvement, not a removal — it
stays a mixin, so it is always M7, never a destination. Do it where a mixin survives
the interim anyway; do not count it as progress on this goal.

**Current state: 23 CC-targeting mechanisms (files or lookup sites) across the forks.
Target: 0.** Fifteen yield to M1–M3 (no new CC call sites at all), six need an M4
seam, one is M5, and `TileCable_Mixin` alone is the M4-or-M5 decision (§8).

---

## 1. Verified inventory

### CCTweaks-Legacy — `main` @ `55ce41d` ("Update to ComputerCraft 3.2.0")

20 mixin/accessor/invoker files, all listed in
`src/main/resources/mixins.cctweaks.late.json`; `Mixins.java` enumerates only the 10
behaviour mixins (6 CC, 3 OpenPeripheral, 1 multipart).

**15 target CC:**

| # | File | Kind | Target |
|---|---|---|---|
| 1 | `TileCable_Mixin` | 2 `@Inject` + 25 `@Overwrite` | `TileCable` |
| 2 | `BlockCable_Mixin` | `@Overwrite` static | `BlockCable.isCable` |
| 3 | `CableBlockRenderingHandler_Mixin` | `@Overwrite` | `ComputerCraftProxyClient$CableBlockRenderingHandler` |
| 4 | `ItemCable_Mixin` | `@Overwrite` | `ItemCable.onItemUse` |
| 5 | `PeripheralAPI_Mixin` | 2 `@Inject` | `PeripheralAPI$PeripheralWrapper.attach/detach` |
| 6 | `TurtleRefuelCommand_Mixin` | `@Overwrite` | `TurtleRefuelCommand.execute` |
| 7 | `TileCable_Accessor` | accessor | `m_attachedPeripheralID`, `s_cableIcons`, `s_modemIcons` |
| 8 | `PeripheralAPI_Accessor` | accessor | `PeripheralAPI.m_environment` |
| 9 | `PeripheralWrapper_Accessor` | accessor | `PeripheralAPI$PeripheralWrapper.m_peripheral`, `this$0` |
| 10 | `Computer_Accessor` | accessor | `Computer.m_machine` |
| 11 | `ServerComputer_Accessor` | accessor | `ServerComputer.m_computer` |
| 12 | `CobaltMachine_Accessor` | accessor | `CobaltMachine.state` |
| 13 | `TileTurtle_Accessor` | accessor | `TileTurtle.m_moved` (get + set) |
| 14 | `ItemPocketComputer_Invoker` | invoker | `ItemPocketComputer.createServerComputer` |
| 15 | `TileComputerBase_Invoker` | invoker | `TileComputerBase.transferStateFrom` |

**5 target OpenPeripheral** (`AdapterPeripheral_Mixin`, `ClassMethodsComposer_Accessor`,
`ComposedMethodsFactory_Accessor`, `ModuleComputerCraft_Mixin`, `PeripheralProxy_Mixin`) —
no CC involvement anywhere in them. Out of scope.

### PeripheralsPlusPlus — fork `main`

3 late (CC) mixins + 4 early (vanilla-only) mixins:

| File | Kind | Target |
|---|---|---|
| `ItemPocketComputerMixin` | 1 `@Inject` + 1 `@ModifyExpressionValue` + 1 `@Inject` | `ItemPocketComputer` create / update / display name |
| `PocketAPIMixin` | `@Shadow` + `@Inject` | `PocketAPI.doEquipBack` |
| `CCTurtleProxyCommonAccessor` | accessor | `CCTurtleProxyCommon.m_turtleUpgrades` |
| 4 × `early/*Accessor` | accessors | `EntityLiving`, `EntityLivingBase`, `ItemBucket`, `Gui` — **vanilla, not CC** |

The fork already dropped upstream's ASM transformer, `ppp_at.cfg` and the reflective
`ComputerCraftRegistry` unwrapping.

### OpenPeripheral (Core / Addons / Integration) — forks

No CC-targeting mixins, no ASM, no AT anywhere. Mechanism surface is
`openmods.reflection` only, and only three files reach CC:

| Fork | File | What it touches | Rung |
|---|---|---|---|
| Addons | `TileEntityPeripheralProxy` | `TileCable.m_peripheralAccessAllowed` (private field) + `togglePeripheralAccess` (already public) | M3 |
| Integrations | `ComputerMetaProvider`, `MediaMetaProvider`, `PrintoutMetaProvider`, `TurtleMetaProvider` | already-public CC members, via string-based `MethodAccess` | **M2** — see §6 |
| Core | `PeripheralProvider`, `ProxyAdapterPeripheral` | the public `IPeripheralProvider` contract; reflection over its own proxy class | nothing to do |

---

## 2. Corrections to the earlier revisions

1. **`ModuleComputerCraft_Mixin` is not a CC mixin** — it targets
   `openperipheral.interfaces.cc.ModuleComputerCraft`. CC count is 15, not 16.
2. **Three files were missing or mis-attributed**: `ItemPocketComputer_Invoker` and
   `TileComputerBase_Invoker` were folded into "`TileTurtle_Accessor` (part)", and
   `PeripheralWrapper_Accessor` was absent.
3. **`PeripheralAPI_Accessor` + `PeripheralWrapper_Accessor` need nothing.** Our
   `IComputerAccess.getAvailablePeripherals()` backport is already implemented on
   `PeripheralAPI$PeripheralWrapper` (`PeripheralAPI.java:501` / `:515`); CCTweaks'
   mixin implements `IComputerAccess` and can call `this.getAvailablePeripherals()`.
   Both are **M1**.
4. **`TileCable#setAttachedPeripheralID` is not needed** — nothing writes the field.
5. **`TileTurtle` has no `setMoved`** (`m_moved` is assigned directly at
   `TileTurtle.java:485`), so that half is an add, not a widening. `getMoved` has no
   caller — `hasMoved()` is already public.
6. **`OP-Integrations` compiles against CC** (`dependencies.gradle:39`,
   `api("com.github.MrFuzzihead:Computercraft-Legacy:3.2.0:dev")`), and
   `ComputerFamily` is a **public enum**
   (`ComputerFamily.java:3`). Four `MethodAccess` sites are replaceable with direct
   calls today — no CC change.
7. **`TileCable_Mixin` has 6 dead/failing `@Overwrite`s** against our `TileCable`
   (`attachPeripheral`, `detachPeripheral` — no such method; `getTypeRemote`,
   `getMethodNamesRemote`, `callMethodRemote` — exist only on the inner
   `TileCable$Peripheral`; `getConnectedPeripheral` — overwritten `private` over our
   `protected`, a visibility narrowing Mixin rejects). CCTweaks does not currently
   apply. This is why nothing here can be tested until it is repaired.

The earlier revision treated "keep the mixin" as a normal outcome. That was wrong for
this objective: M7 exists only as an interim for a mechanism with a named endgame.

---

## 3. CCTweaks-Legacy — all 15 removed

### 3.1 Accessors and invokers (9) — all deleted

| Mechanism | Rung | Removal |
|---|---|---|
| `TileCable_Accessor.getAttachedPeripheralID` | M3 | add `public int getAttachedPeripheralID()` |
| `TileCable_Accessor.s_cableIcons` | **M2** | `PartCable` only indexes `[0]`/`[1]`; `TileCable.getCableItemTexture(int)` is public and maps to exactly those two icons |
| `TileCable_Accessor.s_modemIcons` | **M2 + M3** | `BlockNetworked` builds `s_modemIcons[i*2]` for `i<4` — that is the existing public `getModemFaceTexture(int)`. `PartModem` also uses the odd indices (`texture+1`) → add `getModemSideTexture(int anim)` |
| `TileTurtle_Accessor.getMoved` | **M1** | unused; `hasMoved()` is public |
| `TileTurtle_Accessor.setMoved` | M3 | add `public void setMoved(boolean)` |
| `TileComputerBase_Invoker` | M3 | widen `transferStateFrom(TileComputerBase)` to public |
| `ItemPocketComputer_Invoker` | M3 (or M1) | widen `createServerComputer(...)` to public — or delete CCTweaks' `PocketAccess` P++ integration, whose only use is `getModemLight`/`getUpgrades` |
| `Computer_Accessor`, `ServerComputer_Accessor`, `CobaltMachine_Accessor` | M3 | three getters, used only by `ItemDebugger`. Either add them, or **M1**: drop `ItemDebugger` |
| `PeripheralAPI_Accessor`, `PeripheralWrapper_Accessor` | **M1** | `getAvailablePeripherals()` (§2.3) |

Exposing an `IIcon[]` (the earlier proposal) is worse than indexed getters: the arrays
are mutable statics, and one getter per anim state needs no defensive copy.

### 3.2 Behaviour mixins (6) — one M5, four M4, one decision

| Mixin | Rung | Removal |
|---|---|---|
| `PeripheralAPI_Mixin` | **M5** | No CC change. `IPeripheral.attach(IComputerAccess)`/`detach` are already called by CC's wrapper, and the network view CCTweaks needs (`getPeripheralsOnNetwork`, `transmitPacket`) is addon code. Move the network registration into the addon's own `attach()`/`detach()` — the only in-tree `INetworkedPeripheral` implementors are CCTweaks' own (`TileDebugNetworkedPeripheral`, and the two OpenPeripheral mixin classes). `Point` already drives attach/detach for network nodes, so this is consolidation, not new logic. Functional check: attach a peripheral to a computer side and confirm the network view matches `getAvailablePeripherals()`. |
| `TurtleRefuelCommand_Mixin` | **M4** | CC-side turtle fuel-provider registry, consulted from `TurtleRefuelCommand` with `TileEntityFurnace.getBurnTime` as the fallback. Retires the mixin *and* the current `@Overwrite`'s four behavioural regressions (it drops `turtleDisabledActions`, container items, the `m_limit == 0` test-only branch and `InventoryUtil` slot handling). |
| `BlockCable_Mixin` | **M4** | Cable-connectivity seam consulted by `BlockCable.isCable` (§3.3). |
| `ItemCable_Mixin` | **M4** | Same seam, consulted at the placement points in `ItemCable.onItemUse`. Also deletes the mixin's copy of CC's placement code (`cCTweaks_Legacy$nativePlace`). |
| `CableBlockRenderingHandler_Mixin` | **M4** | Same seam, consulted by `ComputerCraftProxyClient$CableBlockRenderingHandler.renderWorldBlock` (and `TileCable.getCableBounds`). |
| `TileCable_Mixin` | **M4 or M5** | §8 — the one real design decision. |

### 3.3 The cable-connectivity seam (one seam, three mixins)

CC's cable knows only about itself: `BlockCable.isCable` checks
`ComputerCraft.Blocks.cable`, and the renderer/bounds follow from it. Every one of
`BlockCable_Mixin`, `ItemCable_Mixin` and `CableBlockRenderingHandler_Mixin` exists
because CCTweaks' multipart parts must count as cable neighbours.

One seam retires all three: a neighbour-ask interface (e.g. `ICableNode`, or a
`NetworkAPI`-style connectivity provider registered through `ComputerCraftAPI`) that
the following consult **before** their existing check, falling through untouched when
nothing is registered:

1. `BlockCable.isCable` — neighbour is a cable node?
2. `TileCable.getCableBounds` / the render handler — render an arm toward it?
3. `ItemCable.onItemUse` — is the block being clicked a connectable cable?

Design rules for it: no CodeChicken dependency in CC (a marker interface plus a
registration list is enough); `false`-by-default, so a CC-only install compiles to
today's behaviour; and a test that asserts an unregistered world renders and connects
identically.

---

## 4. PeripheralsPlusPlus — 3 removed

| Mechanism | Rung | Removal |
|---|---|---|
| `CCTurtleProxyCommonAccessor` | M3 | add `CCTurtleProxyCommon#getTurtleUpgrades()`, an unmodifiable view. `getTurtleUpgrade(int)` exists but ids are arbitrary (P++ uses ≥ 7), so a bounded loop is not a substitute. |
| `PocketAPIMixin` | **M4, unreachable-only** | `PocketAPI.java:147` refuses only when `PocketUpgrades.isUpgrade(id)` holds, i.e. `1 ≤ id ≤ NPC_INTERFACE` (`PocketUpgrades.java:75`); every writer of the `upgrade` tag in CC is `PocketUpgrades`-driven. Widening the guard to "any `upgrade` key present" therefore changes **no reachable CC-only state** — it is M6 in letter, M4 in effect, and it deletes the mixin. Record it in `NOTICE` as a deliberate unreachable-only fix, with a test pinning ids 1–6 and the absent-tag case. |
| `ItemPocketComputerMixin` | **M4** | A pocket-upgrade seam: `IPocketComputerUpgrade` + registration, consulted at CC's three points (`createServerComputer` return, the `onUpdate` refresh, `getItemStackDisplayName`). This is additive surface CC:T does not have, and it is the honest price of removing the mixin. Fallback must be "upgrade id unknown → today's path", asserted by the same test set. |
| 4 × early accessors | — | vanilla targets; not CC, not in scope. |

Consistency note: if `doEquipBack` is widened, decide explicitly whether
`doIsEquipped` (`PocketAPI.java:271`) should also report third-party upgrades. P++ does
not read it; the minimal change leaves it alone, and that asymmetry should be a
recorded decision rather than an oversight.

---

## 5. OpenPeripheralAddons — 1 removed

`TileEntityPeripheralProxy` reflects `TileCable.m_peripheralAccessAllowed` with
`openmods.reflection.FieldAccess`, and builds a `MethodAccess` for
`togglePeripheralAccess` although that method is already public (`TileCable.java:578`).

**M3:** add `TileCable#isPeripheralAccessAllowed()`; the `MethodAccess` becomes a direct
call in the same edit. OPA's only CC-privates touchpoint.

---

## 6. OpenPeripheralCore and OpenPeripheralIntegrations — 4 removed, M2

Core: nothing to do — `LuaReflectionHelper`, `IndexedMethodMap`, `NamedMethodMap`,
`PropertyListBuilder`, `AdapterWrapper`, `CommonMethodsBuilder` reflect adapter targets
and their own classes; `PeripheralProvider` uses the public `IPeripheralProvider`.

Integrations: `dependencies.gradle:39` already declares
`api("com.github.MrFuzzihead:Computercraft-Legacy:3.2.0:dev")`, so the four CC meta
providers can call their targets directly and delete `MethodAccess` outright:

| Provider | Direct call | Visibility |
|---|---|---|
| `ComputerMetaProvider` | `IComputerItem#getComputerID/getLabel/getFamily` | interface methods; `ComputerFamily` is a **public enum** (`:3`), so `getFamily` no longer needs to return `Object` |
| `MediaMetaProvider` | `ComputerCraft.getMedia` | `public static` (`:604`) |
| `PrintoutMetaProvider` | `ItemPrintout.getTitle/getPageCount/getText` | `public static` (`:138`/`:143`/`:148`) |
| `TurtleMetaProvider` | `ITurtleItem#getFuelLevel/getUpgrade` | interface methods |

The string lookups only existed to avoid a compile-time dependency, and the fork no
longer needs to avoid it. Standing guard: **do not narrow these members' visibility**
in future CC refactors.

---

## 7. The CC change set, by pass

**Pass 1 — M1 + M3 (zero behavioural risk).** Removes 9 CCTweaks accessors/invokers,
OPA's reflection, and P++'s accessor.

| Class | Change |
|---|---|
| `TileCable` | `getAttachedPeripheralID()`, `getModemSideTexture(int)`, `isPeripheralAccessAllowed()` |
| `TileTurtle` | `setMoved(boolean)` |
| `TileComputerBase` | `transferStateFrom(TileComputerBase)` → public |
| `ItemPocketComputer` | `createServerComputer(...)` → public |
| `Computer` / `ServerComputer` / `CobaltMachine` | `getLuaMachine()` / `getComputer()` / `getLuaState()` (debugger; or drop the debugger) |
| `CCTurtleProxyCommon` | `getTurtleUpgrades()` unmodifiable view |

**Pass 2 — M4 (new call sites, fallback asserted).** Removes 4 CCTweaks mixins and
P++'s 2.

| Seam | Consulted by | Retires |
|---|---|---|
| Cable connectivity (§3.3) | `BlockCable.isCable`, `TileCable.getCableBounds`, cable render handler, `ItemCable.onItemUse` | `BlockCable_Mixin`, `CableBlockRenderingHandler_Mixin`, `ItemCable_Mixin` |
| Turtle fuel provider | `TurtleRefuelCommand` | `TurtleRefuelCommand_Mixin` |
| Pocket upgrade registry | `ItemPocketComputer` (create/update/name) | `ItemPocketComputerMixin`, and (with the guard widening) `PocketAPIMixin` |

**Pass 3 — M5.** `PeripheralAPI_Mixin` (addon-side; no CC change) and the `TileCable`
decision (§8).

### Net effect

| Fork | Mechanisms now | After pass 1 | After pass 2 | After pass 3 |
|---|---|---|---|---|
| CCTweaks | 15 | 6 | 2 | **0–1** (see §8) |
| Peripherals++ | 3 late | 2 | **0** | 0 |
| OpenPeripheralAddons | 1 | **0** | 0 | 0 |
| OpenPeripheralIntegrations | 4 | **0** | 0 | 0 |
| OpenPeripheralCore | 0 | 0 | 0 | 0 |

---

## 8. The one genuine holdout: `TileCable_Mixin`

`TileCable_Mixin` is ~25 `@Overwrite`s of modem/cable/network behaviour. It cannot be
removed by M1–M3, and M6 (merge the extended network into CC) is rejected outright —
that network *is* the divergence.

Two viable endgames:

- **M4 — a CC-side cable-node seam.** Retires `TileCable_Mixin` only partially: the
  tile's network behaviour (`transmit`, `addReceiver`, `networkChanged`,
  `togglePeripheralAccess`, NBT, TTL) still has to live somewhere. A seam can route
  the *connectivity* questions into CCTweaks, but not replace the tile.
- **M5 — CCTweaks owns its cable.** Ship CCTweaks' own cable/modem tile
  (`BlockNetworked` already exists as its block) and stop patching CC's `TileCable`.
  This is the only route to genuinely 0, and it is a bounded refactor of
  `blocks/network/*` plus `integration/multipart/*`. Cost: CC's wired modem/cable stops
  bridging into CCTweaks' network unless CCTweaks registers a *separate* node for the
  CC tile — which is a feature decision, not a porting detail.

Interim, so the fork can build at all: delete the 6 dead/failing `@Overwrite`s (§2.7),
rebase the survivors onto our `TileCable`, and exclude `TileWiredModemFull` (PR #28)
from the network. Contract the mixin while it survives (drop the overwrites of methods
our tile no longer drives). That is M7 with M5 as the named endgame.

---

## 9. Sequence

1. **Repair CCTweaks' `TileCable_Mixin`** so the fork applies at all — nothing else is
   testable until then.
2. **Pass 1** (M1/M3) in CC on top of PR #28 → tag. Nothing here changes an existing
   code path.
3. **Delete** the 9 CCTweaks accessors/invokers, P++'s accessor, OPA's reflection, and
   OPI's four `MethodAccess` sites against that tag.
4. **Pass 2** (M4 seams), one seam per branch: fuel → cable connectivity → pocket
   upgrades. Each lands with its fallback test.
5. **Pass 3**: `PeripheralAPI_Mixin`'s move into the addon, then the `TileCable`
   decision (§8).
6. **Clean-room merge** — after which the seams that were only bought to satisfy an
   addon boundary can be re-evaluated, with CC still at 1:1.

---

## 10. Residual risks

- **M4 seams are the parity surface to watch.** Each one is new code in a CC execution
  path; each needs an "unregistered ⇒ today's behaviour" test, not just a review.
- **`TileCable_Mixin` is the standing exception.** Section §8 must end in a decision
  (own the tile, or keep the mixin), not drift.
- **Icon statics are client-only.** `getModemSideTexture`, like the existing
  `getModemFaceTexture`, returns `null` until `registerIcons` runs.
- **OPI direct calls trade runtime safety for compile-time safety.** That is the right
  trade now that CC is an `api` dependency; the guard is "keep those members public".
- **CCTweaks does not currently apply** (6 dead overwrites); the "20 mixins" state is
  broken, not merely divergent.

---

## References

- CCTweaks inventory: `CCTweaks-Legacy/docs/remove-cc-mixins.md`
- Peripherals++ **fork**: `MrFuzzihead/PeripheralsPlusPlus` @ `main` (pinned to
  `Computercraft-Legacy:3.2.0:dev`); upstream `austinv11/PeripheralsPlusPlus` cited
  only to note the ASM/reflection that was already ported away
- OpenPeripheralCore fork: `MrFuzzihead/OpenPeripheral` (upstream `OpenMods/OpenPeripheral`)
- OpenPeripheralAddons fork: `MrFuzzihead/OpenPeripheral-Addons` (upstream `OpenMods/OpenPeripheral-Addons`)
- OpenPeripheralIntegrations fork: `MrFuzzihead/OpenPeripheral-Integration` (upstream `OpenMods/OpenPeripheral-Integration`)
- CC-side sources cited by line: `TileCable.java`, `TileTurtle.java`,
  `TileComputerBase.java`, `TileModemBase.java`, `ItemPocketComputer.java`,
  `PeripheralAPI.java`, `PocketAPI.java`, `PocketUpgrades.java`,
  `CCTurtleProxyCommon.java`, `ComputerFamily.java`, `BlockCable.java`, `ItemCable.java`,
  `TurtleRefuelCommand.java` (all under `src/main/java/dan200/computercraft/`)
