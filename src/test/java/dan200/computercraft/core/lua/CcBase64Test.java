package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dan200.computercraft.core.lua.LuaRomTestSupport.Capture;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * Tests for {@code rom/modules/main/cc/base64.lua}.
 *
 * <p>
 * The module depends on {@code cc.expect}, so the test prepends that ROM module and installs a
 * stub {@code require} rather than dragging in the whole BIOS. The real {@code require} path is
 * exercised separately by {@code BiosBatchAHeadlessTest}.
 * </p>
 *
 * <p>
 * The upstream implementation is written for a runtime with {@code bit32}; this port is not, so
 * the encode/decode round-trip and a set of RFC 4648 vectors guard the arithmetic.
 * </p>
 */
class CcBase64Test {

    private static String prelude;

    @BeforeAll
    static void loadSource() throws IOException {
        String expect = LuaRomTestSupport.readResource("/assets/computercraft/lua/rom/modules/main/cc/expect.lua");
        String base64 = LuaRomTestSupport.readResource("/assets/computercraft/lua/rom/modules/main/cc/base64.lua");
        // Both ROM files end with a `return`, so each has to be wrapped in its own function
        // before anything else can follow it in the same chunk. `require` is defined first
        // because the base64 module calls it while its body is being evaluated.
        prelude = "local _expect_module\n" + "local _base64_module\n"
            + "function require( _name )\n"
            + "  if _name == 'cc.expect' then return _expect_module end\n"
            + "  if _name == 'cc.base64' then return _base64_module end\n"
            + "  error( 'module not found: ' .. tostring( _name ) )\n"
            + "end\n"
            + "_expect_module = (function()\n"
            + expect
            + "\nend)()\n"
            + "_base64_module = (function()\n"
            + base64
            + "\nend)()";
    }

    private static Capture run(String body) {
        Capture capture = new Capture();
        CobaltMachine machine = LuaRomTestSupport.machine(capture);
        LuaRomTestSupport.run(machine, prelude, body);
        return capture;
    }

    // -------------------------------------------------------------------------
    // Encoding
    // -------------------------------------------------------------------------

    @Test
    void encodeMatchesRfc4648Vectors() {
        Capture cap = run(
            "local b = require 'cc.base64'\n"
                + "_capture(b.encode(''), b.encode('f'), b.encode('fo'), b.encode('foo'), b.encode('foob'), b.encode('fooba'), b.encode('foobar'))");

        assertEquals("", cap.string(0), "empty input");
        assertEquals("Zg==", cap.string(1), "'f'");
        assertEquals("Zm8=", cap.string(2), "'fo'");
        assertEquals("Zm9v", cap.string(3), "'foo'");
        assertEquals("Zm9vYg==", cap.string(4), "'foob'");
        assertEquals("Zm9vYmE=", cap.string(5), "'fooba'");
        assertEquals("Zm9vYmFy", cap.string(6), "'foobar'");
    }

    @Test
    void encodeHandlesTheFullByteRange() {
        // Every byte value round-trips, which is what matters for binary payloads (NFT images,
        // DFPWM audio). 0x00 and 0xFF are the interesting edges for the shift arithmetic.
        Capture cap = run(
            "local b = require 'cc.base64'\n" + "local parts = {}\n"
                + "for i = 0, 255 do parts[#parts + 1] = string.char(i) end\n"
                + "local all = table.concat(parts)\n"
                + "local encoded = b.encode(all)\n"
                + "_capture(#encoded, b.decode(encoded) == all, encoded:sub(1, 8))");

        assertEquals(344.0, cap.number(0), "256 bytes encode to 344 Base64 characters");
        assertEquals(true, cap.bool(1), "decoded bytes must round-trip exactly");
        assertEquals("AAECAwQF", cap.string(2), "bytes 0x00-0x03 must encode to their RFC 4648 form");
    }

    @Test
    void encodeSupportsBase64UrlAlphabet() {
        Capture cap = run("local b = require 'cc.base64'\n" + "_capture(b.encode('Test: \\255\\230', '-_'))");
        // RFC 4648 section 10 test vector.
        assertEquals("VGVzdDog_-Y=", cap.string(0));
    }

