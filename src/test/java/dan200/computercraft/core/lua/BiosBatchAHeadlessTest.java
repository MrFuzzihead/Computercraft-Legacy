package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.filesystem.FileMount;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * Headless end-to-end harness for Batch A.
 *
 * <h2>Why this exists</h2>
 * <p>
 * The per-file unit tests load one ROM file at a time into a bare machine. That cannot catch
 * anything that depends on the <em>real</em> bootstrap order: {@code os.loadAPI} copying
 * {@code keys} into a table, {@code require} resolving {@code cc/base64.lua} through
 * {@code package.path}, {@code settings} defaults being registered before {@code settings.load},
 * or the per-program environment {@code os.run} builds. All of those only exist after
 * {@code bios.lua} has run.
 * </p>
 * <p>
 * So this test boots the real BIOS against the real ROM (the same way the game does), drops a
 * {@code /startup} program into the computer's save directory, and lets the shell run it. The
 * program appends {@code OK:name} / {@code FAIL:name:detail} lines to a log file, which is then
 * checked from Java. Every expected check must appear exactly once and none may fail, so a check
 * that stopped running is a failure rather than a silent pass.
 * </p>
 */
class BiosBatchAHeadlessTest {

    /** Every check the startup program is expected to report. */
    private static final List<String> EXPECTED_CHECKS = Arrays.asList(
        "os.version",
        "print.tab_separator",
        "print.nil_holes",
        "print.returns_line_count",
        "write.accepts_number",
        "printError.restores_colour",
        "dofile.defined",
        "dofile.returns_value",
        "loadfile.two_arg_env",
        "loadfile.three_arg",
        "os.run.success",
        "os.run.failure_returns_error",
        "loadAPI.strips_lua_extension",
        "settings.shell_allow_startup",
        "settings.edit_default_extension",
        "settings.motd_path",
        "settings.strict_globals_default",
        "settings.autocomplete_hidden_default",
        "fs.complete.hidden_excluded",
        "fs.complete.hidden_included",
        "fs.complete.boolean_args_keep_hidden",
        "fs.complete.include_dirs_false",
        "require.base64.roundtrip",
        "keys.semicolon_alias",
        "keys.printScreen",
        "keys.f16",
        "bios.strict_globals.enforced",
        "bios.strict_globals.relaxed");

