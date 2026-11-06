package dan200.computercraft.shared.pocket;

import net.minecraft.entity.Entity;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.api.pocket.IPocketUpgrade;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.pocket.items.ItemPocketComputer;

/**
 * The {@link IPocketAccess} handed to upgrades for one callback.
 *
 * <p>
 * A fresh instance is built for every {@code createPeripheral}/{@code update} call rather than kept alive per computer:
 * the holding entity and stack change as the item moves, and the only state an upgrade can own is in the stack's NBT
 * (see {@link #getUpgradeNBTData()}), which survives the instance going away.
 * </p>
 */
public final class PocketAccess implements IPocketAccess {

    /** The key the installed upgrade's data lives under, on the pocket computer's own stack. */
    private static final String NBT_UPGRADE_DATA = "upgradeData";

    private final ServerComputer m_computer;
    private final IInventory m_inventory;
    private final ItemStack m_stack;
    private final Entity m_entity;

    public PocketAccess(ServerComputer computer, IInventory inventory, ItemStack stack, Entity entity) {
        this.m_computer = computer;
        this.m_inventory = inventory;
        this.m_stack = stack;
        this.m_entity = entity;
    }

    /**
     * The computer this access belongs to.
     *
     * <p>
     * Not part of the API: the in-tree upgrades need the computer's user data (the modem light flag lives there) and
     * nothing outside this mod can reach a {@link ServerComputer} anyway.
     * </p>
     *
     * @return the server computer
     */
    public ServerComputer getComputer() {
        return this.m_computer;
    }

    @Override
    public World getWorld() {
        return this.m_computer.getWorld();
    }

    @Override
    public Entity getEntity() {
        return this.m_entity;
    }

    @Override
    public ItemStack getStack() {
        return this.m_stack;
    }

    @Override
    public NBTTagCompound getUpgradeNBTData() {
        NBTTagCompound tag = this.m_stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            this.m_stack.setTagCompound(tag);
        }

        if (!tag.hasKey(NBT_UPGRADE_DATA, Constants.NBT.TAG_COMPOUND)) {
            tag.setTag(NBT_UPGRADE_DATA, new NBTTagCompound());
        }

        return tag.getCompoundTag(NBT_UPGRADE_DATA);
    }

    @Override
    public void updateUpgradeNBTData() {
        if (this.m_inventory != null) {
            this.m_inventory.markDirty();
        }
    }

    @Override
    public void invalidatePeripheral() {
        IPocketUpgrade upgrade = PocketUpgrades.getUpgrade(getInstalledUpgradeID());
        this.m_computer.setPeripheral(
            2,
            upgrade == null ? null
                : upgrade.createPeripheral(
                    new PocketAccess(this.m_computer, this.m_inventory, this.m_stack, this.m_entity)));
    }

    private int getInstalledUpgradeID() {
        if (this.m_stack == null || !(this.m_stack.getItem() instanceof ItemPocketComputer)) {
            return -1;
        }

        return ((ItemPocketComputer) this.m_stack.getItem()).getUpgrade(this.m_stack);
    }
}
