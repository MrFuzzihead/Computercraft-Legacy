package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Tests for the ported file-import ROM code, driven by a fabricated {@code file_transfer} event.
 *
 * <p>
 * The transfer feature has three layers: the client hook that receives a drop (needs a window, cannot
 * be tested here), the network and assembly code (unit-tested), and this - the Lua that actually writes
 * the files. The last layer is the one a player sees, and it can be driven completely headlessly: the
 * event is just a table with {@code getFiles()} on it, so the real {@code cc/internal/import.lua} and
 * the real {@code rom/programs/import} are loaded from the jar and run against a stubbed {@code fs}.
 * </p>
 *
 * <p>
 * The stub records what would have been written to disk. That makes the awkward paths checkable: the
 * overwrite prompt, a short write (out of disk space), and {@code q} on the program's event loop.
 * </p>
 */
class ImportProgramTest {

    private static final String MODULE = "rom/modules/main/cc/internal/import.lua";
    private static final String PROGRAM = "rom/programs/import";

    /** Stubs shared by the module and the program: an in-memory filesystem and a silent terminal. */
    private static String prelude() throws IOException {
        String module = LuaRomTestSupport.readResource("/assets/computercraft/lua/" + MODULE);
        return "_importSrc = [====[\n" + module + "\n]====]\n" + """
            -- In-memory filesystem. `written` is inspected by the tests; `failOn` names a path whose
            -- write raises, standing in for a full disk.
            written = {}
            failOn = nil
            existing = {}
            fs = {
                exists = function(path) return existing[path] ~= nil or written[path] ~= nil end,
                isDir = function(path) return existing[path] == "dir" end,
                open = function(path, mode)
                    if mode ~= "wb" then error("unexpected mode " .. tostring(mode), 0) end
                    return {
                        -- Called as handle.write(chunk), so the data is the first argument;
                        -- real CC handles bind themselves and take the contents as argument one.
                        write = function(first, second)
                            local data = second
                            if data == nil then data = first end
                            if path == failOn then error("disk full", 0) end
                            if type(data) ~= "string" then error("non-string chunk: " .. type(data), 0) end
                            written[path] = (written[path] or "") .. data
                            return self
                        end,
                        close = function() end,
                    }
                end,
            }
            shell = { resolve = function(path) return path end, dir = function() return "" end }
            colours = { cyan = 1 }
            textutils = { pagedTabulate = function() end }
            print = function(...) end
            printError = function(...) _lastError = ... end
            io = { write = function() end }
            answer = "yes"
            read = function() return answer end
            keys = { q = 113 }

            -- `require` as the ROM provides it, for the two names these files use. The module needs
            -- it at load time, so it has to exist before the module is loaded.
            local _importModule
            require = function(name)
                if name == "cc.completion" then return { choice = function() return {} end } end
                if name == "cc.internal.import" then
                    if _importModule == nil then
                        _importModule = assert(loadstring(_importSrc, '@cc/internal/import.lua'))()
                    end
                    return _importModule
                end
                error("unexpected require: " .. tostring(name), 0)
            end
            _importModule = require("cc.internal.import")
            """;
    }

    /**
     * Builds a fake {@code files} list: each entry is a table with {@code getName} and a {@code read}
     * that hands out the contents in chunks, like the real transfer handle.
     */
    private static String fakeFiles(String... namesAndContents) {
        StringBuilder builder = new StringBuilder("local _files = {");
        for (int i = 0; i + 1 < namesAndContents.length; i += 2) {
            String name = namesAndContents[i];
            String contents = namesAndContents[i + 1];
            builder.append(
                String.format(
                    "{ getName = function() return %s end, read = (function()" + " local _left = %s"
                        + " return function(self, n)"
                        + "   if _left == nil or #_left == 0 then return nil end"
                        + "   local chunk = string.sub(_left, 1, n or #_left)"
                        + "   _left = string.sub(_left, #chunk + 1)"
                        + "   return chunk"
                        + " end end)() },",
                    quote(name),
                    quote(contents)));
        }

        return builder.append("}\n")
            .toString();
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r") + "\"";
    }

    /** Runs the module against the given files and reports what it returned and what it wrote. */
    private static LuaRomTestSupport.Capture runModule(String setup) throws IOException {
        LuaRomTestSupport.Capture capture = new LuaRomTestSupport.Capture();
        var machine = LuaRomTestSupport.machine(capture);
        LuaRomTestSupport.run(machine, "local _ok, _err = pcall(function()\n" + prelude() + setup + """
                local ok, err = _importModule(_files)
                _capture(ok, err, written["a.txt"], written["b.txt"])
            end)
            if not _ok then _capture("LUA ERROR: " .. tostring(_err), nil, nil, nil) end
            """);
        return capture;
    }

    @Test
    void writesTheTransferredFile() throws IOException {
        LuaRomTestSupport.Capture capture = runModule(fakeFiles("a.txt", "hello world"));

        assertEquals("LUA ERROR", "LUA ERROR", "the run itself must not raise");
        assertEquals(Boolean.TRUE, capture.at(0), String.valueOf(capture.at(1)));
        assertEquals("hello world", capture.string(2));
    }

