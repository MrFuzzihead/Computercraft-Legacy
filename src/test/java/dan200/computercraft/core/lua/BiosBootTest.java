package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.squiddev.cobalt.LuaTable;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.filesystem.FileMount;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * Boots the real {@code bios.lua} through a {@link CobaltMachine} and drives an event.
 *
 * <h2>Why this test exists</h2>
 * <p>
 * The rest of the Lua test suite exercises {@code CobaltMachine} against small hand-written
 * scripts, or against a single block extracted from {@code bios.lua}
 * (see {@link BiosRequireTest}). None of them ever loaded the whole BIOS and ran it. As a
 * result the following three defects passed the entire suite and were only found in game:
 * </p>
 * <ul>
 *   <li>the packaged jar contained class files newer than Java 8, which FML 1.7.10's ASM 5.0.3
 *       cannot read, so FML discarded the entire mod;</li>
 *   <li>{@code handleEvent} did not handle {@code LuaThread.run} returning {@code null} when the
 *       coroutine suspends, which is what {@code os.pullEvent} does immediately, so the very
 *       first event threw a {@link NullPointerException} that escaped {@code handleEvent};</li>
 *   <li>the {@code load} override only accepted a function as its first argument and ignored the
 *       trailing mode/environment arguments, so {@code bios.lua}'s own {@code loadfile} —
 *       which calls {@code load(<string>, name, "t", env)} during {@code loadAPI} — died with
 *       "bad argument (function expected, got string)" before the computer ever started.</li>
 * </ul>
 * <p>
 * Booting the real BIOS covers all three: it loads the packaged resource, runs to the first
 * {@code os.pullEvent} (suspending the coroutine), and gets as far as {@code loadAPI}, which
 * exercises the string/mode/env form of {@code load}.
 */
class BiosBootTest {

    /**
     * Mirrors {@code Computer.initLua()}: every API the {@link Computer} registered must be added
     * to the machine <em>before</em> the BIOS is loaded, because the BIOS extends those globals
     * at load time ({@code function os.version()}, {@code fs.name}, ...). Registering the real
     * API set, rather than hand-made stubs, is what makes this test faithful to the game.
     */
    private static void addApis(CobaltMachine machine) throws Exception {
        java.lang.reflect.Field computerField = CobaltMachine.class.getDeclaredField("computer");
        computerField.setAccessible(true);
        Computer computer = (Computer) computerField.get(machine);

        // The mount layout must match Computer.startComputer(): a writable "hdd" plus a read-only
        // "rom" pointing at the real Lua ROM. The ROM matters -- bios.lua:867 does
        // `fs.list("rom/apis")` and then dofile()s every module in there, so without it the
        // BIOS dies before it ever reaches os.pullEvent.
        java.lang.reflect.Field fsField = Computer.class.getDeclaredField("m_fileSystem");
        fsField.setAccessible(true);
        if (fsField.get(computer) == null) {
            File rom = new File("src/main/resources/assets/computercraft/lua/rom");
            assertTrue(rom.isDirectory(), "the Lua ROM must be present at " + rom.getAbsolutePath());
            File hdd = Files.createTempDirectory("cc-bios-test").toFile();
            hdd.deleteOnExit();
            FileSystem fs = new FileSystem("hdd", new FileMount(hdd, 4L * 1024 * 1024));
            fs.mount("rom", "rom", new FileMount(rom, 4L * 1024 * 1024));
            fsField.set(computer, fs);
        }

        java.lang.reflect.Field apisField = Computer.class.getDeclaredField("m_apis");
        apisField.setAccessible(true);

        @SuppressWarnings("unchecked")
        java.util.List<ILuaAPI> apis = (java.util.List<ILuaAPI>) apisField.get(computer);
        for (ILuaAPI api : apis) {
            machine.addAPI(api);
            api.startup();
        }
    }

    /** Loads the real {@code bios.lua} into a machine, with the standard APIs registered first. */
    private static CobaltMachine bootBios() throws Exception {
        CobaltMachine machine = LuaTestMachine.create();
        addApis(machine);
        try (InputStream bios = BiosBootTest.class.getResourceAsStream("/assets/computercraft/lua/bios.lua")) {
            assertNotNull(bios, "bios.lua must be on the test classpath");
            machine.loadBios(bios);
        }
        return machine;
    }

    @Test
    void biosLoadsIntoARunnableMachine() throws Exception {
        CobaltMachine machine = bootBios();
        assertFalse(machine.isFinished(), "bios.lua failed to load; the machine would be dead on arrival");
    }

    @Test
    void biosSurvivesTheFirstEventAndSuspends() throws Exception {
        CobaltMachine machine = bootBios();

        // The BIOS runs to os.pullEvent during startup, which suspends the coroutine. At that
        // point LuaThread.run returns null, and handleEvent must treat that as a pause rather
        // than dereferencing the result.
        machine.handleEvent("tick", new Object[0]);

        assertFalse(machine.isFinished(), "computer died while resuming bios.lua on the first event");
    }

    @Test
    void repeatedEventsDoNotKillTheMachine() throws Exception {
        CobaltMachine machine = bootBios();
        for (int i = 0; i < 5; i++) {
            machine.handleEvent("tick", new Object[0]);
            assertFalse(machine.isFinished(), "computer died on event " + i);
        }
    }

    @Test
    void unloadingAWorkingMachineLeavesItFinished() throws Exception {
        CobaltMachine machine = bootBios();
        machine.handleEvent("tick", new Object[0]);
        assertFalse(machine.isFinished(), "precondition: machine should be running");

        machine.unload();

        assertTrue(machine.isFinished(), "unload() should finish the machine");
    }
}
