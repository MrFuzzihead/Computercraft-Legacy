package dan200.computercraft.shared.peripheral.modem;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.peripheral.generic.InventoryPeripheral;
import dan200.computercraft.shared.util.IDAssigner;
import dan200.computercraft.shared.util.PeripheralUtil;

/**
 * Tile entity for {@link BlockWiredModemFull}.
 *
 * <p>Extends {@link TileCable} and inherits all cable-network logic. Key
 * differences from the face-attached Wired Modem:</p>
 * <ul>
 *   <li>No support check on neighbours — a full block needs no face to attach to.</li>
 *   <li>Solid on all six sides; occupies a full 1×1×1 bounding box.</li>
 *   <li>All six faces render the {@code wiredModemFace*} texture.</li>
 *   <li>Right-clicking <em>any</em> face connects <em>all</em> peripherals adjacent
 *       on every face simultaneously (acts as a peripheral hub).</li>
 *   <li>Each connected face receives a stable peripheral ID persisted in NBT.</li>
 *   <li>Drops one {@link BlockWiredModemFull} item when broken.</li>
 * </ul>
 */
public class TileWiredModemFull extends TileCable {

    /**
     * Stable per-side peripheral IDs so that peripheral names survive chunk
     * unload/reload. Keyed by Facing side index (0-5).
     */
    private final Map<Integer, Integer> m_sidePeripheralIDs = new HashMap<>();

    /**
     * True until the first server-side tick after placement. Used to trigger a
     * one-shot {@link #networkChanged()} so that all already-existing cable/modem
     * nodes adjacent to this block re-scan and discover it (fixes issue #2).
     */
    private boolean m_needsNetworkInit = true;

    // -------------------------------------------------------------------------
    // Geometry / rendering
    // -------------------------------------------------------------------------

    @Override
    public boolean isSolidOnSide(int side) {
        return true;
    }

