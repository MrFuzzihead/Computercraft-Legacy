package dan200.computercraft.shared.pocket.peripherals;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

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
 * Implements {@link INpcInterfaceHolder} directly: the linked NPC UUID is
 * persisted in the pocket computer {@link ItemStack}'s tag compound, and
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

    private static final String KEY_UUID = "npcUUID";
    private static final String KEY_NAME = "npcName";

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
     *                     stored UUID.
     */
    public PocketNpcInterfacePeripheral(ItemStack initialStack) {
        this.m_stack = initialStack;
        this.m_peripheral = new NpcInterfacePeripheral(this);

        // Register with manager if already linked
        String uuid = getLinkedUUID();
        if (uuid != null) {
            NpcInterfaceManager.register(uuid, this);
        }
    }

    // =========================================================================
    // Tick-driven updates (called by ItemPocketComputer.onUpdate)
    // =========================================================================

    /**
     * Updates the item stack reference. Detects UUID changes that might result
     * from the item being shared or duplicated, and re-syncs manager
     * registration accordingly.
     */
    public synchronized void setStack(ItemStack stack) {
        String oldUUID = getLinkedUUID();
        m_stack = stack;
        String newUUID = getLinkedUUID();
        if (!java.util.Objects.equals(oldUUID, newUUID)) {
            if (oldUUID != null) NpcInterfaceManager.unregister(oldUUID, this);
            if (newUUID != null) NpcInterfaceManager.register(newUUID, this);
        }
    }

    /** Updates the position used by {@code link} and {@code linkNearest}. */
    public void setLocation(double x, double y, double z) {
        m_x = x;
        m_y = y;
        m_z = z;
    }

    // =========================================================================
    // INpcInterfaceHolder — UUID state (stored in stack NBT)
    // =========================================================================

    @Override
    public synchronized String getLinkedUUID() {
        if (m_stack == null || !m_stack.hasTagCompound()) return null;
        NBTTagCompound tag = m_stack.getTagCompound();
        return tag.hasKey(KEY_UUID) ? tag.getString(KEY_UUID) : null;
    }

    @Override
    public synchronized String getLinkedName() {
        if (m_stack == null || !m_stack.hasTagCompound()) return null;
        NBTTagCompound tag = m_stack.getTagCompound();
        return tag.hasKey(KEY_NAME) ? tag.getString(KEY_NAME) : null;
    }

    @Override
    public synchronized void setLink(String uuid, String name) {
        String old = getLinkedUUID();
        if (old != null) {
            NpcInterfaceManager.unregister(old, this);
        }
        if (m_stack != null) {
            if (!m_stack.hasTagCompound()) {
                m_stack.setTagCompound(new NBTTagCompound());
            }
            NBTTagCompound tag = m_stack.getTagCompound();
            if (uuid != null) {
                tag.setString(KEY_UUID, uuid);
                tag.setString(KEY_NAME, name != null ? name : "");
            } else {
                tag.removeTag(KEY_UUID);
                tag.removeTag(KEY_NAME);
            }
        }
        if (uuid != null) {
            NpcInterfaceManager.register(uuid, this);
        }
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
            String uuid = getLinkedUUID();
            if (uuid != null) {
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
