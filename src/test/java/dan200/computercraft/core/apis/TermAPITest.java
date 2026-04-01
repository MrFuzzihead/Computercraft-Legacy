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
 * Unit tests for {@link TermAPI} covering the {@code term.getCursorBlink} method.
 *
 * <p>
 * Tests call {@link TermAPI#callMethod} directly with a {@code null}
 * {@link dan200.computercraft.api.lua.ILuaContext}; {@code getCursorBlink} and
 * {@code setCursorBlink} do not schedule work on the main thread, so this is safe.
 * </p>
 */
class TermAPITest {

    // Method indices must match the order declared in TermAPI.getMethodNames().
    private static final int METHOD_SET_CURSOR_BLINK = 3;
    private static final int METHOD_GET_CURSOR_BLINK = 19;

    private TermAPI api;

    @BeforeEach
    void setUp() {
        Terminal terminal = new Terminal(51, 19);
        api = new TermAPI(new StubEnv(terminal));
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
