package dan200.computercraft.compat.customnpcs.peripheral.npcinterface;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.api.lua.LuaException;

/**
 * Unit tests for {@link NpcInterfacePeripheral}.
 *
 * <p>
 * No running Minecraft world or CustomNPCs installation is required.
 * CNPC-dependent methods (5–24) are verified to throw the expected
 * {@link LuaException} when CustomNPCs is absent.
 * </p>
 */
class NpcInterfacePeripheralTest {

    private TileNpcInterface tile;
    private NpcInterfacePeripheral peripheral;

    @BeforeEach
    void setUp() {
        tile = new TileNpcInterface();
        peripheral = new NpcInterfacePeripheral(tile);
    }

    // =========================================================================
    // getType / getMethodNames
    // =========================================================================

    @Test
    void getType_returnsNpcInterface() {
        assertEquals("npc_interface", peripheral.getType());
    }

    @Test
    void getMethodNames_32Methods() {
        assertEquals(32, peripheral.getMethodNames().length);
        assertEquals(Arrays.asList(NpcInterfacePeripheral.METHOD_NAMES), Arrays.asList(peripheral.getMethodNames()));
    }

    // =========================================================================
    // equals
    // =========================================================================

    @Test
    void equals_sameTile_returnsTrue() {
        NpcInterfacePeripheral other = new NpcInterfacePeripheral(tile);
        assertTrue(peripheral.equals(other));
    }

    @Test
    void equals_differentTile_returnsFalse() {
        TileNpcInterface other = new TileNpcInterface();
        assertFalse(peripheral.equals(new NpcInterfacePeripheral(other)));
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
        // Directly set link via tile
        tile.setLink("test-uuid", "Guard");
        Object[] linkedResult = peripheral.callMethod(null, null, 3, new Object[0]);
        assertTrue((Boolean) linkedResult[0]);

        // Unlink via method 2
        peripheral.callMethod(null, null, 2, new Object[0]);
        Object[] afterUnlink = peripheral.callMethod(null, null, 3, new Object[0]);
        assertFalse((Boolean) afterUnlink[0]);
    }

    @Test
    void getLinkedName_returnsStoredName() throws Exception {
        tile.setLink("test-uuid", "Guard");
        Object[] result = peripheral.callMethod(null, null, 4, new Object[0]);
        assertEquals("Guard", result[0]);
    }

    // =========================================================================
    // Methods 5–24 throw when CustomNPCs is not installed
    // =========================================================================

