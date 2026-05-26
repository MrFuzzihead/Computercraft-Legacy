# Adding a New Turtle Upgrade to ComputerCraft-Legacy

This guide covers adding a new turtle upgrade — either a **Peripheral** (adds a Lua-accessible peripheral to the turtle, like the modem or speaker) or a **Tool** (responds to `turtle.dig()`/`turtle.attack()`, like the pickaxe). Use `TurtleModem` and `TurtleSpeaker` as peripheral references, and `TurtleTool` as the tool reference.

---

## Overview

A turtle upgrade is a Java class implementing `ITurtleUpgrade`. When attached to a turtle (via crafting or `turtle.setUpgrade`), the upgrade is:

1. Queried for a peripheral via `createPeripheral` — exposed to Lua as `peripheral.wrap("right")` etc.
2. Called via `useTool` when the Lua program calls `turtle.dig()` or `turtle.attack()`.
3. Ticked every game tick via `update()` — used to sync state to NBT for rendering.

**Package:** `dan200.computercraft.shared.turtle.upgrades`

---

## The Two Upgrade Types

`TurtleUpgradeType` controls how the turtle treats the upgrade:

| Type | `createPeripheral` | `useTool` | Example |
|---|---|---|---|
| `Peripheral` | Returns an `IPeripheral` | Returns `null` | `TurtleModem`, `TurtleSpeaker` |
| `Tool` | Returns `null` | Handles `Dig`/`Attack` | `TurtleTool`, `TurtleAxe` |

