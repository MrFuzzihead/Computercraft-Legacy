package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

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
 * Unit tests for {@link CobaltMachine} covering the core VM, coroutine model,
 * BigInteger API, and BitOp API as migrated to Cobalt 0.6.0.
 *
 * <p>
 * The Lua source passed to {@link CobaltMachine#loadBios} runs as a plain bios
 * chunk (NOT wrapped in {@code return function() ... end}), so {@code _capture(...)}
 * is invoked before the coroutine exits.
 * </p>
 */
class CobaltMachineTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Holds values passed from Lua back to Java via {@code _capture(...)}. */
    static class ResultCapture {

        Object[] args;
    }

    private static CobaltMachine buildMachine(ResultCapture capture) {
        ComputerCraft.bigInteger = true;
        ComputerCraft.bitop = true;
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

    /** Loads and runs a plain Lua bios chunk (not wrapped in return function). */
    private static void run(CobaltMachine machine, String lua) {
        machine.loadBios(new ByteArrayInputStream(lua.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 50 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    // -------------------------------------------------------------------------
    // Core VM tests
    // -------------------------------------------------------------------------

    @Test
    void testIsFinishedInitially() {
        assertTrue(buildMachine(new ResultCapture()).isFinished(), "New machine should be finished before loadBios");
    }

    @Test
    void testLoadBiosFinishesImmediately() {
        CobaltMachine machine = buildMachine(new ResultCapture());
        machine.loadBios(new ByteArrayInputStream("return function() end".getBytes(StandardCharsets.UTF_8)));
        assertFalse(machine.isFinished(), "Machine should not be finished before first handleEvent");
        machine.handleEvent(null, null);
        assertTrue(machine.isFinished(), "Machine should finish after running an immediate-return body");
        machine.unload();
    }

    @Test
    void testLoadBiosYieldsAndFinishesAfterResume() {
        CobaltMachine machine = buildMachine(new ResultCapture());
        machine.loadBios(new ByteArrayInputStream("coroutine.yield()".getBytes(StandardCharsets.UTF_8)));
        machine.handleEvent(null, null);
        assertFalse(machine.isFinished(), "Machine should be alive at yield");
        machine.handleEvent(null, null);
        assertTrue(machine.isFinished(), "Machine should finish after resuming past yield");
        machine.unload();
    }

    @Test
    void testUnloadFinishesMachine() {
        CobaltMachine machine = buildMachine(new ResultCapture());
        machine.loadBios(new ByteArrayInputStream("coroutine.yield()".getBytes(StandardCharsets.UTF_8)));
        machine.handleEvent(null, null);
        machine.unload();
        assertTrue(machine.isFinished(), "Machine should be finished after unload");
    }

    @Test
    void testEventFilterIsRespected() {
        CobaltMachine machine = buildMachine(new ResultCapture());
        machine.loadBios(
            new ByteArrayInputStream(
                ("coroutine.yield('my_event')\n" + "coroutine.yield('done')").getBytes(StandardCharsets.UTF_8)));
        machine.handleEvent(null, null);
        machine.handleEvent("other", null);
        assertFalse(machine.isFinished(), "Machine should ignore non-matching events");
        machine.handleEvent("my_event", null);
        assertFalse(machine.isFinished(), "Machine should still be alive after first match");
        machine.handleEvent(null, null);
        assertTrue(machine.isFinished(), "Machine should finish after consuming all yields");
        machine.unload();
    }

    @Test
    void testLuaErrorInBodyKillsMachine() {
        CobaltMachine machine = buildMachine(new ResultCapture());
        machine.loadBios(new ByteArrayInputStream("error('deliberate failure')".getBytes(StandardCharsets.UTF_8)));
        machine.handleEvent(null, null);
        assertTrue(machine.isFinished(), "Machine should be finished after unhandled Lua error");
        machine.unload();
    }

    @Test
    void testPcallCatchesErrors() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, err = pcall(function() error('caught') end)\n" + "_capture(ok, err)");
        assertNotNull(cap.args, "capture should have been called");
        assertFalse((Boolean) cap.args[0]);
        assertTrue(((String) cap.args[1]).contains("caught"));
    }

    @Test
    void testCoroutinesWork() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local co = coroutine.create(function(a,b) return a+b end)\n"
                + "local ok, r = coroutine.resume(co, 10, 32)\n"
                + "_capture(ok, r)");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0]);
        assertEquals(42.0, cap.args[1]);
    }

    // -------------------------------------------------------------------------
    // Standard library smoke tests
    // -------------------------------------------------------------------------

    @Test
    void testStringLibWorks() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(string.upper('hello'), string.len('world'), string.rep('ab',3))");
        assertNotNull(cap.args);
        assertEquals("HELLO", cap.args[0]);
        assertEquals(5.0, cap.args[1]);
        assertEquals("ababab", cap.args[2]);
    }

    @Test
    void testMathLibWorks() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(math.floor(3.7), math.abs(-5), math.max(1,2,3))");
        assertNotNull(cap.args);
        assertEquals(3.0, cap.args[0]);
        assertEquals(5.0, cap.args[1]);
        assertEquals(3.0, cap.args[2]);
    }

    @Test
    void testTableLibWorks() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local t={3,1,4,1,5}\ntable.sort(t)\n_capture(t[1],t[5],#t)");
        assertNotNull(cap.args);
        assertEquals(1.0, cap.args[0]);
        assertEquals(5.0, cap.args[1]);
        assertEquals(5.0, cap.args[2]);
    }

    @Test
    void testLoadstringWorks() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local f=assert(loadstring('return 1+2'))\n_capture(f())");
        assertNotNull(cap.args);
        assertEquals(3.0, cap.args[0]);
    }

    @Test
    void testLoadWithFunctionWorks() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local src='return 10*10'\nlocal i=0\n"
                + "local g=assert(load(function() i=i+1 if i==1 then return src end end))\n"
                + "_capture(g())");
        assertNotNull(cap.args);
        assertEquals(100.0, cap.args[0]);
    }

    @Test
    void testLoadstringBadSyntaxReturnsNil() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local f,err=loadstring('not valid lua !!!')\n_capture(f,type(err))");
        assertNotNull(cap.args);
        assertNull(cap.args[0]);
        assertEquals("string", cap.args[1]);
    }

    @Test
    void testMetatablesWork() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local mt={__add=function(a,b) return {val=a.val+b.val} end}\n" + "local a=setmetatable({val=10},mt)\n"
                + "local b=setmetatable({val=32},mt)\n"
                + "_capture((a+b).val)");
        assertNotNull(cap.args);
        assertEquals(42.0, cap.args[0]);
    }

    // -------------------------------------------------------------------------
    // BitOp API tests
    // -------------------------------------------------------------------------

    @Test
    void testBitopTobit() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(bitop.tobit(0xffffffff),bitop.tobit(1))");
        assertNotNull(cap.args);
        assertEquals(-1.0, cap.args[0]);
        assertEquals(1.0, cap.args[1]);
    }

    @Test
    void testBitopBnot() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(bitop.bnot(0),bitop.bnot(-1))");
        assertNotNull(cap.args);
        assertEquals(-1.0, cap.args[0]);
        assertEquals(0.0, cap.args[1]);
    }

    @Test
    void testBitopBand() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(bitop.band(0xFF,0x0F),bitop.band(0xAA,0xFF,0x0F))");
        assertNotNull(cap.args);
        assertEquals(15.0, cap.args[0]);
        assertEquals(10.0, cap.args[1]);
    }

    @Test
    void testBitopBor() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(bitop.bor(0xF0,0x0F),bitop.bor(1,2,4))");
        assertNotNull(cap.args);
        assertEquals(255.0, cap.args[0]);
        assertEquals(7.0, cap.args[1]);
    }

    @Test
    void testBitopBxor() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(bitop.bxor(0xFF,0x0F),bitop.bxor(0,0))");
        assertNotNull(cap.args);
        assertEquals(240.0, cap.args[0]);
        assertEquals(0.0, cap.args[1]);
    }

    @Test
    void testBitopShifts() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(bitop.lshift(1,4),bitop.rshift(256,4),bitop.arshift(-16,2))");
        assertNotNull(cap.args);
        assertEquals(16.0, cap.args[0]);
        assertEquals(16.0, cap.args[1]);
        assertEquals(-4.0, cap.args[2]);
    }

    @Test
    void testBitopTohex() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(bitop.tohex(255),bitop.tohex(255,2),bitop.tohex(255,-2))");
        assertNotNull(cap.args);
        assertEquals("000000ff", cap.args[0]);
        assertEquals("ff", cap.args[1]);
        assertEquals("FF", cap.args[2]);
    }

    @Test
    void testBitopRotate() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(bitop.rol(1,1),bitop.ror(2,1))");
        assertNotNull(cap.args);
        assertEquals(2.0, cap.args[0]);
        assertEquals(1.0, cap.args[1]);
    }

    // -------------------------------------------------------------------------
    // BigInteger API tests
    // -------------------------------------------------------------------------

    @Test
    void testBigIntegerNew() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local a=biginteger.new(42)\n" + "local b=biginteger.new('123456789012345678901234567890')\n"
                + "_capture(biginteger.tostring(a),biginteger.tostring(b))");
        assertNotNull(cap.args);
        assertEquals("42", cap.args[0]);
        assertEquals("123456789012345678901234567890", cap.args[1]);
    }

    @Test
    void testBigIntegerArithmetic() {
        ResultCapture cap = new ResultCapture();
        // Use the functional biginteger.add/mul/sub API since Lua operator dispatch
        // for __metamethods on userdata types is not supported in this Cobalt build.
        run(
            buildMachine(cap),
            "local a=biginteger.new('1000000000000000000')\n" + "_capture(biginteger.tostring(biginteger.add(a,a)),"
                + "biginteger.tostring(biginteger.mul(a,biginteger.new(3))),"
                + "biginteger.tostring(biginteger.sub(a,biginteger.new(1))))");
        assertNotNull(cap.args);
        assertEquals("2000000000000000000", cap.args[0]);
        assertEquals("3000000000000000000", cap.args[1]);
        assertEquals("999999999999999999", cap.args[2]);
    }

    @Test
    void testBigIntegerDivisionAndMod() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local a=biginteger.new(17)\nlocal b=biginteger.new(5)\n"
                + "_capture(biginteger.tostring(a/b),biginteger.tostring(a%b))");
        assertNotNull(cap.args);
        assertEquals("3", cap.args[0]);
        assertEquals("2", cap.args[1]);
    }

    @Test
    void testBigIntegerPow() {
        ResultCapture cap = new ResultCapture();
        // Use the functional biginteger.pow(base, exp) instead of the ^ operator.
        run(buildMachine(cap), "_capture(biginteger.tostring(biginteger.pow(biginteger.new(2),64)))");
        assertNotNull(cap.args);
        assertEquals("18446744073709551616", cap.args[0]);
    }

    @Test
    void testBigIntegerBitwiseOps() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local a=biginteger.new(0xFF)\nlocal b=biginteger.new(0x0F)\n"
                + "_capture(biginteger.tostring(biginteger.band(a,b)),"
                + "biginteger.tostring(biginteger.bor(a,b)),"
                + "biginteger.tostring(biginteger.bxor(a,b)),"
                + "biginteger.tostring(biginteger.shl(a,4)),"
                + "biginteger.tostring(biginteger.shr(a,4)))");
        assertNotNull(cap.args);
        assertEquals("15", cap.args[0]);
        assertEquals("255", cap.args[1]);
        assertEquals("240", cap.args[2]);
        assertEquals("4080", cap.args[3]);
        assertEquals("15", cap.args[4]);
    }

    @Test
    void testBigIntegerMinMaxAbs() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "local a=biginteger.new(100)\nlocal b=biginteger.new(200)\n"
                + "_capture(biginteger.tostring(biginteger.min(a,b)),"
                + "biginteger.tostring(biginteger.max(a,b)),"
                + "biginteger.tostring(biginteger.abs(biginteger.new(-42))))");
        assertNotNull(cap.args);
        assertEquals("100", cap.args[0]);
        assertEquals("200", cap.args[1]);
        assertEquals("42", cap.args[2]);
    }

    @Test
    void testBigIntegerGcd() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(biginteger.tostring(biginteger.gcd(biginteger.new(48),biginteger.new(18))))");
        assertNotNull(cap.args);
        assertEquals("6", cap.args[0]);
    }

    @Test
    void testBigIntegerModpow() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "_capture(biginteger.tostring("
                + "biginteger.modpow(biginteger.new(2),biginteger.new(10),biginteger.new(1000))))");
        assertNotNull(cap.args);
        assertEquals("24", cap.args[0]);
    }

    @Test
    void testBigIntegerModinv() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "_capture(biginteger.tostring(" + "biginteger.modinv(biginteger.new(3),biginteger.new(11))))");
        assertNotNull(cap.args);
        assertEquals("4", cap.args[0]);
    }

    @Test
    void testBigIntegerIsProbablePrime() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "_capture(biginteger.isProbPrime(biginteger.new(104729),50),"
                + "biginteger.isProbPrime(biginteger.new(100000),50))");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "104729 is prime");
        assertFalse((Boolean) cap.args[1], "100000 is not prime");
    }

    @Test
    void testBigIntegerToNumber() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local n=biginteger.tonumber(biginteger.new(12345))\n_capture(type(n),n)");
        assertNotNull(cap.args);
        assertEquals("number", cap.args[0]);
        assertEquals(12345.0, cap.args[1]);
    }
}
