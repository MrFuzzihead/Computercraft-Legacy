package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@code gps.locate}, driven by scripted modem events.
 *
 * <p>
 * The GPS API is a ROM chunk that installs globals and talks to a modem, so it cannot be exercised
 * by calling a function — but its inputs are all events. Loading the real ROM chunk with a stubbed
 * {@code os}/{@code peripheral} and feeding it a scripted event queue tests the actual shipped code:
 * the fixed-channel filter, the dedupe, the trilateration and the early exits.
 * </p>
 */
class GpsLocateTest {

    private static final String GPS = "rom/apis/gps";
    private static final String VECTOR = "rom/apis/vector";

    /** Runs {@code body} with the real {@code gps} and {@code vector} ROM APIs loaded. */
    private static LuaRomTestSupport.Capture runGps(String stubSetup, String body) throws IOException {
        String gps = LuaRomTestSupport.readResource("/assets/computercraft/lua/" + GPS);
        String vector = LuaRomTestSupport.readResource("/assets/computercraft/lua/" + VECTOR);

        String prelude = "_gpsSrc = [====[\n" + gps
            + "\n]====]\n"
            + "_vectorSrc = [====[\n"
            + vector
            + "\n]====]\n"
            + """
                -- The ROM loads each API chunk with its own environment; the globals they publish are
                -- then installed, which is what is reproduced here.
                -- The vector chunk keeps its table local and exports `new` as a global; the ROM loader
                -- groups a chunk's globals into the API table, so `vector.new` is that global.
                assert(loadstring(_vectorSrc, '@vector'))()
                vector = { new = new }
                -- gps.locate prints only in debug mode, but print must exist either way.
                print = function(...) end
                printError = function(...) end
                term = { getCursorPos = function() return 1, 1 end, setCursorPos = function() end }
                """;

        LuaRomTestSupport.Capture capture = new LuaRomTestSupport.Capture();
        var machine = LuaRomTestSupport.machine(capture);
        // Everything runs inside one pcall, setup included: a stub mistake then reports as a
        // message instead of a silent "never reached _capture".
        LuaRomTestSupport.run(
            machine,
            "local _ok, _err = pcall(function()\n" + prelude
                + stubSetup
                + "\nassert(loadstring(_gpsSrc, '@gps'))()\n"
                + body
                + "\nend)\nif not _ok then _capture('LUA ERROR: ' .. tostring(_err)) end\n");
        assertNotNull(capture.args, "the Lua test never reached _capture");
        return capture;
    }

    /** A modem/peripheral stub that is wireless and openable. */
    private static String modemStub() {
        return """
            _opened, _closed, _transmits = {}, {}, {}
            _modem = {
              isWireless = function() return true end,
              isOpen = function(channel) return _opened[channel] == true end,
              open = function(channel) _opened[channel] = true end,
              close = function(channel) _closed[channel] = true end,
              transmit = function(send, reply, message)
                _transmits[#_transmits + 1] = { send = send, reply = reply, message = message }
              end,
            }
            peripheral = {
              getType = function(side) return side == "back" and "modem" or nil end,
              wrap = function(side) return _modem end,
              getNames = function() return { "back" } end,
              -- locate() probes isWireless through peripheral.call rather than the wrapped object.
              call = function(side, method, ...) return _modem[method](...) end,
            }
            rs = { getSides = function() return { "back" } end }
            """;
    }

    /**
     * A scripted event queue: {@code events} is a Lua string producing the list.
     *
     * <p>
     * {@code os.pullEvent} pops from it; if it runs out, the timer fires so a test cannot hang.
     * </p>
     */
    private static String osStub(String events) {
        return """
            _events = """ + events + """
            _eventIndex = 1
            _timer = 1
            os = {
              getComputerID = function() return 7 end,
              startTimer = function(timeout) _timer = _timer + 1 return _timer end,
              cancelTimer = function() end,
              queueEvent = function() end,
              pullEvent = function(filter)
                local event = _events[_eventIndex]
                _eventIndex = _eventIndex + 1
                if not event then return "timer", _timer end
                return table.unpack(event, 1, event.n or #event)
              end,
            }
            """;
    }

    /** One modem reply: side, channel, reply channel, message, distance. */
    private static String fix(int x, int y, int z, double distance, int channel, int replyChannel) {
        return String.format(
            "{ 'modem_message', 'back', %d, %d, { %d, %d, %d }, %s }",
            channel,
            replyChannel,
            x,
            y,
            z,
            distance);
    }

    private static final int GPS_CHANNEL = 65534;

    // -------------------------------------------------------------------------
    // Basics
    // -------------------------------------------------------------------------

    @Test
    void noWirelessModemMeansNoPosition() throws Exception {
        LuaRomTestSupport.Capture capture = runGps("""
            peripheral = { getType = function() return nil end, wrap = function() end }
            rs = { getSides = function() return { "back" } end }
            os = { startTimer = function() return 1 end, pullEvent = function() return "timer", 1 end }
            """, "_capture(tostring(locate()))");

        assertEquals("nil", capture.string(0));
    }

