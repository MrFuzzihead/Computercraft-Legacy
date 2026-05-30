# Plan: NPC Interface Peripheral

A new block (`npc_interface`) that links to a specific CustomNPC entity in-world, exposing
state-read, control, and event-forwarding Lua methods. Fully gated: if CustomNPCs is absent,
the block registers but its peripheral exposes zero NPC methods and throws `LuaException`
on any NPC call. Linking/management methods (0–4) always work.

> **Note on block registration gating:** Unlike the plan's original description, the block
> itself should also only be registered when `Loader.isModLoaded("customnpcs")` is true
> (matching the pattern used for `npc_detector`). The "block registers but NPC methods throw"
> approach is now considered incorrect — compat blocks must not pollute the registry when the
> compat mod is absent.

---

## Architecture Overview

```
BlockNpcInterface → TileNpcInterface ←→ NpcInterfaceManager (UUID registry)
                         ↑                        ↑
               NpcInterfacePeripheral     NpcInterfaceBridge (Forge @SubscribeEvent)
                  (IPeripheral)            dispatches events → per-UUID tile set
```

The tile stores the **linked NPC's UUID** in NBT. On every Lua method call the peripheral
re-fetches the live entity by UUID from the world. Events are dispatched only to tiles whose
linked UUID matches the NPC that fired the event.

---

## Lua Method Table

Methods 0–4 work **without** CustomNPCs installed (they manage the stored UUID string only).
Methods 5–24 require CNPC installed AND the peripheral to be linked; they run on the main
thread via `ILuaContext#executeMainThreadTask`.

| #  | Method           | Args                                 | Returns       | Notes                                                                           |
|----|------------------|--------------------------------------|---------------|---------------------------------------------------------------------------------|
| 0  | `link`           | `npcName: string [, radius: number]` | `boolean`     | Scan nearby for NPC by name, store UUID                                         |
| 1  | `linkNearest`    | `[radius: number]`                   | `boolean`     | Link to closest NPC within radius                                               |
| 2  | `unlink`         | —                                    | —             | Clear stored UUID                                                               |
| 3  | `isLinked`       | —                                    | `boolean`     | True if UUID is stored                                                          |
| 4  | `getLinkedName`  | —                                    | `string\|nil` | Cached name of linked NPC                                                       |
| 5  | `getName`        | —                                    | `string`      | `ICustomNpc#getName()`                                                          |
| 6  | `getTitle`       | —                                    | `string`      | `ICustomNpc#getTitle()`                                                         |
| 7  | `getUUID`        | —                                    | `string`      | `IEntity#getUniqueID()`                                                         |
| 8  | `isAlive`        | —                                    | `boolean`     | `!IEntity#isDead()`                                                             |
| 9  | `getHealth`      | —                                    | `number`      | `IEntityLivingBase#getHealth()`                                                 |
| 10 | `getMaxHealth`   | —                                    | `number`      | `IEntityLivingBase#getMaxHealth()`                                              |
| 11 | `getPosition`    | —                                    | `{x, y, z}`   | `IEntity#getX/Y/Z()`                                                            |
| 12 | `getMovingType`  | —                                    | `number`      | 0=standing, 1=wandering, 2=path                                                 |
| 13 | `isAttacking`    | —                                    | `boolean`     | `IEntityLivingBase#isAttacking()`                                               |
| 14 | `getTarget`      | —                                    | `string\|nil` | `IEntityLivingBase#getAttackTarget()#getName()`                                 |
| 15 | `getFaction`     | —                                    | `string`      | `ICustomNpc#getFaction()#getName()`                                             |
| 16 | `getJob`         | —                                    | `number`      | `ICustomNpc#getJob()#getType()`                                                 |
| 17 | `getRole`        | —                                    | `number`      | `ICustomNpc#getRole()#getType()`                                                |
| 18 | `say`            | `message: string`                    | —             | `ICustomNpc#say(String)`                                                        |
| 19 | `setHome`        | `x, y, z: number`                    | —             | `ICustomNpc#setHome(int,int,int)`                                               |
| 20 | `setMovingType`  | `type: string`                       | —             | `ICustomNpc#setMovingType(int)` — accepts `"standing"`, `"wandering"`, `"path"` |
| 21 | `navigateTo`     | `x, y, z [, speed]: number`          | —             | `IEntityLiving#navigateTo(x,y,z,speed)`                                         |
| 22 | `executeCommand` | `command: string`                    | —             | `ICustomNpc#executeCommand(String)`                                             |
| 23 | `kill`           | —                                    | —             | `ICustomNpc#kill()`                                                             |
| 24 | `reset`          | —                                    | —             | `ICustomNpc#reset()`                                                            |
| 25 | `setName`        | `name: string`                       | —             | `ICustomNpc#setName(String)`                                                    |
| 26 | `setTitle`       | `title: string`                      | —             | `ICustomNpc#setTitle(String)`                                                   |
| 27 | `setHealth`      | `health: number`                     | —             | `IEntityLivingBase#setHealth(float)`                                            |
| 28 | `setMaxHealth`   | `maxHealth: number`                  | —             | `ICustomNpc#setMaxHealth(double)`                                               |
| 29 | `setFaction`     | `factionId: number`                  | —             | `ICustomNpc#setFaction(int)` — use faction numeric ID                           |
| 30 | `setJob`         | `jobType: number`                    | —             | `ICustomNpc#setJob(int)`                                                        |
| 31 | `setRole`        | `roleType: number`                   | —             | `ICustomNpc#setRole(int)`                                                       |

