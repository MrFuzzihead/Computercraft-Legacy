package dan200.computercraft.core.apis;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.IComputerEnvironment;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.terminal.Terminal;

/**
 * Unit tests for {@link TermAPI} covering the {@code term.getCursorBlink} method
 * and the palette methods ({@code nativePaletteColor}, {@code setPaletteColor},
 * {@code getPaletteColor}) including British {@code *Colour} variants.
 *
 * <p>
 * Tests call {@link TermAPI#callMethod} directly with a {@code null}
 * {@link dan200.computercraft.api.lua.ILuaContext}; none of the tested methods
 * schedule work on the main thread, so this is safe.
 * </p>
 */
class TermAPITest {

    // Method indices must match the order declared in TermAPI.getMethodNames().
    private static final int METHOD_SET_TEXT_COLOUR = 8;
    private static final int METHOD_SET_TEXT_COLOR = 9;
    private static final int METHOD_SET_BG_COLOUR = 10;
    private static final int METHOD_SET_BG_COLOR = 11;
    private static final int METHOD_SET_CURSOR_BLINK = 3;
    private static final int METHOD_GET_CURSOR_BLINK = 19;
    private static final int METHOD_NATIVE_PALETTE_COLOR = 20;
    private static final int METHOD_NATIVE_PALETTE_COLOUR = 21;
    private static final int METHOD_SET_PALETTE_COLOR = 22;
    private static final int METHOD_SET_PALETTE_COLOUR = 23;
    private static final int METHOD_GET_PALETTE_COLOR = 24;
    private static final int METHOD_GET_PALETTE_COLOUR = 25;

    // colors.white = 1 (2^0); colors.black = 32768 (2^15)
    private static final double WHITE_BITMASK = 1.0;
    private static final double BLACK_BITMASK = 32768.0;

    private Terminal terminal;
    private TermAPI api;

    @BeforeEach
    void setUp() {
        terminal = new Terminal(51, 19);
        api = new TermAPI(new StubEnv(terminal));
    }

    // =========================================================================
    // term.setTextColor / setBackgroundColor — all 16 colors on any computer
    // =========================================================================

    @Test
    void setTextColorAcceptsChromaticColorOnNonColorComputer() {
        // StubEnv returns isColour()=false; orange (bitmask 2) was previously rejected.
        assertDoesNotThrow(
            () -> api.callMethod(null, METHOD_SET_TEXT_COLOR, new Object[] { 2.0 }),
            "setTextColor(orange) must succeed on a non-color computer");
    }

    @Test
    void setTextColourAcceptsChromaticColorOnNonColorComputer() {
        assertDoesNotThrow(
            () -> api.callMethod(null, METHOD_SET_TEXT_COLOUR, new Object[] { 2.0 }),
            "setTextColour(orange) must succeed on a non-color computer");
    }

    @Test
    void setBackgroundColorAcceptsChromaticColorOnNonColorComputer() {
        assertDoesNotThrow(
            () -> api.callMethod(null, METHOD_SET_BG_COLOR, new Object[] { 2.0 }),
            "setBackgroundColor(orange) must succeed on a non-color computer");
    }

    @Test
    void setBackgroundColourAcceptsChromaticColorOnNonColorComputer() {
        assertDoesNotThrow(
            () -> api.callMethod(null, METHOD_SET_BG_COLOUR, new Object[] { 2.0 }),
            "setBackgroundColour(orange) must succeed on a non-color computer");
    }

    @Test
    void setTextColorAcceptsAllSixteenColors() {
        for (int n = 0; n < 16; n++) {
            double bitmask = Math.pow(2, n);
            assertDoesNotThrow(
                () -> api.callMethod(null, METHOD_SET_TEXT_COLOR, new Object[] { bitmask }),
                "setTextColor must accept bitmask 2^" + n);
        }
    }

