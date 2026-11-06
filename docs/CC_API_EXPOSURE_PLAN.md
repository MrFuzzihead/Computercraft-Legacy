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

**Status: landed on this branch (phase 1, plus the §4.1 fuel seam and the §4.2 pocket seam).** Ten public members + one guard fix, all additive, no
existing code path changes. The test map is at the end of this section.

| # | Public member | Consumer (addon) | What it retires on their side | Test |
|---|---|---|---|---|
| 1 | `TileCable#getAttachedPeripheralID()` | CCTweaks `PartModem` | `TileCable_Accessor` | `TileCableExposureTest` |
| 2 | `TileCable#getModemSideTexture(int anim)` | CCTweaks `PartModem` | `TileCable_Accessor` | `TileCableExposureTest` |
| 3 | `TileCable#isPeripheralAccessAllowed()` | OPA `TileEntityPeripheralProxy` | `FieldAccess` on `m_peripheralAccessAllowed` | `TileCableExposureTest` |
| 4 | `TileTurtle#setMoved(boolean)` | CCTweaks `ItemComputerUpgrade` | `TileTurtle_Accessor` | `TileTurtleMovedTest` |
| 5 | `TileComputerBase#transferStateFrom(TileComputerBase)` → public | CCTweaks `ItemComputerUpgrade` | `TileComputerBase_Invoker` | `TileComputerBaseTransferStateTest` |
| 6 | `ItemPocketComputer#createServerComputer(...)` → public | CCTweaks `PocketAccess` | `ItemPocketComputer_Invoker` | `ItemPocketComputerServerComputerTest` |
| 7 | `Computer#getLuaMachine()` | CCTweaks `ItemDebugger` | `Computer_Accessor` | `ComputerGetLuaMachineTest` |
| 8 | `ServerComputer#getComputer()` | CCTweaks `ItemDebugger` | `ServerComputer_Accessor` | `ServerComputerGetComputerTest` |
| 9 | `CobaltMachine#getLuaState()` | CCTweaks `ItemDebugger` | `CobaltMachine_Accessor` | `CobaltMachineLuaStateTest` |
| 10 | `CCTurtleProxyCommon#getTurtleUpgrades()` (unmodifiable view) | P++ NEI handler | `CCTurtleProxyCommonAccessor` | `CCTurtleProxyCommonTurtleUpgradesTest` |
| 11 | `PocketAPI#doEquipBack` — refuse *any* installed upgrade | P++ `PocketAPIMixin` | `PocketAPIMixin` | `PocketAPIEquipGuardTest` |

`AddonApiSurfaceTest` re-asserts items 1–10 reflectively (declaring class, name, parameters, return
type, `public`), so a later refactor cannot quietly narrow one back and leave the addons stuck.

Item 11 is the only behaviour change, and it is **unreachable without a third party**: the
guard used to refuse only when `PocketUpgrades.isUpgrade(id)` held, i.e.
`1 ≤ id ≤ NPC_INTERFACE` (`PocketUpgrades.java:75`), and every writer of the `upgrade` tag
in CC is `PocketUpgrades`-driven. Widening the guard to "any `upgrade` key present"
therefore cannot alter a CC-only world. `PocketAPIEquipGuardTest` pins ids 1–6, ids
outside that range, a non-integer value, the absent-tag case, and that a refusal touches
neither the inventory nor the existing tag. `NOTICE` section 2 records the divergence.

Tests are chosen per member by what is actually assertable headless: the getters are
backed by state the tests can reach directly (NBT, mocks, a real `ServerComputer`), and
nothing is asserted through a Minecraft client renderer. The two branches that cannot be
reached in a unit test are called out in the test javadoc — `world.isRemote` and the
"no computer id yet" path in `createServerComputer`, both covered in game.

### Not in this release

- **All addon-side changes** (M1/M2/M5 and the `TileCable_Mixin` repair). Deferred.
- **The M4 seams** — the fuel seam landed (§4.1) and the pocket upgrade registry landed
  (§4.2); cable connectivity is still deferred (§4).
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

### Two members worth a second look (both resolved: kept)

