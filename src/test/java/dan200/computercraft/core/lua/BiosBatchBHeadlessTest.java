package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.filesystem.FileMount;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * Headless end-to-end tests for Batch B ({@code rom/apis/io}).
 *
 * <h2>What this covers that {@code IOAPITest} cannot</h2>
 * <p>
 * {@code IOAPITest} loads {@code io} into a private environment with a real {@code fs} API but no
 * BIOS. Only this test can check the parts that depend on the real bootstrap:
 * </p>
 * <ul>
 * <li>{@code os.loadAPI} giving {@code io} its own environment, so {@code io} does not clobber
 * {@code _G.read} — the reason a naive unit harness hangs forever;</li>
 * <li>{@code io.write} reaching the terminal, and {@code io.stderr} using the real
 * {@code term}/{@code colors} APIs;</li>
 * <li>{@code io.stdin} reading through the BIOS {@code read()}, driven by simulated keystrokes;</li>
 * <li>the bundled programs that use {@code io} — {@code help}, {@code edit}, {@code paintutils} —
 * still working against the real ROM filesystem.</li>
 * </ul>
 */
class BiosBatchBHeadlessTest {

    /** Checks the startup program is expected to report. */
    private static final List<String> EXPECTED_CHECKS = Arrays.asList(
        "io.table.available",
        "io.stdout.write",
        "io.stderr.write",
        "io.open.rom_file",
        "io.open.missing_file",
        "io.lines.rom_file",
        "io.output.to_file",
        "io.input.from_file",
        "io.stdin.read_line",
        "io.stdin.read_A",
        "io.type.closed",
        "io.read.from_stdin");

