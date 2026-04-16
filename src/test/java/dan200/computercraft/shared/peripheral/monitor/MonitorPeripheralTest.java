package dan200.computercraft.shared.peripheral.monitor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.apis.TermAPI;
import dan200.computercraft.core.terminal.Terminal;

/**
 * Unit tests for {@link MonitorPeripheral} covering:
 * <ul>
 * <li>{@code setTextColor}/{@code setBackgroundColor} accept all 16 colors
 * (non-color monitors render them as greyscale on screen).</li>
 * <li>Palette validation logic shared by {@code setPaletteColor} and
 * {@code getPaletteColor}.</li>
 * <li>Method surface: correct count and index of all 26 methods including
 * the new {@code getTextScale}, {@code getCursorBlink},
 * {@code setPaletteColor/Colour}, and {@code getPaletteColor/Colour}.</li>
 * </ul>
 *
 * <h2>Scope</h2>
 * <p>
 * {@link TileMonitor} requires a live Minecraft world and cannot be constructed
 * in a headless unit test. Color-validation contracts are exercised by calling
 * {@link TermAPI#parseColour} directly (the same call the peripheral makes
 * internally), and palette read/write contracts use a plain
 * {@link Terminal} instance. In-game behavioral tests live in
 * {@code test_monitor.lua}.
 * </p>
 */
class MonitorPeripheralTest {

    // -------------------------------------------------------------------------
    // Method-index constants — must match MonitorPeripheral.getMethodNames().
    // -------------------------------------------------------------------------
    private static final int IDX_WRITE = 0;
    private static final int IDX_SET_TEXT_SCALE = 8;
    private static final int IDX_SET_TEXT_COLOUR = 9;
    private static final int IDX_SET_TEXT_COLOR = 10;
    private static final int IDX_SET_BG_COLOUR = 11;
    private static final int IDX_SET_BG_COLOR = 12;
    private static final int IDX_BLIT = 19;
    private static final int IDX_GET_TEXT_SCALE = 20;
    private static final int IDX_GET_CURSOR_BLINK = 21;
    private static final int IDX_SET_PALETTE_COLOR = 22;
    private static final int IDX_SET_PALETTE_COLOUR = 23;
    private static final int IDX_GET_PALETTE_COLOR = 24;
    private static final int IDX_GET_PALETTE_COLOUR = 25;
    private static final int TOTAL_METHOD_COUNT = 26;

    // CC color bitmasks (colors.white = 1, colors.orange = 2, …,
    // colors.black = 32768).
    private static final double WHITE = 1.0;
    private static final double ORANGE = 2.0;
    private static final double MAGENTA = 4.0;
    private static final double LIGHT_BLUE = 8.0;
    private static final double YELLOW = 16.0;
    private static final double LIME = 32.0;
    private static final double PINK = 64.0;
    private static final double GRAY = 128.0;
    private static final double LIGHT_GRAY = 256.0;
    private static final double CYAN = 512.0;
    private static final double PURPLE = 1024.0;
    private static final double BLUE = 2048.0;
    private static final double BROWN = 4096.0;
    private static final double GREEN = 8192.0;
    private static final double RED = 16384.0;
    private static final double BLACK = 32768.0;

    /** All 16 standard CC color bitmask values. */
    private static final double[] ALL_COLORS = { WHITE, ORANGE, MAGENTA, LIGHT_BLUE, YELLOW, LIME, PINK, GRAY,
        LIGHT_GRAY, CYAN, PURPLE, BLUE, BROWN, GREEN, RED, BLACK };

    private Terminal terminal;

    @BeforeEach
    void setUp() {
        terminal = new Terminal(51, 19);
    }

    // =========================================================================
    // Method surface
    // =========================================================================

    @Test
    void methodCountIsCorrect() {
        MonitorPeripheral peripheral = new MonitorPeripheral(null);
        assertEquals(
            TOTAL_METHOD_COUNT,
            peripheral.getMethodNames().length,
            "MonitorPeripheral must expose exactly " + TOTAL_METHOD_COUNT + " methods");
    }

    @Test
    void methodNamesContainNewGetters() {
        String[] names = new MonitorPeripheral(null).getMethodNames();
        assertEquals("getTextScale", names[IDX_GET_TEXT_SCALE], "index " + IDX_GET_TEXT_SCALE);
        assertEquals("getCursorBlink", names[IDX_GET_CURSOR_BLINK], "index " + IDX_GET_CURSOR_BLINK);
        assertEquals("setPaletteColor", names[IDX_SET_PALETTE_COLOR], "index " + IDX_SET_PALETTE_COLOR);
        assertEquals("setPaletteColour", names[IDX_SET_PALETTE_COLOUR], "index " + IDX_SET_PALETTE_COLOUR);
        assertEquals("getPaletteColor", names[IDX_GET_PALETTE_COLOR], "index " + IDX_GET_PALETTE_COLOR);
        assertEquals("getPaletteColour", names[IDX_GET_PALETTE_COLOUR], "index " + IDX_GET_PALETTE_COLOUR);
    }