- **Items 7–9** exist only for CCTweaks' `ItemDebugger`. Three public members for a
  debug item is a poor trade if the debugger is not sacred — dropping it would delete
  `Computer_Accessor`, `ServerComputer_Accessor` and `CobaltMachine_Accessor` for free.
  Kept: they are three one-line accessors over fields that already exist, and the debug
  item is in-tree, so the alternative was keeping the addon's accessors.
- **Item 6** exists only for CCTweaks' `PocketAccess` (its integration with P++). The
  merged mod would own both sides, so the widening may be short-lived. Kept: harmless,
  and removing a public member later is cheaper than leaving the addon with an invoker.

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

## 4. The M4 seams

The fuel and pocket seams landed (§4.1, §4.2); cable connectivity is still deferred. Each seam adds a
call site, so each ships with an "unregistered ⇒ today's behaviour" test.

| Seam | Consulted by | Retires (addon-side) | Status |
|---|---|---|---|
| Cable connectivity (neighbour-ask interface + `ComputerCraftAPI` registration) | `BlockCable.isCable`, `TileCable.getCableBounds`, cable render handler, `ItemCable.onItemUse` | CCTweaks `BlockCable_Mixin`, `CableBlockRenderingHandler_Mixin`, `ItemCable_Mixin` | deferred |
| Pocket upgrade registry (`IPocketUpgrade` + `ComputerCraftAPI.registerPocketUpgrade`) | `ItemPocketComputer` create / update / display name, `PocketAPI` equip / unequip | P++ `ItemPocketComputerMixin` | **landed (§4.2)** |

Design rules when/if they land: no CodeChicken dependency in CC (a marker interface
plus a registration list suffices); `false`-by-default; and a test asserting an
unregistered world connects and renders identically.

### 4.1 Turtle fuel handlers — landed

This seam is a **port of upstream**, not an invention: CC: Tweaked 1.120.2 ships `TurtleRefuelHandler`,
`impl.TurtleRefuelHandlers` and `FurnaceRefuelHandler` today. It landed here because CCTweaks'
`TurtleRefuelCommand_Mixin` is load-bearing functionality, not a bug patch — it is the only thing that makes
a battery refuel a turtle — and the mixin's `@Overwrite` costs four behaviours that CC's command owns:

* `turtleDisabledActions` (`refuel` can be disabled by an admin) — the mixin drops the check;
* the `refuel(0)` probe — the mixin answers "a provider matched" rather than "this would yield fuel", so an
  empty battery reports `true`;
* selected-slot discipline — the mixin's default provider consumes from the whole inventory;
* the tank cap — neither the old code nor the mixin limited *items consumed* to the remaining space, so a
  nearly-full turtle burned a full stack for the fuel of one item.

| Piece | Notes |
|---|---|
| `api/turtle/TurtleRefuelHandler` | `OptionalInt refuel(turtle, stack, slot, limit)`; `empty()` = not my fuel, `of(0)` = mine but the limit was zero. Ported from CC: Tweaked (MPL-2.0, `NOTICE`). |
| `impl/TurtleRefuelHandlers` | First present result wins; throws on a negative yield and on consumption during the zero-limit probe. |
| `ComputerCraftAPI.registerRefuelHandler` | Added to the **reflective** 1.75 API (a `findCCMethod` lookup plus a static `ComputerCraft` target), not as an upstream-style service call. `ComputerCraftAPIRefuelHandlerTest` pins the name/signature pair so it cannot silently stop working. |
| `shared/turtle/core/FurnaceRefuelHandler` | `getBurnTime * 5 / 100`, plus the tank-cap and container-item rules. Registered at `init`. |
| `TurtleRefuelCommand` | Keeps the disabled check and both failure messages; delegates fuel to the registry. |
| `shared/turtle/refuel/*` | **Addition, not parity:** IC2 (`IElectricItem.discharge`) and CoFH (`IEnergyContainerItem.extractEnergy`) handlers, 25 EU and 100 RF per fuel by default, both configurable and both an off switch at 0. Guarded by a class-load shim, so a game without those mods never resolves their API classes. |

