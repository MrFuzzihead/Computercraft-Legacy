package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dan200.computercraft.core.apis.FSAPI;
import dan200.computercraft.core.filesystem.FileMount;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.filesystem.FileSystemException;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;
import dan200.computercraft.testsupport.StubApiEnvironment;

/**
 * Tests for {@code rom/modules/main/cc/require.lua} (ported from CC:Tweaked 1.120.2).
 *
 * <p>
 * The module is loaded into a machine with a real {@link FileSystem}, so {@code package.path}
 * resolution, {@code loadfile} and directory-relative lookup all run against real files rather
 * than mocks. The module's {@code require("cc.expect")} is satisfied by a stub, since the point
 * under test is module loading, not argument validation.
 * </p>
 */
class CcRequireTest {

    @TempDir
    Path tempDir;

    private LuaRomTestSupport.Capture capture;
    private CobaltMachine machine;
    private FileSystem fileSystem;
    private String prelude;

    @BeforeEach
    void setUp() throws FileSystemException, IOException {
        capture = new LuaRomTestSupport.Capture();
        machine = LuaRomTestSupport.machine(capture);

        fileSystem = new FileSystem("hdd", new FileMount(tempDir.toFile(), 1024 * 1024L));
        FSAPI fsApi = new FSAPI(new StubApiEnvironment(fileSystem));
        machine.addAPI(fsApi);
        fsApi.startup();

        String expect = LuaRomTestSupport.readResource("/assets/computercraft/lua/rom/modules/main/cc/expect.lua");
        String requireSource = LuaRomTestSupport
            .readResource("/assets/computercraft/lua/rom/modules/main/cc/require.lua");
        prelude = "local _expect_module = (function()\n" + expect
            + "\nend)()\n"
            + "function require( _name )\n"
            + "  if _name == 'cc.expect' then return _expect_module end\n"
            + "  error( 'module not found: ' .. tostring( _name ) )\n"
            + "end\n"
            // loadfile is a BIOS global. This machine has no BIOS, so provide the same
            // implementation bios.lua uses (mode and env are passed through to load).
            + "function loadfile( _sFile, _sMode, _tEnv )\n"
            + "  local file = fs.open( _sFile, 'r' )\n"
            + "  if not file then return nil, 'File not found' end\n"
            + "  local contents = file.readAll()\n"
            + "  file.close()\n"
            + "  return load( contents, '@/' .. _sFile, _sMode, _tEnv )\n"
            + "end\n"
            + "cc = { require = (function()\n"
            + requireSource
            + "\nend)() }\n";
    }

    /** Releases open files so Windows can delete the temp directory. */
    @AfterEach
    void tearDown() {
        if (fileSystem != null) fileSystem.unload();
    }

    private LuaRomTestSupport.Capture run(String body) {
        LuaRomTestSupport.run(machine, prelude + body);
        return capture;
    }

