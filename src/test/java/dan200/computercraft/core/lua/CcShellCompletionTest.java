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
 * Tests for the {@code cc.shell.completion} Lua module
 * ({@code rom/modules/main/cc/shell/completion.lua}).
 *
 * <p>
 * Each test builds a minimal Lua environment containing:
 * <ul>
 * <li>A stub {@code fs} global with a recording {@code fs.complete} that
 * captures its four arguments and returns a configurable list.</li>
 * <li>A stub {@code shell} table whose {@code dir()} returns
 * {@code "/test_dir"}.</li>
 * <li>A minimal {@code require} shim (no {@code cc.expect} is needed because
 * the three functions delegate entirely to {@code fs.complete}).</li>
 * <li>The module loaded as the global {@code sc}.</li>
 * </ul>
 * </p>
 *
 * <p>
 * The parameter conventions for the legacy {@code fs.complete} implemented in
 * {@code bios.lua} are:
 * <ol>
 * <li>{@code path} — the partial text to complete</li>
 * <li>{@code location} — the base directory</li>
 * <li>{@code bIncludeFiles} — whether regular files appear in results</li>
 * <li>{@code bIncludeDirs} — whether directories also appear <em>without</em>
 * a trailing {@code "/"} (they always appear <em>with</em> one)</li>
 * </ol>
 * Therefore:
 * <ul>
 * <li>{@code file} passes {@code (true,  false)}</li>
 * <li>{@code dir} passes {@code (false, true)}</li>
 * <li>{@code dirOrFile} passes {@code (true,  true)}</li>
 * </ul>
 * </p>
 */
class CcShellCompletionTest {

    private static String expectSource;
    private static String completionSource;

    // ── Setup ──────────────────────────────────────────────────────────────────

