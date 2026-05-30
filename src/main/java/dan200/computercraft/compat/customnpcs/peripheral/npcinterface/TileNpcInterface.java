package dan200.computercraft.compat.customnpcs.peripheral.npcinterface;

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
 * Persists the set of linked NPC UUIDs in NBT (key {@code linkedNpcs}).
 * Old saves with a single {@code npcUUID} key are migrated automatically.
 * On load, re-registers with {@link NpcInterfaceManager} so events resume.
 * </p>
 */
public class TileNpcInterface extends TileGeneric implements IPeripheralTile, INpcInterfaceHolder {

    /** Facing direction (2–5). */
    int m_direction = 2;

    /** UUID → cached display name for every linked NPC. */
    private final Map<String, String> m_linkedNpcs = new LinkedHashMap<>();

    /** Computers currently attached to this peripheral. */
    private final Set<IComputerAccess> m_computers = new HashSet<>();

    // -------------------------------------------------------------------------
    // INpcInterfaceHolder — UUID / link state
    // -------------------------------------------------------------------------

    @Override
    public synchronized String getLinkedUUID() {
        return m_linkedNpcs.isEmpty() ? null
            : m_linkedNpcs.keySet()
                .iterator()
                .next();
    }

    @Override
    public synchronized String getLinkedName() {
        return m_linkedNpcs.isEmpty() ? null
            : m_linkedNpcs.values()
                .iterator()
                .next();
    }

    @Override
    public synchronized Map<String, String> getLinkedNpcs() {
        return new LinkedHashMap<>(m_linkedNpcs);
    }

    @Override
    public synchronized void setLink(String uuid, String name) {
        for (String old : new ArrayList<>(m_linkedNpcs.keySet())) {
            NpcInterfaceManager.unregister(old, this);
        }
        m_linkedNpcs.clear();
        if (uuid != null) {
            m_linkedNpcs.put(uuid, name != null ? name : "");
            NpcInterfaceManager.register(uuid, this);
        }
        markDirty();
    }

    @Override
    public synchronized void addLink(String uuid, String name) {
        if (uuid == null || m_linkedNpcs.containsKey(uuid)) return;
        m_linkedNpcs.put(uuid, name != null ? name : "");
        NpcInterfaceManager.register(uuid, this);
        markDirty();
    }

    @Override
    public synchronized void removeLink(String uuid) {
        if (uuid == null || !m_linkedNpcs.containsKey(uuid)) return;
        m_linkedNpcs.remove(uuid);
        NpcInterfaceManager.unregister(uuid, this);
        markDirty();
    }

    @Override
    public synchronized void clearLinks() {
        for (String uuid : new ArrayList<>(m_linkedNpcs.keySet())) {
            NpcInterfaceManager.unregister(uuid, this);
        }
        m_linkedNpcs.clear();
        markDirty();
    }

    // -------------------------------------------------------------------------
    // Event dispatch (called by NpcInterfaceManager on the event thread)
    // -------------------------------------------------------------------------

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

    @Override
    public synchronized void attachComputer(IComputerAccess computer) {
        m_computers.add(computer);
    }

    @Override
    public synchronized void detachComputer(IComputerAccess computer) {
        m_computers.remove(computer);
    }

    // -------------------------------------------------------------------------
    // INpcInterfaceHolder — NPC resolution
    // -------------------------------------------------------------------------

    @Override
    public ICustomNpc<?> resolveNpc() {
        String uuid;
        synchronized (this) {
            if (m_linkedNpcs.isEmpty()) return null;
            uuid = m_linkedNpcs.keySet()
                .iterator()
                .next();
        }
        return resolveByUUID(uuid);
    }

    @Override
    public List<ICustomNpc<?>> resolveNpcs() {
        Map<String, String> snapshot;
        synchronized (this) {
            if (m_linkedNpcs.isEmpty()) return Collections.emptyList();
            snapshot = new LinkedHashMap<>(m_linkedNpcs);
        }
        List<ICustomNpc<?>> result = new ArrayList<>();
        try {
            if (!AbstractNpcAPI.IsAvailable()) return result;
            AbstractNpcAPI api = AbstractNpcAPI.Instance();
            if (api == null) return result;
            for (IEntity<?> entity : api.getLoadedEntities()) {
                if (entity instanceof ICustomNpc && snapshot.containsKey(entity.getUniqueID())) {
                    result.add((ICustomNpc<?>) entity);
                }
            }
        } catch (Throwable t) {
            // CNPC absent or incompatible
        }
        return result;
    }

    private static ICustomNpc<?> resolveByUUID(String uuid) {
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

    // -------------------------------------------------------------------------
    // TileEntity lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void validate() {
        super.validate();
        Map<String, String> snapshot;
        synchronized (this) {
            snapshot = new LinkedHashMap<>(m_linkedNpcs);
        }
        for (String uuid : snapshot.keySet()) {
            NpcInterfaceManager.register(uuid, this);
        }
    }

    @Override
    public void invalidate() {
        Map<String, String> snapshot;
        synchronized (this) {
            snapshot = new LinkedHashMap<>(m_linkedNpcs);
        }
        for (String uuid : snapshot.keySet()) {
            NpcInterfaceManager.unregister(uuid, this);
        }
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        Map<String, String> snapshot;
        synchronized (this) {
            snapshot = new LinkedHashMap<>(m_linkedNpcs);
        }
        for (String uuid : snapshot.keySet()) {
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
        synchronized (this) {
            m_linkedNpcs.clear();
            if (nbt.hasKey("linkedNpcs")) {
                // New multi-link format
                NBTTagList list = nbt.getTagList("linkedNpcs", 10 /* TAG_COMPOUND */);
                for (int i = 0; i < list.tagCount(); i++) {
                    NBTTagCompound entry = list.getCompoundTagAt(i);
                    String uuid = entry.getString("uuid");
                    String name = entry.getString("name");
                    if (!uuid.isEmpty()) {
                        m_linkedNpcs.put(uuid, name);
                    }
                }
            } else if (nbt.hasKey("npcUUID")) {
                // Migrate old single-link format
                String uuid = nbt.getString("npcUUID");
                String name = nbt.hasKey("npcName") ? nbt.getString("npcName") : "";
                if (!uuid.isEmpty()) {
                    m_linkedNpcs.put(uuid, name);
                }
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("dir", m_direction);
        // Remove old single-link keys so migrated saves stay clean
        nbt.removeTag("npcUUID");
        nbt.removeTag("npcName");
        Map<String, String> snapshot;
        synchronized (this) {
            snapshot = new LinkedHashMap<>(m_linkedNpcs);
        }
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, String> e : snapshot.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("uuid", e.getKey());
            entry.setString("name", e.getValue());
            list.appendTag(entry);
        }
        nbt.setTag("linkedNpcs", list);
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
