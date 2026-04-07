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
 * Tests for the {@code rednet} Lua API ({@code rom/apis/rednet}).
 *
 * <p>
 * Each test loads the {@code rednet} ROM file into a fresh {@link CobaltMachine} with
 * mock {@code os}, {@code math}, and {@code peripheral} globals, then exercises the API
 * via the injected {@code _capture} helper.
 * </p>
 *
 * <p>
 * The mock peripheral tracks which channels are open per side and records every
 * {@code transmit} call in {@code _transmit_calls}. Queued OS events are stored in
 * {@code _queued_events}. Tests that need an open modem set
 * {@code _modem_sides["left"] = "modem"} and then call {@code open("left")}.
 * </p>
 */
class RednetAPITest {

    private static String rednetSource;

    /**
     * Mock preamble providing minimal {@code os}, {@code math}, and {@code peripheral}
     * globals required by {@code rednet.lua}.
     *
     * <ul>
     *   <li>{@code _modem_sides} — map of side → type; add {@code "modem"} entries to simulate
     *       attached modems before calling {@code open()}.</li>
     *   <li>{@code _open_channels[side][channel]} — tracks open channels per modem side.</li>
     *   <li>{@code _queued_events} — accumulates calls to {@code os.queueEvent}.</li>
     *   <li>{@code _transmit_calls} — accumulates {@code peripheral.call(side,"transmit",...)}
     *       invocations; each entry is {@code {channel, replyChannel, message}}.</li>
     *   <li>{@code os.pullEvent} immediately returns {@code "timer", 999} so that
     *       {@code lookup} exits after one event-loop iteration when a modem is open.</li>
     *   <li>{@code table.unpack = unpack} mirrors the shim installed by {@code bios.lua}.</li>
     * </ul>
     */
    private static final String MOCK_PREAMBLE = "table.unpack = unpack\n"
        + "_queued_events = {}\n"
        + "_transmit_calls = {}\n"
        + "_open_channels = {}\n"
        + "_modem_sides = {}\n"
        + "os = {\n"
        + "  getComputerID = function() return 42 end,\n"
        + "  startTimer    = function(t) return 999 end,\n"
        + "  cancelTimer   = function(t) end,\n"
        + "  queueEvent    = function(...) _queued_events[#_queued_events+1] = {...} end,\n"
        + "  pullEvent     = function(f) return 'timer', 999 end,\n"
        + "  pullEventRaw  = function(f) return 'timer', 999 end,\n"
        + "}\n"
        + "math = { random = function(a, b) return 12345 end }\n"
        + "peripheral = {\n"
        + "  getNames = function()\n"
        + "    local r = {}\n"
        + "    for k, _ in pairs(_modem_sides) do r[#r+1] = k end\n"
        + "    return r\n"
        + "  end,\n"
        + "  getType = function(side) return _modem_sides[side] end,\n"
        + "  call = function(side, method, ...)\n"
        + "    local args = {...}\n"
        + "    if method == 'open' then\n"
        + "      if not _open_channels[side] then _open_channels[side] = {} end\n"
        + "      _open_channels[side][args[1]] = true\n"
        + "    elseif method == 'close' then\n"
        + "      if _open_channels[side] then _open_channels[side][args[1]] = nil end\n"
        + "    elseif method == 'isOpen' then\n"
        + "      return _open_channels[side] ~= nil and (_open_channels[side][args[1]] == true)\n"
        + "    elseif method == 'transmit' then\n"
        + "      _transmit_calls[#_transmit_calls+1] = args\n"
        + "    end\n"
        + "  end,\n"
        + "}\n";

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @BeforeAll
    static void loadSource() throws IOException {
        rednetSource = readResource("/assets/computercraft/lua/rom/apis/rednet");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream is = RednetAPITest.class.getResourceAsStream(path)) {
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

    /** Runs {@code testLua} with the mock preamble and rednet source pre-loaded. */
    private static void run(CobaltMachine machine, String testLua) {
        String combined = MOCK_PREAMBLE + "\n" + rednetSource + "\n" + testLua;
        machine.loadBios(new ByteArrayInputStream(combined.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 50 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    // =========================================================================
    // open / isOpen
    // =========================================================================

    @Test
    void openErrorsOnNonStringArgument() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, err = pcall(open, 42)\n_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "open(42) must throw");
        assertTrue(((String) cap.args[1]).contains("expected string"), "Error must mention expected string");
    }

    @Test
    void openErrorsOnNonModemSide() {
        ResultCapture cap = new ResultCapture();
        // "right" is not registered in _modem_sides, so peripheral.getType returns nil
        run(buildMachine(cap), "local ok, err = pcall(open, 'right')\n_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "open('right') must throw when no modem is present");
        assertTrue(((String) cap.args[1]).contains("No such modem"), "Error must mention No such modem");
    }

    @Test
    void openSetsChannelsSoIsOpenReturnsTrue() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "_modem_sides['left'] = 'modem'\n"
                + "open('left')\n"
                + "_capture(isOpen('left'))");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "isOpen('left') must be true after open('left')");
    }

