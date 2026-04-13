package dan200.computercraft.core.apis;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dan200.computercraft.api.lua.LuaException;

/**
 * Unit tests for {@link HTTPResponse} covering all six methods exposed to Lua:
 * {@code readLine}, {@code readAll}, {@code read}, {@code close},
 * {@code getResponseCode}, and {@code getResponseHeaders}.
 *
 * <p>
 * Tests call {@link HTTPResponse#callMethod} directly with {@code null} context
 * and {@code null} args where no arguments are required.
 * </p>
 */
class HTTPResponseTest {

    // Method indices must match the order declared in HTTPResponse.getMethodNames().
    private static final int METHOD_READ_LINE = 0;
    private static final int METHOD_READ_ALL = 1;
    private static final int METHOD_READ = 2;
    private static final int METHOD_CLOSE = 3;
    private static final int METHOD_GET_RESPONSE_CODE = 4;
    private static final int METHOD_GET_RESPONSE_HEADERS = 5;

    // =========================================================================
    // Helpers
    // =========================================================================

    private static HTTPResponse make(String body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/plain");
        headers.put("X-Custom", "foo, bar");
        return new HTTPResponse(200, "OK", body.getBytes(StandardCharsets.UTF_8), headers);
    }

    private static String str(Object[] result) {
        assertNotNull(result);
        assertEquals(1, result.length);
        assertInstanceOf(byte[].class, result[0]);
        return new String((byte[]) result[0], StandardCharsets.UTF_8);
    }

    /** Asserts the method returned a single nil value — the CC:Tweaked EOF sentinel. */
    private static void assertNilReturn(Object[] result) {
        assertNotNull(result, "expected {nil} array, got null");
        assertEquals(1, result.length);
        assertNull(result[0]);
    }

    // =========================================================================
    // getResponseCode
    // =========================================================================

    @Test
    void getResponseCodeReturnsCode() throws LuaException, InterruptedException {
        HTTPResponse resp = new HTTPResponse(404, "Not Found", new byte[0], new HashMap<>());

        Object[] result = resp.callMethod(null, METHOD_GET_RESPONSE_CODE, new Object[0]);

        assertNotNull(result);
        assertEquals(2, result.length, "getResponseCode must return exactly two values");
        assertEquals(404, result[0], "first return value must be the numeric status code");
        assertEquals("Not Found", result[1], "second return value must be the status message");
    }

    @Test
    void getResponseCodeReturnsBothValuesFor200() throws LuaException, InterruptedException {
        HTTPResponse resp = new HTTPResponse(200, "OK", new byte[0], new HashMap<>());

        Object[] result = resp.callMethod(null, METHOD_GET_RESPONSE_CODE, new Object[0]);

        assertNotNull(result);
        assertEquals(2, result.length);
        assertEquals(200, result[0]);
        assertEquals("OK", result[1]);
    }

    @Test
    void getResponseCodeMessageIsEmptyStringWhenNull() throws LuaException, InterruptedException {
        HTTPResponse resp = new HTTPResponse(200, null, new byte[0], new HashMap<>());

        Object[] result = resp.callMethod(null, METHOD_GET_RESPONSE_CODE, new Object[0]);

        assertNotNull(result);
        assertEquals("", result[1], "null message must be normalised to empty string");
    }

    // =========================================================================
    // getResponseHeaders
    // =========================================================================

