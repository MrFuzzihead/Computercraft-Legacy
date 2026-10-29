package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;

import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.filesystem.FileMount;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * Drives simulated keyboard and mouse input through the real BIOS and multishell.
 *
 * <h2>Why this exists</h2>
 * <p>
 * The Batch A in-game scripts open a second multishell tab and ask a human to type into it. A
 * superficially correct script can still leave behind a tab running a program that never reads
 * input — typing then does nothing and the only way out is Ctrl+T, which looks exactly like a
 * broken computer. That happened during review, so the "the tab is interactive" claim is now
 * checked here rather than assumed.
 * </p>
 * <p>
 * It also pins the tab geometry: with two tabs, the labels occupy row 1 and each label is
 * {@code <title>} followed by one space, so a click at x=9 selects the second tab. If that layout
 * ever changes, these tests fail instead of quietly sending input to the wrong process.
 * </p>
 */
class BiosMultishellInputTest {

    // -------------------------------------------------------------------------
    // Harness
    // -------------------------------------------------------------------------

    private static final class Machine {

        final CobaltMachine lua;
        final Computer computer;

        Machine(CobaltMachine lua, Computer computer) {
            this.lua = lua;
            this.computer = computer;
        }

        /** Queues one {@code char} event per character, as the GUI does. */
        void type(String text) {
            for (char ch : text.toCharArray()) {
                lua.handleEvent("char", new Object[] { String.valueOf(ch) });
            }
        }

        void pressEnter() {
            lua.handleEvent("key", new Object[] { 28 }); // keys.enter
        }

        /** Queues {@code count} idle events so suspended coroutines can make progress. */
        void pump(int count) {
            for (int i = 0; i < count && !lua.isFinished(); i++) {
                lua.handleEvent("tick", new Object[0]);
            }
        }

        String screen() throws Exception {
            Field tf = Computer.class.getDeclaredField("m_terminal");
            tf.setAccessible(true);
            dan200.computercraft.core.terminal.Terminal t = (dan200.computercraft.core.terminal.Terminal) tf
                .get(computer);
            StringBuilder sb = new StringBuilder();
            for (int y = 0; y < t.getHeight(); y++) {
                sb.append(
                    t.getLine(y)
                        .toString()
                        .replaceAll("\\s+$", ""))
                    .append('\n');
            }
            return sb.toString();
        }
    }

    private static Machine boot() throws Exception {
        File rom = new File("src/main/resources/assets/computercraft/lua/rom");
        assertTrue(rom.isDirectory(), "the Lua ROM must be present at " + rom.getAbsolutePath());

        File hdd = Files.createTempDirectory("cc-multishell-input")
            .toFile();
        hdd.deleteOnExit();

        CobaltMachine machine = LuaTestMachine.create();
        Computer computer = computerOf(machine);

        Field fsField = Computer.class.getDeclaredField("m_fileSystem");
        fsField.setAccessible(true);
        FileSystem fs = new FileSystem("hdd", new FileMount(hdd, 4L * 1024 * 1024));
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

        try (java.io.InputStream bios = BiosMultishellInputTest.class
            .getResourceAsStream("/assets/computercraft/lua/bios.lua")) {
            assertNotNull(bios, "bios.lua must be on the test classpath");
            machine.loadBios(bios);
        }
        return new Machine(machine, computer);
    }

    private static Computer computerOf(CobaltMachine machine) throws Exception {
        Field f = CobaltMachine.class.getDeclaredField("computer");
        f.setAccessible(true);
        return (Computer) f.get(machine);
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void aSecondShellTabAcceptsTypedCommands() throws Exception {
        Machine m = boot();
        m.pump(5);

        // Open a real shell tab from tab 1.
        m.type("bg shell");
        m.pressEnter();
        m.pump(15);

        String withMenu = m.screen();
        assertTrue(withMenu.contains("shell"), "the tab menu should be visible. Screen:\n" + withMenu);
        // Two labels of "shell" plus one space each: tab 1 spans x=1..7, tab 2 x=8..14.
        assertTrue(
            withMenu.lines()
                .findFirst()
                .orElse("")
                .trim()
                .equals("shell  shell"),
            "expected two tab labels on row 1. Screen:\n" + withMenu);

        // Focus the second tab, then type a command that produces output.
        m.lua.handleEvent("mouse_click", new Object[] { 1, 9, 1 });
        m.pump(5);
        m.type("time");
        m.pressEnter();
        m.pump(15);

        String afterTyping = m.screen();
        assertTrue(
            afterTyping.contains("time"),
            "the second tab should echo the typed command. Screen:\n" + afterTyping);
        assertTrue(
            afterTyping.toLowerCase()
                .contains("day") || afterTyping.contains(":"),
            "the second tab should have run the command and printed its output. Screen:\n" + afterTyping);
        assertTrue(
            !afterTyping.contains("No such program"),
            "the typed command was not executed. Screen:\n" + afterTyping);

        m.lua.unload();
    }

    @Test
    void theFirstTabKeepsWorkingAfterTheMenuAppears() throws Exception {
        Machine m = boot();
        m.pump(5);

        m.type("bg shell");
        m.pressEnter();
        m.pump(15);

        // Still on tab 1, which resized when the menu appeared. Typing must still work.
        m.type("time");
        m.pressEnter();
        m.pump(15);

        String screen = m.screen();
        assertTrue(
            screen.contains("time"),
            "tab 1 should still echo input after the tab menu appears. Screen:\n" + screen);
        assertTrue(!screen.contains("No such program"), "the command was not executed in tab 1. Screen:\n" + screen);

        m.lua.unload();
    }
}
