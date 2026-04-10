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
 * Tests for the {@code help} Lua API ({@code rom/apis/help}).
 *
 * <p>
 * Focuses on the updated {@link #lookup} behaviour (extension-aware file search)
 * and the corresponding {@link #topics} extension-stripping, both of which were
 * added to match the CC:Tweaked reference implementation.
 * </p>
 *
 * <p>
 * A minimal mock {@code fs} table is injected before the help source so that all
 * tests run without a live filesystem. Files are registered by setting
 * {@code _mock_files["path"] = true}; the help root {@code /rom/help} is
 * pre-registered as a directory.
 * </p>
 */
class HelpAPITest {

    private static String helpSource;

    /**
     * Mock {@code fs} preamble.
     *
     * <ul>
     * <li>{@code _mock_files} — set of full file paths that "exist" as regular files.</li>
     * <li>{@code _mock_dirs} — set of directory paths; {@code /rom/help} is pre-seeded.</li>
     * <li>{@code fs.combine} concatenates with {@code /} without double-slashes.</li>
     * <li>{@code fs.list} returns the direct children of a directory present in
     * {@code _mock_files}.</li>
     * </ul>
     */
    private static final String MOCK_PREAMBLE = "_mock_files = {}\n" + "_mock_dirs  = { ['/rom/help'] = true }\n"
        + "fs = {\n"
        + "  exists  = function(p) return _mock_files[p] == true or _mock_dirs[p] == true end,\n"
        + "  isDir   = function(p) return _mock_dirs[p] == true end,\n"
        + "  combine = function(a, b)\n"
        + "    if a == '' then return b end\n"
        + "    if b == '' then return a end\n"
        + "    return a .. '/' .. b\n"
        + "  end,\n"
        + "  list = function(dir)\n"
        + "    local r, prefix = {}, dir .. '/'\n"
        + "    for path, _ in pairs(_mock_files) do\n"
        + "      if path:sub(1, #prefix) == prefix then\n"
        + "        local rest = path:sub(#prefix + 1)\n"
        + "        if not rest:find('/', 1, true) then r[#r+1] = rest end\n"
        + "      end\n"
        + "    end\n"
        + "    return r\n"
        + "  end,\n"
        + "}\n";

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @BeforeAll
    static void loadSource() throws IOException {
        helpSource = readResource("/assets/computercraft/lua/rom/apis/help");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream is = HelpAPITest.class.getResourceAsStream(path)) {
            assertNotNull(is, path + " must be on the test classpath");
            try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                return scanner.hasNext() ? scanner.next() : "";
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
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

    /** Runs {@code testLua} with the mock {@code fs} preamble and help source pre-loaded. */
    private static void run(CobaltMachine machine, String testLua) {
        String combined = MOCK_PREAMBLE + "\n" + helpSource + "\n" + testLua;
        machine.loadBios(new ByteArrayInputStream(combined.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 50 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    // =========================================================================
    // lookup — exact match (existing behaviour preserved)
    // =========================================================================

    @Test
    void lookupFindsExactMatch() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_mock_files['/rom/help/intro'] = true\n" + "_capture(lookup('intro'))");
        assertNotNull(cap.args);
        assertEquals("/rom/help/intro", cap.args[0], "lookup must return the exact path when found");
    }

    @Test
    void lookupReturnsNilWhenNotFound() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(lookup('missing'))");
        assertNotNull(cap.args);
        assertNull(cap.args[0], "lookup must return nil when no file exists under any extension");
    }

    // =========================================================================
    // lookup — Markdown (.md) support
    // =========================================================================

    @Test
    void lookupFindsMdFile() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_mock_files['/rom/help/intro.md'] = true\n" + "_capture(lookup('intro'))");
        assertNotNull(cap.args);
        assertEquals("/rom/help/intro.md", cap.args[0], "lookup must find 'intro.md' when queried as 'intro'");
    }

    @Test
    void lookupMdReturnedWithFullExtensionPath() {
        // The returned path must include the .md suffix so the caller can open it.
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_mock_files['/rom/help/turtle.md'] = true\n" + "_capture(lookup('turtle'))");
        assertNotNull(cap.args);
        assertTrue(((String) cap.args[0]).endsWith(".md"), "Returned path must end with .md");
    }

    // =========================================================================
    // lookup — plain text (.txt) support
    // =========================================================================

    @Test
    void lookupFindsTxtFile() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_mock_files['/rom/help/fs.txt'] = true\n" + "_capture(lookup('fs'))");
        assertNotNull(cap.args);
        assertEquals("/rom/help/fs.txt", cap.args[0], "lookup must find 'fs.txt' when queried as 'fs'");
    }

    @Test
    void lookupTxtReturnedWithFullExtensionPath() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_mock_files['/rom/help/os.txt'] = true\n" + "_capture(lookup('os'))");
        assertNotNull(cap.args);
        assertTrue(((String) cap.args[0]).endsWith(".txt"), "Returned path must end with .txt");
    }

    // =========================================================================
    // lookup — extension priority: exact > .md > .txt
    // =========================================================================

    @Test
    void lookupPrefersExactMatchOverMd() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "_mock_files['/rom/help/intro']    = true\n" + "_mock_files['/rom/help/intro.md'] = true\n"
                + "_capture(lookup('intro'))");
        assertNotNull(cap.args);
        assertEquals("/rom/help/intro", cap.args[0], "Exact match must take priority over .md when both exist");
    }

    @Test
    void lookupPrefersMdOverTxt() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "_mock_files['/rom/help/intro.md']  = true\n" + "_mock_files['/rom/help/intro.txt'] = true\n"
                + "_capture(lookup('intro'))");
        assertNotNull(cap.args);
        assertEquals("/rom/help/intro.md", cap.args[0], ".md must take priority over .txt when both exist");
    }

    @Test
    void lookupFallsBackToTxtWhenMdAbsent() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_mock_files['/rom/help/intro.txt'] = true\n" + "_capture(lookup('intro'))");
        assertNotNull(cap.args);
        assertEquals("/rom/help/intro.txt", cap.args[0], "lookup must fall back to .txt when no exact or .md exists");
    }

    // =========================================================================
    // topics — extension stripping
    // =========================================================================

    @Test
    void topicsStripsMdExtension() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "_mock_files['/rom/help/turtle.md'] = true\n" + "local t = topics()\n"
                + "local found = false\n"
                + "for _, v in ipairs(t) do if v == 'turtle' then found = true end end\n"
                + "_capture(found)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "topics() must list 'turtle', not 'turtle.md'");
    }

    @Test
    void topicsStripsTxtExtension() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "_mock_files['/rom/help/fs.txt'] = true\n" + "local t = topics()\n"
                + "local found = false\n"
                + "for _, v in ipairs(t) do if v == 'fs' then found = true end end\n"
                + "_capture(found)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "topics() must list 'fs', not 'fs.txt'");
    }

    @Test
    void topicsDoesNotListRawExtensionedNames() {
        // The raw filename 'turtle.md' must not appear in topics() — only 'turtle'.
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "_mock_files['/rom/help/turtle.md'] = true\n" + "local t = topics()\n"
                + "local rawFound = false\n"
                + "for _, v in ipairs(t) do if v == 'turtle.md' then rawFound = true end end\n"
                + "_capture(rawFound)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "topics() must not list 'turtle.md' — extension must be stripped");
    }

    @Test
    void topicsDeduplicatesWhenBothMdAndTxtExistForSameTopic() {
        // If both 'intro.md' and 'intro.txt' are on disk, topics() should list 'intro' only once.
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "_mock_files['/rom/help/intro.md']  = true\n" + "_mock_files['/rom/help/intro.txt'] = true\n"
                + "local t = topics()\n"
                + "local count = 0\n"
                + "for _, v in ipairs(t) do if v == 'intro' then count = count + 1 end end\n"
                + "_capture(count)");
        assertNotNull(cap.args);
        assertEquals(1.0, ((Number) cap.args[0]).doubleValue(), "'intro' must appear exactly once in topics()");
    }

    @Test
    void topicsIncludesExtensionlessFiles() {
        // Files with no extension (legacy style) must still be listed.
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "_mock_files['/rom/help/rednet'] = true\n" + "local t = topics()\n"
                + "local found = false\n"
                + "for _, v in ipairs(t) do if v == 'rednet' then found = true end end\n"
                + "_capture(found)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "topics() must still list extension-less files");
    }

    // =========================================================================
    // completeTopic — works correctly with stripped names
    // =========================================================================

    @Test
    void completeTopicMatchesStrippedMdName() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "_mock_files['/rom/help/turtle.md'] = true\n" + "local r = completeTopic('turt')\n" + "_capture(#r, r[1])");
        assertNotNull(cap.args);
        assertEquals(1.0, ((Number) cap.args[0]).doubleValue(), "completeTopic must return one completion");
        assertEquals("le", cap.args[1], "Completion suffix must be 'le' (completing 'turt' -> 'turtle')");
    }

    @Test
    void completeTopicReturnsEmptyForNoMatch() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_mock_files['/rom/help/turtle.md'] = true\n" + "_capture(#completeTopic('xyz'))");
        assertNotNull(cap.args);
        assertEquals(0.0, ((Number) cap.args[0]).doubleValue(), "completeTopic must return empty list for no match");
    }
}
