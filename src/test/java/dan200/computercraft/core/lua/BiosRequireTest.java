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
 * Tests for the {@code require()} implementation in {@code bios.lua}.
 *
 * <p>
 * The {@code require} block is extracted from the live {@code bios.lua} source
 * at test startup (via {@link BeforeAll}) so that any change to that function
 * is automatically reflected in these tests. A mock filesystem is injected
 * via the Lua preamble so the tests run without touching disk.
 * </p>
 *
 * <h2>Regression guard</h2>
 * <p>
 * Before the fix the sentinel stored in {@code package.loaded} during loading
 * was the boolean {@code true}. Any circular {@code require} or module that
 * returned {@code nil} would therefore expose a boolean rather than a table.
 * The tests below verify that the sentinel is always a table.
 * </p>
 */
class BiosRequireTest {

    /**
     * The {@code package}/{@code require} block extracted verbatim from
     * {@code bios.lua}. Bounded by the comment {@code "-- require / package"}
     * (inclusive) and the start of {@code function os.run(} (exclusive).
     */
    private static String requireBlock;

    @BeforeAll
    static void extractRequireBlock() throws IOException {
        String biosSource;
        try (InputStream is = BiosRequireTest.class.getResourceAsStream("/assets/computercraft/lua/bios.lua")) {
            assertNotNull(is, "bios.lua must be on the test classpath");
            try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                biosSource = scanner.hasNext() ? scanner.next() : "";
            }
        }

        int start = biosSource.indexOf("-- require / package");
        int end = biosSource.indexOf("\nfunction os.run(");
        assertTrue(start >= 0, "bios.lua must contain the '-- require / package' comment");
        assertTrue(end > start, "bios.lua must contain 'function os.run(' after the require block");
        requireBlock = biosSource.substring(start, end);
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
     * Builds a Lua preamble that:
     * <ol>
     * <li>Populates a {@code _modules} table mapping {@code "name.lua"} →
     * Lua source (supplied as alternating name/source pairs).</li>
     * <li>Provides mock {@code fs} (exists, getName, open) and {@code loadfile}
     * backed by that table.</li>
     * <li>Embeds the live {@link #requireBlock} so the real
     * {@code require()} function is under test.</li>
     * <li>Overrides {@code package.path} to the flat {@code "?.lua"} template
     * so module names map directly to keys in the mock fs.</li>
     * </ol>
     *
     * @param moduleEntries alternating (name, luaSource) pairs
     */
    private String buildPreamble(String... moduleEntries) {
        StringBuilder modules = new StringBuilder("local _modules = {\n");
        for (int i = 0; i < moduleEntries.length; i += 2) {
            modules.append("  [\"")
                .append(moduleEntries[i])
                .append(".lua\"] = [=[\n")
                .append(moduleEntries[i + 1])
                .append("\n]=],\n");
        }
        modules.append("}\n");

        return modules
            // bios.lua polyfill — no 'n' field, matching the real runtime.
            + "table.pack = function( ... ) return { ... } end\n"
            // Mock filesystem backed by _modules.
            + "fs = {}\n"
            + "fs.exists  = function(p) return _modules[p] ~= nil end\n"
            + "fs.getName = function(p) return p:match('[^/]+$') or p end\n"
            + "fs.open    = function(p, _)\n"
            + "  local src = _modules[p]\n"
            + "  if not src then return nil end\n"
            + "  return { readAll = function() return src end, close = function() end }\n"
            + "end\n"
            // loadfile mock: reads from the fs mock and compiles via loadstring.
            // No setfenv: modules inherit the global environment so they can call require().
            + "loadfile = function(path, _env)\n"
            + "  local f = fs.open(path, 'r')\n"
            + "  if not f then return nil, 'not found' end\n"
            + "  local src = f.readAll(); f.close()\n"
            + "  return loadstring(src, '@' .. path)\n"
            + "end\n"
            // Live require block from bios.lua (sets package = {}, package.path, require).
            + requireBlock
            + "\n"
            // Override to flat template so "mod" maps to "mod.lua".
            + "package.path = \"?.lua\"\n";
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
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void requireNormalModuleReturnsModuleTable() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble("mymod", "return { answer = 42 }"),
            "local m = require('mymod')\n_capture(m.answer)");
        assertArrayEquals(new Object[] { 42.0 }, cap.args);
    }

    @Test
    void requireCachesModuleOnSecondCall() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble("counter", "return {}"),
            "local a = require('counter')\n" + "local b = require('counter')\n" + "_capture(a == b)");
        assertEquals(Boolean.TRUE, cap.args[0], "second require should return the cached module");
    }

    @Test
    void requireNilReturningModuleFallsBackToSentinelTable() {
        // Before the fix, package.loaded[name] was set to boolean true when the
        // module returned nil. After the fix it must be a table (the sentinel).
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble("nilmod", "return nil"), "local m = require('nilmod')\n_capture(type(m))");
        assertEquals("table", cap.args[0], "nil-returning module should fall back to sentinel table, not boolean true");
    }

    @Test
    void requireCircularDependencyReturnsSentinelTable() {
        // mod_a requires mod_b; mod_b requires mod_a while mod_a is still loading.
        // With the old code the circular require returned boolean true.
        // After the fix it must return the (empty) sentinel table.
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(
                "mod_a",
                "local b = require('mod_b'); return { b_ref = b }",
                "mod_b",
                "local a = require('mod_a'); return { a_type = type(a) }"),
            "local a = require('mod_a')\n" + "_capture(a.b_ref.a_type)");
        assertEquals("table", cap.args[0], "circular require should receive an empty-table sentinel, not boolean true");
    }
}