    @BeforeAll
    static void loadSources() throws IOException {
        expectSource = readResource("/assets/computercraft/lua/rom/modules/main/cc/expect.lua");
        completionSource = readResource("/assets/computercraft/lua/rom/modules/main/cc/shell/completion.lua");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream is = CcShellCompletionTest.class.getResourceAsStream(path)) {
            assertNotNull(is, path + " must be on the test classpath");
            try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                return scanner.hasNext() ? scanner.next() : "";
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

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
     * Builds the require/package preamble plus the {@code cc.shell.completion}
     * module, and installs a recording {@code fs} global and a mock {@code shell}.
     *
     * <p>
     * The stub {@code fs.complete} stores its four arguments in
     * {@code _c_path}, {@code _c_loc}, {@code _c_if}, {@code _c_id} and returns
     * the two-element table {@code {"aaa", "bbb"}}.
     * </p>
     *
     * <p>
     * The mock {@code shell} additionally exposes {@code completeProgram},
     * {@code resolveProgram}, and {@code getCompletionInfo} for the
     * {@code program}/{@code programWithArgs} tests.
     * </p>
     */
    private String buildPreamble() {
        return
        // ── fs mock ──────────────────────────────────────────────────────
        "_c_path, _c_loc, _c_if, _c_id = nil, nil, nil, nil\n" + "fs = {}\n"
            + "function fs.complete(path, loc, inc_files, inc_dirs)\n"
            + "  _c_path = path\n"
            + "  _c_loc  = loc\n"
            + "  _c_if   = inc_files\n"
            + "  _c_id   = inc_dirs\n"
            + "  return {'aaa', 'bbb'}\n"
            + "end\n"
            // ── help mock (must be global before module loads) ────────────
            + "help = {}\n"
            + "function help.completeTopic(text)\n"
            + "  _h_text = text\n"
            + "  return {'topic1', 'topic2'}\n"
            + "end\n"
            // ── shell mock ────────────────────────────────────────────────
            + "_cp_text = nil\n"
            + "_rp_name = nil\n"
            + "_sub_i, _sub_t, _sub_p = nil, nil, nil\n"
            + "local mock_shell = {\n"
            + "  dir = function() return '/test_dir' end,\n"
            + "  completeProgram = function(text)\n"
            + "    _cp_text = text\n"
            + "    return {'prog1', 'prog2'}\n"
            + "  end,\n"
            + "  resolveProgram = function(name)\n"
            + "    _rp_name = name\n"
            + "    if name == 'edit' then return '/rom/programs/edit' end\n"
            + "    return nil\n"
            + "  end,\n"
            + "  getCompletionInfo = function()\n"
            + "    return {\n"
            + "      ['/rom/programs/edit'] = {\n"
            + "        fnComplete = function(s, i, t, p)\n"
            + "          _sub_i = i; _sub_t = t; _sub_p = p\n"
            + "          return {'sub_result'}\n"
            + "        end\n"
            + "      }\n"
            + "    }\n"
            + "  end,\n"
            + "}\n"
            // ── require shim ──────────────────────────────────────────────────────────
            + "package = { loaded = {} }\n"
            // Pre-load cc.expect (real implementation, no Minecraft globals needed).
            + "do\n"
            + "  local fn, err = loadstring([=[\n"
            + expectSource
            + "\n]=])\n"
            + "  if not fn then error('cc.expect load failed: ' .. tostring(err)) end\n"
            + "  package.loaded['cc.expect'] = fn()\n"
            + "end\n"
            // Pre-load cc.completion mock (captures calls for assertion).
            + "_comp_choice_text, _comp_choice_choices, _comp_choice_add_space = nil, nil, nil\n"
            + "_comp_periph_text, _comp_side_text = nil, nil\n"
            + "_comp_setting_text, _comp_command_text = nil, nil\n"
            + "package.loaded['cc.completion'] = {\n"
            + "  choice = function(text, choices, add_space)\n"
            + "    _comp_choice_text = text\n"
            + "    _comp_choice_choices = choices\n"
            + "    _comp_choice_add_space = add_space\n"
            + "    return {'choice_r'}\n"
            + "  end,\n"
            + "  peripheral = function(text)\n"
            + "    _comp_periph_text = text; return {'periph_r'}\n"
            + "  end,\n"
            + "  side = function(text)\n"
            + "    _comp_side_text = text; return {'side_r'}\n"
            + "  end,\n"
            + "  setting = function(text)\n"
            + "    _comp_setting_text = text; return {'setting_r'}\n"
            + "  end,\n"
            + "  command = function(text)\n"
            + "    _comp_command_text = text; return {'command_r'}\n"
            + "  end,\n"
            + "}\n"
            + "function require(name)\n"
            + "  if package.loaded[name] ~= nil then return package.loaded[name] end\n"
            + "  error('module \\'' .. name .. '\\' not found')\n"
            + "end\n"
            // ── load cc.shell.completion ──────────────────────────────────
            + "do\n"
            + "  local fn, err = loadstring([=[\n"
            + completionSource
            + "\n]=])\n"
            + "  if not fn then error('cc.shell.completion load failed: ' .. tostring(err)) end\n"
            + "  sc = fn()\n"
            + "end\n"
            // ── expose mock_shell to test bodies ──────────────────────────
            + "local shell_ref = mock_shell\n";
    }

    private static void run(CobaltMachine machine, String preamble, String testLua) {
        String combined = preamble + "\n" + testLua;
        machine.loadBios(new ByteArrayInputStream(combined.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 50 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    // ── Module structure ───────────────────────────────────────────────────────

    @Test
    void testModuleExposesFileFunction() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(type(sc.file))");
        assertEquals("function", cap.args[0]);
    }

    @Test
    void testModuleExposesDirFunction() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(type(sc.dir))");
        assertEquals("function", cap.args[0]);
    }

    @Test
    void testModuleExposesDirOrFileFunction() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(type(sc.dirOrFile))");
        assertEquals("function", cap.args[0]);
    }

    // ── file() ─────────────────────────────────────────────────────────────────

    @Test
    void testFilePassesTextAsPath() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "sc.file(shell_ref, 1, 'partial', {})\n" + "_capture(_c_path)");
        assertEquals("partial", cap.args[0]);
    }

    @Test
    void testFilePassesShellDirAsLocation() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "sc.file(shell_ref, 1, 'x', {})\n" + "_capture(_c_loc)");
        assertEquals("/test_dir", cap.args[0]);
    }

    @Test
    void testFilePassesIncludeFilesTrue() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "sc.file(shell_ref, 1, '', {})\n" + "_capture(_c_if)");
        assertEquals(Boolean.TRUE, cap.args[0], "file() must pass include_files=true");
    }

    @Test
    void testFilePassesIncludeDirsFalse() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "sc.file(shell_ref, 1, '', {})\n" + "_capture(_c_id)");
        assertEquals(
            Boolean.FALSE,
            cap.args[0],
            "file() must pass include_dirs=false (dirs shown with '/' suffix only)");
    }

    @Test
    void testFileReturnsCompletionList() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local r = sc.file(shell_ref, 1, '', {})\n" + "_capture(#r, r[1], r[2])");
        assertEquals(2.0, cap.args[0], "result count");
        assertEquals("aaa", cap.args[1]);
        assertEquals("bbb", cap.args[2]);
    }

    @Test
    void testFileForwardsEmptyText() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "sc.file(shell_ref, 1, '', {})\n" + "_capture(_c_path)");
        assertEquals("", cap.args[0]);
    }

    // ── dir() ──────────────────────────────────────────────────────────────────

    @Test
    void testDirPassesTextAsPath() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "sc.dir(shell_ref, 1, 'ro', {})\n" + "_capture(_c_path)");
        assertEquals("ro", cap.args[0]);
    }

    @Test
    void testDirPassesShellDirAsLocation() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "sc.dir(shell_ref, 1, '', {})\n" + "_capture(_c_loc)");
        assertEquals("/test_dir", cap.args[0]);
    }

    @Test
    void testDirPassesIncludeFilesFalse() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "sc.dir(shell_ref, 1, '', {})\n" + "_capture(_c_if)");
        assertEquals(Boolean.FALSE, cap.args[0], "dir() must pass include_files=false");
    }

    @Test
    void testDirPassesIncludeDirsTrue() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "sc.dir(shell_ref, 1, '', {})\n" + "_capture(_c_id)");
        assertEquals(
            Boolean.TRUE,
            cap.args[0],
            "dir() must pass include_dirs=true (dirs appear with '/' and also as bare names)");
    }

    @Test
    void testDirReturnsCompletionList() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local r = sc.dir(shell_ref, 1, '', {})\n" + "_capture(#r, r[1], r[2])");
        assertEquals(2.0, cap.args[0]);
        assertEquals("aaa", cap.args[1]);
        assertEquals("bbb", cap.args[2]);
    }

    // ── dirOrFile() ────────────────────────────────────────────────────────────

    @Test
    void testDirOrFilePassesTextAsPath() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "sc.dirOrFile(shell_ref, 1, 'src', {})\n" + "_capture(_c_path)");
        assertEquals("src", cap.args[0]);
    }

    @Test
    void testDirOrFilePassesShellDirAsLocation() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "sc.dirOrFile(shell_ref, 1, '', {})\n" + "_capture(_c_loc)");
        assertEquals("/test_dir", cap.args[0]);
    }

    @Test
    void testDirOrFilePassesIncludeFilesTrue() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "sc.dirOrFile(shell_ref, 1, '', {})\n" + "_capture(_c_if)");
        assertEquals(Boolean.TRUE, cap.args[0], "dirOrFile() must pass include_files=true");
    }

    @Test
    void testDirOrFilePassesIncludeDirsTrue() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "sc.dirOrFile(shell_ref, 1, '', {})\n" + "_capture(_c_id)");
        assertEquals(Boolean.TRUE, cap.args[0], "dirOrFile() must pass include_dirs=true");
    }

    @Test
    void testDirOrFileReturnsCompletionList() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local r = sc.dirOrFile(shell_ref, 1, '', {})\n" + "_capture(#r, r[1], r[2])");
        assertEquals(2.0, cap.args[0]);
        assertEquals("aaa", cap.args[1]);
        assertEquals("bbb", cap.args[2]);
    }

    // ── Shell dir is used correctly ─────────────────────────────────────────────

    @Test
    void testDifferentShellDirIsForwarded() {
        ResultCapture cap = new ResultCapture();
        // Override mock_shell to return a different directory.
        run(
            buildMachine(cap),
            buildPreamble(),
            "local other_shell = { dir = function() return '/programs' end }\n"
                + "sc.file(other_shell, 1, 'test', {})\n"
                + "_capture(_c_loc)");
        assertEquals("/programs", cap.args[0], "shell.dir() result must be forwarded verbatim to fs.complete");
    }

    @Test
    void testAllThreeFunctionsRespectShellDir() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local s = { dir = function() return '/home' end }\n" + "sc.file(s, 1, '', {}); local l1 = _c_loc\n"
                + "sc.dir(s, 1, '', {}); local l2 = _c_loc\n"
                + "sc.dirOrFile(s, 1, '', {}); local l3 = _c_loc\n"
                + "_capture(l1, l2, l3)");
        assertEquals("/home", cap.args[0], "file location");
        assertEquals("/home", cap.args[1], "dir location");
        assertEquals("/home", cap.args[2], "dirOrFile location");
    }

    // ── file/dir/dirOrFile use distinct fs.complete arg combinations ───────────

    @Test
    void testThreeFunctionsUseDistinctArgs() {
        ResultCapture cap = new ResultCapture();
        // Collect (include_files, include_dirs) for all three, confirm they differ.
        run(
            buildMachine(cap),
            buildPreamble(),
            "sc.file(shell_ref, 1, '', {})\n" + "local f_if, f_id = _c_if, _c_id\n"
                + "sc.dir(shell_ref, 1, '', {})\n"
                + "local d_if, d_id = _c_if, _c_id\n"
                + "sc.dirOrFile(shell_ref, 1, '', {})\n"
                + "local df_if, df_id = _c_if, _c_id\n"
                + "_capture(f_if, f_id, d_if, d_id, df_if, df_id)");
        // file: (true, false)
        assertEquals(Boolean.TRUE, cap.args[0], "file include_files");
        assertEquals(Boolean.FALSE, cap.args[1], "file include_dirs");
        // dir: (false, true)
        assertEquals(Boolean.FALSE, cap.args[2], "dir include_files");
        assertEquals(Boolean.TRUE, cap.args[3], "dir include_dirs");
        // dirOrFile: (true, true)
        assertEquals(Boolean.TRUE, cap.args[4], "dirOrFile include_files");
        assertEquals(Boolean.TRUE, cap.args[5], "dirOrFile include_dirs");
    }

    // ── program() ──────────────────────────────────────────────────────────────

    @Test
    void testModuleExposesProgramFunction() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(type(sc.program))");
        assertEquals("function", cap.args[0]);
    }

    @Test
    void testProgramCallsCompleteProgramAtIndex1() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "sc.program(shell_ref, 1, 'ed', {})\n" + "_capture(_cp_text)");
        assertEquals("ed", cap.args[0]);
    }

    @Test
    void testProgramReturnsResultFromCompleteProgram() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local r = sc.program(shell_ref, 1, 'p', {})\n" + "_capture(#r, r[1], r[2])");
        assertEquals(2.0, cap.args[0], "count");
        assertEquals("prog1", cap.args[1]);
        assertEquals("prog2", cap.args[2]);
    }

    @Test
    void testProgramReturnsNilAtIndex2() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local r = sc.program(shell_ref, 2, 'anything', {'wrapper', 'edit'})\n" + "_capture(r)");
        assertNull(cap.args[0], "program() must return nil for index != 1");
    }

    @Test
    void testProgramForwardsEmptyText() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "sc.program(shell_ref, 1, '', {})\n" + "_capture(_cp_text)");
        assertEquals("", cap.args[0]);
    }

    // ── programWithArgs() ──────────────────────────────────────────────────────

    @Test
    void testModuleExposesProgramWithArgsFunction() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(type(sc.programWithArgs))");
        assertEquals("function", cap.args[0]);
    }

    @Test
    void testProgramWithArgsCompletesAtIndex1() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local r = sc.programWithArgs(shell_ref, 1, 'ed', {'wrapper'})\n" + "_capture(_cp_text, #r)");
        assertEquals("ed", cap.args[0], "text forwarded to completeProgram");
        assertEquals(2.0, cap.args[1], "result count");
    }

    @Test
    void testProgramWithArgsDelegatesToSubProgramCompletion() {
        ResultCapture cap = new ResultCapture();
        // previous = {"wrapper", "edit"}, index = 2, text = "myfile"
        // Expected: resolve "edit" → "/rom/programs/edit", call its fnComplete
        // with (shell, 1, "myfile", {"edit"})
        run(
            buildMachine(cap),
            buildPreamble(),
            "local r = sc.programWithArgs(shell_ref, 2, 'myfile', {'wrapper', 'edit'})\n"
                + "_capture(#r, r[1], _sub_i, _sub_t)");
        assertEquals(1.0, cap.args[0], "result count");
        assertEquals("sub_result", cap.args[1], "delegated result");
        assertEquals(1.0, cap.args[2], "sub index should be index-1 = 1");
        assertEquals("myfile", cap.args[3], "text forwarded");
    }

    @Test
    void testProgramWithArgsDelegatesShiftedPrevious() {
        ResultCapture cap = new ResultCapture();
        // previous = {"wrapper", "edit", "arg1"}, index = 3
        // Sub-program should see previous = {"edit", "arg1"} and index = 2
        run(
            buildMachine(cap),
            buildPreamble(),
            "sc.programWithArgs(shell_ref, 3, 'x', {'wrapper', 'edit', 'arg1'})\n"
                + "_capture(_sub_i, _sub_p[1], _sub_p[2])");
        assertEquals(2.0, cap.args[0], "sub index = 3-1 = 2");
        assertEquals("edit", cap.args[1], "sub previous[1] = nested program name");
        assertEquals("arg1", cap.args[2], "sub previous[2] = first arg to nested program");
    }

    @Test
    void testProgramWithArgsResolvesSubProgramName() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "sc.programWithArgs(shell_ref, 2, '', {'wrapper', 'edit'})\n" + "_capture(_rp_name)");
        assertEquals("edit", cap.args[0], "resolveProgram called with sub-program name");
    }

    @Test
    void testProgramWithArgsReturnsNilForUnknownSubProgram() {
        ResultCapture cap = new ResultCapture();
        // "unknown" resolves to nil → should return nil gracefully
        run(
            buildMachine(cap),
            buildPreamble(),
            "local r = sc.programWithArgs(shell_ref, 2, 'x', {'wrapper', 'unknown'})\n" + "_capture(r)");
        assertNull(cap.args[0], "nil for unresolvable sub-program");
    }

    @Test
    void testProgramWithArgsReturnsNilWhenNoPrevious2() {
        ResultCapture cap = new ResultCapture();
        // index = 2, previous has no [2]
        run(
            buildMachine(cap),
            buildPreamble(),
            "local r = sc.programWithArgs(shell_ref, 2, 'x', {'wrapper'})\n" + "_capture(r)");
        assertNull(cap.args[0], "nil when previous[2] is absent");
    }

    @Test
    void testProgramWithArgsReturnsNilWhenNoCompletionRegistered() {
        ResultCapture cap = new ResultCapture();
        // "list" resolves to nil (not "edit"), so no completion info
        run(
            buildMachine(cap),
            buildPreamble(),
            "local r = sc.programWithArgs(shell_ref, 2, 'x', {'wrapper', 'list'})\n" + "_capture(r)");
        assertNull(cap.args[0], "nil when sub-program has no registered completion");
    }

    // ── help() ─────────────────────────────────────────────────────────────────

    @Test
    void testModuleExposesHelpFunction() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(type(sc.help))");
        assertEquals("function", cap.args[0]);
    }

    @Test
    void testHelpCallsCompleteTopicAtIndex1() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "sc.help(shell_ref, 1, 'fs', {})\n" + "_capture(_h_text)");
        assertEquals("fs", cap.args[0], "text forwarded to help.completeTopic");
    }

    @Test
    void testHelpReturnsResultFromCompleteTopic() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local r = sc.help(shell_ref, 1, '', {})\n" + "_capture(#r, r[1], r[2])");
        assertEquals(2.0, cap.args[0], "count");
        assertEquals("topic1", cap.args[1]);
        assertEquals("topic2", cap.args[2]);
    }

    @Test
    void testHelpReturnsNilAtIndex2() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local r = sc.help(shell_ref, 2, 'anything', {'help'})\n" + "_capture(r)");
        assertNull(cap.args[0], "help() must return nil for index != 1");
    }

    @Test
    void testHelpDoesNotIndexLocalFunctionAsTable() {
        ResultCapture cap = new ResultCapture();
        // If the naming collision is not resolved, calling help() would error with
        // "attempt to index a function value". Verify it runs without error.
        run(
            buildMachine(cap),
            buildPreamble(),
            "local ok, err = pcall(sc.help, shell_ref, 1, 'os', {})\n" + "_capture(ok, err)");
        assertEquals(Boolean.TRUE, cap.args[0], "help() must not error (naming collision guard); err=" + cap.args[1]);
    }

    // ── choice() factory ───────────────────────────────────────────────────────

    @Test
    void testModuleExposesChoiceFunction() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(type(sc.choice))");
        assertEquals("function", cap.args[0]);
    }

    @Test
    void testChoiceReturnsFunction() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(type(sc.choice({'a','b'})))");
        assertEquals("function", cap.args[0], "choice factory must return a function");
    }

    @Test
    void testChoiceReturnedFunctionDelegatesToCcCompletion() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local fn = sc.choice({'yes','no'})\n" + "local r = fn(shell_ref, 1, 'y', {})\n"
                + "_capture(_comp_choice_text, _comp_choice_choices[1], _comp_choice_choices[2], #r)");
        assertEquals("y", cap.args[0], "text forwarded");
        assertEquals("yes", cap.args[1], "choices[1]");
        assertEquals("no", cap.args[2], "choices[2]");
        assertEquals(1.0, cap.args[3], "result count");
    }

    @Test
    void testChoiceForwardsAddSpace() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local fn = sc.choice({'x'}, true)\n" + "fn(shell_ref, 1, '', {})\n" + "_capture(_comp_choice_add_space)");
        assertEquals(Boolean.TRUE, cap.args[0], "add_space forwarded");
    }

    @Test
    void testChoiceTypeErrorOnNonTableChoices() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local ok, err = pcall(sc.choice, 'not_a_table')\n" + "_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0]);
        assertTrue(
            ((String) cap.args[1]).contains("expected"),
            "Error must mention expected type; got: " + cap.args[1]);
    }

    // ── peripheral() ──────────────────────────────────────────────────────────

    @Test
    void testModuleExposesPeripheralFunction() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(type(sc.peripheral))");
        assertEquals("function", cap.args[0]);
    }

    @Test
    void testPeripheralDelegatesToCcCompletion() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local r = sc.peripheral(shell_ref, 1, 'mon', {})\n" + "_capture(_comp_periph_text, r[1])");
        assertEquals("mon", cap.args[0], "text forwarded");
        assertEquals("periph_r", cap.args[1], "result from mock");
    }

    @Test
    void testPeripheralReturnsResultAtAnyIndex() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local r = sc.peripheral(shell_ref, 3, 'mon', {})\n" + "_capture(r ~= nil)");
        assertEquals(Boolean.TRUE, cap.args[0], "peripheral does not guard on index; always delegates");
    }

    // ── side() ─────────────────────────────────────────────────────────────────

    @Test
    void testModuleExposesSideFunction() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(type(sc.side))");
        assertEquals("function", cap.args[0]);
    }

    @Test
    void testSideDelegatesToCcCompletion() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local r = sc.side(shell_ref, 1, 'to', {})\n" + "_capture(_comp_side_text, r[1])");
        assertEquals("to", cap.args[0], "text forwarded");
        assertEquals("side_r", cap.args[1], "result from mock");
    }

    // ── setting() ──────────────────────────────────────────────────────────────

    @Test
    void testModuleExposesSettingFunction() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(type(sc.setting))");
        assertEquals("function", cap.args[0]);
    }

    @Test
    void testSettingDelegatesToCcCompletion() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local r = sc.setting(shell_ref, 1, 'shell', {})\n" + "_capture(_comp_setting_text, r[1])");
        assertEquals("shell", cap.args[0], "text forwarded");
        assertEquals("setting_r", cap.args[1], "result from mock");
    }

    // ── command() ──────────────────────────────────────────────────────────────

    @Test
    void testModuleExposesCommandFunction() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(type(sc.command))");
        assertEquals("function", cap.args[0]);
    }

    @Test
    void testCommandDelegatesToCcCompletion() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local r = sc.command(shell_ref, 1, 'say', {})\n" + "_capture(_comp_command_text, r[1])");
        assertEquals("say", cap.args[0], "text forwarded");
        assertEquals("command_r", cap.args[1], "result from mock");
    }

    // ── build() ────────────────────────────────────────────────────────────────

    @Test
    void testModuleExposesBuildFunction() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(type(sc.build))");
        assertEquals("function", cap.args[0]);
    }

    @Test
    void testBuildReturnsFunction() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(type(sc.build(sc.file)))");
        assertEquals("function", cap.args[0]);
    }

    @Test
    void testBuildDispatchesByIndex() {
        ResultCapture cap = new ResultCapture();
        // Spec 1 = peripheral, spec 2 = side. Completing arg 1 should hit peripheral,
        // arg 2 should hit side.
        run(
            buildMachine(cap),
            buildPreamble(),
            "local fn = sc.build(sc.peripheral, sc.side)\n" + "fn(shell_ref, 1, 'mon', {})\n"
                + "local t1 = _comp_periph_text\n"
                + "fn(shell_ref, 2, 'top', {})\n"
                + "local t2 = _comp_side_text\n"
                + "_capture(t1, t2)");
        assertEquals("mon", cap.args[0], "index 1 → peripheral");
        assertEquals("top", cap.args[1], "index 2 → side");
    }

    @Test
    void testBuildNilSpecReturnsNil() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local fn = sc.build(nil, sc.file)\n" + "_capture(fn(shell_ref, 1, '', {}) == nil)");
        assertEquals(Boolean.TRUE, cap.args[0], "nil spec at index 1 must yield nil");
    }

    @Test
    void testBuildIndexOutOfRangeReturnsNil() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local fn = sc.build(sc.file)\n" + "_capture(fn(shell_ref, 2, '', {}) == nil)");
        assertEquals(Boolean.TRUE, cap.args[0], "index beyond spec count must yield nil");
    }

    @Test
    void testBuildNormalizesIndexToOne() {
        ResultCapture cap = new ResultCapture();
        // sc.program checks `if index == 1`; build must normalize before calling it.
        run(
            buildMachine(cap),
            buildPreamble(),
            "local fn = sc.build(nil, sc.program)\n" + "local r = fn(shell_ref, 2, 'ed', {})\n"
                + "_capture(_cp_text, r ~= nil)");
        assertEquals("ed", cap.args[0], "text forwarded to completeProgram");
        assertEquals(Boolean.TRUE, cap.args[1], "program returned a result (index normalized to 1)");
    }

    @Test
    void testBuildTableFactorySpec() {
        ResultCapture cap = new ResultCapture();
        // { sc.choice, {"yes","no"} } → build calls choice({"yes","no"}) at build time,
        // stores the returned fn, then invokes it with (shell, 1, text, previous).
        run(
            buildMachine(cap),
            buildPreamble(),
            "local fn = sc.build({ sc.choice, {'yes','no'} })\n" + "fn(shell_ref, 1, 'y', {})\n"
                + "_capture(_comp_choice_text, _comp_choice_choices[1])");
        assertEquals("y", cap.args[0], "text forwarded through factory spec");
        assertEquals("yes", cap.args[1], "choices passed to cc.completion.choice");
    }

    @Test
    void testBuildTableDirectFunctionSpec() {
        ResultCapture cap = new ResultCapture();
        // { sc.file } with no extra args → fn is used directly (no factory call).
        run(
            buildMachine(cap),
            buildPreamble(),
            "local fn = sc.build({ sc.peripheral })\n" + "fn(shell_ref, 1, 'disk', {})\n"
                + "_capture(_comp_periph_text)");
        assertEquals("disk", cap.args[0], "text forwarded through single-element table spec");
    }

    @Test
    void testBuildMultipleSpecsArePre_Resolved() {
        ResultCapture cap = new ResultCapture();
        // Calling build twice must produce independent functions.
        run(
            buildMachine(cap),
            buildPreamble(),
            "local fn1 = sc.build(sc.peripheral)\n" + "local fn2 = sc.build(sc.side)\n"
                + "fn1(shell_ref, 1, 'a', {}); local t1 = _comp_periph_text\n"
                + "fn2(shell_ref, 1, 'b', {}); local t2 = _comp_side_text\n"
                + "_capture(t1, t2)");
        assertEquals("a", cap.args[0]);
        assertEquals("b", cap.args[1]);
    }
}
