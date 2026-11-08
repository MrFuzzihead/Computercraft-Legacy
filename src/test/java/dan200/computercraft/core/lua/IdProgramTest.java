package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests {@code rom/programs/id}, in particular the audio-disc and non-disk branches that this fork
 * was missing: an audio disc answered "No disk in drive" and a data source with no floppy ID
 * printed "The disk is #nil".
 */
class IdProgramTest {

    /** {@code audioTitle} and {@code diskId} are Lua expressions, so a test can pass {@code nil}. */
    private static String prelude(String drive, boolean hasAudio, String audioTitle, boolean hasData, String diskId,
        String label) {
        return """
            table = table or {}
            if not table.unpack then table.unpack = unpack end

            _printed = {}
            function print(m) _printed[#_printed + 1] = tostring(m) end
            arg = %s

            os = {
              getComputerID = function() return 7 end,
              getComputerLabel = function() return "workbench" end,
            }

            disk = {
              hasAudio = function(s) return %s end,
              getAudioTitle = function(s) return %s end,
              hasData = function(s) return %s end,
              getID = function(s) return %s end,
              getLabel = function(s) return %s end,
            }
            """.formatted(drive == null ? "{}" : "{ \"" + drive + "\" }", hasAudio, audioTitle, hasData, diskId, label);
    }

    private static String lines(String prelude) throws Exception {
        LuaRomTestSupport.Capture cap = LuaRomTestSupport
            .runProgram("rom/programs/id", prelude, "_capture(table.concat(_printed, '\\n'))");
        return cap.string(0);
    }

    @Test
    void reportsTheComputerAndItsLabel() throws Exception {
        assertEquals(
            "This is computer #7\nThis computer is labelled \"workbench\"",
            lines(prelude(null, false, "nil", false, "nil", "nil")));
    }

    @Test
    void reportsAPlainDisk() throws Exception {
        assertEquals("The disk is #123", lines(prelude("left", false, "nil", true, "123", "nil")));
    }

    @Test
    void reportsADiskLabel() throws Exception {
        assertEquals(
            "The disk is #123\nLabelled \"spare\"",
            lines(prelude("left", false, "nil", true, "123", "\"spare\"")));
    }

    @Test
    void reportsATitledAudioDisc() throws Exception {
        // An audio disc is not a data source: it must not be reported as "No disk in drive".
        assertEquals("Has audio track \"13\"", lines(prelude("left", true, "\"13\"", false, "nil", "nil")));
    }

    @Test
    void reportsAnUntitledAudioDisc() throws Exception {
        assertEquals("Has untitled audio", lines(prelude("left", true, "nil", false, "nil", "nil")));
    }

    @Test
    void reportsAnEmptyDrive() throws Exception {
        assertEquals("No disk in drive left", lines(prelude("left", false, "nil", false, "nil", "nil")));
    }

    @Test
    void reportsADataSourceWithNoFloppyId() throws Exception {
        // An addon mount has data but no ComputerCraft floppy ID; the old code printed "#nil".
        assertEquals("Non-disk data source", lines(prelude("left", false, "nil", true, "nil", "nil")));
    }
}
