package dan200.computercraft.shared.peripheral.inventory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.Map;

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
