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
 * Tests for the {@code paintutils} Lua API ({@code rom/apis/paintutils}).
 *
 * <p>
 * Covers all seven public functions:
 * {@code parseImage}, {@code loadImage}, {@code drawPixel}, {@code drawLine},
 * {@code drawBox}, {@code drawFilledBox}, and {@code drawImage}.
 * </p>
 *
 * <p>
 * A mock {@code term} table records every {@code setCursorPos}/{@code write} pair
 * as an entry in {@code _pixels = {x, y, bgColor}}. A mock {@code fs}/{@code io}
 * pair allows {@code loadImage} to read from an in-memory string without touching
 * the real filesystem.
 * </p>
 */
class PaintutilsAPITest {

    private static String paintutilsSource;

    /**
     * Mock {@code term} preamble.
     * <ul>
     * <li>{@code _pixels} — sequential table of {@code {x, y, bgColor}} entries;
     * appended each time {@code term.write} is called.</li>
     * <li>{@code _bg} — current background color, mutated by
     * {@code term.setBackgroundColor}.</li>
     * </ul>
     */
    private static final String TERM_MOCK = "_pixels = {}\n" + "_last_x, _last_y, _bg = 1, 1, 1\n"
        + "term = {\n"
        + "  setCursorPos        = function(x, y) _last_x, _last_y = x, y end,\n"
        + "  setBackgroundColor  = function(c) _bg = c end,\n"
        + "  setBackgroundColour = function(c) _bg = c end,\n"
        + "  write               = function(s) _pixels[#_pixels+1] = {_last_x, _last_y, _bg} end,\n"
        + "}\n";

