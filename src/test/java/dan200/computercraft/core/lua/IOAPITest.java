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
 * Tests for the three CC:Tweaked io API improvements ported into io.lua:
 *
 * <ol>
 * <li><b>Handle.write varargs</b> — a text-mode handle returned by
 * {@code io.open(path, "w")} (or {@code "a"}) now accepts multiple
 * string/number arguments and writes them all, returning {@code self}
 * for chaining.</li>
 * <li><b>io.write varargs</b> — {@code io.write("a","b","c")} writes every
 * argument to the current output (through {@code g_defaultOutput.write},
 * which is also updated); returns the current output handle.</li>
 * <li><b>io.open unsupported modes</b> — passing {@code "r+"}, {@code "w+"},
 * or any other unrecognised mode now returns {@code nil, "Unsupported mode"}
 * instead of throwing a Lua error. An unresolvable path returns
 * {@code nil, "Failed to open file"}.</li>
 * </ol>
 *
 * <h2>Test strategy</h2>
 * <p>
 * Each test loads io.lua into a fresh {@link CobaltMachine} preceded by a
 * preamble that provides:
 * <ul>
 * <li>A mock {@code fs} table whose {@code open} function returns in-memory
 * handles for {@code "w"} and {@code "r"} modes and {@code nil} for any
 * path named {@code "missing.txt"}.</li>
 * <li>A stub {@code write} global (used by {@code g_defaultOutput.write} via
 * {@code _G.write}) that appends each call's argument to {@code _writes}.</li>
 * <li>A {@code _writes} table shared between the mock and test assertions.</li>
 * </ul>
 * The {@code _capture(...)} helper (injected from Java) records the return
 * values of the expression under test.
 * </p>
 */
class IOAPITest {

    private static String ioSource;

    // ------------------------------------------------------------------
    // Mock preamble – injects `fs`, `_G.write`, and a `_writes` tracker
    // ------------------------------------------------------------------

    /**
     * Sets up:
     * <ul>
     * <li>{@code _real_type} — saved copy of the built-in {@code type} function.</li>
     * <li>{@code _writes} — Lua array; every call to a mock {@code file.write}
     * or the stub {@code _G.write} appends the argument.</li>
     * <li>{@code fs.open} — returns:
     * <ul>
     * <li>a writable handle for modes {@code "w"} and {@code "a"},</li>
     * <li>a readable handle for mode {@code "r"},</li>
     * <li>a read-write handle with {@code seek} for modes {@code "r+"} and {@code "w+"},</li>
     * <li>{@code nil} for the path {@code "missing.txt"} when mode requires the file to
     * exist ({@code "r"}, {@code "r+"}), or for any completely unrecognised mode.</li>
     * </ul>
     * </li>
     * <li>{@code write} — stub for the global terminal {@code write}.</li>
     * </ul>
     */
    private static final String MOCK_PREAMBLE = "_real_type = type\n" + "_writes = {}\n"
        + "fs = {\n"
        + "  open = function(path, mode)\n"
        + "    if (mode == 'r' or mode == 'r+') and path == 'missing.txt' then return nil end\n"
        + "    if mode == 'w' or mode == 'a' then\n"
        + "      return {\n"
        + "        write = function(s) _writes[#_writes+1] = s end,\n"
        + "        flush = function() end,\n"
        + "        close = function() end,\n"
        + "      }\n"
        + "    elseif mode == 'r' then\n"
        + "      return {\n"
        + "        readLine  = function() return nil end,\n"
        + "        readAll   = function() return ''  end,\n"
        + "        close     = function() end,\n"
        + "      }\n"
        + "    elseif mode == 'r+' or mode == 'w+' then\n"
        + "      return {\n"
        + "        readLine = function() return nil end,\n"
        + "        readAll  = function() return '' end,\n"
        + "        write    = function(s) _writes[#_writes+1] = s end,\n"
        + "        seek     = function(whence, offset) return 0 end,\n"
        + "        flush    = function() end,\n"
        + "        close    = function() end,\n"
        + "      }\n"
        + "    end\n"
        + "    return nil\n"
        + "  end,\n"
        + "}\n"
        + "write = function(s) _writes[#_writes+1] = s end\n";

    // ------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------