## CC Events Queued to Attached Computers

| Event               | Parameters                                               |
|---------------------|----------------------------------------------------------|
| `npc_interact`      | `playerName: string`                                     |
| `npc_dialog`        | `playerName: string, dialogId: number, optionId: number` |
| `npc_dialog_closed` | `playerName: string, dialogId: number, optionId: number` |
| `npc_damaged`       | `sourceName: string, damage: number, damageType: string` |
| `npc_died`          | `killerName: string, damageType: string`                 |
| `npc_target`        | `targetName: string`                                     |
| `npc_target_lost`   | *(no params)*                                            |
| `npc_tick`          | *(no params, fired every 20 ticks)*                      |

---

## Implementation Steps

### Step 1 — `NpcInterfaceManager` (new class)

**File:** `dan200.computercraft.compat.customnpcs.peripheral.npcinterface.NpcInterfaceManager`

Mirrors `ChatBoxManager` but keyed by NPC UUID. Each dispatch method does a synchronized
snapshot of the UUID's tile set, then calls `tile.queueNpcEvent(...)` on each.

```java
package dan200.computercraft.compat.customnpcs.peripheral.npcinterface;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcInterfaceManager {

    // UUID string → set of tiles linked to that NPC
    private static final Map<String, Set<TileNpcInterface>> BY_UUID = new ConcurrentHashMap<>();

    private NpcInterfaceManager() {}

    public static void register(String npcUUID, TileNpcInterface tile) {
        BY_UUID.computeIfAbsent(npcUUID, k -> Collections.synchronizedSet(new HashSet<>())).add(tile);
    }

    public static void unregister(String npcUUID, TileNpcInterface tile) {
        Set<TileNpcInterface> set = BY_UUID.get(npcUUID);
        if (set != null) {
            set.remove(tile);
            if (set.isEmpty()) BY_UUID.remove(npcUUID);
        }
    }

    private static void dispatch(String npcUUID, String event, Object... params) {
        Set<TileNpcInterface> set = BY_UUID.get(npcUUID);
        if (set == null) return;
        Set<TileNpcInterface> snapshot;
        synchronized (set) { snapshot = new HashSet<>(set); }
        for (TileNpcInterface tile : snapshot) tile.queueNpcEvent(event, params);
    }

    public static void dispatchInteract(String uuid, String playerName) {
        dispatch(uuid, "npc_interact", playerName);
    }
    public static void dispatchDialog(String uuid, String playerName, int dialogId, int optionId) {
        dispatch(uuid, "npc_dialog", playerName, dialogId, optionId);
    }
    public static void dispatchDialogClosed(String uuid, String playerName, int dialogId, int optionId) {
        dispatch(uuid, "npc_dialog_closed", playerName, dialogId, optionId);
    }
    public static void dispatchDamaged(String uuid, String sourceName, float damage, String damageType) {
        dispatch(uuid, "npc_damaged", sourceName, damage, damageType);
    }
    public static void dispatchDied(String uuid, String killerName, String damageType) {
        dispatch(uuid, "npc_died", killerName, damageType);
    }
    public static void dispatchTarget(String uuid, String targetName) {
        dispatch(uuid, "npc_target", targetName);
    }
    public static void dispatchTargetLost(String uuid) {
        dispatch(uuid, "npc_target_lost");
    }
    public static void dispatchTick(String uuid) {
        dispatch(uuid, "npc_tick");
    }
}
```

