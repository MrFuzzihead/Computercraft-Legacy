package dan200.computercraft.core.lua;

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
}
