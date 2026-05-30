package dan200.computercraft.shared.turtle.upgrades;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IIcon;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleUpgradeType;
import dan200.computercraft.api.turtle.TurtleVerb;
import dan200.computercraft.compat.customnpcs.peripheral.npcinterface.BlockNpcInterface;
import dan200.computercraft.compat.customnpcs.peripheral.npcinterface.INpcInterfaceHolder;
import dan200.computercraft.compat.customnpcs.peripheral.npcinterface.NpcInterfaceManager;
import dan200.computercraft.compat.customnpcs.peripheral.npcinterface.NpcInterfacePeripheral;
import noppes.npcs.api.AbstractNpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;

/**
 * Turtle upgrade that embeds an NPC Interface into the turtle's tool slot.
 * All {@code npc_interface} methods are available; the scan origin is the
 * turtle's current block position. Linked NPC UUIDs are persisted in the
 * turtle's upgrade NBT data under key {@code linkedNpcs}.
 *
 * <p>
 * Only registered when CustomNPCs (mod ID {@code customnpcs}) is loaded.
 * </p>
 */
public class TurtleNpcInterface implements ITurtleUpgrade {

    private final int m_id;

    /**
     * Tracks the most-recently created {@link Holder} per turtle × side so that
     * {@link #createPeripheral} can explicitly unregister any stale holder before
     * constructing the replacement. Access must be synchronized on this field.
     */
    private final Map<ITurtleAccess, EnumMap<TurtleSide, Holder>> m_activeHolders = new HashMap<>();

    public TurtleNpcInterface(int id) {
        this.m_id = id;
    }

    @Override
    public int getUpgradeID() {
        return m_id;
    }

    @Override
    public String getUnlocalisedAdjective() {
        return "upgrade.computercraft:npc_interface.adjective";
    }

    @Override
    public TurtleUpgradeType getType() {
        return TurtleUpgradeType.Peripheral;
    }

    @Override
    public ItemStack getCraftingItem() {
        return new ItemStack(ComputerCraft.Blocks.npcInterface);
    }

    @Override
    public IIcon getIcon(ITurtleAccess turtle, TurtleSide side) {
        return BlockNpcInterface.getNpcInterfaceIcon(2, 2);
    }

    @Override
    public IPeripheral createPeripheral(ITurtleAccess turtle, TurtleSide side) {
        // Unregister any stale Holder that was never cleaned up by a detach cycle
        // (e.g. peripheral dropped on chunk reload without a proper attach/detach pair).
        synchronized (m_activeHolders) {
            EnumMap<TurtleSide, Holder> byTurtle = m_activeHolders.get(turtle);
            if (byTurtle != null) {
                Holder stale = byTurtle.remove(side);
                if (stale != null) stale.unregisterFromManager();
                if (byTurtle.isEmpty()) m_activeHolders.remove(turtle);
            }
        }
        Holder holder = new Holder(turtle, side, this);
        synchronized (m_activeHolders) {
            m_activeHolders.computeIfAbsent(turtle, k -> new EnumMap<>(TurtleSide.class))
                .put(side, holder);
        }
        return new NpcInterfacePeripheral(holder);
    }

    /** Called by {@link Holder#detachComputer} when its computer set becomes empty. */
    void removeActiveHolder(ITurtleAccess turtle, TurtleSide side) {
        synchronized (m_activeHolders) {
            EnumMap<TurtleSide, Holder> byTurtle = m_activeHolders.get(turtle);
            if (byTurtle != null) {
                byTurtle.remove(side);
                if (byTurtle.isEmpty()) m_activeHolders.remove(turtle);
            }
        }
    }

    @Override
    public TurtleCommandResult useTool(ITurtleAccess turtle, TurtleSide side, TurtleVerb verb, int dir) {
        return null;
    }

    @Override
    public void update(ITurtleAccess turtle, TurtleSide side) {}

    // =========================================================================
    // Holder — bridges ITurtleAccess to INpcInterfaceHolder
    // =========================================================================

    private static final class Holder implements INpcInterfaceHolder {

        private static final String KEY_LINKED_NPCS = "linkedNpcs";
        /** Legacy single-link keys — read-only for migration. */
        private static final String KEY_UUID_LEGACY = "npcUUID";
        private static final String KEY_NAME_LEGACY = "npcName";

        private final ITurtleAccess m_turtle;
        private final TurtleSide m_side;
        private final TurtleNpcInterface m_upgrade;

        /** Computers with this peripheral attached. */
        private final Set<IComputerAccess> m_computers = new HashSet<>();

        Holder(ITurtleAccess turtle, TurtleSide side, TurtleNpcInterface upgrade) {
            this.m_turtle = turtle;
            this.m_side = side;
            this.m_upgrade = upgrade;
            // Register with manager for any UUIDs already persisted (e.g. turtle loaded from disk)
            for (String uuid : readLinkedNpcs().keySet()) {
                NpcInterfaceManager.register(uuid, this);
            }
        }

        // -----------------------------------------------------------------
        // NBT helpers — stored in the turtle's per-side upgrade NBT
        // -----------------------------------------------------------------

        /** Reads the linked-NPC map from the turtle's upgrade NBT, migrating old format. */
        private Map<String, String> readLinkedNpcs() {
            NBTTagCompound nbt = m_turtle.getUpgradeNBTData(m_side);
            Map<String, String> result = new LinkedHashMap<>();
            if (nbt.hasKey(KEY_LINKED_NPCS)) {
                NBTTagList list = nbt.getTagList(KEY_LINKED_NPCS, 10 /* TAG_COMPOUND */);
                for (int i = 0; i < list.tagCount(); i++) {
                    NBTTagCompound entry = list.getCompoundTagAt(i);
                    String uuid = entry.getString("uuid");
                    String name = entry.getString("name");
                    if (!uuid.isEmpty()) result.put(uuid, name);
                }
            } else if (nbt.hasKey(KEY_UUID_LEGACY)) {
                // Migrate old single-link format
                String uuid = nbt.getString(KEY_UUID_LEGACY);
                String name = nbt.hasKey(KEY_NAME_LEGACY) ? nbt.getString(KEY_NAME_LEGACY) : "";
                if (!uuid.isEmpty()) result.put(uuid, name);
            }
            return result;
        }

