package dan200.computercraft.testsupport;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/**
 * A plain array-backed {@link IInventory} for unit tests.
 *
 * <p>
 * Deliberately not a {@code TileEntity}: the turtle and refuel code paths take an {@code IInventory} and never look at
 * the tile. {@link #setStack} and {@link #count} keep the tests readable, and {@link #dirtyCalls} lets a test prove
 * that
 * a refused refuel did not touch the inventory.
 * </p>
 */
public final class StubInventory implements IInventory {

    private final ItemStack[] slots;
    private int dirtyCalls;

    public StubInventory(int size) {
        this.slots = new ItemStack[size];
    }

    /** Places a stack in a slot, replacing whatever was there. */
    public StubInventory setStack(int slot, ItemStack stack) {
        this.slots[slot] = stack;
        return this;
    }

    /** The stack size in a slot, or zero when it is empty. */
    public int count(int slot) {
        ItemStack stack = this.slots[slot];
        return stack == null ? 0 : stack.stackSize;
    }

    /** How many times {@link #markDirty()} has been called. */
    public int dirtyCalls() {
        return this.dirtyCalls;
    }

    @Override
    public int getSizeInventory() {
        return this.slots.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return this.slots[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int count) {
        ItemStack stack = this.slots[slot];
        if (stack == null) {
            return null;
        }

        if (stack.stackSize <= count) {
            this.slots[slot] = null;
            return stack;
        }

        return stack.splitStack(count);
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        ItemStack stack = this.slots[slot];
        this.slots[slot] = null;
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        this.slots[slot] = stack;
    }

    @Override
    public String getInventoryName() {
        return "computercraft:test_inventory";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void markDirty() {
        this.dirtyCalls++;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return true;
    }
}
