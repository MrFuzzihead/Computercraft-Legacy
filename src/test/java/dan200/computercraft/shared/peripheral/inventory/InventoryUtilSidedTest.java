package dan200.computercraft.shared.peripheral.inventory;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

import dan200.computercraft.shared.util.InventoryUtil;

/**
 * Tests for ISidedInventory behaviour exercised via InventoryUtil's sided store/take APIs.
 */
class InventoryUtilSidedTest {

    /**
     * Minimal sided inventory where each side indexes a single slot (side 0 -> slot0, side1->slot1, ...).
     * Insertion/extraction is only allowed when the side matches the slot index.
     */
    static class StubSidedInventoryTile extends StubInventoryTile implements ISidedInventory {

        StubSidedInventoryTile(int size) {
            super(size);
        }

        @Override
        public int[] getAccessibleSlotsFromSide(int side) {
            if (side >= 0 && side < getSizeInventory()) {
                return new int[] { side };
            }
            int[] slots = new int[getSizeInventory()];
            for (int i = 0; i < slots.length; i++) slots[i] = i;
            return slots;
        }

        @Override
        public boolean canInsertItem(int index, ItemStack stack, int side) {
            // only allow insert if the side targets this slot
            return side >= 0 && side == index;
        }

        @Override
        public boolean canExtractItem(int index, ItemStack stack, int side) {
            // only allow extract if the side targets this slot
            return side >= 0 && side == index;
        }
    }

    @Test
    void storeItemsRespectsAccessibleSlotsAndCanInsert() {
        StubSidedInventoryTile inv = new StubSidedInventoryTile(3);

        // Try to insert 5 items from the north (side=1) — should only be placed into slot 1.
        ItemStack stack = new ItemStack((net.minecraft.item.Item) null, 5, 0);
        ItemStack remainder = InventoryUtil.storeItems(stack, inv, 1);

        assertNull(remainder, "All items should be stored into the accessible slot");
        assertNull(inv.getStackInSlot(0));
        assertNotNull(inv.getStackInSlot(1));
        assertEquals(5, inv.getStackInSlot(1).stackSize);
        assertNull(inv.getStackInSlot(2));
    }

    @Test
    void storeItemsRespectsCanInsertBlocking() {
        StubSidedInventoryTile inv = new StubSidedInventoryTile(3);

        // Use a side that maps to slot 5 (out of range) so getAccessibleSlotsFromSide returns all slots,
        // but canInsertItem will only allow insertion when side==index, which will be false for all slots.
        ItemStack stack = new ItemStack((net.minecraft.item.Item) null, 5, 0);
        ItemStack remainder = InventoryUtil.storeItems(stack, inv, 5);

        // No insertion should have taken place
        assertNotNull(remainder);
        assertEquals(5, remainder.stackSize);
        assertNull(inv.getStackInSlot(0));
        assertNull(inv.getStackInSlot(1));
        assertNull(inv.getStackInSlot(2));
    }

    @Test
    void takeItemsRespectsAccessibleSlotsAndCanExtract() {
        StubSidedInventoryTile inv = new StubSidedInventoryTile(3);
        // Place 7 items into slot 2 (use a real Item to avoid headless NPEs in takeItems)
        inv.setSlot(2, new ItemStack(new net.minecraft.item.Item(), 7, 0));

        // Attempt to take 4 items from side=2 — should succeed and return a stack of 4
        ItemStack taken = InventoryUtil.takeItems(4, inv, 2);
        assertNotNull(taken);
        assertEquals(4, taken.stackSize);

        // Remaining in slot 2 should be 3
        ItemStack remaining = inv.getStackInSlot(2);
        assertNotNull(remaining);
        assertEquals(3, remaining.stackSize);
    }
}
