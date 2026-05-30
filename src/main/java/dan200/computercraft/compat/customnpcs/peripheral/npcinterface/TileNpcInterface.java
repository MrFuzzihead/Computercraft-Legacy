package dan200.computercraft.compat.customnpcs.peripheral.npcinterface;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.common.TileGeneric;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.IPeripheralTile;
import noppes.npcs.api.AbstractNpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;

/**
 * Tile entity for the NPC Interface block.
 *
 * <p>
 * Persists the linked NPC's UUID in NBT. On load it re-registers with
 * {@link NpcInterfaceManager} so that events resume routing to this tile.
 * </p>
 */
public class TileNpcInterface extends TileGeneric implements IPeripheralTile, INpcInterfaceHolder {

    /** Facing direction (2–5). */
    int m_direction = 2;

    // Persisted
    private String m_linkedUUID = null;
    private String m_linkedName = null;

    /** Computers currently attached to this peripheral. */
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

    public synchronized String getLinkedUUID() {
        return m_linkedUUID;
    }

    public synchronized String getLinkedName() {
        return m_linkedName;
    }

    /**
     * Resolves the live NPC entity by UUID. Must be called on the main thread.
     *
     * @return the live {@link ICustomNpc}, or {@code null} if not found / CNPC absent.
     */
    public ICustomNpc<?> resolveNpc() {
        if (m_linkedUUID == null) return null;
        try {
            if (!AbstractNpcAPI.IsAvailable()) return null;
            AbstractNpcAPI api = AbstractNpcAPI.Instance();
            if (api == null) return null;
            for (IEntity<?> entity : api.getLoadedEntities()) {
                if (entity instanceof ICustomNpc && m_linkedUUID.equals(entity.getUniqueID())) {
                    return (ICustomNpc<?>) entity;
                }
            }
        } catch (Throwable t) {
            // CNPC absent or incompatible
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Event dispatch (called by NpcInterfaceManager on the event thread)
    // -------------------------------------------------------------------------

    public void queueNpcEvent(String event, Object... params) {
        Set<IComputerAccess> snapshot;
        synchronized (this) {
            snapshot = new HashSet<>(m_computers);
        }
        for (IComputerAccess computer : snapshot) {
            computer.queueEvent(event, params);
        }
    }

    // -------------------------------------------------------------------------
    // INpcInterfaceHolder — position (scan origin for link / linkNearest)
    // -------------------------------------------------------------------------

    @Override
    public double getPositionX() {
        return xCoord + 0.5;
    }

    @Override
    public double getPositionY() {
        return yCoord + 0.5;
    }

    @Override
    public double getPositionZ() {
        return zCoord + 0.5;
    }

    // -------------------------------------------------------------------------
    // IPeripheralTile
    // -------------------------------------------------------------------------

    @Override
    public PeripheralType getPeripheralType() {
        return PeripheralType.NpcInterface;
    }

    @Override
    public IPeripheral getPeripheral(int side) {
        return new NpcInterfacePeripheral(this);
    }

    @Override
    public String getLabel() {
        return null;
    }

    @Override
    public int getDirection() {
        return m_direction;
    }

    @Override
    public void setDirection(int dir) {
        if (dir < 2 || dir > 5) dir = 2;
        m_direction = dir;
    }

    // -------------------------------------------------------------------------
    // Computer tracking (called by NpcInterfacePeripheral.attach / .detach)
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
        String uuid;
        synchronized (this) {
            uuid = m_linkedUUID;
        }
        if (uuid != null) {
            NpcInterfaceManager.register(uuid, this);
        }
    }

    @Override
    public void invalidate() {
        String uuid;
        synchronized (this) {
            uuid = m_linkedUUID;
        }
        if (uuid != null) {
            NpcInterfaceManager.unregister(uuid, this);
        }
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        String uuid;
        synchronized (this) {
            uuid = m_linkedUUID;
        }
        if (uuid != null) {
            NpcInterfaceManager.unregister(uuid, this);
        }
        super.onChunkUnload();
    }

    // -------------------------------------------------------------------------
    // TileGeneric — drops / pick block / texture
    // -------------------------------------------------------------------------

    @Override
    public IIcon getTexture(int side) {
        return BlockNpcInterface.getNpcInterfaceIcon(side, m_direction);
    }

    @Override
    public void getDroppedItems(List<ItemStack> drops, int fortune, boolean creative, boolean silkTouch) {
        if (!creative) {
            drops.add(new ItemStack(ComputerCraft.Blocks.npcInterface));
        }
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(ComputerCraft.Blocks.npcInterface);
    }

    // -------------------------------------------------------------------------
    // NBT
    // -------------------------------------------------------------------------

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey("dir")) {
            m_direction = nbt.getInteger("dir");
        }
        m_linkedUUID = nbt.hasKey("npcUUID") ? nbt.getString("npcUUID") : null;
        m_linkedName = nbt.hasKey("npcName") ? nbt.getString("npcName") : null;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("dir", m_direction);
        if (m_linkedUUID != null) nbt.setString("npcUUID", m_linkedUUID);
        if (m_linkedName != null) nbt.setString("npcName", m_linkedName);
    }

    @Override
    protected void writeDescription(NBTTagCompound nbt) {
        nbt.setInteger("dir", m_direction);
    }

    @Override
    protected void readDescription(NBTTagCompound nbt) {
        m_direction = nbt.getInteger("dir");
        updateBlock();
    }
}
