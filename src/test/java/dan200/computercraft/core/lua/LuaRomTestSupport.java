package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.function.VarArgFunction;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.core.lua.lib.cobalt.CobaltConverter;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * Shared plumbing for tests which load ROM Lua files into a {@link CobaltMachine}.
 *
 * <p>
 * Several older test classes (see {@code SettingsAPITest}, {@code TextUtilsJsonTest}) each carry
 * their own copy of this boilerplate. Newer tests use this helper instead so that a change to the
 * machine setup only has to be made once.
 * </p>
 */
final class LuaRomTestSupport {

    private LuaRomTestSupport() {}

    /** Holds the arguments of the most recent {@code _capture(...)} call from Lua. */
    static final class Capture {

        Object[] args;

        Object at(int index) {
            assertNotNull(args, "capture must be called by the Lua test");
            return args[index];
        }

        /** @return {@code args[index]} as a {@code String} */
        String string(int index) {
            return (String) at(index);
        }

        /** @return {@code args[index]} as a {@code double} */
        double number(int index) {
            return ((Number) at(index)).doubleValue();
        }

        /** @return {@code args[index]} as a {@code boolean} */
        boolean bool(int index) {
            return (Boolean) at(index);
        }
    }

    /** Reads a classpath resource (usually a ROM file) as UTF-8 text. */
    static String readResource(String path) throws IOException {
        try (InputStream is = LuaRomTestSupport.class.getResourceAsStream(path)) {
            assertNotNull(is, path + " must be on the test classpath");
            try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                return scanner.hasNext() ? scanner.next() : "";
            }
        }
    }

    /** Builds a machine with the {@code _capture} helper installed. */
    static CobaltMachine machine(Capture capture) {
        ComputerCraft.bigInteger = false;
        ComputerCraft.bitop = false;
        ComputerCraft.timeoutError = false;
        CobaltMachine machine = LuaTestMachine.create();
        injectCapture(machine, capture);
        return machine;
    }

    private static void injectCapture(CobaltMachine machine, Capture capture) {
        try {
            Field f = CobaltMachine.class.getDeclaredField("globals");
            f.setAccessible(true);
            LuaTable globals = (LuaTable) f.get(machine);
            globals.rawset("_capture", new VarArgFunction() {

                @Override
                public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                    capture.args = CobaltConverter.toObjects(args, 1, false);
                    return Constants.NONE;
                }
            });
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to inject _capture", e);
        }
    }

    /**
     * Runs {@code source} (ROM prelude plus the test body) to completion.
     *
     * @param machine the machine to run on
     * @param source  the Lua source to load and run
     */
    static void run(CobaltMachine machine, String source) {
        machine.loadBios(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 50 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    /** Convenience for {@code run(machine, prelude + "\n" + body)}. */
    static void run(CobaltMachine machine, String prelude, String body) {
        run(machine, prelude + "\n" + body);
    }

    /**
     * Runs a ROM program file as a program would be run: the file's chunk executes after
     * {@code prelude} (which supplies the globals the program expects) and before {@code body}.
     *
     * <p>
     * Wrapping the source in a function means a program's top-level {@code return} ends the
     * program rather than the test, which is what the shell does when it loads a program.
     * </p>
     *
     * <p>
     * The chunk is called with the arguments in {@code arg[1..n]}, exactly as the shell passes them,
     * so a program that begins {@code local tArgs = { ... }} sees its command line. {@code arg[0]}
     * is left alone so it can stand in for the command as typed.
     * </p>
     *
     * @param resourcePath the classpath path of the ROM file, e.g. {@code rom/programs/rename}
     * @param prelude      Lua supplying the globals the program uses, including {@code arg}
     * @param body         Lua assertions, run afterwards
     * @return the capture written by {@code body}
     */
    static Capture runProgram(String resourcePath, String prelude, String body) throws IOException {
        Capture capture = new Capture();
        CobaltMachine machine = machine(capture);
        String source = readResource("/assets/computercraft/lua/" + resourcePath);
        assertFalse(source.contains("]====]"), resourcePath + " must not contain the test's long-bracket delimiter");

        String chunk = prelude + "\n"
            + "arg = arg or {}\n"
            + "do\n"
            + "  local chunk = assert(loadstring([====[\n"
            + source
            + "\n]====], '@"
            + resourcePath
            + "'))\n"
            + "  chunk( table.unpack( arg, 1, #arg ) )\n"
            + "end\n"
            + body;
        run(machine, chunk);
        return capture;
    }
}
