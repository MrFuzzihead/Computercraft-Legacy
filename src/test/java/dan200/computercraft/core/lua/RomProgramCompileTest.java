package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Compiles the ROM programs and modules touched by file transfer.
 *
 * <p>
 * {@code rom/programs/shell} had to be restructured to handle a transfer while its prompt is open:
 * the prompt now runs {@code read()} in a coroutine and pulls events itself. That loop is the only
 * piece of this feature that runs on every single boot of every computer, and it cannot be executed
 * headlessly without a terminal and a real filesystem - so it is at least pinned for syntax. A
 * typo here would otherwise only show up as a computer that boots to a blank screen.
 * </p>
 */
class RomProgramCompileTest {

    private static String source(String path) throws IOException {
        return LuaRomTestSupport.readResource("/assets/computercraft/lua/" + path);
    }

    private static void assertCompiles(String path, String contents) {
        LuaRomTestSupport.Capture capture = new LuaRomTestSupport.Capture();
        var machine = LuaRomTestSupport.machine(capture);
        LuaRomTestSupport.run(
            machine,
            "local _f, _err = loadstring([====[\n" + contents
                + "\n]====], '@"
                + path
                + "')\n_capture(_f ~= nil, tostring(_err))\n");

        assertEquals(Boolean.TRUE, capture.at(0), path + " must compile: " + capture.at(1));
    }

    @Test
    void theShellProgramCompiles() throws IOException {
        assertCompiles("rom/programs/shell", source("rom/programs/shell"));
    }

    @Test
    void theImportProgramCompiles() throws IOException {
        assertCompiles("rom/programs/import", source("rom/programs/import"));
    }

    @Test
    void theImportModuleCompiles() throws IOException {
        String path = "rom/modules/main/cc/internal/import.lua";
        assertCompiles(path, source(path));
    }

    /**
     * The restructured shell has to keep handling a transfer while its prompt is open, and it does so
     * by re-dispatching events into a coroutine-wrapped {@code read}. This pins that the pieces that
     * make that work are actually present, so a later tidy-up cannot quietly drop them.
     */
    @Test
    void theShellStillRunsPromptInACoroutineAndHandlesTransfers() throws IOException {
        String shell = source("rom/programs/shell");

        assertTrue(
            shell.contains("coroutine.create( read )"),
            "the prompt must be run in a coroutine, or read() cannot be resumed with a transfer");
        assertTrue(shell.contains("event[1] == \"file_transfer\""), "the prompt must handle file_transfer");
        assertTrue(
            shell.contains("require( \"cc.internal.import\" )"),
            "the prompt must hand the files to the import module");
        assertTrue(
            shell.contains("term.setCursorBlink( true )"),
            "the prompt's cursor has to be restored after an import");
    }
}
