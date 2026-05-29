# Plan: Trader Role Peripheral

A new block peripheral (`npc_trader`) that links to a specific CustomNPC entity carrying a Trader
role. It exposes the full `IRoleTrader` API to Lua: reading/writing trade slots (item + currency),
stock system management, per-player purchase counts, and currency costs. No Forge events fire for
trades, so the peripheral is **poll-only** (no CC event dispatch, no manager/bridge classes).
Follows the same link-by-UUID pattern established in `NPC_INTERFACE_PERIPHERAL_PLAN.md`.

---

## Architecture Overview

```
BlockTraderRole → TileTraderRole → TraderRolePeripheral
                       ↓
             resolveTrader() — main thread
             UUID lookup → ICustomNpc → getRole() → IRoleTrader
             throws LuaException if role type ≠ 1 (Trader)
```

No manager class. No bridge. No event bus registration. Entirely pull-based.

---

## Implementation Steps

### Step 1 — `TileTraderRole` (new tile entity)

**File:** `dan200.computercraft.compat.customnpcs.peripheral.traderrole.TileTraderRole`

Stores the linked NPC's UUID + display name in NBT. Tracks `IComputerAccess` instances.
Exposes `resolveTrader()` which must be called on the main thread.

```java
package dan200.computercraft.compat.customnpcs.peripheral.traderrole;

import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.api.AbstractNpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.roles.IRoleTrader;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.common.TileGeneric;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.IPeripheralTile;
import dan200.computercraft.ComputerCraft;

public class TileTraderRole extends TileGeneric implements IPeripheralTile {

    // Persisted
    private String m_linkedUUID = null;
    private String m_linkedName = null;

    // Runtime
    private final Set<IComputerAccess> m_computers = new HashSet<>();

    // -------------------------------------------------------------------------
    // Linking
    // -------------------------------------------------------------------------

    public synchronized void setLink(String uuid, String name) {
        m_linkedUUID = uuid;
        m_linkedName = name;
        markDirty();
    }

    public synchronized String getLinkedUUID() { return m_linkedUUID; }
    public synchronized String getLinkedName() { return m_linkedName; }

    /**
     * Resolves the live NPC's Trader role. Must be called on the main thread.
     * Throws LuaException if the NPC is not found, dead, or does not have a Trader role (type 1).
     */
    public IRoleTrader resolveTrader() throws LuaException {
        if (m_linkedUUID == null) throw new LuaException("Not linked to any NPC");
        if (!AbstractNpcAPI.IsAvailable()) throw new LuaException("CustomNPCs is not installed");
        AbstractNpcAPI api = AbstractNpcAPI.Instance();
        if (api == null) throw new LuaException("CustomNPCs API unavailable");
        for (IEntity<?> entity : api.getLoadedEntities()) {
            if (!(entity instanceof ICustomNpc)) continue;
            if (!m_linkedUUID.equals(entity.getUniqueID())) continue;
            ICustomNpc<?> npc = (ICustomNpc<?>) entity;
            if (npc.isDead()) throw new LuaException("NPC is dead");
            var role = npc.getRole();
            if (role == null || role.getType() != 1) {
                throw new LuaException("NPC does not have a Trader role");
            }
            return (IRoleTrader) role;
        }
        throw new LuaException("NPC not found — is it loaded?");
    }

    // -------------------------------------------------------------------------
    // IPeripheralTile
    // -------------------------------------------------------------------------

    @Override
    public PeripheralType getPeripheralType() { return PeripheralType.TraderRole; }

    @Override
    public IPeripheral getPeripheral(int side) { return new TraderRolePeripheral(this); }

    @Override public String getLabel() { return null; }
    @Override public int getDirection() { return 2; }
    @Override public void setDirection(int dir) {}

    // -------------------------------------------------------------------------
    // Computer tracking
    // -------------------------------------------------------------------------

    public synchronized void attachComputer(IComputerAccess computer) {
        m_computers.add(computer);
    }

    public synchronized void detachComputer(IComputerAccess computer) {
        m_computers.remove(computer);
    }

    // -------------------------------------------------------------------------
    // NBT
    // -------------------------------------------------------------------------

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        m_linkedUUID = nbt.hasKey("npcUUID") ? nbt.getString("npcUUID") : null;
        m_linkedName = nbt.hasKey("npcName") ? nbt.getString("npcName") : null;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (m_linkedUUID != null) nbt.setString("npcUUID", m_linkedUUID);
        if (m_linkedName != null) nbt.setString("npcName", m_linkedName);
    }

    // -------------------------------------------------------------------------
    // Block drops
    // -------------------------------------------------------------------------

    @Override
    public void getDroppedItems(java.util.List<ItemStack> drops, int fortune,
                                boolean creative, boolean silkTouch) {
        if (!creative) drops.add(new ItemStack(ComputerCraft.Blocks.traderRole));
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(ComputerCraft.Blocks.traderRole);
    }
}
```

