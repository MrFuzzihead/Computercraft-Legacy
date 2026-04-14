package dan200.computercraft.shared.peripheral.redstone;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.computer.Computer;

/**
 * Unit tests for {@link RedstoneRelayPeripheral}.
 *
 * <p>
 * Verifies the method surface (count, names, ordering) and the validation
 * paths that throw {@link LuaException} before touching Minecraft world state,
 * plus {@code getSides()} which has no world dependency.
 * </p>
 *
 * <p>
 * Both {@link TileRedstoneRelay} and {@link RedstoneRelayPeripheral} are
 * safe to construct with {@code worldObj == null}: the constructor does not
 * dereference the world, and all tested code paths complete before any world
 * access would occur.
 * </p>
 */
class RedstoneRelayPeripheralTest {

    private static final int EXPECTED_METHOD_COUNT = 14;

    private static RedstoneRelayPeripheral peripheral;

    @BeforeAll
    static void buildPeripheral() {
        // TileRedstoneRelay is safe with null world for these test paths.
        TileRedstoneRelay tile = new TileRedstoneRelay();
        peripheral = new RedstoneRelayPeripheral(tile);
    }

    // =========================================================================
    // Method count
    // =========================================================================

    @Test
    void methodCountIsFourteen() {
        assertEquals(EXPECTED_METHOD_COUNT, peripheral.getMethodNames().length);
    }

    // =========================================================================
    // Method names and indices
    // =========================================================================

    @Test
    void getSidesIsAtIndex0() {
        assertEquals("getSides", peripheral.getMethodNames()[0]);
    }

    @Test
    void setOutputIsAtIndex1() {
        assertEquals("setOutput", peripheral.getMethodNames()[1]);
    }

    @Test
    void getOutputIsAtIndex2() {
        assertEquals("getOutput", peripheral.getMethodNames()[2]);
    }

    @Test
    void getInputIsAtIndex3() {
        assertEquals("getInput", peripheral.getMethodNames()[3]);
    }

    @Test
    void setBundledOutputIsAtIndex4() {
        assertEquals("setBundledOutput", peripheral.getMethodNames()[4]);
    }

    @Test
    void getBundledOutputIsAtIndex5() {
        assertEquals("getBundledOutput", peripheral.getMethodNames()[5]);
    }

    @Test
    void getBundledInputIsAtIndex6() {
        assertEquals("getBundledInput", peripheral.getMethodNames()[6]);
    }

    @Test
    void testBundledInputIsAtIndex7() {
        assertEquals("testBundledInput", peripheral.getMethodNames()[7]);
    }

    @Test
    void setAnalogOutputIsAtIndex8() {
        assertEquals("setAnalogOutput", peripheral.getMethodNames()[8]);
    }

    @Test
    void setAnalogueOutputIsAtIndex9() {
        assertEquals("setAnalogueOutput", peripheral.getMethodNames()[9]);
    }

    @Test
    void getAnalogOutputIsAtIndex10() {
        assertEquals("getAnalogOutput", peripheral.getMethodNames()[10]);
    }

    @Test
    void getAnalogueOutputIsAtIndex11() {
        assertEquals("getAnalogueOutput", peripheral.getMethodNames()[11]);
    }

    @Test
    void getAnalogInputIsAtIndex12() {
        assertEquals("getAnalogInput", peripheral.getMethodNames()[12]);
    }

    @Test
    void getAnalogueInputIsAtIndex13() {
        assertEquals("getAnalogueInput", peripheral.getMethodNames()[13]);
    }

    // =========================================================================
    // Peripheral type
    // =========================================================================

    @Test
    void peripheralTypeIsRedstoneRelay() {
        assertEquals("redstone_relay", peripheral.getType());
    }

    // =========================================================================
    // getSides() — no world access required
    // =========================================================================

    @Test
    @SuppressWarnings("unchecked")
    void getSidesReturnsSixEntries() throws LuaException {
        Object[] result = peripheral.callMethod(null, null, 0, new Object[0]);
        assertNotNull(result);
        assertEquals(1, result.length);
        Map<Object, Object> sides = (Map<Object, Object>) result[0];
        assertEquals(6, sides.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSidesMatchesSideNames() throws LuaException {
        Object[] result = peripheral.callMethod(null, null, 0, new Object[0]);
        Map<Object, Object> sides = (Map<Object, Object>) result[0];
        List<String> expected = Arrays.asList(Computer.s_sideNames);
        for (int i = 1; i <= 6; i++) {
            assertTrue(expected.contains(sides.get(i)), "Missing side name at index " + i);
        }
    }

    // =========================================================================
    // parseSide validation — no world access required
    // =========================================================================

    @Test
    void getOutputThrowsOnInvalidSide() {
        assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, 2, new Object[] { "invalid_side" }));
    }

    @Test
    void getInputThrowsOnInvalidSide() {
        assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, 3, new Object[] { "invalid_side" }));
    }

    @Test
    void getOutputThrowsOnMissingArg() {
        assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, 2, new Object[0]));
    }

    // =========================================================================
    // Analog range validation — throws before any world access
    // =========================================================================

    @Test
    void setAnalogOutputThrowsOnNegativeLevel() {
        assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, 8, new Object[] { "top", -1.0 }));
    }

    @Test
    void setAnalogOutputThrowsOnLevelAbove15() {
        assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, 8, new Object[] { "top", 16.0 }));
    }

    @Test
    void setAnalogueOutputThrowsOnNegativeLevel() {
        assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, 9, new Object[] { "top", -1.0 }));
    }

    @Test
    void setAnalogueOutputThrowsOnLevelAbove15() {
        assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, 9, new Object[] { "top", 16.0 }));
    }

    // =========================================================================
    // equals — identity comparison
    // =========================================================================

    @Test
    void equalsReturnsTrueForSameTile() {
        TileRedstoneRelay tile = new TileRedstoneRelay();
        RedstoneRelayPeripheral p1 = new RedstoneRelayPeripheral(tile);
        RedstoneRelayPeripheral p2 = new RedstoneRelayPeripheral(tile);
        assertTrue(p1.equals(p2));
    }

    @Test
    void equalsReturnsFalseForDifferentTile() {
        RedstoneRelayPeripheral p1 = new RedstoneRelayPeripheral(new TileRedstoneRelay());
        RedstoneRelayPeripheral p2 = new RedstoneRelayPeripheral(new TileRedstoneRelay());
        assertFalse(p1.equals(p2));
    }
}
