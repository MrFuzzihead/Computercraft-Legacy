package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dan200.computercraft.core.lua.LuaRomTestSupport.Capture;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * Tests that {@code cc.expect} honours a value's {@code __name}, which needs the debug library
 * (Batch C installed it).
 *
 * <p>
 * Function naming in the message is not covered: Cobalt 0.9.9's debug.getinfo reports `what`
 * but never `name`, so that path stays inert here.
 * </p>
 */
class CcExpectDebugTest {

    private static String prelude;

    @BeforeAll
    static void loadSource() throws IOException {
        String expect = LuaRomTestSupport.readResource("/assets/computercraft/lua/rom/modules/main/cc/expect.lua");
        prelude = "local _expect_module = (function()\n" + expect
            + "\nend)()\n"
            + "local expect, field = _expect_module.expect, _expect_module.field\n";
    }

    private static Capture run(String body) {
        Capture capture = new Capture();
        CobaltMachine machine = LuaRomTestSupport.machine(capture);
        LuaRomTestSupport.run(machine, prelude, body);
        return capture;
    }

    @Test
    void errorWithoutANameableCallerStillWorks() {
        // An anonymous function has no name to report, so the message falls back to the old form.
        Capture cap = run(
            "local ok, err = pcall(function() expect(1, 42, 'string') end)\n" + "_capture(ok, tostring(err))");
        assertEquals(false, cap.bool(0));
        assertTrue(
            cap.string(1)
                .contains("bad argument #1"),
            "unexpected message: " + cap.string(1));
    }

    @Test
    void metatableNameIsUsedAsTheType() {
        Capture cap = run(
            "local files = setmetatable({}, { __name = 'FILE*' })\n"
                + "local ok, err = pcall(expect, 1, files, 'string')\n"
                + "_capture(ok, tostring(err))");
        assertEquals(false, cap.bool(0));
        assertTrue(
            cap.string(1)
                .contains("FILE*"),
            "a __name should be reported instead of 'table', got: " + cap.string(1));
    }

    @Test
    void fieldErrorsAlsoUseTheMetatableName() {
        Capture cap = run(
            "local files = setmetatable({}, { __name = 'FILE*' })\n"
                + "local ok, err = pcall(field, { f = files }, 'f', 'string')\n"
                + "_capture(ok, tostring(err))");
        assertEquals(false, cap.bool(0));
        assertTrue(
            cap.string(1)
                .contains("FILE*"),
            "field() should report the __name too, got: " + cap.string(1));
    }
}
