package dan200.computercraft.compat.customnpcs.peripheral.traderrole;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.api.lua.LuaException;

/**
 * Unit tests for {@link TraderRolePeripheral}.
 *
 * <p>
 * No running Minecraft world or CustomNPCs installation is required.
 * CNPC-dependent methods (5–15) are verified to throw the expected
 * {@link LuaException} when CustomNPCs is absent.
 * </p>
 */
class TraderRolePeripheralTest {

    private TileTraderRole tile;
    private TraderRolePeripheral peripheral;

    @BeforeEach
    void setUp() {
        tile = new TileTraderRole();
        peripheral = new TraderRolePeripheral(tile);
    }

    // =========================================================================
    // getType / getMethodNames
    // =========================================================================

    @Test
    void getType_returnsNpcTrader() {
        assertEquals("npc_trader", peripheral.getType());
    }

    @Test
    void getMethodNames_16Methods() {
        assertEquals(16, peripheral.getMethodNames().length);
        assertEquals(Arrays.asList(TraderRolePeripheral.METHOD_NAMES), Arrays.asList(peripheral.getMethodNames()));
    }

    // =========================================================================
    // equals
    // =========================================================================

    @Test
    void equals_sameTile_returnsTrue() {
        assertTrue(peripheral.equals(new TraderRolePeripheral(tile)));
    }

    @Test
    void equals_differentTile_returnsFalse() {
        assertFalse(peripheral.equals(new TraderRolePeripheral(new TileTraderRole())));
    }

    // =========================================================================
    // Link management (methods 0–4) — work without CustomNPCs
    // =========================================================================

    @Test
    void isLinked_initiallyFalse() throws Exception {
        Object[] result = peripheral.callMethod(null, null, 3, new Object[0]);
        assertEquals(false, result[0]);
    }

    @Test
    void getLinkedName_initiallyNull() throws Exception {
        Object[] result = peripheral.callMethod(null, null, 4, new Object[0]);
        assertNull(result[0]);
    }

    @Test
    void unlink_clearsPreviousLink() throws Exception {
        tile.setLink("test-uuid", "Merchant");
        assertTrue((Boolean) peripheral.callMethod(null, null, 3, new Object[0])[0]);

        peripheral.callMethod(null, null, 2, new Object[0]);
        assertFalse((Boolean) peripheral.callMethod(null, null, 3, new Object[0])[0]);
    }

    @Test
    void getLinkedName_returnsStoredName() throws Exception {
        tile.setLink("test-uuid", "Merchant");
        Object[] result = peripheral.callMethod(null, null, 4, new Object[0]);
        assertEquals("Merchant", result[0]);
    }

    // =========================================================================
    // Methods 5–15 throw when CustomNPCs is not installed
    // =========================================================================

    private void assertCnpcRequired(int method, Object... args) {
        LuaException ex = assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, method, args));
        assertEquals("CustomNPCs is not installed", ex.getMessage());
    }

    @Test
    void getSellOption_throwsWhenCnpcAbsent() {
        assertCnpcRequired(5, 0);
    }

    @Test
    void getCurrency_throwsWhenCnpcAbsent() {
        assertCnpcRequired(6, 0);
    }

    @Test
    void setSellOption_throwsWhenCnpcAbsent() {
        assertCnpcRequired(7, 0, "minecraft:gold_ingot", 1, "minecraft:emerald", 1);
    }

    @Test
    void removeSellOption_throwsWhenCnpcAbsent() {
        assertCnpcRequired(8, 0);
    }

    @Test
    void isSlotEnabled_throwsWhenCnpcAbsent() {
        assertCnpcRequired(9, 0);
    }

    @Test
    void enableSlot_throwsWhenCnpcAbsent() {
        assertCnpcRequired(10, 0);
    }

    @Test
    void disableSlot_throwsWhenCnpcAbsent() {
        assertCnpcRequired(11, 0);
    }

    @Test
    void getPurchaseNum_throwsWhenCnpcAbsent() {
        assertCnpcRequired(12, 0);
    }

    @Test
    void resetPurchaseNum_throwsWhenCnpcAbsent() {
        assertCnpcRequired(13);
    }

    @Test
    void getMarket_throwsWhenCnpcAbsent() {
        assertCnpcRequired(14);
    }

    @Test
    void setMarket_throwsWhenCnpcAbsent() {
        assertCnpcRequired(15, "MyMarket");
    }

    // =========================================================================
    // Argument validation (slot range)
    // =========================================================================

    @Test
    void getSellOption_slotTooHigh_throwsSlotError() {
        LuaException ex = assertThrows(
            LuaException.class,
            () -> peripheral.callMethod(null, null, 5, new Object[] { 18 }));
        assertTrue(
            ex.getMessage()
                .contains("Slot must be 0–17"));
    }

    @Test
    void getSellOption_slotNegative_throwsSlotError() {
        LuaException ex = assertThrows(
            LuaException.class,
            () -> peripheral.callMethod(null, null, 5, new Object[] { -1 }));
        assertTrue(
            ex.getMessage()
                .contains("Slot must be 0–17"));
    }

    @Test
    void getSellOption_nonNumberSlot_throwsExpectedNumber() {
        assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, 5, new Object[] { "zero" }));
    }

    // =========================================================================
    // requireString / requireInt helpers (package-visible for test access)
    // =========================================================================

    @Test
    void requireString_missingArg_throws() {
        LuaException ex = assertThrows(
            LuaException.class,
            () -> TraderRolePeripheral.requireString(new Object[0], 0, "name"));
        assertTrue(
            ex.getMessage()
                .contains("name"));
    }

    @Test
    void requireInt_missingArg_throws() {
        LuaException ex = assertThrows(
            LuaException.class,
            () -> TraderRolePeripheral.requireInt(new Object[0], 0, "count"));
        assertTrue(
            ex.getMessage()
                .contains("count"));
    }
}
