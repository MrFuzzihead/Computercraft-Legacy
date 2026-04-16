package dan200.computercraft.shared.peripheral.inventory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;

import org.junit.jupiter.api.Test;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

/**
 * Unit tests for {@link InventoryPeripheral}.
 *
 * <h2>Scope</h2>
 * <p>
 * Tests that can run without a live Minecraft environment — method-surface
 * verification, stub-inventory logic for {@code size}, {@code list},
 * {@code getItemDetail}, {@code getItemLimit}, and error-path tests for
 * {@code pushItems} and {@code pullItems}.
 * </p>
 *
 * <p>
 * Tests requiring {@code Item.itemRegistry} (list with occupied slots,
 * {@code getItemDetail} for a non-empty slot, successful
 * {@code pushItems}/{@code pullItems} transfers) are covered by the in-game
 * script {@code run/saves/Test World/computer/37/test_inventory.lua}.
 * </p>
 *
 * <h2>Threading note</h2>
 * <p>
 * All six methods use {@link ILuaContext#executeMainThreadTask}. The
 * {@link #SYNC_CONTEXT} stub executes tasks synchronously on the calling
 * thread, which is safe for the paths tested here.
 * </p>
 */
class InventoryPeripheralTest {

    // =========================================================================
    // Constants — must match the order in InventoryPeripheral#getMethodNames()
    // =========================================================================

    private static final int METHOD_SIZE = 0;
    private static final int METHOD_LIST = 1;
    private static final int METHOD_GET_ITEM_DETAIL = 2;
    private static final int METHOD_GET_ITEM_LIMIT = 3;
    private static final int METHOD_PUSH_ITEMS = 4;
    private static final int METHOD_PULL_ITEMS = 5;

    // =========================================================================
    // Stubs
    // =========================================================================

    /**
     * A minimal {@link ILuaContext} that runs {@link ILuaTask} callbacks
     * synchronously so tests do not need a real Minecraft main thread.
     */
    private static final ILuaContext SYNC_CONTEXT = new ILuaContext() {

        @Override
        public Object[] executeMainThreadTask(ILuaTask task) throws LuaException, InterruptedException {
            return task.execute();
        }

        @Override
        public long issueMainThreadTask(ILuaTask task) throws LuaException {
            return 0;
        }

        @Override
        public Object[] pullEvent(String filter) throws LuaException, InterruptedException {
            return null;
        }

        @Override
        public Object[] pullEventRaw(String filter) throws InterruptedException {
            return null;
        }

        @Override
        public Object[] yield(Object[] args) throws InterruptedException {
            return null;
        }
    };

    /**
     * A minimal {@link IComputerAccess} that returns an empty peripheral map,
     * simulating a computer with no other peripherals on the modem network.
     */
    private static final IComputerAccess EMPTY_COMPUTER = makeComputerWithPeripheral(null, null);

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Creates an {@link InventoryPeripheral} backed by a stub inventory of {@code size} slots. */
    private static InventoryPeripheral makePeripheral(int size) {
        return new InventoryPeripheral(new StubInventoryTile(size));
    }

    // =========================================================================
    // Method surface
    // =========================================================================

    @Test
    void peripheralTypeIsInventory() {
        assertEquals("inventory", makePeripheral(9).getType());
    }

    @Test
    void methodCountIsSix() {
        assertEquals(6, makePeripheral(9).getMethodNames().length);
    }

    @Test
    void sizeIsAtIndex0() {
        assertEquals("size", makePeripheral(9).getMethodNames()[METHOD_SIZE]);
    }

    @Test
    void listIsAtIndex1() {
        assertEquals("list", makePeripheral(9).getMethodNames()[METHOD_LIST]);
    }

    @Test
    void getItemDetailIsAtIndex2() {
        assertEquals("getItemDetail", makePeripheral(9).getMethodNames()[METHOD_GET_ITEM_DETAIL]);
    }

    @Test
    void getItemLimitIsAtIndex3() {
        assertEquals("getItemLimit", makePeripheral(9).getMethodNames()[METHOD_GET_ITEM_LIMIT]);
    }

    @Test
    void pushItemsIsAtIndex4() {
        assertEquals("pushItems", makePeripheral(9).getMethodNames()[METHOD_PUSH_ITEMS]);
    }

