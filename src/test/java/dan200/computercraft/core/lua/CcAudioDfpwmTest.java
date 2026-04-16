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
 * Tests for the {@code cc.audio.dfpwm} Lua module
 * ({@code rom/modules/main/cc/audio/dfpwm.lua}).
 *
 * <p>
 * Each test builds a minimal Lua environment: {@code cc.expect} is pre-loaded,
 * a tiny {@code require} shim is installed, and then {@code cc.audio.dfpwm} is
 * loaded and assigned to the global {@code dfpwm}. A {@code _capture} global
 * accumulates return values for assertions.
 * </p>
 */
class CcAudioDfpwmTest {

    private static String expectSource;
    private static String dfpwmSource;

    // ── Setup ──────────────────────────────────────────────────────────────────

    @BeforeAll
    static void loadSources() throws IOException {
        expectSource = readResource("/assets/computercraft/lua/rom/modules/main/cc/expect.lua");
        dfpwmSource = readResource("/assets/computercraft/lua/rom/modules/main/cc/audio/dfpwm.lua");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream is = CcAudioDfpwmTest.class.getResourceAsStream(path)) {
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

    /** Builds the require/package preamble and loads {@code dfpwm} as a global. */
    private String buildPreamble() {
        return "package = { loaded = {} }\n" + "do\n"
            + "  local fn, err = loadstring([=[\n"
            + expectSource
            + "\n]=])\n"
            + "  if not fn then error('cc.expect load failed: ' .. tostring(err)) end\n"
            + "  package.loaded['cc.expect'] = fn()\n"
            + "end\n"
            + "function require(name)\n"
            + "  if package.loaded[name] ~= nil then return package.loaded[name] end\n"
            + "  error('module \\'' .. name .. '\\' not found')\n"
            + "end\n"
            + "do\n"
            + "  local fn, err = loadstring([=[\n"
            + dfpwmSource
            + "\n]=])\n"
            + "  if not fn then error('cc.audio.dfpwm load failed: ' .. tostring(err)) end\n"
            + "  dfpwm = fn()\n"
            + "end\n";
    }

    private static void run(CobaltMachine machine, String preamble, String testLua) {
        String combined = preamble + "\n" + testLua;
        machine.loadBios(new ByteArrayInputStream(combined.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 50 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    // ── make_encoder / make_decoder ────────────────────────────────────────────

    @Test
    void testMakeEncoderReturnsFunction() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "local enc = dfpwm.make_encoder()\n" + "_capture(type(enc))");
        assertEquals("function", cap.args[0]);
    }

    @Test
    void testMakeDecoderReturnsFunction() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "local dec = dfpwm.make_decoder()\n" + "_capture(type(dec))");
        assertEquals("function", cap.args[0]);
    }

    // ── encode ─────────────────────────────────────────────────────────────────

