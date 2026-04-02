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
 * Tests for the {@code cc.image.nft} Lua module
 * ({@code rom/modules/main/cc/image/nft.lua}).
 *
 * <p>
 * The test preamble pre-loads {@code cc.expect} via {@code loadstring} into
 * {@code package.loaded}, installs a minimal {@code require} shim, then loads
 * {@code cc.image.nft} and assigns the module table to the global {@code nft}.
 * </p>
 *
 * <p>
 * {@link #FS_MOCK} and {@link #TERM_MOCK_INIT} are reusable Lua snippets that inject
 * a minimal {@code fs} (for {@code load()} tests) and a recording {@code term}
 * (for {@code draw()} default-target tests) into the test environment.
 * </p>
 */
class CcImageNftTest {

    private static String expectSource;
    private static String nftSource;

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @BeforeAll
    static void loadSources() throws IOException {
        expectSource = readResource("/assets/computercraft/lua/rom/modules/main/cc/expect.lua");
        nftSource = readResource("/assets/computercraft/lua/rom/modules/main/cc/image/nft.lua");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream is = CcImageNftTest.class.getResourceAsStream(path)) {
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

    /**
     * A minimal {@code fs} mock for {@link #run} calls that exercise
     * {@code nft.load()}. Exposes a single virtual file at {@code /test.nft}
     * whose content is set by the caller via {@code _mock_content}.
     */
    private static final String FS_MOCK = "_mock_content = nil\n" + "fs = {}\n"
        + "function fs.open(path, mode)\n"
        + "  if _mock_content == nil then return nil, 'No such file: ' .. path end\n"
        + "  local c = _mock_content\n"
        + "  return {\n"
        + "    readAll = function() return c end,\n"
        + "    close   = function() end,\n"
        + "  }\n"
        + "end\n";

    /**
     * Installs a recording {@code term} global whose {@code setCursorPos} and
     * {@code blit} calls are accumulated into the Lua table {@code _term_calls}.
     * Each entry is a table: {@code {op, ...args}}.
     */
    private static final String TERM_MOCK_INIT = "_term_calls = {}\n" + "term = {\n"
        + "  setCursorPos = function(x, y)\n"
        + "    _term_calls[#_term_calls + 1] = {'setCursorPos', x, y}\n"
        + "  end,\n"
        + "  blit = function(t, fg, bg)\n"
        + "    _term_calls[#_term_calls + 1] = {'blit', t, fg, bg}\n"
        + "  end,\n"
        + "}\n";

    /** Builds the require/package preamble plus the nft module. */
    private String buildPreamble() {
        return "package = { loaded = {} }\n" + "do\n"
            + "  local fn, err = loadstring([=[\n"
            + expectSource
            + "\n]=])\n"
            + "  if not fn then error('cc.expect load failed: ' .. tostring(err)) end\n"
            + "  package.loaded['cc.expect'] = fn()\n"
            + "end\n"
            + "function require(name)\n"
            + "  if package.loaded[name] ~= nil then return package.loaded[name] end\n"
            + "  error('module \\'' .. name .. '\\' not found')\n"
            + "end\n"
            + "do\n"
            + "  local fn, err = loadstring([=[\n"
            + nftSource
            + "\n]=])\n"
            + "  if not fn then error('cc.image.nft load failed: ' .. tostring(err)) end\n"
            + "  nft = fn()\n"
            + "end\n";
    }

    private static void run(CobaltMachine machine, String preamble, String testLua) {
        String combined = preamble + "\n" + testLua;
        machine.loadBios(new ByteArrayInputStream(combined.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 50 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    // -------------------------------------------------------------------------
    // parse() — basic line handling
    // -------------------------------------------------------------------------

    @Test
    void testParseEmptyStringReturnsEmptyTable() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "local img = nft.parse('')\n" + "_capture(#img)");
        assertEquals(0.0, cap.args[0]);
    }

    @Test
    void testParseSingleLineDefaultColors() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local img = nft.parse('ab')\n" + "_capture(#img, img[1].text, img[1].foreground, img[1].background)");
        assertEquals(1.0, cap.args[0], "line count");
        assertEquals("ab", cap.args[1], "text");
        // Default: foreground = "0" (white), background = "f" (black) — one char per pixel
        assertEquals("00", cap.args[2], "foreground");
        assertEquals("ff", cap.args[3], "background");
    }

    @Test
    void testParseMultipleCharactersShareDefaultColors() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local img = nft.parse('hello')\n" + "_capture(img[1].text, img[1].foreground, img[1].background)");
        assertEquals("hello", cap.args[0]);
        assertEquals("00000", cap.args[1]);
        assertEquals("fffff", cap.args[2]);
    }

    // -------------------------------------------------------------------------
    // parse() — foreground / background color tokens
    // -------------------------------------------------------------------------

    @Test
    void testParseForegroundToken() {
        ResultCapture cap = new ResultCapture();
        // \31 followed by "1" sets foreground to "1"; the next two chars "ab" use it.
        run(
            buildMachine(cap),
            buildPreamble(),
            "local img = nft.parse('\\031' .. '1ab')\n"
                + "_capture(img[1].text, img[1].foreground, img[1].background)");
        assertEquals("ab", cap.args[0], "text");
        assertEquals("11", cap.args[1], "foreground");
        assertEquals("ff", cap.args[2], "background");
    }

    @Test
    void testParseBackgroundToken() {
        ResultCapture cap = new ResultCapture();
        // \30 followed by "3" sets background to "3".
        run(
            buildMachine(cap),
            buildPreamble(),
            "local img = nft.parse('\\030' .. '3xy')\n"
                + "_capture(img[1].text, img[1].foreground, img[1].background)");
        assertEquals("xy", cap.args[0], "text");
        assertEquals("00", cap.args[1], "foreground");
        assertEquals("33", cap.args[2], "background");
    }

    @Test
    void testParseColorChangeInMiddleOfLine() {
        ResultCapture cap = new ResultCapture();
        // "a" with default colors, then foreground changes to "2", then "b".
        run(
            buildMachine(cap),
            buildPreamble(),
            "local img = nft.parse('a' .. '\\031' .. '2b')\n"
                + "_capture(img[1].text, img[1].foreground, img[1].background)");
        assertEquals("ab", cap.args[0], "text");
        assertEquals("02", cap.args[1], "foreground");
        assertEquals("ff", cap.args[2], "background");
    }

    @Test
    void testParseForegroundAndBackgroundCombined() {
        ResultCapture cap = new ResultCapture();
        // Set background to "3" then foreground to "e", then write "X".
        run(
            buildMachine(cap),
            buildPreamble(),
            "local img = nft.parse('\\030' .. '3' .. '\\031' .. 'eX')\n"
                + "_capture(img[1].text, img[1].foreground, img[1].background)");
        assertEquals("X", cap.args[0]);
        assertEquals("e", cap.args[1]);
        assertEquals("3", cap.args[2]);
    }

    // -------------------------------------------------------------------------
    // parse() — multi-line handling
    // -------------------------------------------------------------------------

    @Test
    void testParseMultiLine() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local img = nft.parse('a\\nb')\n" + "_capture(#img, img[1].text, img[2].text)");
        assertEquals(2.0, cap.args[0], "line count");
        assertEquals("a", cap.args[1], "line 1");
        assertEquals("b", cap.args[2], "line 2");
    }

    @Test
    void testParseColorsResetOnNewline() {
        ResultCapture cap = new ResultCapture();
        // Set fg="2" on line 1, then after \n it must reset to "0" on line 2.
        run(
            buildMachine(cap),
            buildPreamble(),
            "local img = nft.parse('\\031' .. '2a\\nb')\n" + "_capture(img[1].foreground, img[2].foreground)");
        assertEquals("2", cap.args[0], "line 1 foreground (changed)");
        assertEquals("0", cap.args[1], "line 2 foreground (reset to default)");
    }

    @Test
    void testParseEmptyLineInMiddle() {
        ResultCapture cap = new ResultCapture();
        // parse("a\n\nb") — the middle line is empty but must still be present.
        run(
            buildMachine(cap),
            buildPreamble(),
            "local img = nft.parse('a\\n\\nb')\n" + "_capture(#img, img[1].text, img[2].text, img[3].text)");
        assertEquals(3.0, cap.args[0], "line count");
        assertEquals("a", cap.args[1], "line 1");
        assertEquals("", cap.args[2], "line 2 (empty)");
        assertEquals("b", cap.args[3], "line 3");
    }

    @Test
    void testParseLeadingNewline() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local img = nft.parse('\\na')\n" + "_capture(#img, img[1].text, img[2].text)");
        assertEquals(2.0, cap.args[0], "line count");
        assertEquals("", cap.args[1], "line 1 (empty)");
        assertEquals("a", cap.args[2], "line 2");
    }

    @Test
    void testParseTrailingNewlineNotDoubledUp() {
        ResultCapture cap = new ResultCapture();
        // "a\n" — the trailing newline must NOT produce a spurious extra line.
        run(buildMachine(cap), buildPreamble(), "local img = nft.parse('a\\n')\n" + "_capture(#img, img[1].text)");
        assertEquals(1.0, cap.args[0], "line count");
        assertEquals("a", cap.args[1], "line 1 text");
    }

    @Test
    void testParseTypeErrorOnNonString() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "local ok, err = pcall(nft.parse, 42)\n" + "_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0], "parse(42) should throw");
        assertTrue(
            ((String) cap.args[1]).contains("expected"),
            "Error should mention expected type; got: " + cap.args[1]);
    }

    // -------------------------------------------------------------------------
    // load()
    // -------------------------------------------------------------------------

    @Test
    void testLoadReturnsNilAndErrorWhenFileNotFound() {
        ResultCapture cap = new ResultCapture();
        // fs mock with _mock_content = nil → file not found
        run(
            buildMachine(cap),
            buildPreamble() + FS_MOCK,
            "local img, err = nft.load('/missing.nft')\n" + "_capture(img, err)");
        assertNull(cap.args[0], "img should be nil");
        assertNotNull(cap.args[1], "error string should be non-nil");
        assertTrue(cap.args[1] instanceof String, "error should be a string; got: " + cap.args[1]);
    }

    @Test
    void testLoadParsesFileContent() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble() + FS_MOCK,
            "_mock_content = 'hi'\n" + "local img = nft.load('/test.nft')\n" + "_capture(#img, img[1].text)");
        assertEquals(1.0, cap.args[0], "line count");
        assertEquals("hi", cap.args[1], "text");
    }

    @Test
    void testLoadTypeErrorOnNonString() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble() + FS_MOCK,
            "local ok, err = pcall(nft.load, 99)\n" + "_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0], "load(99) should throw");
        assertTrue(
            ((String) cap.args[1]).contains("expected"),
            "Error should mention expected type; got: " + cap.args[1]);
    }

    // -------------------------------------------------------------------------
    // draw()
    // -------------------------------------------------------------------------

    @Test
    void testDrawBlitsEachLine() {
        ResultCapture cap = new ResultCapture();
        // Parse a two-line image, draw it into a recording mock terminal.
        run(
            buildMachine(cap),
            buildPreamble(),
            "local _pos, _blits = {}, {}\n" + "local mock = {\n"
                + "  setCursorPos = function(x, y) _pos[#_pos+1] = {x, y} end,\n"
                + "  blit = function(t, fg, bg) _blits[#_blits+1] = {t, fg, bg} end,\n"
                + "}\n"
                + "local img = nft.parse('ab\\ncd')\n"
                + "nft.draw(img, 3, 5, mock)\n"
                + "_capture(\n"
                + "  _pos[1][1], _pos[1][2],\n" // setCursorPos(3, 5)
                + "  _pos[2][1], _pos[2][2],\n" // setCursorPos(3, 6)
                + "  _blits[1][1], _blits[2][1]\n" // blit texts
                + ")");
        assertEquals(3.0, cap.args[0], "line 1 x");
        assertEquals(5.0, cap.args[1], "line 1 y");
        assertEquals(3.0, cap.args[2], "line 2 x");
        assertEquals(6.0, cap.args[3], "line 2 y");
        assertEquals("ab", cap.args[4], "blit text line 1");
        assertEquals("cd", cap.args[5], "blit text line 2");
    }

    @Test
    void testDrawPassesColorStringsToTarget() {
        ResultCapture cap = new ResultCapture();
        // Verify that foreground and background strings from parse() reach blit().
        run(
            buildMachine(cap),
            buildPreamble(),
            "local t_arg, fg_arg, bg_arg\n" + "local mock = {\n"
                + "  setCursorPos = function() end,\n"
                + "  blit = function(t, fg, bg) t_arg=t; fg_arg=fg; bg_arg=bg end,\n"
                + "}\n"
                + "local img = nft.parse('\\031' .. '2X')\n" // fg=2, text=X
                + "nft.draw(img, 1, 1, mock)\n"
                + "_capture(t_arg, fg_arg, bg_arg)");
        assertEquals("X", cap.args[0], "blit text");
        assertEquals("2", cap.args[1], "blit foreground");
        assertEquals("f", cap.args[2], "blit background");
    }

    @Test
    void testDrawUsesGlobalTermWhenNoTargetGiven() {
        ResultCapture cap = new ResultCapture();
        // Install a recording global `term`, then call draw() without a target.
        run(
            buildMachine(cap),
            buildPreamble() + TERM_MOCK_INIT,
            "local img = nft.parse('hi')\n" + "nft.draw(img, 2, 4)\n"
                + "_capture(\n"
                + "  _term_calls[1][1],\n" // 'setCursorPos'
                + "  _term_calls[1][2],\n" // x=2
                + "  _term_calls[1][3],\n" // y=4
                + "  _term_calls[2][1],\n" // 'blit'
                + "  _term_calls[2][2]\n" // text='hi'
                + ")");
        assertEquals("setCursorPos", cap.args[0]);
        assertEquals(2.0, cap.args[1], "x");
        assertEquals(4.0, cap.args[2], "y");
        assertEquals("blit", cap.args[3]);
        assertEquals("hi", cap.args[4]);
    }

    @Test
    void testDrawEmptyImageCallsNoBlit() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local call_count = 0\n" + "local mock = {\n"
                + "  setCursorPos = function() call_count = call_count + 1 end,\n"
                + "  blit = function() call_count = call_count + 1 end,\n"
                + "}\n"
                + "nft.draw(nft.parse(''), 1, 1, mock)\n"
                + "_capture(call_count)");
        assertEquals(0.0, cap.args[0], "no calls for an empty image");
    }

    @Test
    void testDrawTypeErrorOnBadImage() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local ok, err = pcall(nft.draw, 'notatable', 1, 1)\n" + "_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0], "draw('notatable',...) should throw");
        assertTrue(
            ((String) cap.args[1]).contains("expected"),
            "Error should mention expected type; got: " + cap.args[1]);
    }

    @Test
    void testDrawTypeErrorOnBadCoordinates() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "local ok, err = pcall(nft.draw, {}, 'x', 1)\n" + "_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0], "draw({}, 'x', 1) should throw");
        assertTrue(
            ((String) cap.args[1]).contains("expected"),
            "Error should mention expected type; got: " + cap.args[1]);
    }
}
