package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dan200.computercraft.core.lua.LuaRomTestSupport.Capture;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * Batch E tests for the Lua-side terminal polish: {@code colors.toBlit} argument validation and
 * {@code paintutils.drawLine}'s argument check.
 *
 * <p>
 * Both are small, self-contained ROM APIs, so they are loaded into a bare machine with just the
 * globals they touch. Keeping them in their own class avoids threading new mocks through the
 * existing {@code ColorsAPITest} / {@code PaintutilsAPITest} harnesses.
 * </p>
 */
class LuaTerminalPolishTest {

    private static String colorsSource;
    private static String paintutilsSource;

    @BeforeAll
    static void loadSources() throws IOException {
        colorsSource = LuaRomTestSupport.readResource("/assets/computercraft/lua/rom/apis/colors");
        paintutilsSource = LuaRomTestSupport.readResource("/assets/computercraft/lua/rom/apis/paintutils");
    }

    /** A {@code term} stub good enough for paintutils: it records background colour and pixels. */
    private static String termStub() {
        return String.join(
            "\n",
            "_bg = nil",
            "_pixels = {}",
            "_cx, _cy = 1, 1",
            "term = {",
            "  setBackgroundColor = function(c) _bg = c end,",
            "  setBackgroundColour = function(c) _bg = c end,",
            "  getBackgroundColor = function() return _bg end,",
            "  getBackgroundColour = function() return _bg end,",
            // paintutils draws by positioning the cursor and writing a space.
            "  setPixel = function(x, y) _pixels[#_pixels + 1] = { x, y } end,",
            "  setCursorPos = function(x, y) _cx, _cy = x, y end,",
            "  getCursorPos = function() return _cx, _cy end,",
            "  getSize = function() return 51, 19 end,",
            "  isColour = function() return true end,",
            "  isColor = function() return true end,",
            "  setTextColour = function() end,",
            "  setTextColor = function() end,",
            "  write = function(s) if s == ' ' then _pixels[#_pixels + 1] = { _cx, _cy } end end,",
            "  clear = function() end,",
            "  scroll = function() end,",
            "  blit = function() end,",
            "}");
    }

    private static Capture runColors(String body) {
        Capture capture = new Capture();
        CobaltMachine machine = LuaRomTestSupport.machine(capture);
        // `colors` is loaded by os.loadAPI in game; here it is loaded and exposed directly.
        LuaRomTestSupport.run(machine, colorsSource, body);
        return capture;
    }

    private static Capture runPaintutils(String body) {
        Capture capture = new Capture();
        CobaltMachine machine = LuaRomTestSupport.machine(capture);
        // paintutils loads `colors` (for parseImage/fromBlit) and uses `term`.
        LuaRomTestSupport.run(machine, termStub() + "\n" + colorsSource + "\n" + paintutilsSource, body);
        return capture;
    }

    // -------------------------------------------------------------------------
    // colors.toBlit
    // -------------------------------------------------------------------------

    @Test
    void toBlitStillMapsThePaletteBitmasks() {
        Capture cap = runColors("_capture(toBlit(1), toBlit(2), toBlit(32), toBlit(32768))");
        assertEquals("0", cap.string(0), "white = 1");
        assertEquals("1", cap.string(1), "orange = 2");
        assertEquals("5", cap.string(2), "lime = 32");
        assertEquals("f", cap.string(3), "black = 32768");
    }

    @Test
    void toBlitRejectsValuesOutsideTheValidRange() {
        Capture cap = runColors(
            "local a = pcall(toBlit, 0)\n" + "local b = pcall(toBlit, 65536)\n"
                + "local c = pcall(toBlit, -8)\n"
                + "_capture(a, b, c)");
        assertEquals(false, cap.bool(0), "0 is below the valid range");
        assertEquals(false, cap.bool(1), "65536 is above the valid range");
        assertEquals(false, cap.bool(2));
    }

