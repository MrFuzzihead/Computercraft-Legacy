package dan200.computercraft.shared.peripheral.modem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.peripheral.PeripheralType;

/**
 * The tile entity for {@link BlockWiredModemFull}.
 *
 * <p>
 * It is a wired modem on all six faces. Every face has its own modem peripheral and its own local peripheral, but all
 * six share one {@link ModemState}, one wired network node and one set of remote peripheral wrappers. Right-clicking
 * the block toggles all six local peripherals at once.
 * </p>
 */
public class TileWiredModemFull extends TileCable {

    private static final int ALL_SIDES = 0x3F;

    private final ModemState m_state = new ModemState();
    private final TileCable.Peripheral[] m_modems = new TileCable.Peripheral[6];
    private final WiredModemLocalPeripheral[] m_peripherals = new WiredModemLocalPeripheral[6];

    private boolean m_peripheralEnabled = false;
    private int m_invalidSides = 0;
    private boolean m_needsNetworkInit = true;

    public TileWiredModemFull() {
        super();

        for (int side = 0; side < 6; side++) {
            this.m_peripherals[side] = new WiredModemLocalPeripheral();
        }

        // getModem() lazily calls createPeripheral(); by now m_state is initialised.
        this.m_modems[0] = (TileCable.Peripheral) this.getModem();
        for (int side = 1; side < 6; side++) {
            this.m_modems[side] = new TileCable.Peripheral(this, side, this.m_state);
        }
    }

    @Override
    protected ModemState getModemState() {
        return this.m_state;
    }

    @Override
    protected ModemPeripheral createPeripheral() {
        return new TileCable.Peripheral(this, 0, this.m_state);
    }

    @Override
    public PeripheralType getPeripheralType() {
        return PeripheralType.WiredModemWithCable;
    }

