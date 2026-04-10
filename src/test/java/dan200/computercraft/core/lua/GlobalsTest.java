package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import org.junit.jupiter.api.AfterEach;
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
 * Unit tests for the {@code _G} additions:
 * <ul>
 * <li>{@code _HOST} — set by {@code CobaltMachine} from
 * {@link dan200.computercraft.core.computer.IComputerEnvironment#getHostString()}</li>
 * <li>{@code _CC_DEFAULT_SETTINGS} — set from {@link ComputerCraft#cc_default_settings}
 * when non-empty; absent when empty</li>
 * <li>{@code read(replaceChar, history, completeFn, default)} — optional 4th argument
 * pre-populates the input line</li>
 * </ul>
 */
class GlobalsTest {

    /**
     * The {@code read} function definition extracted verbatim from bios.lua,
     * from {@code function read(} (inclusive) up to (exclusive) {@code loadfile =}.
     */
    private static String readDef;

    /** Saved value of {@link ComputerCraft#cc_default_settings} before each test. */
    private String savedDefaultSettings;

    // -------------------------------------------------------------------------
    // Setup / teardown
    // -------------------------------------------------------------------------

    @BeforeAll
    static void extractReadDef() throws IOException {
        String biosSource;
        try (InputStream is = GlobalsTest.class.getResourceAsStream("/assets/computercraft/lua/bios.lua")) {
            assertNotNull(is, "bios.lua must be on the test classpath");
            try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                biosSource = scanner.hasNext() ? scanner.next() : "";
            }
        }

        int start = biosSource.indexOf("\nfunction read(");
        int end = biosSource.indexOf("\nloadfile =");
        assertTrue(start >= 0, "bios.lua must contain 'function read(' definition");
        assertTrue(end > start, "bios.lua must contain 'loadfile =' after read()");
        readDef = biosSource.substring(start, end);
    }

    @AfterEach
    void restoreDefaultSettings() {
        // Restore static field so tests do not leak state into one another.
        ComputerCraft.cc_default_settings = savedDefaultSettings != null ? savedDefaultSettings : "";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    static class ResultCapture {

        Object[] args;
    }

    private ResultCapture buildCapture() {
        savedDefaultSettings = ComputerCraft.cc_default_settings;
        return new ResultCapture();
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

    private static void run(CobaltMachine machine, String lua) {
        machine.loadBios(new ByteArrayInputStream(lua.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 50 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    /**
     * Builds a Lua preamble that mocks all globals required by the {@code read}
     * function: {@code term}, {@code keys}, {@code os.pullEvent}, and
     * {@code print}.
     *
     * <p>
     * {@code os.pullEvent} is replaced with a plain (non-yielding) Lua
     * function that returns events from {@code _mock_events} in order, so the
     * event loop runs synchronously in a single {@code machine.handleEvent} call.
     * </p>
     *
     * @param events alternating (eventName, param) pairs to return from pullEvent
     */
    private static String buildReadPreamble(Object... events) {
        StringBuilder sb = new StringBuilder();

        // term stub — read() uses setCursorBlink, getSize, getCursorPos, write, setCursorPos
        sb.append("term = {\n");
        sb.append("  setCursorBlink = function() end,\n");
        sb.append("  getSize        = function() return 51, 19 end,\n");
        sb.append("  getCursorPos   = function() return 1, 1 end,\n");
        sb.append("  write          = function() end,\n");
        sb.append("  setCursorPos   = function() end,\n");
        sb.append("  setTextColor   = function() end,\n");
        sb.append("  setBackgroundColor = function() end,\n");
        sb.append("  getTextColor   = function() return 1 end,\n");
        sb.append("  getBackgroundColor = function() return 32768 end,\n");
        sb.append("}\n");

        // keys stub — only 'enter' needs to match what the mock events return
        sb.append("keys = {\n");
        sb.append("  enter = 28, left = 203, right = 205, up = 200, down = 208,\n");
        sb.append("  backspace = 14, home = 199, delete = 211, tab = 15,\n");
        sb.append("}\n");
        sb.append("keys[\"end\"] = 207\n");

        // print stub
        sb.append("function print() end\n");

        // os.pullEvent mock — synchronous, no coroutine yield
        sb.append("local _mock_events = {\n");
        for (int i = 0; i < events.length; i += 2) {
            sb.append("  { \"")
                .append(events[i])
                .append("\", ")
                .append(events[i + 1])
                .append(" },\n");
        }
        sb.append("}\n");
        sb.append("local _ev_idx = 1\n");
        sb.append("os = {}\n");
        sb.append("function os.pullEvent()\n");
        sb.append("  local ev = _mock_events[_ev_idx] or { \"key\", 28 }\n");
        sb.append("  _ev_idx = _ev_idx + 1\n");
        sb.append("  return ev[1], ev[2]\n");
        sb.append("end\n");

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // _CC_DEFAULT_SETTINGS global
    // -------------------------------------------------------------------------

    @Test
    void ccDefaultSettingsGlobalIsSetWhenFieldIsNonEmpty() {
        ResultCapture cap = buildCapture();
        ComputerCraft.cc_default_settings = "shell.autocomplete=false";
        run(buildMachine(cap), "_capture(_CC_DEFAULT_SETTINGS)");
        assertArrayEquals(
            new Object[] { "shell.autocomplete=false" },
            cap.args,
            "_CC_DEFAULT_SETTINGS should equal the configured cc_default_settings value");
    }

    @Test
    void ccDefaultSettingsGlobalIsAbsentWhenFieldIsEmpty() {
        ResultCapture cap = buildCapture();
        ComputerCraft.cc_default_settings = "";
        run(buildMachine(cap), "_capture(_CC_DEFAULT_SETTINGS)");
        assertArrayEquals(
            new Object[] { null },
            cap.args,
            "_CC_DEFAULT_SETTINGS should be nil when cc_default_settings is empty");
    }

    @Test
    void ccDefaultSettingsGlobalIsAbsentWhenFieldIsNull() {
        ResultCapture cap = buildCapture();
        ComputerCraft.cc_default_settings = null;
        run(buildMachine(cap), "_capture(_CC_DEFAULT_SETTINGS)");
        assertArrayEquals(
            new Object[] { null },
            cap.args,
            "_CC_DEFAULT_SETTINGS should be nil when cc_default_settings is null");
    }

    // -------------------------------------------------------------------------
    // read() default parameter
    // -------------------------------------------------------------------------

    @Test
    void readWithDefaultReturnsDefaultWhenEnterPressedImmediately() {
        ResultCapture cap = buildCapture();
        // Simulate pressing Enter immediately (key 28 = keys.enter)
        String preamble = buildReadPreamble("key", 28);
        run(buildMachine(cap), preamble + readDef + "\n_capture(read(nil, nil, nil, \"hello\"))");
        assertArrayEquals(
            new Object[] { "hello" },
            cap.args,
            "read() with default 'hello' and immediate Enter should return 'hello'");
    }

    @Test
    void readWithoutDefaultReturnsEmptyWhenEnterPressedImmediately() {
        ResultCapture cap = buildCapture();
        String preamble = buildReadPreamble("key", 28);
        run(buildMachine(cap), preamble + readDef + "\n_capture(read())");
        assertArrayEquals(
            new Object[] { "" },
            cap.args,
            "read() with no default and immediate Enter should return empty string");
    }

    @Test
    void readWithDefaultAllowsTypingToExtend() {
        ResultCapture cap = buildCapture();
        // Simulate typing 'x' then Enter: char 'x' (120) then key enter (28)
        String preamble = buildReadPreamble("char", "\"x\"", "key", 28);
        run(buildMachine(cap), preamble + readDef + "\n_capture(read(nil, nil, nil, \"hi\"))");
        assertArrayEquals(
            new Object[] { "hix" },
            cap.args,
            "read() with default 'hi', then typing 'x' and Enter should return 'hix'");
    }

    @Test
    void readWithDefaultAndNilExplicitlyBehavesLikeNoDefault() {
        ResultCapture cap = buildCapture();
        String preamble = buildReadPreamble("key", 28);
        run(buildMachine(cap), preamble + readDef + "\n_capture(read(nil, nil, nil, nil))");
        assertArrayEquals(
            new Object[] { "" },
            cap.args,
            "read(nil, nil, nil, nil) should behave like read() with no default");
    }
}
