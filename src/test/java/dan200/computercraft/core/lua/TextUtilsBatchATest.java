package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dan200.computercraft.core.lua.LuaRomTestSupport.Capture;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * Batch A tests for {@code rom/apis/textutils} beyond what {@code TextUtilsJsonTest} and
 * {@code TextUtilsSerializeTest} already cover.
 *
 * <p>
 * Covers: {@code formatTime} midnight handling, {@code urlEncode} reserved characters,
 * inf/NaN serialisation, the immutable {@code json_null} / {@code empty_json_array}
 * sentinels, the {@code nbt_style} option in both directions, {@code parse_null}, and
 * {@code \}{@code uXXXX} decoding including surrogate pairs.
 * </p>
 */
class TextUtilsBatchATest {

    private static String textutilsSource;

    @BeforeAll
    static void loadSource() throws IOException {
        textutilsSource = LuaRomTestSupport.readResource("/assets/computercraft/lua/rom/apis/textutils");
    }

    private static Capture run(String body) {
        Capture capture = new Capture();
        CobaltMachine machine = LuaRomTestSupport.machine(capture);
        LuaRomTestSupport.run(machine, textutilsSource, body);
        return capture;
    }

    // -------------------------------------------------------------------------
    // formatTime
    // -------------------------------------------------------------------------

    @Test
    void formatTimeRendersMidnightAsTwelveNotZero() {
        Capture cap = run("_capture(formatTime(0), formatTime(0.5), formatTime(11.99))");
        assertEquals("12:00 AM", cap.string(0));
        assertEquals("12:30 AM", cap.string(1));
        assertEquals("11:59 AM", cap.string(2));
    }

    @Test
    void formatTimeRendersNoonAndAfternoon() {
        Capture cap = run("_capture(formatTime(12), formatTime(12.5), formatTime(13.5), formatTime(23.99))");
        assertEquals("12:00 PM", cap.string(0));
        assertEquals("12:30 PM", cap.string(1));
        assertEquals("1:30 PM", cap.string(2));
        assertEquals("11:59 PM", cap.string(3));
    }

    @Test
    void formatTimeHonoursTwentyFourHourFlag() {
        Capture cap = run("_capture(formatTime(0, true), formatTime(13.5, true), formatTime(23.99, true))");
        assertEquals("0:00", cap.string(0), "24-hour mode has no 12-hour wrap");
        assertEquals("13:30", cap.string(1));
        assertEquals("23:59", cap.string(2));
    }

    @Test
    void formatTimeRejectsNonNumbers() {
        Capture cap = run(
            "local a = pcall(formatTime, 'noon')\n" + "local b = pcall(formatTime, 1, 'yes')\n" + "_capture(a, b)");
        assertFalse(cap.bool(0), "formatTime must reject a non-number time");
        assertFalse(cap.bool(1), "formatTime must reject a non-boolean flag");
    }

    // -------------------------------------------------------------------------
    // urlEncode
    // -------------------------------------------------------------------------

    @Test
    void urlEncodeLeavesTheUnreservedSetAlone() {
        Capture cap = run("_capture(urlEncode('aZ09-_.'))");
        assertEquals("aZ09-_.", cap.string(0), "'-', '_' and '.' are unreserved and must not be escaped");
    }

    @Test
    void urlEncodeEscapesSpacesAndReservedCharacters() {
        Capture cap = run("_capture(urlEncode('a b'), urlEncode('a&b=c'), urlEncode('a~b'), urlEncode('a/b'))");
        assertEquals("a+b", cap.string(0), "space becomes '+'");
        assertEquals("a%26b%3Dc", cap.string(1));
        assertEquals("a%7Eb", cap.string(2), "'~' is not in the unreserved set");
        assertEquals("a%2Fb", cap.string(3));
    }

    @Test
    void urlEncodeNormalisesNewlines() {
        Capture cap = run("_capture(urlEncode('a\\nb'))");
        assertEquals("a%0D%0Ab", cap.string(0), "a bare newline must become CRLF before escaping");
    }

