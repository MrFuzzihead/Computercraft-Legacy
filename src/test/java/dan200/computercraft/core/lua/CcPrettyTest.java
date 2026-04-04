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
 * Tests for the {@code cc.pretty} Lua module ({@code rom/modules/main/cc/pretty.lua}).
 *
 * <p>
 * Each test builds a preamble that:
 * <ul>
 * <li>Provides minimal mocks for {@code colours}, {@code write}, and {@code term}
 * (used by {@code cc.pretty}'s module-level local captures and the
 * {@code write}/{@code print} functions).</li>
 * <li>Pre-loads {@code cc.expect} via {@code loadstring} into
 * {@code package.loaded["cc.expect"]} so that {@code require "cc.expect"} inside
 * {@code cc.pretty} resolves correctly.</li>
 * <li>Loads {@code cc.pretty} via {@code loadstring} and assigns the returned module
 * to the global {@code pretty}.</li>
 * </ul>
 * Tests use {@code pretty.render(doc, width)} to get a string result and capture it
 * with the injected {@code _capture} helper.
 * </p>
 */
class CcPrettyTest {

    private static String expectSource;
    private static String prettySource;

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @BeforeAll
    static void loadSources() throws IOException {
        expectSource = readResource("/assets/computercraft/lua/rom/modules/main/cc/expect.lua");
        prettySource = readResource("/assets/computercraft/lua/rom/modules/main/cc/pretty.lua");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream is = CcPrettyTest.class.getResourceAsStream(path)) {
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
     * Builds a Lua preamble that mocks dependencies and loads both {@code cc.expect}
     * and {@code cc.pretty} into the test environment.
     *
     * <p>
     * Uses level-1 long strings ({@code [=[ ... ]=]}) to embed the source files.
     * Neither source file contains the sequence {@code ]=]}, so this is safe.
     * </p>
     */
    private String buildPreamble() {
        return // Minimal colour table required by pretty_impl (colours.red, .magenta, .lightGrey)
        "colours = { red = 1, magenta = 2, lightGrey = 4 }\n"
            // No-op write stub (cc.pretty captures the global 'write' at load time)
            + "write = function(s) end\n"
            // Minimal term mock used by cc.pretty's write() and print() functions
            + "term = {\n"
            + "  getSize       = function() return 51, 19 end,\n"
            + "  getCursorPos  = function() return 1, 1 end,\n"
            + "  setTextColour = function() end,\n"
            + "  getTextColour = function() return 1 end,\n"
            + "  scroll        = function() end,\n"
            + "  setCursorPos  = function() end,\n"
            + "}\n"
            // Set up package / require so that require("cc.expect") resolves correctly
            + "package = { loaded = {} }\n"
            + "do\n"
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
            // Load cc.pretty and expose it as the global 'pretty'
            + "do\n"
            + "  local fn, err = loadstring([=[\n"
            + prettySource
            + "\n]=])\n"
            + "  if not fn then error('cc.pretty load failed: ' .. tostring(err)) end\n"
            + "  pretty = fn()\n"
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
    // Document primitives: empty, space, line, space_line
    // -------------------------------------------------------------------------

    @Test
    void testRenderEmpty() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.empty))");
        assertArrayEquals(new Object[] { "" }, cap.args);
    }

    @Test
    void testRenderSpace() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.space))");
        assertArrayEquals(new Object[] { " " }, cap.args);
    }

    @Test
    void testSpaceLineInGroupWithoutWidthCollapsesToSpace() {
        ResultCapture cap = new ResultCapture();
        // Without a render width, groups always use the flat form.
        // space_line.flat == space, so the result is " ".
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.group(pretty.space_line)))");
        assertArrayEquals(new Object[] { " " }, cap.args);
    }

    @Test
    void testLineInGroupWithoutWidthCollapsesToEmpty() {
        ResultCapture cap = new ResultCapture();
        // line.flat == empty, so the result is "".
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.group(pretty.line)))");
        assertArrayEquals(new Object[] { "" }, cap.args);
    }

    @Test
    void testUnwrappedLineRendersAsNewline() {
        ResultCapture cap = new ResultCapture();
        // A line node outside a group always renders as a newline (indent=0).
        run(
            buildMachine(cap),
            buildPreamble(),
            "_capture(pretty.render(pretty.text('a') .. pretty.line .. pretty.text('b')))");
        assertArrayEquals(new Object[] { "a\nb" }, cap.args);
    }

    // -------------------------------------------------------------------------
    // text()
    // -------------------------------------------------------------------------

    @Test
    void testTextRendersCorrectly() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.text('hello')))");
        assertArrayEquals(new Object[] { "hello" }, cap.args);
    }

    @Test
    void testTextWithEmbeddedNewlineSplitsIntoSpaceLine() {
        ResultCapture cap = new ResultCapture();
        // text("a\nb") splits into concat(text("a"), space_line, text("b")).
        // Without a group wrapper, space_line renders as a real newline.
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.text('a\\nb')))");
        assertArrayEquals(new Object[] { "a\nb" }, cap.args);
    }

    @Test
    void testTextErrorOnNonString() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "local ok, err = pcall(pretty.text, 42)\n" + "_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0], "text(42) should throw a type error");
        assertTrue(
            ((String) cap.args[1]).contains("expected"),
            "Error message should mention expected type; got: " + cap.args[1]);
    }

    // -------------------------------------------------------------------------
    // concat()
    // -------------------------------------------------------------------------

    @Test
    void testConcatNoArgs() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.concat()))");
        assertArrayEquals(new Object[] { "" }, cap.args);
    }

    @Test
    void testConcatSingleDoc() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.concat(pretty.text('a'))))");
        assertArrayEquals(new Object[] { "a" }, cap.args);
    }

    @Test
    void testConcatMixedStringsAndDocs() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.concat('a', pretty.text('b'), 'c')))");
        assertArrayEquals(new Object[] { "abc" }, cap.args);
    }

    @Test
    void testConcatViaMetamethod() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "_capture(pretty.render(pretty.text('foo') .. ' - ' .. pretty.text('bar')))");
        assertArrayEquals(new Object[] { "foo - bar" }, cap.args);
    }

    // -------------------------------------------------------------------------
    // nest()
    // -------------------------------------------------------------------------

    @Test
    void testNestIndentsSubsequentLines() {
        ResultCapture cap = new ResultCapture();
        // nest(2, text("a") .. line .. text("b")) renders as "a\n b" with width=10.
        run(
            buildMachine(cap),
            buildPreamble(),
            "_capture(pretty.render(" + "pretty.nest(2, pretty.text('a') .. pretty.line .. pretty.text('b')), 10))");
        assertArrayEquals(new Object[] { "a\n  b" }, cap.args);
    }

    @Test
    void testNestZeroDepthErrors() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local ok, err = pcall(pretty.nest, 0, pretty.text('x'))\n" + "_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0], "nest(0, ...) should throw");
        assertTrue(
            ((String) cap.args[1]).contains("depth must be a positive number"),
            "Error should mention 'depth must be a positive number'; got: " + cap.args[1]);
    }

    @Test
    void testNestNegativeDepthErrors() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local ok, err = pcall(pretty.nest, -1, pretty.text('x'))\n" + "_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0], "nest(-1, ...) should throw");
        assertTrue(
            ((String) cap.args[1]).contains("depth must be a positive number"),
            "Error should mention 'depth must be a positive number'; got: " + cap.args[1]);
    }

    // -------------------------------------------------------------------------
    // group()
    // -------------------------------------------------------------------------

    @Test
    void testGroupFitsOnOneLine() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "_capture(pretty.render("
                + "pretty.group(pretty.text('hello') .. pretty.space_line .. pretty.text('world')), 80))");
        assertArrayEquals(new Object[] { "hello world" }, cap.args);
    }

    @Test
    void testGroupWrapsWhenTooWide() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "_capture(pretty.render("
                + "pretty.group(pretty.text('hello') .. pretty.space_line .. pretty.text('world')), 5))");
        assertArrayEquals(new Object[] { "hello\nworld" }, cap.args);
    }

    @Test
    void testGroupIsIdempotent() {
        ResultCapture cap = new ResultCapture();
        // group(group(x)) should render identically to group(x).
        run(
            buildMachine(cap),
            buildPreamble(),
            "local doc = pretty.group(pretty.text('hello') .. pretty.space_line .. pretty.text('world'))\n"
                + "_capture(pretty.render(doc, 80), pretty.render(pretty.group(doc), 80))");
        assertEquals(cap.args[0], cap.args[1], "group(group(x)) should equal group(x)");
    }

    // -------------------------------------------------------------------------
    // render() — width and ribbon_frac
    // -------------------------------------------------------------------------

    @Test
    void testRenderWithoutWidthAlwaysFlattensGroups() {
        ResultCapture cap = new ResultCapture();
        // No width → groups always use flat form regardless of content length.
        run(
            buildMachine(cap),
            buildPreamble(),
            "_capture(pretty.render("
                + "pretty.group(pretty.text('hello') .. pretty.space_line .. pretty.text('world'))))");
        assertArrayEquals(new Object[] { "hello world" }, cap.args);
    }

    @Test
    void testRenderRibbonFracLimitsLineWidth() {
        ResultCapture cap = new ResultCapture();
        // ribbon_frac=0.1 with width=100 → ribbon_width=10.
        // "hello world" is 11 chars, which exceeds the 10-char ribbon → wraps.
        run(
            buildMachine(cap),
            buildPreamble(),
            "_capture(pretty.render("
                + "pretty.group(pretty.text('hello') .. pretty.space_line .. pretty.text('world')), 100, 0.1))");
        assertArrayEquals(new Object[] { "hello\nworld" }, cap.args);
    }

    // -------------------------------------------------------------------------
    // pretty() — various value types
    // -------------------------------------------------------------------------

    @Test
    void testPrettyNil() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.pretty(nil)))");
        assertArrayEquals(new Object[] { "nil" }, cap.args);
    }

    @Test
    void testPrettyTrue() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.pretty(true)))");
        assertArrayEquals(new Object[] { "true" }, cap.args);
    }

    @Test
    void testPrettyFalse() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.pretty(false)))");
        assertArrayEquals(new Object[] { "false" }, cap.args);
    }

    @Test
    void testPrettyInteger() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.pretty(42)))");
        assertArrayEquals(new Object[] { "42" }, cap.args);
    }

    @Test
    void testPrettyFloat() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.pretty(3.14)))");
        assertArrayEquals(new Object[] { "3.14" }, cap.args);
    }

    @Test
    void testPrettyString() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.pretty('hello')))");
        // %q wraps in double quotes: "hello"
        assertArrayEquals(new Object[] { "\"hello\"" }, cap.args);
    }

    @Test
    void testPrettyStringEscapesNewline() {
        ResultCapture cap = new ResultCapture();
        // pretty("a\nb") uses %q then gsub to replace \<newline> with \n.
        // Result is the 6-char string: ", a, \, n, b, "
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.pretty('a\\nb')))");
        assertArrayEquals(new Object[] { "\"a\\nb\"" }, cap.args);
    }

    @Test
    void testPrettyEmptyTable() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.pretty({})))");
        assertArrayEquals(new Object[] { "{}" }, cap.args);
    }

    @Test
    void testPrettySimpleArray() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.pretty({1, 2, 3}), 40))");
        assertArrayEquals(new Object[] { "{ 1, 2, 3 }" }, cap.args);
    }

    @Test
    void testPrettyTableWithStringKey() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.pretty({a = 1}), 40))");
        assertArrayEquals(new Object[] { "{ a = 1 }" }, cap.args);
    }

    @Test
    void testPrettyTableWithKeywordKeyUsesBrackets() {
        ResultCapture cap = new ResultCapture();
        // "if" is a Lua keyword; it must be rendered as ["if"] rather than if =
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.pretty({['if'] = 1}), 40))");
        String result = (String) cap.args[0];
        assertTrue(result.contains("[\"if\"]"), "Keyword key should be bracket-quoted; got: " + result);
    }

    @Test
    void testPrettyNestedTable() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(pretty.render(pretty.pretty({1, {2, 3}}), 40))");
        assertArrayEquals(new Object[] { "{ 1, { 2, 3 } }" }, cap.args);
    }

    @Test
    void testPrettyCircularReferenceDoesNotLoop() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local t = {}\n" + "t.self = t\n" + "_capture(pretty.render(pretty.pretty(t), 80))");
        assertNotNull(cap.args, "Circular reference should not cause an infinite loop");
        // The self-referential value falls back to tostring(t) = "table: 0x..."
        assertTrue(
            ((String) cap.args[0]).contains("table:"),
            "Circular reference value should fall back to tostring; got: " + cap.args[0]);
    }

    @Test
    void testPrettyUsesTostringWhenMetamethodPresent() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local mt = { __tostring = function(t) return 'custom' end }\n" + "local obj = setmetatable({}, mt)\n"
                + "_capture(pretty.render(pretty.pretty(obj)))");
        assertArrayEquals(new Object[] { "custom" }, cap.args);
    }

    @Test
    void testPrettyOptionsInvalidTypeErrors() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local ok, err = pcall(pretty.pretty, {}, 'bad')\n" + "_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0], "pretty({}, 'bad') should throw a type error");
        assertTrue(
            ((String) cap.args[1]).contains("expected"),
            "Error message should mention expected type; got: " + cap.args[1]);
    }
}
