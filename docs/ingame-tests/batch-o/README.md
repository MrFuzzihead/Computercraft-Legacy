# Batch O — in-game validation pack

Turtle refuelling: the new fuel-handler registry, and refuelling from charged items (IC2 EU, CoFH RF).

## What changed

`turtle.refuel()` no longer hardcodes forge's furnace burn time. It now asks a list of
`TurtleRefuelHandler`s (`dan200.computercraft.api.turtle.TurtleRefuelHandler`), registered with
`ComputerCraftAPI.registerRefuelHandler`, and the first one to accept the item supplies the fuel. ComputerCraft
registers one handler itself — the furnace-burn-time one — plus IC2 and CoFH energy handlers when those mods are
installed.

| Area | Before | After |
|---|---|---|
| fuel sources | furnace burn time only | furnace fuel, plus anything a registered handler accepts |
| IC2 | not supported by ComputerCraft | a charged `IElectricItem` is **drained**, not consumed; 25 EU per fuel unit by default (`turtleEuRefuelAmount`) |
| CoFH RF | not supported by ComputerCraft | a charged `IEnergyContainerItem` is drained; 100 RF per fuel unit by default (`turtleRfRefuelAmount`) |
| `refuel(0)` | probe, returned success/failure | probe delegated to handlers, which must report recognition but consume nothing |
| nearly-full tank | items were consumed and the excess fuel was clamped away | only the items that fit are consumed |
| `turtleDisabledActions` | respected | still respected — and now also on the electric paths |
| handler bugs | n/a | a negative yield, or consumption during the `refuel(0)` probe, throws immediately |

The registry, the handler interface and the furnace handler follow CC: Tweaked 1.120.2 (`TurtleRefuelHandler`,
`TurtleRefuelHandlers`, `FurnaceRefuelHandler`). The electric handlers are this project's own: upstream has the
registry but no built-in IC2/RF integration. Their conversion ratios match CCTweaks' `ITurtleFuelProvider`
implementations so a pack migrating off CCTweaks keeps the same economy.

## Run

```bat
copy docs\ingame-tests\batch-o\*.lua "run\client\saves\<World>\computer\<id>\"
copy docs\ingame-tests\readlog_manual.lua "run\client\saves\<World>\computer\<id>\"
```

Then run `o1_turtle_fuel` **on a turtle**. It checks what a turtle can see on its own:

| Check | Detail |
|---|---|
| fuel surface | `getFuelLevel` / `getFuelLimit` are numbers (or `unlimited`), the level never exceeds the limit |
| empty-slot guard | `refuel(1)` fails with `No items to combust`, and `refuel(0)` fails too |
| probe and consume | if any slot holds a burnable or charged item, `refuel(0)` recognises it and `refuel(1)` raises fuel without lowering it |

Everything else is staged at the end of the run, because a turtle program cannot put items in its own inventory.

## The staged checks, and why each one exists

1. **Coal** — the ordinary path must be untouched: `refuel(2)` consumes exactly two items and adds `2 × 20` fuel
   (coal burns 80 ticks, 5 fuel per second of burn).
2. **`refuel()` with no argument** — fills up to a stack's worth, capped by the tank.
3. **Tank cap** — nearly-full turtle with a full stack: the tank tops out and the leftover coal stays in the slot. The
   old code consumed first and let `addFuel` clamp, silently destroying the difference.
4. **IC2 battery** — `refuel(0)` true; `refuel(1)` raises fuel and **the battery is still in the slot** with less
   charge. That is the whole point of the electric path: drained, not eaten.
5. **IC2 ratio** — 25 EU per fuel by default, so a 10000 EU battery is 400 fuel. Change `turtleEuRefuelAmount` and the
   yield should scale; set it to 0 and batteries must stop being accepted.
6. **CoFH energy cell** — same as (4)/(5) with `turtleRfRefuelAmount` (100 RF per fuel by default).
7. **Lava bucket** — consumed for fuel *and* an empty bucket appears. A full tank must consume nothing.
8. **Disabled action** — `refuel` in `turtleDisabledActions` must fail with
   `Turtle action "refuel" is disabled` and eat nothing. This is the behaviour the removed CCTweaks mixin lost.
9. **Empty battery probe** — `refuel(0)` still recognises the item, `refuel(1)` gains nothing, consumes nothing and
   does not crash.

## Covered by JVM tests instead

* `TurtleRefuelHandlersTest` (10) — registration order, first-accepted-wins, `OptionalInt.empty()` meaning "not my
  item", `of(0)` meaning "handled, no fuel", the slot/limit/turtle/stack arguments, and both handler-bug guards (a
  negative yield, or consumption under `refuel(0)`).
* `FurnaceRefuelHandlerTest` (10) — non-fuel rejection, the zero-limit probe consuming nothing, `min(limit, available)`
  consumption, selected-slot-only reads, the tank cap (no items taken, inventory not marked dirty), and container items
  returned to the inventory.
* `TurtleRefuelCommandTest` (8) — the disabled-actions check, both failure messages, fuel added, no animation when no
  fuel is gained, and the slot/limit/stack handed to the handler.
* `EnergyRefuelTest` (11) — the electric arithmetic: capping to remaining tank space, never negative, saturating
  instead of overflowing an `int`, whole-unit conversion with the remainder left in the item.
* `ElectricRefuelIntegrationTest` (3) — the class-load shim: `register()` outside a running game is a no-op that
  registers nothing, so a JVM without IC2 or CoFH never resolves their API classes.
* `ComputerCraftAPIRefuelHandlerTest` (3) — the reflective bridge reaches the registry, and the method it reflects into
  still exists with the expected signature. Without this, a rename would silently stop accepting addon handlers.

## What cannot be tested headless

`IC2RefuelHandler` and `RFRefuelHandler` themselves are thin adapters over `ElectricItem.manager.discharge` and
`IEnergyContainerItem.extractEnergy`: the tests cover the arithmetic around them and the class-load shim, and the
adapter call is covered by staged checks 4–6. `ElectricItem.manager` is created during IC2's own init, so a unit test
has nothing to discharge into.