    /**
     * The program the shell runs at boot. Kept as one Lua string rather than a resource file so
     * that the checks sit next to the assertions that read them.
     */
    private static final String STARTUP = String.join(
        "\n",
        "local results = {}",
        "local function record( name, ok, detail )",
        "    results[ #results + 1 ] = ( ok and 'OK:' or 'FAIL:' ) .. name .. ( detail and ( ':' .. tostring( detail ) ) or '' )",
        "end",
        "local function check( name, fn )",
        "    local ok, err = pcall( fn )",
        "    record( name, ok, err )",
        "end",
        "local function expect( name, cond, detail )",
        "    record( name, cond and true or false, ( not cond ) and detail or nil )",
        "end",
        "",
        "-- A term recorder, so print()/write()/printError() can be inspected without a screen.",
        "local function recorder()",
        "    local written = {}",
        // A `local rec = { ... }` constructor would leave these closures pointing at a
        // global `rec`, because the local is not in scope until the statement ends.
        "    local rec = {}",
        "    rec.written = written",
        "    rec._text = 1",
        "    rec.setTextColour = function( c ) rec._text = c end",
        "    rec.getTextColour = function() return rec._text end",
        "    rec.isColour = function() return true end",
        "    rec.getSize = function() return 51, 19 end",
        "    rec.getCursorPos = function() return 1, 1 end",
        "    rec.setCursorPos = function() end",
        "    rec.scroll = function() end",
        "    rec.clearLine = function() end",
        "    rec.clear = function() end",
        "    rec.setCursorBlink = function() end",
        "    rec.write = function( s ) written[ #written + 1 ] = s end",
        "    return rec",
        "end",
        "local function captured_text( rec ) return table.concat( rec.written ) end",
        "",
        "-- os.version",
        "expect( 'os.version', os.version() == 'CraftOS 1.9', os.version() )",
        "",
        "-- print() separates with tabs and does not drop nil holes",
        "do",
        "    local rec = recorder()",
        "    local old = term.redirect( rec )",
        "    print( 'a', 'b' )",
        "    term.redirect( old )",
        "    -- write() implements newlines by moving the cursor, so the recorder sees no '\\n'.",
        "    expect( 'print.tab_separator', captured_text( rec ) == 'a\\tb', string.format( '%q', captured_text( rec ) ) )",
        "end",
        "do",
        "    local rec = recorder()",
        "    local old = term.redirect( rec )",
        "    print( 1, nil, 3 )",
        "    term.redirect( old )",
        "    local txt = captured_text( rec )",
        "    expect( 'print.nil_holes', txt == '1\\tnil\\t3', string.format( '%q', txt ) )",
        "end",
        "do",
        "    local rec = recorder()",
        "    local old = term.redirect( rec )",
        "    local n = print( 'x' )",
        "    term.redirect( old )",
        "    expect( 'print.returns_line_count', n == 1, tostring( n ) )",
        "end",
        "",
        "-- write() coerces numbers instead of erroring",
        "do",
        "    local rec = recorder()",
        "    local old = term.redirect( rec )",
        "    local ok, err = pcall( write, 42 )",
        "    term.redirect( old )",
        "    expect( 'write.accepts_number', ok and captured_text( rec ) == '42', tostring( err ) .. '/' .. captured_text( rec ) )",
        "end",
        "",
        "-- printError restores the previous text colour rather than forcing white",
        "do",
        "    local rec = recorder()",
        "    local old = term.redirect( rec )",
        "    local before = colours.lime",
        "    term.setTextColour( before )",
        "    printError( 'oops' )",
        "    local after = rec.getTextColour()",
        "    term.redirect( old )",
        "    expect( 'printError.restores_colour', after == before, tostring( after ) .. ' vs ' .. tostring( before ) )",
        "end",
        "",
        "-- dofile() exists and returns the chunk's value",
        "check( 'dofile.defined', function() assert( type( dofile ) == 'function', 'dofile is ' .. type( dofile ) ) end )",
        "do",
        "    local f = fs.open( '/dofile-test', 'w' )",
        "    f.write( 'return 21 * 2' )",
        "    f.close()",
        "end",
        "check( 'dofile.returns_value', function()",
        "    local v = dofile( '/dofile-test' )",
        "    assert( v == 42, 'got ' .. tostring( v ) )",
        "end )",
        "",
        "-- loadfile( path, env ) and loadfile( path, mode, env )",
        "check( 'loadfile.two_arg_env', function()",
        "    local env = { }",
        "    local fn, err = loadfile( '/dofile-test', env )",
        "    assert( fn, tostring( err ) )",
        "    assert( fn() == 42 )",
        "end )",
        "check( 'loadfile.three_arg', function()",
        "    local fn, err = loadfile( '/dofile-test', 't', { } )",
        "    assert( fn, tostring( err ) )",
        "    assert( fn() == 42 )",
        "end )",
        "",
        "-- os.run returns (true) on success and (false, message) on failure",
        "do",
        "    local f = fs.open( '/okprog', 'w' )",
        "    f.write( 'return' )",
        "    f.close()",
        "    local f2 = fs.open( '/badprog', 'w' )",
        "    f2.write( 'error( \"boom\" )' )",
        "    f2.close()",
        "end",
        "check( 'os.run.success', function()",
        "    local ok = os.run( { }, '/okprog' )",
        "    assert( ok == true, tostring( ok ) )",
        "end )",
        "check( 'os.run.failure_returns_error', function()",
        "    local ok, err = os.run( { }, '/badprog' )",
        "    assert( ok == false, 'expected false, got ' .. tostring( ok ) )",
        "    assert( type( err ) == 'string', 'expected an error string, got ' .. type( err ) )",
        "    assert( err:find( 'boom' ), 'error did not mention boom: ' .. err )",
        "end )",
        "",
        "-- os.loadAPI exposes foo.lua as foo",
        "do",
        "    local f = fs.open( '/myapi.lua', 'w' )",
        "    f.write( 'function hello() return \"hi\" end' )",
        "    f.close()",
        "end",
        "check( 'loadAPI.strips_lua_extension', function()",
        "    assert( os.loadAPI( '/myapi.lua' ), 'loadAPI failed' )",
        "    assert( type( myapi ) == 'table', 'myapi is ' .. type( myapi ) )",
        "    assert( myapi.hello() == 'hi' )",
        "    os.unloadAPI( 'myapi' )",
        "end )",
        "",
        "-- BIOS-registered settings defaults",
        "expect( 'settings.shell_allow_startup', settings.get( 'shell.allow_startup' ) == true, tostring( settings.get( 'shell.allow_startup' ) ) )",
        "expect( 'settings.edit_default_extension', settings.get( 'edit.default_extension' ) == 'lua', tostring( settings.get( 'edit.default_extension' ) ) )",
        "expect( 'settings.motd_path', settings.get( 'motd.path' ) == '/rom/motd.txt:/motd.txt', tostring( settings.get( 'motd.path' ) ) )",
        "expect( 'settings.strict_globals_default', settings.get( 'bios.strict_globals' ) == false, tostring( settings.get( 'bios.strict_globals' ) ) )",
        "expect( 'settings.autocomplete_hidden_default', settings.get( 'shell.autocomplete_hidden' ) == false, tostring( settings.get( 'shell.autocomplete_hidden' ) ) )",
        "",
        "-- fs.complete include_hidden",
        "do",
        "    fs.makeDir( '/hidtest' )",
        "    local a = fs.open( '/hidtest/.hidden', 'w' ) a.close()",
        "    local b = fs.open( '/hidtest/visible', 'w' ) b.close()",
        "    fs.makeDir( '/hidtest/subdir' )",
        "end",
        "local function contains( t, v )",
        "    for _, x in ipairs( t ) do if x == v then return true end end",
        "    return false",
        "end",
        "check( 'fs.complete.hidden_excluded', function()",
        "    local r = fs.complete( '', '/hidtest', { include_files = true, include_dirs = false, include_hidden = false } )",
        "    assert( contains( r, 'visible' ), 'visible missing' )",
        "    assert( not contains( r, '.hidden' ), '.hidden should have been filtered' )",
        "end )",
        "check( 'fs.complete.hidden_included', function()",
        "    local r = fs.complete( '', '/hidtest', { include_files = true, include_dirs = false, include_hidden = true } )",
        "    assert( contains( r, '.hidden' ), '.hidden missing: ' .. table.concat( r, ',' ) )",
        "end )",
        "check( 'fs.complete.boolean_args_keep_hidden', function()",
        "    -- Legacy callers pass booleans; hidden entries must stay included for them.",
        "    local r = fs.complete( '', '/hidtest', true, false )",
        "    assert( contains( r, '.hidden' ), '.hidden missing: ' .. table.concat( r, ',' ) )",
        "end )",
        "check( 'fs.complete.include_dirs_false', function()",
        "    local r = fs.complete( '', '/hidtest', { include_files = true, include_dirs = false } )",
        "    assert( contains( r, 'subdir/' ), 'directory entry with slash missing' )",
        "    assert( not contains( r, 'subdir' ), 'raw directory entry should be suppressed' )",
        "end )",
        "",
        "-- cc.base64 through the real require/package.path",
        "check( 'require.base64.roundtrip', function()",
        "    local base64 = require 'cc.base64'",
        "    assert( base64.encode( 'foobar' ) == 'Zm9vYmFy', 'encode mismatch' )",
        "    assert( base64.decode( 'Zm9vYmFy' ) == 'foobar', 'decode mismatch' )",
        "end )",
        "",
        "-- keys aliases through the loaded keys API table",
        "expect( 'keys.semicolon_alias', keys.semicolon == keys.semiColon and keys.semicolon == 39, tostring( keys.semicolon ) )",
        "expect( 'keys.printScreen', keys.printScreen == 183, tostring( keys.printScreen ) )",
        "expect( 'keys.f16', keys.f16 == 103 and keys.f17 == 104 and keys.f18 == 105 and keys.f19 == 113, tostring( keys.f16 ) )",
        "",
        "-- bios.strict_globals end to end",
        "do",
        "    local f = fs.open( '/globalprog', 'w' )",
        "    f.write( 'leaked_global = 1' )",
        "    f.close()",
        "end",
        "check( 'bios.strict_globals.enforced', function()",
        "    settings.set( 'bios.strict_globals', true )",
        "    local env = {}",
        "    local ok, err = os.run( env, '/globalprog' )",
        "    settings.unset( 'bios.strict_globals' )",
        "    assert( ok == false, 'strict globals should have rejected the assignment' )",
        "    assert( env.leaked_global == nil, 'the assignment must not have taken effect' )",
        "    assert( type( err ) == 'string' and err:find( 'global' ), 'unexpected error: ' .. tostring( err ) )",
        "end )",
        "check( 'bios.strict_globals.relaxed', function()",
        "    -- os.run() loads into the env table it is handed, so the assignment lands there",
        "    -- (not in _G) -- which is exactly the Lua 5.2 semantics we want.",
        "    local env = {}",
        "    local ok = os.run( env, '/globalprog' )",
        "    assert( ok == true, 'default mode must still allow globals' )",
        "    assert( env.leaked_global == 1, 'the global was not created in the program env' )",
        "end )",
        "",
        "-- Write the report",
        "local out = fs.open( '/batch-a.log', 'w' )",
        "out.write( table.concat( results, '\\n' ) )",
        "out.close()");