---

### Step 2 — `NpcInterfaceBridge` (new class)

**File:** `dan200.computercraft.compat.customnpcs.peripheral.npcinterface.NpcInterfaceBridge`

Registered onto `AbstractNpcAPI.Instance().events()` at init time (same site as
`CustomNpcChatBoxBridge` from the ChatBox plan).

```java
package dan200.computercraft.compat.customnpcs.peripheral.npcinterface;

import java.util.concurrent.ConcurrentHashMap;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
// NOTE: Use the concrete NpcEvent classes, NOT INpcEvent interfaces.
// FML's EventBus dispatches by concrete class hierarchy and does not walk interfaces.
// Concrete classes: noppes.npcs.scripted.event.NpcEvent.*
import noppes.npcs.scripted.event.NpcEvent;

public class NpcInterfaceBridge {

    // Per-NPC tick counter for 20-tick throttle
    private final ConcurrentHashMap<String, Integer> m_tickCounters = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onInteract(NpcEvent.InteractEvent e) {
        NpcInterfaceManager.dispatchInteract(
            e.getNpc().getUniqueID(), e.getPlayer().getName());
    }

    @SubscribeEvent
    public void onDialog(NpcEvent.DialogEvent e) {
        NpcInterfaceManager.dispatchDialog(
            e.getNpc().getUniqueID(), e.getPlayer().getName(),
            e.getDialogId(), e.getOptionId());
    }

    @SubscribeEvent
    public void onDialogClosed(NpcEvent.DialogClosedEvent e) {
        NpcInterfaceManager.dispatchDialogClosed(
            e.getNpc().getUniqueID(), e.getPlayer().getName(),
            e.getDialogId(), e.getOptionId());
    }

    @SubscribeEvent
    public void onDamaged(NpcEvent.DamagedEvent e) {
        // IEntity has getTypeName(), not getName()
        String src = e.getSource() != null ? e.getSource().getTypeName() : "";
        NpcInterfaceManager.dispatchDamaged(
            e.getNpc().getUniqueID(), src, e.getDamage(), e.getType());
    }

    @SubscribeEvent
    public void onDied(NpcEvent.DiedEvent e) {
        // IEntity has getTypeName(), not getName()
        String killer = e.getSource() != null ? e.getSource().getTypeName() : "";
        NpcInterfaceManager.dispatchDied(
            e.getNpc().getUniqueID(), killer, e.getType());
    }

    @SubscribeEvent
    public void onTarget(NpcEvent.TargetEvent e) {
        String name = e.getTarget() != null ? e.getTarget().getTypeName() : "";
        NpcInterfaceManager.dispatchTarget(e.getNpc().getUniqueID(), name);
    }

    @SubscribeEvent
    public void onTargetLost(NpcEvent.TargetLostEvent e) {
        NpcInterfaceManager.dispatchTargetLost(e.getNpc().getUniqueID());
    }

    @SubscribeEvent
    public void onTick(NpcEvent.UpdateEvent e) {
        String uuid = e.getNpc().getUniqueID();
        int count = m_tickCounters.merge(uuid, 1, Integer::sum);
        if (count % 20 == 0) {
            NpcInterfaceManager.dispatchTick(uuid);
        }
    }
}
```

