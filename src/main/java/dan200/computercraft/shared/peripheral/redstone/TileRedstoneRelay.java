package dan200.computercraft.shared.peripheral.redstone;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.common.TileGeneric;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.IPeripheralTile;
import dan200.computercraft.shared.util.DirectionUtil;
import dan200.computercraft.shared.util.RedstoneUtil;

/**
 * Tile entity for the Redstone Relay block. Exposes the full redstone API
 * surface as an {@link IPeripheral} so computers can interact with redstone
 * via wired or wireless modems without being adjacent to the circuit.
 */
public class TileRedstoneRelay extends TileGeneric implements IPeripheralTile {

    // Icon indices: 0=top, 1=side, 2=front, 3=bottom
    private static final IIcon[] s_icons = new IIcon[4];

    /** Analog output levels per local side (0–15). Persisted in NBT. */
    private final int[] m_output = new int[6];

    /** Bundled cable output bitmasks per local side. Persisted in NBT. */
    private final int[] m_bundledOutput = new int[6];

    /** Cached analog input levels per local side (transient). */
    private final int[] m_input = new int[6];

    /** Cached bundled cable input bitmasks per local side (transient). */
    private final int[] m_bundledInput = new int[6];

    /** Facing direction (Minecraft Facing convention, 2–5). */
    private int m_dir = 2;

    /** Computers attached via modem; receives "redstone" events on input change. */
    private final Set<IComputerAccess> m_computers = new HashSet<>();

    /** Set on neighbour change; processed in updateEntity on the next server tick. */
    private boolean m_inputDirty = true;

    /** Cached peripheral instance (one-per-tile singleton). */
    private RedstoneRelayPeripheral m_peripheral = null;

    // =========================================================================
    // Icon registration
    // =========================================================================

    @SideOnly(Side.CLIENT)
    public static void registerIcons(IIconRegister iconRegister) {
        s_icons[0] = iconRegister.registerIcon("computercraft:redstoneRelayTop");
        s_icons[1] = iconRegister.registerIcon("computercraft:redstoneRelaySide");
        s_icons[2] = iconRegister.registerIcon("computercraft:redstoneRelayFront");
        s_icons[3] = iconRegister.registerIcon("computercraft:redstoneRelayBottom");
    }

    /** Returns the icon for an item-stack rendering (assumes front faces east, direction=5). */
    public static IIcon getItemTexture(int side) {
        switch (side) {
            case 0:
                return s_icons[3]; // bottom
            case 1:
                return s_icons[0]; // top
            case 4:
                return s_icons[2]; // front (east — appears on the left in the isometric inventory view)
            default:
                return s_icons[1]; // side
        }
    }

    // =========================================================================
    // TileGeneric — texture
    // =========================================================================

    @Override
    public IIcon getTexture(int worldSide) {
        switch (worldSide) {
            case 0:
                return s_icons[3]; // bottom
            case 1:
                return s_icons[0]; // top
            default:
                return worldSide == m_dir ? s_icons[2] : s_icons[1];
        }
    }

    // =========================================================================
    // IDirectionalTile (required by IPeripheralTile)
    // =========================================================================

    @Override
    public int getDirection() {
        return m_dir;
    }

    @Override
    public void setDirection(int dir) {
        if (dir < 2 || dir >= 6) {
            dir = 2;
        }
        m_dir = dir;
    }

    // =========================================================================
    // IPeripheralTile
    // =========================================================================

    @Override
    public PeripheralType getPeripheralType() {
        return PeripheralType.RedstoneRelay;
    }

    @Override
    public synchronized IPeripheral getPeripheral(int side) {
        if (m_peripheral == null) {
            m_peripheral = new RedstoneRelayPeripheral(this);
        }
        return m_peripheral;
    }

    @Override
    public String getLabel() {
        return null;
    }

    // =========================================================================
    // TileGeneric — redstone connectivity / output
    // =========================================================================

    @Override
    public boolean getRedstoneConnectivity(int worldSide) {
        return true;
    }

    @Override
    public int getRedstoneOutput(int worldSide) {
        return m_output[DirectionUtil.toLocal(this, worldSide)];
    }

    @Override
    public boolean getBundledRedstoneConnectivity(int worldSide) {
        return true;
    }

    @Override
    public int getBundledRedstoneOutput(int worldSide) {
        return m_bundledOutput[DirectionUtil.toLocal(this, worldSide)];
    }

    // =========================================================================
    // Output setters (called from RedstoneRelayPeripheral.callMethod)
    // =========================================================================

    public synchronized void setOutput(int localSide, int level) {
        if (m_output[localSide] != level) {
            m_output[localSide] = level;
            if (worldObj != null && !worldObj.isRemote) {
                RedstoneUtil.propogateRedstoneOutput(worldObj, xCoord, yCoord, zCoord, localToWorldSide(localSide));
            }
            markDirty();
        }
    }

