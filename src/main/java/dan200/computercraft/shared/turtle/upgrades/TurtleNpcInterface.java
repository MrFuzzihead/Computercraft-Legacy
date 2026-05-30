package dan200.computercraft.shared.turtle.upgrades;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
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
 * All 32 {@code npc_interface} methods are available; the scan origin is the
 * turtle's current block position. The linked NPC UUID is persisted in the
 * turtle's upgrade NBT data.
 *
 * <p>
 * Only registered when CustomNPCs (mod ID {@code customnpcs}) is loaded.
 * </p>
 */
public class TurtleNpcInterface implements ITurtleUpgrade {

    private final int m_id;

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
        return new NpcInterfacePeripheral(new Holder(turtle, side));
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

        private static final String KEY_UUID = "npcUUID";
        private static final String KEY_NAME = "npcName";

        private final ITurtleAccess m_turtle;
        private final TurtleSide m_side;

        /** Computers with this peripheral attached. */
        private final Set<IComputerAccess> m_computers = new HashSet<>();

        Holder(ITurtleAccess turtle, TurtleSide side) {
            this.m_turtle = turtle;
            this.m_side = side;
            // Register with manager if already linked (e.g. turtle loaded from disk)
            String uuid = getLinkedUUID();
            if (uuid != null) {
                NpcInterfaceManager.register(uuid, this);
            }
        }

        // -----------------------------------------------------------------
        // INpcInterfaceHolder — UUID state (stored in upgrade NBT)
        // -----------------------------------------------------------------

        @Override
        public String getLinkedUUID() {
            NBTTagCompound nbt = m_turtle.getUpgradeNBTData(m_side);
            return nbt.hasKey(KEY_UUID) ? nbt.getString(KEY_UUID) : null;
        }

        @Override
        public String getLinkedName() {
            NBTTagCompound nbt = m_turtle.getUpgradeNBTData(m_side);
            return nbt.hasKey(KEY_NAME) ? nbt.getString(KEY_NAME) : null;
        }

        @Override
        public synchronized void setLink(String uuid, String name) {
            String old = getLinkedUUID();
            if (old != null) {
                NpcInterfaceManager.unregister(old, this);
            }
            NBTTagCompound nbt = m_turtle.getUpgradeNBTData(m_side);
            if (uuid != null) {
                nbt.setString(KEY_UUID, uuid);
                nbt.setString(KEY_NAME, name != null ? name : "");
            } else {
                nbt.removeTag(KEY_UUID);
                nbt.removeTag(KEY_NAME);
            }
            m_turtle.updateUpgradeNBTData(m_side);
            if (uuid != null) {
                NpcInterfaceManager.register(uuid, this);
            }
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
            // If no computers remain, unregister so we stop receiving events.
            if (m_computers.isEmpty()) {
                String uuid = getLinkedUUID();
                if (uuid != null) {
                    NpcInterfaceManager.unregister(uuid, this);
                }
            }
        }

        // -----------------------------------------------------------------
        // INpcInterfaceHolder — NPC resolution
        // -----------------------------------------------------------------

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