---

### Step 3 — `TileNpcInterface` (new tile entity)

**File:** `dan200.computercraft.compat.customnpcs.peripheral.npcinterface.TileNpcInterface`

```java
package dan200.computercraft.compat.customnpcs.peripheral.npcinterface;

import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.api.AbstractNpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.common.TileGeneric;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.IPeripheralTile;
import dan200.computercraft.ComputerCraft;

public class TileNpcInterface extends TileGeneric implements IPeripheralTile {

    // Persisted in NBT
    private String m_linkedUUID = null;
    private String m_linkedName = null;

    // Runtime: computers attached to this peripheral
    private final Set<IComputerAccess> m_computers = new HashSet<>();

    // -------------------------------------------------------------------------
    // Linking
    // -------------------------------------------------------------------------

    public synchronized void setLink(String uuid, String name) {
        if (m_linkedUUID != null) {
            NpcInterfaceManager.unregister(m_linkedUUID, this);
        }
        m_linkedUUID = uuid;
        m_linkedName = name;
        if (uuid != null) {
            NpcInterfaceManager.register(uuid, this);
        }
        markDirty();
    }

    public synchronized String getLinkedUUID() { return m_linkedUUID; }
    public synchronized String getLinkedName() { return m_linkedName; }

    /** Resolves the live NPC entity. Must be called on the main thread. */
    public ICustomNpc<?> resolveNpc() {
        if (m_linkedUUID == null || !AbstractNpcAPI.IsAvailable()) return null;
        AbstractNpcAPI api = AbstractNpcAPI.Instance();
        if (api == null) return null;
        for (IEntity<?> entity : api.getLoadedEntities()) {
            if (entity instanceof ICustomNpc && m_linkedUUID.equals(entity.getUniqueID())) {
                return (ICustomNpc<?>) entity;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Event dispatch (called by NpcInterfaceManager)
    // -------------------------------------------------------------------------

    public void queueNpcEvent(String event, Object... params) {
        Set<IComputerAccess> snapshot;
        synchronized (this) { snapshot = new HashSet<>(m_computers); }
        for (IComputerAccess computer : snapshot) {
            computer.queueEvent(event, params);
        }
    }

    // -------------------------------------------------------------------------
    // IPeripheralTile
    // -------------------------------------------------------------------------

    @Override
    public PeripheralType getPeripheralType() { return PeripheralType.NpcInterface; }

    @Override
    public IPeripheral getPeripheral(int side) { return new NpcInterfacePeripheral(this); }

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
    // TileEntity lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void validate() {
        super.validate();
        if (m_linkedUUID != null) {
            NpcInterfaceManager.register(m_linkedUUID, this);
        }
    }

    @Override
    public void invalidate() {
        if (m_linkedUUID != null) {
            NpcInterfaceManager.unregister(m_linkedUUID, this);
        }
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        if (m_linkedUUID != null) {
            NpcInterfaceManager.unregister(m_linkedUUID, this);
        }
        super.onChunkUnload();
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
        if (!creative) drops.add(new ItemStack(ComputerCraft.Blocks.npcInterface));
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(ComputerCraft.Blocks.npcInterface);
    }
}
```

---

### Step 4 — `NpcInterfacePeripheral` (new peripheral)

**File:** `dan200.computercraft.compat.customnpcs.peripheral.npcinterface.NpcInterfacePeripheral`

