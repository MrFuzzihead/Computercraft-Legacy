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
 * Tests for the three additions to the {@code window} Lua API:
 * <ul>
 *   <li>{@code window.getLine(y)} — returns text/textColor/backgroundColor for a line</li>
 *   <li>{@code window.isVisible()} — reflects the current visibility state</li>
 *   <li>{@code window.reposition} — optional 5th {@code newParent} parameter</li>
 * </ul>
 *
 * <p>Each test loads the {@code window} ROM file into a fresh {@link CobaltMachine}
 * after setting up a {@code colors} table and a minimal mock parent terminal.</p>
 */
class WindowAPITest {

    private static String windowSource;

    /**
     * Lua preamble: defines the {@code colors} table that window.lua needs at load
     * time (for {@code tHex}), a bare {@code term} global so the parent-check guard
     * in {@code create} does not fire, and a {@code mockParent()} factory function
     * that returns a minimal but complete terminal-surface table.
     */
    private static final String PREAMBLE = "colors = {\n"
        + "  white=1, orange=2, magenta=4, lightBlue=8,\n"
        + "  yellow=16, lime=32, pink=64, gray=128,\n"
        + "  lightGray=256, cyan=512, purple=1024, blue=2048,\n"
        + "  brown=4096, green=8192, red=16384, black=32768\n"
        + "}\n"
        + "term = {}\n"
        + "function mockParent()\n"
        + "  return {\n"
        + "    isColor            = function() return true end,\n"
        + "    setCursorPos       = function(x, y) end,\n"
        + "    setCursorBlink     = function(b) end,\n"
        + "    setTextColor       = function(c) end,\n"
        + "    blit               = function(t, tc, bc) end,\n"
        + "    setPaletteColor    = function(c, r, g, b) end,\n"
        + "    setPaletteColour   = function(c, r, g, b) end,\n"
        + "    getPaletteColor    = function(c) return 1, 1, 1 end,\n"
        + "    getPaletteColour   = function(c) return 1, 1, 1 end,\n"
        + "    nativePaletteColor  = function(c) return 1, 1, 1 end,\n"
        + "    nativePaletteColour = function(c) return 1, 1, 1 end,\n"
        + "  }\n"
        + "end\n";

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @BeforeAll
    static void loadSources() throws IOException {
        windowSource = readResource("/assets/computercraft/lua/rom/apis/window");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream is = WindowAPITest.class.getResourceAsStream(path)) {
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

    private static void run(CobaltMachine machine, String testLua) {
        String combined = PREAMBLE + windowSource + "\n" + testLua;
        machine.loadBios(new ByteArrayInputStream(combined.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 50 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    // =========================================================================
    // window.getLine
    // =========================================================================

    @Test
    void getLineFreshWindowReturnsEmptyLine() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local w = create(mockParent(), 1, 1, 10, 5)\n"
                + "_capture(w.getLine(1))");
        assertNotNull(cap.args);
        // Default text color = white ("0"), background = black ("f"), text = spaces
        assertEquals("          ", cap.args[0], "fresh line text must be 10 spaces");
        assertEquals("0000000000", cap.args[1], "fresh line textColor must be all white (\"0\")");
        assertEquals("ffffffffff", cap.args[2], "fresh line backgroundColor must be all black (\"f\")");
    }

    @Test
    void getLineAfterWriteReturnsWrittenText() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local w = create(mockParent(), 1, 1, 10, 5)\n"
                + "w.write('hello')\n"
                + "_capture(w.getLine(1))");
        assertNotNull(cap.args);
        assertEquals("hello     ", cap.args[0], "written text must appear padded to full width");
        assertEquals("0000000000", cap.args[1], "textColor must remain all white");
        assertEquals("ffffffffff", cap.args[2], "backgroundColor must remain all black");
    }

