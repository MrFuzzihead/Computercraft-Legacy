package dan200.computercraft.shared.peripheral.modem;

import java.io.File;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.util.IDAssigner;
import dan200.computercraft.shared.util.PeripheralUtil;

/**
 * One local peripheral exposed on a wired network.
 *
 * <p>
 * This is the 1.7.10 equivalent of CC:Tweaked's {@code WiredModemLocalPeripheral}. It tracks the peripheral in the
 * world, its type and the ID used in its {@code type_id} network name. The ID is stable across detach/re-attach and
 * is reallocated when the peripheral's type changes, exactly as upstream does.
 * </p>
 */
public class WiredModemLocalPeripheral {

    private static final String NBT_PERIPHERAL_TYPE = "PeripheralType";
    private static final String NBT_PERIPHERAL_ID = "PeripheralId";

    private int m_id = -1;
    private String m_type = null;
    private IPeripheral m_peripheral = null;

    /**
     * Resolve and attach the peripheral adjacent to a block.
     *
     * @param world The world to search in.
     * @param x     The origin block's x coordinate.
     * @param y     The origin block's y coordinate.
     * @param z     The origin block's z coordinate.
     * @param side  The side of the origin block to search.
     * @return Whether the attached peripheral changed.
     */
    public boolean attach(World world, int x, int y, int z, int side) {
        IPeripheral peripheral = PeripheralUtil.getPeripheral(world, x, y, z, side);
        return attach(peripheral, type -> assignId(world, type));
    }

    boolean attach(IPeripheral peripheral, IdAllocator allocator) {
        // Wired modems are not peripherals themselves: exposing one would let a modem see itself through a
        // neighbouring modem. This mirrors the upstream check for WiredModemPeripheral.
        if (peripheral != null && TileCable.isWiredModemPeripheral(peripheral)) {
            peripheral = null;
        }

        IPeripheral oldPeripheral = this.m_peripheral;
        this.m_peripheral = peripheral;

        if (peripheral == null) {
            return oldPeripheral != null;
        }

        String type = peripheral.getType();
        if (this.m_id > 0 && this.m_type == null) {
            // If we had an ID but no type (an older save), just set the type.
            this.m_type = type;
        } else if (this.m_id < 0 || !type.equals(this.m_type)) {
            this.m_type = type;
            this.m_id = allocator.getNextId(type);
        }

        return oldPeripheral == null || !oldPeripheral.equals(peripheral);
    }

    /**
     * Detach the current peripheral, keeping the type and ID for a later re-attach.
     *
     * @return Whether the attached peripheral changed.
     */
    public boolean detach() {
        if (this.m_peripheral == null) {
            return false;
        }

        this.m_peripheral = null;
        return true;
    }

    public boolean hasPeripheral() {
        return this.m_peripheral != null;
    }

    public IPeripheral getPeripheral() {
        return this.m_peripheral;
    }

    public String getType() {
        return this.m_type;
    }

    public int getId() {
        return this.m_id;
    }

    public String getConnectedName() {
        return this.m_peripheral != null ? this.m_type + "_" + this.m_id : null;
    }

    public void extendMap(Map<String, IPeripheral> peripherals) {
        if (this.m_peripheral != null) {
            peripherals.put(this.m_type + "_" + this.m_id, this.m_peripheral);
        }
    }

    public void write(NBTTagCompound tag, String suffix) {
        if (this.m_id >= 0) {
            tag.setInteger(NBT_PERIPHERAL_ID + suffix, this.m_id);
        }
        if (this.m_type != null) {
            tag.setString(NBT_PERIPHERAL_TYPE + suffix, this.m_type);
        }
    }

    public void read(NBTTagCompound tag, String suffix) {
        this.m_id = tag.hasKey(NBT_PERIPHERAL_ID + suffix) ? tag.getInteger(NBT_PERIPHERAL_ID + suffix) : -1;
        this.m_type = tag.hasKey(NBT_PERIPHERAL_TYPE + suffix) ? tag.getString(NBT_PERIPHERAL_TYPE + suffix) : null;
        this.m_peripheral = null;
    }

    protected int assignId(World world, String type) {
        return IDAssigner
            .getNextIDFromFile(new File(ComputerCraft.getWorldDir(world), "computer/lastid_" + type + ".txt"));
    }

    interface IdAllocator {

        int getNextId(String type);
    }
}
