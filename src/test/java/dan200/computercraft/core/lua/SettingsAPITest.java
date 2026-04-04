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
 * Tests for the {@code settings} Lua API ({@code rom/apis/settings}).
 *
 * <p>
 * Pure-logic tests (define, set, get, unset, clear, getNames, getDetails) run with
 * no fs/textutils dependency. Load/save tests inject a minimal Lua mock for both
 * {@code fs} and {@code textutils} via a preamble string prepended to the settings
 * source before each run.
 * </p>
 */
class SettingsAPITest {

    private static String settingsSource;

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @BeforeAll
    static void loadSource() throws IOException {
        settingsSource = readResource("/assets/computercraft/lua/rom/apis/settings");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream is = SettingsAPITest.class.getResourceAsStream(path)) {
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
     * Runs {@code testLua} with the settings source pre-loaded.
     * No fs/textutils mocks are injected — suitable for pure-logic tests.
     */
    private static void run(CobaltMachine machine, String testLua) {
        String combined = settingsSource + "\n" + testLua;
        machine.loadBios(new ByteArrayInputStream(combined.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 50 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    /**
     * Minimal Lua preamble that provides mock {@code fs} and {@code textutils}
     * tables, allowing load/save tests to run without the real ComputerCraft
     * filesystem.
     *
     * <p>
     * The mock filesystem is a Lua table ({@code _mock_fs}) keyed by path.
     * {@code textutils.serialize} produces a valid Lua table literal;
     * {@code textutils.unserialize} evaluates it via {@code loadstring}.
     * </p>
     *
     * @param initialContent The string content to pre-seed at path {@code ".settings"},
     *                       or {@code null} if the file should not exist.
     */
    private static String mockPreamble(String initialContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("_mock_fs = {}\n");
        if (initialContent != null) {
            // Escape the content as a Lua long string to avoid injection issues.
            sb.append("_mock_fs[\".settings\"] = [[\n")
                .append(initialContent)
                .append("]]\n");
        }
        sb.append(
            "fs = {}\n" + "function fs.open(path, mode)\n"
                + "  if mode == 'r' then\n"
                + "    local c = _mock_fs[path]\n"
                + "    if not c then return nil end\n"
                + "    return { readAll = function() return c end, close = function() end }\n"
                + "  elseif mode == 'w' then\n"
                + "    local buf = {}\n"
                + "    return {\n"
                + "      write = function(s) buf[#buf+1] = s end,\n"
                + "      close = function() _mock_fs[path] = table.concat(buf) end\n"
                + "    }\n"
                + "  end\n"
                + "  return nil\n"
                + "end\n"
                + "function fs.exists(path)\n"
                + "  return _mock_fs[path] ~= nil\n"
                + "end\n"
                // Minimal textutils: serialize produces a basic Lua table literal;
                // unserialize evaluates it via loadstring.
                + "textutils = {}\n"
                + "function textutils.serialize(t)\n"
                + "  local parts = {'{'}\n"
                + "  for k, v in pairs(t) do\n"
                + "    local ks\n"
                + "    if type(k) == 'string' then\n"
                + "      ks = '[' .. string.format('%q', k) .. ']'\n"
                + "    else\n"
                + "      ks = '[' .. tostring(k) .. ']'\n"
                + "    end\n"
                + "    local vs\n"
                + "    if type(v) == 'string' then\n"
                + "      vs = string.format('%q', v)\n"
                + "    elseif type(v) == 'boolean' then\n"
                + "      vs = tostring(v)\n"
                + "    elseif type(v) == 'number' then\n"
                + "      vs = tostring(v)\n"
                + "    else\n"
                + "      vs = 'nil'\n"
                + "    end\n"
                + "    parts[#parts+1] = ks .. ' = ' .. vs .. ', '\n"
                + "  end\n"
                + "  parts[#parts+1] = '}'\n"
                + "  return table.concat(parts)\n"
                + "end\n"
                + "function textutils.unserialize(s)\n"
                + "  local f, err = loadstring('return ' .. s)\n"
                + "  if not f then return nil end\n"
                + "  local ok, v = pcall(f)\n"
                + "  if not ok then return nil end\n"
                + "  return v\n"
                + "end\n");
        return sb.toString();
    }

    /**
     * Runs {@code testLua} with both the mock fs/textutils preamble and the
     * settings source pre-loaded.
     */
    private static void runWithMocks(CobaltMachine machine, String initialContent, String testLua) {
        String combined = mockPreamble(initialContent) + settingsSource + "\n" + testLua;
        machine.loadBios(new ByteArrayInputStream(combined.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 50 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    // =========================================================================
    // settings.define / settings.getDetails
    // =========================================================================

    @Test
    void defineAndGetDetailsReturnsOptions() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "define('mykey', { default='hello', description='A test key', type='string' })\n"
                + "local d = getDetails('mykey')\n"
                + "_capture(d.default, d.description, d.type, d.changed)");
        assertNotNull(cap.args);
        assertEquals("hello", cap.args[0]);
        assertEquals("A test key", cap.args[1]);
        assertEquals("string", cap.args[2]);
        assertFalse((Boolean) cap.args[3], "changed must be false when no value is set");
    }

    @Test
    void defineWithNoOptionsCreatesEmptyEntry() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "define('bare')\n" + "local d = getDetails('bare')\n"
                + "_capture(d.changed, d.default, d.type, d.description)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0]);
        assertNull(cap.args[1]);
        assertNull(cap.args[2]);
        assertNull(cap.args[3]);
    }

    @Test
    void defineUpdatesExistingRegistration() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "define('k', { default=1 })\n" + "define('k', { default=2, description='updated' })\n"
                + "local d = getDetails('k')\n"
                + "_capture(d.default, d.description)");
        assertNotNull(cap.args);
        assertEquals(2.0, ((Number) cap.args[0]).doubleValue());
        assertEquals("updated", cap.args[1]);
    }

    @Test
    void defineNonStringNameErrors() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, err = pcall(define, 42)\n" + "_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0]);
        assertTrue(((String) cap.args[1]).contains("string expected"), "Error must mention string expected");
    }

    // =========================================================================
    // settings.undefine
    // =========================================================================

    @Test
    void undefineRemovesRegistration() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "define('gone', { default=99 })\n" + "undefine('gone')\n"
                + "local d = getDetails('gone')\n"
                + "_capture(d.default, d.type, d.description)");
        assertNotNull(cap.args);
        assertNull(cap.args[0]);
        assertNull(cap.args[1]);
        assertNull(cap.args[2]);
    }

    @Test
    void undefinePreservesSetValue() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "define('k', { default='x' })\n" + "set('k', 'y')\n" + "undefine('k')\n" + "_capture(get('k'))");
        assertNotNull(cap.args);
        assertEquals("y", cap.args[0], "Stored value must survive undefine");
    }

    // =========================================================================
    // settings.set / settings.get
    // =========================================================================

    @Test
    void setAndGetReturnsStoredValue() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "set('x', 42)\n_capture(get('x'))");
        assertNotNull(cap.args);
        assertEquals(42.0, ((Number) cap.args[0]).doubleValue());
    }

    @Test
    void getReturnsDefinedDefaultWhenNotSet() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "define('k', { default='def' })\n" + "_capture(get('k'))");
        assertNotNull(cap.args);
        assertEquals("def", cap.args[0]);
    }

    @Test
    void getReturnsFallbackDefaultWhenNeitherSetNorDefined() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(get('missing', 'fallback'))");
        assertNotNull(cap.args);
        assertEquals("fallback", cap.args[0]);
    }

    @Test
    void getReturnsNilWhenNeitherSetNorDefinedAndNoFallback() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(get('missing'))");
        assertNotNull(cap.args);
        assertNull(cap.args[0]);
    }

    @Test
    void setOverridesDefinedDefault() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "define('k', { default='original' })\n" + "set('k', 'override')\n" + "_capture(get('k'))");
        assertNotNull(cap.args);
        assertEquals("override", cap.args[0]);
    }

    @Test
    void setWithWrongTypeErrorsWhenTypeConstrained() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "define('n', { type='number' })\n" + "local ok, err = pcall(set, 'n', 'not-a-number')\n"
                + "_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0]);
        assertTrue(((String) cap.args[1]).contains("number expected"), "Error must mention type mismatch");
    }

    @Test
    void setNilErrors() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, err = pcall(set, 'k', nil)\n" + "_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0]);
        assertTrue(((String) cap.args[1]).contains("nil"), "Error must mention nil");
    }

    @Test
    void setFunctionErrors() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, err = pcall(set, 'k', function() end)\n" + "_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0]);
        assertTrue(((String) cap.args[1]).contains("serialize"), "Error must mention cannot serialize");
    }

    @Test
    void setBooleanValueRoundtrips() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "set('flag', true)\n_capture(get('flag'))");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0]);
    }

    @Test
    void setTableValueRoundtrips() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "set('t', {a=1,b=2})\nlocal v=get('t')\n_capture(v.a, v.b)");
        assertNotNull(cap.args);
        assertEquals(1.0, ((Number) cap.args[0]).doubleValue());
        assertEquals(2.0, ((Number) cap.args[1]).doubleValue());
    }

    // =========================================================================
    // settings.unset
    // =========================================================================

    @Test
    void unsetRevertsToDefinedDefault() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "define('k', { default='def' })\n" + "set('k', 'override')\n" + "unset('k')\n" + "_capture(get('k'))");
        assertNotNull(cap.args);
        assertEquals("def", cap.args[0]);
    }

    @Test
    void unsetUndefinedKeyLeavesNoValue() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "set('k', 'x')\n" + "unset('k')\n" + "_capture(get('k', 'fallback'))");
        assertNotNull(cap.args);
        assertEquals("fallback", cap.args[0]);
    }

    // =========================================================================
    // settings.clear
    // =========================================================================

    @Test
    void clearRemovesAllValues() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "set('a', 1)\nset('b', 2)\nset('c', 3)\n" + "clear()\n" + "_capture(get('a'), get('b'), get('c'))");
        assertNotNull(cap.args);
        assertNull(cap.args[0]);
        assertNull(cap.args[1]);
        assertNull(cap.args[2]);
    }

    @Test
    void clearPreservesDefinitions() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "define('k', { default='d' })\n" + "set('k', 'v')\n" + "clear()\n" + "_capture(get('k'))");
        assertNotNull(cap.args);
        assertEquals("d", cap.args[0], "Defined default must survive clear()");
    }

    // =========================================================================
    // settings.getNames
    // =========================================================================

    @Test
    void getNamesReturnsSortedUnionOfDefinedAndSetNames() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "define('charlie')\n" + "define('alpha')\n"
                + "set('bravo', 10)\n"
                + "local names = getNames()\n"
                + "_capture(#names, names[1], names[2], names[3])");
        assertNotNull(cap.args);
        assertEquals(3.0, ((Number) cap.args[0]).doubleValue());
        assertEquals("alpha", cap.args[1]);
        assertEquals("bravo", cap.args[2]);
        assertEquals("charlie", cap.args[3]);
    }

    @Test
    void getNamesIncludesUndefinedSetKeys() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "set('undeclared', true)\n" + "local names = getNames()\n" + "_capture(#names, names[1])");
        assertNotNull(cap.args);
        assertEquals(1.0, ((Number) cap.args[0]).doubleValue());
        assertEquals("undeclared", cap.args[1]);
    }

    @Test
    void getNamesIsEmptyInitially() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(#getNames())");
        assertNotNull(cap.args);
        assertEquals(0.0, ((Number) cap.args[0]).doubleValue());
    }

    // =========================================================================
    // settings.getDetails — changed flag
    // =========================================================================

    @Test
    void getDetailsChangedTrueWhenValueSet() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "define('k', { default=0 })\n" + "set('k', 99)\n"
                + "local d = getDetails('k')\n"
                + "_capture(d.changed, d.value, d.default)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0]);
        assertEquals(99.0, ((Number) cap.args[1]).doubleValue());
        assertEquals(0.0, ((Number) cap.args[2]).doubleValue());
    }

    @Test
    void getDetailsForUnknownKeyReturnsEmptyTable() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local d = getDetails('nope')\n" + "_capture(d.changed, d.default, d.type, d.description)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0]);
        assertNull(cap.args[1]);
        assertNull(cap.args[2]);
        assertNull(cap.args[3]);
    }

    // =========================================================================
    // settings.load
    // =========================================================================

    @Test
    void loadMissingFileReturnsTrue() {
        ResultCapture cap = new ResultCapture();
        runWithMocks(
            buildMachine(cap),
            null, // no .settings file in mock fs
            "local ok, err = load()\n" + "_capture(ok, err)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "Missing file must be treated as empty (fresh install)");
        assertNull(cap.args[1], "No error message expected for missing file");
    }

    @Test
    void loadPopulatesValues() {
        // Pre-seed mock fs with a serialized table containing one entry.
        String fileContent = "{[\"mykey\"] = 42, }";
        ResultCapture cap = new ResultCapture();
        runWithMocks(buildMachine(cap), fileContent, "local ok = load()\n" + "_capture(ok, get('mykey'))");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0]);
        assertEquals(42.0, ((Number) cap.args[1]).doubleValue());
    }

    @Test
    void loadSkipsTypeMismatchedValues() {
        // 'strict' is registered as number; file contains a string value.
        String fileContent = "{[\"strict\"] = \"oops\", }";
        ResultCapture cap = new ResultCapture();
        runWithMocks(
            buildMachine(cap),
            fileContent,
            "define('strict', { type='number', default=0 })\n" + "load()\n" + "_capture(get('strict'))");
        assertNotNull(cap.args);
        assertEquals(0.0, ((Number) cap.args[0]).doubleValue(), "Type-mismatched value must be skipped");
    }

    @Test
    void loadIsAdditiveDoesNotClearExistingValues() {
        String fileContent = "{[\"fromfile\"] = \"yes\", }";
        ResultCapture cap = new ResultCapture();
        runWithMocks(
            buildMachine(cap),
            fileContent,
            "set('existing', 'kept')\n" + "load()\n" + "_capture(get('existing'), get('fromfile'))");
        assertNotNull(cap.args);
        assertEquals("kept", cap.args[0], "Pre-existing value must survive load()");
        assertEquals("yes", cap.args[1]);
    }

    @Test
    void loadEmptyFileSucceeds() {
        ResultCapture cap = new ResultCapture();
        runWithMocks(
            buildMachine(cap),
            "", // empty file
            "local ok = load()\n" + "_capture(ok)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0]);
    }

    // =========================================================================
    // settings.save
    // =========================================================================

    @Test
    void saveReturnsTrueOnSuccess() {
        ResultCapture cap = new ResultCapture();
        runWithMocks(buildMachine(cap), null, "set('x', 1)\n" + "local ok = save()\n" + "_capture(ok)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0]);
    }

    @Test
    void saveThenLoadRoundtrip() {
        // Save a number, a string, and a boolean; then reload and verify all
        // three values are recovered identically.
        ResultCapture cap = new ResultCapture();
        runWithMocks(
            buildMachine(cap),
            null,
            "set('n', 123)\n" + "set('s', 'hello')\n"
                + "set('b', false)\n"
                + "save()\n"
                + "clear()\n"
                + "load()\n"
                + "_capture(get('n'), get('s'), get('b'))");
        assertNotNull(cap.args);
        assertEquals(123.0, ((Number) cap.args[0]).doubleValue(), "number must round-trip");
        assertEquals("hello", cap.args[1], "string must round-trip");
        assertFalse((Boolean) cap.args[2], "boolean false must round-trip");
    }

    @Test
    void saveWithCustomPathUsesCorrectFile() {
        // Save to a custom path; confirm that loading from the same path works.
        ResultCapture cap = new ResultCapture();
        // Reuse mock preamble but use path "custom.cfg" rather than ".settings"
        runWithMocks(
            buildMachine(cap),
            null,
            "set('val', 'custom')\n" + "save('custom.cfg')\n"
                + "clear()\n"
                + "load('custom.cfg')\n"
                + "_capture(get('val'))");
        assertNotNull(cap.args);
        assertEquals("custom", cap.args[0]);
    }
}