        /** Writes the linked-NPC map to the turtle's upgrade NBT and marks it dirty. */
        private void writeLinkedNpcs(Map<String, String> linked) {
            NBTTagCompound nbt = m_turtle.getUpgradeNBTData(m_side);
            // Remove legacy keys on first write
            nbt.removeTag(KEY_UUID_LEGACY);
            nbt.removeTag(KEY_NAME_LEGACY);
            NBTTagList list = new NBTTagList();
            for (Map.Entry<String, String> e : linked.entrySet()) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setString("uuid", e.getKey());
                entry.setString("name", e.getValue());
                list.appendTag(entry);
            }
            nbt.setTag(KEY_LINKED_NPCS, list);
            m_turtle.updateUpgradeNBTData(m_side);
        }

        // -----------------------------------------------------------------
        // INpcInterfaceHolder — UUID state
        // -----------------------------------------------------------------

        @Override
        public String getLinkedUUID() {
            Map<String, String> linked = readLinkedNpcs();
            return linked.isEmpty() ? null
                : linked.keySet()
                    .iterator()
                    .next();
        }

        @Override
        public String getLinkedName() {
            Map<String, String> linked = readLinkedNpcs();
            return linked.isEmpty() ? null
                : linked.values()
                    .iterator()
                    .next();
        }

        @Override
        public synchronized Map<String, String> getLinkedNpcs() {
            return readLinkedNpcs();
        }

        @Override
        public synchronized void setLink(String uuid, String name) {
            Map<String, String> old = readLinkedNpcs();
            for (String oldUUID : old.keySet()) {
                NpcInterfaceManager.unregister(oldUUID, this);
            }
            Map<String, String> newMap = new LinkedHashMap<>();
            if (uuid != null) {
                newMap.put(uuid, name != null ? name : "");
                NpcInterfaceManager.register(uuid, this);
            }
            writeLinkedNpcs(newMap);
        }

        @Override
        public synchronized void addLink(String uuid, String name) {
            if (uuid == null) return;
            Map<String, String> linked = readLinkedNpcs();
            if (linked.containsKey(uuid)) return;
            linked.put(uuid, name != null ? name : "");
            writeLinkedNpcs(linked);
            NpcInterfaceManager.register(uuid, this);
        }

        @Override
        public synchronized void removeLink(String uuid) {
            if (uuid == null) return;
            Map<String, String> linked = readLinkedNpcs();
            if (!linked.containsKey(uuid)) return;
            linked.remove(uuid);
            writeLinkedNpcs(linked);
            NpcInterfaceManager.unregister(uuid, this);
        }

        @Override
        public synchronized void clearLinks() {
            Map<String, String> linked = readLinkedNpcs();
            for (String uuid : linked.keySet()) {
                NpcInterfaceManager.unregister(uuid, this);
            }
            writeLinkedNpcs(new LinkedHashMap<>());
        }

        // -----------------------------------------------------------------
        // INpcInterfaceHolder — position (turtle's block centre)
        // -----------------------------------------------------------------

        @Override
        public double getPositionX() {
            ChunkCoordinates pos = m_turtle.getPosition();
            return pos.posX + 0.5;
        }

        @Override
        public double getPositionY() {
            ChunkCoordinates pos = m_turtle.getPosition();
            return pos.posY + 0.5;
        }

        @Override
        public double getPositionZ() {
            ChunkCoordinates pos = m_turtle.getPosition();
            return pos.posZ + 0.5;
        }

        // -----------------------------------------------------------------
        // INpcInterfaceHolder — computer tracking
        // -----------------------------------------------------------------

        @Override
        public synchronized void attachComputer(IComputerAccess computer) {
            m_computers.add(computer);
        }

        @Override
        public synchronized void detachComputer(IComputerAccess computer) {
            m_computers.remove(computer);
            // If no computers remain, unregister so we stop receiving events,
            // and remove this holder from the upgrade's active-holder map.
            if (m_computers.isEmpty()) {
                unregisterFromManager();
                m_upgrade.removeActiveHolder(m_turtle, m_side);
            }
        }

        /** Unregisters this holder from {@link NpcInterfaceManager} for all linked UUIDs. */
        void unregisterFromManager() {
            for (String uuid : readLinkedNpcs().keySet()) {
                NpcInterfaceManager.unregister(uuid, this);
            }
        }

        // -----------------------------------------------------------------
        // INpcInterfaceHolder — NPC resolution
        // -----------------------------------------------------------------

        @Override
        public ICustomNpc<?> resolveNpc() {
            Map<String, String> linked = readLinkedNpcs();
            if (linked.isEmpty()) return null;
            String uuid = linked.keySet()
                .iterator()
                .next();
            return resolveByUUID(uuid);
        }

        @Override
        public List<ICustomNpc<?>> resolveNpcs() {
            Map<String, String> linked = readLinkedNpcs();
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

        // -----------------------------------------------------------------
        // INpcInterfaceHolder — event dispatch
        // -----------------------------------------------------------------

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

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Holder)) return false;
            Holder other = (Holder) obj;
            return m_turtle == other.m_turtle && m_side == other.m_side;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(m_turtle) * 31 + m_side.hashCode();
        }
    }
}