    @Test
    void urlEncodeKeepsLeniencyForNil() {
        // The local implementation has always returned nil rather than throwing on no argument;
        // ROM callers rely on that, so it must not regress.
        Capture cap = run("_capture(urlEncode(nil) == nil)");
        assertTrue(cap.bool(0));
    }

    // -------------------------------------------------------------------------
    // serialize: inf / nan
    // -------------------------------------------------------------------------

    @Test
    void serializeEmitsExpressionsForInfinityAndNan() {
        Capture cap = run(
            "_capture(serialize(math.huge), serialize(-math.huge), serialize(0/0), serialize({math.huge}))");
        assertEquals("1/0", cap.string(0));
        assertEquals("-1/0", cap.string(1));
        assertEquals("0/0", cap.string(2));
        assertEquals("{\n  1/0,\n}", cap.string(3));
    }

    @Test
    void serializedInfinityAndNanRoundTrip() {
        Capture cap = run(
            "local inf = unserialize(serialize(math.huge))\n" + "local ninf = unserialize(serialize(-math.huge))\n"
                + "local nan = unserialize(serialize(0/0))\n"
                + "_capture(inf == math.huge, ninf == -math.huge, nan ~= nan)");
        assertTrue(cap.bool(0), "1/0 must unserialize back to math.huge");
        assertTrue(cap.bool(1), "-1/0 must unserialize back to -math.huge");
        assertTrue(cap.bool(2), "0/0 must unserialize back to a NaN");
    }

    // -------------------------------------------------------------------------
    // JSON sentinels
    // -------------------------------------------------------------------------

    @Test
    void sentinelsSerializeToTheirJsonForm() {
        Capture cap = run("_capture(serializeJSON(json_null), serializeJSON(empty_json_array))");
        assertEquals("null", cap.string(0));
        assertEquals("[]", cap.string(1));
    }

    @Test
    void sentinelsReportTheirJsonFormThroughTostring() {
        Capture cap = run("_capture(tostring(json_null), tostring(empty_json_array))");
        assertEquals("null", cap.string(0));
        assertEquals("[]", cap.string(1));
    }

    @Test
    void sentinelsAreImmutable() {
        Capture cap = run(
            "local ok_null = pcall(function() json_null.x = 1 end)\n"
                + "local ok_array = pcall(function() empty_json_array[1] = 1 end)\n"
                + "local _, err = pcall(function() json_null.x = 1 end)\n"
                + "_capture(ok_null, ok_array, tostring(err))");
        assertFalse(cap.bool(0), "json_null must reject writes");
        assertFalse(cap.bool(1), "empty_json_array must reject writes");
        assertTrue(
            cap.string(2)
                .contains("json_null"),
            "the error should name the sentinel, got: " + cap.string(2));
    }

    @Test
    void sentinelsStayEmpty() {
        Capture cap = run("_capture(next(json_null) == nil, next(empty_json_array) == nil, #empty_json_array)");
        assertTrue(cap.bool(0));
        assertTrue(cap.bool(1));
        assertEquals(0.0, cap.number(2));
    }

    // -------------------------------------------------------------------------
    // serializeJSON: shape
    // -------------------------------------------------------------------------

    @Test
    void arrayHolesBecomeJsonNull() {
        // A nil in the middle of a sequence must not truncate the array.
        Capture cap = run("_capture(serializeJSON({ 1, nil, 3 }))");
        assertEquals("[1,null,3]", cap.string(0));
    }

    @Test
    void trailingNilDoesNotExtendTheArray() {
        Capture cap = run("local t = {1, 2, 3}\nt[5] = 5\n_capture(serializeJSON(t))");
        assertEquals("[1,2,3,null,5]", cap.string(0));
    }

    @Test
    void repeatedTablesAreReportedDistinctlyFromCycles() {
        Capture cap = run(
            "local inner = {1}\n" + "local a, ea = pcall(serializeJSON, {inner, inner})\n"
                + "local t = {}\n"
                + "t[1] = t\n"
                + "local b, eb = pcall(serializeJSON, t)\n"
                + "_capture(a, tostring(ea), b, tostring(eb))");
        assertFalse(cap.bool(0));
        assertTrue(
            cap.string(1)
                .contains("repeated"),
            "expected a 'repeated entries' error, got: " + cap.string(1));
        assertFalse(cap.bool(2));
        assertTrue(
            cap.string(3)
                .contains("recursive"),
            "expected a 'recursive entries' error, got: " + cap.string(3));
    }