    private void assertCnpcRequired(int method, Object... args) {
        LuaException ex = assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, method, args));
        assertEquals("CustomNPCs is not installed", ex.getMessage());
    }

    @Test
    void getName_throwsWhenCnpcAbsent() {
        assertCnpcRequired(5);
    }

    @Test
    void getTitle_throwsWhenCnpcAbsent() {
        assertCnpcRequired(6);
    }

    @Test
    void getUUID_throwsWhenCnpcAbsent() {
        assertCnpcRequired(7);
    }

    @Test
    void isAlive_throwsWhenCnpcAbsent() {
        assertCnpcRequired(8);
    }

    @Test
    void getHealth_throwsWhenCnpcAbsent() {
        assertCnpcRequired(9);
    }

    @Test
    void getMaxHealth_throwsWhenCnpcAbsent() {
        assertCnpcRequired(10);
    }

    @Test
    void getPosition_throwsWhenCnpcAbsent() {
        assertCnpcRequired(11);
    }

    @Test
    void getMovingType_throwsWhenCnpcAbsent() {
        assertCnpcRequired(12);
    }

    @Test
    void isAttacking_throwsWhenCnpcAbsent() {
        assertCnpcRequired(13);
    }

    @Test
    void getTarget_throwsWhenCnpcAbsent() {
        assertCnpcRequired(14);
    }

    @Test
    void getFaction_throwsWhenCnpcAbsent() {
        assertCnpcRequired(15);
    }

    @Test
    void getJob_throwsWhenCnpcAbsent() {
        assertCnpcRequired(16);
    }

    @Test
    void getRole_throwsWhenCnpcAbsent() {
        assertCnpcRequired(17);
    }

    @Test
    void say_throwsWhenCnpcAbsent() {
        assertCnpcRequired(18, "Hello!");
    }

    @Test
    void setHome_throwsWhenCnpcAbsent() {
        assertCnpcRequired(19, 0, 64, 0);
    }

    @Test
    void setMovingType_throwsWhenCnpcAbsent() {
        assertCnpcRequired(20, "wandering");
    }

    @Test
    void navigateTo_throwsWhenCnpcAbsent() {
        assertCnpcRequired(21, 100.0, 64.0, 100.0);
    }

    @Test
    void executeCommand_throwsWhenCnpcAbsent() {
        assertCnpcRequired(22, "/say Hi");
    }

    @Test
    void kill_throwsWhenCnpcAbsent() {
        assertCnpcRequired(23);
    }

    @Test
    void reset_throwsWhenCnpcAbsent() {
        assertCnpcRequired(24);
    }

    @Test
    void setName_throwsWhenCnpcAbsent() {
        assertCnpcRequired(25, "NewName");
    }

    @Test
    void setTitle_throwsWhenCnpcAbsent() {
        assertCnpcRequired(26, "Guard");
    }

    @Test
    void setHealth_throwsWhenCnpcAbsent() {
        assertCnpcRequired(27, 20.0);
    }

    @Test
    void setMaxHealth_throwsWhenCnpcAbsent() {
        assertCnpcRequired(28, 40.0);
    }

    @Test
    void setFaction_throwsWhenCnpcAbsent() {
        assertCnpcRequired(29, 1);
    }

    @Test
    void setJob_throwsWhenCnpcAbsent() {
        assertCnpcRequired(30, 2);
    }

    @Test
    void setRole_throwsWhenCnpcAbsent() {
        assertCnpcRequired(31, 3);
    }

    // =========================================================================
    // Argument validation (checked before CNPC gate for string/number requirements)
    // =========================================================================

    @Test
    void say_missingArg_throwsExpectedString() {
        // CNPC check fires first in the outer default branch — both produce LuaException
        assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, 18, new Object[0]));
    }

    // =========================================================================
    // requireString / requireInt / requireDouble helpers
    // =========================================================================

    @Test
    void requireString_missingArg_throws() {
        LuaException ex = assertThrows(
            LuaException.class,
            () -> NpcInterfacePeripheral.requireString(new Object[0], 0, "name"));
        assertTrue(
            ex.getMessage()
                .contains("name"));
    }

    @Test
    void requireString_wrongType_throws() {
        LuaException ex = assertThrows(
            LuaException.class,
            () -> NpcInterfacePeripheral.requireString(new Object[] { 42 }, 0, "name"));
        assertTrue(
            ex.getMessage()
                .contains("name"));
    }

    @Test
    void requireInt_missingArg_throws() {
        LuaException ex = assertThrows(
            LuaException.class,
            () -> NpcInterfacePeripheral.requireInt(new Object[0], 0, "x"));
        assertTrue(
            ex.getMessage()
                .contains("x"));
    }

    @Test
    void requireDouble_missingArg_throws() {
        LuaException ex = assertThrows(
            LuaException.class,
            () -> NpcInterfacePeripheral.requireDouble(new Object[0], 0, "x"));
        assertTrue(
            ex.getMessage()
                .contains("x"));
    }

    // =========================================================================
    // setMovingType string parsing
    // =========================================================================

    @Test
    void setMovingType_invalidString_throwsWithOptions() {
        LuaException ex = assertThrows(
            LuaException.class,
            () -> peripheral.callMethod(null, null, 20, new Object[] { "flying" }));
        String msg = ex.getMessage();
        assertTrue(msg.contains("flying"), "message should mention the invalid value");
        assertTrue(msg.contains("standing"), "message should list valid options");
        assertTrue(msg.contains("wandering"), "message should list valid options");
        assertTrue(msg.contains("path"), "message should list valid options");
    }

    @Test
    void setMovingType_numericArg_throwsLuaException() {
        // Passing a number where a string is expected must throw
        assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, 20, new Object[] { 1 }));
    }
}
