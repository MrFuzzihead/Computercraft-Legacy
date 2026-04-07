package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.function.VarArgFunction;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.core.lua.lib.cobalt.CobaltConverter;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * Unit tests for the {@code shell} program ({@code rom/programs/shell}).
 *
 * <p>
 * The shell source is loaded up to (but not including) the {@code local tArgs = { ... }}
 * marker so that all function definitions are available without starting the
 * interactive REPL. A Lua preamble mocks {@code term}, {@code fs}, and
 * {@code os.run} so that every test runs headlessly without Minecraft.
 * </p>
 *
 * <h2>Coverage</h2>
 * <ul>
 *   <li>{@code shell.execute} — new CC:Tweaked method (1.87.0)</li>
 *   <li>{@code shell.run} — now delegates to execute; prints errors</li>
 *   <li>{@code shell.resolveProgram} — {@code .lua} extension fallback</li>
 *   <li>Per-program environment isolation (CC:Tweaked 1.80pr1)</li>
 *   <li>{@code arg} table injection (CC:Tweaked 1.83.0)</li>
 *   <li>Remaining shell methods: {@code dir/setDir}, {@code path/setPath},
 *       {@code resolve}, {@code getRunningProgram}, {@code setAlias/clearAlias/aliases}</li>
 * </ul>
 */
class ShellAPITest {

    /**
     * Shell function definitions extracted from the live source, stopping just
     * before the {@code local tArgs = { ... }} interactive-loop entry point.
     */
    private static String shellDefs;

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @BeforeAll
    static void loadSource() throws IOException {
        String full = readResource("/assets/computercraft/lua/rom/programs/shell");
        int cutoff = full.indexOf("\nlocal tArgs = { ... }");
        assertTrue(cutoff > 0, "shell must contain 'local tArgs = { ... }' as the start of the interactive loop");
        shellDefs = full.substring(0, cutoff);
    }