---

### Step 2 — `TraderRolePeripheral` (new peripheral)

**File:** `dan200.computercraft.compat.customnpcs.peripheral.traderrole.TraderRolePeripheral`

All NPC-facing methods (5–37) execute on the main thread via `ILuaContext#executeMainThreadTask`.
Methods 0–4 (link management) work without CustomNPCs installed.

```java
package dan200.computercraft.compat.customnpcs.peripheral.traderrole;

import java.util.HashMap;
import java.util.Map;
import noppes.npcs.api.AbstractNpcAPI;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.item.IItemStack;
import noppes.npcs.api.roles.IRoleTrader;
import dan200.computercraft.api.lua.*;
import dan200.computercraft.api.peripheral.*;

public class TraderRolePeripheral implements IPeripheral {

    private static final String[] METHOD_NAMES = {
        // Link management (0–4) — always available
        "link", "linkNearest", "unlink", "isLinked", "getLinkedName",
        // Trade slots (5–8)
        "getSellOption", "getCurrency", "setSellOption", "removeSellOption",
        // Slot enable/disable (9–11)
        "isSlotEnabled", "enableSlot", "disableSlot",
        // Purchase counts (12–13)
        "getPurchaseNum", "resetPurchaseNum",
        // Market (14–15)
        "getMarket", "setMarket",
        // Stock system — flags (16–19)
        "isStockEnabled", "setStockEnabled", "isPerPlayerStock", "setPerPlayerStock",
        // Stock system — reset schedule (20–23)
        "getStockResetType", "setStockResetType", "getCustomResetTime", "setCustomResetTime",
        // Stock system — per-slot (24–30)
        "getMaxStock", "setMaxStock", "getAvailableStock",
        "getCurrentStock", "setCurrentStock",
        "getPlayerPurchased", "setPlayerPurchased",
        // Stock system — bulk ops (31–34)
        "resetStock", "resetCooldown", "getLastResetTime", "getTimeUntilReset",
        // Currency cost (35–37)
        "getCurrencyCost", "setCurrencyCost", "hasCurrencyCost"
    };

    private final TileTraderRole m_tile;

    public TraderRolePeripheral(TileTraderRole tile) {
        this.m_tile = tile;
    }

    @Override public String getType() { return "npc_trader"; }
    @Override public String[] getMethodNames() { return METHOD_NAMES; }

    @Override
    public void attach(IComputerAccess computer) { m_tile.attachComputer(computer); }

    @Override
    public void detach(IComputerAccess computer) { m_tile.detachComputer(computer); }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof TraderRolePeripheral
            && ((TraderRolePeripheral) other).m_tile == m_tile;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context,
                               int method, Object[] args)
        throws LuaException, InterruptedException {

        switch (method) {
            // ---- Link management ----------------------------------------
            case 0: return methodLink(context, args);
            case 1: return methodLinkNearest(context, args);
            case 2: m_tile.setLink(null, null); return null;
            case 3: return new Object[]{ m_tile.getLinkedUUID() != null };
            case 4: return new Object[]{ m_tile.getLinkedName() };

            // ---- Trade slots --------------------------------------------
            case 5: { // getSellOption(slot)
                int slot = requireSlot(args, 0);
                return trader(context, tr -> {
                    IItemStack item = tr.getSellOption(slot);
                    return new Object[]{ item != null ? itemTable(item) : null };
                });
            }
            case 6: { // getCurrency(slot)
                int slot = requireSlot(args, 0);
                return trader(context, tr -> {
                    IItemStack[] cur = tr.getCurrency(slot);
                    return new Object[]{
                        cur[0] != null ? itemTable(cur[0]) : null,
                        cur[1] != null ? itemTable(cur[1]) : null
                    };
                });
            }
            case 7: { // setSellOption(slot, currency, [currency2,] sold)
                // setSellOption is read-only in v1 — see Open Questions #2
                throw new LuaException("setSellOption is not supported in this version");
            }
            case 8: { // removeSellOption(slot)
                int slot = requireSlot(args, 0);
                return trader(context, tr -> { tr.removeSellOption(slot); return null; });
            }

            // ---- Slot enable/disable ------------------------------------
            case 9: { // isSlotEnabled(slot [, playerName])
                int slot = requireSlot(args, 0);
                String pName = optString(args, 1);
                return trader(context, tr -> {
                    if (pName != null) {
                        IPlayer p = requirePlayer(pName);
                        return new Object[]{ tr.isSlotEnabled(slot, p) };
                    }
                    return new Object[]{ tr.isSlotEnabled(slot) };
                });
            }
            case 10: { // enableSlot(slot [, playerName])
                int slot = requireSlot(args, 0);
                String pName = optString(args, 1);
                return trader(context, tr -> {
                    if (pName != null) { tr.enableSlot(slot, requirePlayer(pName)); }
                    else tr.enableSlot(slot);
                    return null;
                });
            }
            case 11: { // disableSlot(slot [, playerName])
                int slot = requireSlot(args, 0);
                String pName = optString(args, 1);
                return trader(context, tr -> {
                    if (pName != null) { tr.disableSlot(slot, requirePlayer(pName)); }
                    else tr.disableSlot(slot);
                    return null;
                });
            }

            // ---- Purchase counts ----------------------------------------
            case 12: { // getPurchaseNum(slot [, playerName])
                int slot = requireSlot(args, 0);
                String pName = optString(args, 1);
                return trader(context, tr -> {
                    if (pName != null) {
                        return new Object[]{ tr.getPurchaseNum(slot, requirePlayer(pName)) };
                    }
                    return new Object[]{ tr.getPurchaseNum(slot) };
                });
            }
            case 13: { // resetPurchaseNum([slot [, playerName]])
                Integer slot = optInt(args, 0);
                String pName = optString(args, 1);
                return trader(context, tr -> {
                    if (slot == null) { tr.resetPurchaseNum(); }
                    else if (pName != null) { tr.resetPurchaseNum(slot, requirePlayer(pName)); }
                    else { tr.resetPurchaseNum(slot); }
                    return null;
                });
            }

            // ---- Market -------------------------------------------------
            case 14: return trader(context, tr -> new Object[]{ tr.getMarket() });
            case 15: {
                String name = requireString(args, 0, "name");
                return trader(context, tr -> { tr.setMarket(name); return null; });
            }

            // ---- Stock flags --------------------------------------------
            case 16: return trader(context, tr -> new Object[]{ tr.isStockEnabled() });
            case 17: {
                boolean b = requireBoolean(args, 0, "enabled");
                return trader(context, tr -> { tr.setStockEnabled(b); return null; });
            }
            case 18: return trader(context, tr -> new Object[]{ tr.isPerPlayerStock() });
            case 19: {
                boolean b = requireBoolean(args, 0, "perPlayer");
                return trader(context, tr -> { tr.setPerPlayerStock(b); return null; });
            }

            // ---- Stock reset schedule -----------------------------------
            case 20: return trader(context, tr -> new Object[]{ tr.getStockResetType() });
            case 21: {
                int t = requireInt(args, 0, "type");
                return trader(context, tr -> { tr.setStockResetType(t); return null; });
            }
            case 22: return trader(context, tr -> new Object[]{ tr.getCustomResetTime() });
            case 23: {
                long time = requireLong(args, 0, "time");
                return trader(context, tr -> { tr.setCustomResetTime(time); return null; });
            }

            // ---- Per-slot stock -----------------------------------------
            case 24: {
                int slot = requireSlot(args, 0);
                return trader(context, tr -> new Object[]{ tr.getMaxStock(slot) });
            }
            case 25: {
                int slot = requireSlot(args, 0);
                int amount = requireInt(args, 1, "amount");
                return trader(context, tr -> { tr.setMaxStock(slot, amount); return null; });
            }
            case 26: { // getAvailableStock(slot [, playerName])
                int slot = requireSlot(args, 0);
                String pName = optString(args, 1);
                return trader(context, tr -> {
                    if (pName != null) {
                        return new Object[]{ tr.getAvailableStock(slot, requirePlayer(pName)) };
                    }
                    return new Object[]{ tr.getAvailableStock(slot) };
                });
            }
            case 27: {
                int slot = requireSlot(args, 0);
                return trader(context, tr -> new Object[]{ tr.getCurrentStock(slot) });
            }
            case 28: {
                int slot = requireSlot(args, 0);
                int amount = requireInt(args, 1, "amount");
                return trader(context, tr -> { tr.setCurrentStock(slot, amount); return null; });
            }
            case 29: {
                int slot = requireSlot(args, 0);
                String pName = requireString(args, 1, "playerName");
                return trader(context, tr ->
                    new Object[]{ tr.getPlayerPurchased(slot, requirePlayer(pName)) });
            }
            case 30: {
                int slot = requireSlot(args, 0);
                String pName = requireString(args, 1, "playerName");
                int amount = requireInt(args, 2, "amount");
                return trader(context, tr -> {
                    tr.setPlayerPurchased(slot, requirePlayer(pName), amount);
                    return null;
                });
            }

            // ---- Bulk stock ops ----------------------------------------
            case 31: return trader(context, tr -> { tr.resetStock(); return null; });
            case 32: return trader(context, tr -> { tr.resetCooldown(); return null; });
            case 33: return trader(context, tr -> new Object[]{ tr.getLastResetTime() });
            case 34: return trader(context, tr -> new Object[]{ tr.getTimeUntilReset() });

            // ---- Currency cost ------------------------------------------
            case 35: {
                int slot = requireSlot(args, 0);
                return trader(context, tr -> new Object[]{ tr.getCurrencyCost(slot) });
            }
            case 36: {
                int slot = requireSlot(args, 0);
                long cost = requireLong(args, 1, "cost");
                return trader(context, tr -> { tr.setCurrencyCost(slot, cost); return null; });
            }
            case 37: {
                int slot = requireSlot(args, 0);
                return trader(context, tr -> new Object[]{ tr.hasCurrencyCost(slot) });
            }

            default: return null;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @FunctionalInterface
    private interface TraderAction { Object[] run(IRoleTrader trader) throws LuaException; }

    private Object[] trader(ILuaContext ctx, TraderAction action)
        throws LuaException, InterruptedException {
        requireCnpc();
        return ctx.executeMainThreadTask(() -> action.run(m_tile.resolveTrader()));
    }

    private void requireCnpc() throws LuaException {
        if (!AbstractNpcAPI.IsAvailable()) throw new LuaException("CustomNPCs is not installed");
    }

    /** Resolve a player by name. Must be called on the main thread. */
    private static IPlayer requirePlayer(String name) throws LuaException {
        AbstractNpcAPI api = AbstractNpcAPI.Instance();
        if (api != null) {
            for (noppes.npcs.api.entity.IEntity<?> e : api.getLoadedEntities()) {
                if (e instanceof IPlayer && name.equals(e.getName())) {
                    return (IPlayer) e;
                }
            }
        }
        throw new LuaException("Player not found or not online: " + name);
    }

    private static Map<String, Object> itemTable(IItemStack item) {
        Map<String, Object> t = new HashMap<>();
        t.put("name", item.getName());
        t.put("displayName", item.getDisplayName());
        t.put("stackSize", item.getStackSize());
        return t;
    }

    private static int requireSlot(Object[] args, int i) throws LuaException {
        if (args.length <= i || !(args[i] instanceof Number))
            throw new LuaException("Expected number for slot (0–17)");
        int slot = ((Number) args[i]).intValue();
        if (slot < 0 || slot > 17) throw new LuaException("Slot must be 0–17");
        return slot;
    }

    private static String requireString(Object[] args, int i, String name) throws LuaException {
        if (args.length <= i || !(args[i] instanceof String))
            throw new LuaException("Expected string for " + name);
        return (String) args[i];
    }

    private static int requireInt(Object[] args, int i, String name) throws LuaException {
        if (args.length <= i || !(args[i] instanceof Number))
            throw new LuaException("Expected number for " + name);
        return ((Number) args[i]).intValue();
    }

    private static long requireLong(Object[] args, int i, String name) throws LuaException {
        if (args.length <= i || !(args[i] instanceof Number))
            throw new LuaException("Expected number for " + name);
        return ((Number) args[i]).longValue();
    }

    private static boolean requireBoolean(Object[] args, int i, String name) throws LuaException {
        if (args.length <= i || !(args[i] instanceof Boolean))
            throw new LuaException("Expected boolean for " + name);
        return (Boolean) args[i];
    }

    private static String optString(Object[] args, int i) {
        return (args.length > i && args[i] instanceof String) ? (String) args[i] : null;
    }

    private static Integer optInt(Object[] args, int i) {
        return (args.length > i && args[i] instanceof Number)
            ? ((Number) args[i]).intValue() : null;
    }
}
```

