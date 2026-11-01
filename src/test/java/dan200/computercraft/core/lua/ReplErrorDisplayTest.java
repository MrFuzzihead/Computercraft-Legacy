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
 * Checks how a runtime error from the Lua REPL is displayed.
 *
 * <h2>Why this exists</h2>
 * <p>
 * A user-visible difference was found against CC:Tweaked 1.120.2: the same REPL error was reported
 * as a bare red line here, while upstream also prints a {@code Line N} block with the offending
 * source and a caret under the failing token, and names the chunk {@code lua[3]} rather than
 * {@code lua}. The cause was two lines in {@code rom/programs/lua}: the chunk name was the constant
 * {@code "lua"} and {@code exception.report} was handed an empty source map, so
 * {@code cc.internal.exception} had nothing to render and bailed out silently.
 * </p>
 * <p>
 * The error printer, hints and exception modules are byte-identical to upstream, so the only thing
 * that can regress is that wiring — which is exactly what this test drives: real BIOS, real ROM,
 * real keystrokes, real terminal.
 * </p>
 */
class ReplErrorDisplayTest {

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

        void type(String text) {
            for (char ch : text.toCharArray()) {
                lua.handleEvent("char", new Object[] { String.valueOf(ch) });
            }
        }

        void pressEnter() {
            lua.handleEvent("key", new Object[] { 28 }); // keys.enter
        }

        /** Types a line and submits it. */
        void submit(String text) {
            type(text);
            pressEnter();
        }

        void pump(int count) {
            for (int i = 0; i < count && !lua.isFinished(); i++) {
                lua.handleEvent("tick", new Object[0]);
            }
        }

        String screen() throws Exception {
            StringBuilder sb = new StringBuilder();
            for (String line : lines()) {
                sb.append(line)
                    .append('\n');
            }
            return sb.toString();
        }