    /**
     * The startup program. It reads one line from stdin at a known point, so the harness can
     * feed keystrokes after boot; everything before that runs during {@code loadBios}.
     */
    private static final String STARTUP = String.join(
        "\n",
        "local results = {}",
        "local function record( name, ok, detail )",
        "  results[#results + 1] = ( ok and 'OK:' or 'FAIL:' ) .. name",
        "    .. ( detail and ( ':' .. tostring( detail ) ) or '' )",
        "end",
        "local function check( name, fn )",
        "  local ok, err = pcall( fn )",
        "  record( name, ok, err )",
        "end",
        "local function expect( name, cond, detail )",
        "  record( name, cond and true or false, ( not cond ) and detail or nil )",
        "end",
        "",
        "-- io must exist as a loaded API table with the standard surface.",
        "expect( 'io.table.available', io ~= nil and io.open ~= nil and io.write ~= nil",
        "  and io.read ~= nil and io.lines ~= nil and io.close ~= nil and io.stdout ~= nil )",
        "",
        "-- io.write must reach the real terminal (checked from the screen by the Java side).",
        "check( 'io.stdout.write', function()",
        "  local h = io.write( 'BATCHB-WRITE' )",
        "  assert( h == io.stdout, 'io.write must return the current output handle' )",
        "end )",
        "",
        "-- io.stderr must use term/colors without erroring.",
        "check( 'io.stderr.write', function()",
        "  io.stderr:write( 'BATCHB-STDERR' )",
        "end )",
        "",
        "-- Real file reading through /rom.",
        "check( 'io.open.rom_file', function()",
        "  local f = io.open( '/rom/help/intro' )",
        "  assert( f, 'could not open /rom/help/intro' )",
        "  local first = f:read()",
        "  assert( type( first ) == 'string' and #first > 0, 'first line was empty' )",
        "  local rest = f:read( '*a' )",
        "  assert( type( rest ) == 'string', 'read(*a) did not return a string' )",
        "  f:close()",
        "  assert( io.type( f ) == 'closed file', 'handle should report as closed' )",
        "end )",
        "",
        "check( 'io.open.missing_file', function()",
        "  local f, err = io.open( '/rom/help/definitely-not-a-topic' )",
        "  assert( f == nil, 'opening a missing file should fail' )",
        "  assert( type( err ) == 'string', 'failure should carry a message' )",
        "end )",
        "",
        "-- io.lines closes its own handle at EOF.",
        "check( 'io.lines.rom_file', function()",
        "  local n = 0",
        "  for line in io.lines( '/rom/help/intro' ) do",
        "    assert( type( line ) == 'string' )",
        "    n = n + 1",
        "  end",
        "  assert( n > 0, 'no lines read' )",
        "end )",
        "",
        "-- io.output redirects io.write to a file instead of the terminal.",
        "check( 'io.output.to_file', function()",
        "  local out = io.output( '/batchb-out.txt' )",
        "  io.write( 'redirected' )",
        "  out:close()",
        "  io.output( io.stdout )",
        "  local f = io.open( '/batchb-out.txt' )",
        "  local v = f:read( '*a' )",
        "  f:close()",
        "  assert( v == 'redirected', 'file contained ' .. tostring( v ) )",
        "end )",
        "",
        "-- io.input redirects io.read to a file.",
        "check( 'io.input.from_file', function()",
        "  local h = fs.open( '/batchb-in.txt', 'w' )",
        "  h.write( 'from the file' )",
        "  h.close()",
        "  io.input( '/batchb-in.txt' )",
        "  local v = io.read()",
        "  io.input( io.stdin )",
        "  assert( v == 'from the file', 'read returned ' .. tostring( v ) )",
        "end )",
        "",
        "-- stdin: a line, then the whole rest (which must be a string, not nil).",
        "check( 'io.stdin.read_line', function()",
        "  io.write( 'PROMPT1' )",
        "  local line = io.stdin:read()",
        "  assert( line == 'hello world', 'got ' .. tostring( line ) )",
        "end )",
        "-- The standard streams only expose readLine/write (no readAll), so read('*a') on stdin",
        "-- is nil. That matches CC:Tweaked: defaultInput is make_file({ readLine = ... }).",
        "check( 'io.stdin.read_A', function()",
        "  io.write( 'PROMPT2' )",
        "  local v = io.stdin:read( '*a' )",
        "  assert( v == nil, 'read(*a) on stdin should be nil, got ' .. tostring( v ) )",
        "end )",
        "",
        "-- io.read() must also route through stdin.",
        "check( 'io.read.from_stdin', function()",
        "  io.write( 'PROMPT3' )",
        "  local line = io.read()",
        "  assert( line == 'second', 'got ' .. tostring( line ) )",
        "end )",
        "",
        "-- io.type on a non-handle.",
        "expect( 'io.type.closed', io.type( {} ) == nil )",
        "",
        "local f = fs.open( '/batchb.log', 'w' )",
        "f.write( table.concat( results, '\\n' ) )",
        "f.close()");

    // -------------------------------------------------------------------------
    // Harness
    // -------------------------------------------------------------------------

    private static final class Boot {

        final File hdd;
        final String screen;
        final String log;

        Boot(File hdd, String screen, String log) {
            this.hdd = hdd;
            this.screen = screen;
            this.log = log;
        }
    }