    @Test
    void pullItemsIsAtIndex5() {
        assertEquals("pullItems", makePeripheral(9).getMethodNames()[METHOD_PULL_ITEMS]);
    }

    // =========================================================================
    // size()
    // =========================================================================

    /**
     * A {@link TileEntity} that does NOT implement {@link net.minecraft.inventory.IInventory}.
     * Used to exercise the "inventory no longer available" error path in
     * {@code InventoryPeripheral.getInventory()}.
     */
    private static class StubNonInventoryTile extends TileEntity {
    }

    @Test
    void sizeThrowsLuaExceptionWhenTileIsNotAnInventory() {
        // Constructing InventoryPeripheral around a non-IInventory tile (m_world == null)
        // must throw LuaException with a clear message rather than ClassCastException/NPE.
        InventoryPeripheral p = new InventoryPeripheral(new StubNonInventoryTile());
        LuaException ex = assertThrows(
            LuaException.class,
            () -> p.callMethod(EMPTY_COMPUTER, SYNC_CONTEXT, METHOD_SIZE, new Object[0]));
        assertTrue(
            ex.getMessage()
                .contains("no longer available"),
            "Error should indicate the inventory is unavailable, got: " + ex.getMessage());
    }

    @Test
    void sizeReturnsInventorySize() throws Exception {
        InventoryPeripheral p = makePeripheral(9);
        Object[] result = p.callMethod(EMPTY_COMPUTER, SYNC_CONTEXT, METHOD_SIZE, new Object[0]);
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals(9, ((Number) result[0]).intValue());
    }

    // =========================================================================
    // list()
    // =========================================================================

    @Test
    void listReturnsEmptyTableForEmptyInventory() throws Exception {
        InventoryPeripheral p = makePeripheral(9);
        Object[] result = p.callMethod(EMPTY_COMPUTER, SYNC_CONTEXT, METHOD_LIST, new Object[0]);
        assertNotNull(result);
        assertEquals(1, result.length);
        @SuppressWarnings("unchecked")
        Map<Object, Object> map = (Map<Object, Object>) result[0];
        assertTrue(map.isEmpty(), "list() should return an empty map for an empty inventory");
    }

    // =========================================================================
    // getItemDetail()
    // =========================================================================

    @Test
    void getItemDetailReturnsNullForEmptySlot() throws Exception {
        InventoryPeripheral p = makePeripheral(9);
        Object[] result = p.callMethod(EMPTY_COMPUTER, SYNC_CONTEXT, METHOD_GET_ITEM_DETAIL, new Object[] { 1 });
        assertNotNull(result);
        assertEquals(1, result.length);
        assertNull(result[0], "getItemDetail should return null for an empty slot");
    }

    @Test
    void getItemDetailThrowsForSlotZero() {
        InventoryPeripheral p = makePeripheral(9);
        assertThrows(
            LuaException.class,
            () -> p.callMethod(EMPTY_COMPUTER, SYNC_CONTEXT, METHOD_GET_ITEM_DETAIL, new Object[] { 0 }));
    }

    @Test
    void getItemDetailThrowsForSlotBeyondSize() {
        InventoryPeripheral p = makePeripheral(9);
        assertThrows(
            LuaException.class,
            () -> p.callMethod(EMPTY_COMPUTER, SYNC_CONTEXT, METHOD_GET_ITEM_DETAIL, new Object[] { 10 }));
    }

    // =========================================================================
    // getItemLimit()
    // =========================================================================

    @Test
    void getItemLimitThrowsForSlotZero() {
        InventoryPeripheral p = makePeripheral(9);
        assertThrows(
            LuaException.class,
            () -> p.callMethod(EMPTY_COMPUTER, SYNC_CONTEXT, METHOD_GET_ITEM_LIMIT, new Object[] { 0 }));
    }

    @Test
    void getItemLimitThrowsForSlotBeyondSize() {
        InventoryPeripheral p = makePeripheral(9);
        assertThrows(
            LuaException.class,
            () -> p.callMethod(EMPTY_COMPUTER, SYNC_CONTEXT, METHOD_GET_ITEM_LIMIT, new Object[] { 10 }));
    }