---

### Step 3 — `BlockTraderRole` (new block)

**File:** `dan200.computercraft.compat.customnpcs.peripheral.traderrole.BlockTraderRole`

Modeled directly after `BlockChatBox`.

```java
package dan200.computercraft.compat.customnpcs.peripheral.traderrole;

// Mirrors BlockChatBox structure:
// createTile(int metadata) → new TileTraderRole()
// setBlockName("computercraft:npc_trader")
// Hardness 2.0, material iron, creative tab main
// Three texture slots: npcTraderTop, npcTraderSide, npcTraderFront
```

---

### Step 4 — Wire into `ComputerCraft.java` and support files

**`PeripheralType.java`** — add `TraderRole` to the enum.

**`ComputerCraft.java` — `Blocks` inner class:**
```java
public static BlockTraderRole traderRole;
```

**`ComputerCraft.java` — `preInit`:**
```java
Blocks.traderRole = new BlockTraderRole();
GameRegistry.registerBlock(Blocks.traderRole, "npc_trader");
GameRegistry.registerTileEntity(TileTraderRole.class, "computercraft:npc_trader");
// Crafting recipe: chest center, surrounded by gold ingots
GameRegistry.addRecipe(new ItemStack(Blocks.traderRole), new Object[]{
    "GGG", "GCG", "GGG",
    'G', Items.gold_ingot,
    'C', new ItemStack(Blocks.computerNormal)
});
```

