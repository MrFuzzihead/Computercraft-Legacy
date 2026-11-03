package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.function.VarArgFunction;

import dan200.computercraft.core.lua.lib.cobalt.CobaltConverter;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * Checks the bootstrap file {@code edit}'s Run writes before launching a program.
 *
 * <h2>Why this exists</h2>
 * <p>
 * Run used to save the buffer to {@code /.temp} and open a tab on it, so a runtime error was
 * reported by {@code shell} against {@code /.temp} — the wrong file — and the tab closed before
 * the error could be read. It now writes a bootstrap
 * ({@code return require("cc.internal.edit_runner")(title, "@" .. path, contents)}) so that
 * {@code cc.internal.edit_runner} loads the buffer under the real path, reports errors against it,
 * and waits for a key press.
 * </p>
 * <p>
 * The bootstrap embeds the whole buffer in a Lua string literal via {@code string.format("%q")}.
 * A buffer containing quotes, backslashes or newlines would produce a file that does not parse, and
 * the failure would only appear in game as "No such program". Rather than paraphrase the
 * production code here (which risks testing a different construct), this reads the handler literal
 * straight out of {@code rom/programs/edit} and formats it the same way.
 * </p>
 */
class EditRunnerBootstrapTest {

    private static final Path EDIT_PROGRAM = Path.of("src/main/resources/assets/computercraft/lua/rom/programs/edit");

    /** Characters that break naive escaping: quotes, a tab, line breaks and a '%'. */
    private static final String BUFFER = String.join(
        "\n",
        "-- a comment with \"quotes\" and a tab\there",
        "local ratio = 100 % 7 -- a percent sign",
        "error(\"boom\")");

    // -------------------------------------------------------------------------
    // Harness
    // -------------------------------------------------------------------------

    /** Pulls the {@code runHandler = [[...]]} literal out of the edit program. */
    private static String handlerLiteral() throws IOException {
        String source = Files.readString(EDIT_PROGRAM, StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("runHandler\\s*=\\s*(\\[=*\\[[\\s\\S]*?\\]=*\\])")
            .matcher(source);
        assertTrue(m.find(), "edit no longer writes a runHandler bootstrap; update this test and the audit notes");
        return m.group(1);
    }

    private static final class Captured {

        Object[] args;

        @Override
        public String toString() {
            if (args == null) return "<none>";
            StringBuilder sb = new StringBuilder();
            for (Object arg : args) {
                sb.append("[")
                    .append(String.valueOf(arg))
                    .append("]");
            }
            return sb.toString();
        }
    }

    private static void injectCapture(CobaltMachine machine, Captured captured) {
        try {
            Field f = CobaltMachine.class.getDeclaredField("globals");
            f.setAccessible(true);
            LuaTable globals = (LuaTable) f.get(machine);
            globals.rawset("_capture", new VarArgFunction() {

                @Override
                public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                    captured.args = CobaltConverter.toObjects(args, 1, false);
                    return Constants.NONE;
                }
            });
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to inject _capture", e);
        }
    }

    private static Captured runInLua(String lua) {
        CobaltMachine machine = LuaTestMachine.create();
        Captured captured = new Captured();
        injectCapture(machine, captured);
        machine.loadBios(new ByteArrayInputStream(lua.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 50 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
        assertNotNull(captured.args, "the Lua check did not run");
        return captured;
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void theHandlerIsTheUpstreamBootstrapShape() throws IOException {
        String literal = handlerLiteral();

        assertTrue(
            literal.contains("cc.internal.edit_runner"),
            "the bootstrap should launch cc.internal.edit_runner, not a temp file: " + literal);
        assertTrue(literal.contains("%q"), "the bootstrap should embed its arguments with %q: " + literal);
    }

    @Test
    void theRunBootstrapLoadsAndPassesTheBufferThrough() throws IOException {
        String lua = String.join(
            "\n",
            "function require(name)",
            "  return function(...) _capture(name, ...) end",
            "end",
            "local contents = [==[" + BUFFER + "]==]",
            "local runHandler = " + handlerLiteral(),
            "local bootstrap = runHandler:format(\"demo\", \"@/demo.lua\", contents)",
            "local fn, err = loadstring(bootstrap)",
            "if not fn then _capture(\"load failed\", tostring(err)) return end",
            "fn()");

        Captured captured = runInLua(lua);

        assertEquals(4, captured.args.length, "expected (module, title, path, contents), got: " + captured);
        assertEquals("cc.internal.edit_runner", captured.args[0]);
        assertEquals("demo", captured.args[1]);
        assertEquals("@/demo.lua", captured.args[2]);
        assertEquals(BUFFER, String.valueOf(captured.args[3]), "the buffer must survive the round trip intact");
    }

    @Test
    void theBufferKeepsItsLineCount() throws IOException {
        // Line numbers in a reported error come from the buffer as it was when Run was pressed, so
        // a bootstrap that mangled newlines would point at the wrong line.
        String lua = String.join(
            "\n",
            "function require(name)",
            "  return function(title, path, contents)",
            "    local count = 1",
            "    for _ in contents:gmatch('[\\n]') do count = count + 1 end",
            "    _capture(count, contents:sub(1, 9))",
            "  end",
            "end",
            "local contents = [==[" + BUFFER + "]==]",
            "local runHandler = " + handlerLiteral(),
            "local bootstrap = runHandler:format(\"t\", \"@/t.lua\", contents)",
            "local fn, err = loadstring(bootstrap)",
            "if not fn then _capture(\"load failed\", tostring(err)) return end",
            "fn()");

        Captured captured = runInLua(lua);

        assertEquals(2, captured.args.length, "expected (lines, prefix), got: " + captured);
        assertEquals(3.0, ((Number) captured.args[0]).doubleValue(), "the buffer should still have 3 lines");
        assertEquals("-- a comm", String.valueOf(captured.args[1]));
    }
}