    @Test
    void getItemLimitReturnsStackLimit() throws Exception {
        StubInventoryTile tile = new StubInventoryTile(9);
        tile.setStackLimit(64);
        InventoryPeripheral p = new InventoryPeripheral(tile);

        Object[] result = p.callMethod(EMPTY_COMPUTER, SYNC_CONTEXT, METHOD_GET_ITEM_LIMIT, new Object[] { 1 });
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals(64, ((Number) result[0]).intValue());
    }

    // =========================================================================
    // pushItems() / pullItems() error paths
    // =========================================================================

    @Test
    void pushItemsThrowsForNonInventoryTarget() {
        // Target peripheral is IPeripheralTargeted but its getTarget() is a plain
        // TileEntity (StubNonInventoryTile) that does not implement IInventory and has
        // no world — must throw "not an inventory", not NPE/CCE.
        InventoryPeripheral nonInvPeripheral = new InventoryPeripheral(new StubNonInventoryTile());
        IComputerAccess computerWithNonInv = makeComputerWithPeripheral("not_an_inv", nonInvPeripheral);

        InventoryPeripheral p = makePeripheral(9);
        LuaException ex = assertThrows(
            LuaException.class,
            () -> p.callMethod(computerWithNonInv, SYNC_CONTEXT, METHOD_PUSH_ITEMS, new Object[] { "not_an_inv", 1 }));
        assertTrue(
            ex.getMessage()
                .contains("not an inventory"),
            "Error should mention 'not an inventory', got: " + ex.getMessage());
    }

    @Test
    void pullItemsThrowsForNonInventorySource() {
        InventoryPeripheral nonInvPeripheral = new InventoryPeripheral(new StubNonInventoryTile());
        IComputerAccess computerWithNonInv = makeComputerWithPeripheral("not_an_inv", nonInvPeripheral);

        InventoryPeripheral p = makePeripheral(9);
        LuaException ex = assertThrows(
            LuaException.class,
            () -> p.callMethod(computerWithNonInv, SYNC_CONTEXT, METHOD_PULL_ITEMS, new Object[] { "not_an_inv", 1 }));
        assertTrue(
            ex.getMessage()
                .contains("not an inventory"),
            "Error should mention 'not an inventory', got: " + ex.getMessage());
    }

    @Test
    void pushItemsThrowsForMissingTarget() {
        InventoryPeripheral p = makePeripheral(9);
        LuaException ex = assertThrows(
            LuaException.class,
            () -> p.callMethod(EMPTY_COMPUTER, SYNC_CONTEXT, METHOD_PUSH_ITEMS, new Object[] { "missing_chest", 1 }));
        assertTrue(
            ex.getMessage()
                .contains("does not exist"),
            "Error should mention 'does not exist'");
    }

    @Test
    void pullItemsThrowsForMissingTarget() {
        InventoryPeripheral p = makePeripheral(9);
        LuaException ex = assertThrows(
            LuaException.class,
            () -> p.callMethod(EMPTY_COMPUTER, SYNC_CONTEXT, METHOD_PULL_ITEMS, new Object[] { "missing_chest", 1 }));
        assertTrue(
            ex.getMessage()
                .contains("does not exist"),
            "Error should mention 'does not exist'");
    }

    @Test
    void pushItemsReturnsZeroForEmptySourceSlot() throws Exception {
        StubInventoryTile fromTile = new StubInventoryTile(9); // slot 1 is empty
        StubInventoryTile toTile = new StubInventoryTile(9);
        InventoryPeripheral toPeripheral = new InventoryPeripheral(toTile);
        IComputerAccess computerWithTarget = makeComputerWithPeripheral("target_chest", toPeripheral);

        InventoryPeripheral p = new InventoryPeripheral(fromTile);
        Object[] result = p
            .callMethod(computerWithTarget, SYNC_CONTEXT, METHOD_PUSH_ITEMS, new Object[] { "target_chest", 1 });

        assertNotNull(result);
        assertEquals(0, ((Number) result[0]).intValue(), "Moving from an empty slot should return 0");
    }

