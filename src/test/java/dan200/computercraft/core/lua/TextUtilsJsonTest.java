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
 * Tests for {@code textutils.serializeJSON} and {@code textutils.unserializeJSON} as
 * implemented in the {@code assets/computercraft/lua/rom/apis/textutils} Lua file.
 *
 * <p>
 * Each test loads the textutils Lua module into a fresh {@link CobaltMachine} and
 * exercises the JSON API, capturing return values via the injected {@code _capture}
 * helper.
 */
class TextUtilsJsonTest {

    /** Source of the textutils ROM API, loaded once for all tests. */
    private static String textutilsSource;

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @BeforeAll
    static void loadTextUtils() throws IOException {
        try (InputStream is = TextUtilsJsonTest.class
            .getResourceAsStream("/assets/computercraft/lua/rom/apis/textutils")) {
            assertNotNull(is, "textutils resource must be on the test classpath");
            // Use Scanner for Java 8 compatible reading
            try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                textutilsSource = scanner.hasNext() ? scanner.next() : "";
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers (mirrored from CobaltMachineTest)
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
     * Runs {@code testLua} inside a Lua environment that has already executed the
     * full textutils module, making all textutils globals available.
     */
    private static void run(CobaltMachine machine, String testLua) {
        String combined = textutilsSource + "\n" + testLua;
        machine.loadBios(new ByteArrayInputStream(combined.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 100 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    // -------------------------------------------------------------------------
    // serializeJSON tests
    // -------------------------------------------------------------------------

    @Test
    void testSerializeJsonString() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(serializeJSON(\"hello\"))");
        assertNotNull(cap.args, "capture must be called");
        assertEquals("\"hello\"", cap.args[0]);
    }

    @Test
    void testSerializeJsonStringEscapesSpecialChars() {
        ResultCapture cap = new ResultCapture();
        // newline, tab, backslash, and double-quote must all be escaped in JSON
        run(buildMachine(cap), "_capture(serializeJSON(\"line1\\nline2\\t\\\\end\\\"\"))");
        assertNotNull(cap.args);
        assertEquals("\"line1\\nline2\\t\\\\end\\\"\"", cap.args[0]);
    }

    @Test
    void testSerializeJsonNumber() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(serializeJSON(42), serializeJSON(3.14))");
        assertNotNull(cap.args);
        assertEquals("42", cap.args[0]);
        // tonumber roundtrip must equal the original
        assertEquals(3.14, Double.parseDouble((String) cap.args[1]), 1e-9);
    }

    @Test
    void testSerializeJsonBoolean() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(serializeJSON(true), serializeJSON(false))");
        assertNotNull(cap.args);
        assertEquals("true", cap.args[0]);
        assertEquals("false", cap.args[1]);
    }

    @Test
    void testSerializeJsonEmptyObject() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(serializeJSON({}))");
        assertNotNull(cap.args);
        assertEquals("{}", cap.args[0]);
    }

    @Test
    void testSerializeJsonEmptyArray() {
        ResultCapture cap = new ResultCapture();
        // empty_json_array is the sentinel for a JSON empty array
        run(buildMachine(cap), "_capture(serializeJSON(empty_json_array))");
        assertNotNull(cap.args);
        assertEquals("[]", cap.args[0]);
    }

    @Test
    void testSerializeJsonArray() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(serializeJSON({10, 20, 30}))");
        assertNotNull(cap.args);
        assertEquals("[10,20,30]", cap.args[0]);
    }

    @Test
    void testSerializeJsonObject() {
        ResultCapture cap = new ResultCapture();
        // Single-key object – ordering is deterministic
        run(buildMachine(cap), "_capture(serializeJSON({key=\"value\"}))");
        assertNotNull(cap.args);
        assertEquals("{\"key\":\"value\"}", cap.args[0]);
    }

    @Test
    void testSerializeJsonNestedStructure() {
        ResultCapture cap = new ResultCapture();
        // Verify a nested object/array round-trips correctly via unserializeJSON
        run(
            buildMachine(cap),
            "local t = {nums={1,2,3}, label=\"test\"}\n" + "local json = serializeJSON(t)\n"
                + "local parsed = unserializeJSON(json)\n"
                + "_capture(parsed.label, parsed.nums[1], parsed.nums[2], parsed.nums[3])");
        assertNotNull(cap.args);
        assertEquals("test", cap.args[0]);
        assertEquals(1.0, cap.args[1]);
        assertEquals(2.0, cap.args[2]);
        assertEquals(3.0, cap.args[3]);
    }

    @Test
    void testSerializeJsonUnsupportedTypeErrors() {
        ResultCapture cap = new ResultCapture();
        // Passing a function should raise an error caught by pcall
        run(
            buildMachine(cap),
            "local ok, err = pcall(function() serializeJSON(function() end) end)\n" + "_capture(ok, type(err))");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "serializeJSON with a function must error");
        assertEquals("string", cap.args[1]);
    }

    // -------------------------------------------------------------------------
    // unserializeJSON tests
    // -------------------------------------------------------------------------

