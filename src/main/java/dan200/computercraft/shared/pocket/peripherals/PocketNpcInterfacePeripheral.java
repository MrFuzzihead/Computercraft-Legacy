package dan200.computercraft.shared.pocket.peripherals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.compat.customnpcs.peripheral.npcinterface.INpcInterfaceHolder;
import dan200.computercraft.compat.customnpcs.peripheral.npcinterface.NpcInterfaceManager;
import dan200.computercraft.compat.customnpcs.peripheral.npcinterface.NpcInterfacePeripheral;
import noppes.npcs.api.AbstractNpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;

/**
 * NPC Interface peripheral for pocket computers.
 *
 * <p>
 * Implements {@link INpcInterfaceHolder} directly: the linked NPC UUIDs are
 * persisted in the pocket computer {@link ItemStack}'s tag compound under key
 * {@code linkedNpcs} (NBTTagList). Old saves with {@code npcUUID} are migrated.
 * {@link dan200.computercraft.shared.pocket.items.ItemPocketComputer#onUpdate}
 * calls {@link #setStack} and {@link #setLocation} every tick to keep the
 * stack reference and position current.
 * </p>
 *
 * <p>
 * Only created when CustomNPCs (mod ID {@code customnpcs}) is loaded.
 * </p>
 */
public class PocketNpcInterfacePeripheral implements IPeripheral, INpcInterfaceHolder {

    private static final String KEY_LINKED_NPCS = "linkedNpcs";
    /** Legacy single-link keys — read-only for migration. */
    private static final String KEY_UUID_LEGACY = "npcUUID";
    private static final String KEY_NAME_LEGACY = "npcName";

    // Updated every tick by ItemPocketComputer.onUpdate
    private volatile ItemStack m_stack;
    private volatile double m_x;
    private volatile double m_y;
    private volatile double m_z;

    /** Computers with this peripheral attached. */
    private final Set<IComputerAccess> m_computers = new HashSet<>();

    /** Delegates all IPeripheral + NPC logic to the shared implementation. */
    private final NpcInterfacePeripheral m_peripheral;

    /**
     * @param initialStack the pocket computer stack at the moment the server
     *                     computer is created; used to read any previously
     *                     stored UUID(s).
     */
    public PocketNpcInterfacePeripheral(ItemStack initialStack) {
        this.m_stack = initialStack;
        this.m_peripheral = new NpcInterfacePeripheral(this);
        // Register with manager for any UUIDs already stored in the stack
        for (String uuid : getLinkedNpcs().keySet()) {
            NpcInterfaceManager.register(uuid, this);
        }
    }

    // =========================================================================
    // Tick-driven updates (called by ItemPocketComputer.onUpdate)
    // =========================================================================

    /**
     * Updates the item stack reference. Detects UUID set changes that might result
     * from the item being shared or duplicated, and re-syncs manager registration.
     */
    public synchronized void setStack(ItemStack stack) {
        Map<String, String> oldLinked = readFromStack(m_stack);
        m_stack = stack;
        Map<String, String> newLinked = readFromStack(m_stack);
        // Unregister UUIDs that are no longer present
        for (String uuid : oldLinked.keySet()) {
            if (!newLinked.containsKey(uuid)) NpcInterfaceManager.unregister(uuid, this);
        }
        // Register newly-present UUIDs
        for (String uuid : newLinked.keySet()) {
            if (!oldLinked.containsKey(uuid)) NpcInterfaceManager.register(uuid, this);
        }
    }

    /** Updates the position used by {@code link} and {@code linkNearest}. */
    public void setLocation(double x, double y, double z) {
        m_x = x;
        m_y = y;
        m_z = z;
    }

    // =========================================================================
    // NBT helpers
    // =========================================================================