    // -------------------------------------------------------------------------
    // Harness
    // -------------------------------------------------------------------------

    /** A booted machine plus everything a test needs to inspect it. */
    private static final class Boot {

        final File hdd;
        final String screen;

        Boot(File hdd, String screen) {
            this.hdd = hdd;
            this.screen = screen;
        }

        File log(String name) {
            return new File(hdd, name);
        }
    }

    /**
     * Boots the real BIOS against the real ROM.
     *
     * @param startup the contents of {@code /startup}, or {@code null} for none
     */
    private static Boot boot(String startup) throws Exception {
        return boot(startup, null);
    }

    /**
     * As {@link #boot(String)}, but also seeds {@code _CC_DEFAULT_SETTINGS} the way the mod does
     * from {@code cc_default_settings} in {@code ComputerCraft.cfg}. The static field is restored
     * afterwards so one test cannot leak defaults into another.
     *
     * @param startup         the contents of {@code /startup}, or {@code null}
     * @param defaultSettings the value of {@code _CC_DEFAULT_SETTINGS}, or {@code null}
     */
    private static Boot boot(String startup, String defaultSettings) throws Exception {
        File rom = new File("src/main/resources/assets/computercraft/lua/rom");
        assertTrue(rom.isDirectory(), "the Lua ROM must be present at " + rom.getAbsolutePath());

        File hdd = Files.createTempDirectory("cc-bios-batch-a")
            .toFile();
        hdd.deleteOnExit();

        // The shell runs /startup while bios.lua is still executing, so the file has to be in
        // place before loadBios().
        if (startup != null) {
            Files.write(new File(hdd, "startup").toPath(), startup.getBytes(StandardCharsets.UTF_8));
        }

        String previousDefaults = dan200.computercraft.ComputerCraft.cc_default_settings;
        dan200.computercraft.ComputerCraft.cc_default_settings = defaultSettings == null ? "" : defaultSettings;
        CobaltMachine machine;
        Computer computer;
        try {
            machine = LuaTestMachine.create();
            computer = computerOf(machine);
        } finally {
            dan200.computercraft.ComputerCraft.cc_default_settings = previousDefaults;
        }

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

        try (java.io.InputStream bios = BiosBatchAHeadlessTest.class
            .getResourceAsStream("/assets/computercraft/lua/bios.lua")) {
            assertNotNull(bios, "bios.lua must be on the test classpath");
            machine.loadBios(bios);
        }

        // Let the shell settle at its prompt; the startup program itself runs synchronously.
        for (int i = 0; i < 5 && !machine.isFinished(); i++) {
            machine.handleEvent("tick", new Object[0]);
        }
        assertFalse(machine.isFinished(), "the computer shut down while booting");

        String screen = readScreen(computer);
        machine.unload();
        return new Boot(hdd, screen);
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
    void batchAChecksAllPassInTheRealRomEnvironment() throws Exception {
        Boot boot = boot(STARTUP);

        File log = boot.log("batch-a.log");
        assertTrue(log.isFile(), "the startup program did not write /batch-a.log. Screen was:\n" + boot.screen);

        List<String> lines = Files.readAllLines(log.toPath(), StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty(), "the startup program wrote an empty report");

        List<String> failures = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String line : lines) {
            if (line.isEmpty()) continue;
            String name = line.substring(line.indexOf(':') + 1);
            int detail = name.indexOf(':');
            if (detail >= 0) name = name.substring(0, detail);
            seen.add(name);
            if (line.startsWith("FAIL:")) failures.add(line);
        }

        assertTrue(failures.isEmpty(), "checks failed in the real ROM environment: " + failures);

        Set<String> missing = new TreeSet<>(EXPECTED_CHECKS);
        missing.removeAll(seen);
        assertTrue(missing.isEmpty(), "these checks never ran: " + missing);

        Set<String> unexpected = new TreeSet<>(seen);
        unexpected.removeAll(EXPECTED_CHECKS);
        assertTrue(unexpected.isEmpty(), "checks reported but not declared in EXPECTED_CHECKS: " + unexpected);
    }