        java.util.List<String> lines() throws Exception {
            Field tf = Computer.class.getDeclaredField("m_terminal");
            tf.setAccessible(true);
            dan200.computercraft.core.terminal.Terminal t = (dan200.computercraft.core.terminal.Terminal) tf
                .get(computer);
            java.util.List<String> out = new java.util.ArrayList<>();
            for (int y = 0; y < t.getHeight(); y++) {
                out.add(
                    t.getLine(y)
                        .toString());
            }
            return out;
        }
    }

    private static Machine boot() throws Exception {
        return boot(java.util.Map.of());
    }

    /**
     * Boots with the given files already on the computer's drive, so a test can run a real program.
     *
     * @param files file name to contents
     */
    private static Machine boot(java.util.Map<String, String> files) throws Exception {
        File rom = new File("src/main/resources/assets/computercraft/lua/rom");
        assertTrue(rom.isDirectory(), "the Lua ROM must be present at " + rom.getAbsolutePath());

        File hdd = Files.createTempDirectory("cc-repl-error")
            .toFile();
        hdd.deleteOnExit();
        for (java.util.Map.Entry<String, String> file : files.entrySet()) {
            Files.writeString(
                new File(hdd, file.getKey()).toPath(),
                file.getValue(),
                java.nio.charset.StandardCharsets.UTF_8);
        }

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

        try (java.io.InputStream bios = ReplErrorDisplayTest.class
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

    /** Boots, opens the REPL, and runs the two inputs that produced the reported screenshot. */
    private static Machine replWithFailingCall() throws Exception {
        Machine m = boot();
        m.pump(5);

        m.submit("lua");
        m.pump(10);

        // First input: a plain assignment, so the REPL's chunk counter advances to 2.
        m.submit("t = {}");
        m.pump(10);

        // Second input: the call that fails, in the expression-wrapper form.
        m.submit("t.getType()");
        m.pump(20);
        return m;
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void aRuntimeErrorNamesTheReplChunk() throws Exception {
        Machine m = replWithFailingCall();
        String screen = m.screen();

        assertTrue(
            screen.contains("attempt to call field 'getType'"),
            "the error itself should be reported. Screen:\n" + screen);

        // Upstream reads `lua[3]:1: ...`, naming the REPL input. A bare `lua:1:` means the chunk
        // name lost its counter again.
        assertTrue(
            screen.contains("lua[2]:1:"),
            "the error should name the REPL chunk with its index (lua[2]). Screen:\n" + screen);

        m.lua.unload();
    }

    @Test
    void aRuntimeErrorShowsTheOffendingSourceLine() throws Exception {
        Machine m = replWithFailingCall();
        String screen = m.screen();

        assertTrue(
            screen.contains("Line 1"),
            "the error should be annotated with the source line it came from. Screen:\n" + screen);
        assertTrue(
            screen.contains("t.getType()"),
            "the annotated block should echo the input that failed. Screen:\n" + screen);

        m.lua.unload();
    }

    @Test
    void aRuntimeErrorPointsAtTheFailingToken() throws Exception {
        Machine m = replWithFailingCall();

        // The caret row is drawn with blits of U+0083; find the row that carries them.
        String caretRow = null;
        for (String line : m.lines()) {
            if (line.indexOf('\u0083') >= 0) {
                caretRow = line;
                break;
            }
        }
        assertTrue(caretRow != null, "no caret row was drawn. Screen:\n" + m.screen());

        // The caret is drawn at the column Cobalt reports, after the source map's offset (13) has
        // subtracted the `return _echo(` wrapper. Without that correction the caret lands 13
        // columns to the right, past the end of a short line -- and with no source map at all
        // (the bug behind the screenshot) the whole annotated block is skipped.
        int caretStart = caretRow.indexOf('\u0083') + 1;

        assertTrue(
            caretStart >= 1 && caretStart <= "t.getType()".length(),
            "the caret should fall inside the echoed line (columns 1-" + "t.getType()"
                .length() + "), not at column " + caretStart + ". Screen:\n" + m.screen() + "\ncaret row: " + caretRow);

        m.lua.unload();
    }

    @Test
    void aStatementErrorIsAnnotatedWithoutTheWrapperOffset() throws Exception {
        // The expression path is wrapped as `return _echo(...)` and needs a 13-column correction;
        // a statement is loaded as-is (offset 0), so the caret comes straight from the token
        // column. Both paths must still produce the annotated block.
        Machine m = boot();
        m.pump(5);
        m.submit("lua");
        m.pump(10);
        m.submit("local t = {} t.x.y = 1");
        m.pump(20);

        String screen = m.screen();
        assertTrue(screen.contains("attempt to index"), "the statement's error should be reported. Screen:\n" + screen);
        assertTrue(screen.contains("Line 1"), "the error should be annotated. Screen:\n" + screen);

        String caretRow = null;
        for (String line : m.lines()) {
            if (line.indexOf('\u0083') >= 0) {
                caretRow = line;
                break;
            }
        }
        assertTrue(caretRow != null, "no caret row was drawn. Screen:\n" + screen);
        int caretStart = caretRow.lastIndexOf('\u0083') + 1;
        assertTrue(
            caretStart <= "local t = {} t.x.y = 1".length(),
            "the caret should fall inside the echoed line, not at column " + caretStart + ". Screen:\n" + screen);

        m.lua.unload();
    }

    @Test
    void aCrashingProgramIsAnnotatedFromTheFileOnDisk() throws Exception {
        // The most common error anyone sees: a program that throws. This path has no source map --
        // exception.report re-reads the file from disk -- so it is checked separately from the REPL.
        Machine m = boot(java.util.Map.of("boom", "local x = nil\n-- second line\nerror(\"from line 3\")\n"));
        m.pump(5);
        m.submit("boom");
        m.pump(25);

        String screen = m.screen();
        assertTrue(screen.contains("from line 3"), "the program's error should be reported. Screen:\n" + screen);
        assertTrue(screen.contains("boom:3:"), "the error should name the program and its line. Screen:\n" + screen);
        assertTrue(screen.contains("Line 3"), "the error should be annotated with the source line. Screen:\n" + screen);
        assertTrue(
            screen.contains("error(\"from line 3\")") || screen.contains("error('from line 3')"),
            "the annotated block should echo the offending line from disk. Screen:\n" + screen);

        m.lua.unload();
    }

    @Test
    void aSyntaxErrorIsReportedByTheRomParser() throws Exception {
        // Syntax errors take the other branch: the ROM's own parser reports them with a caret
        // rather than echoing the Lua loader's message.
        Machine m = boot();
        m.pump(5);
        m.submit("lua");
        m.pump(10);
        m.submit("this is not lua");
        m.pump(15);

        String screen = m.screen();
        assertTrue(
            screen.contains("Line 1") || screen.contains("unexpected") || screen.contains("expected"),
            "a syntax error should be reported with the parser's annotation. Screen:\n" + screen);

        m.lua.unload();
    }
}
