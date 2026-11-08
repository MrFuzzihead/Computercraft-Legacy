package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests {@code rom/programs/rename}'s argument and state checks.
 *
 * <p>
 * The guards exist because {@code fs.move} refuses the same renames in Java, but reports them by
 * throwing, which the shell shows as a traceback. Renaming a mount root in particular has no Java
 * guard at all — {@code move} is copy-then-delete, so it would try to delete the drive's storage —
 * and that is checked here.
 * </p>
 */
class RenameProgramTest {

    /** Records {@code print}/{@code printError} output and what {@code fs.move} was called with. */
    private static String prelude(boolean sourceExists, boolean sourceIsRoot, boolean sourceReadOnly,
        boolean destExists, boolean destReadOnly) {
        return """
            table = table or {}
            if not table.unpack then table.unpack = unpack end

            _printed, _errors, _moved = {}, {}, {}

            function print(msg) _printed[#_printed + 1] = tostring(msg) end
            function printError(msg) _errors[#_errors + 1] = tostring(msg) end

            arg = { [0] = "rename", "a", "b" }

            shell = {
              resolve = function(p) return p end,
              getRunningProgram = function() return "rom/programs/rename" end,
            }

            fs = {
              exists = function(p) return p == "a" and %s or p == "b" and %s end,
              isDriveRoot = function(p) return %s end,
              isReadOnly = function(p) return (%s and p == "a") or (%s and p == "b") end,
              move = function(s, d) _moved[#_moved + 1] = s .. "->" .. d end,
              getName = function(p) return p:match("[^/]+$") or p end,
            }
            """.formatted(sourceExists, destExists, sourceIsRoot, sourceReadOnly, destReadOnly);
    }

    private static LuaRomTestSupport.Capture run(String prelude, String body) throws Exception {
        return LuaRomTestSupport.runProgram("rom/programs/rename", prelude, body);
    }

    private static String firstError(boolean src, boolean root, boolean srcRo, boolean dst, boolean dstRo)
        throws Exception {
        LuaRomTestSupport.Capture cap = run(prelude(src, root, srcRo, dst, dstRo), "_capture(_errors[1], #_moved)");
        return cap.string(0);
    }

    @Test
    void missingSourceIsReported() throws Exception {
        assertEquals("No matching files", firstError(false, false, false, false, false));
    }

    @Test
    void aMountRootCannotBeRenamed() throws Exception {
        // No Java guard backs this one: move() would copy the mount and then delete its storage.
        assertEquals("Can't rename mounts", firstError(true, true, false, false, false));
    }

    @Test
    void aReadOnlySourceIsRefused() throws Exception {
        assertEquals("Source is read-only", firstError(true, false, true, false, false));
    }

    @Test
    void anExistingDestinationIsRefusedRatherThanOverwritten() throws Exception {
        assertEquals("Destination exists", firstError(true, false, false, true, false));
    }

    @Test
    void aReadOnlyDestinationIsRefused() throws Exception {
        assertEquals("Destination is read-only", firstError(true, false, false, false, true));
    }

    @Test
    void aValidRenameMovesTheFile() throws Exception {
        LuaRomTestSupport.Capture cap = run(prelude(true, false, false, false, false), "_capture(#_errors, _moved[1])");
        assertEquals(0.0, cap.number(0), "a valid rename must not print an error");
        assertEquals("a->b", cap.string(1));
    }

    @Test
    void usageNamesTheProgramAsTyped() throws Exception {
        // arg[0] is the command (the alias included) after the shell's arg[0] fix, and it is what
        // prints here rather than the resolved "rom/programs/rename".
        LuaRomTestSupport.Capture cap = LuaRomTestSupport.runProgram(
            "rom/programs/rename",
            """
                table = table or {}
                if not table.unpack then table.unpack = unpack end
                _printed = {}
                function print(m) _printed[#_printed + 1] = tostring(m) end
                function printError(m) _errors = ( _errors or {} ); _errors[#_errors + 1] = tostring(m) end
                arg = { [0] = "ren" }
                shell = { resolve = function(p) return p end, getRunningProgram = function() return "rom/programs/rename" end }
                fs = { getName = function(p) return p:match("[^/]+$") or p end }
                """,
            "_capture(_printed[1])");
        assertEquals("Usage: ren <source> <destination>", cap.string(0));
    }
}
