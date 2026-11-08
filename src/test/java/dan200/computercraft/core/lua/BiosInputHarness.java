package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;

import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.filesystem.FileMount;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;
import dan200.computercraft.core.terminal.Terminal;

/**
 * Boots a computer on the real BIOS and ROM, and lets a test drive it like a player would: type,
 * press keys, click, and read the screen.
 *
 * <p>
 * Tests that only load a single program with mocked globals cannot show that a key reaches it
 * through the shell, that the shell's tab machinery is wired up, or that the editor's footer
 * actually opens on a click. Those were exactly the gaps this harness was written for, so the
 * programs are exercised end to end against the shipped ROM.
 * </p>
 */
final class BiosInputHarness {

    /** Minecraft/LWJGL2 key codes, matching rom/apis/keys. */
    static final int KEY_BACKSPACE = 14, KEY_TAB = 15, KEY_ENTER = 28, KEY_LEFT_CTRL = 29, KEY_DELETE = 211,
        KEY_NUM_PAD_ENTER = 156, KEY_LEFT = 203, KEY_RIGHT = 205, KEY_UP = 200, KEY_DOWN = 208;

    final CobaltMachine lua;
    final Computer computer;
    final File hdd;

    private BiosInputHarness(CobaltMachine lua, Computer computer, File hdd) {
        this.lua = lua;
        this.computer = computer;
        this.hdd = hdd;
    }

    /** Boots a computer, letting {@code seed} write into its save directory first. */
    static BiosInputHarness boot(Consumer<File> seed) throws Exception {
        File rom = new File("src/main/resources/assets/computercraft/lua/rom");
        assertTrue(rom.isDirectory(), "the Lua ROM must be present at " + rom.getAbsolutePath());

        File hdd = Files.createTempDirectory("cc-input-harness")
            .toFile();
        hdd.deleteOnExit();
        if (seed != null) seed.accept(hdd);

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

        try (InputStream bios = BiosInputHarness.class.getResourceAsStream("/assets/computercraft/lua/bios.lua")) {
            assertNotNull(bios, "bios.lua must be on the test classpath");
            machine.loadBios(bios);
        }
        return new BiosInputHarness(machine, computer, hdd);
    }

    /** Boots a computer with an empty save directory. */
    static BiosInputHarness boot() throws Exception {
        return boot(null);
    }

    /** Queues one {@code char} event per character, as the GUI does. */
    void type(String text) {
        for (char ch : text.toCharArray()) {
            lua.handleEvent("char", new Object[] { String.valueOf(ch) });
        }
    }

    /** Queues a {@code key} event. */
    void press(int key) {
        lua.handleEvent("key", new Object[] { key });
    }

    /** Queues a {@code mouse_click} event. */
    void click(int x, int y) {
        lua.handleEvent("mouse_click", new Object[] { 1, x, y });
    }

    /** Queues a {@code mouse_scroll} event. */
    void scroll(int direction) {
        lua.handleEvent("mouse_scroll", new Object[] { direction, 1, 1 });
    }

    /** Types {@code text} and presses Enter, then lets the computer settle. */
    void run(String command) {
        type(command);
        press(KEY_ENTER);
        pump(15);
    }

    /** Queues {@code count} idle events so suspended coroutines can make progress. */
    void pump(int count) {
        for (int i = 0; i < count && !lua.isFinished(); i++) {
            lua.handleEvent("tick", new Object[0]);
        }
    }

    /** The whole screen, one row per line, trailing whitespace removed. */
    String screen() {
        try {
            Field tf = Computer.class.getDeclaredField("m_terminal");
            tf.setAccessible(true);
            Terminal terminal = (Terminal) tf.get(computer);

            StringBuilder sb = new StringBuilder();
            for (int y = 0; y < terminal.getHeight(); y++) {
                sb.append(
                    terminal.getLine(y)
                        .toString()
                        .replaceAll("\\s+$", ""))
                    .append('\n');
            }
            return sb.toString();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** One screen row, 1-based, as a player sees it. */
    String line(int y) {
        return screen().lines()
            .skip(y - 1)
            .findFirst()
            .orElse("");
    }

    /** Screen height, so tests can address the footer row. */
    int height() {
        try {
            Field tf = Computer.class.getDeclaredField("m_terminal");
            tf.setAccessible(true);
            return ((Terminal) tf.get(computer)).getHeight();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Unloads the machine; call at the end of every test that booted one. */
    void close() {
        lua.unload();
    }

    private static Computer computerOf(CobaltMachine machine) throws Exception {
        Field f = CobaltMachine.class.getDeclaredField("computer");
        f.setAccessible(true);
        return (Computer) f.get(machine);
    }
}