    private static String readResource(String path) throws IOException {
        try (InputStream is = ShellAPITest.class.getResourceAsStream(path)) {
            assertNotNull(is, path + " must be on the test classpath");
            try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                return scanner.hasNext() ? scanner.next() : "";
            }
        }
    }

    // -------------------------------------------------------------------------
    // Test infrastructure
    // -------------------------------------------------------------------------

    static class ResultCapture {

        Object[] args;
    }

    private static CobaltMachine buildMachine(ResultCapture capture) {
        ComputerCraft.bigInteger = false;
        ComputerCraft.bitop = false;
        ComputerCraft.timeoutError = false;
        CobaltMachine machine = new CobaltMachine(null);
        injectCapture(machine, capture);
        return machine;
    }

    private static void injectCapture(CobaltMachine machine, ResultCapture capture) {
        try {
            Field f = CobaltMachine.class.getDeclaredField("globals");
            f.setAccessible(true);
            LuaTable globals = (LuaTable) f.get(machine);
            globals.rawset("_capture", new VarArgFunction() {

                @Override
                public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                    capture.args = CobaltConverter.toObjects(args, 1, false);
                    return org.squiddev.cobalt.Constants.NONE;
                }
            });
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to inject _capture", e);
        }
    }

    /**
     * Builds a Lua preamble that mocks all globals required by the shell:
     * <ul>
     *   <li>{@code term} — headless stubs for {@code isColour/isColor} and {@code current}</li>
     *   <li>{@code colours}/{@code colors} — numeric constants</li>
     *   <li>{@code fs} — in-memory file set; only paths in {@code mockFiles} exist</li>
     *   <li>{@code os.run} — records the last call ({@code _os_run_env},
     *       {@code _os_run_path}, {@code _os_run_args}) and returns
     *       {@code _os_run_result} (defaults to {@code true})</li>
     *   <li>{@code printError} — records the last message in {@code _printError_last}</li>
     *   <li>{@code table.unpack} polyfill for Lua 5.1 / Cobalt</li>
     * </ul>
     *
     * <p>The default shell PATH is {@code ".:/rom/programs"} (set when
     * {@code parentShell} is {@code nil}).  Tests that need files to be found
     * should place them at {@code "rom/programs/<name>"} or as a bare name for
     * the {@code "."} entry.</p>
     *
     * @param mockFiles file paths that will be treated as existing regular files
     */
    private static String buildPreamble(String... mockFiles) {
        StringBuilder sb = new StringBuilder();

        // Lua 5.1 / Cobalt polyfill
        sb.append("if not table.unpack then table.unpack = unpack end\n");

        // term stub — shell reads term.isColour() once during colour initialisation
        sb.append("term = {\n");
        sb.append("  isColour = function() return false end,\n");
        sb.append("  isColor  = function() return false end,\n");
        sb.append("  current  = function() return {} end,\n");
        sb.append("}\n");

        // colours/colors stubs (values only used for promptColour etc., not under test)
        sb.append("colours = { white=1, yellow=16, black=32768 }\n");
        sb.append("colors   = colours\n");

        // Mock filesystem — only paths listed in mockFiles exist
        sb.append("_mock_files = {");
        for (String file : mockFiles) {
            sb.append(" [\"").append(file).append("\"]=true,");
        }
        sb.append(" }\n");
        sb.append("fs = {}\n");

        // fs.combine: strip leading slash for absolute paths; join with "/" otherwise
        sb.append("function fs.combine(base, rel)\n");
        sb.append("  local first = rel:sub(1,1)\n");
        sb.append("  if first == '/' or first == '\\\\' then\n");
        sb.append("    return rel:gsub('^[/\\\\]+', '')\n");
        sb.append("  end\n");
        sb.append("  if base == '' or base == '.' then return rel end\n");
        sb.append("  return base .. '/' .. rel\n");
        sb.append("end\n");

        sb.append("function fs.exists(p)  return _mock_files[p] == true end\n");
        sb.append("function fs.isDir(p)   return false end\n");
        sb.append("function fs.getName(p) return p:match('[^/]+$') or p end\n");

        // os.run mock — records call details; returns _os_run_result (default true)
        sb.append("os = {}\n");
        sb.append("_os_run_env    = nil\n");
        sb.append("_os_run_path   = nil\n");
        sb.append("_os_run_args   = nil\n");
        sb.append("_os_run_result = true\n");
        sb.append("function os.run(env, path, ...)\n");
        sb.append("  _os_run_env  = env\n");
        sb.append("  _os_run_path = path\n");
        sb.append("  _os_run_args = { ... }\n");
        sb.append("  return _os_run_result\n");
        sb.append("end\n");

        // printError stub
        sb.append("_printError_last = nil\n");
        sb.append("function printError(msg) _printError_last = msg end\n");

        return sb.toString();
    }

    /**
     * Runs {@code testLua} with the shell definitions pre-loaded and the mock
     * preamble injected.  The combined chunk is:
     * {@code preamble + shellDefs + testLua}.
     */
    private static void run(CobaltMachine machine, String testLua, String... mockFiles) {
        String combined = buildPreamble(mockFiles) + "\n" + shellDefs + "\n" + testLua;
        machine.loadBios(new ByteArrayInputStream(combined.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 50 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    // =========================================================================
    // shell.execute — new method (CC:Tweaked 1.87.0)
    // =========================================================================

    @Test
    void executeIsAFunction() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(type(shell.execute))");
        assertNotNull(cap.args);
        assertEquals("function", cap.args[0]);
    }

    @Test
    void executeReturnsFalseWithReasonWhenProgramNotFound() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, err = shell.execute('__missing__')\n_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "execute must return false when program is not found");
        assertEquals("No such program", cap.args[1]);
    }

    @Test
    void executeDoesNotCallPrintErrorForMissingProgram() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "shell.execute('__missing__')\n_capture(_printError_last)");
        assertNotNull(cap.args);
        assertNull(cap.args[0], "execute must not call printError — caller is responsible for error display");
    }

    @Test
    void executeCallsOsRunWithNormalisedPath() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "shell.execute('/rom/programs/id')\n_capture(_os_run_path)",
            "rom/programs/id");
        assertNotNull(cap.args);
        assertEquals("rom/programs/id", cap.args[0], "leading slash must be stripped by fs.combine");
    }

    @Test
    void executeForwardsArgumentsToOsRun() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "shell.execute('/rom/programs/prog', 'alpha', 'beta')\n_capture(_os_run_args[1], _os_run_args[2])",
            "rom/programs/prog");
        assertNotNull(cap.args);
        assertEquals("alpha", cap.args[0]);
        assertEquals("beta", cap.args[1]);
    }

    @Test
    void executeReturnsTrueWhenOsRunSucceeds() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "_os_run_result = true\nlocal ok = shell.execute('/rom/programs/id')\n_capture(ok)",
            "rom/programs/id");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0]);
    }

    @Test
    void executeReturnsFalseWhenOsRunFails() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "_os_run_result = false\nlocal ok = shell.execute('/rom/programs/id')\n_capture(ok)",
            "rom/programs/id");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0]);
    }

    // =========================================================================
    // shell.run — changed (delegates to execute, prints errors)
    // =========================================================================

    @Test
    void runReturnsFalseForEmptyInput() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok = shell.run('')\n_capture(ok)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0]);
    }

    @Test
    void runReturnsFalseAndPrintsErrorForMissingProgram() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok = shell.run('__missing__')\n_capture(ok, _printError_last)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "run must return false when program not found");
        assertEquals("No such program", cap.args[1], "run must forward the error to printError");
    }

    @Test
    void runTokenizesSpaceSeparatedArguments() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "shell.run('prog alpha beta')\n_capture(_os_run_args[1], _os_run_args[2])",
            "rom/programs/prog");
        assertNotNull(cap.args);
        assertEquals("alpha", cap.args[0]);
        assertEquals("beta", cap.args[1]);
    }

    @Test
    void runHandlesQuotedArgument() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "shell.run('prog \"hello world\"')\n_capture(_os_run_args[1])",
            "rom/programs/prog");
        assertNotNull(cap.args);
        assertEquals("hello world", cap.args[0], "quoted string must be a single token");
    }

    @Test
    void runReturnsTrueWhenProgramSucceeds() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "_os_run_result = true\nlocal ok = shell.run('prog')\n_capture(ok)",
            "rom/programs/prog");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0]);
    }

    // =========================================================================
    // shell.resolveProgram — .lua extension fallback
    // =========================================================================

    @Test
    void resolveProgramReturnsNilForMissingProgram() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(shell.resolveProgram('__missing__'))");
        assertNotNull(cap.args);
        assertNull(cap.args[0]);
    }

    @Test
    void resolveProgramFindsFileOnPath() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "_capture(shell.resolveProgram('myprogram'))",
            "rom/programs/myprogram");
        assertNotNull(cap.args);
        assertEquals("rom/programs/myprogram", cap.args[0]);
    }

    @Test
    void resolveProgramFindsLuaExtensionFallback() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "_capture(shell.resolveProgram('myscript'))",
            "rom/programs/myscript.lua");
        assertNotNull(cap.args);
        assertEquals(
            "rom/programs/myscript.lua",
            cap.args[0],
            "resolveProgram must find 'myscript.lua' when bare 'myscript' does not exist");
    }

    @Test
    void resolveProgramPrefersBareNameOverLuaExtension() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "_capture(shell.resolveProgram('prog'))",
            "rom/programs/prog",
            "rom/programs/prog.lua");
        assertNotNull(cap.args);
        assertEquals(
            "rom/programs/prog",
            cap.args[0],
            "bare name must be preferred when both bare and .lua variant exist");
    }

    @Test
    void resolveProgramResolvesAbsolutePath() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "_capture(shell.resolveProgram('/rom/programs/edit'))",
            "rom/programs/edit");
        assertNotNull(cap.args);
        assertEquals("rom/programs/edit", cap.args[0]);
    }

    @Test
    void resolveProgramReturnsNilForMissingAbsolutePath() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(shell.resolveProgram('/does/not/exist'))");
        assertNotNull(cap.args);
        assertNull(cap.args[0]);
    }

    @Test
    void resolveProgramSubstitutesAlias() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "shell.setAlias('ll', 'list')\n_capture(shell.resolveProgram('ll'))",
            "rom/programs/list");
        assertNotNull(cap.args);
        assertEquals("rom/programs/list", cap.args[0]);
    }

    // =========================================================================
    // Per-program environment isolation (CC:Tweaked 1.80pr1)
    // =========================================================================

    @Test
    void eachExecuteCallReceivesADistinctEnvTable() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "local env1, env2\n"
                + "local call = 0\n"
                + "function os.run(env, path, ...)\n"
                + "  call = call + 1\n"
                + "  if call == 1 then env1 = env else env2 = env end\n"
                + "  return true\n"
                + "end\n"
                + "shell.execute('/rom/programs/prog')\n"
                + "shell.execute('/rom/programs/prog')\n"
                + "_capture(env1 == env2)\n",
            "rom/programs/prog");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "each execute call must produce a fresh env table");
    }

    @Test
    void globalsMutatedInOneRunDoNotBleedToNextRun() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "local leaked\n"
                + "local call = 0\n"
                + "function os.run(env, path, ...)\n"
                + "  call = call + 1\n"
                + "  if call == 1 then\n"
                + "    env.__sentinel__ = 'leaked'\n"
                + "  else\n"
                + "    leaked = env.__sentinel__\n"
                + "  end\n"
                + "  return true\n"
                + "end\n"
                + "shell.execute('/rom/programs/prog')\n"
                + "shell.execute('/rom/programs/prog')\n"
                + "_capture(leaked)\n",
            "rom/programs/prog");
        assertNotNull(cap.args);
        assertNull(cap.args[0], "a global written into one run's env must not appear in the next run's env");
    }

    @Test
    void perRunEnvInheritsShellFromBaseEnv() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "function os.run(env, path, ...) _capture(type(env.shell)) end\n"
                + "shell.execute('/rom/programs/prog')\n",
            "rom/programs/prog");
        assertNotNull(cap.args);
        assertEquals("table", cap.args[0], "per-run env must expose 'shell' via __index into the base tEnv");
    }

    // =========================================================================
    // arg table injection (CC:Tweaked 1.83.0)
    // =========================================================================

    @Test
    void argTableIsInjectedIntoPerRunEnv() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "function os.run(env, path, ...) _capture(type(env.arg)) end\n"
                + "shell.execute('/rom/programs/prog')\n",
            "rom/programs/prog");
        assertNotNull(cap.args);
        assertEquals("table", cap.args[0], "env.arg must be a table");
    }

    @Test
    void argZeroIsResolvedProgramPath() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "function os.run(env, path, ...) _capture(env.arg[0]) end\n"
                + "shell.execute('/rom/programs/prog')\n",
            "rom/programs/prog");
        assertNotNull(cap.args);
        assertEquals("rom/programs/prog", cap.args[0], "arg[0] must be the resolved program path");
    }

    @Test
    void argOneIsFirstArgument() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "function os.run(env, path, ...) _capture(env.arg[1]) end\n"
                + "shell.execute('/rom/programs/prog', 'myarg')\n",
            "rom/programs/prog");
        assertNotNull(cap.args);
        assertEquals("myarg", cap.args[0], "arg[1] must be the first argument passed to execute");
    }

    @Test
    void argOneIsNilWhenCalledWithNoArguments() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "function os.run(env, path, ...) _capture(env.arg[1]) end\n"
                + "shell.execute('/rom/programs/prog')\n",
            "rom/programs/prog");
        assertNotNull(cap.args);
        assertNull(cap.args[0], "arg[1] must be nil when program is called with no arguments");
    }

    // =========================================================================
    // shell.dir / shell.setDir
    // =========================================================================

    @Test
    void dirReturnsEmptyStringByDefault() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(shell.dir())");
        assertNotNull(cap.args);
        assertEquals("", cap.args[0]);
    }

    @Test
    void setDirChangesCurrentDirectory() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "shell.setDir('mydir')\n_capture(shell.dir())");
        assertNotNull(cap.args);
        assertEquals("mydir", cap.args[0]);
    }

    // =========================================================================
    // shell.path / shell.setPath
    // =========================================================================

    @Test
    void pathReturnsDefaultPath() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(shell.path())");
        assertNotNull(cap.args);
        assertEquals(".:/rom/programs", cap.args[0]);
    }

    @Test
    void setPathChangesPath() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "shell.setPath('/custom')\n_capture(shell.path())");
        assertNotNull(cap.args);
        assertEquals("/custom", cap.args[0]);
    }

    // =========================================================================
    // shell.resolve
    // =========================================================================

    @Test
    void resolveAbsolutePathStripsLeadingSlash() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(shell.resolve('/rom/programs/edit'))");
        assertNotNull(cap.args);
        // fs.combine("", "/rom/programs/edit") strips the leading slash
        assertEquals("rom/programs/edit", cap.args[0]);
    }

    @Test
    void resolveRelativePathPrependsCurrentDir() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "shell.setDir('mydir')\n_capture(shell.resolve('file'))");
        assertNotNull(cap.args);
        assertEquals("mydir/file", cap.args[0]);
    }

    // =========================================================================
    // shell.getRunningProgram
    // =========================================================================

    @Test
    void getRunningProgramReturnsNilInitially() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(shell.getRunningProgram())");
        assertNotNull(cap.args);
        assertNull(cap.args[0]);
    }

    @Test
    void getRunningProgramReturnsPathDuringExecution() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "function os.run(env, path, ...)\n"
                + "  _capture(shell.getRunningProgram())\n"
                + "  return true\n"
                + "end\n"
                + "shell.execute('/rom/programs/prog')\n",
            "rom/programs/prog");
        assertNotNull(cap.args);
        assertEquals(
            "rom/programs/prog",
            cap.args[0],
            "getRunningProgram must return the current program path during os.run");
    }

    // =========================================================================
    // shell.setAlias / shell.clearAlias / shell.aliases
    // =========================================================================

    @Test
    void setAliasIsReflectedInAliases() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "shell.setAlias('ll', 'list')\n_capture(shell.aliases()['ll'])");
        assertNotNull(cap.args);
        assertEquals("list", cap.args[0]);
    }

    @Test
    void clearAliasRemovesAlias() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "shell.setAlias('ll', 'list')\n"
                + "shell.clearAlias('ll')\n"
                + "_capture(shell.aliases()['ll'])");
        assertNotNull(cap.args);
        assertNull(cap.args[0]);
    }

    @Test
    void aliasesReturnsCopyThatDoesNotAffectInternalTable() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap),
            "shell.setAlias('a', 'b')\n"
                + "local copy = shell.aliases()\n"
                + "copy['a'] = 'mutated'\n"
                + "_capture(shell.aliases()['a'])");
        assertNotNull(cap.args);
        assertEquals("b", cap.args[0], "mutating the returned copy must not affect the internal aliases table");
    }
}

