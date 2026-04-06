package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.function.VarArgFunction;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.core.lua.lib.cobalt.CobaltConverter;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * Tests for the two new functions added to the {@code peripheral} Lua API:
 * {@code getName} and {@code hasType}.
 *
 * <p>
 * Each test loads the {@code peripheral} ROM file into a fresh
 * {@link CobaltMachine} with mock {@code peripheral} (native) and {@code rs}
 * globals, then exercises the new API via the injected {@code _capture} helper.
 * </p>
 */
class PeripheralAPITest {

    private static String peripheralSource;

    /**
     * Preamble that installs mock {@code peripheral} (native) and {@code rs}
     * globals so the peripheral.lua API can be loaded without a live Minecraft
     * server. Two fake sides are defined:
     * <ul>
     * <li>{@code "left"} — type {@code "monitor"}, methods {@code write}/{@code getSize}</li>
     * <li>{@code "right"} — type {@code "printer"}, methods {@code newPage}/{@code endPage}</li>
     * </ul>
     */
    private static final String MOCK_PREAMBLE = "peripheral = {\n"
        + "  isPresent  = function(side) return side == 'left' or side == 'right' end,\n"
        + "  getType    = function(side)\n"
        + "    if side == 'left'  then return 'monitor' end\n"
        + "    if side == 'right' then return 'printer'  end\n"
        + "    return nil\n"
        + "  end,\n"
        + "  getMethods = function(side)\n"
        + "    if side == 'left'  then return {'write','getSize'} end\n"
        + "    if side == 'right' then return {'newPage','endPage'} end\n"
        + "    return {}\n"
        + "  end,\n"
        + "  call = function(side, method, ...) return nil end,\n"
        + "}\n"
        + "rs = { getSides = function() return {} end }\n";

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @BeforeAll
    static void loadSources() throws IOException {
        peripheralSource = readResource("/assets/computercraft/lua/rom/apis/peripheral");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream is = PeripheralAPITest.class.getResourceAsStream(path)) {
            assertNotNull(is, path + " must be on the test classpath");
            try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                return scanner.hasNext() ? scanner.next() : "";
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    static class ResultCapture {

        Object[] args;
    }

    private static CobaltMachine buildMachine(ResultCapture capture) {
        ComputerCraft.bigInteger = false;
        ComputerCraft.bitop = false;
        ComputerCraft.timeoutError = false;
        CobaltMachine machine = new CobaltMachine(null);
        injectCapture(machine, capture);
        return machine;
    }

    private static void injectCapture(CobaltMachine machine, ResultCapture capture) {
        try {
            Field f = CobaltMachine.class.getDeclaredField("globals");
            f.setAccessible(true);
            LuaTable globals = (LuaTable) f.get(machine);
            globals.rawset("_capture", new VarArgFunction() {

                @Override
                public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                    capture.args = CobaltConverter.toObjects(args, 1, false);
                    return org.squiddev.cobalt.Constants.NONE;
                }
            });
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to inject _capture", e);
        }
    }