Coverage: `TurtleRefuelHandlersTest`, `FurnaceRefuelHandlerTest`, `TurtleRefuelCommandTest`,
`EnergyRefuelTest`, `ElectricRefuelIntegrationTest`, `ComputerCraftAPIRefuelHandlerTest`, and the in-game
batch O (`docs/ingame-tests/batch-o`).

What CCTweaks then deletes: `TurtleRefuelCommand_Mixin`, `TurtleFuelRegistry`, `ITurtleFuelRegistry`,
`ITurtleFuelProvider` and the fuel half of `DefaultTurtleProviders` — re-registering their two integrations
as `TurtleRefuelHandler`s (`canRefuel` → `OptionalInt.empty()`).


### 4.2 Pocket upgrade registry — landed

**What was impossible.** `dan200.computercraft.api` had no `pocket` package and `ComputerCraftAPI` had no
`registerPocketUpgrade`. The upgrade set was a closed six-id enum: `PocketUpgrades` mapped id → `PeripheralType` →
`switch` → peripheral, `ItemPocketComputer.getUpgrade` clamped anything outside 1–6 to `-1`, and the display name was a
six-branch `if` chain. Attaching a peripheral was never the problem — `ServerComputer#setPeripheral(int, IPeripheral)`
is public and `getPeripheral(2)` is what CC itself uses. **Being called was the problem**: the three moments an upgrade
needs code at (peripheral creation inside `createServerComputer`, the per-tick chain in `onUpdate`, and
`getItemStackDisplayName`) all live in `ItemPocketComputer`, and the upgrade was an `int` in ComputerCraft's own NBT.
That is exactly the set of things Peripherals++ `@Inject`s into.

**What shipped.** A port of upstream's API (CC: Tweaked 1.120.2 `api/pocket/IPocketUpgrade`,
`api/pocket/IPocketAccess`, `api/upgrades/UpgradeBase`), adapted to 1.7.10:

| Piece | Notes |
|---|---|
| `api/pocket/IPocketUpgrade` | `getUpgradeID`, `getUnlocalisedAdjective`, `getCraftingItem`, `createPeripheral(IPocketAccess)`; defaults `getUpgradeData(ItemStack)`, `getUpgradeItem(NBTTagCompound)`, `update(IPocketAccess, IPeripheral)`. |
| `api/pocket/IPocketAccess` | `getWorld`, `getEntity`, `getStack`, `getUpgradeNBTData`, `updateUpgradeNBTData`, `invalidatePeripheral`. |
| `ComputerCraftAPI.registerPocketUpgrade` | Reflective 1.75 hook (a `findCCMethod` lookup plus a static `ComputerCraft` target), like `registerRefuelHandler`; `PocketUpgrades.register` refuses reserved ids (< 7) and duplicates the way `CCTurtleProxyCommon#registerTurtleUpgrade` does. |
| `shared/pocket/PocketUpgrades` | The registry. Ids 1–6 are pre-registered with the six built-ins, so saved items, names and peripherals are unchanged; mods add ids ≥ 7, so Peripherals++'s 7 and 8 keep working with no migration. |
| `shared/pocket/upgrades/*` | The six built-ins as upgrades. The `instanceof` chain in `onUpdate` is now their `update(...)` methods — the modem's position and light flag, the speaker's tick, the chat box's dimension, the NPC interface's stack hand-off all moved with it, unchanged. |
| `PocketAccess` | The access object: the stack, the holder, and upgrade NBT stored on the item stack (`upgradeData`) so it survives the computer being recreated. |

Call sites replaced: `createServerComputer` (peripheral), `onUpdate` (tick), `getItemStackDisplayName` (adjective),
`getUpgrade` (registry lookup instead of the 1–6 range check), `PocketAPI#doEquipBack` and `#doUnequipBack`.

**Two behaviour changes, both parity fixes, both recorded in `NOTICE` section 2.** The old `equipBack` scan required
an `IPeripheralItem`, which none of the speaker, chat box or NPC *block* items are, so in practice only the wireless
modem could ever be equipped; and `unequipBack` refused everything but id 1 while `isEquipped` reported 1–6 as
equipped. Both now accept any registered upgrade, which is upstream's behaviour. An id with no registered upgrade still
refuses, so a save from an uninstalled mod keeps its upgrade instead of losing it.