    public synchronized void setBundledOutput(int localSide, int mask) {
        if (m_bundledOutput[localSide] != mask) {
            m_bundledOutput[localSide] = mask;
            if (worldObj != null && !worldObj.isRemote) {
                RedstoneUtil.propogateRedstoneOutput(worldObj, xCoord, yCoord, zCoord, localToWorldSide(localSide));
            }
            markDirty();
        }
    }

    // =========================================================================
    // Input getters (called from RedstoneRelayPeripheral.callMethod)
    // =========================================================================

    public synchronized int getOutput(int localSide) {
        return m_output[localSide];
    }

    public synchronized int getBundledOutput(int localSide) {
        return m_bundledOutput[localSide];
    }

    public synchronized int getInput(int localSide) {
        return m_input[localSide];
    }

    public synchronized int getBundledInput(int localSide) {
        return m_bundledInput[localSide];
    }

    // =========================================================================
    // Computer management
    // =========================================================================

    public synchronized void attachComputer(IComputerAccess computer) {
        m_computers.add(computer);
    }

    public synchronized void detachComputer(IComputerAccess computer) {
        m_computers.remove(computer);
    }

    // =========================================================================
    // TileEntity lifecycle
    // =========================================================================

    @Override
    public void onNeighbourChange() {
        m_inputDirty = true;
    }

    @Override
    public void updateEntity() {
        if (!worldObj.isRemote && m_inputDirty) {
            m_inputDirty = false;
            updateInput();
        }
    }

    @Override
    public void destroy() {
        if (!worldObj.isRemote) {
            for (int worldDir = 0; worldDir < 6; worldDir++) {
                RedstoneUtil.propogateRedstoneOutput(worldObj, xCoord, yCoord, zCoord, worldDir);
            }
        }
    }

    // =========================================================================
    // NBT
    // =========================================================================

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("dir", m_dir);
        tag.setIntArray("output", m_output);
        tag.setIntArray("bundledOutput", m_bundledOutput);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("dir")) {
            m_dir = tag.getInteger("dir");
        }
        if (tag.hasKey("output")) {
            int[] saved = tag.getIntArray("output");
            if (saved.length == 6) {
                System.arraycopy(saved, 0, m_output, 0, 6);
            }
        }
        if (tag.hasKey("bundledOutput")) {
            int[] saved = tag.getIntArray("bundledOutput");
            if (saved.length == 6) {
                System.arraycopy(saved, 0, m_bundledOutput, 0, 6);
            }
        }
    }

    @Override
    protected void writeDescription(NBTTagCompound tag) {
        super.writeDescription(tag);
        tag.setInteger("dir", m_dir);
    }

    @Override
    protected void readDescription(NBTTagCompound tag) {
        super.readDescription(tag);
        if (tag.hasKey("dir")) {
            m_dir = tag.getInteger("dir");
        }
    }

    // =========================================================================
    // Item drops
    // =========================================================================

    @Override
    public void getDroppedItems(List<ItemStack> drops, int fortune, boolean creative, boolean silkTouch) {
        if (!creative) {
            drops.add(new ItemStack(ComputerCraft.Blocks.redstoneRelay));
        }
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(ComputerCraft.Blocks.redstoneRelay);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void updateInput() {
        boolean changed = false;
        for (int worldDir = 0; worldDir < 6; worldDir++) {
            int localDir = DirectionUtil.toLocal(this, worldDir);
            int offsetX = xCoord + Facing.offsetsXForSide[worldDir];
            int offsetY = yCoord + Facing.offsetsYForSide[worldDir];
            int offsetZ = zCoord + Facing.offsetsZForSide[worldDir];
            int offsetSide = Facing.oppositeSide[worldDir];

            int newInput = RedstoneUtil.getRedstoneOutput(worldObj, offsetX, offsetY, offsetZ, offsetSide);
            int newBundled = RedstoneUtil.getBundledRedstoneOutput(worldObj, offsetX, offsetY, offsetZ, offsetSide);

            synchronized (this) {
                if (newInput != m_input[localDir] || newBundled != m_bundledInput[localDir]) {
                    m_input[localDir] = newInput;
                    m_bundledInput[localDir] = newBundled;
                    changed = true;
                }
            }
        }

        if (changed) {
            Set<IComputerAccess> computers;
            synchronized (this) {
                computers = new HashSet<>(m_computers);
            }
            for (IComputerAccess computer : computers) {
                computer.queueEvent("redstone", new Object[0]);
            }
        }
    }

    /**
     * Converts a local side index (0–5 in {@code s_sideNames} order) back to a
     * world-space Facing direction. Faces 0 (down) and 1 (up) are never rotated.
     */
    private int localToWorldSide(int localSide) {
        if (localSide < 2) {
            return localSide;
        }
        for (int worldDir = 2; worldDir < 6; worldDir++) {
            if (DirectionUtil.toLocal(this, worldDir) == localSide) {
                return worldDir;
            }
        }
        return localSide;
    }
}