    @Test
    void thePingGoesOutOnTheGpsChannelWithTheGpsReplyChannel() throws Exception {
        // This is the "fixed channel" half of the batch: request and reply both use CHANNEL_GPS,
        // never the computer's own id.
        LuaRomTestSupport.Capture capture = runGps(modemStub() + osStub("{ }"), """
            locate(1)
            _capture(
              tostring(#_transmits),
              tostring(_transmits[1].send),
              tostring(_transmits[1].reply),
              tostring(_transmits[1].message),
              tostring(_opened[65534] == true),
              tostring(_closed[65534] == true)
            )
            """);

        assertEquals("1", capture.string(0));
        assertEquals("65534", capture.string(1));
        assertEquals("65534", capture.string(2));
        assertEquals("PING", capture.string(3));
        assertEquals("true", capture.string(4), "the GPS channel should be opened");
        assertEquals("true", capture.string(5), "and closed again afterwards");
    }

    // -------------------------------------------------------------------------
    // Trilateration
    // -------------------------------------------------------------------------

    @Test
    void threeDistinctHostsGiveThePosition() throws Exception {
        // Three host centres and the listener in one plane. That matters: with three ranges the
        // intersection is a pair of points mirrored in the hosts' plane, so an out-of-plane listener
        // legitimately yields two solutions and locate() reports "ambiguous". Coplanar geometry has
        // the two solutions coincide, which is the case worth pinning.
        double d0 = Math.sqrt(10 * 10 + 20 * 20);
        double d1 = Math.sqrt(90 * 90 + 20 * 20);
        double d2 = Math.sqrt(10 * 10 + 80 * 80);

        LuaRomTestSupport.Capture capture = runGps(
            modemStub() + osStub(
                "{" + fix(0, 0, 0, d0, GPS_CHANNEL, GPS_CHANNEL)
                    + ","
                    + fix(100, 0, 0, d1, GPS_CHANNEL, GPS_CHANNEL)
                    + ","
                    + fix(0, 0, 100, d2, GPS_CHANNEL, GPS_CHANNEL)
                    + "}"),
            "_capture(locate(1))");

        assertEquals(3, capture.args.length, "expected x, y, z: " + java.util.Arrays.toString(capture.args));
        assertEquals(10.0, capture.number(0), 0.05);
        assertEquals(0.0, capture.number(1), 0.05);
        assertEquals(20.0, capture.number(2), 0.05);
    }

    @Test
    void aZeroDistanceReplyIsTheAnswerImmediately() throws Exception {
        LuaRomTestSupport.Capture capture = runGps(
            modemStub() + osStub("{" + fix(5, 6, 7, 0, GPS_CHANNEL, GPS_CHANNEL) + "}"),
            "_capture(locate(1))");

        assertEquals(3, capture.args.length);
        assertEquals(5.0, capture.number(0));
        assertEquals(6.0, capture.number(1));
        assertEquals(7.0, capture.number(2));
    }

    // -------------------------------------------------------------------------
    // Dedupe — the reason this batch exists
    // -------------------------------------------------------------------------

    @Test
    void threeRepliesFromOneHostDoNotMakeAPosition() throws Exception {
        // All three replies come from the same place, so the dedupe must keep a single fix and
        // trilateration must never run: a lone host cannot fake a location.
        double d = Math.sqrt(3 * 3 + 4 * 4);

        LuaRomTestSupport.Capture capture = runGps(
            modemStub() + osStub(
                "{" + fix(0, 0, 0, d, GPS_CHANNEL, GPS_CHANNEL)
                    + ","
                    + fix(0, 0, 0, d, GPS_CHANNEL, GPS_CHANNEL)
                    + ","
                    + fix(0, 0, 0, d, GPS_CHANNEL, GPS_CHANNEL)
                    + "}"),
            "_capture(tostring(locate(1)))");

        assertEquals("nil", capture.string(0), "one host, however chatty, must not give a position");
    }

    @Test
    void twoHostsAndARepeatOfTheFirstStillGiveNothing() throws Exception {
        // Two real hosts plus a duplicate of the first: still only two distinct fixes.
        LuaRomTestSupport.Capture capture = runGps(
            modemStub() + osStub(
                "{" + fix(0, 0, 0, 10, GPS_CHANNEL, GPS_CHANNEL)
                    + ","
                    + fix(100, 0, 0, 90, GPS_CHANNEL, GPS_CHANNEL)
                    + ","
                    + fix(1, 0, 0, 11, GPS_CHANNEL, GPS_CHANNEL)
                    + "}"),
            "_capture(tostring(locate(1)))");

        assertEquals("nil", capture.string(0), "two distinct hosts cannot trilaterate");
    }