    @Override
    public AxisAlignedBB getBounds() {
        return AxisAlignedBB.getBoundingBox(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
    }

    @Override
    public void getCollisionBounds(List<AxisAlignedBB> bounds) {
        bounds.add(AxisAlignedBB.getBoundingBox(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
    }

    @Override
    public IIcon getTexture(int side) {
        return TileCable.getModemFaceIcon(this.getAnim());
    }

    // -------------------------------------------------------------------------
    // Direction — irrelevant for a full block; use a fixed sentinel value
    // -------------------------------------------------------------------------

    @Override
    public int getDirection() {
        return 2;
    }

    @Override
    public void setDirection(int dir) {
        // No-op: full block has no orientation.
    }

    // -------------------------------------------------------------------------
    // IPeripheral exposure — expose the modem on ALL sides
    // -------------------------------------------------------------------------

    @Override
    public IPeripheral getPeripheral(int side) {
        return this.m_modem;
    }

    // -------------------------------------------------------------------------
    // Neighbour change — notify the network so adjacent peripheral changes
    // (e.g. placing/breaking a chest) trigger a re-scan (fixes issue #1)
    // -------------------------------------------------------------------------

    @Override
    public void onNeighbourChange() {
        // Full blocks are self-supporting, so we do NOT call super (which would
        // try to break us if the supporting face is gone). We do need to tell
        // the cable network that something nearby changed.
        if (this.worldObj != null && !this.worldObj.isRemote) {
            this.networkChanged();
        }
    }

    // -------------------------------------------------------------------------
    // First-tick network init — triggers a networkChanged() once after
    // placement so already-existing cable nodes re-scan and discover us
    // (fixes issue #2)
    // -------------------------------------------------------------------------

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (!this.worldObj.isRemote && this.m_needsNetworkInit) {
            this.m_needsNetworkInit = false;
            this.networkChanged();
        }
    }

    // -------------------------------------------------------------------------
    // Peripheral hub — expose own adjacent peripherals as origin (fixes issue #3)
    // -------------------------------------------------------------------------

    @Override
    protected void collectLocalPeripherals(Map<String, IPeripheral> map) {
        this.collectConnectedPeripherals(map);
    }

    // -------------------------------------------------------------------------
    // Peripheral hub — connect every adjacent peripheral on all 6 faces
    // -------------------------------------------------------------------------

    @Override
    protected boolean canExposePeripheral() {
        return true;
    }

    /**
     * Returns the first peripheral found on any adjacent face.
     * Used by {@link #togglePeripheralAccess()} to decide whether enabling
     * access makes sense.
     */
    @Override
    protected IPeripheral getConnectedPeripheral() {
        if (!this.m_peripheralAccessAllowed) return null;
        for (int side = 0; side < 6; side++) {
            IPeripheral p = this.getPeripheralOnSide(side);
            if (p != null) return p;
        }
        return null;
    }

    /**
     * Adds all adjacent peripherals (one per face, if present) into {@code map}
     * with stable names derived from per-side IDs.
     */
    @Override
    protected void collectConnectedPeripherals(Map<String, IPeripheral> map) {
        if (!this.m_peripheralAccessAllowed) return;
        for (int side = 0; side < 6; side++) {
            IPeripheral peripheral = this.getPeripheralOnSide(side);
            if (peripheral != null) {
                String type = peripheral.getType();
                // Use only the first type segment for the peripheral name prefix.
                String baseType = type.contains(";") ? type.substring(0, type.indexOf(';')) : type;
                int id = this.getOrAssignSideID(side, baseType);
                map.put(baseType + "_" + id, peripheral);
            }
        }
    }

    /** Retrieves the peripheral on the given face, including IInventory fallback. */
    private IPeripheral getPeripheralOnSide(int side) {
        int x = this.xCoord + Facing.offsetsXForSide[side];
        int y = this.yCoord + Facing.offsetsYForSide[side];
        int z = this.zCoord + Facing.offsetsZForSide[side];
        IPeripheral peripheral = PeripheralUtil.getPeripheral(this.worldObj, x, y, z, Facing.oppositeSide[side]);
        if (peripheral == null && y >= 0 && y < this.worldObj.getHeight() && !this.worldObj.isRemote) {
            TileEntity te = this.worldObj.getTileEntity(x, y, z);
            if (te instanceof IInventory) {
                peripheral = new InventoryPeripheral((IInventory) te);
            }
        }
        return peripheral;
    }

    /**
     * Returns the stable ID for a given face, assigning a new one from the
     * world's ID file if none has been assigned yet.
     */
    private int getOrAssignSideID(int side, String type) {
        Integer id = this.m_sidePeripheralIDs.get(side);
        if (id == null || id < 0) {
            id = IDAssigner.getNextIDFromFile(
                new File(ComputerCraft.getWorldDir(this.worldObj), "computer/lastid_" + type + ".txt"));
            this.m_sidePeripheralIDs.put(side, id);
        }
        return id;
    }

    // -------------------------------------------------------------------------
    // Right-click — toggle ALL faces at once; report each change
    // -------------------------------------------------------------------------

    @Override
    public boolean onActivate(EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) {
            if (this.worldObj.isRemote) return true;

            // Snapshot the current set of connected peripheral names before toggle.
            Map<String, IPeripheral> before = new HashMap<>();
            this.collectConnectedPeripherals(before);

            this.togglePeripheralAccess();

            // Snapshot after toggle.
            Map<String, IPeripheral> after = new HashMap<>();
            this.collectConnectedPeripherals(after);

            // Report each disconnect and each new connect.
            for (String name : before.keySet()) {
                if (!after.containsKey(name)) {
                    player.addChatMessage(new ChatComponentTranslation(
                        "gui.computercraft:wired_modem.peripheral_disconnected",
                        new Object[] { name }));
                }
            }
            for (String name : after.keySet()) {
                if (!before.containsKey(name)) {
                    player.addChatMessage(new ChatComponentTranslation(
                        "gui.computercraft:wired_modem.peripheral_connected",
                        new Object[] { name }));
                }
            }

            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Drops / pick-block
    // -------------------------------------------------------------------------

    @Override
    public void getDroppedItems(List<ItemStack> drops, int fortune, boolean creative, boolean silkTouch) {
        if (!creative) {
            drops.add(new ItemStack(ComputerCraft.Blocks.wiredModemFull, 1, 0));
        }
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(ComputerCraft.Blocks.wiredModemFull, 1, 0);
    }

    // -------------------------------------------------------------------------
    // NBT
    // -------------------------------------------------------------------------

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.m_sidePeripheralIDs.clear();
        if (tag.hasKey("sideIDs")) {
            NBTTagCompound ids = tag.getCompoundTag("sideIDs");
            for (int s = 0; s < 6; s++) {
                String key = "s" + s;
                if (ids.hasKey(key)) {
                    this.m_sidePeripheralIDs.put(s, ids.getInteger(key));
                }
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagCompound ids = new NBTTagCompound();
        for (Map.Entry<Integer, Integer> entry : this.m_sidePeripheralIDs.entrySet()) {
            ids.setInteger("s" + entry.getKey(), entry.getValue());
        }
        tag.setTag("sideIDs", ids);
    }
}















