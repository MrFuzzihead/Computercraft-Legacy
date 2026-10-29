package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.filesystem.FileMount;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * Runs the in-game validation scripts from {@code docs/ingame-tests/batch-a/} through the real
 * BIOS, headlessly.
 *
 * <h2>Why this exists</h2>
 * <p>
 * Those scripts are the ones a human runs in a live client. Nothing else checks that they still
 * parse, that the helpers they call exist (both {@code fs.delete} and {@code fs.makeDir} are
 * 1.7.10-era, so a typo would only show up in game), or that their assertions still describe the
 * shipped behaviour. Running them here means a broken in-game test is a build failure rather than
 * a wasted manual pass.
 * </p>
 * <p>
 * The scripts print {@code RESULT BATCH-A-<n>} followed by {@code passed N failed M}, so this
 * test only has to boot a computer, run one script through the shell, and read the summary off
 * the terminal. Anything that needs a real key press is printed after the summary and is ignored.
 * </p>
 */
class BatchAIngameScriptsTest {

    private static final String SCRIPT_DIR = "docs/ingame-tests/batch-a";

    // -------------------------------------------------------------------------
    // Harness
    // -------------------------------------------------------------------------

    /**
     * Result of running one or more scripts against a save directory.
     *
     * <p>
     * The scripts append their summary to {@code /batch-a-results.txt}, because the manual
     * drills they print afterwards scroll a 19-row screen and can push the RESULT block out of
     * view. Reading the log is therefore the only reliable check.
     * </p>
     */
    private static final class Run {

        final String screen;
        final String log;

        Run(String screen, String log) {
            this.screen = screen;
            this.log = log;
        }

        /** Summarises every "passed N failed M" written so far, one entry per run. */
        List<String> summaries() {
            List<String> out = new java.util.ArrayList<>();
            for (String line : log.split("\n")) {
                if (line.contains("passed ") && line.contains("failed ")) out.add(line.trim());
            }
            return out;
        }