    @Test
    void pushItemsCanResolveTargetBySideName() throws Exception {
        // Regression: PeripheralWrapper.getAvailablePeripherals() previously returned an empty
        // map, causing "Target 'top' does not exist" when the target was a directly-attached
        // side peripheral rather than a wired-modem peripheral.
        StubInventoryTile fromTile = new StubInventoryTile(9); // slot 1 is empty
        StubInventoryTile toTile = new StubInventoryTile(9);
        InventoryPeripheral toPeripheral = new InventoryPeripheral(toTile);
        IComputerAccess computerWithSideTarget = makeComputerWithPeripheral("top", toPeripheral);

        InventoryPeripheral p = new InventoryPeripheral(fromTile);
        Object[] result = p
            .callMethod(computerWithSideTarget, SYNC_CONTEXT, METHOD_PUSH_ITEMS, new Object[] { "top", 1 });

        assertNotNull(result);
        assertEquals(
            0,
            ((Number) result[0]).intValue(),
            "Moving from an empty slot to a side peripheral should return 0, not throw");
    }

    @Test
    void pullItemsCanResolveSourceBySideName() throws Exception {
        // Regression: same root cause as pushItemsCanResolveTargetBySideName.
        StubInventoryTile fromTile = new StubInventoryTile(9); // slot 1 is empty
        InventoryPeripheral fromPeripheral = new InventoryPeripheral(fromTile);
        IComputerAccess computerWithSideSource = makeComputerWithPeripheral("top", fromPeripheral);

        StubInventoryTile toTile = new StubInventoryTile(9);
        InventoryPeripheral p = new InventoryPeripheral(toTile);

        Object[] result = p
            .callMethod(computerWithSideSource, SYNC_CONTEXT, METHOD_PULL_ITEMS, new Object[] { "top", 1 });

        assertNotNull(result);
        assertEquals(
            0,
            ((Number) result[0]).intValue(),
            "Pulling from an empty side peripheral slot should return 0, not throw");
    }

    @Test
    void pullItemsReturnsZeroForEmptySourceSlot() throws Exception {
        StubInventoryTile fromTile = new StubInventoryTile(9); // slot 1 is empty
        InventoryPeripheral fromPeripheral = new InventoryPeripheral(fromTile);
        IComputerAccess computerWithSource = makeComputerWithPeripheral("source_chest", fromPeripheral);

        StubInventoryTile toTile = new StubInventoryTile(9);
        InventoryPeripheral p = new InventoryPeripheral(toTile);

        Object[] result = p
            .callMethod(computerWithSource, SYNC_CONTEXT, METHOD_PULL_ITEMS, new Object[] { "source_chest", 1 });

        assertNotNull(result);
        assertEquals(0, ((Number) result[0]).intValue(), "Pulling from an empty slot should return 0");
    }

    @Test
    void pushItemsMovesStackToTarget() throws Exception {
        StubInventoryTile fromTile = new StubInventoryTile(9);
        // Create a dummy ItemStack with a null Item (allowed in headless tests)
        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack((net.minecraft.item.Item) null, 10, 0);
        fromTile.setSlot(0, stack);

        StubInventoryTile toTile = new StubInventoryTile(9);
        InventoryPeripheral toPeripheral = new InventoryPeripheral(toTile);
        IComputerAccess computerWithTarget = makeComputerWithPeripheral("target_chest", toPeripheral);

        InventoryPeripheral p = new InventoryPeripheral(fromTile);
        Object[] result = p
            .callMethod(computerWithTarget, SYNC_CONTEXT, METHOD_PUSH_ITEMS, new Object[] { "target_chest", 1 });

        assertNotNull(result);
        assertEquals(10, ((Number) result[0]).intValue(), "Should move the full stack");

        // Source should be emptied
        assertNull(fromTile.getStackInSlot(0));

        // Destination should contain the moved stack in slot 0
        net.minecraft.item.ItemStack dest = toTile.getStackInSlot(0);
        assertNotNull(dest);
        assertEquals(10, dest.stackSize);
    }