    @Test
    void serializeJsonSupportsNbtStyleKeys() {
        Capture cap = run("_capture(serializeJSON({ a = 1 }, { nbt_style = true }))");
        assertEquals("{a:1}", cap.string(0), "nbt_style must emit unquoted keys");
    }

    // -------------------------------------------------------------------------
    // unserializeJSON: options
    // -------------------------------------------------------------------------

    @Test
    void parseNullReturnsTheJsonNullSentinel() {
        Capture cap = run(
            "local t = unserializeJSON('{\"a\":null}', { parse_null = true })\n" + "_capture(t.a == json_null)");
        assertTrue(cap.bool(0));
    }

    @Test
    void parseNullDefaultsToNil() {
        Capture cap = run("local t = unserializeJSON('{\"a\":null}')\n" + "_capture(t.a == nil, next(t) == nil)");
        assertTrue(cap.bool(0));
        assertTrue(cap.bool(1), "a null field must not create a key");
    }

    @Test
    void legacyNullOptionStillWorks() {
        // The pre-existing local spelling was `{ null = <value> }`; it must keep working
        // so existing disk files and programs are unaffected.
        Capture cap = run("local t = unserializeJSON('{\"a\":null}', { null = 'MISSING' })\n" + "_capture(t.a)");
        assertEquals("MISSING", cap.string(0));
    }

    @Test
    void parseEmptyArrayDefaultsToTheSentinel() {
        Capture cap = run(
            "local def = unserializeJSON('[]')\n"
                + "local explicit = unserializeJSON('[]', { parse_empty_array = true })\n"
                + "local plain = unserializeJSON('[]', { parse_empty_array = false })\n"
                + "_capture(def == empty_json_array, explicit == empty_json_array, plain == empty_json_array, type(plain), next(plain) == nil)");
        assertTrue(cap.bool(0), "default must return the sentinel");
        assertTrue(cap.bool(1), "explicit true must return the sentinel");
        assertFalse(cap.bool(2), "parse_empty_array = false must return a fresh table");
        assertEquals("table", cap.string(3));
        assertTrue(cap.bool(4));
    }

    // -------------------------------------------------------------------------
    // NBT-style input
    // -------------------------------------------------------------------------

    @Test
    void nbtStyleParsesUnquotedKeysAndNumericSuffixes() {
        Capture cap = run(
            "local t = unserializeJSON('{a:1b,b:2L,c:3.5f,d:4s,Name:\"Steve\"}', { nbt_style = true })\n"
                + "_capture(t.a, t.b, t.c, t.d, t.Name)");
        assertEquals(1.0, cap.number(0), "byte suffix");
        assertEquals(2.0, cap.number(1), "long suffix");
        assertEquals(3.5, cap.number(2), "float suffix");
        assertEquals(4.0, cap.number(3), "short suffix");
        assertEquals("Steve", cap.string(4));
    }

    @Test
    void nbtStyleParsesSingleQuotedStrings() {
        Capture cap = run("local t = unserializeJSON(\"{a:'hello world'}\", { nbt_style = true })\n" + "_capture(t.a)");
        assertEquals("hello world", cap.string(0));
    }

    @Test
    void nbtStyleParsesTypedArrays() {
        Capture cap = run(
            "local i = unserializeJSON('[I;1,2,3]', { nbt_style = true })\n"
                + "local b = unserializeJSON('[B;1b,2b]', { nbt_style = true })\n"
                + "local l = unserializeJSON('[L;1L,2L]', { nbt_style = true })\n"
                + "_capture(#i, i[1], i[3], #b, b[2], #l, l[2])");
        assertEquals(3.0, cap.number(0), "int array length");
        assertEquals(1.0, cap.number(1));
        assertEquals(3.0, cap.number(2));
        assertEquals(2.0, cap.number(3), "byte array length");
        assertEquals(2.0, cap.number(4));
        assertEquals(2.0, cap.number(5), "long array length");
        assertEquals(2.0, cap.number(6));
    }