    @BeforeAll
    static void loadSources() throws IOException {
        ioSource = readResource("/assets/computercraft/lua/rom/apis/io");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream is = IOAPITest.class.getResourceAsStream(path)) {
            assertNotNull(is, path + " must be on the test classpath");
            try (Scanner sc = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                return sc.hasNext() ? sc.next() : "";
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

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

    /** Runs {@code testLua} with the mock preamble and io source pre-loaded. */
    private static void run(CobaltMachine machine, String testLua) {
        String combined = MOCK_PREAMBLE + "\n" + ioSource + "\n" + testLua;
        machine.loadBios(new ByteArrayInputStream(combined.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 50 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    // ==================================================================
    // Handle.write — multiple arguments
    // ==================================================================

    @Test
    void handleWriteAcceptsMultipleStringArgs() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local f = open('test.txt', 'w')\n" + "f:write('hello', ' ', 'world')\n"
                + "_capture(table.concat(_writes, ''))");
        assertNotNull(cap.args);
        assertEquals("hello world", cap.args[0]);
    }

    @Test
    void handleWriteAcceptsNumberArgs() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local f = open('test.txt', 'w')\n" + "f:write(1, 2, 3)\n" + "_capture(table.concat(_writes, ''))");
        assertNotNull(cap.args);
        assertEquals("123", cap.args[0]);
    }

    @Test
    void handleWriteAcceptsSingleArg() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local f = open('test.txt', 'w')\n" + "f:write('only')\n" + "_capture(table.concat(_writes, ''))");
        assertNotNull(cap.args);
        assertEquals("only", cap.args[0]);
    }

    @Test
    void handleWriteReturnsSelf() {
        // Chaining: f:write("a"):write("b") must work — each write returns the handle.
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local f = open('test.txt', 'w')\n" + "local ret = f:write('x')\n" + "_capture(ret == f)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "write() must return self for chaining");
    }