        void assertAllPassed(String script) {
            List<String> summaries = summaries();
            assertFalse(
                summaries.isEmpty(),
                script + " wrote no summary to /batch-a-results.txt. Screen was:\n" + screen + "\nLog:\n" + log);
            for (String summary : summaries) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("failed (\\d+)")
                    .matcher(summary);
                assertTrue(m.find(), "malformed summary \"" + summary + "\". Screen was:\n" + screen);
                assertEquals(
                    0,
                    Integer.parseInt(m.group(1)),
                    script + " reported failures (" + summary + "). Screen was:\n" + screen + "\nLog:\n" + log);
            }
        }
    }

    /**
     * Boots a computer whose save directory is {@code hdd}, copies {@code script} into it, and
     * runs it through the shell as {@code /startup}.
     */
    private static Run runScript(File hdd, String script) throws Exception {
        File rom = new File("src/main/resources/assets/computercraft/lua/rom");
        assertTrue(rom.isDirectory(), "the Lua ROM must be present at " + rom.getAbsolutePath());

        File source = new File(SCRIPT_DIR, script + ".lua");
        assertTrue(source.isFile(), "missing in-game test script: " + source.getAbsolutePath());
        Files.copy(source.toPath(), new File(hdd, script).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // Run it from /startup so it inherits the shell's environment (which is where
        // `shell` and `multishell` live -- os.run({}, ...) from a bare env would not see them).
        Files.write(
            new File(hdd, "startup").toPath(),
            ("shell.run(\"/" + script + "\")\n").getBytes(StandardCharsets.UTF_8));

        CobaltMachine machine = LuaTestMachine.create();
        Computer computer = computerOf(machine);

        Field fsField = Computer.class.getDeclaredField("m_fileSystem");
        fsField.setAccessible(true);
        FileSystem fs = new FileSystem("hdd", new FileMount(hdd, 8L * 1024 * 1024));
        fs.mount("rom", "rom", new FileMount(rom, 4L * 1024 * 1024));
        fsField.set(computer, fs);

        Field apisField = Computer.class.getDeclaredField("m_apis");
        apisField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ILuaAPI> apis = (List<ILuaAPI>) apisField.get(computer);
        for (ILuaAPI api : apis) {
            machine.addAPI(api);
            api.startup();
        }

        try (java.io.InputStream bios = BatchAIngameScriptsTest.class
            .getResourceAsStream("/assets/computercraft/lua/bios.lua")) {
            assertNotNull(bios, "bios.lua must be on the test classpath");
            machine.loadBios(bios);
        }

        // Plenty of room for the shell to start and the script to run to completion.
        for (int i = 0; i < 40 && !machine.isFinished(); i++) {
            machine.handleEvent("tick", new Object[0]);
        }

        String screen = readScreen(computer);
        machine.unload();

        File results = new File(hdd, "batch-a-results.txt");
        String log = results.isFile() ? new String(Files.readAllBytes(results.toPath()), StandardCharsets.UTF_8) : "";
        return new Run(screen, log);
    }

    private static Computer computerOf(CobaltMachine machine) throws Exception {
        Field f = CobaltMachine.class.getDeclaredField("computer");
        f.setAccessible(true);
        return (Computer) f.get(machine);
    }

    private static String readScreen(Computer computer) throws Exception {
        Field terminalField = Computer.class.getDeclaredField("m_terminal");
        terminalField.setAccessible(true);
        dan200.computercraft.core.terminal.Terminal terminal = (dan200.computercraft.core.terminal.Terminal) terminalField
            .get(computer);

        StringBuilder screen = new StringBuilder();
        for (int y = 0; y < terminal.getHeight(); y++) {
            screen.append(
                terminal.getLine(y)
                    .toString()
                    .replaceAll("\\s+$", ""))
                .append('\n');
        }
        return screen.toString();
    }

    private static File freshHdd() throws IOException {
        File hdd = Files.createTempDirectory("cc-ingame-batch-a")
            .toFile();
        hdd.deleteOnExit();
        return hdd;
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Each of these is single-phase: one boot, one RESULT block.
     *
     * <p>
     * {@code a3_multishell} is included because the terminal used by the test harness is a colour
     * one, so the BIOS runs multishell and its scripted assertions are live.
     * </p>
     */
    @Test
    void inGameScriptsAllPassHeadlessly() throws Exception {
        Map<String, String> results = new LinkedHashMap<>();

        for (String script : new String[] { "a3_multishell", "a4_print", "a6_strict_globals", "a8_json",
            "a9_fs_complete" }) {
            Run run = runScript(freshHdd(), script);
            results.put(script, run.log);
            run.assertAllPassed(script);
        }

        assertEquals(5, results.size());
    }

    /**
     * {@code a5_settings} is deliberately two-phase: it writes a throwaway key, saves, and asks for
     * a reboot. Running it twice against the same save directory simulates that reboot, including
     * the cleanup phase.
     */
    @Test
    void settingsScriptSurvivesAReboot() throws Exception {
        File hdd = freshHdd();

        Run first = runScript(hdd, "a5_settings");
        first.assertAllPassed("a5_settings (phase 1)");
        assertTrue(
            first.log.contains("BATCH-A-5 settings (phase 1)"),
            "the first run should identify itself as phase 1. Screen was:\n" + first.screen + "\nLog:\n" + first.log);
        assertTrue(new File(hdd, ".batch-a-settings-phase").isFile(), "phase 1 must leave its phase marker behind");

        Run second = runScript(hdd, "a5_settings");
        second.assertAllPassed("a5_settings (phase 2)");
        assertTrue(
            second.log.contains("BATCH-A-5 settings (phase 2)"),
            "the second run should take the phase-2 branch. Screen was:\n" + second.screen + "\nLog:\n" + second.log);
        assertTrue(!new File(hdd, ".batch-a-settings-phase").isFile(), "phase 2 must clean up its marker file");
    }
}