    @Test
    void testUnserializeJsonString() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(unserializeJSON('\"hello world\"'))");
        assertNotNull(cap.args);
        assertEquals("hello world", cap.args[0]);
    }

    @Test
    void testUnserializeJsonStringEscapes() {
        ResultCapture cap = new ResultCapture();
        // JSON escape sequences must be decoded to their Lua equivalents
        run(buildMachine(cap), "_capture(unserializeJSON('\"\\\\n\\\\t\\\\r\\\\\\\\\\\\\"\"'))");
        assertNotNull(cap.args);
        assertEquals("\n\t\r\\\"", cap.args[0]);
    }

    @Test
    void testUnserializeJsonNumber() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(unserializeJSON('42'), unserializeJSON('3.14'))");
        assertNotNull(cap.args);
        assertEquals(42.0, cap.args[0]);
        assertEquals(3.14, (Double) cap.args[1], 1e-9);
    }

    @Test
    void testUnserializeJsonNegativeNumber() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(unserializeJSON('-7'))");
        assertNotNull(cap.args);
        assertEquals(-7.0, cap.args[0]);
    }

    @Test
    void testUnserializeJsonBoolean() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(unserializeJSON('true'), unserializeJSON('false'))");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0]);
        assertFalse((Boolean) cap.args[1]);
    }

    @Test
    void testUnserializeJsonNull() {
        ResultCapture cap = new ResultCapture();
        // Without a null-option, JSON null becomes Lua nil (not captured)
        run(buildMachine(cap), "local v = unserializeJSON('null')\n_capture(v == nil)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "JSON null should become Lua nil by default");
    }

    @Test
    void testUnserializeJsonNullWithSentinel() {
        ResultCapture cap = new ResultCapture();
        // With tOptions.null = json_null, the sentinel is returned instead of nil
        run(
            buildMachine(cap),
            "local t = unserializeJSON('{\"a\":null}', {null=json_null})\n" + "_capture(t.a == json_null)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "json_null sentinel should be returned for null fields");
    }

    @Test
    void testUnserializeJsonEmptyObject() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local t = unserializeJSON('{}')\n_capture(type(t), next(t) == nil)");
        assertNotNull(cap.args);
        assertEquals("table", cap.args[0]);
        assertTrue((Boolean) cap.args[1], "Empty object should produce an empty table");
    }

    @Test
    void testUnserializeJsonEmptyArray() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local t = unserializeJSON('[]')\n_capture(type(t), #t)");
        assertNotNull(cap.args);
        assertEquals("table", cap.args[0]);
        assertEquals(0.0, cap.args[1], "Empty array should produce a table with length 0");
    }

    @Test
    void testUnserializeJsonArray() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local t = unserializeJSON('[1,2,3]')\n_capture(t[1],t[2],t[3],#t)");
        assertNotNull(cap.args);
        assertEquals(1.0, cap.args[0]);
        assertEquals(2.0, cap.args[1]);
        assertEquals(3.0, cap.args[2]);
        assertEquals(3.0, cap.args[3]);
    }

    @Test
    void testUnserializeJsonObject() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local t = unserializeJSON('{\"x\":10,\"y\":20}')\n_capture(t.x, t.y)");
        assertNotNull(cap.args);
        assertEquals(10.0, cap.args[0]);
        assertEquals(20.0, cap.args[1]);
    }

    @Test
    void testUnserializeJsonWhitespace() {
        ResultCapture cap = new ResultCapture();
        // JSON allows arbitrary whitespace around tokens
        run(buildMachine(cap), "local t = unserializeJSON('  {  \"k\" :  \"v\"  }  ')\n_capture(t.k)");
        assertNotNull(cap.args);
        assertEquals("v", cap.args[0]);
    }

    @Test
    void testUnserializeJsonUnicodeEscape() {
        ResultCapture cap = new ResultCapture();
        // \u0041 is 'A', \u03B1 is the Greek letter alpha (multi-byte UTF-8)
        run(
            buildMachine(cap),
            "local s1 = unserializeJSON('\"\\\\u0041\"')\n" + "local s2 = unserializeJSON('\"\\\\u03B1\"')\n"
                + "_capture(s1, #s2)");
        assertNotNull(cap.args);
        assertEquals("A", cap.args[0]);
        // α is 2 bytes in UTF-8
        assertEquals(2.0, cap.args[1]);
    }

    @Test
    void testUnserializeJsonNestedStructure() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local t = unserializeJSON('{\"a\":{\"b\":[1,2,{\"c\":true}]}}')\n"
                + "_capture(t.a.b[1], t.a.b[2], t.a.b[3].c)");
        assertNotNull(cap.args);
        assertEquals(1.0, cap.args[0]);
        assertEquals(2.0, cap.args[1]);
        assertTrue((Boolean) cap.args[2]);
    }

    @Test
    void testUnserializeJsonInvalidInputReturnsNilAndError() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local result, err = unserializeJSON('not valid json')\n" + "_capture(result == nil, type(err))");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "Invalid JSON must return nil as first value");
        assertEquals("string", cap.args[1], "Second return value must be an error string");
    }

    @Test
    void testUnserializeJsonTrailingContentFails() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local result, err = unserializeJSON('42 extra')\n" + "_capture(result == nil, type(err))");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "JSON with trailing content must return nil");
        assertEquals("string", cap.args[1]);
    }

    // -------------------------------------------------------------------------
    // Alias tests
    // -------------------------------------------------------------------------

    @Test
    void testGbSpellingAliasesExist() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(" + "type(serialiseJSON)," + "type(unserialiseJSON)" + ")");
        assertNotNull(cap.args);
        assertEquals("function", cap.args[0], "serialiseJSON alias must exist");
        assertEquals("function", cap.args[1], "unserialiseJSON alias must exist");
    }

    @Test
    void testSerialiseJsonAlias() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(serialiseJSON({1,2,3}))");
        assertNotNull(cap.args);
        assertEquals("[1,2,3]", cap.args[0]);
    }

    @Test
    void testUnserialiseJsonAlias() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local t = unserialiseJSON('[4,5,6]')\n_capture(t[1],t[2],t[3])");
        assertNotNull(cap.args);
        assertEquals(4.0, cap.args[0]);
        assertEquals(5.0, cap.args[1]);
        assertEquals(6.0, cap.args[2]);
    }
}
