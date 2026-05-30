package dan200.computercraft.compat.customnpcs.peripheral.npcinterface;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.api.lua.LuaException;

/**
 * Unit tests for {@link NpcInterfacePeripheral}.
 *
 * <p>
 * No running Minecraft world or CustomNPCs installation is required.
 * CNPC-dependent methods (8–35) are verified to throw the expected
 * {@link LuaException} when CustomNPCs is absent.
 * </p>
 *
 * <p>
 * Method index reference (matches {@link NpcInterfacePeripheral#METHOD_NAMES}):
 * 
 * <pre>
 *   Link management (no CNPC gate unless noted):
 *     0  link(uuid)
 *     1  linkByName(name[,r])     [CNPC]
 *     2  linkAll(name[,r])        [CNPC]
 *     3  linkNearest([r])         [CNPC]
 *     4  unlink([uuid])
 *     5  isLinked([uuid])
 *     6  getLinkedNpcs()
 *     7  getLinkedUUID()
 *     8  scanNpcs([r])            [CNPC]
 *   State-read (all CNPC):        9–21
 *   Control / state-write (CNPC): 22–35
 * </pre>
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
    void getMethodNames_36Methods() {
        assertEquals(36, peripheral.getMethodNames().length);
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
    // Link management — methods that work without CustomNPCs
    // =========================================================================

    @Test
    void isLinked_initiallyFalse() throws Exception {
        // method 5 = isLinked
        Object[] result = peripheral.callMethod(null, null, 5, new Object[0]);
        assertEquals(false, result[0]);
    }

    @Test
    void isLinked_afterSetLink_returnsTrue() throws Exception {
        tile.setLink("test-uuid", "Guard");
        Object[] result = peripheral.callMethod(null, null, 5, new Object[0]);
        assertEquals(true, result[0]);
    }

    @Test
    void isLinked_withUuidArg_checksSpecificUuid() throws Exception {
        tile.setLink("test-uuid", "Guard");
        Object[] yes = peripheral.callMethod(null, null, 5, new Object[] { "test-uuid" });
        assertEquals(true, yes[0]);
        Object[] no = peripheral.callMethod(null, null, 5, new Object[] { "other-uuid" });
        assertEquals(false, no[0]);
    }

    @Test
    void unlink_clearsPreviousLink() throws Exception {
        // method 4 = unlink
        tile.setLink("test-uuid", "Guard");
        assertTrue((Boolean) peripheral.callMethod(null, null, 5, new Object[0])[0]);

        peripheral.callMethod(null, null, 4, new Object[0]);
        assertFalse((Boolean) peripheral.callMethod(null, null, 5, new Object[0])[0]);
    }

    @Test
    void getLinkedNpcs_initiallyEmpty() throws Exception {
        // method 6 = getLinkedNpcs
        Object[] result = peripheral.callMethod(null, null, 6, new Object[0]);
        assertNotNull(result[0]);
        assertTrue(((Map<?, ?>) result[0]).isEmpty());
    }

    @Test
    void getLinkedNpcs_afterSetLink_containsEntry() throws Exception {
        tile.setLink("test-uuid", "Guard");
        Object[] result = peripheral.callMethod(null, null, 6, new Object[0]);
        @SuppressWarnings("unchecked")
        Map<String, String> table = (Map<String, String>) result[0];
        assertEquals(1, table.size());
        assertEquals("Guard", table.get("test-uuid"));
    }

    // =========================================================================
    // getLinkedUUID — method 7
    // =========================================================================

    @Test
    void getLinkedUUID_initiallyNull() throws Exception {
        // method 7 = getLinkedUUID
        Object[] result = peripheral.callMethod(null, null, 7, new Object[0]);
        assertNull(result[0], "getLinkedUUID() must return nil when not linked");
    }

    @Test
    void getLinkedUUID_returnsStoredUUID() throws Exception {
        tile.setLink("aaaaaaaa-0000-0000-0000-aaaaaaaaaaaa", "Guard");
        Object[] result = peripheral.callMethod(null, null, 7, new Object[0]);
        assertEquals("aaaaaaaa-0000-0000-0000-aaaaaaaaaaaa", result[0]);
    }

    @Test
    void getLinkedUUID_afterUnlink_returnsNull() throws Exception {
        tile.setLink("aaaaaaaa-0000-0000-0000-aaaaaaaaaaaa", "Guard");
        peripheral.callMethod(null, null, 4, new Object[0]); // unlink
        Object[] result = peripheral.callMethod(null, null, 7, new Object[0]);
        assertNull(result[0]);
    }

    @Test
    void getLinkedUUID_doesNotRequireCnpc() {
        // Must not throw even when CustomNPCs is absent
        assertDoesNotThrow(() -> peripheral.callMethod(null, null, 7, new Object[0]));
    }

    // =========================================================================
    // CNPC-gated link methods — 1, 2, 3, 8
    // =========================================================================

    @Test
    void linkByName_throwsWhenCnpcAbsent() {
        assertCnpcRequired(1, "Guard");
    }

    @Test
    void linkAll_throwsWhenCnpcAbsent() {
        assertCnpcRequired(2, "Guard");
    }

    @Test
    void linkNearest_throwsWhenCnpcAbsent() {
        assertCnpcRequired(3);
    }

    @Test
    void scanNpcs_throwsWhenCnpcAbsent() {
        assertCnpcRequired(8);
    }

    // =========================================================================
    // State-read methods (9–21) — all require CNPC
    // =========================================================================

    @Test
    void getName_throwsWhenCnpcAbsent() {
        assertCnpcRequired(9);
    }

    @Test
    void getTitle_throwsWhenCnpcAbsent() {
        assertCnpcRequired(10);
    }

    @Test
    void getUUID_throwsWhenCnpcAbsent() {
        assertCnpcRequired(11);
    }

    @Test
    void isAlive_throwsWhenCnpcAbsent() {
        assertCnpcRequired(12);
    }

    @Test
    void getHealth_throwsWhenCnpcAbsent() {
        assertCnpcRequired(13);
    }

    @Test
    void getMaxHealth_throwsWhenCnpcAbsent() {
        assertCnpcRequired(14);
    }

    @Test
    void getPosition_throwsWhenCnpcAbsent() {
        assertCnpcRequired(15);
    }

    @Test
    void getMovingType_throwsWhenCnpcAbsent() {
        assertCnpcRequired(16);
    }

    @Test
    void isAttacking_throwsWhenCnpcAbsent() {
        assertCnpcRequired(17);
    }

    @Test
    void getTarget_throwsWhenCnpcAbsent() {
        assertCnpcRequired(18);
    }

    @Test
    void getFaction_throwsWhenCnpcAbsent() {
        assertCnpcRequired(19);
    }

    @Test
    void getJob_throwsWhenCnpcAbsent() {
        assertCnpcRequired(20);
    }

    @Test
    void getRole_throwsWhenCnpcAbsent() {
        assertCnpcRequired(21);
    }

    // =========================================================================
    // Control methods (22–28) — all require CNPC
    // =========================================================================

    @Test
    void say_throwsWhenCnpcAbsent() {
        assertCnpcRequired(22, "Hello!");
    }

    @Test
    void setHome_throwsWhenCnpcAbsent() {
        assertCnpcRequired(23, 0, 64, 0);
    }

    @Test
    void setMovingType_throwsWhenCnpcAbsent() {
        assertCnpcRequired(24, "wandering");
    }

    @Test
    void navigateTo_throwsWhenCnpcAbsent() {
        assertCnpcRequired(25, 100.0, 64.0, 100.0);
    }

    @Test
    void executeCommand_throwsWhenCnpcAbsent() {
        assertCnpcRequired(26, "/say Hi");
    }

    @Test
    void kill_throwsWhenCnpcAbsent() {
        assertCnpcRequired(27);
    }

    @Test
    void reset_throwsWhenCnpcAbsent() {
        assertCnpcRequired(28);
    }

    // =========================================================================
    // State-write methods (29–35) — all require CNPC
    // =========================================================================

    @Test
    void setName_throwsWhenCnpcAbsent() {
        assertCnpcRequired(29, "NewName");
    }

    @Test
    void setTitle_throwsWhenCnpcAbsent() {
        assertCnpcRequired(30, "Guard");
    }

    @Test
    void setHealth_throwsWhenCnpcAbsent() {
        assertCnpcRequired(31, 20.0);
    }

    @Test
    void setMaxHealth_throwsWhenCnpcAbsent() {
        assertCnpcRequired(32, 40.0);
    }

    @Test
    void setFaction_throwsWhenCnpcAbsent() {
        assertCnpcRequired(33, 1);
    }

    @Test
    void setJob_throwsWhenCnpcAbsent() {
        assertCnpcRequired(34, 2);
    }

    @Test
    void setRole_throwsWhenCnpcAbsent() {
        assertCnpcRequired(35, 3);
    }

    // =========================================================================
    // Argument validation (checked before or alongside CNPC gate)
    // =========================================================================

    @Test
    void say_missingArg_throwsLuaException() {
        // method 22 = say — CNPC check fires first, but LuaException is always the result
        assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, 22, new Object[0]));
    }

    @Test
    void setMovingType_invalidString_throwsWithOptions() {
        // method 24 = setMovingType — type string is validated BEFORE the CNPC gate
        LuaException ex = assertThrows(
            LuaException.class,
            () -> peripheral.callMethod(null, null, 24, new Object[] { "flying" }));
        String msg = ex.getMessage();
        assertTrue(msg.contains("flying"), "message should mention the invalid value");
        assertTrue(msg.contains("standing"), "message should list valid options");
        assertTrue(msg.contains("wandering"), "message should list valid options");
        assertTrue(msg.contains("path"), "message should list valid options");
    }

    @Test
    void setMovingType_numericArg_throwsLuaException() {
        // Passing a number where a string is expected must throw
        assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, 24, new Object[] { 1 }));
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
    // Helper
    // =========================================================================

    private void assertCnpcRequired(int method, Object... args) {
        LuaException ex = assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, method, args));
        assertEquals("CustomNPCs is not installed", ex.getMessage());
    }
}
