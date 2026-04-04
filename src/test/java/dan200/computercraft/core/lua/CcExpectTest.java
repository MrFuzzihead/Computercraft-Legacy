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
 * Tests for the {@code cc.expect} Lua module ({@code rom/modules/main/cc/expect.lua}).
 *
 * <p>
 * The environment deliberately defines {@code table.pack} <em>without</em> the
 * {@code n} field (matching bios.lua's polyfill) to verify that
 * {@code get_type_names} does not rely on {@code table.pack.n}.
 * </p>
 */
class CcExpectTest {

    private static String expectSource;

    @BeforeAll
    static void loadSources() throws IOException {
        expectSource = readResource("/assets/computercraft/lua/rom/modules/main/cc/expect.lua");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream is = CcExpectTest.class.getResourceAsStream(path)) {
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
     * Builds a preamble that simulates the bios.lua environment:
     * {@code table.pack} is defined <em>without</em> the {@code n} field, exactly
     * as bios.lua does it. Then {@code cc.expect} is loaded and its three
     * functions are exposed as globals.
     */
    private String buildPreamble() {
        return // bios.lua table.pack polyfill — intentionally lacks the 'n' field
        "table.pack = function( ... ) return { ... } end\n" + "do\n"
            + "  local fn, err = loadstring([=[\n"
            + expectSource
            + "\n]=])\n"
            + "  if not fn then error('cc.expect load failed: ' .. tostring(err)) end\n"
            + "  local m = fn()\n"
            + "  expect = m.expect\n"
            + "  field  = m.field\n"
            + "  range  = m.range\n"
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
    // expect() — success path
    // -------------------------------------------------------------------------

    @Test
    void expectSuccessReturnsSameValue() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(expect(1, 'hello', 'string'))");
        assertArrayEquals(new Object[] { "hello" }, cap.args);
    }

    // -------------------------------------------------------------------------
    // expect() — failure path (exercises get_type_names / the table.pack.n bug)
    // -------------------------------------------------------------------------

    @Test
    void expectFailureSingleTypeProducesCorrectMessage() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "local ok, err = pcall(expect, 1, 42, 'string')\n_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0], "expect should throw for wrong type");
        assertTrue(
            ((String) cap.args[1]).contains("string expected, got number"),
            "Error should say 'string expected, got number'; got: " + cap.args[1]);
    }

    @Test
    void expectFailureMultipleTypesProducesOrSeparatedList() {
        // This is the primary regression test: get_type_names iterates the type
        // list with table.pack.n, which is nil in the bios.lua polyfill.
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local ok, err = pcall(expect, 1, 42, 'string', 'table')\n_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0], "expect should throw for wrong type");
        String err = (String) cap.args[1];
        assertTrue(
            err.contains("string or table expected"),
            "Error should say 'string or table expected'; got: " + err);
    }

    @Test
    void expectNilTypeStringIsStrippedFromErrorMessage() {
        // When "nil" appears in the allowed-types list it is stripped from the
        // human-readable message (implied by absence). Before the fix, iterating
        // types.n == nil would error before any message could be produced.
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local ok, err = pcall(expect, 1, false, 'string', 'nil')\n_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0], "expect should throw for wrong type");
        String err = (String) cap.args[1];
        assertTrue(
            err.contains("string expected"),
            "Error should say 'string expected' with nil stripped; got: " + err);
    }

    // -------------------------------------------------------------------------
    // field() — exercises get_type_names via the wrong-field-type code path
    // -------------------------------------------------------------------------

    @Test
    void fieldSuccessReturnsValue() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(field({x = 42}, 'x', 'number'))");
        assertArrayEquals(new Object[] { 42.0 }, cap.args);
    }

    @Test
    void fieldWrongTypeProducesCorrectMessage() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local ok, err = pcall(field, {x = 'oops'}, 'x', 'number', 'nil')\n_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0], "field should throw for wrong field type");
        assertTrue(
            ((String) cap.args[1]).contains("number expected"),
            "Error should mention 'number expected'; got: " + cap.args[1]);
    }
}