    /** Reads the linked-NPC map from a stack's NBT, migrating old single-link format. */
    private static Map<String, String> readFromStack(ItemStack stack) {
        Map<String, String> result = new LinkedHashMap<>();
        if (stack == null || !stack.hasTagCompound()) return result;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag.hasKey(KEY_LINKED_NPCS)) {
            NBTTagList list = tag.getTagList(KEY_LINKED_NPCS, 10 /* TAG_COMPOUND */);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                String uuid = entry.getString("uuid");
                String name = entry.getString("name");
                if (!uuid.isEmpty()) result.put(uuid, name);
            }
        } else if (tag.hasKey(KEY_UUID_LEGACY)) {
            // Migrate old format
            String uuid = tag.getString(KEY_UUID_LEGACY);
            String name = tag.hasKey(KEY_NAME_LEGACY) ? tag.getString(KEY_NAME_LEGACY) : "";
            if (!uuid.isEmpty()) result.put(uuid, name);
        }
        return result;
    }

    /** Writes the linked-NPC map to the current stack's NBT. */
    private synchronized void writeToStack(Map<String, String> linked) {
        if (m_stack == null) return;
        if (!m_stack.hasTagCompound()) m_stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound tag = m_stack.getTagCompound();
        tag.removeTag(KEY_UUID_LEGACY);
        tag.removeTag(KEY_NAME_LEGACY);
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, String> e : linked.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("uuid", e.getKey());
            entry.setString("name", e.getValue());
            list.appendTag(entry);
        }
        tag.setTag(KEY_LINKED_NPCS, list);
    }

    // =========================================================================
    // INpcInterfaceHolder — UUID state (stored in stack NBT)
    // =========================================================================

    @Override
    public synchronized String getLinkedUUID() {
        Map<String, String> linked = readFromStack(m_stack);
        return linked.isEmpty() ? null
            : linked.keySet()
                .iterator()
                .next();
    }

    @Override
    public synchronized String getLinkedName() {
        Map<String, String> linked = readFromStack(m_stack);
        return linked.isEmpty() ? null
            : linked.values()
                .iterator()
                .next();
    }

    @Override
    public synchronized Map<String, String> getLinkedNpcs() {
        return readFromStack(m_stack);
    }

    @Override
    public synchronized void setLink(String uuid, String name) {
        Map<String, String> old = readFromStack(m_stack);
        for (String oldUUID : old.keySet()) {
            NpcInterfaceManager.unregister(oldUUID, this);
        }
        Map<String, String> newMap = new LinkedHashMap<>();
        if (uuid != null) {
            newMap.put(uuid, name != null ? name : "");
            NpcInterfaceManager.register(uuid, this);
        }
        writeToStack(newMap);
    }

    @Override
    public synchronized void addLink(String uuid, String name) {
        if (uuid == null) return;
        Map<String, String> linked = readFromStack(m_stack);
        if (linked.containsKey(uuid)) return;
        linked.put(uuid, name != null ? name : "");
        writeToStack(linked);
        NpcInterfaceManager.register(uuid, this);
    }

    @Override
    public synchronized void removeLink(String uuid) {
        if (uuid == null) return;
        Map<String, String> linked = readFromStack(m_stack);
        if (!linked.containsKey(uuid)) return;
        linked.remove(uuid);
        writeToStack(linked);
        NpcInterfaceManager.unregister(uuid, this);
    }

    @Override
    public synchronized void clearLinks() {
        Map<String, String> linked = readFromStack(m_stack);
        for (String uuid : linked.keySet()) {
            NpcInterfaceManager.unregister(uuid, this);
        }
        writeToStack(new LinkedHashMap<>());
    }

    // =========================================================================
    // INpcInterfaceHolder — position (player's eye position, updated each tick)
    // =========================================================================

    @Override
    public double getPositionX() {
        return m_x;
    }

    @Override
    public double getPositionY() {
        return m_y;
    }

    @Override
    public double getPositionZ() {
        return m_z;
    }

    // =========================================================================
    // INpcInterfaceHolder — computer tracking
    // =========================================================================

    @Override
    public synchronized void attachComputer(IComputerAccess computer) {
        m_computers.add(computer);
    }

    @Override
    public synchronized void detachComputer(IComputerAccess computer) {
        m_computers.remove(computer);
        // Unregister from manager when the last computer detaches (peripheral removed).
        if (m_computers.isEmpty()) {
            for (String uuid : readFromStack(m_stack).keySet()) {
                NpcInterfaceManager.unregister(uuid, this);
            }
        }
    }

    // =========================================================================
    // INpcInterfaceHolder — NPC resolution
    // =========================================================================

    @Override
    public ICustomNpc<?> resolveNpc() {
        String uuid = getLinkedUUID();
        if (uuid == null) return null;
        try {
            if (!AbstractNpcAPI.IsAvailable()) return null;
            AbstractNpcAPI api = AbstractNpcAPI.Instance();
            if (api == null) return null;
            for (IEntity<?> entity : api.getLoadedEntities()) {
                if (entity instanceof ICustomNpc && uuid.equals(entity.getUniqueID())) {
                    return (ICustomNpc<?>) entity;
                }
            }
        } catch (Throwable t) {
            // CNPC absent or incompatible
        }
        return null;
    }

    @Override
    public List<ICustomNpc<?>> resolveNpcs() {
        Map<String, String> linked = getLinkedNpcs();
        if (linked.isEmpty()) return Collections.emptyList();
        List<ICustomNpc<?>> result = new ArrayList<>();
        try {
            if (!AbstractNpcAPI.IsAvailable()) return result;
            AbstractNpcAPI api = AbstractNpcAPI.Instance();
            if (api == null) return result;
            for (IEntity<?> entity : api.getLoadedEntities()) {
                if (entity instanceof ICustomNpc && linked.containsKey(entity.getUniqueID())) {
                    result.add((ICustomNpc<?>) entity);
                }
            }
        } catch (Throwable t) {
            // CNPC absent or incompatible
        }
        return result;
    }

    // =========================================================================
    // INpcInterfaceHolder — event dispatch
    // =========================================================================

    @Override
    public void queueNpcEvent(String event, Object... params) {
        Set<IComputerAccess> snapshot;
        synchronized (this) {
            snapshot = new HashSet<>(m_computers);
        }
        for (IComputerAccess computer : snapshot) {
            computer.queueEvent(event, params);
        }
    }

    // =========================================================================
    // IPeripheral — delegated to NpcInterfacePeripheral
    // =========================================================================

    @Override
    public String getType() {
        return m_peripheral.getType();
    }

    @Override
    public String[] getMethodNames() {
        return m_peripheral.getMethodNames();
    }

    @Override
    public void attach(IComputerAccess computer) {
        m_peripheral.attach(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        m_peripheral.detach(computer);
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] args)
        throws LuaException, InterruptedException {
        return m_peripheral.callMethod(computer, context, method, args);
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof PocketNpcInterfacePeripheral;
    }
}