    @Test
    void copiesAFileLargerThanOneChunk() throws IOException {
        StringBuilder contents = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            contents.append((char) ('a' + (i % 26)));
        }

        LuaRomTestSupport.Capture capture = runModule(fakeFiles("a.txt", contents.toString()));

        assertEquals(Boolean.TRUE, capture.at(0), String.valueOf(capture.at(1)));
        assertEquals(contents.toString(), capture.string(2), "the whole file must arrive, not just the first chunk");
    }

    @Test
    void writesEachFileSeparately() throws IOException {
        LuaRomTestSupport.Capture capture = runModule(fakeFiles("a.txt", "one", "b.txt", "two"));

        assertEquals(Boolean.TRUE, capture.at(0), String.valueOf(capture.at(1)));
        assertEquals("one", capture.string(2));
        assertEquals("two", capture.string(3));
    }

    @Test
    void asksBeforeOverwritingAndHonoursNo() throws IOException {
        LuaRomTestSupport.Capture capture = runModule(
            fakeFiles("a.txt", "new contents") + "existing[\"a.txt\"] = \"file\"\nanswer = \"no\"\n");

        assertEquals(null, capture.at(1), "declining is not an error");
        assertEquals(
            null,
            capture.at(2),
            "a declined overwrite must not write anything, so the existing file is left alone");
    }

    @Test
    void overwritesWhenThePlayerSaysYes() throws IOException {
        LuaRomTestSupport.Capture capture = runModule(
            fakeFiles("a.txt", "new contents") + "existing[\"a.txt\"] = \"file\"\nanswer = \"yes\"\n");

        assertEquals(Boolean.TRUE, capture.at(0), String.valueOf(capture.at(1)));
        assertEquals("new contents", capture.string(2));
    }

    @Test
    void refusesToOverwriteADirectory() throws IOException {
        LuaRomTestSupport.Capture capture = runModule(
            fakeFiles("a.txt", "contents") + "existing[\"a.txt\"] = \"dir\"\n");

        assertEquals(null, capture.at(0), "the module returns nil on failure");
        assertTrue(
            String.valueOf(capture.at(1))
                .contains("already a directory"),
            String.valueOf(capture.at(1)));
    }

    @Test
    void reportsAFailedWrite() throws IOException {
        LuaRomTestSupport.Capture capture = runModule(fakeFiles("a.txt", "contents") + "failOn = \"a.txt\"\n");

        assertEquals(null, capture.at(0));
        assertTrue(
            String.valueOf(capture.at(1))
                .contains("Failed to write file"),
            String.valueOf(capture.at(1)));
    }

    // --- the program, which waits for the event -------------------------------------------------

    /** Runs {@code rom/programs/import} with a scripted event queue. */
    private static LuaRomTestSupport.Capture runProgram(String setup) throws IOException {
        String program = LuaRomTestSupport.readResource("/assets/computercraft/lua/" + PROGRAM);

        LuaRomTestSupport.Capture capture = new LuaRomTestSupport.Capture();
        var machine = LuaRomTestSupport.machine(capture);
        LuaRomTestSupport.run(
            machine,
            "local _ok, _err = pcall(function()\n" + prelude() + "_programSrc = [====[\n" + program + "\n]====]\n" + """
                -- os.pullEvent hands out the scripted queue, then the program's loop is over.
                local _events = {}
                os = { pullEvent = function()
                    local event = table.remove(_events, 1)
                    if event == nil then error("the program kept waiting for events", 0) end
                    return event[1], event[2]
                end }
                """ + setup + """
                    assert(loadstring(_programSrc, '@rom/programs/import'))()
                    _capture("done", written["a.txt"])
                end)
                if not _ok then _capture("LUA ERROR: " .. tostring(_err), nil) end
                """);
        return capture;
    }

    @Test
    void theProgramWritesTheFilesFromTheEvent() throws IOException {
        LuaRomTestSupport.Capture capture = runProgram(fakeFiles("a.txt", "dropped") + """
            _events = {
                { "file_transfer", { getFiles = function() return _files end } },
            }
            """);

        assertEquals("done", capture.string(0), "the program must finish after one transfer");
        assertEquals("dropped", capture.string(1));
    }

    @Test
    void theProgramGivesUpOnQ() throws IOException {
        LuaRomTestSupport.Capture capture = runProgram("""
            _events = {
                { "key", 113 },
            }
            """);

        assertEquals("done", capture.string(0));
        assertEquals(null, capture.at(1), "q must leave without writing anything");
    }

    @Test
    void theProgramIgnoresUnrelatedEvents() throws IOException {
        LuaRomTestSupport.Capture capture = runProgram(fakeFiles("a.txt", "later") + """
            _events = {
                { "key", 30 },
                { "mouse_click", 1 },
                { "file_transfer", { getFiles = function() return _files end } },
            }
            """);

        assertEquals("done", capture.string(0));
        assertEquals("later", capture.string(1), "the transfer must still be handled after other events");
    }
}
