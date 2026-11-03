package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Tests for the parts of Batch I's editor work a JVM test can reach.
 *
 * <p>
 * The editor itself cannot be driven headlessly — it blocks on {@code os.pullEventRaw} — so two
 * things are covered here instead:
 * </p>
 *
 * <ul>
 * <li>every ROM program, API and module still <em>compiles</em>, which is what a bad edit to
 * {@code edit}/{@code lua}/{@code help} breaks first;</li>
 * <li>the lexer's continuation protocol round-trips, which is the mechanism the new syntax
 * highlighting is built on. If a continuation could not be re-fed to the lexer, multi-line strings
 * and comments could not be coloured at all.</li>
 * </ul>
 */
class EditorHighlightingTest {

    /** Where the ROM Lua lives, relative to the project root the tests run from. */
    private static final Path ROM = Path.of("src/main/resources/assets/computercraft/lua");

    private static final String EXPECT = "rom/modules/main/cc/expect.lua";
    private static final String PARSER = "rom/modules/main/cc/internal/syntax/parser.lua";
    private static final String LEXER = "rom/modules/main/cc/internal/syntax/lexer.lua";
    private static final String ERRORS = "rom/modules/main/cc/internal/syntax/errors.lua";
    private static final String PRETTY = "rom/modules/main/cc/pretty.lua";

    // -------------------------------------------------------------------------
    // Every ROM file compiles
    // -------------------------------------------------------------------------