    @Test
    void shellHeaderShowsCraftOs19() throws Exception {
        Boot boot = boot(null);
        assertTrue(
            boot.screen.contains("CraftOS 1.9"),
            "the shell header should report CraftOS 1.9, screen was:\n" + boot.screen);
    }

    @Test
    void shippedSettingsDefaultsAreRegisteredBeforeTheShellStarts() throws Exception {
        // A separate, minimal startup so a failure here is not masked by the big report.
        Boot boot = boot(
            String.join(
                "\n",
                "local f = fs.open( '/defaults.log', 'w' )",
                "local names = settings.getNames()",
                "f.write( table.concat( names, '\\n' ) )",
                "f.close()"));

        File log = boot.log("defaults.log");
        assertTrue(log.isFile(), "defaults probe did not run. Screen was:\n" + boot.screen);
        List<String> names = Files.readAllLines(log.toPath(), StandardCharsets.UTF_8);
        for (String expected : Arrays.asList(
            "shell.allow_startup",
            "shell.allow_disk_startup",
            "shell.autocomplete",
            "shell.autocomplete_hidden",
            "edit.autocomplete",
            "lua.autocomplete",
            "edit.default_extension",
            "paint.default_extension",
            "list.show_hidden",
            "motd.enable",
            "motd.path",
            "lua.warn_against_use_of_local",
            "lua.function_args",
            "lua.function_source",
            "bios.strict_globals",
            "bios.use_multishell")) {
            assertTrue(names.contains(expected), "settings.getNames() is missing " + expected + ", got: " + names);
        }
    }