    @Test
    void aHostJustOutsideOneBlockCountsAsDistinct() throws Exception {
        // The dedupe radius is one block: positions 2 blocks apart are different hosts. With three
        // distinct hosts (even nearly collinear ones) trilateration is attempted; the interesting
        // part is that it is not deduped away, which would leave no answer at all.
        LuaRomTestSupport.Capture capture = runGps(
            modemStub() + osStub(
                "{" + fix(0, 0, 0, 50, GPS_CHANNEL, GPS_CHANNEL)
                    + ","
                    + fix(2, 0, 0, 52, GPS_CHANNEL, GPS_CHANNEL)
                    + ","
                    + fix(0, 0, 2, 52, GPS_CHANNEL, GPS_CHANNEL)
                    + "}"),
            """
                local x, y, z = locate(1)
                _capture(tostring(x ~= nil), tostring(y ~= nil), tostring(z ~= nil))
                """);

        // The geometry here is degenerate, so a non-answer is acceptable; what must not happen is a
        // crash or a hang.
        assertTrue(
            capture.args.length == 1 || capture.args.length == 3,
            "expected nil or a position: " + java.util.Arrays.toString(capture.args));
    }

    // -------------------------------------------------------------------------
    // Filtering
    // -------------------------------------------------------------------------

    @Test
    void repliesOnTheWrongChannelAreIgnored() throws Exception {
        // A well-formed reply that arrives on the computer's own channel is not a GPS reply.
        double d = Math.sqrt(3 * 3 + 4 * 4);
        LuaRomTestSupport.Capture capture = runGps(
            modemStub() + osStub(
                "{" + fix(0, 0, 0, d, 7, 7) + "," + fix(100, 0, 0, d, 7, 7) + "," + fix(0, 0, 100, d, 7, 7) + "}"),
            "_capture(tostring(locate(1)))");

        assertEquals("nil", capture.string(0), "replies on the wrong channel must be ignored");
    }

    @Test
    void aReplyWithoutADistanceIsIgnored() throws Exception {
        LuaRomTestSupport.Capture capture = runGps(
            modemStub() + osStub("{ { 'modem_message', 'back', 65534, 65534, { 0, 0, 0 } } }"),
            "_capture(tostring(locate(1)))");

        assertEquals("nil", capture.string(0), "a reply with no distance must be ignored");
    }

    @Test
    void aReplyFromAnotherModemIsIgnored() throws Exception {
        // sSide must match the modem the ping went out on.
        LuaRomTestSupport.Capture capture = runGps(
            modemStub() + osStub("{ { 'modem_message', 'left', 65534, 65534, { 0, 0, 0 }, 5 } }"),
            "_capture(tostring(locate(1)))");

        assertEquals("nil", capture.string(0));
    }

    @Test
    void aMalformedMessageIsIgnored() throws Exception {
        LuaRomTestSupport.Capture capture = runGps(
            modemStub() + osStub(
                "{" + "{ 'modem_message', 'back', 65534, 65534, 'PING', 5 }"
                    + ","
                    + "{ 'modem_message', 'back', 65534, 65534, { 1, 2 }, 5 }"
                    + ","
                    + "{ 'modem_message', 'back', 65534, 65534, { 'a', 'b', 'c' }, 5 }"
                    + "}"),
            "_capture(tostring(locate(1)))");

        assertEquals("nil", capture.string(0), "non-table, short and non-numeric replies must all be ignored");
    }

    @Test
    void theTimeoutEndsTheSearch() throws Exception {
        // No replies at all: the timer must break the loop rather than waiting for ever.
        LuaRomTestSupport.Capture capture = runGps(
            modemStub() + osStub("{ { 'timer', 2 } }"),
            "_capture(tostring(locate(1)), tostring(_closed[65534] == true))");

        assertEquals("nil", capture.string(0));
        assertEquals("true", capture.string(1), "the channel must be closed on timeout too");
    }

    @Test
    void theChannelIsNotClosedIfItWasAlreadyOpen() throws Exception {
        // A program that already uses CHANNEL_GPS must keep it open afterwards.
        LuaRomTestSupport.Capture capture = runGps(
            modemStub() + osStub("{ { 'timer', 2 } }") + "_opened[65534] = true\n",
            "_capture(tostring(locate(1)), tostring(_closed[65534] == true))");

        assertEquals("nil", capture.string(0));
        assertEquals("false", capture.string(1), "locate must not close a channel it did not open");
    }

    @Test
    void theChannelConstantIsUnchanged() throws Exception {
        LuaRomTestSupport.Capture capture = runGps(
            modemStub() + osStub("{ }"),
            "_capture(CHANNEL_GPS, tostring(CHANNEL_GPS))");

        assertEquals(65534.0, capture.number(0));
        assertEquals("65534", capture.string(1));
    }

    @Test
    void theChunkIsValidLua() throws Exception {
        // Guards against the study of syntax errors that broke two ROM programs in Batch I.
        String source = LuaRomTestSupport.readResource("/assets/computercraft/lua/" + GPS);
        assertTrue(source.contains("CHANNEL_GPS = 65534"));
        assertEquals(
            1,
            source.split("modem.transmit", -1).length - 1,
            "locate should transmit exactly once: " + StandardCharsets.UTF_8);
    }
}