*(No bridge registration required — this peripheral is poll-only.)*

---

### Step 5 — Tests

**`TraderRolePeripheralTest.java`**
- `getMethodNames()` returns exactly 38 entries.
- Methods 0–4 operate correctly when CNPC is unavailable.
- Methods 5–37 throw `LuaException("CustomNPCs is not installed")` when CNPC is unavailable.
- `link()` with no match returns `false`; with a match returns `true` and tile UUID is set.
- `unlink()` clears UUID; `isLinked()` returns `false` after.
- `getSellOption(0)` returns `nil` when slot is empty (mock `IRoleTrader`).
- `isSlotEnabled(0)` returns `true` on freshly enabled slot (mock).
- `getPurchaseNum(0)` returns correct integer from mock.
- `getMarket()` returns string from mock.
- `isStockEnabled()` reflects mock state.
- `getCurrencyCost(0)` returns `long` value from mock.

**`TileTraderRoleTest.java`**
- `resolveTrader()` throws when not linked.
- `resolveTrader()` throws when NPC role type is not 1.
- `resolveTrader()` throws when NPC is dead.
- `resolveTrader()` returns `IRoleTrader` when NPC is alive and role type is 1.
- NBT round-trip: write UUID + name, read back, assert equal.

---

## Lua Method Table (38 methods)

