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
 * Tests for the pure helpers of the {@code cc.audio.tts} Lua module
 * ({@code rom/modules/main/cc/audio/tts.lua}).
 *
 * <p>
 * Only the I/O-free surface is exercised here ({@code tts.url} and
 * {@code tts.pcm_samples}); the streaming/HTTP paths require a live speaker and
 * event loop and are covered by integration usage instead.
 * </p>
 */
class CcAudioTtsTest {

    private static String expectSource;
    private static String dfpwmSource;
    private static String ttsSource;

    @BeforeAll
    static void loadSources() throws IOException {
        expectSource = readResource("/assets/computercraft/lua/rom/modules/main/cc/expect.lua");
        dfpwmSource = readResource("/assets/computercraft/lua/rom/modules/main/cc/audio/dfpwm.lua");
        ttsSource = readResource("/assets/computercraft/lua/rom/modules/main/cc/audio/tts.lua");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream is = CcAudioTtsTest.class.getResourceAsStream(path)) {
            assertNotNull(is, path + " must be on the test classpath");
            try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                return scanner.hasNext() ? scanner.next() : "";
            }
        }
    }

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

    /** Loads {@code cc.expect}, {@code cc.audio.dfpwm} and {@code cc.audio.tts}. */
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
            + "  package.loaded['cc.audio.dfpwm'] = fn()\n"
            + "end\n"
            + "do\n"
            + "  local fn, err = loadstring([=[\n"
            + ttsSource
            + "\n]=])\n"
            + "  if not fn then error('cc.audio.tts load failed: ' .. tostring(err)) end\n"
            + "  tts = fn()\n"
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

    // ── url building ────────────────────────────────────────────────────────────

    @Test
    void testUrlBuildsQueryWithDefaults() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "tts.endpoint = 'https://x.example/say'\n" + "_capture(tts.url('hi there'))");
        assertEquals("https://x.example/say?text=hi%20there&lang=en&format=pcm_u8", cap.args[0]);
    }

    @Test
    void testUrlPercentEncodesSpecialChars() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "_capture(tts.url('a&b=c?', { endpoint = 'http://h/s', format = 'pcm_u8', lang = 'fr', voice = 'v1' }))");
        assertEquals("http://h/s?text=a%26b%3Dc%3F&lang=fr&format=pcm_u8&voice=v1", cap.args[0]);
    }

    @Test
    void testUrlAppendsWithAmpersandWhenEndpointHasQuery() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "_capture(tts.url('x', { endpoint = 'http://h/s?client=tw-ob' }))");
        assertEquals("http://h/s?client=tw-ob&text=x&lang=en&format=pcm_u8", cap.args[0]);
    }

    @Test
    void testUrlWithoutEndpointErrors() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), buildPreamble(), "local ok, err = pcall(tts.url, 'hi')\n" + "_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0]);
        assertTrue(((String) cap.args[1]).contains("endpoint"));
    }

    @Test
    void testUrlWithBadFormatErrors() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local ok, err = pcall(tts.url, 'hi', { endpoint = 'http://h', format = 'mp3' })\n" + "_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0]);
        assertTrue(((String) cap.args[1]).contains("mp3"));
    }

    // ── pcm conversion ──────────────────────────────────────────────────────────

    @Test
    void testPcmU8SubtractsBias() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local s = tts.pcm_samples(string.char(0, 128, 255), 'pcm_u8')\n" + "_capture(s[1], s[2], s[3])");
        assertEquals(-128.0, ((Number) cap.args[0]).doubleValue());
        assertEquals(0.0, ((Number) cap.args[1]).doubleValue());
        assertEquals(127.0, ((Number) cap.args[2]).doubleValue());
    }

    @Test
    void testPcmS8TwosComplement() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local s = tts.pcm_samples(string.char(0, 127, 128, 255), 'pcm_s8')\n"
                + "_capture(s[1], s[2], s[3], s[4])");
        assertEquals(0.0, ((Number) cap.args[0]).doubleValue());
        assertEquals(127.0, ((Number) cap.args[1]).doubleValue());
        assertEquals(-128.0, ((Number) cap.args[2]).doubleValue());
        assertEquals(-1.0, ((Number) cap.args[3]).doubleValue());
    }

    @Test
    void testPcmBadFormatErrors() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            buildPreamble(),
            "local ok, err = pcall(tts.pcm_samples, 'abc', 'pcm_s16')\n" + "_capture(ok, err)");
        assertEquals(Boolean.FALSE, cap.args[0]);
    }
}