    @Test
    void isOpenSpecificReturnsFalseByDefault() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_modem_sides['left'] = 'modem'\n_capture(isOpen('left'))");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "isOpen must be false before open() is called");
    }

    @Test
    void isOpenAnyReturnsFalseWithNoModems() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(isOpen())");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "isOpen() must be false when no modem is present");
    }

    @Test
    void isOpenAnyReturnsTrueAfterOpen() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "_modem_sides['left'] = 'modem'\n"
                + "open('left')\n"
                + "_capture(isOpen())");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "isOpen() must be true when at least one modem is open");
    }

    // =========================================================================
    // close
    // =========================================================================

    @Test
    void closeSpecificModemSetsIsOpenFalse() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "_modem_sides['left'] = 'modem'\n"
                + "open('left')\n"
                + "close('left')\n"
                + "_capture(isOpen('left'))");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "isOpen('left') must be false after close('left')");
    }

    @Test
    void closeAllClosesPreviouslyOpenedModem() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "_modem_sides['left'] = 'modem'\n"
                + "open('left')\n"
                + "close()\n"
                + "_capture(isOpen())");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "isOpen() must be false after close()");
    }

    @Test
    void closeErrorsOnNonStringArgument() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, err = pcall(close, 99)\n_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "close(99) must throw");
        assertTrue(((String) cap.args[1]).contains("expected string"), "Error must mention expected string");
    }

    // =========================================================================
    // send — return value
    // =========================================================================

    @Test
    void sendReturnsTrueForLoopback() {
        ResultCapture cap = new ResultCapture();
        // Recipient == own computer ID (42) — loopback branch
        run(buildMachine(cap), "_capture(send(42, 'hello'))");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "send to self must return true");
    }

    @Test
    void sendReturnsTrueWhenOpenModemExists() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "_modem_sides['left'] = 'modem'\n"
                + "open('left')\n"
                + "_capture(send(99, 'hello'))");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "send must return true when a modem is open");
    }

    @Test
    void sendReturnsFalseWhenNoOpenModems() {
        ResultCapture cap = new ResultCapture();
        // Recipient != own ID, no modems at all
        run(buildMachine(cap), "_capture(send(99, 'hello'))");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "send must return false when no open modems exist");
    }

    @Test
    void sendReturnsFalseWhenModemPresentButNotOpened() {
        ResultCapture cap = new ResultCapture();
        // Modem is attached but open() was never called
        run(buildMachine(cap), "_modem_sides['left'] = 'modem'\n_capture(send(99, 'hello'))");
        assertNotNull(cap.args);
        assertFalse(
            (Boolean) cap.args[0],
            "send must return false when a modem is present but not opened");
    }

    // =========================================================================
    // send — side effects
    // =========================================================================

    @Test
    void sendQueuesRednetMessageEventForLoopback() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "send(42, 'hi', 'myproto')\n"
                + "_capture(\n"
                + "  #_queued_events,\n"
                + "  _queued_events[1][1],\n"
                + "  _queued_events[1][2],\n"
                + "  _queued_events[1][3],\n"
                + "  _queued_events[1][4])");
        assertNotNull(cap.args);
        assertEquals(1.0, ((Number) cap.args[0]).doubleValue(), "Exactly one event must be queued");
        assertEquals("rednet_message", cap.args[1], "Event name must be rednet_message");
        assertEquals(42.0, ((Number) cap.args[2]).doubleValue(), "Sender ID must be own computer ID");
        assertEquals("hi", cap.args[3], "Message must match");
        assertEquals("myproto", cap.args[4], "Protocol must match");
    }

    @Test
    void sendTransmitsToRecipientChannelAndRepeatChannel() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "_modem_sides['left'] = 'modem'\n"
                + "open('left')\n"
                + "send(99, 'msg')\n"
                // transmit args: {channel, replyChannel, message}
                + "_capture(#_transmit_calls, _transmit_calls[1][1], _transmit_calls[2][1])");
        assertNotNull(cap.args);
        assertEquals(2.0, ((Number) cap.args[0]).doubleValue(), "send must produce 2 transmit calls");
        assertEquals(99.0, ((Number) cap.args[1]).doubleValue(), "First transmit must target recipient channel");
        assertEquals(
            65533.0,
            ((Number) cap.args[2]).doubleValue(),
            "Second transmit must target CHANNEL_REPEAT (65533)");
    }

    // =========================================================================
    // broadcast — return value
    // =========================================================================

    @Test
    void broadcastReturnsTrueWhenModemOpen() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "_modem_sides['left'] = 'modem'\n"
                + "open('left')\n"
                + "_capture(broadcast('hello'))");
        assertNotNull(cap.args);
        assertTrue((Boolean) cap.args[0], "broadcast must return true when a modem is open");
    }

    @Test
    void broadcastReturnsFalseWithNoOpenModems() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(broadcast('hello'))");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "broadcast must return false when no open modems exist");
    }

    @Test
    void broadcastSendsToChannelBroadcast() {
        ResultCapture cap = new ResultCapture();
        // CHANNEL_BROADCAST = 65535; verify that the first transmit targets it
        run(
            buildMachine(cap),
            "_modem_sides['left'] = 'modem'\n"
                + "open('left')\n"
                + "broadcast('hello')\n"
                + "_capture(_transmit_calls[1][1])");
        assertNotNull(cap.args);
        assertEquals(
            65535.0,
            ((Number) cap.args[0]).doubleValue(),
            "broadcast must transmit to CHANNEL_BROADCAST (65535)");
    }

    // =========================================================================
    // host / unhost / lookup
    // =========================================================================

    @Test
    void lookupLocalhostReturnsOwnComputerIdWhenHosted() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "host('myproto', 'myhost')\n"
                + "_capture(lookup('myproto', 'localhost'))");
        assertNotNull(cap.args);
        assertEquals(42.0, ((Number) cap.args[0]).doubleValue(), "localhost lookup must return own computer ID");
    }

    @Test
    void lookupByExactNameReturnsOwnComputerIdWhenHosted() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "host('myproto', 'myhost')\n"
                + "_capture(lookup('myproto', 'myhost'))");
        assertNotNull(cap.args);
        assertEquals(
            42.0,
            ((Number) cap.args[0]).doubleValue(),
            "Exact hostname lookup must return own computer ID");
    }

    @Test
    void lookupReturnsNilWhenNotHosted() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "_capture(lookup('myproto', 'unknown'))");
        assertNotNull(cap.args);
        assertNull(cap.args[0], "lookup must return nil when hostname is not hosted");
    }

    @Test
    void unhostRemovesHostname() {
        ResultCapture cap = new ResultCapture();
        run(
            buildMachine(cap),
            "host('myproto', 'myhost')\n"
                + "unhost('myproto')\n"
                + "_capture(lookup('myproto', 'myhost'))");
        assertNotNull(cap.args);
        assertNull(cap.args[0], "lookup must return nil after unhost");
    }

    // =========================================================================
    // host — error cases
    // =========================================================================

    @Test
    void hostErrorsOnNonStringProtocol() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, err = pcall(host, 42, 'name')\n_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "host(42, 'name') must throw");
        assertTrue(((String) cap.args[1]).contains("expected string"), "Error must mention expected string");
    }

    @Test
    void hostErrorsOnReservedHostname() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, err = pcall(host, 'proto', 'localhost')\n_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "host('proto', 'localhost') must throw");
        assertTrue(((String) cap.args[1]).contains("Reserved hostname"), "Error must mention Reserved hostname");
    }

    @Test
    void unhostErrorsOnNonStringProtocol() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, err = pcall(unhost, 99)\n_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "unhost(99) must throw");
        assertTrue(((String) cap.args[1]).contains("expected string"), "Error must mention expected string");
    }

    @Test
    void lookupErrorsOnNonStringProtocol() {
        ResultCapture cap = new ResultCapture();
        run(buildMachine(cap), "local ok, err = pcall(lookup, 99)\n_capture(ok, err)");
        assertNotNull(cap.args);
        assertFalse((Boolean) cap.args[0], "lookup(99) must throw");
        assertTrue(((String) cap.args[1]).contains("expected string"), "Error must mention expected string");
    }
}