A third option exists via `IExtendedTurtleUpgrade` — see [Upgrades That Are Both](#upgrades-that-are-both-tool--peripheral).

---

## Step 1 — Implement `ITurtleUpgrade`

### Peripheral upgrade

```java
package dan200.computercraft.shared.turtle.upgrades;

import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.*;
import dan200.computercraft.ComputerCraft;

public class TurtleMyThing implements ITurtleUpgrade {

    private final int m_id;

    public TurtleMyThing(int id) {
        this.m_id = id;
    }

    @Override
    public int getUpgradeID() {
        return m_id;
    }

    /** Lang key — combined with "Turtle" in the item name, e.g. "My Upgrade Turtle". */
    @Override
    public String getUnlocalisedAdjective() {
        return "upgrade.computercraft:my_thing.adjective";
    }

    @Override
    public TurtleUpgradeType getType() {
        return TurtleUpgradeType.Peripheral;
    }

    /** The ItemStack a player places next to a turtle in crafting to attach this upgrade. */
    @Override
    public ItemStack getCraftingItem() {
        return new ItemStack(ComputerCraft.Blocks.myThing, 1, 0);
    }

    /** The IIcon rendered on the turtle's left or right face. */
    @Override
    public IIcon getIcon(ITurtleAccess turtle, TurtleSide side) {
        return MyThingBlock.getIcon(side);
    }

    /** Return the IPeripheral this upgrade exposes. Never return null for Peripheral-type upgrades. */
    @Override
    public IPeripheral createPeripheral(ITurtleAccess turtle, TurtleSide side) {
        return new Peripheral(turtle);
    }

    /** Tools only — return null for pure peripheral upgrades. */
    @Override
    public TurtleCommandResult useTool(ITurtleAccess turtle, TurtleSide side, TurtleVerb verb, int direction) {
        return null;
    }

    /**
     * Called every server tick. Use this to sync state from the peripheral into
     * upgrade NBT data so the client can render the correct icon.
     * Always guard with !turtle.getWorld().isRemote.
     */
    @Override
    public void update(ITurtleAccess turtle, TurtleSide side) {
        if (!turtle.getWorld().isRemote) {
            IPeripheral peripheral = turtle.getPeripheral(side);
            if (peripheral instanceof Peripheral p) {
                if (p.pollChanged()) {
                    turtle.getUpgradeNBTData(side).setBoolean("active", p.isActive());
                    turtle.updateUpgradeNBTData(side); // marks NBT dirty, syncs to client
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Inner Peripheral class
    // -------------------------------------------------------------------------

    private static class Peripheral extends MyThingPeripheral {

        private final ITurtleAccess m_turtle;

        Peripheral(ITurtleAccess turtle) {
            this.m_turtle = turtle;
        }

        // Override any methods that need the turtle's world position instead of
        // a fixed tile-entity position. For example:
        @Override
        protected Vec3 getPosition() {
            ChunkCoordinates pos = m_turtle.getPosition();
            return Vec3.createVectorHelper(pos.posX, pos.posY, pos.posZ);
        }

        @Override
        public boolean equals(IPeripheral other) {
            if (other instanceof Peripheral o) return o.m_turtle == this.m_turtle;
            return false;
        }
    }
}
```

Reference: `TurtleModem` (peripheral with LED state in NBT), `TurtleSpeaker` (peripheral with per-tick world position update via `update()`).

### Tool upgrade

```java
public class TurtleMyTool implements ITurtleUpgrade {

    private final int m_id;
    private final String m_adjective;
    protected final ItemStack m_item;

    public TurtleMyTool(int id, String adjective, Item item) {
        this.m_id = id;
        this.m_adjective = adjective;
        this.m_item = new ItemStack(item, 1, 0);
    }

    @Override public int getUpgradeID() { return m_id; }
    @Override public String getUnlocalisedAdjective() { return m_adjective; }
    @Override public TurtleUpgradeType getType() { return TurtleUpgradeType.Tool; }
    @Override public ItemStack getCraftingItem() { return m_item.copy(); }
    @Override public IIcon getIcon(ITurtleAccess turtle, TurtleSide side) {
        return m_item.getItem().getIconIndex(m_item);
    }

    /** Tools do not expose a peripheral. */
    @Override public IPeripheral createPeripheral(ITurtleAccess turtle, TurtleSide side) { return null; }

    /** Tools do not need a tick. */
    @Override public void update(ITurtleAccess turtle, TurtleSide side) {}

    @Override
    public TurtleCommandResult useTool(ITurtleAccess turtle, TurtleSide side, TurtleVerb verb, int direction) {
        switch (verb) {
            case Dig:    return dig(turtle, direction);
            case Attack: return attack(turtle, direction);
            default:     return TurtleCommandResult.failure("Unsupported action");
        }
    }

    private TurtleCommandResult dig(ITurtleAccess turtle, int direction) {
        // ... world interaction ...
        return TurtleCommandResult.success();
        // or: return TurtleCommandResult.failure("Nothing to dig here");
    }

    private TurtleCommandResult attack(ITurtleAccess turtle, int direction) {
        // ... entity interaction ...
        return TurtleCommandResult.success();
    }
}
```

Reference: `TurtleTool` (full dig/attack implementation with `TurtlePlayer`, drop collection, and block-protection checks).

---

## `TurtleCommandResult`

Return one of these from `useTool`:

| Factory method | Meaning |
|---|---|
| `TurtleCommandResult.success()` | Action succeeded; Lua receives `true` |
| `TurtleCommandResult.success(Object[] results)` | Success with extra return values |
| `TurtleCommandResult.failure()` | Generic failure; Lua receives `false, nil` |
| `TurtleCommandResult.failure("message")` | Failure with error string; Lua receives `false, "message"` |

---

## `ITurtleAccess` — What You Can Do

Inside `useTool`, `update`, or your inner `Peripheral`, the `ITurtleAccess` gives you:

| Method | Use |
|---|---|
| `getWorld()` / `getPosition()` | Current world and block coords |
| `getInventory()` | The turtle's 16-slot inventory |
| `getSelectedSlot()` / `setSelectedSlot(int)` | Active slot |
| `getFuelLevel()` / `consumeFuel(int)` | Fuel management |
| `playAnimation(TurtleAnimation)` | Trigger a visual animation |
| `getUpgradeNBTData(side)` / `updateUpgradeNBTData(side)` | Per-side persistent NBT (survives reboots, syncs to client) |
| `getUpgrade(side)` / `setUpgrade(side, upgrade)` | Read/change upgrades at runtime |
| `executeCommand(context, command)` | Run a blocking `ITurtleCommand` from a peripheral method |

---

## Blocking Peripheral Commands with `ITurtleCommand`

When a peripheral method needs to execute an action that must block the Lua coroutine until complete (e.g. crafting, placing), implement `ITurtleCommand` and call `turtle.executeCommand(context, command)` from `callMethod`.

```java
// In your inner Peripheral's callMethod:
@Override
public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] args)
    throws LuaException, InterruptedException {
    switch (method) {
        case 0: // myAction()
            return m_turtle.executeCommand(context, turtle -> {
                // This lambda runs on the server thread while the Lua coroutine waits.
                // Return a TurtleCommandResult.
                return TurtleCommandResult.success(new Object[]{ true });
            });
        default:
            return null;
    }
}
```

Reference: `CraftingTablePeripheral.callMethod` calls `turtle.executeCommand(context, new TurtleCraftCommand(limit))`.

---

## Upgrades That Are Both Tool & Peripheral

Implement `IExtendedTurtleUpgrade` instead of `ITurtleUpgrade` when your upgrade needs to both expose a peripheral **and** act as a tool. Return `TurtleUpgradeType.Tool` from `getType()` and return `true` from `alsoPeripheral()`.

```java
public class TurtleMyHybrid implements IExtendedTurtleUpgrade {

    @Override
    public TurtleUpgradeType getType() {
        return TurtleUpgradeType.Tool; // required for useTool to be called
    }

    @Override
    public boolean alsoPeripheral() {
        return true; // createPeripheral will also be called
    }

    /**
     * Called when the upgrade on the *other* side of the turtle changes.
     * Use this to react to the other upgrade being attached or removed.
     *
     * @param turtle     The turtle.
     * @param side       The side *this* upgrade is on.
     * @param oldUpgrade The previous upgrade on the opposite side (may be null).
     * @param newUpgrade The new upgrade on the opposite side (may be null).
     */
    @Override
    public void upgradeChanged(ITurtleAccess turtle, TurtleSide side,
                               ITurtleUpgrade oldUpgrade, ITurtleUpgrade newUpgrade) {
        // React to the other side changing, e.g. enable a feature only when
        // a compatible upgrade is present on the opposite side.
    }

    // ... all other ITurtleUpgrade methods ...
}
```

---

## Step 2 — Register the Upgrade

### 2a. Add a field to `ComputerCraft.Upgrades`

Open [`ComputerCraft.java`](../src/main/java/dan200/computercraft/ComputerCraft.java) and add a field:

```java
public static class Upgrades {
    // ...existing fields...
    public static TurtleMyThing myThing;
}
```

### 2b. Instantiate and register in `CCTurtleProxyCommon`

Open [`CCTurtleProxyCommon.java`](../src/main/java/dan200/computercraft/shared/proxy/CCTurtleProxyCommon.java), find `registerUpgrades()`, and add:

```java
ComputerCraft.Upgrades.myThing = new TurtleMyThing(10); // pick the next free ID
this.registerTurtleUpgradeInternal(ComputerCraft.Upgrades.myThing);
```

**Upgrade ID rules:**
- Valid range: `0–32766`.
- IDs `1–9` are reserved by ComputerCraft (see reference table below).
- For IDs `< 64`, the crafting recipe (turtle + crafting item → upgraded turtle) is **auto-generated**.
- For IDs `≥ 64`, add a manual `GameRegistry.addRecipe(...)` entry.

---

## Step 3 — Add Localization

Add the adjective to `assets/computercraft/lang/en_US.lang`:

```properties
upgrade.computercraft:my_thing.adjective=Gadget
```

This produces the item name **"Gadget Turtle"** in-game.

---

## Reference: Existing Upgrades

| ID | Field | Class | Type | Crafting item |
|---|---|---|---|---|
| 1 | `modem` | `TurtleModem` | Peripheral | Wireless Modem |
| 2 | `craftingTable` | `TurtleCraftingTable` | Peripheral | Crafting Table |
| 3 | `diamondSword` | `TurtleSword` | Tool | Diamond Sword |
| 4 | `diamondShovel` | `TurtleShovel` | Tool | Diamond Shovel |
| 5 | `diamondPickaxe` | `TurtleTool` | Tool | Diamond Pickaxe |
| 6 | `diamondAxe` | `TurtleAxe` | Tool | Diamond Axe |
| 7 | `diamondHoe` | `TurtleHoe` | Tool | Diamond Hoe |
| 8 | `enderModem` | `TurtleEnderModem` | Peripheral | Ender Modem |
| 9 | `speaker` | `TurtleSpeaker` | Peripheral | Speaker |

**Next available ID: 10.**

---

## Checklist for Adding a New Upgrade

1. **Choose a type** — `Peripheral`, `Tool`, or both (`IExtendedTurtleUpgrade`).
2. **Pick an ID** — must be unique; IDs 1–9 are taken. Use the next free ID.
3. **Create the upgrade class** in `dan200.computercraft.shared.turtle.upgrades`.
4. **Implement `createPeripheral`** — return a new inner `Peripheral` instance (Peripheral type) or `null` (Tool type).
5. **Implement `useTool`** — handle `TurtleVerb.Dig` and `TurtleVerb.Attack` (Tool type) or return `null` (Peripheral type).
6. **Implement `update`** — sync any renderable state into upgrade NBT data; always guard with `!turtle.getWorld().isRemote`.
7. **Implement `upgradeChanged`** — only if using `IExtendedTurtleUpgrade`.
8. **Register in `ComputerCraft.Upgrades`** and `CCTurtleProxyCommon.registerUpgrades()`.
9. **Add the lang entry** in `en_US.lang`.
10. **Add tests** — at minimum verify `createPeripheral` returns the correct type and `useTool` returns `success`/`failure` for each verb. Reference: existing peripheral tests in `src/test/`.

---

*Reference implementations: `TurtleModem` (peripheral with NBT state), `TurtleSpeaker` (peripheral with per-tick world position), `TurtleTool` (full dig/attack tool), `CraftingTablePeripheral` + `TurtleCraftingTable` (peripheral using `executeCommand` for blocking Lua calls).*