    @Test
    void nbtStyleIsRejectedUnlessRequested() {
        Capture cap = run("local v, err = unserializeJSON('{a:1}')\n" + "_capture(v == nil, type(err))");
        assertTrue(cap.bool(0), "unquoted keys must be rejected in plain JSON mode");
        assertEquals("string", cap.string(1));
    }

    @Test
    void nbtStyleRoundTripsThroughSerializeJson() {
        Capture cap = run(
            "local t = { a = 1, b = 'two' }\n" + "local s = serializeJSON(t, { nbt_style = true })\n"
                + "local back = unserializeJSON(s, { nbt_style = true })\n"
                + "_capture(back.a, back.b)");
        assertEquals(1.0, cap.number(0));
        assertEquals("two", cap.string(1));
    }

    // -------------------------------------------------------------------------
    // Unicode escapes
    // -------------------------------------------------------------------------

    @Test
    void unicodeEscapesDecodeToUtf8() {
        Capture cap = run(
            "local ascii = unserializeJSON('\"\\\\u0041\"')\n" + "local two = unserializeJSON('\"\\\\u00e9\"')\n"
                + "local three = unserializeJSON('\"\\\\u20ac\"')\n"
                + "_capture(ascii, two, three)");
        assertEquals("A", cap.string(0));
        assertEquals(
            0xC3,
            (int) cap.string(1)
                .charAt(0),
            "é must be encoded as 2 UTF-8 bytes");
        assertEquals(
            0xA9,
            (int) cap.string(1)
                .charAt(1));
        assertEquals(
            0xE2,
            (int) cap.string(2)
                .charAt(0),
            "€ must be encoded as 3 UTF-8 bytes");
        assertEquals(
            3,
            cap.string(2)
                .length());
    }

    @Test
    void surrogatePairsDecodeToOneCodePoint() {
        // U+1F600 GRINNING FACE is \uD83D\uDE00.
        Capture cap = run(
            "local s = unserializeJSON('\"\\\\uD83D\\\\uDE00\"')\n"
                + "_capture(#s, s:byte(1), s:byte(2), s:byte(3), s:byte(4))");
        assertEquals(4.0, cap.number(0), "a supplementary code point is 4 UTF-8 bytes");
        assertEquals(0xF0, cap.number(1));
        assertEquals(0x9F, cap.number(2));
        assertEquals(0x98, cap.number(3));
        assertEquals(0x80, cap.number(4));
    }

    @Test
    void unicodeStringsEscapeCodePoints() {
        // Matches CC:Tweaked: with unicode_strings the string is walked as UTF-8 and each
        // code point is escaped, using uppercase hex.
        Capture cap = run(
            "local e_acute = string.char(195, 169)\n" // UTF-8 for U+00E9
                + "local euro = string.char(226, 130, 172)\n" // U+20AC
                + "local emoji = string.char(240, 159, 152, 128)\n" // U+1F600
                + "_capture(serializeJSON(e_acute, { unicode_strings = true }),"
                + " serializeJSON(euro, { unicode_strings = true }),"
                + " serializeJSON(emoji, { unicode_strings = true }))");

        assertEquals("\"\\u00E9\"", cap.string(0), "2-byte code point");
        assertEquals("\"\\u20AC\"", cap.string(1), "3-byte code point");
        assertEquals("\"\\uD83D\\uDE00\"", cap.string(2), "supplementary code point becomes a surrogate pair");
    }

    @Test
    void unicodeStringsRoundTrips() {
        // The point of code-point escaping: the output decodes back to the original bytes.
        Capture cap = run(
            "local cases = {\n" + "  string.char(195, 169),\n"
                + "  string.char(226, 130, 172),\n"
                + "  string.char(240, 159, 152, 128),\n"
                + "  'plain ascii',\n"
                + "  '',\n"
                + "}\n"
                + "local ok, detail = true, ''\n"
                + "for _, s in ipairs(cases) do\n"
                + "  local json = serializeJSON(s, { unicode_strings = true })\n"
                + "  if unserializeJSON(json) ~= s then ok, detail = false, json end\n"
                + "end\n"
                + "_capture(ok, detail)");
        assertTrue(cap.bool(0), "unicode_strings output must round-trip, failed on: " + cap.string(1));
    }