    // -------------------------------------------------------------------------
    // A.1.2: _CC_DEFAULT_SETTINGS parsing
    // -------------------------------------------------------------------------

    @Test
    void defaultSettingsParseBooleansNumbersAndNil() throws Exception {
        // `cc_test.*` names are deliberately unregistered: registered settings have a declared
        // type, so e.g. lua.function_args=2 is a type error rather than a number.
        Boot boot = boot(
            String.join(
                "\n",
                "local f = fs.open( '/values.log', 'w' )",
                "local function put( name )",
                "    local v = settings.get( name )",
                "    f.write( name .. '=' .. type( v ) .. ':' .. tostring( v ) .. '\\n' )",
                "end",
                "put( 'shell.autocomplete' )",
                "put( 'cc_test.number' )",
                "put( 'edit.default_extension' )",
                "put( 'bios.strict_globals' )",
                "put( 'paint.default_extension' )",
                "f.close()"),
            "shell.autocomplete=false,cc_test.number=2,edit.default_extension=txt,bios.strict_globals=nil,paint.default_extension=plainword");

        File log = boot.log("values.log");
        assertTrue(log.isFile(), "values probe did not run. Screen was:\n" + boot.screen);
        List<String> lines = Files.readAllLines(log.toPath(), StandardCharsets.UTF_8);

        assertEquals("shell.autocomplete=boolean:false", lines.get(0), "a boolean default must be a boolean");
        assertEquals("cc_test.number=number:2", lines.get(1), "a numeric default must be a number");
        assertEquals("edit.default_extension=string:txt", lines.get(2), "a bare word must be the string");
        // "nil" unsets, so the registered default is reported again.
        assertEquals(
            "bios.strict_globals=boolean:false",
            lines.get(3),
            "nil must unset the setting rather than storing the string \"nil\"");
        assertEquals("paint.default_extension=string:plainword", lines.get(4));
    }