    @Test
    void existingMethodIndicesAreUnchanged() {
        String[] names = new MonitorPeripheral(null).getMethodNames();
        assertEquals("write", names[IDX_WRITE], "index " + IDX_WRITE);
        assertEquals("setTextScale", names[IDX_SET_TEXT_SCALE], "index " + IDX_SET_TEXT_SCALE);
        assertEquals("setTextColour", names[IDX_SET_TEXT_COLOUR], "index " + IDX_SET_TEXT_COLOUR);
        assertEquals("setTextColor", names[IDX_SET_TEXT_COLOR], "index " + IDX_SET_TEXT_COLOR);
        assertEquals("setBackgroundColour", names[IDX_SET_BG_COLOUR], "index " + IDX_SET_BG_COLOUR);
        assertEquals("setBackgroundColor", names[IDX_SET_BG_COLOR], "index " + IDX_SET_BG_COLOR);
        assertEquals("blit", names[IDX_BLIT], "index " + IDX_BLIT);
    }

    // =========================================================================
    // setTextColor / setBackgroundColor — all 16 colors accepted (monitor path)
    // =========================================================================

    @Test
    void parseColourAcceptsWhiteOnMonitor() throws LuaException {
        int result = TermAPI.parseColour(new Object[] { WHITE }, true);
        assertEquals(0, result);
    }

    @Test
    void parseColourAcceptsBlackOnMonitor() throws LuaException {
        int result = TermAPI.parseColour(new Object[] { BLACK }, true);
        assertEquals(15, result);
    }

    @Test
    void parseColourAcceptsAllSixteenColorsOnMonitor() {
        for (double bitmask : ALL_COLORS) {
            assertDoesNotThrow(
                () -> TermAPI.parseColour(new Object[] { bitmask }, true),
                "Expected color bitmask " + (int) bitmask + " to be accepted on monitor");
        }
    }

    @Test
    void parseColourReturnsCorrectIndexForEachColor() throws LuaException {
        // bitmask 2^n → index n
        for (int n = 0; n < 16; n++) {
            double bitmask = Math.pow(2, n);
            int idx = TermAPI.parseColour(new Object[] { bitmask }, true);
            assertEquals(n, idx, "Bitmask 2^" + n + " should map to index " + n);
        }
    }

    // =========================================================================
    // Contrast: non-color computer rejects chromatic colors
    // =========================================================================

    @Test
    void parseColourRejectsOrangeOnNonColorComputer() {
        LuaException ex = assertThrows(LuaException.class, () -> TermAPI.parseColour(new Object[] { ORANGE }, false));
        assertEquals("Colour not supported", ex.getMessage());
    }

    @Test
    void parseColourAcceptsGreyscaleColorsOnNonColorComputer() {
        double[] greyscale = { WHITE, GRAY, LIGHT_GRAY, BLACK };
        for (double bitmask : greyscale) {
            assertDoesNotThrow(
                () -> TermAPI.parseColour(new Object[] { bitmask }, false),
                "Expected greyscale bitmask " + (int) bitmask + " to be accepted");
        }
    }

    // =========================================================================
    // setPaletteColor / getPaletteColor — Terminal palette round-trip
    // =========================================================================

    @Test
    void setPaletteColorStoresRgbComponents() {
        // Mimics the logic of MonitorPeripheral cases 22/23.
        terminal.setPaletteColour(0, 1.0, 0.5, 0.25);
        double[] rgb = terminal.getPaletteColour(0);
        assertEquals(1.0, rgb[0], 1e-9, "r");
        assertEquals(0.5, rgb[1], 1e-9, "g");
        assertEquals(0.25, rgb[2], 1e-9, "b");
    }

    @Test
    void setPaletteColorAllSixteenSlots() {
        for (int i = 0; i < 16; i++) {
            double v = i / 15.0;
            terminal.setPaletteColour(i, v, v, v);
            double[] rgb = terminal.getPaletteColour(i);
            assertEquals(v, rgb[0], 1e-9, "slot " + i + " r");
        }
    }

    @Test
    void getPaletteColorReturnsCopyNotReference() {
        terminal.setPaletteColour(0, 0.1, 0.2, 0.3);
        double[] first = terminal.getPaletteColour(0);
        double[] second = terminal.getPaletteColour(0);
        assertNotSame(first, second, "getPaletteColour must return a copy");
    }

    @Test
    void parseColourRejectsOutOfRangeColorForPalette() {
        // 65536 = 2^16 — one bit above the valid range.
        LuaException ex = assertThrows(LuaException.class, () -> TermAPI.parseColour(new Object[] { 65536.0 }, true));
        assertEquals("Colour out of range", ex.getMessage());
    }

    // =========================================================================
    // Shared error paths
    // =========================================================================

    @Test
    void parseColourThrowsOnZero() {
        LuaException ex = assertThrows(LuaException.class, () -> TermAPI.parseColour(new Object[] { 0.0 }, true));
        assertEquals("Colour out of range", ex.getMessage());
    }

    @Test
    void parseColourThrowsOnNegative() {
        LuaException ex = assertThrows(LuaException.class, () -> TermAPI.parseColour(new Object[] { -1.0 }, true));
        assertEquals("Colour out of range", ex.getMessage());
    }

    @Test
    void parseColourThrowsOnNonNumber() {
        LuaException ex = assertThrows(LuaException.class, () -> TermAPI.parseColour(new Object[] { "red" }, true));
        assertEquals("Expected number", ex.getMessage());
    }

    @Test
    void parseColourThrowsOnEmptyArgs() {
        LuaException ex = assertThrows(LuaException.class, () -> TermAPI.parseColour(new Object[0], true));
        assertEquals("Expected number", ex.getMessage());
    }
}
