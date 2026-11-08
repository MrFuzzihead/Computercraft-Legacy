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
            shell.contains("tImportEnv.require( \"cc.internal.import\" )"),
            "the prompt must load the import module through a shell environment (with `shell` in "
                + "scope); the BIOS-level require runs modules against the global env, where "
                + "shell.resolve is a nil index");
        assertTrue(
            shell.contains("term.setCursorBlink( true )"),
            "the prompt's cursor has to be restored after an import");
    }

    // -------------------------------------------------------------------------
    // Every ROM program and module
    // -------------------------------------------------------------------------

    /**
     * Compiles every Lua file the ROM ships.
     *
     * <p>
     * A syntax error in a program only shows up in game as a computer that boots to a red
     * traceback when that program is run — which nobody does for {@code paint} or {@code id} in a
     * test suite. This walks the ROM and compiles each program and module, so a broken edit fails
     * the build instead. Data files (the {@code adventure} levels) are skipped: they are not Lua.
     * </p>
     */
    @Test
    void everyRomProgramAndModuleCompiles() throws IOException {
        java.io.File root = new java.io.File("src/main/resources/assets/computercraft/lua");
        assertTrue(root.isDirectory(), "the Lua ROM must be present at " + root.getAbsolutePath());

        java.util.List<String> failures = new java.util.ArrayList<>();
        int compiled = 0;
        for (java.io.File file : luaFiles(root)) {
            String path = root.toPath()
                .relativize(file.toPath())
                .toString()
                .replace('\\', '/');
            try {
                assertCompiles(path, source(path));
                compiled++;
            } catch (AssertionError e) {
                failures.add(e.getMessage());
            }
        }

        assertTrue(compiled > 50, "expected the ROM to contain programs and modules, compiled " + compiled);
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    /** Lua source files in the ROM, by the conventions the loader uses. */
    private static java.util.List<java.io.File> luaFiles(java.io.File root) {
        java.util.List<java.io.File> files = new java.util.ArrayList<>();
        java.io.File[] entries = root.listFiles();
        if (entries == null) return files;
        java.util.Arrays.sort(entries);
        for (java.io.File entry : entries) {
            if (entry.isDirectory()) {
                // rom/programs/fun/advanced/levels holds adventure save files, not Lua, and
                // rom/help holds help topics (prose, extensionless) rather than programs.
                if (entry.getName()
                    .equals("levels")
                    || entry.getName()
                        .equals("help"))
                    continue;
                files.addAll(luaFiles(entry));
            } else if (entry.getName()
                .endsWith(".lua")) {
                    files.add(entry);
                } else if (entry.getName()
                    .indexOf('.') < 0) {
                        // ROM programs and APIs are shipped without an extension.
                        files.add(entry);
                    }
        }
        return files;
    }
}