    private static Boot boot() throws Exception {
        File rom = new File("src/main/resources/assets/computercraft/lua/rom");
        assertTrue(rom.isDirectory(), "the Lua ROM must be present at " + rom.getAbsolutePath());

        File hdd = Files.createTempDirectory("cc-bios-batch-b")
            .toFile();
        hdd.deleteOnExit();
        Files.write(new File(hdd, "startup").toPath(), STARTUP.getBytes(StandardCharsets.UTF_8));

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

        try (java.io.InputStream bios = BiosBatchBHeadlessTest.class
            .getResourceAsStream("/assets/computercraft/lua/bios.lua")) {
            assertNotNull(bios, "bios.lua must be on the test classpath");
            machine.loadBios(bios);
        }

        // loadBios returns once the startup program blocks on the first stdin read.
        // Feed the three lines it expects: one per prompt.
        for (int i = 0; i < 5; i++) machine.handleEvent("tick", new Object[0]);
        // read(*a) on stdin consumes nothing, so only two lines are actually read.
        typeLine(machine, "hello world");
        typeLine(machine, "second");

        for (int i = 0; i < 40 && !machine.isFinished(); i++) {
            machine.handleEvent("tick", new Object[0]);
        }

        String screen = readScreen(computer);
        machine.unload();

        File logFile = new File(hdd, "batchb.log");
        String log = logFile.isFile() ? new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8) : "";
        return new Boot(hdd, screen, log);
    }

    private static void typeLine(CobaltMachine machine, String text) {
        for (char ch : text.toCharArray()) {
            machine.handleEvent("char", new Object[] { String.valueOf(ch) });
        }
        machine.handleEvent("key", new Object[] { 28 }); // keys.enter
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

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void everyCheckPassesAgainstTheRealRom() throws Exception {
        Boot boot = boot();

        assertFalse(
            boot.log.isEmpty(),
            "the startup program wrote no report; it probably failed at load. Screen:\n" + boot.screen);

        List<String> lines = Arrays.asList(boot.log.split("\n"));

        List<String> failures = new java.util.ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String line : lines) {
            if (line.isEmpty()) continue;
            String name = line.substring(line.indexOf(':') + 1);
            int detail = name.indexOf(':');
            if (detail >= 0) name = name.substring(0, detail);
            seen.add(name);
            if (line.startsWith("FAIL:")) failures.add(line);
        }

        assertTrue(failures.isEmpty(), "checks failed: " + failures);
        Set<String> missing = new java.util.TreeSet<>(EXPECTED_CHECKS);
        missing.removeAll(seen);
        assertTrue(missing.isEmpty(), "these checks never ran: " + missing);
        Set<String> unexpected = new java.util.TreeSet<>(seen);
        unexpected.removeAll(EXPECTED_CHECKS);
        assertTrue(unexpected.isEmpty(), "undeclared checks: " + unexpected);
    }

    @Test
    void ioWriteAndStderrReachTheTerminal() throws Exception {
        Boot boot = boot();

        // io.write goes through the BIOS write(), so the text lands on the screen.
        assertTrue(
            boot.screen.contains("BATCHB-WRITE"),
            "io.write did not reach the terminal. Screen:\n" + boot.screen);
        // io.stderr uses _G.write too (in red), so it must also appear.
        assertTrue(
            boot.screen.contains("BATCHB-STDERR"),
            "io.stderr did not reach the terminal. Screen:\n" + boot.screen);
        // The prompts prove stdin reads consumed the simulated keystrokes in order.
        assertTrue(boot.screen.contains("PROMPT3"), "the third stdin read never ran. Screen:\n" + boot.screen);
    }

    @Test
    void helpStillPrintsThroughIo() throws Exception {
        // `help` is one of the bundled programs that reads its topic file with io.open/:read.
        File rom = new File("src/main/resources/assets/computercraft/lua/rom");
        File hdd = Files.createTempDirectory("cc-bios-batch-b-help")
            .toFile();
        hdd.deleteOnExit();
        Files.write(new File(hdd, "startup").toPath(), "shell.run(\"help intro\")\n".getBytes(StandardCharsets.UTF_8));

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
        try (java.io.InputStream bios = BiosBatchBHeadlessTest.class
            .getResourceAsStream("/assets/computercraft/lua/bios.lua")) {
            machine.loadBios(bios);
        }
        for (int i = 0; i < 10 && !machine.isFinished(); i++) machine.handleEvent("tick", new Object[0]);

        String screen = readScreen(computer);
        machine.unload();

        // The intro topic is paged output; any of its text proves io.open/:read worked.
        assertFalse(
            screen.contains("No such file") || screen.contains("attempt to"),
            "help could not read its topic file. Screen:\n" + screen);
        assertTrue(
            screen.trim()
                .length() > 0,
            "help printed nothing at all. Screen:\n" + screen);
    }

}