    @Test
    void getLineReadsCorrectRow() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local w = create(mockParent(), 1, 1, 10, 5)\n"
                + "w.setCursorPos(1, 3)\n"
                + "w.write('row3')\n"
                + "_capture(w.getLine(3))");
        assertNotNull(cap.args);
        assertEquals("row3      ", cap.args[0], "getLine(3) must return text written on row 3");
    }

    @Test
    void getLineRow1IsUnaffectedByWriteOnRow3() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local w = create(mockParent(), 1, 1, 10, 5)\n"
                + "w.setCursorPos(1, 3)\n"
                + "w.write('row3')\n"
                + "_capture(w.getLine(1))");
        assertNotNull(cap.args);
        assertEquals("          ", cap.args[0], "row 1 must be untouched when only row 3 was written");
    }

    @Test
    void getLineErrorOnNonNumberArgument() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local w = create(mockParent(), 1, 1, 10, 5)\n"
                + "local ok, err = pcall(function() w.getLine('x') end)\n"
                + "_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "getLine('x') must throw");
        assertTrue(cap.args[1].toString().contains("expected number"), "error must mention 'expected number'");
    }

    @Test
    void getLineErrorOnIndexAboveHeight() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local w = create(mockParent(), 1, 1, 10, 5)\n"
                + "local ok, err = pcall(function() w.getLine(6) end)\n"
                + "_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "getLine(6) on a height-5 window must throw");
        assertTrue(cap.args[1].toString().contains("out of range"), "error must mention 'out of range'");
    }

    @Test
    void getLineErrorOnZero() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local w = create(mockParent(), 1, 1, 10, 5)\n"
                + "local ok, err = pcall(function() w.getLine(0) end)\n"
                + "_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "getLine(0) must throw (lines are 1-indexed)");
        assertTrue(cap.args[1].toString().contains("out of range"), "error must mention 'out of range'");
    }

    // =========================================================================
    // window.isVisible
    // =========================================================================

    @Test
    void isVisibleTrueByDefault() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local w = create(mockParent(), 1, 1, 5, 5)\n"
                + "_capture(w.isVisible())");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "window must be visible by default");
    }

    @Test
    void isVisibleFalseWhenCreatedHidden() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local w = create(mockParent(), 1, 1, 5, 5, false)\n"
                + "_capture(w.isVisible())");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "window created with bStartVisible=false must not be visible");
    }

    @Test
    void isVisibleTrueAfterSetVisibleTrue() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local w = create(mockParent(), 1, 1, 5, 5, false)\n"
                + "w.setVisible(true)\n"
                + "_capture(w.isVisible())");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "isVisible() must return true after setVisible(true)");
    }

    @Test
    void isVisibleFalseAfterSetVisibleFalse() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local w = create(mockParent(), 1, 1, 5, 5)\n"
                + "w.setVisible(false)\n"
                + "_capture(w.isVisible())");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "isVisible() must return false after setVisible(false)");
    }

    // =========================================================================
    // window.reposition — new optional newParent (#5) parameter
    // =========================================================================

    @Test
    void repositionWithoutNewParentKeepsOriginalParent() {
        ResultCapture cap = new ResultCapture();
        // p1 counts blit calls; window starts hidden, reposition, then show.
        // All redraws must go to p1 since no new parent was supplied.
        run(
            buildMachine(cap),
            "local calls = 0\n"
                + "local p1 = {\n"
                + "  isColor=function() return true end,\n"
                + "  setCursorPos=function() end, setCursorBlink=function() end,\n"
                + "  setTextColor=function() end,\n"
                + "  blit=function() calls = calls + 1 end,\n"
                + "  setPaletteColor=function() end, setPaletteColour=function() end,\n"
                + "  getPaletteColor=function() return 1,1,1 end,\n"
                + "  getPaletteColour=function() return 1,1,1 end,\n"
                + "  nativePaletteColor=function() return 1,1,1 end,\n"
                + "  nativePaletteColour=function() return 1,1,1 end,\n"
                + "}\n"
                + "local w = create(p1, 1, 1, 5, 3, false)\n"
                + "w.reposition(2, 2)\n"
                + "w.setVisible(true)\n"
                + "_capture(calls)");
        assertNotNull(cap.args);
        assertTrue(((Number) cap.args[0]).doubleValue() > 0, "p1 must receive redraws after reposition without new parent");
    }

    @Test
    void repositionSwitchesParent() {
        ResultCapture cap = new ResultCapture();
        // Window starts hidden on p1. After reposition with p2, show triggers draw to p2 only.
        run(
            buildMachine(cap),
            "local calls1, calls2 = 0, 0\n"
                + "local p1 = {\n"
                + "  isColor=function() return true end,\n"
                + "  setCursorPos=function() end, setCursorBlink=function() end,\n"
                + "  setTextColor=function() end,\n"
                + "  blit=function() calls1 = calls1 + 1 end,\n"
                + "  setPaletteColor=function() end, setPaletteColour=function() end,\n"
                + "  getPaletteColor=function() return 1,1,1 end,\n"
                + "  getPaletteColour=function() return 1,1,1 end,\n"
                + "  nativePaletteColor=function() return 1,1,1 end,\n"
                + "  nativePaletteColour=function() return 1,1,1 end,\n"
                + "}\n"
                + "local p2 = {\n"
                + "  isColor=function() return true end,\n"
                + "  setCursorPos=function() end, setCursorBlink=function() end,\n"
                + "  setTextColor=function() end,\n"
                + "  blit=function() calls2 = calls2 + 1 end,\n"
                + "  setPaletteColor=function() end, setPaletteColour=function() end,\n"
                + "  getPaletteColor=function() return 1,1,1 end,\n"
                + "  getPaletteColour=function() return 1,1,1 end,\n"
                + "  nativePaletteColor=function() return 1,1,1 end,\n"
                + "  nativePaletteColour=function() return 1,1,1 end,\n"
                + "}\n"
                + "local w = create(p1, 1, 1, 5, 3, false)\n"
                + "w.reposition(1, 1, nil, nil, p2)\n"
                + "w.setVisible(true)\n"
                + "_capture(calls1, calls2)");
        assertNotNull(cap.args);
        assertEquals(0.0, ((Number) cap.args[0]).doubleValue(), "p1 must not receive draws after parent swap");
        assertTrue(((Number) cap.args[1]).doubleValue() > 0, "p2 must receive draws after parent swap");
    }

    @Test
    void repositionNewParentNonTableThrows() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local w = create(mockParent(), 1, 1, 5, 5)\n"
                + "local ok, err = pcall(function() w.reposition(1, 1, nil, nil, 42) end)\n"
                + "_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "reposition with non-table newParent must throw");
        assertTrue(cap.args[1].toString().contains("expected table"), "error must mention 'expected table'");
    }

    @Test
    void repositionNewParentNilDoesNotThrow() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local w = create(mockParent(), 1, 1, 5, 5, false)\n"
                + "local ok = pcall(function() w.reposition(1, 1, nil, nil, nil) end)\n"
                + "_capture(ok)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "reposition with explicit nil newParent must not throw");
    }
}