    @Test
    void pullItemsMovesStackFromSourceToDestination() throws Exception {
        StubInventoryTile fromTile = new StubInventoryTile(9);
        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack((net.minecraft.item.Item) null, 5, 0);
        fromTile.setSlot(0, stack);

        InventoryPeripheral fromPeripheral = new InventoryPeripheral(fromTile);
        IComputerAccess computerWithSource = makeComputerWithPeripheral("source_chest", fromPeripheral);

        StubInventoryTile toTile = new StubInventoryTile(9);
        InventoryPeripheral p = new InventoryPeripheral(toTile);

        Object[] result = p
            .callMethod(computerWithSource, SYNC_CONTEXT, METHOD_PULL_ITEMS, new Object[] { "source_chest", 1 });

        assertNotNull(result);
        assertEquals(5, ((Number) result[0]).intValue(), "Should move the full stack");

        // Source should be emptied
        assertNull(fromTile.getStackInSlot(0));

        // Destination should contain the moved stack
        net.minecraft.item.ItemStack dest = toTile.getStackInSlot(0);
        assertNotNull(dest);
        assertEquals(5, dest.stackSize);
    }

    @Test
    void pushItemsRespectsDestinationInventoryStackLimit() throws Exception {
        StubInventoryTile fromTile = new StubInventoryTile(9);
        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack((net.minecraft.item.Item) null, 20, 0);
        fromTile.setSlot(0, stack);

        StubInventoryTile toTile = new StubInventoryTile(9);
        // Limit destination inventory to 16 per-slot
        toTile.setStackLimit(16);
        InventoryPeripheral toPeripheral = new InventoryPeripheral(toTile);
        IComputerAccess computerWithTarget = makeComputerWithPeripheral("target_chest", toPeripheral);

        InventoryPeripheral p = new InventoryPeripheral(fromTile);
        Object[] result = p
            .callMethod(computerWithTarget, SYNC_CONTEXT, METHOD_PUSH_ITEMS, new Object[] { "target_chest", 1 });

        assertNotNull(result);
        // InventoryUtil will store across multiple slots, so the full 20 should be moved
        assertEquals(20, ((Number) result[0]).intValue(), "Should move all items into available slots");

        // Source should be emptied
        assertNull(fromTile.getStackInSlot(0));

        // Destination should have filled slot 0 up to its per-slot limit, with remainder in slot 1
        net.minecraft.item.ItemStack dest0 = toTile.getStackInSlot(0);
        assertNotNull(dest0);
        assertEquals(16, dest0.stackSize);
        net.minecraft.item.ItemStack dest1 = toTile.getStackInSlot(1);
        assertNotNull(dest1);
        assertEquals(4, dest1.stackSize);
    }

    @Test
    void pushItemsMergesIntoExistingStackRespectingMaxStackSize() throws Exception {
        // Use a real Item instance to avoid null-Item NPEs when querying max stack size
        net.minecraft.item.Item dummyItem = new net.minecraft.item.Item();

        StubInventoryTile fromTile = new StubInventoryTile(9);
        net.minecraft.item.ItemStack srcStack = new net.minecraft.item.ItemStack(dummyItem, 10, 0);
        fromTile.setSlot(0, srcStack);

        StubInventoryTile toTile = new StubInventoryTile(9);
        // Destination already has a stack of the same item
        net.minecraft.item.ItemStack existing = new net.minecraft.item.ItemStack(dummyItem, 60, 0);
        toTile.setSlot(0, existing);

        InventoryPeripheral toPeripheral = new InventoryPeripheral(toTile);
        IComputerAccess computerWithTarget = makeComputerWithPeripheral("target_chest", toPeripheral);

        InventoryPeripheral p = new InventoryPeripheral(fromTile);
        Object[] result = p
            .callMethod(computerWithTarget, SYNC_CONTEXT, METHOD_PUSH_ITEMS, new Object[] { "target_chest", 1 });

        assertNotNull(result);
        // InventoryUtil will merge into the existing slot and then store remainder into following slots
        assertEquals(10, ((Number) result[0]).intValue(), "Should move the full source stack across slots");

        // Source should be emptied
        assertNull(fromTile.getStackInSlot(0));

        // Destination slot 0 should be full at 64 and slot 1 should contain the remainder (6)
        net.minecraft.item.ItemStack dest0 = toTile.getStackInSlot(0);
        assertNotNull(dest0);
        assertEquals(64, dest0.stackSize);
        net.minecraft.item.ItemStack dest1 = toTile.getStackInSlot(1);
        assertNotNull(dest1);
        assertEquals(6, dest1.stackSize);
    }

