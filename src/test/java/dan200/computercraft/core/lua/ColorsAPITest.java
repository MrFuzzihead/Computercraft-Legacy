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
 * Tests for the four new functions added to the {@code colors} Lua API:
 * {@code packRGB}, {@code unpackRGB}, {@code toBlit}, and {@code fromBlit}.
 *
 * <p>Each test loads the {@code colors} ROM file (and, where needed, the
 * {@code colours} ROM file) into a fresh {@link CobaltMachine} and exercises the
 * API, capturing return values via the injected {@code _capture} helper.</p>
 *
 * <p>The {@code colours} tests simulate what {@code os.loadAPI} does: the
 * {@code colors} source is executed first (populating globals), a {@code colors}
 * table is built from those globals, and then the {@code colours} source runs and
 * copies entries from that table into its own environment.</p>
 */
class ColorsAPITest {

    private static String colorsSource;
    private static String coloursSource;

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @BeforeAll
    static void loadSources() throws IOException {
        colorsSource = readResource("/assets/computercraft/lua/rom/apis/colors");
        coloursSource = readResource("/assets/computercraft/lua/rom/apis/colours");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream is = ColorsAPITest.class.getResourceAsStream(path)) {
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

    /** Runs {@code testLua} with the colors source pre-loaded. */
    private static void run(CobaltMachine machine, String testLua) {
        String combined = colorsSource + "\n" + testLua;
        machine.loadBios(new ByteArrayInputStream(combined.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 50 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    /**
     * Runs {@code testLua} in an environment where both {@code colors} and
     * {@code colours} have been loaded, mimicking {@code os.loadAPI} by building a
     * {@code colors} table from the globals set by the colors source, then running
     * the colours source (which copies that table into its own environment).
     */
    private static void runColours(CobaltMachine machine, String testLua) {
        // After colorsSource runs, all constants and functions are plain globals.
        // Build the 'colors' table as os.loadAPI would, then run coloursSource.
        // '_ENV = _G' mirrors what os.loadAPI does via setfenv: colours.lua uses
        // 'local colours = _ENV' to obtain a reference to its own environment.
        String buildColorsTable =
            "_ENV = _G\n"
                + "colors = { packRGB=packRGB, unpackRGB=unpackRGB, toBlit=toBlit, fromBlit=fromBlit,"
                + " gray=gray, lightGray=lightGray }\n";
        String combined = colorsSource + "\n" + buildColorsTable + coloursSource + "\n" + testLua;
        machine.loadBios(new ByteArrayInputStream(combined.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 50 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    // =========================================================================
    // colors.packRGB
    // =========================================================================

    @Test
    void packRGBPureRed() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(packRGB(1, 0, 0))");
        assertNotNull(cap.args);
        assertEquals(0xFF0000, ((Number) cap.args[0]).longValue());
    }

    @Test
    void packRGBPureGreen() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(packRGB(0, 1, 0))");
        assertNotNull(cap.args);
        assertEquals(0x00FF00, ((Number) cap.args[0]).longValue());
    }

    @Test
    void packRGBPureBlue() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(packRGB(0, 0, 1))");
        assertNotNull(cap.args);
        assertEquals(0x0000FF, ((Number) cap.args[0]).longValue());
    }

    @Test
    void packRGBWhite() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(packRGB(1, 1, 1))");
        assertNotNull(cap.args);
        assertEquals(0xFFFFFF, ((Number) cap.args[0]).longValue());
    }

    @Test
    void packRGBBlack() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(packRGB(0, 0, 0))");
        assertNotNull(cap.args);
        assertEquals(0L, ((Number) cap.args[0]).longValue());
    }

    @Test
    void packRGBMidGray() {
        // 0.5 * 255 = 127.5 → floor → 127; 127 * 65536 + 127 * 256 + 127 = 0x7F7F7F
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(packRGB(0.5, 0.5, 0.5))");
        assertNotNull(cap.args);
        assertEquals(0x7F7F7FL, ((Number) cap.args[0]).longValue());
    }

    // =========================================================================
    // colors.unpackRGB
    // =========================================================================

    @Test
    void unpackRGBRed() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(unpackRGB(0xFF0000))");
        assertNotNull(cap.args);
        assertEquals(1.0, ((Number) cap.args[0]).doubleValue(), 1e-10, "r");
        assertEquals(0.0, ((Number) cap.args[1]).doubleValue(), 1e-10, "g");
        assertEquals(0.0, ((Number) cap.args[2]).doubleValue(), 1e-10, "b");
    }

    @Test
    void unpackRGBGreen() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(unpackRGB(0x00FF00))");
        assertNotNull(cap.args);
        assertEquals(0.0, ((Number) cap.args[0]).doubleValue(), 1e-10, "r");
        assertEquals(1.0, ((Number) cap.args[1]).doubleValue(), 1e-10, "g");
        assertEquals(0.0, ((Number) cap.args[2]).doubleValue(), 1e-10, "b");
    }

