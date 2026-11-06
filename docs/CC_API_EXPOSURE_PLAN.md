# CC API Exposure Plan — the Computercraft release that unblocks the addons

**Objective.** Remove **every** ComputerCraft-targeting mechanism — mixin, accessor,
invoker, ASM transformer, reflection, access transformer — from the three maintained
forks (`CCTweaks-Legacy`, `PeripheralsPlusPlus`, `OpenPeripheral` + `-Addons` +
`-Integration`).

**Constraint.** `Computercraft-Legacy` stays **1:1 with upstream CC: Tweaked**.

**Scope of this document.** Only the **Computercraft-Legacy** deliverable is in scope:
the public surface that has to ship in a CC release so the addons can then do their
own work, in their own repos, on their own schedule. Every addon-side change is
**deferred** — recorded here only as the justification for each public member
(§3), not as work to be done now.

**Status.** Branch `feature/api-exposure-for-mixin-removal`, cut from
`feature/wired-modem-full-cleanroom` (PR #28). Fork facts are verified against the
workspace forks at `main`.

---

## 0. Which rung runs where

Removal methods, and who executes each. **CC only ever performs M3, and optionally
M4.**

| Rung | Method | Executed in | CC change |
|---|---|---|---|
| M1 | Delete — unused, nothing replaces it | **addon** (deferred) | none |
| M2 | Replace with an **existing** public CC member | **addon** (deferred) | none |
| **M3** | **CC visibility**: getter/setter over existing state, or `private`/`protected` → `public`, value passed through verbatim | **CC** | additive, no new call sites |
| M4 | CC **additive seam**: new interface/registry/hook CC consults, no-consumer path identical to today | **CC** (optional, §4) | additive, *new call sites* |
| M5 | Addon owns the code (own tile/block, own renderer, direct calls) | **addon** (deferred) | none |
| M6 | CC internals behaviour change | rejected | divergence |
| M7 | Keep the mechanism; interim only, with a named endgame | **addon** (deferred) | none |

Consequences worth stating plainly:

- "Delete the accessor", "swap to `getAvailablePeripherals()`", "rewrite
  `PeripheralAPI_Mixin` to `attach()`" and "ship our own cable tile" are all **addon
  work**. Nothing in this document requires CC to participate in them.
- CCTweaks does not currently apply at all (6 dead `@Overwrite`s, §3.1). Fixing that
  is addon work too — it is *not* a prerequisite for THIS release, only for their own.

---

## 1. The deliverable

**Ten public members + one guard fix, all additive, no existing code path changes.**

| # | Public member | Consumer (addon) | What it retires on their side |
|---|---|---|---|
| 1 | `TileCable#getAttachedPeripheralID()` | CCTweaks `PartModem` | `TileCable_Accessor` |
| 2 | `TileCable#getModemSideTexture(int anim)` | CCTweaks `PartModem` | `TileCable_Accessor` |
| 3 | `TileCable#isPeripheralAccessAllowed()` | OPA `TileEntityPeripheralProxy` | `FieldAccess` on `m_peripheralAccessAllowed` |
| 4 | `TileTurtle#setMoved(boolean)` | CCTweaks `ItemComputerUpgrade` | `TileTurtle_Accessor` |
| 5 | `TileComputerBase#transferStateFrom(TileComputerBase)` → public | CCTweaks `ItemComputerUpgrade` | `TileComputerBase_Invoker` |
| 6 | `ItemPocketComputer#createServerComputer(...)` → public | CCTweaks `PocketAccess` | `ItemPocketComputer_Invoker` |
| 7 | `Computer#getLuaMachine()` | CCTweaks `ItemDebugger` | `Computer_Accessor` |
| 8 | `ServerComputer#getComputer()` | CCTweaks `ItemDebugger` | `ServerComputer_Accessor` |
| 9 | `CobaltMachine#getLuaState()` | CCTweaks `ItemDebugger` | `CobaltMachine_Accessor` |
| 10 | `CCTurtleProxyCommon#getTurtleUpgrades()` (unmodifiable view) | P++ NEI handler | `CCTurtleProxyCommonAccessor` |
| 11 | `PocketAPI#doEquipBack` — refuse *any* installed upgrade | P++ `PocketAPIMixin` | `PocketAPIMixin` |

Item 11 is the only behaviour change, and it is **unreachable without a third party**:
`PocketAPI.java:147` currently refuses only when `PocketUpgrades.isUpgrade(id)` holds,
i.e. `1 ≤ id ≤ NPC_INTERFACE` (`PocketUpgrades.java:75`), and every writer of the
`upgrade` tag in CC is `PocketUpgrades`-driven. Widening the guard to "any `upgrade` key
present" therefore cannot alter a CC-only world. It ships with a test pinning ids 1–6
and the absent-tag case, and a `NOTICE` line.

### Not in this release

- **All addon-side changes** (M1/M2/M5 and the `TileCable_Mixin` repair). Deferred.
- **The M4 seams** — turtle fuel provider, cable connectivity, pocket upgrade registry
  (§4). Optional; see the staging decision below.
- **Nothing that adds a new call site** to any CC execution path, unless you take §4.

### Staging decision

| | What ships | Addon outcome | Cost |
|---|---|---|---|
| **A (recommended)** | the 11 items above | addons delete 11 of 23 mechanisms (all accessors, invokers, OPA reflection, P++'s accessor + guard mixin, OPI's four lookups); their behaviour mixins stay | zero behavioural risk; second CC release later for the seams |
| **B** | the 11 items **+ the 3 M4 seams** | addons can reach everything except `TileCable_Mixin` in one round | 3 new call sites inside CC paths must be designed and fallback-tested before the tag |

A is recommended because M3 content is provably risk-free (every existing code path
emits the same code) while each M4 seam is new code inside a CC execution path. Ship A,
let the addons prove the surface is sufficient in practice, then design the seams
against that experience. If you would rather not cut two tags, B is defensible — the
seams are small — but they are the only thing in this plan that needs a spec.

### Two members worth a second look before cutting

- **Items 7–9** exist only for CCTweaks' `ItemDebugger`. Three public members for a
  debug item is a poor trade if the debugger is not sacred — dropping it deletes
  `Computer_Accessor`, `ServerComputer_Accessor` and `CobaltMachine_Accessor` for free.
- **Item 6** exists only for CCTweaks' `PocketAccess` (its integration with P++). The
  merged mod would own both sides, so the widening may be short-lived. Harmless to
  ship, but note it is provisional.

---

## 2. Verified inventory

### CCTweaks-Legacy — `main` @ `55ce41d`

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
no CC involvement. Out of scope entirely.

### PeripheralsPlusPlus — fork `main`

| File | Kind | Target |
|---|---|---|
| `ItemPocketComputerMixin` | `@Inject` + `@ModifyExpressionValue` + `@Inject` | `ItemPocketComputer` create / update / name |
| `PocketAPIMixin` | `@Shadow` + `@Inject` | `PocketAPI.doEquipBack` |
| `CCTurtleProxyCommonAccessor` | accessor | `CCTurtleProxyCommon.m_turtleUpgrades` |
| 4 × `early/*Accessor` | accessors | vanilla only — not CC, not in scope |

### OpenPeripheral — forks

No CC-targeting mixins, no ASM, no AT. Three CC touchpoints: OPA's
`m_peripheralAccessAllowed` reflection (item 3 above), and OPI's four meta providers.

---

## 3. Supplier-side facts behind the number

These are the findings that decide what CC must ship versus what the addons can do
themselves. They are recorded so the surface stays justified; none of them is work.

1. **`getAvailablePeripherals()` is already implemented.** Our `IComputerAccess`
   backport is implemented on `PeripheralAPI$PeripheralWrapper`
   (`PeripheralAPI.java:501`/`:515`), so CCTweaks' `PeripheralAPI_Accessor` and
   `PeripheralWrapper_Accessor` need **no CC change** — their mixin just has to call
   `this.getAvailablePeripherals()`. Both are M2/M5, not M3.
2. **Cable icons need less than expected.** `PartCable` indexes only `[0]`/`[1]`, which
   the existing `TileCable.getCableItemTexture(int)` maps exactly; `BlockNetworked`
   builds `s_modemIcons[i*2]` (`i<4`), which is the existing `getModemFaceTexture(int)`.
   Only the **odd** modem indices (used by `PartModem`) need a new getter → item 2.
3. **`TileTurtle` has no `setMoved`** — `m_moved` is assigned directly
   (`TileTurtle.java:485`); `getMoved()` has no caller and `hasMoved()` is public
   (`:62`). So the read side is already free, the write side is item 4, and
   `TileTurtle_Accessor` disappears only when both are settled.
4. **`TileTurtle.transferStateFrom(TileTurtle)` is already public**; only the
   `TileComputerBase` overload is `protected` (`TileComputerBase.java:379`) → item 5.
5. **OP-Integrations compiles against CC** (`dependencies.gradle:39`,
   `api("com.github.MrFuzzihead:Computercraft-Legacy:3.2.0:dev")`) and `ComputerFamily`
   is a **public enum** (`ComputerFamily.java:3`), so its four `MethodAccess` sites are
   replaceable with direct calls — **no CC change**, and `getFamily` no longer needs to
   return `Object`. Standing guard for the release: do not narrow those members.
6. **`ModuleComputerCraft_Mixin` is not a CC mixin** — it targets
   `openperipheral.interfaces.cc.ModuleComputerCraft`. CCTweaks' CC count is 15, not 16.
7. **`TileCable#setAttachedPeripheralID` is not needed** — nothing writes the field.
8. **CCTweaks' `TileCable_Mixin` has 6 dead `@Overwrite`s** against our `TileCable`
   (`attachPeripheral`, `detachPeripheral` — no such method; `getTypeRemote`,
   `getMethodNamesRemote`, `callMethodRemote` — only on the inner `TileCable$Peripheral`;
   `getConnectedPeripheral` — `private` over our `protected`). Addon-side repair (§5).

---

## 4. Deferred: the M4 seams (not in this release)

If you later choose staging B, these are the only CC changes that add call sites. Each
must ship with an "unregistered ⇒ today's behaviour" test.

| Seam | Consulted by | Retires (addon-side) |
|---|---|---|
| Turtle fuel provider registry, fallback `TileEntityFurnace.getBurnTime` | `TurtleRefuelCommand` | CCTweaks `TurtleRefuelCommand_Mixin` — also removes that overwrite's four behavioural regressions (it drops `turtleDisabledActions`, container items, the `m_limit == 0` test-only branch and `InventoryUtil` slot handling) |
| Cable connectivity (neighbour-ask interface + `ComputerCraftAPI` registration) | `BlockCable.isCable`, `TileCable.getCableBounds`, cable render handler, `ItemCable.onItemUse` | CCTweaks `BlockCable_Mixin`, `CableBlockRenderingHandler_Mixin`, `ItemCable_Mixin` |
| Pocket upgrade registry (`IPocketComputerUpgrade` + registration) | `ItemPocketComputer` create / update / display name | P++ `ItemPocketComputerMixin` |

Design rules when/if they land: no CodeChicken dependency in CC (a marker interface
plus a registration list suffices); `false`-by-default; and a test asserting an
unregistered world connects, renders and refuels identically.

---

## 5. Deferred: the addon backlog

Recorded so each addon's work is known, not scheduled. All of it happens in
`CCTweaks-Legacy`, `PeripheralsPlusPlus` and the OpenPeripheral forks, against the tag
produced by §1.

**CCTweaks** — after the release: delete 9 accessors/invokers (M1/M2/M3), rewrite
`PeripheralAPI_Mixin` to use `IPeripheral.attach()` + `getAvailablePeripherals()`
(M5), and repair `TileCable_Mixin` (delete the 6 dead overwrites) before anything is
testable. Its four remaining behaviour mixins (`TurtleRefuelCommand`, `BlockCable`,
`ItemCable`, `CableBlockRenderingHandler`) need the M4 seams, i.e. a later CC tag.

**Peripherals++** — delete `CCTurtleProxyCommonAccessor` and `PocketAPIMixin`; keep
`ItemPocketComputerMixin` until the pocket seam exists.

**OPA** — replace field reflection with item 3's getter and turn
`togglePeripheralAccess`'s `MethodAccess` into a direct call.

**OPI** — replace four `MethodAccess` sites with direct calls; no CC dependency added,
it already has one.

**The one genuine holdout** remains `TileCable_Mixin` (~25 overwrites of modem/cable
network behaviour). It cannot be removed by M1–M3, and M6 (merging that network into
CC) is rejected. Its endgame is M5 — CCTweaks ships its own cable tile instead of
patching CC's — with the feature consequence that CC's wired modem stops bridging into
CCTweaks' network. That decision belongs to the addon work and the eventual merge, not
to this release.

---

## 6. Risk register (release-scoped)

- **Items 1–10 are pure visibility**, so the compile output for every existing path is
  identical; review alone is sufficient. Item 11 needs the reachability test.
- **Public members with one consumer are cheap to ship and expensive to retract.**
  Items 7–9 (debugger) and 6 (P++ integration) are the ones to reconsider now rather
  than after the tag.
- **The tag is the addons' floor.** CCTweaks is pinned at `3.2.0:dev` and does not
  apply; P++ and OPI already build against `3.2.0:dev`. A release containing §1 is
  therefore the prerequisite for all three to start.
- **Icon getters are client-only**, like the existing `getModemFaceTexture`: `null`
  until `registerIcons` runs, which is already the documented contract.

---

## 7. Sequence for the release

1. Rebase on PR #28 (full wired modem); nothing in §1 conflicts with it.
2. Decide the two open items: keep or drop `ItemDebugger` (items 7–9), keep or drop the
   `PocketAccess` integration (item 6).
3. Land items 1–11 as one additive commit set, plus the item-11 reachability test and
   the `NOTICE` line.
4. `./gradlew build test` and a `runServer25` smoke boot; no behaviour change expected.
5. Tag. Hand the addons the member list in §1 as their contract.

---

## References

- CCTweaks inventory: `CCTweaks-Legacy/docs/remove-cc-mixins.md`
- Peripherals++ fork: `MrFuzzihead/PeripheralsPlusPlus` @ `main`; upstream
  `austinv11/PeripheralsPlusPlus` only for the ASM/reflection already ported away
- OpenPeripheral forks: `MrFuzzihead/OpenPeripheral`, `-Addons`, `-Integration`
- CC sources cited by line: `TileCable.java`, `TileTurtle.java`, `TileComputerBase.java`,
  `TileModemBase.java`, `ItemPocketComputer.java`, `PeripheralAPI.java`, `PocketAPI.java`,
  `PocketUpgrades.java`, `CCTurtleProxyCommon.java`, `ComputerFamily.java`,
  `BlockCable.java`, `ItemCable.java`, `TurtleRefuelCommand.java`