    /** Collects the ROM Lua files that should compile, with their sources. */
    private static List<String[]> romSources() throws IOException {
        List<String[]> out = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(ROM)) {
            for (Path path : paths.filter(Files::isRegularFile)
                .toList()) {
                String relative = ROM.relativize(path)
                    .toString()
                    .replace('\\', '/');
                // ROM APIs and programs are extensionless; modules end in .lua.
                boolean isLua = (relative.startsWith("rom/programs/") || relative.startsWith("rom/apis/")
                    || relative.endsWith(".lua"))
                    // fun/advanced/levels/* are level data files read by the game, not Lua.
                    && !relative.startsWith("rom/programs/fun/advanced/levels/");
                if (!isLua) continue;
                out.add(new String[] { relative, Files.readString(path, StandardCharsets.UTF_8) });
            }
        }
        return out;
    }

    @Test
    void everyRomProgramApiAndModuleCompiles() throws IOException {
        List<String[]> sources = romSources();
        assertTrue(sources.size() > 50, "expected the whole ROM, only found " + sources.size() + " files");

        StringBuilder table = new StringBuilder("{\n");
        for (String[] entry : sources) {
            // Element source can contain ]] or ]==], so use a long bracket level that cannot clash.
            table.append("[\"")
                .append(entry[0])
                .append("\"] = [====[\n")
                .append(entry[1])
                .append("\n]====],\n");
        }
        table.append("}");

        LuaRomTestSupport.Capture capture = new LuaRomTestSupport.Capture();
        run(capture, "_rom_check = " + table + "\n" + """
            local broken, checked = {}, 0
            for name, source in pairs(_rom_check) do
              checked = checked + 1
              local chunk, err = loadstring(source, '@' .. name)
              if not chunk then broken[#broken + 1] = name .. ': ' .. tostring(err) end
            end
            table.sort(broken)
            _capture(checked, table.concat(broken, ', '))
            """);

        int checked = (int) capture.number(0);
        String broken = capture.string(1);
        assertEquals("", broken, "these ROM files do not compile");
        assertTrue(checked > 50, "should have compiled " + checked + " files");
    }

    // -------------------------------------------------------------------------
    // The lexer continuation protocol the editor depends on
    // -------------------------------------------------------------------------

    /** Runs {@code body} with the ROM lexer and parser reachable through {@code require}. */
    private static LuaRomTestSupport.Capture runWithLexer(String body) throws IOException {
        // The lexer requires the error table, which requires cc.pretty, so all four must be present
        // or the module load fails before the test body ever runs.
        String modules = "{\n" + moduleEntry("cc.expect", EXPECT)
            + moduleEntry("cc.pretty", PRETTY)
            + moduleEntry("cc.internal.syntax.errors", ERRORS)
            + moduleEntry("cc.internal.syntax.parser", PARSER)
            + moduleEntry("cc.internal.syntax.lexer", LEXER)
            + "}";

        return run(new LuaRomTestSupport.Capture(), "_rom = " + modules + "\n" + """
            -- cc.pretty and the error table read `colours` and `write` at load time, and the headless
            -- machine has no APIs installed, so a minimal stub stands in for them.
            colours = {
              white = 1, orange = 2, magenta = 3, lightBlue = 4, yellow = 5, lime = 6, pink = 7,
              grey = 8, lightGrey = 9, cyan = 10, purple = 11, blue = 12, brown = 13, green = 14,
              red = 15, black = 16,
            }
            term = {
              setTextColour = function() end, setTextColor = function() end,
              getTextColour = function() return colours.white end,
              write = function() end, blit = function() end, clearLine = function() end,
              setCursorPos = function() end,
            }
            _cache = {}
            function require(name)
              if _cache[name] ~= nil then return _cache[name] end
              local src = _rom[name]
              if not src then error('missing module ' .. name) end
              local f = assert(loadstring(src, '@' .. name))
              local r = f()
              if r == nil then r = true end
              _cache[name] = r
              return r
            end
            """ + body);
    }

    /**
     * Runs Lua on a fresh machine with {@code _capture} installed and returns what it captured.
     *
     * <p>
     * The body runs inside {@code pcall} so a Lua error surfaces as a captured message instead of a
     * silent "never reached _capture" — the failure mode that costs the most time to diagnose.
     * </p>
     */
    private static LuaRomTestSupport.Capture run(LuaRomTestSupport.Capture capture, String source) {
        var machine = LuaRomTestSupport.machine(capture);
        String wrapped = "local _run_ok, _run_err = pcall(function()\n" + source
            + "\nend)\n"
            + "if not _run_ok then _capture('LUA ERROR: ' .. tostring(_run_err)) end\n";
        LuaRomTestSupport.run(machine, wrapped);
        assertNotNull(capture.args, "the Lua test neither captured a result nor raised");
        return capture;
    }

    private static String moduleEntry(String name, String path) throws IOException {
        String source = LuaRomTestSupport.readResource("/assets/computercraft/lua/" + path);
        return "[\"" + name + "\"] = [====[\n" + source + "\n]====],\n";
    }

    @Test
    void aContinuationCanBeRefedToTheLexer() throws Exception {
        // Exactly what the editor does for the line after an unterminated *long* string: walk the
        // tokens, keep the continuation the lexer hands back at the end of the line, then call that
        // continuation's function with the arguments it carries to colour the next line.
        //
        // Only long strings and long comments can span lines: a short "quoted" string is terminated
        // by the newline by definition, so it leaves no continuation (asserted below).
        LuaRomTestSupport.Capture capture = runWithLexer("""
            local tokens = require("cc.internal.syntax.parser").tokens
            local lex_one = require("cc.internal.syntax.lexer").lex_one
            local ctx = { line = function() end, report = function() end }

            local line = 'local s = [[ line one'
            local pos, cont, sawString = 1, nil, false
            while pos <= #line do
              local token, _, finish, _, continuation = lex_one(ctx, line, pos)
              if not token then break end
              if token == tokens.STRING then sawString = true end
              cont = continuation
              if continuation then break end
              pos = finish + 1
            end

            if not cont then _capture("no continuation", tostring(sawString)) return end

            -- Feed it the next line: it must report STRING covering that whole line, then finish.
            local resumed, finish, _, cont2 =
              cont[1](ctx, 'line two ]]', table.unpack(cont, 2))

            _capture(
              tostring(sawString),
              tostring(type(cont) == "table"),
              tostring(resumed == tokens.STRING),
              tostring(finish == #'line two ]]'),
              tostring(cont2 == nil)
            )
            """);

        assertEquals(5, capture.args.length, "captured: " + java.util.Arrays.toString(capture.args));
        assertEquals("true", capture.string(0), "the line should lex a string token");
        assertEquals("true", capture.string(1), "a long string should leave a continuation");
        assertEquals("true", capture.string(2), "the next line should resume as a string");
        assertEquals("true", capture.string(3), "the resumed token should cover the whole line");
        assertEquals("true", capture.string(4), "a closed long string should leave no continuation");
    }

    @Test
    void aShortStringLeavesNoContinuationAcrossLines() throws Exception {
        // Lua's truth, not the editor's convenience: a short string ends at the newline, so the
        // editor must not carry state over it.
        LuaRomTestSupport.Capture capture = runWithLexer("""
            local tokens = require("cc.internal.syntax.parser").tokens
            local lex_one = require("cc.internal.syntax.lexer").lex_one
            local ctx = { line = function() end, report = function() end }

            local line = 'local x = "unterminated'
            local pos, cont = 1, nil
            while pos <= #line do
              local _, _, finish, _, continuation = lex_one(ctx, line, pos)
              if not finish then break end
              cont = continuation
              if continuation then break end
              pos = finish + 1
            end
            _capture(tostring(cont == nil))
            """);

        assertEquals("true", capture.string(0), "a short string must not leak state across lines");
    }

    @Test
    void aLongCommentKeepsItsContinuationUntilItCloses() throws Exception {
        LuaRomTestSupport.Capture capture = runWithLexer("""
            local tokens = require("cc.internal.syntax.parser").tokens
            local lex_one = require("cc.internal.syntax.lexer").lex_one
            local ctx = { line = function() end, report = function() end }

            local _, _, _, _, cont = lex_one(ctx, '--[[ opening a long comment', 1)
            local mid, _, _, contMid = cont[1](ctx, 'inside', table.unpack(cont, 2))
            local _, _, _, contEnd = contMid[1](ctx, 'closing ]]', table.unpack(contMid, 2))

            _capture(tostring(mid == tokens.COMMENT), tostring(contMid ~= nil), tostring(contEnd == nil))
            """);

        assertEquals("true", capture.string(0));
        assertEquals("true", capture.string(1));
        assertEquals("true", capture.string(2));
    }

    @Test
    void aClosedLongCommentLeavesNoContinuation() throws Exception {
        LuaRomTestSupport.Capture capture = runWithLexer("""
            local lex_one = require("cc.internal.syntax.lexer").lex_one
            local ctx = { line = function() end, report = function() end }

            local _, _, _, _, cont = lex_one(ctx, '--[[ closed on one line ]] local x = 1', 1)
            _capture(tostring(cont == nil))
            """);

        assertEquals("true", capture.string(0));
    }

    @Test
    void aMultiLineStringLeavesNoContinuationOnceClosed() throws Exception {
        LuaRomTestSupport.Capture capture = runWithLexer("""
            local lex_one = require("cc.internal.syntax.lexer").lex_one
            local ctx = { line = function() end, report = function() end }

            -- Walk to the string: `local s = [[ line one` starts with two other tokens.
            local pos, cont = 1, nil
            while pos <= #'local s = [[ line one' do
              local token, _, finish, _, continuation = lex_one(ctx, 'local s = [[ line one', pos)
              if not token then break end
              cont = continuation
              if continuation then break end
              pos = finish + 1
            end
            if not cont then _capture("no continuation") return end
            local _, _, _, cont2 = cont[1](ctx, 'line two ]]', table.unpack(cont, 2))
            _capture(tostring(cont2 == nil))
            """);

        assertEquals("true", capture.string(0));
    }

    @Test
    void aBlankLineInsideALongCommentKeepsItsState() throws Exception {
        // A blank line inside --[[ ]] must not drop the continuation, or the comment stops being
        // coloured from the first empty line onwards.
        LuaRomTestSupport.Capture capture = runWithLexer("""
            local tokens = require("cc.internal.syntax.parser").tokens
            local lex_one = require("cc.internal.syntax.lexer").lex_one
            local ctx = { line = function() end, report = function() end }

            local _, _, _, _, cont = lex_one(ctx, '--[[ opening', 1)
            if not cont then _capture("no continuation", "start") return end

            -- An empty line, exactly as the editor would feed it.
            local token, finish, _, cont2 = cont[1](ctx, '', table.unpack(cont, 2))
            _capture(tostring(token == tokens.COMMENT), tostring(cont2 ~= nil), tostring(finish))
            """);

        assertEquals(3, capture.args.length, "captured: " + java.util.Arrays.toString(capture.args));
        assertEquals("true", capture.string(0), "an empty line inside a comment is still a comment");
        assertEquals("true", capture.string(1), "the state must survive an empty line");
    }

    @Test
    void theLexerAndParserExposeWhatTheEditorRequires() throws Exception {
        LuaRomTestSupport.Capture capture = runWithLexer("""
            local parser = require("cc.internal.syntax.parser")
            local lexer = require("cc.internal.syntax.lexer")
            _capture(tostring(type(parser.tokens) == "table"), tostring(type(lexer.lex_one) == "function"))
            """);

        assertNotNull(capture.args);
        assertEquals("true", capture.string(0));
        assertEquals("true", capture.string(1));
    }
}