    // -------------------------------------------------------------------------
    // Geometry and rendering
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
        bounds.add(this.getBounds());
    }

    @Override
    public int getDirection() {
        return 2;
    }

    @Override
    public void setDirection(int dir) {}

    @Override
    public IIcon getTexture(int side) {
        return TileCable.getModemFaceTexture(this.getAnim());
    }

    @Override
    protected void updateAnim() {
        int anim = this.m_state.isOpen() ? 1 : 0;
        if (this.hasAnyPeripheral()) {
            anim += 2;
        }
        this.setAnim(anim);
    }

    // -------------------------------------------------------------------------
    // Peripherals
    // -------------------------------------------------------------------------

    @Override
    public IPeripheral getPeripheral(int side) {
        return side >= 0 && side < 6 ? this.m_modems[side] : null;
    }

    @Override
    protected boolean canExposePeripheral() {
        return true;
    }

    @Override
    protected void collectLocalPeripherals(Map<String, IPeripheral> peripherals) {
        for (WiredModemLocalPeripheral peripheral : this.m_peripherals) {
            peripheral.extendMap(peripherals);
        }
    }

    @Override
    protected String getLocalPeripheralNameForSide(int side) {
        return side >= 0 && side < 6 ? this.m_peripherals[side].getConnectedName() : null;
    }

    @Override
    public String getConnectedPeripheralName() {
        for (WiredModemLocalPeripheral peripheral : this.m_peripherals) {
            String name = peripheral.getConnectedName();
            if (name != null) {
                return name;
            }
        }
        return null;
    }

    private boolean hasAnyPeripheral() {
        for (WiredModemLocalPeripheral peripheral : this.m_peripherals) {
            if (peripheral.hasPeripheral()) {
                return true;
            }
        }
        return false;
    }

    boolean isPeripheralEnabled() {
        return this.m_peripheralEnabled;
    }

    WiredModemLocalPeripheral getLocalPeripheral(int side) {
        return side >= 0 && side < 6 ? this.m_peripherals[side] : null;
    }

    private boolean isPeripheralOn() {
        return this.m_peripheralEnabled;
    }

    private boolean attachPeripherals(int sides) {
        boolean changed = false;
        for (int side = 0; side < 6; side++) {
            if ((sides & (1 << side)) == 0) continue;

            changed |= this.m_peripherals[side].attach(
                this.worldObj,
                this.xCoord + Facing.offsetsXForSide[side],
                this.yCoord + Facing.offsetsYForSide[side],
                this.zCoord + Facing.offsetsZForSide[side],
                Facing.oppositeSide[side]);
        }

        if (changed) {
            this.updateAnim();
            this.networkChanged();
        }
        return changed;
    }

    private boolean detachPeripherals() {
        boolean changed = false;
        for (WiredModemLocalPeripheral peripheral : this.m_peripherals) {
            changed |= peripheral.detach();
        }

        if (changed) {
            this.updateAnim();
            this.networkChanged();
        }
        return changed;
    }

    private List<String> getConnectedPeripheralNames() {
        List<String> names = new ArrayList<>(6);
        for (WiredModemLocalPeripheral peripheral : this.m_peripherals) {
            String name = peripheral.getConnectedName();
            if (name != null) {
                names.add(name);
            }
        }
        Collections.sort(names);
        return names;
    }

    private static List<String> difference(List<String> left, List<String> right) {
        List<String> result = new ArrayList<>();
        for (String name : left) {
            if (!right.contains(name)) {
                result.add(name);
            }
        }
        return result;
    }

    private static void sendPeripheralChanges(EntityPlayer player, String key, List<String> names) {
        if (names.isEmpty()) {
            return;
        }

        player.addChatMessage(new ChatComponentTranslation(key, new Object[] { String.join(", ", names) }));
    }

    @Override
    public boolean onActivate(EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (player.isSneaking() || !player.capabilities.allowEdit) {
            return false;
        }

        if (this.worldObj.isRemote) {
            return true;
        }

        List<String> oldNames = this.getConnectedPeripheralNames();
        if (this.isPeripheralOn()) {
            this.detachPeripherals();
            this.m_peripheralEnabled = false;
        } else {
            this.attachPeripherals(ALL_SIDES);
            this.m_peripheralEnabled = this.hasAnyPeripheral();
        }

        List<String> newNames = this.getConnectedPeripheralNames();
        if (!oldNames.equals(newNames)) {
            sendPeripheralChanges(
                player,
                "gui.computercraft:wired_modem.peripheral_disconnected",
                difference(oldNames, newNames));
            sendPeripheralChanges(
                player,
                "gui.computercraft:wired_modem.peripheral_connected",
                difference(newNames, oldNames));
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Ticking and neighbour changes
    // -------------------------------------------------------------------------

    @Override
    public void onNeighbourChange() {
        // A full block is self-supporting; unlike a wall modem it must never pop off. 1.7.10 does not tell us which
        // neighbour changed, so re-resolve all six sides on the next tick.
        if (!this.worldObj.isRemote) {
            this.m_invalidSides = ALL_SIDES;
            this.networkChanged();
        }
    }

    @Override
    public void updateEntity() {
        if (!this.worldObj.isRemote) {
            if (this.m_needsNetworkInit) {
                this.m_needsNetworkInit = false;
                this.networkChanged();
            }

            if (this.m_invalidSides != 0) {
                int sides = this.m_invalidSides;
                this.m_invalidSides = 0;
                if (this.m_peripheralEnabled) {
                    this.attachPeripherals(sides);
                    if (!this.hasAnyPeripheral()) {
                        this.m_peripheralEnabled = false;
                        this.updateAnim();
                    }
                }
            }
        }

        super.updateEntity();
    }

    // -------------------------------------------------------------------------
    // Drops
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
        this.m_peripheralEnabled = tag.getBoolean("PeripheralEnabled");
        for (int side = 0; side < 6; side++) {
            this.m_peripherals[side].read(tag, Integer.toString(side));
        }

        if (this.m_peripheralEnabled) {
            this.m_invalidSides = ALL_SIDES;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setBoolean("PeripheralEnabled", this.m_peripheralEnabled);
        for (int side = 0; side < 6; side++) {
            this.m_peripherals[side].write(tag, Integer.toString(side));
        }
        super.writeToNBT(tag);
    }

    @Override
    public void destroy() {
        for (TileCable.Peripheral modem : this.m_modems) {
            if (modem != null) {
                modem.destroy();
            }
        }
        this.m_modem = null;
        super.destroy();
    }
}