| #  | Method               | Args                                 | Returns                | Notes                       |
|----|----------------------|--------------------------------------|------------------------|-----------------------------|
| 0  | `link`               | `npcName: string [, radius: number]` | `boolean`              | Scan nearby for NPC by name |
| 1  | `linkNearest`        | `[radius: number]`                   | `boolean`              | Link to closest NPC         |
| 2  | `unlink`             | —                                    | —                      | Clear stored UUID           |
| 3  | `isLinked`           | —                                    | `boolean`              |                             |
| 4  | `getLinkedName`      | —                                    | `string\|nil`          | Cached name                 |
| 5  | `getSellOption`      | `slot: number`                       | `ItemTable\|nil`       |                             |
| 6  | `getCurrency`        | `slot: number`                       | `ItemTable, ItemTable` | Two-currency slots          |
| 7  | `setSellOption`      | *(see Open Questions #2)*            | —                      | **Not supported in v1**     |
| 8  | `removeSellOption`   | `slot: number`                       | —                      |                             |
| 9  | `isSlotEnabled`      | `slot [, playerName]`                | `boolean`              |                             |
| 10 | `enableSlot`         | `slot [, playerName]`                | —                      |                             |
| 11 | `disableSlot`        | `slot [, playerName]`                | —                      |                             |
| 12 | `getPurchaseNum`     | `slot [, playerName]`                | `number`               |                             |
| 13 | `resetPurchaseNum`   | `[slot [, playerName]]`              | —                      | All overloads               |
| 14 | `getMarket`          | —                                    | `string`               |                             |
| 15 | `setMarket`          | `name: string`                       | —                      |                             |
| 16 | `isStockEnabled`     | —                                    | `boolean`              |                             |
| 17 | `setStockEnabled`    | `enabled: boolean`                   | —                      |                             |
| 18 | `isPerPlayerStock`   | —                                    | `boolean`              |                             |
| 19 | `setPerPlayerStock`  | `perPlayer: boolean`                 | —                      |                             |
| 20 | `getStockResetType`  | —                                    | `number`               | 0–6 (see IRoleTrader)       |
| 21 | `setStockResetType`  | `type: number`                       | —                      |                             |
| 22 | `getCustomResetTime` | —                                    | `number`               | Ticks or ms                 |
| 23 | `setCustomResetTime` | `time: number`                       | —                      |                             |
| 24 | `getMaxStock`        | `slot: number`                       | `number`               | -1 = unlimited              |
| 25 | `setMaxStock`        | `slot, amount: number`               | —                      |                             |
| 26 | `getAvailableStock`  | `slot [, playerName]`                | `number`               |                             |
| 27 | `getCurrentStock`    | `slot: number`                       | `number`               | -1 if not initialized       |
| 28 | `setCurrentStock`    | `slot, amount: number`               | —                      | Global mode only            |
| 29 | `getPlayerPurchased` | `slot, playerName: string`           | `number`               | Per-player mode             |
| 30 | `setPlayerPurchased` | `slot, playerName, amount`           | —                      | Per-player mode             |
| 31 | `resetStock`         | —                                    | —                      | All slots to max            |
| 32 | `resetCooldown`      | —                                    | —                      | Trigger reset on next check |
| 33 | `getLastResetTime`   | —                                    | `number`               | Ticks or ms                 |
| 34 | `getTimeUntilReset`  | —                                    | `number`               | -1 if no reset scheduled    |
| 35 | `getCurrencyCost`    | `slot: number`                       | `number`               |                             |
| 36 | `setCurrencyCost`    | `slot, cost: number`                 | —                      |                             |
| 37 | `hasCurrencyCost`    | `slot: number`                       | `boolean`              |                             |

### `ItemTable` schema

```lua
{
  name        = string,   -- IItemStack#getName()
  displayName = string,   -- IItemStack#getDisplayName()
  stackSize   = number,   -- IItemStack#getStackSize()
}
```

---

## Files Created / Changed

| File                                                                        | Type                                |
|-----------------------------------------------------------------------------|-------------------------------------|
| `compat/customnpcs/peripheral/traderrole/BlockTraderRole.java`              | New                                 |
| `compat/customnpcs/peripheral/traderrole/TileTraderRole.java`               | New                                 |
| `compat/customnpcs/peripheral/traderrole/TraderRolePeripheral.java`         | New                                 |
| `shared/peripheral/PeripheralType.java`                                     | Add `TraderRole`                    |
| `ComputerCraft.java`                                                        | Register block, tile entity, recipe |
| `assets/computercraft/textures/npcTrader*.png`                              | New (3 placeholder textures)        |
| `test/.../TraderRolePeripheralTest.java`                                    | New                                 |
| `test/.../TileTraderRoleTest.java`                                          | New                                 |

## Files NOT Changed

| File                          | Reason                          |
|-------------------------------|---------------------------------|
| `NpcInterfacePeripheral.java` | Separate peripheral; no overlap |
| `ChatBoxPeripheral.java`      | Unrelated                       |
| `NpcInterfaceManager.java`    | No event dispatch needed        |

---

## Open Questions / Decisions Before Implementation

1. **Player resolution**: Confirm that `AbstractNpcAPI#getLoadedEntities()` includes `IPlayer`
   instances for online players, or identify the correct API method (e.g.
   `AbstractNpcAPI#getOnlinePlayer(String)`). If a dedicated method exists, prefer it over the
   linear scan to avoid O(n) cost on every player-scoped call.

2. **`setSellOption` item construction**: Lua cannot construct `IItemStack` objects directly.
   Two options for v2:
   - Accept an item name string + stack size and use `AbstractNpcAPI#createItemStack(String, int)`
     if that method exists.
   - Accept a raw NBT string and parse via Minecraft's `NBTTagCompound` → `ItemStack` path.
   Deferred to v2; method 7 throws a clear `LuaException` in v1.

3. **No trade events**: CustomNPCs fires no Forge event when a player purchases from a trader.
   Reactive automation (e.g., auto-restock when a slot sells out) requires polling
   `getAvailableStock` in a Lua loop. Document this limitation prominently in the ROM help text.

4. **Stock reset type values**: Validate the integer constants `0–6` against the actual
   `CustomNPCs` source before implementation, as the enum backing these may differ between
   CustomNPCs versions bundled with this mod.

5. **Turtle / Pocket upgrade variant**: Deferring a `TurtleTraderRole` / portable upgrade
   to a follow-up plan, consistent with the approach used for `NPC_INTERFACE_PERIPHERAL_PLAN.md`.