```java
package dan200.computercraft.compat.customnpcs.peripheral.npcinterface;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import noppes.npcs.api.AbstractNpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import dan200.computercraft.api.lua.*;
import dan200.computercraft.api.peripheral.*;

public class NpcInterfacePeripheral implements IPeripheral {

    private static final String[] METHOD_NAMES = {
        "link", "linkNearest", "unlink", "isLinked", "getLinkedName",  // 0–4
        "getName", "getTitle", "getUUID", "isAlive",                   // 5–8
        "getHealth", "getMaxHealth", "getPosition",                     // 9–11
        "getMovingType", "isAttacking", "getTarget",                    // 12–14
        "getFaction", "getJob", "getRole",                              // 15–17
        "say", "setHome", "setMovingType", "navigateTo",               // 18–21
        "executeCommand", "kill", "reset"                               // 22–24
    };

    private final TileNpcInterface m_tile;

    public NpcInterfacePeripheral(TileNpcInterface tile) {
        this.m_tile = tile;
    }

    @Override public String getType() { return "npc_interface"; }
    @Override public String[] getMethodNames() { return METHOD_NAMES; }

    @Override
    public void attach(IComputerAccess computer) { m_tile.attachComputer(computer); }

    @Override
    public void detach(IComputerAccess computer) { m_tile.detachComputer(computer); }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof NpcInterfacePeripheral
            && ((NpcInterfacePeripheral) other).m_tile == m_tile;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context,
                               int method, Object[] args)
        throws LuaException, InterruptedException {

        switch (method) {
            case 0: return methodLink(context, args);
            case 1: return methodLinkNearest(context, args);
            case 2: m_tile.setLink(null, null); return null;
            case 3: return new Object[]{ m_tile.getLinkedUUID() != null };
            case 4: return new Object[]{ m_tile.getLinkedName() };
            default:
                requireCnpc();
                switch (method) {
                    case 5:  return mainThread(context, npc -> new Object[]{ npc.getName() });
                    case 6:  return mainThread(context, npc -> new Object[]{ npc.getTitle() });
                    case 7:  return mainThread(context, npc -> new Object[]{ npc.getUniqueID() });
                    case 8:  return mainThread(context, npc -> new Object[]{ !npc.isDead() });
                    case 9:  return mainThread(context, npc -> new Object[]{ npc.getHealth() });
                    case 10: return mainThread(context, npc -> new Object[]{ npc.getMaxHealth() });
                    case 11: return mainThread(context, npc -> {
                        java.util.Map<String, Double> pos = new java.util.HashMap<>();
                        pos.put("x", npc.getX()); pos.put("y", npc.getY()); pos.put("z", npc.getZ());
                        return new Object[]{ pos };
                    });
                    case 12: return mainThread(context, npc -> new Object[]{ npc.getMovingType() });
                    case 13: return mainThread(context, npc -> new Object[]{ npc.isAttacking() });
                    case 14: return mainThread(context, npc -> {
                        var t = npc.getAttackTarget();
                        return new Object[]{ t != null ? t.getName() : null };
                    });
                    case 15: return mainThread(context, npc -> {
                        var f = npc.getFaction();
                        return new Object[]{ f != null ? f.getName() : "" };
                    });
                    case 16: return mainThread(context, npc -> {
                        var j = npc.getJob();
                        return new Object[]{ j != null ? j.getType() : -1 };
                    });
                    case 17: return mainThread(context, npc -> {
                        var r = npc.getRole();
                        return new Object[]{ r != null ? r.getType() : -1 };
                    });
                    case 18: { // say(message)
                        String msg = requireString(args, 0, "message");
                        return mainThread(context, npc -> { npc.say(msg); return null; });
                    }
                    case 19: { // setHome(x, y, z)
                        int x = requireInt(args, 0, "x");
                        int y = requireInt(args, 1, "y");
                        int z = requireInt(args, 2, "z");
                        return mainThread(context, npc -> { npc.setHome(x, y, z); return null; });
                    }
                    case 20: { // setMovingType(type)
                        int t = requireInt(args, 0, "type");
                        return mainThread(context, npc -> { npc.setMovingType(t); return null; });
                    }
                    case 21: { // navigateTo(x, y, z [, speed])
                        double x = requireDouble(args, 0, "x");
                        double y = requireDouble(args, 1, "y");
                        double z = requireDouble(args, 2, "z");
                        double speed = (args.length > 3 && args[3] instanceof Number)
                            ? ((Number) args[3]).doubleValue() : 0.7;
                        return mainThread(context, npc -> {
                            npc.navigateTo(x, y, z, speed); return null;
                        });
                    }
                    case 22: { // executeCommand(cmd)
                        String cmd = requireString(args, 0, "command");
                        return mainThread(context, npc -> { npc.executeCommand(cmd); return null; });
                    }
                    case 23: return mainThread(context, npc -> { npc.kill(); return null; });
                    case 24: return mainThread(context, npc -> { npc.reset(); return null; });
                    default: return null;
                }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @FunctionalInterface
    private interface NpcAction { Object[] run(ICustomNpc<?> npc) throws LuaException; }

    private Object[] mainThread(ILuaContext ctx, NpcAction action)
        throws LuaException, InterruptedException {
        return ctx.executeMainThreadTask(() -> {
            ICustomNpc<?> npc = m_tile.resolveNpc();
            if (npc == null) throw new LuaException("NPC not found — is it loaded?");
            return action.run(npc);
        });
    }

    private Object[] methodLink(ILuaContext context, Object[] args)
        throws LuaException, InterruptedException {
        requireCnpc();
        String name = requireString(args, 0, "npcName");
        double radius = (args.length > 1 && args[1] instanceof Number)
            ? ((Number) args[1]).doubleValue() : 16.0;
        final double rSq = radius * radius;
        return context.executeMainThreadTask(() -> {
            AbstractNpcAPI api = AbstractNpcAPI.Instance();
            if (api == null) return new Object[]{ false };
            for (IEntity<?> entity : api.getLoadedEntities()) {
                if (!(entity instanceof ICustomNpc)) continue;
                ICustomNpc<?> npc = (ICustomNpc<?>) entity;
                if (!name.equals(npc.getName())) continue;
                double dx = npc.getX() - (m_tile.xCoord + 0.5);
                double dy = npc.getY() - (m_tile.yCoord + 0.5);
                double dz = npc.getZ() - (m_tile.zCoord + 0.5);
                if (dx*dx + dy*dy + dz*dz <= rSq) {
                    m_tile.setLink(npc.getUniqueID(), npc.getName());
                    return new Object[]{ true };
                }
            }
            return new Object[]{ false };
        });
    }

    private Object[] methodLinkNearest(ILuaContext context, Object[] args)
        throws LuaException, InterruptedException {
        requireCnpc();
        double radius = (args.length > 0 && args[0] instanceof Number)
            ? ((Number) args[0]).doubleValue() : 16.0;
        final double rSq = radius * radius;
        return context.executeMainThreadTask(() -> {
            AbstractNpcAPI api = AbstractNpcAPI.Instance();
            if (api == null) return new Object[]{ false };
            ICustomNpc<?> best = null;
            double bestDist = Double.MAX_VALUE;
            for (IEntity<?> entity : api.getLoadedEntities()) {
                if (!(entity instanceof ICustomNpc)) continue;
                ICustomNpc<?> npc = (ICustomNpc<?>) entity;
                double dx = npc.getX() - (m_tile.xCoord + 0.5);
                double dy = npc.getY() - (m_tile.yCoord + 0.5);
                double dz = npc.getZ() - (m_tile.zCoord + 0.5);
                double dist = dx*dx + dy*dy + dz*dz;
                if (dist <= rSq && dist < bestDist) { best = npc; bestDist = dist; }
            }
            if (best == null) return new Object[]{ false };
            m_tile.setLink(best.getUniqueID(), best.getName());
            return new Object[]{ true };
        });
    }

    private void requireCnpc() throws LuaException {
        if (!AbstractNpcAPI.IsAvailable()) {
            throw new LuaException("CustomNPCs is not installed");
        }
    }

    private static String requireString(Object[] args, int i, String name) throws LuaException {
        if (args.length <= i || !(args[i] instanceof String)) {
            throw new LuaException("Expected string for " + name);
        }
        return (String) args[i];
    }

    private static int requireInt(Object[] args, int i, String name) throws LuaException {
        if (args.length <= i || !(args[i] instanceof Number)) {
            throw new LuaException("Expected number for " + name);
        }
        return ((Number) args[i]).intValue();
    }

    private static double requireDouble(Object[] args, int i, String name) throws LuaException {
        if (args.length <= i || !(args[i] instanceof Number)) {
            throw new LuaException("Expected number for " + name);
        }
        return ((Number) args[i]).doubleValue();
    }
}
```