    /**
     * Mock {@code fs} and {@code io} preamble for {@code loadImage} tests.
     * Files are registered as {@code _mock_files["path"] = "content"}.
     * {@code fs.exists} returns {@code true} only for registered paths.
     * {@code io.open} returns a handle whose {@code read("*a")} yields the content.
     */
    private static final String FS_IO_MOCK = "_mock_files = {}\n"
        + "fs = { exists = function(p) return _mock_files[p] ~= nil end }\n"
        + "io = {\n"
        + "  open = function(path, mode)\n"
        + "    local c = _mock_files[path]\n"
        + "    if not c then return nil end\n"
        + "    return { read = function(self, fmt) return c end,\n"
        + "             close = function(self) end }\n"
        + "  end,\n"
        + "}\n";

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @BeforeAll
    static void loadSource() throws IOException {
        paintutilsSource = readResource("/assets/computercraft/lua/rom/apis/paintutils");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream is = PaintutilsAPITest.class.getResourceAsStream(path)) {
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

    /** Runs {@code testLua} with the paintutils source pre-loaded (no extra mocks). */
    private static void run(CobaltMachine machine, String testLua) {
        runWith(machine, "", testLua);
    }

    /** Runs {@code testLua} with {@code extraPreamble} injected before the paintutils source. */
    private static void runWith(CobaltMachine machine, String extraPreamble, String testLua) {
        String combined = extraPreamble + "\n" + paintutilsSource + "\n" + testLua;
        machine.loadBios(new ByteArrayInputStream(combined.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 50 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    // =========================================================================
    // parseImage
    // =========================================================================

    @Test
    void parseImageSingleRowParsesColors() {
        // '0' → 2^0 = 1 (white), '1' → 2^1 = 2 (orange)
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local img = parseImage('01')\n_capture(img[1][1], img[1][2])");
        assertNotNull(cap.args);
        assertEquals(1.0, ((Number) cap.args[0]).doubleValue(), "'0' must map to 1");
        assertEquals(2.0, ((Number) cap.args[1]).doubleValue(), "'1' must map to 2");
    }

    @Test
    void parseImageMultiRowProducesCorrectRowCount() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local img = parseImage('0f\\na5')\n_capture(#img)");
        assertNotNull(cap.args);
        assertEquals(2.0, ((Number) cap.args[0]).doubleValue(), "Two-line string must produce two rows");
    }

    @Test
    void parseImageMultiRowParsesSecondRowColors() {
        // 'a' → 2^10 = 1024, '5' → 2^5 = 32
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local img = parseImage('00\\na5')\n_capture(img[2][1], img[2][2])");
        assertNotNull(cap.args);
        assertEquals(1024.0, ((Number) cap.args[0]).doubleValue(), "'a' must map to 1024");
        assertEquals(32.0, ((Number) cap.args[1]).doubleValue(), "'5' must map to 32");
    }

    @Test
    void parseImageSpaceMapsToTransparent() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local img = parseImage(' ')\n_capture(img[1][1])");
        assertNotNull(cap.args);
        assertEquals(0.0, ((Number) cap.args[0]).doubleValue(), "Space must map to 0 (transparent)");
    }

    @Test
    void parseImageCharFMapsTo32768() {
        // 'f' → 2^15 = 32768 (black)
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local img = parseImage('f')\n_capture(img[1][1])");
        assertNotNull(cap.args);
        assertEquals(32768.0, ((Number) cap.args[0]).doubleValue(), "'f' must map to 32768");
    }

    @Test
    void parseImageAllSixteenColorsInOrder() {
        // Verify first, second, and last of the 16-char row
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local img = parseImage('0123456789abcdef')\n" + "_capture(img[1][1], img[1][2], img[1][9], img[1][16])");
        assertNotNull(cap.args);
        assertEquals(1.0, ((Number) cap.args[0]).doubleValue(), "'0' must map to 1");
        assertEquals(2.0, ((Number) cap.args[1]).doubleValue(), "'1' must map to 2");
        assertEquals(256.0, ((Number) cap.args[2]).doubleValue(), "'8' must map to 256");
        assertEquals(32768.0, ((Number) cap.args[3]).doubleValue(), "'f' must map to 32768");
    }

    @Test
    void parseImageEmptyStringReturnsSingleEmptyRow() {
        // "" + "\n" → one match of "" → one empty row {}
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local img = parseImage('')\n_capture(#img, #img[1])");
        assertNotNull(cap.args);
        assertEquals(1.0, ((Number) cap.args[0]).doubleValue(), "Empty string must yield one row");
        assertEquals(0.0, ((Number) cap.args[1]).doubleValue(), "That row must be empty");
    }

    @Test
    void parseImageErrorsOnNonString() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, err = pcall(parseImage, 42)\n_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "parseImage(42) must throw");
        assertTrue(((String) cap.args[1]).contains("Expected string"), "Error must mention Expected string");
    }

    // =========================================================================
    // loadImage
    // =========================================================================

    @Test
    void loadImageReturnsNilForMissingFile() {
        ResultCapture cap = new ResultCapture();
        runWith(buildMachine(cap), FS_IO_MOCK, "_capture(loadImage('missing.nfp'))");
        assertNotNull(cap.args);
        assertNull(cap.args[0], "loadImage must return nil when the file does not exist");
    }

    @Test
    void loadImageErrorsOnNonStringPath() {
        ResultCapture cap = new ResultCapture();
        runWith(buildMachine(cap), FS_IO_MOCK, "local ok, err = pcall(loadImage, 99)\n_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "loadImage(99) must throw");
        assertTrue(((String) cap.args[1]).contains("Expected path"), "Error must mention Expected path");
    }

    @Test
    void loadImageDelegatesParsingToParseImage() {
        // '0' → 1, 'f' → 32768 across two rows
        ResultCapture cap = new ResultCapture();
        runWith(
            buildMachine(cap),
            FS_IO_MOCK,
            "_mock_files['test.nfp'] = '0f\\na5'\n" + "local img = loadImage('test.nfp')\n"
                + "_capture(#img, img[1][1], img[1][2], img[2][1], img[2][2])");
        assertNotNull(cap.args);
        assertEquals(2.0, ((Number) cap.args[0]).doubleValue(), "Two-row file must produce two rows");
        assertEquals(1.0, ((Number) cap.args[1]).doubleValue(), "img[1][1] must be 1");
        assertEquals(32768.0, ((Number) cap.args[2]).doubleValue(), "img[1][2] must be 32768");
        assertEquals(1024.0, ((Number) cap.args[3]).doubleValue(), "img[2][1] must be 1024");
        assertEquals(32.0, ((Number) cap.args[4]).doubleValue(), "img[2][2] must be 32");
    }

    // =========================================================================
    // drawPixel
    // =========================================================================

    @Test
    void drawPixelDrawsAtCorrectPosition() {
        ResultCapture cap = new ResultCapture();
        runWith(
            buildMachine(cap),
            TERM_MOCK,
            "drawPixel(3, 7, 1)\n" + "_capture(#_pixels, _pixels[1][1], _pixels[1][2])");
        assertNotNull(cap.args);
        assertEquals(1.0, ((Number) cap.args[0]).doubleValue(), "Exactly one pixel must be drawn");
        assertEquals(3.0, ((Number) cap.args[1]).doubleValue(), "Pixel x must be 3");
        assertEquals(7.0, ((Number) cap.args[2]).doubleValue(), "Pixel y must be 7");
    }

    @Test
    void drawPixelSetsBackgroundColorWhenProvided() {
        ResultCapture cap = new ResultCapture();
        runWith(
            buildMachine(cap),
            TERM_MOCK,
            "_bg = 1\n" + "drawPixel(1, 1, 4)\n" // 4 = yellow
                + "_capture(_bg)");
        assertNotNull(cap.args);
        assertEquals(4.0, ((Number) cap.args[0]).doubleValue(), "Background color must be updated to 4");
    }

    @Test
    void drawPixelDoesNotChangeColorWhenNilProvided() {
        ResultCapture cap = new ResultCapture();
        runWith(
            buildMachine(cap),
            TERM_MOCK,
            "_bg = 8\n" + "drawPixel(1, 1)\n" // no color argument
                + "_capture(_bg)");
        assertNotNull(cap.args);
        assertEquals(8.0, ((Number) cap.args[0]).doubleValue(), "Background color must not change when color is nil");
    }

    @Test
    void drawPixelErrorsOnInvalidArgs() {
        ResultCapture cap = new ResultCapture();
        runWith(buildMachine(cap), TERM_MOCK, "local ok, err = pcall(drawPixel, 'x', 1, 1)\n_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "drawPixel with non-number x must throw");
        assertTrue(((String) cap.args[1]).contains("Expected x, y"), "Error must mention expected args");
    }

    // =========================================================================
    // drawLine
    // =========================================================================

    @Test
    void drawLineSinglePointWhenStartEqualsEnd() {
        ResultCapture cap = new ResultCapture();
        runWith(
            buildMachine(cap),
            TERM_MOCK,
            "drawLine(2, 3, 2, 3, 1)\n" + "_capture(#_pixels, _pixels[1][1], _pixels[1][2])");
        assertNotNull(cap.args);
        assertEquals(1.0, ((Number) cap.args[0]).doubleValue(), "Degenerate line must draw exactly one pixel");
        assertEquals(2.0, ((Number) cap.args[1]).doubleValue(), "Pixel x must be 2");
        assertEquals(3.0, ((Number) cap.args[2]).doubleValue(), "Pixel y must be 3");
    }

    @Test
    void drawLineHorizontalCoversAllXPositions() {
        // Line from (1,2) to (3,2) must draw 3 pixels
        ResultCapture cap = new ResultCapture();
        runWith(buildMachine(cap), TERM_MOCK, "drawLine(1, 2, 3, 2, 1)\n_capture(#_pixels)");
        assertNotNull(cap.args);
        assertEquals(3.0, ((Number) cap.args[0]).doubleValue(), "Horizontal line of length 3 must draw 3 pixels");
    }

    @Test
    void drawLineVerticalCoversAllYPositions() {
        // Line from (1,1) to (1,3) must draw 3 pixels
        ResultCapture cap = new ResultCapture();
        runWith(buildMachine(cap), TERM_MOCK, "drawLine(1, 1, 1, 3, 1)\n_capture(#_pixels)");
        assertNotNull(cap.args);
        assertEquals(3.0, ((Number) cap.args[0]).doubleValue(), "Vertical line of length 3 must draw 3 pixels");
    }

    // =========================================================================
    // drawBox
    // =========================================================================

    @Test
    void drawBoxSinglePointWhenStartEqualsEnd() {
        ResultCapture cap = new ResultCapture();
        runWith(
            buildMachine(cap),
            TERM_MOCK,
            "drawBox(4, 5, 4, 5, 1)\n" + "_capture(#_pixels, _pixels[1][1], _pixels[1][2])");
        assertNotNull(cap.args);
        assertEquals(1.0, ((Number) cap.args[0]).doubleValue(), "Degenerate box must draw exactly one pixel");
        assertEquals(4.0, ((Number) cap.args[1]).doubleValue(), "Pixel x must be 4");
        assertEquals(5.0, ((Number) cap.args[2]).doubleValue(), "Pixel y must be 5");
    }

    @Test
    void drawBoxDrawsCorrectBorderPixelCount() {
        // 3×3 box: 3 top + 3 bottom + 1 left-mid + 1 right-mid = 8 border pixels
        ResultCapture cap = new ResultCapture();
        runWith(buildMachine(cap), TERM_MOCK, "drawBox(1, 1, 3, 3, 1)\n_capture(#_pixels)");
        assertNotNull(cap.args);
        assertEquals(8.0, ((Number) cap.args[0]).doubleValue(), "3×3 box must draw 8 border pixels");
    }

    @Test
    void drawBoxDoesNotDrawInteriorPixels() {
        // Center pixel (2,2) of a 3×3 box must never be drawn
        ResultCapture cap = new ResultCapture();
        runWith(
            buildMachine(cap),
            TERM_MOCK,
            "drawBox(1, 1, 3, 3, 1)\n" + "local hasCenter = false\n"
                + "for _, p in ipairs(_pixels) do\n"
                + "  if p[1] == 2 and p[2] == 2 then hasCenter = true end\n"
                + "end\n"
                + "_capture(hasCenter)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "drawBox must not draw the interior center pixel");
    }

    // =========================================================================
    // drawFilledBox
    // =========================================================================

    @Test
    void drawFilledBoxSinglePointWhenStartEqualsEnd() {
        ResultCapture cap = new ResultCapture();
        runWith(
            buildMachine(cap),
            TERM_MOCK,
            "drawFilledBox(2, 2, 2, 2, 1)\n" + "_capture(#_pixels, _pixels[1][1], _pixels[1][2])");
        assertNotNull(cap.args);
        assertEquals(1.0, ((Number) cap.args[0]).doubleValue(), "Degenerate filled box must draw exactly one pixel");
        assertEquals(2.0, ((Number) cap.args[1]).doubleValue(), "Pixel x must be 2");
        assertEquals(2.0, ((Number) cap.args[2]).doubleValue(), "Pixel y must be 2");
    }

    @Test
    void drawFilledBoxDrawsAllPixels() {
        // 2×2 box must draw all 4 pixels
        ResultCapture cap = new ResultCapture();
        runWith(buildMachine(cap), TERM_MOCK, "drawFilledBox(1, 1, 2, 2, 1)\n_capture(#_pixels)");
        assertNotNull(cap.args);
        assertEquals(4.0, ((Number) cap.args[0]).doubleValue(), "2×2 filled box must draw exactly 4 pixels");
    }

    @Test
    void drawFilledBoxDrawsInteriorPixel() {
        // Center pixel (2,2) of a 3×3 filled box MUST be drawn (unlike drawBox)
        ResultCapture cap = new ResultCapture();
        runWith(
            buildMachine(cap),
            TERM_MOCK,
            "drawFilledBox(1, 1, 3, 3, 1)\n" + "local hasCenter = false\n"
                + "for _, p in ipairs(_pixels) do\n"
                + "  if p[1] == 2 and p[2] == 2 then hasCenter = true end\n"
                + "end\n"
                + "_capture(hasCenter)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "drawFilledBox must draw the interior center pixel");
    }

    // =========================================================================
    // drawImage
    // =========================================================================

    @Test
    void drawImageSkipsTransparentPixels() {
        // Image row: {0, 1} — first pixel is transparent (0), second is white (1)
        ResultCapture cap = new ResultCapture();
        runWith(
            buildMachine(cap),
            TERM_MOCK,
            "drawImage({{0, 1}}, 1, 1)\n" + "_capture(#_pixels, _pixels[1][1], _pixels[1][2])");
        assertNotNull(cap.args);
        assertEquals(
            1.0,
            ((Number) cap.args[0]).doubleValue(),
            "Only one pixel must be drawn (the non-transparent one)");
        assertEquals(2.0, ((Number) cap.args[1]).doubleValue(), "The drawn pixel must be at x=2 (second column)");
        assertEquals(1.0, ((Number) cap.args[2]).doubleValue(), "The drawn pixel must be at y=1");
    }

    @Test
    void drawImageAppliesPositionOffset() {
        // Single white pixel image drawn at (3, 5): must appear at (3, 5)
        ResultCapture cap = new ResultCapture();
        runWith(
            buildMachine(cap),
            TERM_MOCK,
            "drawImage({{1}}, 3, 5)\n" + "_capture(#_pixels, _pixels[1][1], _pixels[1][2])");
        assertNotNull(cap.args);
        assertEquals(1.0, ((Number) cap.args[0]).doubleValue(), "Exactly one pixel must be drawn");
        assertEquals(3.0, ((Number) cap.args[1]).doubleValue(), "Pixel x must respect xPos offset");
        assertEquals(5.0, ((Number) cap.args[2]).doubleValue(), "Pixel y must respect yPos offset");
    }

    @Test
    void drawImageSetsCorrectBackgroundColor() {
        // Pixel with value 4 (yellow) must cause background to be set to 4
        ResultCapture cap = new ResultCapture();
        runWith(buildMachine(cap), TERM_MOCK, "drawImage({{4}}, 1, 1)\n_capture(_pixels[1][3])");
        assertNotNull(cap.args);
        assertEquals(4.0, ((Number) cap.args[0]).doubleValue(), "Drawn pixel must carry the correct background color");
    }

    @Test
    void drawImageErrorsOnInvalidArgs() {
        ResultCapture cap = new ResultCapture();
        runWith(buildMachine(cap), TERM_MOCK, "local ok, err = pcall(drawImage, 'notatable', 1, 1)\n_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "drawImage with non-table first arg must throw");
        assertTrue(((String) cap.args[1]).contains("Expected image"), "Error must mention Expected image");
    }

    @Test
    void drawImageMultiRowPositionsAreCorrect() {
        // Image: row1 = {1}, row2 = {2} — drawn at (1,1)
        // row2 pixel → x + xPos - 1 = 1, y + yPos - 1 = 2
        ResultCapture cap = new ResultCapture();
        runWith(
            buildMachine(cap),
            TERM_MOCK,
            "drawImage({{1},{2}}, 1, 1)\n" + "_capture(#_pixels, _pixels[1][2], _pixels[2][2])");
        assertNotNull(cap.args);
        assertEquals(2.0, ((Number) cap.args[0]).doubleValue(), "Two-row image must draw two pixels");
        assertEquals(1.0, ((Number) cap.args[1]).doubleValue(), "First pixel must be at y=1");
        assertEquals(2.0, ((Number) cap.args[2]).doubleValue(), "Second pixel must be at y=2");
    }
}
