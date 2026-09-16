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
 * Unit tests for the Lua-level {@code http.get}, {@code http.post}, and {@code http.request}
 * wrappers defined in {@code bios.lua}.
 *
 * <p>
 * The tests extract the HTTP install section from {@code bios.lua} and run it against a
 * mock {@code http} (native) table and a mock {@code os.pullEvent} backed by a
 * pre-populated event queue. This avoids real network calls while exercising every code
 * path in the Lua wrappers.
 * </p>
 *
 * <p>
 * Mock event format: each entry in {@code _events} is a table whose elements are the
 * return values of one {@code os.pullEvent()} call, e.g.
 * {@code {"http_success", url, responseHandle}} or
 * {@code {"http_failure", url, errMsg, responseHandleOrNil}}.
 * </p>
 */
class HTTPAPITest {

    // =========================================================================
    // Shared state
    // =========================================================================

    /** The HTTP-install section extracted verbatim from bios.lua. */
    private static String httpSection;

    // =========================================================================
    // Setup
    // =========================================================================

    @BeforeAll
    static void extractHttpSection() throws IOException {
        String biosSource;
        try (InputStream is = HTTPAPITest.class.getResourceAsStream("/assets/computercraft/lua/bios.lua")) {
            assertNotNull(is, "bios.lua must be on the test classpath");
            try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                biosSource = scanner.hasNext() ? scanner.next() : "";
            }
        }

        int start = biosSource.indexOf("\n-- Install the lua part of the HTTP api");
        int end = biosSource.indexOf("\n-- Install the lua part of the FS api");
        assertTrue(start >= 0, "bios.lua must contain the HTTP api install block");
        assertTrue(end > start, "bios.lua must contain the FS api install block after the HTTP block");
        httpSection = biosSource.substring(start, end);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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