    @Test
    void handleWriteChainingWorksForMultipleArgs() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local f = open('test.txt', 'w')\n" + "f:write('a', 'b'):write('c', 'd')\n"
                + "_capture(table.concat(_writes, ''))");
        assertNotNull(cap.args);
        assertEquals("abcd", cap.args[0]);
    }

    @Test
    void appendModeHandleWriteAcceptsMultipleArgs() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local f = open('test.txt', 'a')\n" + "f:write('x', 'y', 'z')\n" + "_capture(table.concat(_writes, ''))");
        assertNotNull(cap.args);
        assertEquals("xyz", cap.args[0]);
    }

    // ==================================================================
    // io.write — multiple arguments, return value
    // ==================================================================

    @Test
    void ioWriteAcceptsMultipleArgs() {
        // Redirect output to a file handle so that the global `write` function
        // (io.lua's write) routes through the mock file's write method instead of
        // _G.write (which would create infinite recursion after io.lua redefines it).
        // We must also restore `type = _real_type` first because io.lua's output()
        // calls _G.type internally, which would recurse into io.lua's own type().
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "type = _real_type\n" + "local f = open('test.txt', 'w')\n"
                + "output(f)\n"
                + "write('x', 'y', 'z')\n"
                + "_capture(table.concat(_writes, ''))");
        assertNotNull(cap.args);
        assertEquals("xyz", cap.args[0]);
    }

    @Test
    void ioWriteReturnsOutputHandle() {
        // write(...) must return the current output handle (for chaining).
        // Same _G.type / output() fix as ioWriteAcceptsMultipleArgs.
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "type = _real_type\n" + "local f = open('test.txt', 'w')\n"
                + "output(f)\n"
                + "local ret = write('test')\n"
                + "_capture(ret == f, f.bFileHandle)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "write() must return the current output handle");
        assertTrue((Boolean) cap.args[1], "returned handle must have bFileHandle == true");
    }

    // ==================================================================
    // io.open — mode validation / nil+err returns
    // ==================================================================

    @Test
    void openWithUnknownModeReturnsNilAndError() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local f, err = open('test.txt', 'x')\n" + "_capture(f, err)");
        assertNotNull(cap.args);
        assertNull(cap.args[0]);
        assertEquals("Unsupported mode", cap.args[1]);
    }

    @Test
    void openWithMissingFileReturnsNilAndError() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local f, err = open('missing.txt', 'r')\n" + "_capture(f, err)");
        assertNotNull(cap.args);
        assertNull(cap.args[0], "open for a missing file must return nil as first value");
        assertNotNull(cap.args[1], "open for a missing file must return an error string");
        assertFalse(((String) cap.args[1]).isEmpty(), "Error message must be non-empty");
    }

    @Test
    void openWithValidModeReturnsHandle() {
        // Avoid calling type(f) — after io.lua loads, type() is the io.type shadow,
        // and _G.type inside it recurses. Check bFileHandle directly instead.
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local f, err = open('test.txt', 'w')\n" + "_capture(f ~= nil, err, f.bFileHandle, f.bClosed)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "open('w') must return a non-nil handle");
        assertNull(cap.args[1], "err must be nil on success");
        assertTrue((Boolean) cap.args[2], "handle must have bFileHandle == true");
        assertFalse((Boolean) cap.args[3], "handle must have bClosed == false");
    }

    // ==================================================================
    // io.open — r+ and w+ mode support
    // ==================================================================

    @Test
    void openWithRPlusReturnsHandleForExistingFile() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local f, err = open('test.txt', 'r+')\n" + "_capture(f ~= nil, err, f.bFileHandle, f.bClosed)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "open('r+') must return a non-nil handle");
        assertNull(cap.args[1], "err must be nil on success");
        assertTrue((Boolean) cap.args[2], "handle must have bFileHandle == true");
        assertFalse((Boolean) cap.args[3], "handle must have bClosed == false");
    }

    @Test
    void openWithWPlusReturnsHandle() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local f, err = open('test.txt', 'w+')\n" + "_capture(f ~= nil, err, f.bFileHandle, f.bClosed)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "open('w+') must return a non-nil handle");
        assertNull(cap.args[1], "err must be nil on success");
        assertTrue((Boolean) cap.args[2], "handle must have bFileHandle == true");
        assertFalse((Boolean) cap.args[3], "handle must have bClosed == false");
    }

    @Test
    void openWithRPlusOnMissingFileReturnsNilAndError() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local f, err = open('missing.txt', 'r+')\n" + "_capture(f, err)");
        assertNotNull(cap.args);
        assertNull(cap.args[0], "open('r+') on missing file must return nil");
        assertNotNull(cap.args[1], "open('r+') on missing file must return an error string");
    }

    @Test
    void rPlusHandleCanWrite() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local f = open('test.txt', 'r+')\n" + "f:write('hello', ' ', 'world')\n"
                + "_capture(table.concat(_writes, ''))");
        assertNotNull(cap.args);
        assertEquals("hello world", cap.args[0]);
    }

    @Test
    void rPlusHandleWriteReturnsSelf() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local f = open('test.txt', 'r+')\n" + "local ret = f:write('x')\n" + "_capture(ret == f)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "write() must return self for chaining");
    }

    @Test
    void rPlusHandleCanReadLine() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local f = open('test.txt', 'r+')\n" + "local line = f:read('*l')\n" + "_capture(line)");
        assertNotNull(cap.args);
        // The mock readLine returns nil (EOF), so io read returns nil
        assertNull(cap.args[0], "read('*l') at EOF should return nil");
    }

    @Test
    void rPlusHandleCanSeek() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local f = open('test.txt', 'r+')\n" + "local pos = f:seek('set', 0)\n" + "_capture(pos)");
        assertNotNull(cap.args);
        assertNotNull(cap.args[0], "seek must return the new position");
    }

    @Test
    void wPlusHandleCanWrite() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local f = open('test.txt', 'w+')\n" + "f:write('a', 'b', 'c')\n" + "_capture(table.concat(_writes, ''))");
        assertNotNull(cap.args);
        assertEquals("abc", cap.args[0]);
    }

    @Test
    void openWithRPlusDoesNotThrow() {
        // pcall must succeed — open must not throw for r+, it should return a handle
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, result = pcall(open, 'test.txt', 'r+')\n" + "_capture(ok, result ~= nil)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "pcall must succeed — open must not throw for r+");
        assertTrue((Boolean) cap.args[1], "open('r+') must return a non-nil handle");
    }
}