    @Test
    void pushItemsIntoSpecificSlotRespectsIsItemValidForSlot() throws Exception {
        StubInventoryTile fromTile = new StubInventoryTile(9);
        fromTile.setSlot(0, new net.minecraft.item.ItemStack((net.minecraft.item.Item) null, 5, 0));

        // Destination rejects all items in slot 0 (Lua slot 1).
        StubInventoryTile toTile = new StubInventoryTile(9) {

            @Override
            public boolean isItemValidForSlot(int index, net.minecraft.item.ItemStack stack) {
                return index != 0; // slot 1 (1-indexed) is invalid
            }
        };
        InventoryPeripheral toPeripheral = new InventoryPeripheral(toTile);
        IComputerAccess computerWithTarget = makeComputerWithPeripheral("target_chest", toPeripheral);

        InventoryPeripheral p = new InventoryPeripheral(fromTile);
        // toSlot = 1 maps to index 0, which isItemValidForSlot() rejects.
        Object[] result = p
            .callMethod(computerWithTarget, SYNC_CONTEXT, METHOD_PUSH_ITEMS, new Object[] { "target_chest", 1, 5, 1 });

        assertNotNull(result);
        assertEquals(
            0,
            ((Number) result[0]).intValue(),
            "Should not move items into a slot rejected by isItemValidForSlot");

        // Source unchanged.
        assertNotNull(fromTile.getStackInSlot(0));
        assertEquals(5, fromTile.getStackInSlot(0).stackSize);

        // Destination slot 0 still empty.
        assertNull(toTile.getStackInSlot(0));
    }

    @Test
    void pushItemsIntoSpecificSlotSucceedsWhenSlotIsValid() throws Exception {
        StubInventoryTile fromTile = new StubInventoryTile(9);
        fromTile.setSlot(0, new net.minecraft.item.ItemStack((net.minecraft.item.Item) null, 5, 0));

        // Destination rejects slot 0 but accepts slot 1 (Lua slot 2).
        StubInventoryTile toTile = new StubInventoryTile(9) {

            @Override
            public boolean isItemValidForSlot(int index, net.minecraft.item.ItemStack stack) {
                return index != 0;
            }
        };
        InventoryPeripheral toPeripheral = new InventoryPeripheral(toTile);
        IComputerAccess computerWithTarget = makeComputerWithPeripheral("target_chest", toPeripheral);

        InventoryPeripheral p = new InventoryPeripheral(fromTile);
        // toSlot = 2 maps to index 1, which is accepted.
        Object[] result = p
            .callMethod(computerWithTarget, SYNC_CONTEXT, METHOD_PUSH_ITEMS, new Object[] { "target_chest", 1, 5, 2 });

        assertNotNull(result);
        assertEquals(5, ((Number) result[0]).intValue(), "Should move items into a valid slot");
        assertNull(fromTile.getStackInSlot(0));
        assertNotNull(toTile.getStackInSlot(1));
        assertEquals(5, toTile.getStackInSlot(1).stackSize);
    }

    @Test
    void pushItemsThrowsForOutOfRangeToSlot() {
        StubInventoryTile fromTile = new StubInventoryTile(9);
        fromTile.setSlot(0, new net.minecraft.item.ItemStack((net.minecraft.item.Item) null, 5, 0));

        StubInventoryTile toTile = new StubInventoryTile(9);
        InventoryPeripheral toPeripheral = new InventoryPeripheral(toTile);
        IComputerAccess computerWithTarget = makeComputerWithPeripheral("target_chest", toPeripheral);

        InventoryPeripheral p = new InventoryPeripheral(fromTile);
        // toSlot 10 exceeds target inventory size of 9 — must throw LuaException, not AIOOBE
        assertThrows(
            LuaException.class,
            () -> p.callMethod(
                computerWithTarget,
                SYNC_CONTEXT,
                METHOD_PUSH_ITEMS,
                new Object[] { "target_chest", 1, 5, 10 }),
            "pushItems with an out-of-range toSlot must throw LuaException");
    }