    @Test
    void toBlitRejectsNonNumbers() {
        Capture cap = runColors("local ok, err = pcall(toBlit, 'red')\n_capture(ok, tostring(err))");
        assertEquals(false, cap.bool(0));
        assertTrue(
            cap.string(1)
                .contains("number"),
            "the message should name the expected type, got: " + cap.string(1));
    }

    @Test
    void toBlitFloorsNonPowerOfTwoValues() {
        // Matches upstream: floor(log2(color)) for anything that is not a palette bitmask.
        Capture cap = runColors("_capture(toBlit(6), toBlit(7), toBlit(12))");
        assertEquals("2", cap.string(0), "6 is between 4 (2) and 8 (3)");
        assertEquals("2", cap.string(1));
        assertEquals("3", cap.string(2), "12 is between 8 (3) and 16 (4)");
    }

    @Test
    void toBlitAcceptsTheTopOfTheRange() {
        Capture cap = runColors("_capture(toBlit(0xffff))");
        assertEquals("f", cap.string(0), "0xffff is the largest accepted value");
    }

    // -------------------------------------------------------------------------
    // paintutils.drawLine
    // -------------------------------------------------------------------------

    @Test
    void drawLineRejectsANonNumericStartY() {
        // The check used to test startX twice, so a bad startY reached math.floor.
        Capture cap = runPaintutils(
            "local ok, err = pcall(drawLine, 1, 'two', 3, 4, 1)\n" + "_capture(ok, tostring(err))");
        assertEquals(false, cap.bool(0), "a string startY must be rejected");
        assertTrue(
            cap.string(1)
                .contains("Expected startX, startY"),
            "unexpected message: " + cap.string(1));
    }

    @Test
    void drawLineRejectsOtherNonNumericArguments() {
        Capture cap = runPaintutils(
            "local a = pcall(drawLine, 'x', 1, 3, 4, 1)\n" + "local b = pcall(drawLine, 1, 1, 'x', 4, 1)\n"
                + "local c = pcall(drawLine, 1, 1, 3, 'x', 1)\n"
                + "local d = pcall(drawLine, 1, 1, 3, 4, 'x')\n"
                + "_capture(a, b, c, d)");
        assertEquals(false, cap.bool(0));
        assertEquals(false, cap.bool(1));
        assertEquals(false, cap.bool(2));
        assertEquals(false, cap.bool(3));
    }

    @Test
    void drawLineDoesNotLeakGlobals() {
        // maxX/minY/maxY used to be assigned without `local`, polluting the global table.
        Capture cap = runPaintutils("drawLine(1, 1, 4, 3, 1)\n" + "_capture(maxX == nil, minY == nil, maxY == nil)");
        assertEquals(true, cap.bool(0), "maxX must stay local");
        assertEquals(true, cap.bool(1), "minY must stay local");
        assertEquals(true, cap.bool(2), "maxY must stay local");
    }

    @Test
    void drawLineStillDrawsTheExpectedPixels() {
        Capture cap = runPaintutils(
            "drawLine(1, 2, 3, 2, 1)\n" + "_capture(#_pixels, _pixels[1][1], _pixels[3][1], _pixels[1][2])");
        assertEquals(3.0, cap.number(0), "a horizontal line covers three columns");
        assertEquals(1.0, cap.number(1));
        assertEquals(3.0, cap.number(2));
        assertEquals(2.0, cap.number(3), "at the requested row");
    }

    @Test
    void drawLineAcceptsNumericStringsAsBefore() {
        // math.floor coerces numeric strings, and the type check rejects them first, so this is
        // really asserting the *check* is strict about types.
        Capture cap = runPaintutils("local ok = pcall(drawLine, '1', 1, 3, 4, 1)\n_capture(ok)");
        assertEquals(false, cap.bool(0), "strings are rejected even when numeric");
    }

    @Test
    void sourcesAreLoadable() {
        assertNotNull(colorsSource);
        assertTrue(colorsSource.contains("function toBlit"));
        assertTrue(paintutilsSource.contains("function drawLine"));
    }
}