    /** Runs {@code testLua} with the mock preamble and peripheral source pre-loaded. */
    private static void run(CobaltMachine machine, String testLua) {
        String combined = MOCK_PREAMBLE + "\n" + peripheralSource + "\n" + testLua;
        machine.loadBios(new ByteArrayInputStream(combined.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 50 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    // =========================================================================
    // peripheral.getName
    // =========================================================================

    @Test
    void getNameReturnsCorrectSideForWrappedLeft() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local w = wrap('left')\n_capture(getName(w))");
        assertNotNull(cap.args);
        assertEquals("left", cap.args[0]);
    }

    @Test
    void getNameReturnsCorrectSideForWrappedRight() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local w = wrap('right')\n_capture(getName(w))");
        assertNotNull(cap.args);
        assertEquals("right", cap.args[0]);
    }

    @Test
    void getNameErrorsOnNonTable() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, err = pcall(getName, 'left')\n_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "getName('left') must throw — string is not a wrapped table");
        assertTrue(((String) cap.args[1]).contains("Expected table"), "Error message must mention 'Expected table'");
    }

    @Test
    void getNameErrorsOnPlainTable() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, err = pcall(getName, {})\n_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "getName({}) must throw — plain table is not a registered peripheral");
        assertTrue(
            ((String) cap.args[1]).contains("value is not a peripheral"),
            "Error message must mention 'value is not a peripheral'");
    }

    @Test
    void getNameReturnsStringType() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local w = wrap('left')\n_capture(type(getName(w)))");
        assertNotNull(cap.args);
        assertEquals("string", cap.args[0]);
    }

    // =========================================================================
    // peripheral.getType — wrapped-table argument
    // =========================================================================

    @Test
    void getTypeWithWrappedPeripheralReturnsType() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local w = wrap('left')\n_capture(getType(w))");
        assertNotNull(cap.args);
        assertEquals("monitor", cap.args[0]);
    }

    @Test
    void getTypeWithWrappedPeripheralRightSideReturnsType() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local w = wrap('right')\n_capture(getType(w))");
        assertNotNull(cap.args);
        assertEquals("printer", cap.args[0]);
    }

    @Test
    void getTypeErrorsOnPlainTable() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, err = pcall(getType, {})\n_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "getType({}) must throw — plain table is not a registered peripheral");
        assertTrue(
            ((String) cap.args[1]).contains("value is not a peripheral"),
            "Error message must mention 'value is not a peripheral'");
    }

    @Test
    void getTypeErrorsOnNonStringNonTable() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, err = pcall(getType, 42)\n_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "getType(42) must throw — only string or table is accepted");
        assertTrue(
            ((String) cap.args[1]).contains("Expected string or table"),
            "Error message must mention 'Expected string or table'");
    }

    // =========================================================================
    // peripheral.hasType — string-side argument
    // =========================================================================

    @Test
    void hasTypeReturnsTrueForMatchingStringSide() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(hasType('left', 'monitor'))");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0]);
    }

    @Test
    void hasTypeReturnsFalseForNonMatchingStringSide() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(hasType('left', 'printer'))");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0]);
    }

    @Test
    void hasTypeReturnsFalseForAbsentSide() {
        // "top" is not present; getType returns nil, so nil == "monitor" is false.
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(hasType('top', 'monitor'))");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0]);
    }

    // =========================================================================
    // peripheral.hasType — wrapped-table argument
    // =========================================================================

    @Test
    void hasTypeReturnsTrueForMatchingWrappedPeripheral() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local w = wrap('left')\n_capture(hasType(w, 'monitor'))");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0]);
    }

    @Test
    void hasTypeReturnsFalseForNonMatchingWrappedPeripheral() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local w = wrap('right')\n_capture(hasType(w, 'monitor'))");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0]);
    }

    @Test
    void hasTypeErrorsOnPlainTable() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, err = pcall(hasType, {}, 'monitor')\n_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "hasType({}, 'monitor') must throw");
        assertTrue(
            ((String) cap.args[1]).contains("value is not a peripheral"),
            "Error message must mention 'value is not a peripheral'");
    }

    @Test
    void hasTypeErrorsOnNonStringType() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, err = pcall(hasType, 'left', 42)\n_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "hasType('left', 42) must throw — type must be a string");
        assertTrue(
            ((String) cap.args[1]).contains("Expected string"),
            "Error message must mention 'Expected string'");
    }

    @Test
    void hasTypeErrorsOnNonStringNonTableFirstArg() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, err = pcall(hasType, 42, 'monitor')\n_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "hasType(42, 'monitor') must throw — first arg must be string or table");
        assertTrue(
            ((String) cap.args[1]).contains("Expected string or table"),
            "Error message must mention 'Expected string or table'");
    }

    // =========================================================================
    // wrap registers correctly for both getName and hasType
    // =========================================================================

    @Test
    void multipleWrappedPeripheralsAreTrackedIndependently() {
        // Calling wrap() for two different sides must register each result
        // in the wrapNames registry so getName() returns the correct side for each.
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local w1 = wrap('left')\n"
                + "local w2 = wrap('right')\n"
                + "_capture(getName(w1), getName(w2))");
        assertNotNull(cap.args);
        assertEquals("left", cap.args[0]);
        assertEquals("right", cap.args[1]);
    }
}