    @Test
    void pushItemsRespectsExplicitLimitParameter() throws Exception {
        StubInventoryTile fromTile = new StubInventoryTile(9);
        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack((net.minecraft.item.Item) null, 10, 0);
        fromTile.setSlot(0, stack);

        StubInventoryTile toTile = new StubInventoryTile(9);
        InventoryPeripheral toPeripheral = new InventoryPeripheral(toTile);
        IComputerAccess computerWithTarget = makeComputerWithPeripheral("target_chest", toPeripheral);

        InventoryPeripheral p = new InventoryPeripheral(fromTile);
        // Provide an explicit limit of 3 items
        Object[] result = p
            .callMethod(computerWithTarget, SYNC_CONTEXT, METHOD_PUSH_ITEMS, new Object[] { "target_chest", 1, 3 });

        assertNotNull(result);
        assertEquals(3, ((Number) result[0]).intValue(), "Should move only the explicit limit");

        // Source should have 7 remaining
        net.minecraft.item.ItemStack src = fromTile.getStackInSlot(0);
        assertNotNull(src);
        assertEquals(7, src.stackSize);
    }

    @Test
    void pullItemsRespectsExplicitLimitParameter() throws Exception {
        StubInventoryTile fromTile = new StubInventoryTile(9);
        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack((net.minecraft.item.Item) null, 8, 0);
        fromTile.setSlot(0, stack);

        InventoryPeripheral fromPeripheral = new InventoryPeripheral(fromTile);
        IComputerAccess computerWithSource = makeComputerWithPeripheral("source_chest", fromPeripheral);

        StubInventoryTile toTile = new StubInventoryTile(9);
        InventoryPeripheral p = new InventoryPeripheral(toTile);

        // Explicit limit of 5 should move only 5 items
        Object[] result = p
            .callMethod(computerWithSource, SYNC_CONTEXT, METHOD_PULL_ITEMS, new Object[] { "source_chest", 1, 5 });

        assertNotNull(result);
        assertEquals(5, ((Number) result[0]).intValue(), "Should pull only the explicit limit");

        // Source should have 3 remaining
        net.minecraft.item.ItemStack src = fromTile.getStackInSlot(0);
        assertNotNull(src);
        assertEquals(3, src.stackSize);
    }

    // =========================================================================
    // equals()
    // =========================================================================

    @Test
    void equalsReturnsTrueForSameTile() {
        StubInventoryTile tile = new StubInventoryTile(9);
        InventoryPeripheral a = new InventoryPeripheral(tile);
        InventoryPeripheral b = new InventoryPeripheral(tile);
        assertTrue(a.equals(b));
    }

    @Test
    void equalsReturnsFalseForDifferentTile() {
        InventoryPeripheral a = new InventoryPeripheral(new StubInventoryTile(9));
        InventoryPeripheral b = new InventoryPeripheral(new StubInventoryTile(9));
        assertFalse(a.equals(b));
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Creates an {@link IComputerAccess} that exposes at most one named
     * peripheral. Pass {@code null} for both arguments to get an empty map.
     */
    private static IComputerAccess makeComputerWithPeripheral(String name, IPeripheral peripheral) {
        final Map<String, IPeripheral> peripherals = (name != null) ? Collections.singletonMap(name, peripheral)
            : Collections.emptyMap();

        return new IComputerAccess() {

            @Override
            public String mount(String desiredLocation, dan200.computercraft.api.filesystem.IMount mount) {
                return null;
            }

            @Override
            public String mount(String desiredLocation, dan200.computercraft.api.filesystem.IMount mount,
                String driveName) {
                return null;
            }

            @Override
            public String mountWritable(String desiredLocation,
                dan200.computercraft.api.filesystem.IWritableMount mount) {
                return null;
            }

            @Override
            public String mountWritable(String desiredLocation,
                dan200.computercraft.api.filesystem.IWritableMount mount, String driveName) {
                return null;
            }

            @Override
            public void unmount(String location) {}

            @Override
            public int getID() {
                return 0;
            }

            @Override
            public void queueEvent(String event, Object[] arguments) {}

            @Override
            public String getAttachmentName() {
                return "left";
            }

            @Override
            public Map<String, IPeripheral> getAvailablePeripherals() {
                return peripherals;
            }
        };
    }
}
