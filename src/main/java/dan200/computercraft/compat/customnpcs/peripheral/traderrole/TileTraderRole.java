package dan200.computercraft.compat.customnpcs.peripheral.traderrole;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.common.TileGeneric;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.IPeripheralTile;
import noppes.npcs.api.AbstractNpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.roles.IRoleTrader;

/**
 * Tile entity for the NPC Trader Role peripheral block.
 *
 * <p>
 * Persists the linked NPC's UUID in NBT. No event subscription is needed —
 * this peripheral is entirely poll-based.
 * </p>
 */
public class TileTraderRole extends TileGeneric implements IPeripheralTile {

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
        m_linkedUUID = uuid;
        m_linkedName = name;
        markDirty();
    }

    public synchronized String getLinkedUUID() {
        return m_linkedUUID;
    }

    public synchronized String getLinkedName() {
        return m_linkedName;
    }

    /**
     * Resolves the live NPC's Trader role. <strong>Must be called on the main
     * thread.</strong>
     *
     * @return the live {@link IRoleTrader}
     * @throws LuaException if not linked, NPC is not found/loaded, is dead, or
     *                      does not have a Trader role (role type 1).
     */
    public IRoleTrader resolveTrader() throws LuaException {
        if (m_linkedUUID == null) {
            throw new LuaException("Not linked to any NPC");
        }
        try {
            if (!AbstractNpcAPI.IsAvailable()) {
                throw new LuaException("CustomNPCs is not installed");
            }
            AbstractNpcAPI api = AbstractNpcAPI.Instance();
            if (api == null) {
                throw new LuaException("CustomNPCs API unavailable");
            }
            for (IEntity<?> entity : api.getLoadedEntities()) {
                if (!(entity instanceof ICustomNpc)) continue;
                if (!m_linkedUUID.equals(entity.getUniqueID())) continue;
                ICustomNpc<?> npc = (ICustomNpc<?>) entity;
                if (!npc.isAlive()) {
                    throw new LuaException("NPC is dead");
                }
                var role = npc.getRole();
                if (role == null || role.getType() != 1) {
                    throw new LuaException("NPC does not have a Trader role");
                }
                return (IRoleTrader) role;
            }
        } catch (LuaException e) {
            throw e;
        } catch (Throwable t) {
            throw new LuaException("CustomNPCs is not installed");
        }
        throw new LuaException("NPC not found — is it loaded?");
    }

    // -------------------------------------------------------------------------
    // IPeripheralTile
    // -------------------------------------------------------------------------

    @Override
    public PeripheralType getPeripheralType() {
        return PeripheralType.TraderRole;
    }

    @Override
    public IPeripheral getPeripheral(int side) {
        return new TraderRolePeripheral(this);
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
    // Computer tracking
    // -------------------------------------------------------------------------

    public synchronized void attachComputer(IComputerAccess computer) {
        m_computers.add(computer);
    }

    public synchronized void detachComputer(IComputerAccess computer) {
        m_computers.remove(computer);
    }

    // -------------------------------------------------------------------------
    // TileGeneric — rendering / drops / pick block
    // -------------------------------------------------------------------------

    @Override
    public IIcon getTexture(int side) {
        return BlockTraderRole.getTraderRoleIcon(side, m_direction);
    }

    @Override
    public void getDroppedItems(List<ItemStack> drops, int fortune, boolean creative, boolean silkTouch) {
        if (!creative) {
            drops.add(new ItemStack(ComputerCraft.Blocks.traderRole));
        }
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(ComputerCraft.Blocks.traderRole);
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