    @Test
    void unpackRGBBlue() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(unpackRGB(0x0000FF))");
        assertNotNull(cap.args);
        assertEquals(0.0, ((Number) cap.args[0]).doubleValue(), 1e-10, "r");
        assertEquals(0.0, ((Number) cap.args[1]).doubleValue(), 1e-10, "g");
        assertEquals(1.0, ((Number) cap.args[2]).doubleValue(), 1e-10, "b");
    }

    @Test
    void unpackRGBBlack() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(unpackRGB(0))");
        assertNotNull(cap.args);
        assertEquals(0.0, ((Number) cap.args[0]).doubleValue(), 1e-10, "r");
        assertEquals(0.0, ((Number) cap.args[1]).doubleValue(), 1e-10, "g");
        assertEquals(0.0, ((Number) cap.args[2]).doubleValue(), 1e-10, "b");
    }

    @Test
    void unpackRGBWhite() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(unpackRGB(0xFFFFFF))");
        assertNotNull(cap.args);
        assertEquals(1.0, ((Number) cap.args[0]).doubleValue(), 1e-10, "r");
        assertEquals(1.0, ((Number) cap.args[1]).doubleValue(), 1e-10, "g");
        assertEquals(1.0, ((Number) cap.args[2]).doubleValue(), 1e-10, "b");
    }

    @Test
    void packUnpackRoundtrip() {
        // 0.8 * 255 = 204, 0.4 * 255 = 102, 0.2 * 255 = 51 — all exact integers,
        // so the quantization error after pack→unpack is zero.
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(unpackRGB(packRGB(0.8, 0.4, 0.2)))");
        assertNotNull(cap.args);
        double tolerance = 1.0 / 255.0;
        assertEquals(0.8, ((Number) cap.args[0]).doubleValue(), tolerance, "r roundtrip");
        assertEquals(0.4, ((Number) cap.args[1]).doubleValue(), tolerance, "g roundtrip");
        assertEquals(0.2, ((Number) cap.args[2]).doubleValue(), tolerance, "b roundtrip");
    }

    // =========================================================================
    // colors.toBlit
    // =========================================================================

    @Test
    void toBlitWhiteIsZero() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(toBlit(1))");   // white = 1
        assertNotNull(cap.args);
        assertEquals("0", cap.args[0]);
    }

    @Test
    void toBlitOrangeIsOne() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(toBlit(2))");   // orange = 2
        assertNotNull(cap.args);
        assertEquals("1", cap.args[0]);
    }

    @Test
    void toBlitLimeIsFive() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(toBlit(lime))"); // lime = 32 = 2^5
        assertNotNull(cap.args);
        assertEquals("5", cap.args[0]);
    }

    @Test
    void toBlitGreenIsD() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(toBlit(green))"); // green = 8192 = 2^13
        assertNotNull(cap.args);
        assertEquals("d", cap.args[0]);
    }

    @Test
    void toBlitBlackIsF() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(toBlit(black))"); // black = 32768 = 2^15
        assertNotNull(cap.args);
        assertEquals("f", cap.args[0]);
    }

    @Test
    void toBlitAllSixteenColorsProduceDistinctChars() {
        // Capture all 16 blit chars in one call and verify they are "0"–"f" in order.
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "_capture("
                + "toBlit(white),toBlit(orange),toBlit(magenta),toBlit(lightBlue),"
                + "toBlit(yellow),toBlit(lime),toBlit(pink),toBlit(gray),"
                + "toBlit(lightGray),toBlit(cyan),toBlit(purple),toBlit(blue),"
                + "toBlit(brown),toBlit(green),toBlit(red),toBlit(black)"
                + ")");
        assertNotNull(cap.args);
        assertEquals(16, cap.args.length, "All 16 colors must produce a blit char");
        String expected = "0123456789abcdef";
        for (int i = 0; i < 16; i++) {
            assertEquals(
                String.valueOf(expected.charAt(i)),
                cap.args[i],
                "Color index " + i + " must map to blit char '" + expected.charAt(i) + "'");
        }
    }

    // =========================================================================
    // colors.fromBlit
    // =========================================================================

    @Test
    void fromBlitZeroIsWhite() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(fromBlit('0'))");
        assertNotNull(cap.args);
        assertEquals(1.0, ((Number) cap.args[0]).doubleValue(), 1e-10); // white = 1
    }

    @Test
    void fromBlitFiveIsLime() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(fromBlit('5'))");
        assertNotNull(cap.args);
        assertEquals(32.0, ((Number) cap.args[0]).doubleValue(), 1e-10); // lime = 32
    }

    @Test
    void fromBlitInvalidCharReturnsNil() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(fromBlit('g'))");
        assertNotNull(cap.args);
        assertNull(cap.args[0], "Invalid hex char must return nil");
    }

    @Test
    void fromBlitMultiCharStringReturnsNil() {
        // Regression: "10" parses as hex 16, so without the length check
        // fromBlit("10") would wrongly return 2^16 = 65536 instead of nil.
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(fromBlit('10'))");
        assertNotNull(cap.args);
        assertNull(cap.args[0], "Multi-char string must return nil, not 2^16");
    }

    @Test
    void fromBlitEmptyStringReturnsNil() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(fromBlit(''))");
        assertNotNull(cap.args);
        assertNull(cap.args[0], "Empty string must return nil");
    }

    @Test
    void fromBlitNonStringReturnsNil() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(fromBlit(5))");
        assertNotNull(cap.args);
        assertNull(cap.args[0], "Non-string argument must return nil");
    }

    @Test
    void fromBlitUppercaseIsAccepted() {
        // tonumber(char, 16) is case-insensitive, so "F" == "f"
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(fromBlit('F'))");
        assertNotNull(cap.args);
        assertEquals(32768.0, ((Number) cap.args[0]).doubleValue(), 1e-10);
    }

    @Test
    void fromBlitToBlitRoundtrip() {
        // All 16 CC color bitmasks must round-trip through toBlit and fromBlit.
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local ok = true\n"
                + "local cols = {white,orange,magenta,lightBlue,yellow,lime,pink,gray,"
                + "lightGray,cyan,purple,blue,brown,green,red,black}\n"
                + "for _,c in ipairs(cols) do\n"
                + "  if fromBlit(toBlit(c)) ~= c then ok = false end\n"
                + "end\n"
                + "_capture(ok)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "All 16 colors must round-trip through toBlit/fromBlit");
    }

    // =========================================================================
    // colors.rgb8 (deprecated dispatcher)
    // =========================================================================

    @Test
    void rgb8WithOneArgDelegatesToUnpackRGB() {
        // rgb8(rgb) should behave identically to unpackRGB(rgb).
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(rgb8(0xFF0000))");
        assertNotNull(cap.args);
        assertEquals(1.0, ((Number) cap.args[0]).doubleValue(), 1e-10, "r");
        assertEquals(0.0, ((Number) cap.args[1]).doubleValue(), 1e-10, "g");
        assertEquals(0.0, ((Number) cap.args[2]).doubleValue(), 1e-10, "b");
    }

    @Test
    void rgb8WithThreeArgsDelegatesToPackRGB() {
        // rgb8(r, g, b) should behave identically to packRGB(r, g, b).
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(rgb8(1, 0, 0))");
        assertNotNull(cap.args);
        assertEquals(0xFF0000L, ((Number) cap.args[0]).longValue());
    }

    @Test
    void rgb8RoundtripMatchesPackUnpack() {
        // rgb8(rgb8(r, g, b)) must equal (r, g, b) within quantization error.
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(rgb8(rgb8(0.8, 0.4, 0.2)))");
        assertNotNull(cap.args);
        double tol = 1.0 / 255.0;
        assertEquals(0.8, ((Number) cap.args[0]).doubleValue(), tol, "r roundtrip");
        assertEquals(0.4, ((Number) cap.args[1]).doubleValue(), tol, "g roundtrip");
        assertEquals(0.2, ((Number) cap.args[2]).doubleValue(), tol, "b roundtrip");
    }

    // =========================================================================
    // colours (British English) mirror
    // =========================================================================

    @Test
    void coloursMirrorsPackRGB() {
        ResultCapture cap = new ResultCapture();
        runColours(buildMachine(cap), "_capture(type(packRGB))");
        assertNotNull(cap.args);
        assertEquals("function", cap.args[0], "colours must expose packRGB");
    }

    @Test
    void coloursMirrorsUnpackRGB() {
        ResultCapture cap = new ResultCapture();
        runColours(buildMachine(cap), "_capture(type(unpackRGB))");
        assertNotNull(cap.args);
        assertEquals("function", cap.args[0], "colours must expose unpackRGB");
    }

    @Test
    void coloursMirrorsToBlit() {
        ResultCapture cap = new ResultCapture();
        runColours(buildMachine(cap), "_capture(type(toBlit))");
        assertNotNull(cap.args);
        assertEquals("function", cap.args[0], "colours must expose toBlit");
    }

    @Test
    void coloursMirrorsFromBlit() {
        ResultCapture cap = new ResultCapture();
        runColours(buildMachine(cap), "_capture(type(fromBlit))");
        assertNotNull(cap.args);
        assertEquals("function", cap.args[0], "colours must expose fromBlit");
    }

    @Test
    void coloursRenamesGrayToGrey() {
        // colours renames 'gray' -> 'grey' and removes 'gray'
        ResultCapture cap = new ResultCapture();
        runColours(buildMachine(cap), "_capture(grey, gray)");
        assertNotNull(cap.args);
        assertEquals(128.0, ((Number) cap.args[0]).doubleValue(), "grey must equal 128");
        assertNull(cap.args[1], "gray must be nil in colours");
    }

    @Test
    void coloursPackRGBProducesCorrectValue() {
        // Verify the mirrored function is functional, not just present.
        ResultCapture cap = new ResultCapture();
        runColours(buildMachine(cap), "_capture(packRGB(1, 0, 0))");
        assertNotNull(cap.args);
        assertEquals(0xFF0000L, ((Number) cap.args[0]).longValue(),
            "colours.packRGB must produce the same result as colors.packRGB");
    }
}