---

### Step 5 — `BlockNpcInterface` (new block)

**File:** `dan200.computercraft.compat.customnpcs.peripheral.npcinterface.BlockNpcInterface`

Modeled directly after `BlockChatBox`. Creates a `TileNpcInterface`.
Three texture slots: `npcInterfaceTop`, `npcInterfaceSide`, `npcInterfaceFront`.
Hardness 2.0, material iron, creative tab main.

```java
package dan200.computercraft.compat.customnpcs.peripheral.npcinterface;

// ... (mirrors BlockChatBox structure)
// createTile(int metadata) → new TileNpcInterface()
// setBlockName("computercraft:npc_interface")
```

---

### Step 6 — Wire into `ComputerCraft.java` and support files

**`PeripheralType.java`** — add `NpcInterface` to the enum.

**`ComputerCraft.java` — `Blocks` inner class:**
```java
public static BlockNpcInterface npcInterface;
```

**`ComputerCraft.java` — `preInit`:**
```java
// Register block and tile entity
Blocks.npcInterface = new BlockNpcInterface();
GameRegistry.registerBlock(Blocks.npcInterface, "npc_interface");
GameRegistry.registerTileEntity(TileNpcInterface.class, "computercraft:npc_interface");
```

**`ComputerCraft.java` — `init` (alongside ChatBox bridge):**
```java
if (AbstractNpcAPI.IsAvailable()) {
    AbstractNpcAPI.Instance().events().register(new CustomNpcChatBoxBridge());
    AbstractNpcAPI.Instance().events().register(new NpcInterfaceBridge());
    logger.info("[ComputerCraft] CustomNPCs detected — NPC Interface peripheral enabled.");
}
```

