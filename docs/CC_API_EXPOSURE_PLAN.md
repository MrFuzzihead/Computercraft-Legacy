# CC API Exposure Plan — removing CC mechanisms from the three maintained forks

**Goal.** Remove every ComputerCraft-targeting mechanism — mixin, accessor, invoker,
ASM transformer, reflection, access transformer — from the three maintained forks
(`CCTweaks-Legacy`, `PeripheralsPlusPlus`, `OpenPeripheral` + `-Addons` +
`-Integration`), while holding `Computercraft-Legacy` to **1:1 behavioural parity
with upstream CC: Tweaked**.

**Status.** Branch `feature/api-exposure-for-mixin-removal`, cut from
`feature/wired-modem-full-cleanroom` (PR #28). Everything below is verified against
the workspace forks at their current `main`, not against upstream addon sources.

**Non-goals.** Restructuring the addons; porting addon features into CC; any CC
change whose only justification is saving an addon a mixin.

---

## 0. Decision hierarchy

Every mechanism is judged against these tiers, in order. Lower tiers win.

| Tier | Meaning | Parity risk | Default |
|---|---|---|---|
| **T0** | Delete in the addon — the mechanism is unused, or something already public does the job. | none | preferred |
| **T1** | **Pure visibility** in CC — a getter/setter over existing state, or `private`/`protected` → `public`. Returns/writes the value verbatim; no new logic, no call site changes, no new branch or interface. | none: the emitted code for every existing path is unchanged | preferred where T0 fails |
| **T2** | **Additive API** in CC — a new public member CC itself never calls, or one carrying a little logic of its own (a view, a copy, a derived value). | none at runtime; permanent surface | acceptable, sparingly |
| **T3** | **Additive hook** in CC — a new call site / branch that a third party registers into. | low, *if* the fallback is provably today's code path | avoid |
| **T4** | **Behaviour change** in CC internals. | direct | reject, unless unreachable without a third party |
| **T5** | Keep the mechanism in the addon. | none | the correct answer surprisingly often |

**Load-bearing rule:** an addon that currently uses `@Overwrite` can almost always
switch to a **cancellable `@Inject` at `HEAD` that falls through** to CC's original
body. `@Inject` + fall-through *is* the extensibility seam — it lives in the addon,
changes nothing in CC, and preserves CC's own logic for everyone else. A CC-side hook
is only warranted when the addon needs to observe something it cannot reach at all.

Applied consistently, this rule deletes most of the CC API the previous revision of
this document asked for.

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
out of scope here; nothing in CC helps them.

### PeripheralsPlusPlus — fork `main`

3 late (CC) mixins + 4 early (vanilla-only) mixins:

| File | Kind | Target |
|---|---|---|
| `ItemPocketComputerMixin` | 1 `@Inject` + 1 `@ModifyExpressionValue` + 1 `@Inject` | `ItemPocketComputer` create / update / display name |
| `PocketAPIMixin` | `@Shadow` + `@Inject` | `PocketAPI.doEquipBack` |
| `CCTurtleProxyCommonAccessor` | accessor | `CCTurtleProxyCommon.m_turtleUpgrades` |
| 4 × `early/*Accessor` | accessors | `EntityLiving`, `EntityLivingBase`, `ItemBucket`, `Gui` — **vanilla, not CC** |

The upstream ASM transformer, `ppp_at.cfg` access transformer and reflective
`ComputerCraftRegistry` unwrapping are already gone from the fork.

### OpenPeripheral (Core / Addons / Integration) — forks

No CC-targeting mixins and no ASM/AT anywhere. Mechanism surface is
`openmods.reflection` only, and only three files reach CC:

| Fork | File | What it touches | Verdict |
|---|---|---|---|
| Addons | `TileEntityPeripheralProxy` | `TileCable.m_peripheralAccessAllowed` (private field) + `togglePeripheralAccess` (already public) | 1 getter fixes it |
| Integrations | `ComputerMetaProvider`, `MediaMetaProvider`, `PrintoutMetaProvider`, `TurtleMetaProvider` | **already-public** CC members via string-based `MethodAccess` | nothing to do |
| Core | `PeripheralProvider`, `ProxyAdapterPeripheral` | the public `IPeripheralProvider` contract; reflection over its own proxy class | nothing to do |

---

## 2. Corrections to the previous revision

The earlier draft was written before the parity constraint was ranked first, and its
inventory had four errors. Both are fixed here.

1. **`ModuleComputerCraft_Mixin` is not a CC mixin** — it targets
   `openperipheral.interfaces.cc.ModuleComputerCraft` and lives in the
   `OPENPERIPHERAL` enum group. CC-targeting count is 15, not 16.
2. **Three files were missing or mis-attributed.** The old Phase-1 table folded
   `ItemPocketComputer_Invoker` (`createServerComputer`) and
   `TileComputerBase_Invoker` (`transferStateFrom`) into "`TileTurtle_Accessor`
   (part)", and omitted `PeripheralWrapper_Accessor` entirely.
3. **`PeripheralAPI_Accessor` and `PeripheralWrapper_Accessor` are unnecessary.**
   Our `IComputerAccess` already declares `getAvailablePeripherals()` /
   `getAvailablePeripheral(String)` (the CC:T backport), and
   `PeripheralAPI$PeripheralWrapper` **already implements them**
   (`PeripheralAPI.java:501` / `:515`). CCTweaks' `PeripheralAPI_Mixin` implements
   `IComputerAccess` and can simply call `this.getAvailablePeripherals()`. Both
   accessor files delete with **no CC change at all**. Likewise the old "add
   `PeripheralAPI#getEnvironment()`" row is dead.
4. **`TileCable#setAttachedPeripheralID` is not needed** — nothing in CCTweaks writes
   the field; only `PartModem` reads it.

Also verified while checking these:

- `TileTurtle.hasMoved()` is already public (`TileTurtle.java:62`) and
  `TileTurtle_Accessor.getMoved()` has no caller — only `setMoved` is used.
- `TileTurtle.transferStateFrom(TileTurtle)` is already public; only the
  `TileComputerBase` overload (`TileComputerBase.java:379`) is `protected`.
- `TileComputerBase.createServerComputer()` is already public — the widening is only
  needed for `ItemPocketComputer`'s `private createServerComputer` (`:413`).
- `BlockCable.isCable` and `ItemCable.onItemUse` are public and non-final, so both
  current `@Overwrite`s can become cancellable `@Inject`s.
- `TileCable_Mixin` has **6 dead `@Overwrite`s** against our `TileCable`, which is why
  CCTweaks does not currently apply:
  `attachPeripheral` and `detachPeripheral` (no such method), `getTypeRemote`,
  `getMethodNamesRemote`, `callMethodRemote` (exist only on the inner
  `TileCable$Peripheral`, not the outer class), and `getConnectedPeripheral`
  (overwritten `private`, ours is `protected` — a visibility narrowing Mixin rejects).

---

## 3. CCTweaks-Legacy

### 3.1 Accessors and invokers — all nine go

| Mechanism | Verdict | Action |
|---|---|---|
| `TileCable_Accessor.getAttachedPeripheralID` | **T1** | add `public int getAttachedPeripheralID()` |
| `TileCable_Accessor.s_cableIcons` | **T0** | `PartCable` only indexes `[0]`/`[1]`; `TileCable.getCableItemTexture(int)` is already public and maps to exactly those two icons |
| `TileCable_Accessor.s_modemIcons` | **T0 + T1 split** | `BlockNetworked.getModemIcon` builds `s_modemIcons[i*2]` for `i<4` — that *is* the existing public `TileCable.getModemFaceTexture(int)`. `PartModem` uses the odd indices too (`texture+1`), which nothing public exposes → add `public static IIcon getModemSideTexture(int anim)` |
| `TileTurtle_Accessor.getMoved` | **T0** | unused; `hasMoved()` is already public |
| `TileTurtle_Accessor.setMoved` | **T1** | CC has no setter today (`m_moved` is written directly at `TileTurtle.java:485`), so add `public void setMoved(boolean)`; `hasMoved()` already exists as the read side |
| `TileComputerBase_Invoker` | **T1** | widen `TileComputerBase#transferStateFrom(TileComputerBase)` to public |
| `ItemPocketComputer_Invoker` | **T1 or T0** | widen `ItemPocketComputer#createServerComputer` to public, *or* drop CCTweaks' `PocketAccess` P++ integration (it is `private` and only used by `getModemLight`/`getUpgrades`) — decide before the merge, since the merged mod owns both sides |
| `Computer_Accessor`, `ServerComputer_Accessor`, `CobaltMachine_Accessor` | **T1, debugger-only** | three getters, existing only so `ItemDebugger` can reach the Lua state. Zero-risk, but note the alternative is **T0**: drop `ItemDebugger` and skip all three |
| `PeripheralAPI_Accessor`, `PeripheralWrapper_Accessor` | **T0** | delete; use `getAvailablePeripherals()` (see §2.3) |

Two small notes on the icon work: exposing an `IIcon[]` (what the old draft proposed)
is worse than exposing indexed getters — the arrays are mutable statics, and a
getter per anim state needs no defensive copy.

### 3.2 Behaviour mixins — all six stay, four reshaped

| Mixin | Verdict | CC change | Rationale / reshape |
|---|---|---|---|
| `TurtleRefuelCommand_Mixin` | **T5** | **none** | The current `@Overwrite` is *lossy*: it drops `turtleDisabledActions`, container items, the `m_limit == 0` test-only branch, and `InventoryUtil` slot handling. Reshape to a cancellable `@Inject(HEAD)`: if a `ITurtleFuelProvider` claims the stack, run the provider path and cancel; otherwise return and let **CC's own `execute`** run. Result: no fuel registry in CC, and CCTweaks gets *closer* to CC behaviour, not further. |
| `BlockCable_Mixin` | **T5** | **none** | `isCable` is a public static; a cancellable `@Inject` that returns `true` when `NetworkAPI.registry().isNode(...)` and falls through otherwise reproduces today's intent without an `ICableNode` interface in CC. |
| `ItemCable_Mixin` | **T5** | **none** | Cancellable `@Inject(HEAD)` on `onItemUse`: attempt multipart placement, fall through to CC's placement. This also lets CCTweaks delete its `cCTweaks_Legacy$nativePlace` copy of CC code — the copy is the main maintenance burden in this file. |
| `PeripheralAPI_Mixin` | **T5** | **none** | The two `attach`/`detach` `@Inject`s are genuinely addon-side (network attach notification). After the T0 fix above, the mixin needs no accessors at all. |
| `CableBlockRenderingHandler_Mixin` | **T5** | **none** | This is the addon's own renderer; it has to overwrite. Nothing in CC can or should absorb it. |
| `TileCable_Mixin` | **T5** | **none** | See 3.3. |

### 3.3 `TileCable_Mixin` — keep it in CCTweaks, contract it

This is the one genuinely invasive mechanism (network/cable/modem behaviour). Under
the T4 rule it can never move into CC: it *is* the behaviour divergence.

Interim repair while PR #28 lands, so CCTweaks applies at all:

1. Delete the 6 dead/failing `@Overwrite`s listed in §2 (or the mixin aborts).
2. Rebase the surviving overwrites onto our `TileCable` (PR #28 added
   `TileWiredModemFull`, which must not be dragged into the cable network).
3. The overwrites of methods our `TileCable` no longer drives end-to-end
   (`dispatchPacket`, `receivePacket`, `findPeripherals`, `getConnectedPeripheral` —
   a mix of no-ops and pure redirects) can become `@Inject`s or be dropped outright,
   which shrinks the mixin from 27 hooks to the handful that actually redirect
   modem/cable state.
4. Only then decide whether the extended network still earns its keep — that decision
   belongs to the merged mod, not to CC.

---

## 4. PeripheralsPlusPlus

| Mechanism | Verdict | Action |
|---|---|---|
| `CCTurtleProxyCommonAccessor` | **T2** | add `CCTurtleProxyCommon#getTurtleUpgrades()` returning an unmodifiable view. `getTurtleUpgrade(int)` already exists but ids are arbitrary (P++ uses ≥ 7), so a bounded loop is not a substitute. 1-line accessor for the NEI handler. |
| `PocketAPIMixin` | **T4, unreachable without a third party** | `PocketAPI.java:147` refuses only when `PocketUpgrades.isUpgrade(id)` is true, i.e. `1 ≤ id ≤ NPC_INTERFACE` (`PocketUpgrades.java:75`). Nothing in CC can write an `upgrade` tag outside that range — every writer is `PocketUpgrades`-driven. So widening the guard to "any `upgrade` key present" changes **no reachable CC-only state**, and deletes the mixin. Document it in `NOTICE`/parity notes as a deliberate, unreachable-only fix. |
| `ItemPocketComputerMixin` | **T5** | **Keep.** A pocket-upgrade seam in CC would mean new call sites in `ItemPocketComputer` (create, update, name) and `PocketAPI` — i.e. **T3** — to remove three hooks from one addon. Not justified by the hierarchy; revisit after the merge, where the seam can be internal. |
| 4 × early accessors | n/a | vanilla targets, nothing to do |

Consistency note: if the `doEquipBack` guard is widened, decide explicitly whether
`doIsEquipped` (`PocketAPI.java:271`) should also report third-party upgrades. P++
does not read it, so the minimal change leaves it alone — but the asymmetry should be
a recorded decision, not an oversight.

---

## 5. OpenPeripheralAddons

| Mechanism | Verdict | Action |
|---|---|---|
| `FieldAccess.create(cableClass, "m_peripheralAccessAllowed")` in `TileEntityPeripheralProxy` | **T1** | add `TileCable#isPeripheralAccessAllowed()`. `togglePeripheralAccess` is already public (`TileCable.java:578`), so the `MethodAccess` for it can become a direct call in the same edit. |

That is OPA's only CC-privates touchpoint. Everything else uses `dan200.computercraft.api.*`.

---

## 6. OpenPeripheralCore and OpenPeripheralIntegrations

Nothing to change in CC, nothing to delete in the addons beyond dead indirection.

- **Core** — `LuaReflectionHelper`, `IndexedMethodMap`, `NamedMethodMap`,
  `PropertyListBuilder`, `AdapterWrapper`, `CommonMethodsBuilder` reflect adapter
  targets and their own classes. `PeripheralProvider` uses the public
  `IPeripheralProvider` contract. No CC privates anywhere.
- **Integrations** — the four CC meta providers resolve **already-public** members
  (`IComputerItem#getComputerID/getLabel/getFamily`, `ComputerCraft.getMedia`,
  `ItemPrintout#getTitle/getPageCount/getText`, `ITurtleItem#getFuelLevel/getUpgrade`)
  by string. Nothing becomes public; the only requirement is a standing rule:
  **do not narrow these members' visibility in future CC refactors**, or the string
  lookups break silently at runtime. A future typed API enum for `ComputerFamily`
  (currently returned as `Object`) would let `ComputerMetaProvider` stop guessing.

---

## 7. The complete CC change set

Nine classes, seven changes — all T0/T1/T2 plus one unreachable-only T4 fix.

| # | Class | Change | Tier | Retires |
|---|---|---|---|---|
| 1 | `TileCable` | `getAttachedPeripheralID()`, `getModemSideTexture(int)`, `isPeripheralAccessAllowed()` | T1 | `TileCable_Accessor` (+ OPA reflection) |
| 2 | `TileTurtle` | add `setMoved(boolean)` (no setter exists today) | T1 | `TileTurtle_Accessor` |
| 3 | `TileComputerBase` | `transferStateFrom(TileComputerBase)` → public | T1 | `TileComputerBase_Invoker` |
| 4 | `ItemPocketComputer` | `createServerComputer(...)` → public | T1 | `ItemPocketComputer_Invoker` |
| 5 | `Computer`, `ServerComputer`, `CobaltMachine` | `getLuaMachine()`, `getComputer()`, `getLuaState()` | T1 | `Computer_Accessor`, `ServerComputer_Accessor`, `CobaltMachine_Accessor` |
| 6 | `CCTurtleProxyCommon` | `getTurtleUpgrades()` unmodifiable view | T2 | `CCTurtleProxyCommonAccessor` |
| 7 | `PocketAPI` | `doEquipBack` guard: refuse *any* installed upgrade | T4 (unreachable) | `PocketAPIMixin` |

No new interfaces, no new events, no new registries, no call-site changes anywhere in
CC.

### Net effect

| Fork | CC mechanisms before | after | Notes |
|---|---|---|---|
| CCTweaks | 15 (7 accessors + 2 invokers + 6 mixins) | **6 mixins** | accessors/invokers: 9 → 0 |
| Peripherals++ | 3 late | **1 late** | 2 early vanilla accessors untouched |
| OpenPeripheralAddons | 1 reflective site | **0** | direct call |
| OpenPeripheralCore | 0 | 0 | — |
| OpenPeripheralIntegrations | 4 string lookups | 4 string lookups | already-public targets |

Twelve mechanisms deleted against seven public members added and one unreachable
behaviour fix.

---

## 8. What we deliberately do **not** add

Each of these was in the previous revision; each is now cancelled because the addon
can solve it itself (T5) or never needed it (T0).

| Withdrawn CC work | Why |
|---|---|
| Pocket-upgrade registry / upgrade event in `ItemPocketComputer` | T3; removes 3 hooks from one addon at the cost of new call sites in CC. P++ keeps `ItemPocketComputerMixin`. |
| `ICableNode` marker interface + `isCable` hook | T3; a cancellable `@Inject` in CCTweaks reproduces it exactly. |
| Fuel-provider registry + `TurtleRefuelCommand` consult | T3; the reshaped `@Inject` is *more* faithful to CC than the current overwrite. |
| Cable placement hook in `ItemCable` | T3; cancellable `@Inject` on `onItemUse`, and it deletes the duplicated `nativePlace`. |
| Peripheral lifecycle seam in `PeripheralAPI` | T3; the two `@Inject`s are addon-side and need no accessors after T0. |
| `TileCable#setAttachedPeripheralID` | never used. |
| `TileCable#getCableIcons()` / `getModemIcons()` array exposure | `getCableItemTexture` covers the cable; `getModemFaceTexture` covers `BlockNetworked`; only the odd modem indices need a getter (§3.1). |
| `PeripheralAPI#getEnvironment()` | `getAvailablePeripherals()` already implemented (`PeripheralAPI.java:501`). |
| Merging CCTweaks' extended `TileCable` network into CC | T4 — that *is* the divergence. |

---

## 9. Sequencing

1. **Publish the T1/T2 set** (items 1–7 above) as one additive CC branch on top of
   PR #28, then tag. Nothing in it changes an existing code path.
2. **CCTweaks**: delete the 9 accessors/invokers, apply the `TileCable_Mixin` interim
   repair (§3.3), and reshape the four behaviour mixins to inject-and-fall-through.
   This is the bulk of the work and it now touches **no** CC internals.
3. **P++**: delete `CCTurtleProxyCommonAccessor` (use the new view) and `PocketAPIMixin`
   (after the guard fix). Keep `ItemPocketComputerMixin`.
4. **OPA**: replace field reflection with the getter; OPI/Core need no change.
5. **Harmonisation pass**: all four mods against the tag, in one instance, with the
   ingame test checklist.
6. **Clean-room merge.** Only the merged mod may decide that a T3 seam is worth
   having — by then it can ship the accessor itself and CC stays at 1:1.

---

## 10. Residual risks

- **`TileCable_Mixin` is the permanent outlier.** Six overwrites + the network classes
  will need a rebase at every CC change that touches `TileCable`. Contracting it (§3.3)
  is the mitigation; deleting it is the escape hatch, at the cost of CCTweaks'
  extended network.
- **Icon statics are client-only.** `getModemSideTexture` (like the existing
  `getModemFaceTexture`) returns `null` until `registerIcons` runs; callers already
  treat icons that way.
- **String-based `MethodAccess` in OPI** breaks silently. Adding a
  `ComputerFamily`-typed API value and keeping the listed members public is the
  standing guard.
- **`PocketAPI` guard** is the only behaviour change. Gate it on a test asserting
  CC-only stacks (ids 1–6, absent tag) behave exactly as before.
- **CCTweaks is not currently applying** (6 dead overwrites); until §3.3 step 1 lands,
  the "20 mixins" state is broken rather than merely divergent.

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
  `CCTurtleProxyCommon.java`, `BlockCable.java`, `ItemCable.java`,
  `TurtleRefuelCommand.java` (all under `src/main/java/dan200/computercraft/`)
