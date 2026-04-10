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
 * Unit tests for {@code textutils.serialize} (and its {@code serialise} alias) focusing
 * on the CC:Tweaked-compatible {@code opts} table argument:
 * <ul>
 * <li>{@code compact} — emit no whitespace or indentation between terms</li>
 * <li>{@code allow_repetitions} — allow the same table to appear in multiple
 * sibling positions without erroring, while still detecting true cycles</li>
 * </ul>
 */
class TextUtilsSerializeTest {

    /** Full textutils source, loaded once for all tests. */
    private static String textutilsSource;

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @BeforeAll
    static void loadTextUtils() throws IOException {
        try (InputStream is = TextUtilsSerializeTest.class
            .getResourceAsStream("/assets/computercraft/lua/rom/apis/textutils")) {
            assertNotNull(is, "textutils resource must be on the test classpath");
            try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                textutilsSource = scanner.hasNext() ? scanner.next() : "";
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

    private static void run(CobaltMachine machine, String testLua) {
        String combined = textutilsSource + "\n" + testLua;
        machine.loadBios(new ByteArrayInputStream(combined.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 100 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    // -------------------------------------------------------------------------
    // serialize without opts (regression: existing behaviour unchanged)
    // -------------------------------------------------------------------------

    @Test
    void serializeDefaultProducesIndentedOutput() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(serialize({1,2,3}))");
        assertNotNull(cap.args);
        String s = (String) cap.args[0];
        // Non-compact output must contain a newline
        assertTrue(s.contains("\n"), "Default serialize must produce newline-separated output");
    }

    @Test
    void serializeDefaultContainsAllValues() {
        ResultCapture cap = new ResultCapture();
        // Verify all values appear in the default (non-compact) output
        run(buildMachine(cap), "_capture(serialize({10,20,30}))");
        assertNotNull(cap.args);
        String s = (String) cap.args[0];
        assertTrue(s.contains("10"), "Default output must contain 10");
        assertTrue(s.contains("20"), "Default output must contain 20");
        assertTrue(s.contains("30"), "Default output must contain 30");
        assertTrue(s.contains("\n"), "Default output must use newlines for indentation");
    }

    @Test
    void serializeRecursiveTableErrorsWithoutOption() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local t = {}\nlocal ok, err = pcall(serialize, {t, t})\n_capture(ok, type(err))");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "Duplicate table reference must error without allow_repetitions");
        assertEquals("string", cap.args[1]);
    }

    // -------------------------------------------------------------------------
    // compact option
    // -------------------------------------------------------------------------

    @Test
    void serializeCompactSequenceHasNoWhitespace() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(serialize({1,2,3}, {compact=true}))");
        assertNotNull(cap.args);
        String s = (String) cap.args[0];
        assertFalse(s.contains("\n"), "Compact output must contain no newlines");
        assertFalse(s.contains("  "), "Compact output must contain no double-spaces");
    }

    @Test
    void serializeCompactSequenceProducesExactFormat() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(serialize({1,2,3}, {compact=true}))");
        assertNotNull(cap.args);
        // Compact array: no spaces, no newlines, trailing comma inside braces
        assertEquals("{1,2,3,}", cap.args[0]);
    }

    @Test
    void serializeCompactStringValue() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(serialize(\"hello\", {compact=true}))");
        assertNotNull(cap.args);
        // Strings are always quoted with %q; compact has no effect on scalar values
        assertTrue(
            cap.args[0].toString()
                .contains("hello"),
            "Compact serialize of a string must still quote it correctly");
    }

    @Test
    void serializeCompactNestedTableProducesExactFormat() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local inner = {7, 8}\n" + "local outer = {inner, 9}\n" + "_capture(serialize(outer, {compact=true}))");
        assertNotNull(cap.args);
        // Compact nested: no newlines, all on one line
        String s = (String) cap.args[0];
        assertFalse(s.contains("\n"), "Compact nested output must have no newlines");
        assertEquals("{{7,8,},9,}", s);
    }

    @Test
    void serializeCompactKeyValueProducesExactFormat() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(serialize({answer=42}, {compact=true}))");
        assertNotNull(cap.args);
        // Compact key=value: no spaces around '='
        assertEquals("{answer=42,}", cap.args[0]);
    }

    @Test
    void serializeCompactFalseMatchesDefault() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(serialize({1,2,3}, {compact=false}) == serialize({1,2,3}))");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "compact=false must produce the same output as no opts");
    }

    // -------------------------------------------------------------------------
    // allow_repetitions option
    // -------------------------------------------------------------------------

    @Test
    void serializeAllowRepetitionsPermitsSiblingReference() {
        ResultCapture cap = new ResultCapture();
        // Same table appears in two sibling positions — allowed with allow_repetitions=true
        run(
            buildMachine(cap),
            "local inner = {99}\n" + "local ok, result = pcall(serialize, {inner, inner}, {allow_repetitions=true})\n"
                + "_capture(ok)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "allow_repetitions=true must not error on sibling duplicate tables");
    }

    @Test
    void serializeAllowRepetitionsSiblingOutputContainsBothCopies() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local inner = {99}\n" + "local s = serialize({inner, inner}, {allow_repetitions=true, compact=true})\n"
                + "_capture(s)");
        assertNotNull(cap.args);
        // Both positions must render the same content
        assertEquals("{{99,},{99,},}", cap.args[0]);
    }

    @Test
    void serializeAllowRepetitionsStillErrorsOnDirectCycle() {
        ResultCapture cap = new ResultCapture();
        // t.self = t is a true cycle and must always error
        run(
            buildMachine(cap),
            "local t = {}\n" + "t.self = t\n"
                + "local ok, err = pcall(serialize, t, {allow_repetitions=true})\n"
                + "_capture(ok, type(err))");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "allow_repetitions must still error on a self-referential cycle");
        assertEquals("string", cap.args[1]);
    }

    @Test
    void serializeAllowRepetitionsDeepCycleErrors() {
        ResultCapture cap = new ResultCapture();
        // a → b → a is also a cycle
        run(
            buildMachine(cap),
            "local a = {}\n" + "local b = {a}\n"
                + "a[1] = b\n"
                + "local ok, err = pcall(serialize, a, {allow_repetitions=true})\n"
                + "_capture(ok, type(err))");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "allow_repetitions must still error on an indirect cycle");
        assertEquals("string", cap.args[1]);
    }

    // -------------------------------------------------------------------------
    // serialise alias
    // -------------------------------------------------------------------------

    @Test
    void serialiseAliasHonorsOpts() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local s = serialise({1,2,3}, {compact=true})\n" + "_capture(s:find('\\n') == nil)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "serialise alias must honour the compact option");
    }
}
