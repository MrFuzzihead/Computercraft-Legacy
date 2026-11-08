package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

/**
 * Boots the real BIOS to check the parts of the boot sequence that only exist there:
 * {@code bios.use_multishell}'s choice of shell, and the Lua REPL's
 * {@code lua.warn_against_use_of_local} warning.
 *
 * <p>
 * Both settings were defined and never read. Neither is reachable from a single program in
 * isolation — the first picks which program the BIOS runs, the second fires on REPL input — so
 * they are driven here with a real computer and simulated keystrokes.
 * </p>
 */
class BiosShellSelectionTest {

    /** A startup program that records whether a multishell API is in scope. */
    private static final String PROBE_STARTUP = "local f = fs.open('/multishell.txt', 'w')\n"
        + "f.write(tostring(multishell))\n"
        + "f.close()\n";

    private static void seed(File dir, String name, String contents) {
        try {
            Files.write(new File(dir, name).toPath(), contents.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String readFile(File dir, String name) throws Exception {
        File f = new File(dir, name);
        assertTrue(f.isFile(), name + " should exist");
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // bios.use_multishell
    // -------------------------------------------------------------------------

    @Test
    void multishellRunsByDefault() throws Exception {
        BiosInputHarness h = BiosInputHarness.boot(dir -> seed(dir, "startup", PROBE_STARTUP));
        try {
            h.pump(20);
            assertTrue(
                readFile(h.hdd, "multishell.txt").startsWith("table"),
                "a colour computer should start under multishell by default. Screen:\n" + h.screen());
        } finally {
            h.close();
        }
    }

    @Test
    void multishellCanBeTurnedOff() throws Exception {
        BiosInputHarness h = BiosInputHarness.boot(dir -> {
            seed(dir, "startup", PROBE_STARTUP);
            seed(dir, ".settings", "{ [\"bios.use_multishell\"] = false }\n");
        });
        try {
            h.pump(20);
            assertTrue(
                readFile(h.hdd, "multishell.txt").equals("nil"),
                "bios.use_multishell = false must run the plain shell instead. Screen:\n" + h.screen());
        } finally {
            h.close();
        }
    }

    @Test
    void thePlainShellIsStillUsable() throws Exception {
        // The point of the setting is a usable single-tab shell, not merely a missing API.
        BiosInputHarness h = BiosInputHarness
            .boot(dir -> seed(dir, ".settings", "{ [\"bios.use_multishell\"] = false }\n"));
        try {
            h.pump(10);
            h.run("time");
            String screen = h.screen();
            assertFalse(screen.contains("No such program"), "the plain shell should run programs. Screen:\n" + screen);
            assertTrue(screen.contains("time is"), "the program's output should appear. Screen:\n" + screen);
        } finally {
            h.close();
        }
    }

    // -------------------------------------------------------------------------
    // lua.warn_against_use_of_local
    // -------------------------------------------------------------------------

    private static final String WARNING = "remove the local keyword";

    /** The warning wraps on a 51-column screen, so compare against a single-line screen. */
    private static String flat(BiosInputHarness harness) {
        return harness.screen()
            .replace('\n', ' ')
            .replaceAll("\\s+", " ");
    }

    @Test
    void theReplWarnsAboutLocalByDefault() throws Exception {
        BiosInputHarness h = BiosInputHarness.boot();
        try {
            h.pump(5);
            h.run("lua");
            h.pump(5);
            h.type("local x = 1");
            h.press(BiosInputHarness.KEY_ENTER);
            h.pump(10);

            assertTrue(
                flat(h).contains(WARNING),
                "the REPL should warn that a local is lost at the next prompt. Screen:\n" + h.screen());
        } finally {
            h.close();
        }
    }

    @Test
    void theWarningCanBeTurnedOff() throws Exception {
        BiosInputHarness h = BiosInputHarness
            .boot(dir -> seed(dir, ".settings", "{ [\"lua.warn_against_use_of_local\"] = false }\n"));
        try {
            h.pump(5);
            h.run("lua");
            h.pump(5);
            h.type("local x = 1");
            h.press(BiosInputHarness.KEY_ENTER);
            h.pump(10);

            assertFalse(
                flat(h).contains(WARNING),
                "lua.warn_against_use_of_local = false must suppress the warning. Screen:\n" + h.screen());
        } finally {
            h.close();
        }
    }

    @Test
    void anOrdinaryStatementIsNotWarnedAbout() throws Exception {
        BiosInputHarness h = BiosInputHarness.boot();
        try {
            h.pump(5);
            h.run("lua");
            h.pump(5);
            h.type("x = 1");
            h.press(BiosInputHarness.KEY_ENTER);
            h.pump(10);

            assertFalse(
                flat(h).contains(WARNING),
                "a plain assignment must not trigger the warning. Screen:\n" + h.screen());
        } finally {
            h.close();
        }
    }

    // -------------------------------------------------------------------------
    // Shell tab titles
    // -------------------------------------------------------------------------

    @Test
    void aTabTitleDropsTheLuaExtension() throws Exception {
        // `edit startup` saves startup.lua, so running it must not label the tab "startup.lua".
        // The tab bar only appears once a second tab exists, and the title is only visible while
        // the program is still running, so the program has to block.
        BiosInputHarness h = BiosInputHarness.boot(dir -> seed(dir, "hello.lua", "sleep(30)\n"));
        try {
            h.pump(5);
            h.run("bg hello.lua");
            h.pump(20);

            String tabs = h.line(1);
            assertTrue(tabs.contains("hello"), "the tab bar should show the program name. Row 1 was: '" + tabs + "'");
            assertFalse(
                tabs.contains("hello.lua"),
                "the tab title should not keep the .lua extension. Row 1 was: '" + tabs + "'");
        } finally {
            h.close();
        }
    }
}
