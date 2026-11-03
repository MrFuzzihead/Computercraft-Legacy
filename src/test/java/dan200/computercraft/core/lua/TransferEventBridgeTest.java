package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dan200.computercraft.core.apis.transfer.TransferredFile;
import dan200.computercraft.core.apis.transfer.TransferredFiles;
import dan200.computercraft.core.lua.lib.cobalt.CobaltConverter;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * End-to-end test of the {@code file_transfer} event across the real Lua bridge.
 *
 * <p>
 * The unit tests pin the Java objects, but the thing that actually has to work is the hop through
 * Cobalt: a {@link TransferredFiles} instance placed in an event must arrive in Lua as a table whose
 * {@code getFiles} returns the files, and each file's {@code read} must produce a Lua string that
 * {@code fs.open(...):write} could accept. That conversion is the part a unit test cannot see, and it
 * is exactly what would break if the legacy bridge did not wrap {@code ILuaObject} event arguments.
 * </p>
 *
 * <p>
 * The program is a stub rather than the ROM: it yields in {@code os.pullEvent} and then inspects what
 * it was resumed with, which is the same path the BIOS uses.
 * </p>
 */
class TransferEventBridgeTest {

    private static final String PROGRAM = """
        local event, files = os.pullEvent()
        local list = files.getFiles()
        local file = list[1]
        local contents = file.read(8192)
        _capture(
            event,
            #list,
            file.getName(),
            type(contents),
            contents == "hello world",
            file.read(8192) == nil,
            pcall(function() return file.read("8192") end)
        )
        """;

    @Test
    void aTransferArrivesInLuaAsAnObjectWithReadableFiles() {
        AtomicInteger consumed = new AtomicInteger();

        byte[] contents = "hello world".getBytes(StandardCharsets.UTF_8);
        TransferredFile file = new TransferredFile("dropped.txt", contents);
        TransferredFiles files = new TransferredFiles(List.of(file), consumed::incrementAndGet);

        CobaltMachine machine = LuaTestMachine.create();
        Object[] captured = runWithTransfer(machine, files);

        assertEquals("file_transfer", captured[0], "the event name reaches Lua unchanged: " + captured[1]);
        assertEquals(1.0, ((Number) captured[1]).doubleValue(), "one file");
        assertEquals("dropped.txt", captured[2], "getName reaches Lua as a string");
        assertEquals("string", captured[3], "read(8192) must hand Lua a string, not a byte table");
        assertEquals(Boolean.TRUE, captured[4], "the contents must survive the bridge byte for byte");
        assertEquals(Boolean.TRUE, captured[5], "one byte past the end is nil");
        assertEquals(Boolean.FALSE, captured[6], "a non-integer count must raise, not be silently accepted");
        assertEquals(1, consumed.get(), "getFiles() tells the client the upload was taken");
    }

    @Test
    void readingPastTheEndGivesNilNotAnEmptyString() {
        TransferredFile file = new TransferredFile("a.txt", "ab".getBytes(StandardCharsets.UTF_8));
        TransferredFiles files = new TransferredFiles(List.of(file));

        CobaltMachine machine = LuaTestMachine.create();
        Object[] captured = runScript(machine, files, """
            local event, arg = os.pullEvent()
            local f = arg.getFiles()[1]
            _capture(f.read(2), f.read(1) == nil, f.readAll() == "")
            """);

        assertEquals("ab", captured[0], String.valueOf(captured[1]));
        assertEquals(Boolean.TRUE, captured[1], "one byte past the end is nil");
        assertEquals(Boolean.TRUE, captured[2], "readAll at the end is the empty string");
    }

    private static Object[] runWithTransfer(CobaltMachine machine, TransferredFiles files) {
        return runScript(machine, files, PROGRAM);
    }

    private static Object[] runScript(CobaltMachine machine, TransferredFiles files, String source) {
        Object[] captured = null;
        machine.loadBios(new ByteArrayInputStream(scriptWith(source).getBytes(StandardCharsets.UTF_8)));

        // Start the program; it runs until its os.pullEvent yields.
        machine.handleEvent(null, null);

        // Resume it with a real file_transfer event, exactly as ServerComputer would.
        machine.handleEvent(TransferredFiles.EVENT, new Object[] { files });

        captured = captureOf(machine);
        machine.unload();

        assertTrue(
            captured != null,
            "the Lua program must have reached _capture (finished=" + machine.isFinished() + ")");
        return captured;
    }

    private static String scriptWith(String body) {
        // No BIOS here: the source below *is* the BIOS, so os has to be created first. The body is
        // wrapped in pcall so that a failure inside it is reported as a captured message rather than
        // ending the thread silently.
        return """
            os = {}
            function os.pullEvent(...)
                return coroutine.yield(...)
            end
            _result = nil
            _capture = function(...) _result = { ... } end
            local _ok, _err = pcall(function()
            """ + body + """
            end)
            if not _ok then _capture("LUA ERROR", tostring(_err)) end
            """;
    }

    private static Object[] captureOf(CobaltMachine machine) {
        try {
            java.lang.reflect.Field field = CobaltMachine.class.getDeclaredField("globals");
            field.setAccessible(true);
            org.squiddev.cobalt.LuaTable globals = (org.squiddev.cobalt.LuaTable) field.get(machine);
            org.squiddev.cobalt.LuaValue table = globals.rawget("_result");
            if (table == null || !(table instanceof org.squiddev.cobalt.LuaTable)) {
                return null;
            }

            org.squiddev.cobalt.LuaTable luaTable = (org.squiddev.cobalt.LuaTable) table;
            int count = luaTable.length();
            Object[] values = new Object[count];
            for (int i = 0; i < count; i++) {
                values[i] = CobaltConverter.toObject(luaTable.rawget(i + 1), false);
            }

            return values;
        } catch (ReflectiveOperationException | org.squiddev.cobalt.LuaError e) {
            throw new AssertionError("could not read the captured values", e);
        }
    }
}