**Crafting recipe** (suggested — e.g., Advanced Computer surrounded by Redstone and Stone):
```
S S S
S A S   → 1× npc_interface
S R S

A = Advanced Computer, S = Stone, R = Redstone
```

---

### Step 7 — Tests

**`NpcInterfaceManagerTest.java`**
- Register mock tile under UUID "abc". Dispatch `npc_interact("abc", "Steve")`.
  Assert tile received `queueNpcEvent("npc_interact", "Steve")`.
- Unregister tile. Dispatch again. Assert no call received.
- Register two tiles under same UUID. Dispatch. Assert both received the event.
- Register tile under UUID "abc", another under "xyz". Dispatch to "abc". Assert only "abc" tile fires.

**`NpcInterfacePeripheralTest.java`**
- `getMethodNames()` returns exactly 25 entries always.
- Methods 0–4 work (no-op / return values) when CNPC is unavailable.
- Methods 5–24 throw `LuaException("CustomNPCs is not installed")` when CNPC is unavailable.
- `link()` with no match returns `false`; with a match returns `true` and tile UUID is set.
- `unlink()` clears UUID and name; `isLinked()` returns `false` after.

---

## Files Created / Changed

| File                                                                    | Type                                                                                    |
|-------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| `compat/customnpcs/peripheral/npcinterface/INpcInterfaceHolder.java`    | New (holder abstraction interface)                                                      |
| `compat/customnpcs/peripheral/npcinterface/BlockNpcInterface.java`      | New                                                                                     |
| `compat/customnpcs/peripheral/npcinterface/TileNpcInterface.java`       | New (implements INpcInterfaceHolder)                                                    |
| `compat/customnpcs/peripheral/npcinterface/NpcInterfacePeripheral.java` | New (takes INpcInterfaceHolder, not TileNpcInterface)                                   |
| `compat/customnpcs/peripheral/npcinterface/NpcInterfaceManager.java`    | New (keyed on INpcInterfaceHolder)                                                      |
| `compat/customnpcs/peripheral/npcinterface/NpcInterfaceBridge.java`     | New                                                                                     |
| `shared/turtle/upgrades/TurtleNpcInterface.java`                        | New (upgrade ID 12; inner Holder class)                                                 |
| `shared/pocket/peripherals/PocketNpcInterfacePeripheral.java`           | New (upgrade=6; UUID in item NBT)                                                       |
| `shared/pocket/recipes/PocketComputerNpcInterfaceUpgradeRecipe.java`    | New                                                                                     |
| `shared/peripheral/PeripheralType.java`                                 | Add `NpcInterface`                                                                      |
| `ComputerCraft.java`                                                    | Add `Upgrades.npcInterface`                                                             |
| `shared/pocket/items/ItemPocketComputer.java`                           | Add `createWithNpcInterface`, `getHasNpcInterface`, onUpdate/createServerComputer hooks |
| `shared/pocket/items/PocketComputerItemFactory.java`                    | Add `createWithNpcInterface`                                                            |
| `shared/proxy/CCTurtleProxyCommon.java`                                 | Register TurtleNpcInterface upgrade ID 12                                               |
| `shared/proxy/ComputerCraftProxyCommon.java`                            | Register pocket NPC Interface recipes + creative                                        |
| `assets/computercraft/textures/npcInterface*.png`                       | New (3 placeholder textures)                                                            |
| `test/.../NpcInterfaceManagerTest.java`                                 | New                                                                                     |
| `test/.../NpcInterfacePeripheralTest.java`                              | New                                                                                     |