    private void writeFile(String path, String content) {
        try {
            Path target = tempDir.resolve(path);
            Files.createDirectories(target.getParent());
            Files.write(target, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    // -------------------------------------------------------------------------
    // searchpath
    // -------------------------------------------------------------------------

    @Test
    void searchpathPrefersTheFirstMatchingTemplate() {
        writeFile("lib/a.lua", "return 1");
        writeFile("lib/a/init.lua", "return 2");

        LuaRomTestSupport.Capture cap = run(
            "local r, p = cc.require.make({}, 'lib')\n" + "_capture(p.searchpath('a', '?;?.lua;?/init.lua'))");
        assertEquals("lib/a.lua", cap.string(0), "the first template with a file wins");
    }

    @Test
    void searchpathResolvesNestedNames() {
        writeFile("lib/deep/mod.lua", "return 'deep'");

        LuaRomTestSupport.Capture cap = run(
            "local r, p = cc.require.make({}, 'lib')\n" + "local path = p.searchpath('deep.mod', '?.lua')\n"
                + "local m = r'deep.mod'\n"
                + "_capture(path, m)");
        assertEquals("lib/deep/mod.lua", cap.string(0), "dots become directory separators");
        assertEquals("deep", cap.string(1));
    }

    @Test
    void searchpathHonoursCustomSeparators() {
        writeFile("lib/deep/mod.lua", "return 1");

        LuaRomTestSupport.Capture cap = run(
            "local r, p = cc.require.make({}, 'lib')\n" + "_capture(p.searchpath('deep::mod', '?.lua', '::', '/'))");
        assertEquals("lib/deep/mod.lua", cap.string(0));
    }

    @Test
    void searchpathReportsEveryPathItTried() {
        LuaRomTestSupport.Capture cap = run(
            "local r, p = cc.require.make({}, 'lib')\n" + "local path, err = p.searchpath('nope', '?;?.lua')\n"
                + "_capture(path == nil, err ~= nil and err:find('no file', 1, true) ~= nil, err)");
        assertTrue(cap.bool(0));
        assertTrue(cap.bool(1), "the error should mention the paths tried");
        assertTrue(
            ((String) cap.at(2)).contains("lib/nope"),
            "the error should include the directory-relative path, got: " + cap.at(2));
    }

    // -------------------------------------------------------------------------
    // require
    // -------------------------------------------------------------------------

    @Test
    void requireLoadsAModuleRelativeToItsDirectory() {
        writeFile("lib/greeter.lua", "return { greet = function() return 'hi' end }");

        LuaRomTestSupport.Capture cap = run(
            "local r, p = cc.require.make({}, 'lib')\n" + "local m = r('greeter')\n" + "_capture(type(m), m.greet())");
        assertEquals("table", cap.string(0));
        assertEquals("hi", cap.string(1));
    }

    @Test
    void requireAcceptsExtensionlessModules() {
        // This ROM's own modules have no .lua suffix, so the "?" template has to work.
        writeFile("lib/plain", "return 'no extension'");

        LuaRomTestSupport.Capture cap = run("local r, p = cc.require.make({}, 'lib')\n_capture(r('plain'))");
        assertEquals("no extension", cap.string(0));
    }

    @Test
    void requireLoadsInitModules() {
        writeFile("lib/pkg/init.lua", "return 'init'");

        LuaRomTestSupport.Capture cap = run("local r, p = cc.require.make({}, 'lib')\n_capture(r('pkg'))");
        assertEquals("init", cap.string(0));
    }

    @Test
    void requireCachesResults() {
        writeFile("lib/counter.lua", "return {}");

        LuaRomTestSupport.Capture cap = run(
            "local r, p = cc.require.make({}, 'lib')\n" + "local a = r('counter')\n"
                + "local b = r('counter')\n"
                + "_capture(a == b, p.loaded['counter'] == a)");
        assertTrue(cap.bool(0), "repeated requires must return the same table");
        assertTrue(cap.bool(1), "the result must be stored in package.loaded");
    }

    @Test
    void moduleReturningNothingBecomesTrue() {
        // Modules load with the provided env, not _G, so the module writes there.
        writeFile("lib/sideeffect.lua", "load_count = (load_count or 0) + 1");

        LuaRomTestSupport.Capture cap = run(
            "local env = {}\n" + "local r, p = cc.require.make(env, 'lib')\n"
                + "local v = r('sideeffect')\n"
                + "_capture(v, env.load_count)");
        assertEquals(true, cap.at(0), "a module with no return value loads as true");
        assertEquals(1.0, cap.number(1));
    }

    @Test
    void modulesRunInTheProvidedEnvironment() {
        // env is passed to loadfile, so a module's globals land in that env table.
        writeFile("lib/envmod.lua", "module_global = 'set by module'\nreturn true");

        LuaRomTestSupport.Capture cap = run(
            "local env = {}\n" + "local r, p = cc.require.make(env, 'lib')\n"
                + "r('envmod')\n"
                + "_capture(env.module_global, _G.module_global)");
        assertEquals("set by module", cap.string(0), "the module should see the given env");
        assertNull(cap.at(1), "and must not leak into the shared _G");
    }

    @Test
    void preloadTakesPriorityOverFiles() {
        writeFile("lib/override.lua", "return 'from file'");

        LuaRomTestSupport.Capture cap = run(
            "local r, p = cc.require.make({}, 'lib')\n" + "p.preload.override = function() return 'from preload' end\n"
                + "_capture(r('override'))");
        assertEquals("from preload", cap.string(0), "package.preload is consulted first");
    }

    @Test
    void missingModuleRaisesWithTheTriedPaths() {
        LuaRomTestSupport.Capture cap = run(
            "local r, p = cc.require.make({}, 'lib')\n" + "local ok, err = pcall(r, 'absent')\n"
                + "_capture(ok, tostring(err))");
        assertFalse(cap.bool(0));
        String err = cap.string(1);
        assertTrue(err.contains("module 'absent' not found"), "unexpected message: " + err);
        assertTrue(err.contains("lib/absent"), "the message should list tried paths: " + err);
    }

    @Test
    void circularRequiresAreReported() {
        // a requires b, b requires a: the second load of a must be reported, not recursed.
        writeFile("lib/a.lua", "require('b')\nreturn 'a'");
        writeFile("lib/b.lua", "require('a')\nreturn 'b'");

        // Modules load with the given env and no _G fallback, so the shell puts require into
        // that env before anything runs; mirror that here.
        LuaRomTestSupport.Capture cap = run(
            "local env = {}\n" + "local r, p = cc.require.make(env, 'lib')\n"
                + "env.require = r\n"
                + "local ok, err = pcall(r, 'a')\n"
                + "_capture(ok, tostring(err))");
        assertFalse(cap.bool(0), "a require cycle must raise rather than loop");
        assertTrue(
            cap.string(1)
                .contains("loop or previous error"),
            "unexpected message: " + cap.string(1));
    }

    @Test
    void failedModulesAreNotLeftCached() {
        writeFile("lib/boom.lua", "error('kaboom')");

        // A load failure leaves the sentinel in package.loaded (upstream behaviour), so a
        // retry reports "loop or previous error" instead of silently reloading.
        LuaRomTestSupport.Capture cap = run(
            "local r, p = cc.require.make({}, 'lib')\n" + "local ok = pcall(r, 'boom')\n"
                + "local ok2, err2 = pcall(r, 'boom')\n"
                + "_capture(ok, ok2, tostring(err2))");
        assertFalse(cap.bool(0), "the module error must propagate");
        assertFalse(cap.bool(1), "a retry must fail rather than reload");
        assertTrue(
            cap.string(2)
                .contains("loop or previous error"),
            "the retry should explain itself, got: " + cap.at(2));
    }

    // -------------------------------------------------------------------------
    // package surface
    // -------------------------------------------------------------------------

    @Test
    void packageExposesTheStandardSurface() {
        LuaRomTestSupport.Capture cap = run(
            "local r, p = cc.require.make({}, '')\n"
                + "_capture(type(p.path), type(p.config), type(p.preload), type(p.loaders), type(p.loaded), #p.loaders)");
        assertEquals("string", cap.string(0));
        assertEquals("string", cap.string(1));
        assertEquals("table", cap.string(2));
        assertEquals("table", cap.string(3));
        assertEquals("table", cap.string(4));
        assertEquals(2.0, cap.number(5), "preload and file loaders");
    }

    @Test
    void packagePathCoversRomModules() {
        LuaRomTestSupport.Capture cap = run(
            "local r, p = cc.require.make({}, '')\n" + "_capture(p.path:find('/rom/modules/main/%?', 1) ~= nil)");
        assertTrue(cap.bool(0), "the default path must include the ROM module directory");
    }

    @Test
    void makeRejectsBadArguments() {
        LuaRomTestSupport.Capture cap = run(
            "local a = pcall(cc.require.make, nil, '/')\n" + "local b = pcall(cc.require.make, {}, nil)\n"
                + "_capture(a, b)");
        assertFalse(cap.bool(0), "env must be a table");
        assertFalse(cap.bool(1), "dir must be a string");
    }

    @Test
    void moduleSourceLoads() {
        assertNotNull(prelude);
        assertTrue(prelude.contains("make_package"));
    }
}