    @Test
    void getResponseHeadersReturnsFlatStringMap() throws LuaException, InterruptedException {
        HTTPResponse resp = make("body");

        Object[] result = resp.callMethod(null, METHOD_GET_RESPONSE_HEADERS, new Object[0]);

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) result[0];
        assertEquals("text/plain", headers.get("Content-Type"));
        assertEquals("foo, bar", headers.get("X-Custom"));
    }

    @Test
    void getResponseHeadersIsEmptyWhenNoHeaders() throws LuaException, InterruptedException {
        HTTPResponse resp = new HTTPResponse(200, "", new byte[0], new HashMap<>());

        Object[] result = resp.callMethod(null, METHOD_GET_RESPONSE_HEADERS, new Object[0]);

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) result[0];
        assertTrue(headers.isEmpty());
    }

    // =========================================================================
    // readAll
    // =========================================================================

    @Test
    void readAllReturnsEntireBody() throws LuaException, InterruptedException {
        HTTPResponse resp = make("hello world");

        assertEquals("hello world", str(resp.callMethod(null, METHOD_READ_ALL, new Object[0])));
    }

    @Test
    void readAllReturnsEmptyStringForEmptyBody() throws LuaException, InterruptedException {
        HTTPResponse resp = new HTTPResponse(200, "", new byte[0], new HashMap<>());

        Object[] result = resp.callMethod(null, METHOD_READ_ALL, new Object[0]);

        assertNotNull(result);
        assertEquals(1, result.length);
        // empty body → empty string (not null)
        assertNotNull(result[0]);
    }

    @Test
    void readAllAfterCloseReturnsNull() throws LuaException, InterruptedException {
        HTTPResponse resp = make("hello");
        resp.callMethod(null, METHOD_CLOSE, new Object[0]);

        assertNull(resp.callMethod(null, METHOD_READ_ALL, new Object[0]));
    }

    // =========================================================================
    // readLine
    // =========================================================================

    @Test
    void readLineReturnsSingleLineWithoutNewline() throws LuaException, InterruptedException {
        HTTPResponse resp = make("hello\nworld");

        assertEquals("hello", str(resp.callMethod(null, METHOD_READ_LINE, new Object[0])));
        assertEquals("world", str(resp.callMethod(null, METHOD_READ_LINE, new Object[0])));
        assertNilReturn(resp.callMethod(null, METHOD_READ_LINE, new Object[0]));
    }

    @Test
    void readLineWithTrailingNewlineIncludesNewline() throws LuaException, InterruptedException {
        HTTPResponse resp = make("hello\nworld");

        assertEquals("hello\n", str(resp.callMethod(null, METHOD_READ_LINE, new Object[] { true })));
        assertEquals("world", str(resp.callMethod(null, METHOD_READ_LINE, new Object[] { true })));
    }

    @Test
    void readLineHandlesCRLF() throws LuaException, InterruptedException {
        HTTPResponse resp = make("line1\r\nline2");

        assertEquals("line1", str(resp.callMethod(null, METHOD_READ_LINE, new Object[0])));
        assertEquals("line2", str(resp.callMethod(null, METHOD_READ_LINE, new Object[0])));
    }

    @Test
    void readLineWithTrailingNewlineIncludesCRLF() throws LuaException, InterruptedException {
        HTTPResponse resp = make("line1\r\nline2");

        assertEquals("line1\r\n", str(resp.callMethod(null, METHOD_READ_LINE, new Object[] { true })));
    }

    @Test
    void readLineReturnsNilAtEOF() throws LuaException, InterruptedException {
        HTTPResponse resp = new HTTPResponse(200, "", new byte[0], new HashMap<>());

        Object[] result = resp.callMethod(null, METHOD_READ_LINE, new Object[0]);

        assertNotNull(result);
        assertEquals(1, result.length);
        assertNull(result[0]);
    }

    @Test
    void readLineAfterCloseReturnsNull() throws LuaException, InterruptedException {
        HTTPResponse resp = make("hello");
        resp.callMethod(null, METHOD_CLOSE, new Object[0]);

        assertNull(resp.callMethod(null, METHOD_READ_LINE, new Object[0]));
    }

    // =========================================================================
    // read
    // =========================================================================

    @Test
    void readReturnsSingleCharacterAsString() throws LuaException, InterruptedException {
        HTTPResponse resp = make("abc");

        assertEquals("a", str(resp.callMethod(null, METHOD_READ, new Object[0])));
        assertEquals("b", str(resp.callMethod(null, METHOD_READ, new Object[0])));
        assertEquals("c", str(resp.callMethod(null, METHOD_READ, new Object[0])));
    }

    @Test
    void readWithCountReturnsMultipleChars() throws LuaException, InterruptedException {
        HTTPResponse resp = make("hello");

        assertEquals("hel", str(resp.callMethod(null, METHOD_READ, new Object[] { 3.0 })));
        assertEquals("lo", str(resp.callMethod(null, METHOD_READ, new Object[] { 10.0 })));
    }

    @Test
    void readReturnsNilAtEOF() throws LuaException, InterruptedException {
        HTTPResponse resp = new HTTPResponse(200, "", new byte[0], new HashMap<>());

        Object[] result = resp.callMethod(null, METHOD_READ, new Object[0]);

        assertNotNull(result);
        assertEquals(1, result.length);
        assertNull(result[0]);
    }

    @Test
    void readAfterCloseReturnsNull() throws LuaException, InterruptedException {
        HTTPResponse resp = make("hello");
        resp.callMethod(null, METHOD_CLOSE, new Object[0]);

        assertNull(resp.callMethod(null, METHOD_READ, new Object[0]));
    }

    // =========================================================================
    // close
    // =========================================================================

    @Test
    void closeIsIdempotent() throws LuaException, InterruptedException {
        HTTPResponse resp = make("hello");

        resp.callMethod(null, METHOD_CLOSE, new Object[0]);
        resp.callMethod(null, METHOD_CLOSE, new Object[0]); // second close should not throw
    }
}