    @Test
    void defaultJsonStringIsAsciiSafe() {
        // Without the option upstream escapes bytes >= 0x7F, so the output is pure ASCII. Note
        // this is deliberately *not* asserted as round-tripping: escaping bytes while decoding
        // code points is asymmetric, which is upstream's behaviour too. Use `unicode_strings`
        // when a round-trip is needed.
        Capture cap = run(
            "local s = string.char(195, 169)\n" + "local json = serializeJSON(s)\n"
                + "local ascii = true\n"
                + "for i = 1, #json do if json:byte(i) >= 128 then ascii = false end end\n"
                + "_capture(json, ascii)");
        assertEquals("\"\\u00C3\\u00A9\"", cap.string(0), "non-ASCII bytes must be escaped by default");
        assertTrue(cap.bool(1), "the default output must be pure ASCII");
    }

    @Test
    void invalidUtf8IsEscapedRatherThanRaising() {
        // Lua's utf8.codes would raise here; this ROM's iterator degrades to byte escaping.
        Capture cap = run(
            "local bad = string.char(0xFF) .. 'x'\n"
                + "local ok, json = pcall(serializeJSON, bad, { unicode_strings = true })\n"
                + "_capture(ok, json)");
        assertTrue(cap.bool(0), "a malformed UTF-8 sequence must not throw");
        assertEquals("\"\\u00FFx\"", cap.string(1));
    }

    // -------------------------------------------------------------------------
    // Error reporting
    // -------------------------------------------------------------------------

    @Test
    void malformedInputReturnsNilAndAMessage() {
        Capture cap = run(
            "local cases = { '', '{', '[', '{a', '\"abc', 'tru', 'nul' }\n" + "local ok = true\n"
                + "local detail = ''\n"
                + "for _, s in ipairs(cases) do\n"
                + "  local v, e = unserializeJSON(s)\n"
                + "  if v ~= nil or type(e) ~= 'string' then ok = false; detail = s end\n"
                + "end\n"
                + "_capture(ok, detail)");
        assertTrue(cap.bool(0), "every malformed input must return nil + a string, failing at: " + cap.string(1));
    }

    @Test
    void trailingContentIsRejected() {
        Capture cap = run("local v, e = unserializeJSON('42 extra')\n" + "_capture(v == nil, tostring(e))");
        assertTrue(cap.bool(0));
        assertTrue(
            cap.string(1)
                .toLowerCase()
                .contains("trailing"),
            "the message should mention trailing content, got: " + cap.string(1));
    }

    @Test
    void unserializeJsonRejectsBadArgumentTypes() {
        Capture cap = run(
            "local a = pcall(unserializeJSON, 42)\n" + "local b = pcall(unserializeJSON, '{}', 'nope')\n"
                + "_capture(a, b)");
        assertFalse(cap.bool(0), "non-string input must be a type error");
        assertFalse(cap.bool(1), "non-table options must be a type error");
    }

    @Test
    void serializeJsonRejectsBadOptions() {
        Capture cap = run("local ok = pcall(serializeJSON, {}, 'nope')\n_capture(ok)");
        assertFalse(cap.bool(0));
    }

    // -------------------------------------------------------------------------
    // Aliases
    // -------------------------------------------------------------------------

    @Test
    void BritishAliasesPointAtTheSameFunctions() {
        Capture cap = run(
            "local j = serializeJSON({1}, { nbt_style = true })\n"
                + "_capture(serialiseJSON == serializeJSON, unserialiseJSON == unserializeJSON, serialise == serialize, unserialise == unserialize, j)");
        assertTrue(cap.bool(0));
        assertTrue(cap.bool(1));
        assertTrue(cap.bool(2));
        assertTrue(cap.bool(3));
        assertEquals("[1]", cap.string(4));
    }

    @Test
    void textutilsSourceIsLoadable() {
        assertNotNull(textutilsSource);
        assertFalse(textutilsSource.isEmpty());
    }
}