    @Test
    void encodeRejectsBadAltChars() {
        Capture cap = run(
            "local b = require 'cc.base64'\n" + "local ok, err = pcall(b.encode, 'x', '+')\n"
                + "_capture(ok, type(err), tostring(err))");
        assertEquals(false, cap.bool(0), "a one-character alt_chars must be rejected");
        assertEquals("string", cap.string(1));
        assertTrue(
            cap.string(2)
                .contains("alt_chars"),
            "error message should mention alt_chars, got: " + cap.string(2));
    }

    // -------------------------------------------------------------------------
    // Decoding
    // -------------------------------------------------------------------------

    @Test
    void decodeMatchesRfc4648Vectors() {
        Capture cap = run(
            "local b = require 'cc.base64'\n"
                + "_capture(b.decode(''), b.decode('Zg=='), b.decode('Zm8='), b.decode('Zm9v'), b.decode('Zm9vYg=='), b.decode('Zm9vYmE='), b.decode('Zm9vYmFy'))");

        assertEquals("", cap.string(0));
        assertEquals("f", cap.string(1));
        assertEquals("fo", cap.string(2));
        assertEquals("foo", cap.string(3));
        assertEquals("foob", cap.string(4));
        assertEquals("fooba", cap.string(5));
        assertEquals("foobar", cap.string(6));
    }

    @Test
    void decodeSupportsBase64UrlAlphabet() {
        Capture cap = run("local b = require 'cc.base64'\n_capture(b.decode('VGVzdDog_-Y=', '-_'))");
        Capture expected = run("_capture('Test: ' .. string.char(255, 230))");
        assertEquals(expected.string(0), cap.string(0));
    }

    @Test
    void decodeRejectsInvalidInput() {
        // Note: each result is collected into a flat scalar list. `_capture(try(a), try(b))`
        // would only expand the *last* call to multiple values, which silently shifts indices.
        Capture cap = run(
            "local b = require 'cc.base64'\n" + "local r = {}\n"
                + "for _, s in ipairs({ 'abc', 'a b c d', '!!!!', 'Zg=' }) do\n"
                + "  local v, e = b.decode(s)\n"
                + "  r[#r + 1] = v == nil and 'nil' or 'value'\n"
                + "  r[#r + 1] = type(e)\n"
                + "end\n"
                + "_capture(r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8])");

        assertEquals("nil", cap.string(0), "length not a multiple of 4 must be rejected");
        assertEquals("string", cap.string(1), "rejection must carry a reason");
        assertEquals("nil", cap.string(2), "whitespace must be rejected");
        assertEquals("nil", cap.string(4), "non-alphabet characters must be rejected");
        assertEquals("nil", cap.string(6), "missing padding must be rejected");
    }

    @Test
    void decodeReturnsNilNotErrorOnMalformedInput() {
        Capture cap = run(
            "local b = require 'cc.base64'\n" + "local ok, v, e = pcall(b.decode, 'not-valid-without-padding')\n"
                + "_capture(ok, type(v), type(e))");
        assertEquals(true, cap.bool(0), "decode reports failure by returning nil, not by throwing");
        assertEquals("nil", cap.string(1));
        assertEquals("string", cap.string(2));
    }

    // -------------------------------------------------------------------------
    // Round-trips
    // -------------------------------------------------------------------------

    @Test
    void roundTripIsStableForEveryLengthMod3() {
        Capture cap = run(
            "local b = require 'cc.base64'\n" + "local ok = true\n"
                + "for n = 0, 40 do\n"
                + "  local s = string.rep('x', n) .. string.char(n % 256)\n"
                + "  if n == 0 then s = '' end\n"
                + "  local decoded = b.decode(b.encode(s))\n"
                + "  if decoded ~= s then ok = false end\n"
                + "end\n"
                + "_capture(ok)");
        assertEquals(true, cap.bool(0), "encode/decode must round-trip for all padding cases");
    }

    @Test
    void moduleIsReachableThroughTheRomRequirePath() {
        // Guards the file location: `require "cc.base64"` maps to rom/modules/main/cc/base64.lua.
        Capture cap = run("local m = require 'cc.base64'\n_capture(type(m.encode), type(m.decode))");
        assertEquals("function", cap.string(0));
        assertEquals("function", cap.string(1));
    }
}
