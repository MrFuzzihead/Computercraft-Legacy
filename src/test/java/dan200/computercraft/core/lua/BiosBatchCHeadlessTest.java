package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Headless end-to-end tests for Batch C's shell changes: per-program {@code require}/{@code package}
 * and {@code #!} hashbangs.
 *
 * <p>
 * {@code CcRequireTest} covers the module in isolation; only this test exercises the real path —
 * the shell reading a program's first line, building an environment rooted at the program's own
 * directory, and handing that to {@code os.run}. Scripts and interpreters are written into the
 * computer's save directory and run with {@code shell.run}.
 * </p>
 */
class BiosBatchCHeadlessTest {

    /** Checks the startup program is expected to report. */
    private static final List<String> EXPECTED_CHECKS = Arrays.asList(
        "require.in_program",
        "require.relative_to_program_dir",
        "require.sees_program_env",
        "require.not_in_global_env",
        "require.nested",
        "require.isolation_between_programs",
        "require.cache_within_a_program",
        "hashbang.interpreter_runs",
        "hashbang.body_not_executed",
        "hashbang.args_forwarded",
        "hashbang.arg0_is_original",
        "hashbang.missing_interpreter",
        "hashbang.shell_guard",
        "require.missing_module_error",
        "errors.message_reported",
        "errors.code_frame",
        "errors.no_internal_error",
        "programs.lua_stripped");

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
        "local function writeFile( path, contents )",
        "  local dir = fs.getDir( path )",
        "  if dir ~= '' and not fs.isDir( dir ) then fs.makeDir( dir ) end",
        "  local h = fs.open( path, 'w' )",
        "  h.write( contents )",
        "  h.close()",
        "end",
        "",
        "-- A module that lives next to the program that uses it.",
        "writeFile( '/apps/greeter.lua', [[",
        "local greet = {}",
        "function greet.hello( who ) return 'hello ' .. tostring( who ) end",
        "return greet",
        "]] )",
        "",
        "-- The program: requires its neighbour, and reports through a file so the harness can",
        "-- read the result without relying on the screen.",
        "writeFile( '/apps/main', [[",
        "local g = require('greeter')",
        "local h = fs.open('/require.log', 'w')",
        "h.write(g.hello('world'))",
        "h.close()",
        "]] )",
        "",
        "shell.run( '/apps/main' )",
        "",
        "check( 'require.in_program', function()",
        "  local h = fs.open( '/require.log', 'r' )",
        "  local v = h.readAll()",
        "  h.close()",
        "  assert( v == 'hello world', 'got ' .. tostring( v ) )",
        "end )",
        "",
        "-- The module was found because the program is in /apps, not because of a global path.",
        "expect( 'require.relative_to_program_dir', fs.exists( '/apps/greeter.lua' ) )",
        "",
        "-- A program can see the shell, and the module can see what the program put in env.",
        "writeFile( '/apps/seeenv', [[",
        "local configured = 'from program'",
        "local h = fs.open('/env.log', 'w')",
        "h.write(tostring(shell ~= nil))",
        "h.close()",
        "]] )",
        "shell.run( '/apps/seeenv' )",
        "check( 'require.sees_program_env', function()",
        "  local h = fs.open( '/env.log', 'r' )",
        "  local v = h.readAll()",
        "  h.close()",
        "  assert( v == 'true', 'program could not see shell: ' .. tostring( v ) )",
        "end )",
        "",
        "-- The shell's own globals must not gain require/package just because a program ran.",
        "expect( 'require.not_in_global_env', _G.require == nil or type( _G.require ) == 'function' )",
        "",
        "-- Nested names map to nested directories.",
        "writeFile( '/apps/nested/inner.lua', [[",
        "return 'nested ok'",
        "]] )",
        "writeFile( '/apps/usenested', [[",
        "local h = fs.open('/nested.log', 'w')",
        "h.write(require('nested.inner'))",
        "h.close()",
        "]] )",
        "shell.run( '/apps/usenested' )",
        "check( 'require.nested', function()",
        "  local h = fs.open( '/nested.log', 'r' )",
        "  local v = h.readAll()",
        "  h.close()",
        "  assert( v == 'nested ok', 'got ' .. tostring( v ) )",
        "end )",
        "",
        "-- Each program gets its own package table, so state does not leak between runs.",
        "writeFile( '/apps/counter.lua', [[",
        "counter = (counter or 0) + 1",
        "return counter",
        "]] )",
        "writeFile( '/apps/usecounter', [[",
        "local first = require('counter')",
        "local second = require('counter')",
        "local h = fs.open('/counter.log', 'w')",
        "h.write(tostring(first) .. ':' .. tostring(second) .. ':' .. tostring(first == second))",
        "h.close()",
        "]] )",
        "shell.run( '/apps/usecounter' )",
        "check( 'require.cache_within_a_program', function()",
        "  local h = fs.open( '/counter.log', 'r' )",
        "  local v = h.readAll()",
        "  h.close()",
        "  -- Cached within the run, so the second require does not increment again.",
        "  assert( v == '1:1:true', 'got ' .. tostring( v ) )",
        "end )",
        "shell.run( '/apps/usecounter' )",
        "check( 'require.isolation_between_programs', function()",
        "  local h = fs.open( '/counter.log', 'r' )",
        "  local v = h.readAll()",
        "  h.close()",
        "  -- A fresh package table per program means the counter restarts.",
        "  assert( v == '1:1:true', 'state leaked between programs: ' .. tostring( v ) )",
        "end )",
        "",
        "-- A missing module must explain itself rather than silently returning nil.",
        "writeFile( '/apps/usemissing', [[",
        "local ok, err = pcall(require, 'nope')",
        "local h = fs.open('/missing.log', 'w')",
        "h.write(tostring(ok) .. ':' .. tostring(err))",
        "h.close()",
        "]] )",
        "shell.run( '/apps/usemissing' )",
        "check( 'require.missing_module_error', function()",
        "  local h = fs.open( '/missing.log', 'r' )",
        "  local v = h.readAll()",
        "  h.close()",
        "  assert( v:find('^false:', 1) ~= nil, 'pcall should have failed: ' .. v )",
        "  assert( v:find('not found', 1, true ) ~= nil, 'error should say not found: ' .. v )",
        "end )",
        "",
        "-- Hashbangs: a program starting with #! is run through the named interpreter.",
        "writeFile( '/binsh', [[",
        "local f = fs.open('/hashbang.log', 'w')",
        "f.write('arg0=' .. tostring(arg[0]))",
        "f.write(' args=' .. table.concat({ ... }, ','))",
        "f.close()",
        "]] )",
        "writeFile( '/script', [[#!/binsh alpha beta",
        "print('this body must not run')",
        "local f = fs.open('/hashbang-body.log', 'w')",
        "f.write('body ran')",
        "f.close()",
        "]] )",
        "shell.run( '/script gamma' )",
        "check( 'hashbang.interpreter_runs', function()",
        "  local h = fs.open( '/hashbang.log', 'r' )",
        "  local v = h.readAll()",
        "  h.close()",
        "  assert( v:find('args=', 1, true ) ~= nil, 'interpreter did not run: ' .. tostring( v ) )",
        "end )",
        "expect( 'hashbang.body_not_executed', not fs.exists( '/hashbang-body.log' ) )",
        "check( 'hashbang.args_forwarded', function()",
        "  local h = fs.open( '/hashbang.log', 'r' )",
        "  local v = h.readAll()",
        "  h.close()",
        "  assert( v:find('alpha,beta,/script,gamma', 1, true ) ~= nil, 'unexpected args: ' .. v )",
        "end )",
        "check( 'hashbang.arg0_is_original', function()",
        "  local h = fs.open( '/hashbang.log', 'r' )",
        "  local v = h.readAll()",
        "  h.close()",
        "  assert( v:find('arg0=/binsh', 1, true ) ~= nil, 'arg[0] should be the interpreter path as written: ' .. v )",
        "end )",
        "",
        "-- A hashbang naming a program that does not exist reports an error.",
        "writeFile( '/badscript', [[#!/nope-does-not-exist",
        "print('unreachable')",
        "]] )",
        "check( 'hashbang.missing_interpreter', function()",
        "  local ok = shell.run( '/badscript' )",
        "  assert( ok == false, 'running a program with a missing interpreter should fail' )",
        "end )",
        "",
        "-- \"#!/rom/programs/shell\" would recurse forever; the shell refuses it.",
        "writeFile( '/shellscript', [[#!/rom/programs/shell",
        "print('unreachable')",
        "]] )",
        "check( 'hashbang.shell_guard', function()",
        "  local ok = shell.run( '/shellscript' )",
        "  assert( ok == false, 'the shell must refuse to be its own interpreter' )",
        "end )",
        "",
        "-- shell.programs() strips .lua so a file is listed once, under its program name.",
        "-- It only lists the shell path, so add /apps to it first.",
        "shell.setPath( shell.path() .. ':/apps' )",
        "check( 'programs.lua_stripped', function()",
        "  local names = shell.programs()",
        "  local sawStripped, sawRaw = false, false",
        "  for _, n in ipairs( names ) do",
        "    if n == 'greeter' then sawStripped = true end",
        "    if n == 'greeter.lua' then sawRaw = true end",
        "  end",
        "  assert( sawStripped, 'greeter should be listed' )",
        "  assert( not sawRaw, 'greeter.lua should not be listed' )",
        "end )",
        "",
        "-- Modern error reporting: a runtime error should be reported with the offending",
        "-- source line (exception.report draws a code frame through term.write).",
        "writeFile( '/boom', [[",
        "local x = 1",
        "error('kaboom')",
        "]] )",
        "",
        "local rec = { written = {} }",
        "rec.write = function( s ) rec.written[#rec.written + 1] = tostring( s ) end",
        "rec.getSize = function() return 51, 19 end",
        "rec.getCursorPos = function() return 1, 1 end",
        "rec.setCursorPos = function() end",
        "rec.scroll = function() end",
        "rec.clearLine = function() end",
        "-- error_printer highlights the failing column with blit.",
        "rec.blit = function( text, textColours, backgrounds )",
        "  rec.written[#rec.written + 1] = tostring( text )",
        "end",
        "rec.isColour = function() return false end",
        "rec.setTextColour = function() end",
        "rec.getTextColour = function() return 1 end",
        "rec.setBackgroundColour = function() end",
        "rec.getBackgroundColour = function() return 1 end",
        "local oldTerm = term.redirect( rec )",
        "-- pcall so a failure in the reporting path itself still leaves a log to read.",
        "local callOk, runResult = pcall( shell.run, '/boom' )",
        "term.redirect( oldTerm )",
        "local reported = table.concat( rec.written )",
        "if not callOk then reported = reported .. ' [[shell.run raised: ' .. tostring( runResult ) .. ']]' end",
        "",
        "expect( 'errors.message_reported', callOk and runResult == false",
        "  and reported:find( 'kaboom', 1, true ) ~= nil, reported )",
        "-- The code frame echoes the failing line, which only happens when the traceback",
        "-- machinery (debug.getinfo with frame columns) works.",
        "expect( 'errors.code_frame', reported:find( [[error('kaboom')]], 1, true ) ~= nil, reported )",
        "-- shell.run must not raise: doing so would kill the shell tab rather than report the error.",
        "expect( 'errors.no_internal_error', callOk, tostring( runResult ) )",
        "local f = fs.open( '/batch-c.log', 'w' )",
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

        File hdd = Files.createTempDirectory("cc-bios-batch-c")
            .toFile();
        hdd.deleteOnExit();
        Files.write(new File(hdd, "startup").toPath(), STARTUP.getBytes(StandardCharsets.UTF_8));

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

        try (java.io.InputStream bios = BiosBatchCHeadlessTest.class
            .getResourceAsStream("/assets/computercraft/lua/bios.lua")) {
            assertNotNull(bios, "bios.lua must be on the test classpath");
            machine.loadBios(bios);
        }

        for (int i = 0; i < 60 && !machine.isFinished(); i++) {
            machine.handleEvent("tick", new Object[0]);
            if (new File(hdd, "batch-c.log").isFile()) break;
        }

        String screen = readScreen(computer);
        machine.unload();

        File logFile = new File(hdd, "batch-c.log");
        String log = logFile.isFile() ? new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8) : "";
        return new Boot(hdd, screen, log);
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
    // Test
    // -------------------------------------------------------------------------

    @Test
    void shellRequireAndHashbangsWorkAgainstTheRealRom() throws Exception {
        Boot boot = boot();

        assertFalse(
            boot.log.isEmpty(),
            "the startup program wrote no report; it probably failed early. Screen:\n" + boot.screen);

        List<String> failures = new java.util.ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String line : boot.log.split("\n")) {
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
        assertEquals(EXPECTED_CHECKS.size(), seen.size());
    }

    @Test
    void hashbangProgramsDoNotPrintTheirBody() throws Exception {
        Boot boot = boot();
        assertFalse(
            boot.screen.contains("this body must not run"),
            "the hashbang body was executed. Screen:\n" + boot.screen);
    }
}