    @Test
    void defaultSettingsTableFallbackStillWorks() throws Exception {
        // Local extension over upstream: a table literal is still parsed. Note the inherited
        // limitation that entries are split on "," before parsing, so only literals without
        // commas (such as {a=1}) survive -- the same limitation the previous implementation had.
        Boot boot = boot(
            String.join(
                "\n",
                "local f = fs.open( '/table.log', 'w' )",
                "local v = settings.get( 'cc_test.table' )",
                "f.write( type( v ) .. ':' .. tostring( v and v.a ) )",
                "f.close()"),
            "cc_test.table={a=1}");

        File log = boot.log("table.log");
        assertTrue(log.isFile(), "table probe did not run. Screen was:\n" + boot.screen);
        assertEquals(
            "table:1",
            Files.readAllLines(log.toPath(), StandardCharsets.UTF_8)
                .get(0),
            "a table default must still be parsed");
    }

    @Test
    void badDefaultSettingDoesNotBrickBoot() throws Exception {
        // motd.path is registered as a string, so a boolean value raises inside settings.set.
        // The BIOS must swallow that, keep the registered default, and keep processing the
        // remaining entries.
        Boot boot = boot(
            String.join(
                "\n",
                "local f = fs.open( '/bad.log', 'w' )",
                "f.write( tostring( settings.get( 'motd.path' ) ) .. '\\n' )",
                "f.write( tostring( settings.get( 'shell.autocomplete' ) ) )",
                "f.close()"),
            "motd.path=true,shell.autocomplete=false");

        File log = boot.log("bad.log");
        assertTrue(log.isFile(), "a type-mismatched default must not abort the BIOS; screen was:\n" + boot.screen);
        List<String> lines = Files.readAllLines(log.toPath(), StandardCharsets.UTF_8);
        assertEquals(
            "/rom/motd.txt:/motd.txt",
            lines.get(0),
            "the rejected value must fall back to the registered default");
        assertEquals("false", lines.get(1), "later entries must still be applied");
    }
}