    private static void run(CobaltMachine machine, String lua) {
        machine.loadBios(new ByteArrayInputStream(lua.getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 200 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        machine.unload();
    }

    /**
     * Common preamble injected before the bios http section in every test.
     *
     * <p>
     * Defines:
     * <ul>
     * <li>{@code _events} — a queue table; each entry is an array that {@code os.pullEvent}
     * will {@code unpack} as its return values.</li>
     * <li>{@code _last_req} — captures the raw arguments received by the native
     * {@code nativeHTTPRequest} mock. Indices 1–6 correspond to
     * (url, body, headers, method, timeout, binary).</li>
     * <li>Mock {@code os} with {@code pullEvent} (pops from {@code _events}) and a
     * no-op {@code queueEvent}.</li>
     * <li>Mock {@code http} with a {@code request} function (populates {@code _last_req}
     * and returns {@code true}), plus minimal stubs for {@code checkURL} and
     * {@code websocket} that the bios http section references.</li>
     * </ul>
     * After this preamble, {@code httpSection} (from bios.lua) is appended, which
     * replaces {@code http.request/get/post} with wrappers while capturing the original
     * {@code request} as {@code nativeHTTPRequest}.
     * </p>
     */
    private static final String PREAMBLE = "local _events = {}\n" + "local _last_req = {}\n"
        + "os = {\n"
        + "    pullEvent = function()\n"
        + "        if #_events > 0 then\n"
        + "            return unpack(table.remove(_events, 1), 1, 5)\n"
        + "        end\n"
        + "        error('event queue exhausted')\n"
        + "    end,\n"
        + "    queueEvent = function() end\n"
        + "}\n"
        + "http = {\n"
        + "    request = function(...)\n"
        + "        _last_req = {...}\n"
        + "        for _, event in ipairs(_events) do\n"
        + "            local slot = event[1] == 'http_success' and 4 or 5\n"
        + "            event[slot] = _last_req[7]\n"
        + "        end\n"
        + "        return true\n"
        + "    end,\n"
        + "    checkURL = function() return true end,\n"
        + "    websocket = function() return false, 'no ws' end\n"
        + "}\n";

    // =========================================================================
    // http.get — success / failure
    // =========================================================================

    /** http.get returns the response handle as its sole return value on success. */
    @Test
    void getSuccessReturnsSingleHandle() {
        ResultCapture capture = new ResultCapture();
        CobaltMachine machine = buildMachine(capture);
        String lua = PREAMBLE + httpSection
            + "local _resp = {}\n"
            + "_events = { {'http_success', 'http://test.com', _resp} }\n"
            + "local r1, r2, r3 = http.get('http://test.com')\n"
            + "_capture(r1 == _resp, r2, r3)\n";
        run(machine, lua);
        assertNotNull(capture.args, "_capture must have been called");
        assertEquals(Boolean.TRUE, capture.args[0], "r1 should be the response handle");
        assertNull(capture.args[1], "r2 should be nil on success");
        assertNull(capture.args[2], "r3 should be nil on success");
    }

    /** http.get returns (nil, errMsg) when failure has no accompanying response handle. */
    @Test
    void getFailureReturnsTwoValuesWhenNoResponseHandle() {
        ResultCapture capture = new ResultCapture();
        CobaltMachine machine = buildMachine(capture);
        String lua = PREAMBLE + httpSection
            + "_events = { {'http_failure', 'http://test.com', 'Connection refused'} }\n"
            + "local r1, r2, r3 = http.get('http://test.com')\n"
            + "_capture(r1, r2, r3)\n";
        run(machine, lua);
        assertNotNull(capture.args);
        assertNull(capture.args[0], "r1 should be nil");
        assertEquals("Connection refused", capture.args[1], "r2 should be the error message");
        assertNull(capture.args[2], "r3 should be nil when no response handle");
    }

    /**
     * http.get returns (nil, errMsg, responseHandle) when the server replied with an
     * error status and a body (matching the 1.80pr1 behaviour).
     */
    @Test
    void getFailureReturnsThreeValuesWithResponseHandle() {
        ResultCapture capture = new ResultCapture();
        CobaltMachine machine = buildMachine(capture);
        String lua = PREAMBLE + httpSection
            + "local _errResp = {}\n"
            + "_events = { {'http_failure', 'http://test.com', 'Not Found', _errResp} }\n"
            + "local r1, r2, r3 = http.get('http://test.com')\n"
            + "_capture(r1, r2, r3 == _errResp)\n";
        run(machine, lua);
        assertNotNull(capture.args);
        assertNull(capture.args[0], "r1 should be nil");
        assertEquals("Not Found", capture.args[1], "r2 should be the error message");
        assertEquals(Boolean.TRUE, capture.args[2], "r3 should be the error response handle");
    }

    // =========================================================================
    // http.get — binary flag (1.80pr1)
    // =========================================================================

    /** binary=true is forwarded as the 6th positional arg to nativeHTTPRequest. */
    @Test
    void getBinaryTruePassedToNativeRequest() {
        ResultCapture capture = new ResultCapture();
        CobaltMachine machine = buildMachine(capture);
        String lua = PREAMBLE + httpSection
            + "_events = { {'http_success', 'http://test.com', {}} }\n"
            + "http.get('http://test.com', nil, true)\n"
            + "_capture(_last_req[1], _last_req[6])\n";
        run(machine, lua);
        assertNotNull(capture.args);
        assertEquals("http://test.com", capture.args[0], "arg[1] should be the url");
        assertEquals(Boolean.TRUE, capture.args[1], "arg[6] should be binary=true");
    }

    /** binary=false is forwarded as the 6th positional arg to nativeHTTPRequest. */
    @Test
    void getBinaryFalsePassedToNativeRequest() {
        ResultCapture capture = new ResultCapture();
        CobaltMachine machine = buildMachine(capture);
        String lua = PREAMBLE + httpSection
            + "_events = { {'http_success', 'http://test.com', {}} }\n"
            + "http.get('http://test.com', nil, false)\n"
            + "_capture(_last_req[6])\n";
        run(machine, lua);
        assertNotNull(capture.args);
        assertEquals(Boolean.FALSE, capture.args[0], "arg[6] should be binary=false");
    }

    // =========================================================================
    // http.get — table argument form (1.80pr1.6)
    // =========================================================================

    /** http.get({url=, binary=, timeout=}) unpacks all three fields. */
    @Test
    void getTableArgUnpacksUrlBinaryAndTimeout() {
        ResultCapture capture = new ResultCapture();
        CobaltMachine machine = buildMachine(capture);
        String lua = PREAMBLE + httpSection
            + "_events = { {'http_success', 'http://test.com', {}} }\n"
            + "http.get({ url='http://test.com', binary=true, timeout=5 })\n"
            + "_capture(_last_req[1], _last_req[5], _last_req[6])\n";
        run(machine, lua);
        assertNotNull(capture.args);
        assertEquals("http://test.com", capture.args[0], "arg[1] = url");
        assertEquals(5.0, ((Number) capture.args[1]).doubleValue(), 0.001, "arg[5] = timeout (seconds)");
        assertEquals(Boolean.TRUE, capture.args[2], "arg[6] = binary");
    }

    /** http.get({url=}) still returns the response handle on success. */
    @Test
    void getTableArgReturnsHandleOnSuccess() {
        ResultCapture capture = new ResultCapture();
        CobaltMachine machine = buildMachine(capture);
        String lua = PREAMBLE + httpSection
            + "local _resp = {}\n"
            + "_events = { {'http_success', 'http://test.com', _resp} }\n"
            + "local r1, r2 = http.get({ url='http://test.com' })\n"
            + "_capture(r1 == _resp, r2)\n";
        run(machine, lua);
        assertNotNull(capture.args);
        assertEquals(Boolean.TRUE, capture.args[0], "r1 should be the response handle");
        assertNull(capture.args[1], "r2 should be nil on success");
    }

    // =========================================================================
    // http.post
    // =========================================================================

    /** http.post returns the response handle on success. */
    @Test
    void postSuccessReturnsSingleHandle() {
        ResultCapture capture = new ResultCapture();
        CobaltMachine machine = buildMachine(capture);
        String lua = PREAMBLE + httpSection
            + "local _resp = {}\n"
            + "_events = { {'http_success', 'http://test.com', _resp} }\n"
            + "local r1, r2 = http.post('http://test.com', 'body')\n"
            + "_capture(r1 == _resp, r2)\n";
        run(machine, lua);
        assertNotNull(capture.args);
        assertEquals(Boolean.TRUE, capture.args[0], "r1 should be the response handle");
        assertNull(capture.args[1], "r2 should be nil on success");
    }

    /** binary=true on http.post is forwarded as arg[6] to nativeHTTPRequest. */
    @Test
    void postBinaryFlagPassedToNativeRequest() {
        ResultCapture capture = new ResultCapture();
        CobaltMachine machine = buildMachine(capture);
        String lua = PREAMBLE + httpSection
            + "_events = { {'http_success', 'http://test.com', {}} }\n"
            + "http.post('http://test.com', 'data', nil, true)\n"
            + "_capture(_last_req[1], _last_req[2], _last_req[6])\n";
        run(machine, lua);
        assertNotNull(capture.args);
        assertEquals("http://test.com", capture.args[0], "arg[1] = url");
        assertEquals("data", capture.args[1], "arg[2] = body");
        assertEquals(Boolean.TRUE, capture.args[2], "arg[6] = binary");
    }

    /** http.post({url=, body=, binary=}) unpacks body and binary from the table. */
    @Test
    void postTableArgUnpacksBodyAndBinary() {
        ResultCapture capture = new ResultCapture();
        CobaltMachine machine = buildMachine(capture);
        String lua = PREAMBLE + httpSection
            + "_events = { {'http_success', 'http://test.com', {}} }\n"
            + "http.post({ url='http://test.com', body='payload', binary=true })\n"
            + "_capture(_last_req[1], _last_req[2], _last_req[6])\n";
        run(machine, lua);
        assertNotNull(capture.args);
        assertEquals("http://test.com", capture.args[0], "arg[1] = url");
        assertEquals("payload", capture.args[1], "arg[2] = body from opts.body");
        assertEquals(Boolean.TRUE, capture.args[2], "arg[6] = binary");
    }

    /** http.post({url=, body=, method=}) forwards opts.method to nativeHTTPRequest. */
    @Test
    void postTableArgForwardsMethod() {
        ResultCapture capture = new ResultCapture();
        CobaltMachine machine = buildMachine(capture);
        String lua = PREAMBLE + httpSection
            + "_events = { {'http_success', 'http://test.com', {}} }\n"
            + "http.post({ url='http://test.com', body='data', method='PUT' })\n"
            + "_capture(_last_req[1], _last_req[2], _last_req[4])\n";
        run(machine, lua);
        assertNotNull(capture.args);
        assertEquals("http://test.com", capture.args[0], "arg[1] = url");
        assertEquals("data", capture.args[1], "arg[2] = body");
        assertEquals("PUT", capture.args[2], "arg[4] = method");
    }

    // =========================================================================
    // http.request — table argument form
    // =========================================================================

    /** http.request({url=, body=, method=, timeout=}) forwards all four fields. */
    @Test
    void requestTableArgPassesMethodAndTimeout() {
        ResultCapture capture = new ResultCapture();
        CobaltMachine machine = buildMachine(capture);
        // http.request is async; no event needed since the native mock returns true immediately.
        String lua = PREAMBLE + httpSection
            + "http.request({ url='http://test.com', body='data', method='PATCH', timeout=10 })\n"
            + "_capture(_last_req[1], _last_req[2], _last_req[4], _last_req[5])\n";
        run(machine, lua);
        assertNotNull(capture.args);
        assertEquals("http://test.com", capture.args[0], "arg[1] = url");
        assertEquals("data", capture.args[1], "arg[2] = body");
        assertEquals("PATCH", capture.args[2], "arg[4] = method");
        assertEquals(10.0, ((Number) capture.args[3]).doubleValue(), 0.001, "arg[5] = timeout seconds");
    }

    /** http.request with positional args forwards method, timeout, and binary correctly. */
    @Test
    void requestPositionalArgsForwardedToNative() {
        ResultCapture capture = new ResultCapture();
        CobaltMachine machine = buildMachine(capture);
        String lua = PREAMBLE + httpSection
            + "http.request('http://test.com', 'body', nil, 'PUT', 3, true)\n"
            + "_capture(_last_req[1], _last_req[2], _last_req[4], _last_req[5], _last_req[6])\n";
        run(machine, lua);
        assertNotNull(capture.args);
        assertEquals("http://test.com", capture.args[0], "arg[1] = url");
        assertEquals("body", capture.args[1], "arg[2] = body");
        assertEquals("PUT", capture.args[2], "arg[4] = method");
        assertEquals(3.0, ((Number) capture.args[3]).doubleValue(), 0.001, "arg[5] = timeout seconds");
        assertEquals(Boolean.TRUE, capture.args[4], "arg[6] = binary");
    }

    /** Broadcast each event to both suspended callers, as parallel.waitForAll does. */
    private static final String CONCURRENT_PREAMBLE = ("local requests, queued = {}, {}\n" + "http = {\n"
        + "request = function(...) requests[#requests + 1] = {...}; return true end,\n"
        + "websocket = function(...) requests[#requests + 1] = {...}; return true end,\n"
        + "checkURL = function() return true end,\n"
        + "}\n"
        + "os = {\n"
        + "pullEvent = function()\n"
        + "local event = {coroutine.yield()}\n"
        + "if event[1] == 'terminate' then error('Terminated', 0) end\n"
        + "return unpack(event)\n"
        + "end,\n"
        + "queueEvent = function(...) queued[#queued + 1] = {...} end,\n"
        + "}\n"
        + "local function resume(co, ...)\n"
        + "if coroutine.status(co) ~= 'dead' then\n"
        + "local ok, err = coroutine.resume(co, ...)\n"
        + "assert(ok, err)\n"
        + "end\n"
        + "end\n");

    @Test
    void concurrentGetAndPostToSameUrlReceiveOnlyTheirOwnCompletion() {
        for (boolean failure : new boolean[] { false, true }) {
            ResultCapture capture = new ResultCapture();
            String lua = CONCURRENT_PREAMBLE + httpSection
                + ("local a, b\n" + "local first = coroutine.create(function() a = {http.get('http://same')} end)\n"
                    + "local second = coroutine.create(function() b = {http.post({url='http://same', body='b'})} end)\n"
                    + "resume(first); resume(second)\n"
                    + "local id1, id2 = requests[1][7], requests[2][7]\n"
                    + "assert(type(id1) == 'number' and id1 ~= id2)\n"
                    + "-- Untagged async events, other URLs and unrelated events must not complete either call.\n"
                    + "for _, co in ipairs({first, second}) do\n"
                    + "resume(co, 'http_success', 'http://same', 'async')\n"
                    + "resume(co, 'http_failure', 'http://same', 'async failure')\n"
                    + "resume(co, 'http_success', 'http://other', 'other', id1)\n"
                    + "resume(co, 'timer', 123)\n"
                    + "end\n"
                    + "assert(a == nil and b == nil)\n")
                + (failure
                    ? ("resume(first, 'http_failure', 'http://same', 'failed', 'error body', id2)\n"
                        + "resume(second, 'http_failure', 'http://same', 'failed', 'error body', id2)\n"
                        + "assert(a == nil and b[1] == nil and b[2] == 'failed' and b[3] == 'error body')\n")
                    : ("resume(first, 'http_success', 'http://same', 'second', id2)\n"
                        + "resume(second, 'http_success', 'http://same', 'second', id2)\n"
                        + "assert(a == nil and b[1] == 'second')\n"))
                + ("resume(first, 'http_success', 'http://same', 'first', id1)\n" + "assert(a[1] == 'first')\n"
                    + "_capture(true)\n");
            run(buildMachine(capture), lua);
            assertArrayEquals(new Object[] { true }, capture.args);
        }
    }

    @Test
    void concurrentWebsocketsToSameUrlReceiveOnlyTheirOwnCompletion() {
        for (String outcome : new String[] { "success", "failure" }) {
            ResultCapture capture = new ResultCapture();
            String lua = CONCURRENT_PREAMBLE + httpSection
                + ("local a, b\n" + "local first = coroutine.create(function() a = {http.websocket('ws://same')} end)\n"
                    + "local second = coroutine.create(function() b = {http.websocket('ws://same')} end)\n"
                    + "resume(first); resume(second)\n"
                    + "local id1, id2 = requests[1][3], requests[2][3]\n"
                    + "assert(type(id1) == 'number' and id1 ~= id2)\n"
                    + "resume(first, 'websocket_success', 'ws://same', 'async')\n"
                    + "resume(second, 'websocket_failure', 'ws://same', 'async error')\n"
                    + "resume(first, 'websocket_success', 'ws://other', 'other', id1)\n"
                    + "assert(a == nil and b == nil)\n")
                + "local outcome = '"
                + outcome
                + "'\n"
                + ("resume(first, 'websocket_' .. outcome, 'ws://same', 'second', id2)\n"
                    + "resume(second, 'websocket_' .. outcome, 'ws://same', 'second', id2)\n"
                    + "assert(a == nil)\n"
                    + "if outcome == 'success' then assert(b[1] == 'second')\n"
                    + "else assert(b[1] == false and b[2] == 'second') end\n"
                    + "resume(first, 'websocket_success', 'ws://same', 'first', id1)\n"
                    + "assert(a[1] == 'first')\n"
                    + "_capture(true)\n");
            run(buildMachine(capture), lua);
            assertArrayEquals(new Object[] { true }, capture.args);
        }
    }

    @Test
    void immediateRejectionsReturnWithoutWaitingOrQueuingSyncFailures() {
        ResultCapture capture = new ResultCapture();
        String lua = CONCURRENT_PREAMBLE
            + ("http.request = function() return false, 'denied' end\n"
                + "http.websocket = function() return false, 'denied' end\n"
                + "os.pullEvent = function() error('must not wait') end\n")
            + httpSection
            + ("local a, b = http.get('http://same')\n" + "local c, d = http.websocket('ws://same')\n"
                + "assert(a == nil and b == 'denied' and c == false and d == 'denied')\n"
                + "assert(#queued == 0)\n"
                + "http.request('http://same')\n"
                + "http.websocketAsync('ws://same')\n"
                + "assert(#queued == 2 and #queued[1] == 3 and #queued[2] == 3)\n"
                + "assert(queued[1][1] == 'http_failure' and queued[2][1] == 'websocket_failure')\n"
                + "_capture(true)\n");
        run(buildMachine(capture), lua);
        assertArrayEquals(new Object[] { true }, capture.args);
    }

    @Test
    void asyncCallsRemainUntaggedAndTerminateStillInterruptsWaiters() {
        ResultCapture capture = new ResultCapture();
        String lua = CONCURRENT_PREAMBLE + httpSection
            + ("http.request('http://same')\n" + "http.websocketAsync('ws://same')\n"
                + "assert(requests[1][7] == nil and requests[2][3] == nil)\n"
                + "for _, call in ipairs({\n"
                + "function() http.get('http://same') end,\n"
                + "function() http.websocket('ws://same') end,\n"
                + "}) do\n"
                + "local co = coroutine.create(call)\n"
                + "resume(co)\n"
                + "local ok, err = coroutine.resume(co, 'terminate')\n"
                + "assert(not ok and err == 'Terminated')\n"
                + "end\n"
                + "_capture(true)\n");
        run(buildMachine(capture), lua);
        assertArrayEquals(new Object[] { true }, capture.args);
    }

}