## Files NOT Changed

| File                                 | Reason                          |
|--------------------------------------|---------------------------------|
| `BlockChatBox.java`                  | No changes needed               |
| `ChatBoxPeripheral.java`             | Separate peripheral; no overlap |
| `TileChatBox.java`                   | No changes needed               |
| `PortableChatBoxPeripheral.java`     | No changes needed               |
| `TurtleNpcDetector.java`             | Separate peripheral; no overlap |
| `PocketNpcDetectorPeripheral.java`   | Separate peripheral; no overlap |

---

## Open Questions / Decisions Before Implementation

1. **`npc_tick` opt-in**: Every 20 ticks, even throttled, could be noisy if many NPC Interfaces
   are active. Consider replacing the always-on tick with opt-in `startTicking()` / `stopTicking()`
   Lua methods that toggle a flag on `TileNpcInterface`.

2. **`getLoadedEntities()` cost**: Scanning all loaded entities in `link()` and `linkNearest()` is
   O(n) over the entire world entity list. For `link`, pre-filter using the world's
   `getEntitiesWithinAABB(EntityCreature.class, AABB)` on the main thread for better performance.

3. **NPC UUID persistence across respawns**: Confirm whether CustomNPCs NPCs that respawn (via
   `setRespawnTime`) retain the same UUID. If they get a new entity instance with a new UUID,
   the tile will need to re-link by name on `npc_died` + timeout, or expose a `relink()` method.

4. **Crafting recipe**: The suggested recipe uses Advanced Computer as ingredient (not a valid
   item in the recipe system). Finalize recipe with the correct ingredient list.

5. **Turtle / Pocket upgrade variant**: ~~Deferred.~~ **Implemented.** Both variants are live:
   - `TurtleNpcInterface` (upgrade ID 12) stores the linked UUID in `turtle.getUpgradeNBTData(side)`,
     uses `turtle.getPosition()` as the scan origin, and re-registers with `NpcInterfaceManager`
     in the `Holder` constructor on each `createPeripheral()` call.
   - `PocketNpcInterfacePeripheral` (upgrade=6) stores the linked UUID in the pocket-computer item's
     tag compound, and has `setStack(stack)` + `setLocation(world, x, y, z)` called every tick by
     `ItemPocketComputer.onUpdate`. Both are fully gated on `isModLoaded("customnpcs")`.
   - The common `INpcInterfaceHolder` interface was extracted so `NpcInterfacePeripheral` and
     `NpcInterfaceManager` work with all three carriers without modification.