    @Test
    void setBackgroundColorAcceptsAllSixteenColors() {
        for (int n = 0; n < 16; n++) {
            double bitmask = Math.pow(2, n);
            assertDoesNotThrow(
                () -> api.callMethod(null, METHOD_SET_BG_COLOR, new Object[] { bitmask }),
                "setBackgroundColor must accept bitmask 2^" + n);
        }
    }

    @Test
    void setTextColorStillRejectsInvalidColor() {
        // Out-of-range colors must still be rejected regardless of the isColour change.
        assertThrows(
            LuaException.class,
            () -> api.callMethod(null, METHOD_SET_TEXT_COLOR, new Object[] { 0.0 }),
            "setTextColor(0) must still throw 'Colour out of range'");
    }

    @Test
    void setBackgroundColorStillRejectsInvalidColor() {
        assertThrows(
            LuaException.class,
            () -> api.callMethod(null, METHOD_SET_BG_COLOR, new Object[] { 0.0 }),
            "setBackgroundColor(0) must still throw 'Colour out of range'");
    }

    // =========================================================================
    // term.getCursorBlink
    // =========================================================================

    @Test
    void getCursorBlinkIsFalseByDefault() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_GET_CURSOR_BLINK, new Object[0]);

        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals(Boolean.FALSE, result[0]);
    }

    @Test
    void getCursorBlinkReturnsTrueAfterSetTrue() throws LuaException {
        api.callMethod(null, METHOD_SET_CURSOR_BLINK, new Object[] { true });

        Object[] result = api.callMethod(null, METHOD_GET_CURSOR_BLINK, new Object[0]);

        assertNotNull(result);
        assertEquals(Boolean.TRUE, result[0]);
    }

    @Test
    void getCursorBlinkReturnsFalseAfterSetFalse() throws LuaException {
        api.callMethod(null, METHOD_SET_CURSOR_BLINK, new Object[] { true });
        api.callMethod(null, METHOD_SET_CURSOR_BLINK, new Object[] { false });

        Object[] result = api.callMethod(null, METHOD_GET_CURSOR_BLINK, new Object[0]);

        assertNotNull(result);
        assertEquals(Boolean.FALSE, result[0]);
    }

    @Test
    void getCursorBlinkRoundTrips() throws LuaException {
        for (boolean expected : new boolean[] { true, false, true, false }) {
            api.callMethod(null, METHOD_SET_CURSOR_BLINK, new Object[] { expected });

            Object[] result = api.callMethod(null, METHOD_GET_CURSOR_BLINK, new Object[0]);

            assertNotNull(result);
            assertEquals(expected, result[0], "round-trip failed for value: " + expected);
        }
    }

    // =========================================================================
    // term.nativePaletteColor / nativePaletteColour
    // =========================================================================

    @Test
    void nativePaletteColorWhiteReturnsDefaultRGB() throws LuaException {
        // colors.white bitmask = 1 → blit index 0 → DEFAULT_PALETTE_HEX[0] = 0xF0F0F0
        double expected = 0xF0 / 255.0;
        Object[] result = api.callMethod(null, METHOD_NATIVE_PALETTE_COLOR, new Object[] { WHITE_BITMASK });

        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(expected, (Double) result[0], 1e-6, "r");
        assertEquals(expected, (Double) result[1], 1e-6, "g");
        assertEquals(expected, (Double) result[2], 1e-6, "b");
    }

    @Test
    void nativePaletteColorBlackReturnsDefaultRGB() throws LuaException {
        // colors.black bitmask = 32768 → blit index 15 → DEFAULT_PALETTE_HEX[15] = 0x191919
        double expected = 0x19 / 255.0;
        Object[] result = api.callMethod(null, METHOD_NATIVE_PALETTE_COLOR, new Object[] { BLACK_BITMASK });

        assertNotNull(result);
        assertEquals(expected, (Double) result[0], 1e-6, "r");
        assertEquals(expected, (Double) result[1], 1e-6, "g");
        assertEquals(expected, (Double) result[2], 1e-6, "b");
    }

    @Test
    void nativePaletteColorIsUnaffectedBySetPaletteColor() throws LuaException {
        // Remap white to red, then verify nativePaletteColor still returns original white.
        api.callMethod(null, METHOD_SET_PALETTE_COLOR, new Object[] { WHITE_BITMASK, 1.0, 0.0, 0.0 });

        double expectedDefault = 0xF0 / 255.0;
        Object[] result = api.callMethod(null, METHOD_NATIVE_PALETTE_COLOR, new Object[] { WHITE_BITMASK });

        assertNotNull(result);
        assertEquals(expectedDefault, (Double) result[0], 1e-6, "native r should still be default white");
    }

    @Test
    void nativePaletteColourMatchesNativePaletteColor() throws LuaException {
        Object[] american = api.callMethod(null, METHOD_NATIVE_PALETTE_COLOR, new Object[] { WHITE_BITMASK });
        Object[] british = api.callMethod(null, METHOD_NATIVE_PALETTE_COLOUR, new Object[] { WHITE_BITMASK });

        assertNotNull(american);
        assertNotNull(british);
        assertEquals(american[0], british[0]);
        assertEquals(american[1], british[1]);
        assertEquals(american[2], british[2]);
    }

    // =========================================================================
    // term.setPaletteColor / setPaletteColour
    // =========================================================================

    @Test
    void setPaletteColorStoresCustomRGB() throws LuaException {
        api.callMethod(null, METHOD_SET_PALETTE_COLOR, new Object[] { WHITE_BITMASK, 0.5, 0.25, 0.75 });

        Object[] result = api.callMethod(null, METHOD_GET_PALETTE_COLOR, new Object[] { WHITE_BITMASK });

        assertNotNull(result);
        assertEquals(0.5, (Double) result[0], 1e-9, "r");
        assertEquals(0.25, (Double) result[1], 1e-9, "g");
        assertEquals(0.75, (Double) result[2], 1e-9, "b");
    }

    @Test
    void setPaletteColourEquivalentToSetPaletteColor() throws LuaException {
        api.callMethod(null, METHOD_SET_PALETTE_COLOUR, new Object[] { BLACK_BITMASK, 0.1, 0.2, 0.3 });

        Object[] result = api.callMethod(null, METHOD_GET_PALETTE_COLOUR, new Object[] { BLACK_BITMASK });

        assertNotNull(result);
        assertEquals(0.1, (Double) result[0], 1e-9, "r");
        assertEquals(0.2, (Double) result[1], 1e-9, "g");
        assertEquals(0.3, (Double) result[2], 1e-9, "b");
    }

    @Test
    void setPaletteColorZeroBitmaskThrows() {
        assertThrows(
            LuaException.class,
            () -> api.callMethod(null, METHOD_SET_PALETTE_COLOR, new Object[] { 0.0, 1.0, 1.0, 1.0 }));
    }

    @Test
    void setPaletteColorOutOfRangeColorBitmaskThrows() {
        // 2^16 = 65536 → blit index 16, which is > 15
        assertThrows(
            LuaException.class,
            () -> api.callMethod(null, METHOD_SET_PALETTE_COLOR, new Object[] { 65536.0, 1.0, 1.0, 1.0 }));
    }

    @Test
    void setPaletteColorOutOfRangeRGBThrows() {
        assertThrows(
            LuaException.class,
            () -> api.callMethod(null, METHOD_SET_PALETTE_COLOR, new Object[] { WHITE_BITMASK, 1.5, 0.0, 0.0 }));
    }

    // =========================================================================
    // term.getPaletteColor / getPaletteColour
    // =========================================================================

    @Test
    void getPaletteColorReturnsDefaultsInitially() throws LuaException {
        // Default white: 0xF0F0F0
        double expected = 0xF0 / 255.0;
        Object[] result = api.callMethod(null, METHOD_GET_PALETTE_COLOR, new Object[] { WHITE_BITMASK });

        assertNotNull(result);
        assertEquals(expected, (Double) result[0], 1e-6, "r");
        assertEquals(expected, (Double) result[1], 1e-6, "g");
        assertEquals(expected, (Double) result[2], 1e-6, "b");
    }

    @Test
    void getPaletteColourMatchesGetPaletteColor() throws LuaException {
        api.callMethod(null, METHOD_SET_PALETTE_COLOR, new Object[] { WHITE_BITMASK, 0.3, 0.6, 0.9 });

        Object[] american = api.callMethod(null, METHOD_GET_PALETTE_COLOR, new Object[] { WHITE_BITMASK });
        Object[] british = api.callMethod(null, METHOD_GET_PALETTE_COLOUR, new Object[] { WHITE_BITMASK });

        assertNotNull(american);
        assertNotNull(british);
        assertEquals(american[0], british[0]);
        assertEquals(american[1], british[1]);
        assertEquals(american[2], british[2]);
    }

    @Test
    void terminalResetRestoresPaletteDefaults() throws LuaException {
        // Remap white to red.
        api.callMethod(null, METHOD_SET_PALETTE_COLOR, new Object[] { WHITE_BITMASK, 1.0, 0.0, 0.0 });

        // Verify the change is in effect.
        Object[] before = api.callMethod(null, METHOD_GET_PALETTE_COLOR, new Object[] { WHITE_BITMASK });
        assertEquals(1.0, (Double) before[0], 1e-9, "r should be 1.0 after set");

        // Reset terminal.
        terminal.reset();

        // Palette should be restored to factory white (0xF0F0F0).
        double expectedDefault = 0xF0 / 255.0;
        Object[] after = api.callMethod(null, METHOD_GET_PALETTE_COLOR, new Object[] { WHITE_BITMASK });
        assertEquals(expectedDefault, (Double) after[0], 1e-6, "r should be restored to default");
    }

    // =========================================================================
    // Stubs
    // =========================================================================

    private static class StubEnv implements IAPIEnvironment {

        private final Terminal m_terminal;

        StubEnv(Terminal terminal) {
            m_terminal = terminal;
        }

        @Override
        public Terminal getTerminal() {
            return m_terminal;
        }

        @Override
        public IComputerEnvironment getComputerEnvironment() {
            return new IComputerEnvironment() {

                @Override
                public int getDay() {
                    return 0;
                }

                @Override
                public double getTimeOfDay() {
                    return 0;
                }

                @Override
                public boolean isColour() {
                    return false;
                }

                @Override
                public long getComputerSpaceLimit() {
                    return 0;
                }

                @Override
                public int assignNewID() {
                    return 0;
                }

                @Override
                public IWritableMount createSaveDirMount(String subPath, long capacity) {
                    return null;
                }

                @Override
                public IMount createResourceMount(String domain, String subPath) {
                    return null;
                }
            };
        }

        @Override
        public Computer getComputer() {
            return null;
        }

        @Override
        public int getComputerID() {
            return 1;
        }

        @Override
        public void shutdown() {}

        @Override
        public void reboot() {}

        @Override
        public void queueEvent(String event, Object[] args) {}

        @Override
        public FileSystem getFileSystem() {
            return null;
        }

        @Override
        public void setOutput(int side, int output) {}

        @Override
        public int getOutput(int side) {
            return 0;
        }

        @Override
        public int getInput(int side) {
            return 0;
        }

        @Override
        public void setBundledOutput(int side, int output) {}

        @Override
        public int getBundledOutput(int side) {
            return 0;
        }

        @Override
        public int getBundledInput(int side) {
            return 0;
        }

        @Override
        public void setPeripheralChangeListener(IAPIEnvironment.IPeripheralChangeListener listener) {}

        @Override
        public IPeripheral getPeripheral(int side) {
            return null;
        }

        @Override
        public String getLabel() {
            return null;
        }

        @Override
        public void setLabel(String label) {}
    }
}