    @Test
    void testEncodeEmptyTableReturnsEmptyTable() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "local out = dfpwm.encode({})\n" + "_capture(#out)");
        assertEquals(0.0, cap.args[0]);
    }

    @Test
    void testEncodeEightSamplesProducesOneByte() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "local out = dfpwm.encode({0,0,0,0,0,0,0,0})\n" + "_capture(#out)");
        assertEquals(1.0, cap.args[0], "8 samples → 1 DFPWM byte");
    }

    @Test
    void testEncodeSixteenSamplesProducesTwoBytes() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local out = dfpwm.encode({0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0})\n" + "_capture(#out)");
        assertEquals(2.0, cap.args[0], "16 samples → 2 DFPWM bytes");
    }

    @Test
    void testEncodeOutputBytesAreInRange() {
        ResultCapture cap = new ResultCapture();
        // Generate 64 samples of a simple sine-like wave, confirm all bytes in [0,255].
        run(
            buildMachine(cap),
            buildPreamble(),
            "local samples = {}\n" + "for i = 1, 64 do\n"
                + "  local v = math.floor(math.sin(i * 0.3) * 64)\n"
                + "  samples[i] = v\n"
                + "end\n"
                + "local out = dfpwm.encode(samples)\n"
                + "local ok = true\n"
                + "for _, b in ipairs(out) do\n"
                + "  if b < 0 or b > 255 then ok = false end\n"
                + "end\n"
                + "_capture(ok, #out)");
        assertEquals(Boolean.TRUE, cap.args[0], "all bytes in [0,255]");
        assertEquals(8.0, cap.args[1], "64 samples → 8 bytes");
    }

    @Test
    void testEncodeTypeErrorOnNonTable() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "local ok, err = pcall(dfpwm.encode, 'hello')\n" + "_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0]);
        assertTrue(
            ((String) cap.args[1]).contains("expected"),
            "Error should mention expected type; got: " + cap.args[1]);
    }

    @Test
    void testEncodeTypeErrorOnNil() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "local ok, err = pcall(dfpwm.encode, nil)\n" + "_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0]);
    }

    // ── decode ─────────────────────────────────────────────────────────────────

    @Test
    void testDecodeEmptyTableReturnsEmptyTable() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "local out = dfpwm.decode({})\n" + "_capture(#out)");
        assertEquals(0.0, cap.args[0]);
    }

    @Test
    void testDecodeOneByteProducesEightSamples() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "local out = dfpwm.decode({0})\n" + "_capture(#out)");
        assertEquals(8.0, cap.args[0], "1 DFPWM byte → 8 PCM samples");
    }

    @Test
    void testDecodeTwoBytesProducesSixteenSamples() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "local out = dfpwm.decode({0, 255})\n" + "_capture(#out)");
        assertEquals(16.0, cap.args[0], "2 DFPWM bytes → 16 PCM samples");
    }

    @Test
    void testDecodeOutputSamplesAreInRange() {
        ResultCapture cap = new ResultCapture();
        // Decode 8 arbitrary bytes; confirm all PCM values are in [-128, 127].
        run(
            buildMachine(cap),
            buildPreamble(),
            "local out = dfpwm.decode({0xAA, 0x55, 0xFF, 0x00, 0x3C, 0xC3, 0x0F, 0xF0})\n" + "local ok = true\n"
                + "for _, v in ipairs(out) do\n"
                + "  if v < -128 or v > 127 then ok = false end\n"
                + "end\n"
                + "_capture(ok, #out)");
        assertEquals(Boolean.TRUE, cap.args[0], "all PCM values in [-128, 127]");
        assertEquals(64.0, cap.args[1], "8 bytes → 64 PCM samples");
    }

    @Test
    void testDecodeTypeErrorOnNonTable() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "local ok, err = pcall(dfpwm.decode, 42)\n" + "_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0]);
        assertTrue(
            ((String) cap.args[1]).contains("expected"),
            "Error should mention expected type; got: " + cap.args[1]);
    }

    @Test
    void testDecodeTypeErrorOnNil() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "local ok, err = pcall(dfpwm.decode, nil)\n" + "_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0]);
    }

    // ── encode/decode round-trip ───────────────────────────────────────────────

    @Test
    void testRoundTripAllZerosSilence() {
        ResultCapture cap = new ResultCapture();
        // A stream of all-zero samples encodes to a predictable pattern; decoding
        // must produce values that converge toward 0 (the LPF settles).
        // We just verify the round-trip doesn't error and produces the right sizes.
        run(
            buildMachine(cap),
            buildPreamble(),
            "local samples = {}\n" + "for i = 1, 64 do samples[i] = 0 end\n"
                + "local encoded = dfpwm.encode(samples)\n"
                + "local decoded = dfpwm.decode(encoded)\n"
                + "_capture(#encoded, #decoded)");
        assertEquals(8.0, cap.args[0], "64 samples → 8 encoded bytes");
        assertEquals(64.0, cap.args[1], "8 bytes → 64 decoded samples");
    }

    @Test
    void testRoundTripMaxAmplitude() {
        ResultCapture cap = new ResultCapture();
        // Alternate between +127 and -128 — maximum possible excursion.
        run(
            buildMachine(cap),
            buildPreamble(),
            "local samples = {}\n" + "for i = 1, 64 do\n"
                + "  samples[i] = (i % 2 == 0) and 127 or -128\n"
                + "end\n"
                + "local encoded = dfpwm.encode(samples)\n"
                + "local decoded = dfpwm.decode(encoded)\n"
                + "_capture(#encoded, #decoded)");
        assertEquals(8.0, cap.args[0]);
        assertEquals(64.0, cap.args[1]);
    }

    @Test
    void testStatefulEncoderPreservesStateAcrossCalls() {
        ResultCapture cap = new ResultCapture();
        // Encoding in two 8-sample calls must equal encoding the 16 samples at once.
        run(
            buildMachine(cap),
            buildPreamble(),
            "local s1 = {0, 10, 20, 30, 20, 10, 0, -10}\n" + "local s2 = {-20, -30, -20, -10, 0, 10, 20, 30}\n"
                + "local combined = {}\n"
                + "for _, v in ipairs(s1) do combined[#combined + 1] = v end\n"
                + "for _, v in ipairs(s2) do combined[#combined + 1] = v end\n"
                + "local enc_once  = dfpwm.encode(combined)\n"
                + "local enc_state = dfpwm.make_encoder()\n"
                + "local part1 = enc_state(s1)\n"
                + "local part2 = enc_state(s2)\n"
                + "local match = (part1[1] == enc_once[1]) and (part2[1] == enc_once[2])\n"
                + "_capture(match)");
        assertEquals(
            Boolean.TRUE,
            cap.args[0],
            "Stateful encoder must produce the same bytes as a single encode() call");
    }

    @Test
    void testStatefulDecoderPreservesStateAcrossCalls() {
        ResultCapture cap = new ResultCapture();
        // Decoding in two 1-byte calls must equal decoding both bytes at once.
        run(
            buildMachine(cap),
            buildPreamble(),
            "local bytes = {0xAA, 0x55}\n" + "local dec_once  = dfpwm.decode(bytes)\n"
                + "local dec_state = dfpwm.make_decoder()\n"
                + "local part1 = dec_state({bytes[1]})\n"
                + "local part2 = dec_state({bytes[2]})\n"
                + "local match = true\n"
                + "for i = 1, 8  do if part1[i] ~= dec_once[i]     then match = false end end\n"
                + "for i = 1, 8  do if part2[i] ~= dec_once[8 + i] then match = false end end\n"
                + "_capture(match)");
        assertEquals(
            Boolean.TRUE,
            cap.args[0],
            "Stateful decoder must produce the same samples as a single decode() call");
    }

    // ── Independent encoder/decoder instances ─────────────────────────────────

    @Test
    void testMultipleEncoderInstancesAreIndependent() {
        ResultCapture cap = new ResultCapture();
        // Priming enc1 must NOT affect enc2. Verify by comparing enc2 against a third
        // fresh encoder (enc3) — they must agree, proving enc1's state didn't leak.
        run(
            buildMachine(cap),
            buildPreamble(),
            "local enc1 = dfpwm.make_encoder()\n" + "local enc2 = dfpwm.make_encoder()\n"
                + "local enc3 = dfpwm.make_encoder()\n"
                // Heavily prime enc1 so its state diverges from fresh.
                + "for i = 1, 20 do enc1({127,127,127,127,127,127,127,127}) end\n"
                // enc2 and enc3 are still fresh — must produce identical output.
                + "local b2 = enc2({0, 10, 20, 30, 20, 10, 0, -10})\n"
                + "local b3 = enc3({0, 10, 20, 30, 20, 10, 0, -10})\n"
                + "_capture(b2[1] == b3[1])");
        assertEquals(
            Boolean.TRUE,
            cap.args[0],
            "Priming enc1 must not affect enc2; enc2 and enc3 (both fresh) must agree");
    }

    @Test
    void testMultipleDecoderInstancesAreIndependent() {
        ResultCapture cap = new ResultCapture();
        // Priming dec1 must NOT affect dec2. Verify by comparing dec2 against a third
        // fresh decoder (dec3) — they must agree, proving dec1's state didn't leak.
        run(
            buildMachine(cap),
            buildPreamble(),
            "local dec1 = dfpwm.make_decoder()\n" + "local dec2 = dfpwm.make_decoder()\n"
                + "local dec3 = dfpwm.make_decoder()\n"
                // Heavily prime dec1 so its state diverges from fresh.
                + "for i = 1, 20 do dec1({0xFF}) end\n"
                // dec2 and dec3 are still fresh — must produce identical output.
                + "local s2 = dec2({0xAA})\n"
                + "local s3 = dec3({0xAA})\n"
                + "local match = true\n"
                + "for i = 1, 8 do if s2[i] ~= s3[i] then match = false end end\n"
                + "_capture(match)");
        assertEquals(
            Boolean.TRUE,
            cap.args[0],
            "Priming dec1 must not affect dec2; dec2 and dec3 (both fresh) must agree");
    }

    // ── make_encoder / make_decoder type checking ──────────────────────────────

    @Test
    void testEncoderFunctionTypeErrorOnNonTable() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local enc = dfpwm.make_encoder()\n" + "local ok, err = pcall(enc, 'not a table')\n" + "_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0]);
        assertTrue(
            ((String) cap.args[1]).contains("expected"),
            "Error should mention expected type; got: " + cap.args[1]);
    }

    @Test
    void testDecoderFunctionTypeErrorOnNonTable() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local dec = dfpwm.make_decoder()\n" + "local ok, err = pcall(dec, 'not a table')\n" + "_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0]);
        assertTrue(
            ((String) cap.args[1]).contains("expected"),
            "Error should mention expected type; got: " + cap.args[1]);
    }
}