**Deliberate divergences from upstream, all documented in `NOTICE`:** `int` ids (like `ITurtleUpgrade`, and like the
values already in saves) rather than `ResourceLocation`; code-driven registration (1.7.10 has no data-pack registry for
`PocketUpgradeSerialiser`); no colour or light on `IPocketAccess` (a 1.7.10 pocket computer has no colour, and its only
light is the modem's boolean flag); and no `onRightClick` hook — upstream's exists to suppress the pocket GUI, which
this version opens on both sides without a packet of its own, so a half-working version would be worse than none.

**Coverage:** `PocketUpgradeRegistryTest` (built-ins intact, reserved and duplicate ids refused, unknown id inert),
`PocketUpgradeDispatchTest` (a mod upgrade is visible in the stack, gets its peripheral attached on side 2, is ticked
with the holder, and installs/removes through `equipBack`/`unequipBack`; an unregistered id does none of it),
`PocketUpgradesTest` (unchanged built-ins) and `AddonApiSurfaceTest` (the interface members and the registration hook are
pinned reflectively so they cannot be narrowed back). The one thing unit tests cannot reach is the *default*
`getUpgradeData` matching rule: headless, `ItemStack#getItem()` is `null` for every stack because Forge fills
`Item#delegate` from the FML registry, so the stack-identity stand-in is used in the test double and the real rule is
covered in game.

**What Peripherals++ deletes:** `ItemPocketComputerMixin`, `ComputerCraftHooks.onPocketComputerCreate` / `.update` /
`.getPocketComputerName`, and the two `cachedPeripherals` maps keyed by computer id. Their nested-upgrade container
needs nothing from CC: the nested upgrades are its own `createPeripheral`/`update` business, so the one-upgrade-per-pocket
model holds.

---

## 5. Deferred: the addon backlog

Recorded so each addon's work is known, not scheduled. All of it happens in
`CCTweaks-Legacy`, `PeripheralsPlusPlus` and the OpenPeripheral forks, against the tag
produced by §1.

**CCTweaks** — after the release: delete 9 accessors/invokers (M1/M2/M3), rewrite
`PeripheralAPI_Mixin` to use `IPeripheral.attach()` + `getAvailablePeripherals()`
(M5), repair `TileCable_Mixin` (delete the 6 dead overwrites) before anything is
testable, and **delete `TurtleRefuelCommand_Mixin` outright** — §4.1's registry replaces
it, so `TurtleFuelRegistry`, `ITurtleFuelRegistry`, `ITurtleFuelProvider` and the fuel half
of `DefaultTurtleProviders` go with it. Three behaviour mixins remain (`BlockCable`,
`ItemCable`, `CableBlockRenderingHandler`), needing the cable seam and a later CC tag.

**Peripherals++** — delete `CCTurtleProxyCommonAccessor` and `PocketAPIMixin`; delete
`ItemPocketComputerMixin` outright once §4.2 is tagged — re-register their two upgrades
(`PocketMotionDetector`, `PocketPeripheralContainer`) as `IPocketUpgrade`s keeping ids 7 and 8,
and drop `ComputerCraftHooks.onPocketComputerCreate` / `.update` / `.getPocketComputerName`
and the two `cachedPeripherals` maps with them. Their `PlayerInteractEvent` right-click
handler stays: §4.2 deliberately has no `onRightClick` hook.

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

1. ~~Rebase on PR #28 (full wired modem)~~ — done; this branch is cut from it.
2. ~~Decide the two open items~~ — both kept (§1).
3. ~~Land items 1–11~~ — done, with a unit test per member, a reflective contract test,
   the `NOTICE` line and the `TWEAKEDCC_COVERAGE.md` note.
4. ~~Land the turtle fuel seam~~ — done (§4.1), with the IC2/RF handlers behind it.
5. `./gradlew build test` green. A `runServer25` smoke boot plus one `runClient25` pass
   over batches M, N and O is still worth doing before the tag: they are the three
   behaviour surfaces this branch touches (pocket upgrade guard, wired modem, refuelling).
6. Tag. Hand the addons the member list in §1 as their contract.

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
