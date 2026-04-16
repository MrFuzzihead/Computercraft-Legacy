package dan200.computercraft.shared.peripheral.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

/**
 * Minimal {@link TileEntity} + {@link IInventory} stub for unit testing
 * {@link InventoryPeripheral} without a live Minecraft environment.
 *
 * <p>
 * {@code worldObj} is intentionally left {@code null} so that
 * {@link InventoryPeripheral} falls back to returning {@code this}
 * directly when resolving the inventory.
 * </p>
 */
class StubInventoryTile extends TileEntity implements IInventory {

    private final ItemStack[] m_slots;
    private int m_stackLimit = 64;

    StubInventoryTile(int size) {
        m_slots = new ItemStack[size];
    }

    /** Sets the stack in a 0-indexed slot (for test setup). */
    void setSlot(int index, ItemStack stack) {
        m_slots[index] = stack;
    }

    /** Overrides the inventory stack limit returned by {@link #getInventoryStackLimit()}. */
    void setStackLimit(int limit) {
        m_stackLimit = limit;
    }

    // =========================================================================
    // IInventory
    // =========================================================================

    @Override
    public int getSizeInventory() {
        return m_slots.length;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return m_slots[index];
    }

    @Override
    public ItemStack decrStackSize(int index, int amount) {
        ItemStack stack = m_slots[index];
        if (stack == null) return null;
        if (stack.stackSize <= amount) {
            m_slots[index] = null;
            return stack;
        }
        ItemStack split = stack.splitStack(amount);
        if (stack.stackSize == 0) m_slots[index] = null;
        return split;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int index) {
        ItemStack stack = m_slots[index];
        m_slots[index] = null;
        return stack;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        m_slots[index] = stack;
    }

    @Override
    public String getInventoryName() {
        return "stub";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return m_stackLimit;
    }

    @Override
    public void markDirty() {}

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return true;
    }
}
