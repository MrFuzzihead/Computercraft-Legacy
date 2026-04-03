package dan200.computercraft.core.apis;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

import org.junit.jupiter.api.Test;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;

/**
 * Unit tests for {@link WebSocketHandle}.
 *
 * <p>
 * All tests run without a live network connection. The {@link StubConnection}
 * inner class implements {@link IWebSocketConnection} so that {@code send} and
 * {@code close} delegations can be verified without a real {@link WebSocketRequest}.
 * {@code receive} is exercised using a minimal {@link ILuaContext} that returns
 * a pre-queued sequence of events.
 * </p>
 */
class WebSocketHandleTest {

    // Method indices — must match WebSocketHandle.METHOD_* constants.
    private static final int METHOD_RECEIVE = 0;
    private static final int METHOD_SEND = 1;
    private static final int METHOD_CLOSE = 2;

    // =========================================================================
    // Test infrastructure
    // =========================================================================

    /** A controllable stand-in for a live {@link WebSocketRequest}. */
    static class StubConnection implements IWebSocketConnection {

        final List<String> textSent = new ArrayList<>();
        final List<byte[]> binarySent = new ArrayList<>();
        boolean closed = false;
        boolean open = true;

        @Override
        public void sendText(String message) {
            textSent.add(message);
        }

        @Override
        public void sendBinary(byte[] data) {
            binarySent.add(data.clone());
        }

        @Override
        public void closeConnection() {
            closed = true;
            open = false;
        }

        @Override
        public boolean isConnectionOpen() {
            return open;
        }
    }

    /**
     * Returns an {@link ILuaContext} whose {@code pullEventRaw} returns events
     * from {@code events} in order; throws {@link InterruptedException} when
     * the queue is empty.
     */
    private static ILuaContext makeContext(Object[]... events) {
        Queue<Object[]> queue = new ArrayDeque<>(Arrays.asList(events));
        return new ILuaContext() {

            @Override
            public Object[] pullEvent(String filter) throws LuaException, InterruptedException {
                return pullEventRaw(filter);
            }

            @Override
            public Object[] pullEventRaw(String filter) throws InterruptedException {
                if (queue.isEmpty()) throw new InterruptedException("no more events");
                return queue.poll();
            }

            @Override
            public Object[] yield(Object[] args) throws InterruptedException {
                return pullEventRaw(null);
            }

            @Override
            public Object[] executeMainThreadTask(ILuaTask task) throws LuaException, InterruptedException {
                return null;
            }

            @Override
            public long issueMainThreadTask(ILuaTask task) throws LuaException {
                return 0;
            }
        };
    }

    private static WebSocketHandle handle(StubConnection conn) {
        return new WebSocketHandle("ws://example.com", conn);
    }

    // =========================================================================
    // getMethodNames
    // =========================================================================

    @Test
    void getMethodNamesContainsExpectedEntries() {
        String[] names = handle(new StubConnection()).getMethodNames();
        assertEquals(3, names.length);
        assertEquals("receive", names[0]);
        assertEquals("send", names[1]);
        assertEquals("close", names[2]);
    }

    // =========================================================================
    // send
    // =========================================================================

    @Test
    void sendTextDelegatesToConnection() throws LuaException, InterruptedException {
        StubConnection conn = new StubConnection();
        handle(conn).callMethod(null, METHOD_SEND, new Object[] { "hello" });
        assertEquals(1, conn.textSent.size());
        assertEquals("hello", conn.textSent.get(0));
        assertTrue(conn.binarySent.isEmpty());
    }

    @Test
    void sendBinaryWhenFlagIsTrue() throws LuaException, InterruptedException {
        StubConnection conn = new StubConnection();
        handle(conn).callMethod(null, METHOD_SEND, new Object[] { "hello", true });
        assertTrue(conn.textSent.isEmpty());
        assertEquals(1, conn.binarySent.size());
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), conn.binarySent.get(0));
    }

    @Test
    void sendTextWhenBinaryFlagIsFalse() throws LuaException, InterruptedException {
        StubConnection conn = new StubConnection();
        handle(conn).callMethod(null, METHOD_SEND, new Object[] { "hi", false });
        assertEquals(1, conn.textSent.size());
        assertEquals("hi", conn.textSent.get(0));
    }

    @Test
    void sendThrowsWhenConnectionIsClosed() {
        StubConnection conn = new StubConnection();
        conn.open = false;
        assertThrows(LuaException.class, () -> handle(conn).callMethod(null, METHOD_SEND, new Object[] { "hello" }));
    }

    @Test
    void sendThrowsOnMissingArgument() {
        assertThrows(
            LuaException.class,
            () -> handle(new StubConnection()).callMethod(null, METHOD_SEND, new Object[0]));
    }

    @Test
    void sendThrowsOnNonStringArgument() {
        assertThrows(
            LuaException.class,
            () -> handle(new StubConnection()).callMethod(null, METHOD_SEND, new Object[] { 42.0 }));
    }

    // =========================================================================
    // close
    // =========================================================================

    @Test
    void closeDelegatesToConnection() throws LuaException, InterruptedException {
        StubConnection conn = new StubConnection();
        handle(conn).callMethod(null, METHOD_CLOSE, new Object[0]);
        assertTrue(conn.closed);
    }

    @Test
    void closeIsIdempotent() throws LuaException, InterruptedException {
        StubConnection conn = new StubConnection();
        WebSocketHandle h = handle(conn);
        h.callMethod(null, METHOD_CLOSE, new Object[0]);
        h.callMethod(null, METHOD_CLOSE, new Object[0]); // second call must not throw
    }

    // =========================================================================
    // receive — message events
    // =========================================================================

    @Test
    void receiveReturnsTextMessage() throws LuaException, InterruptedException {
        StubConnection conn = new StubConnection();
        ILuaContext ctx = makeContext(new Object[] { "websocket_message", "ws://example.com", "hello", false });

        Object[] result = handle(conn).callMethod(ctx, METHOD_RECEIVE, new Object[0]);

        assertNotNull(result);
        assertEquals(2, result.length);
        assertEquals("hello", result[0]);
        assertEquals(false, result[1]);
    }

    @Test
    void receiveReturnsBinaryMessage() throws LuaException, InterruptedException {
        StubConnection conn = new StubConnection();
        byte[] data = "data".getBytes(StandardCharsets.UTF_8);
        ILuaContext ctx = makeContext(new Object[] { "websocket_message", "ws://example.com", data, true });

        Object[] result = handle(conn).callMethod(ctx, METHOD_RECEIVE, new Object[0]);

        assertNotNull(result);
        assertArrayEquals(data, (byte[]) result[0]);
        assertEquals(true, result[1]);
    }

    // =========================================================================
    // receive — closed event
    // =========================================================================

    @Test
    void receiveReturnsNullOnWebsocketClosed() throws LuaException, InterruptedException {
        StubConnection conn = new StubConnection();
        ILuaContext ctx = makeContext(new Object[] { "websocket_closed", "ws://example.com" });

        Object[] result = handle(conn).callMethod(ctx, METHOD_RECEIVE, new Object[0]);

        assertNull(result);
    }

    // =========================================================================
    // receive — URL filtering
    // =========================================================================

    @Test
    void receiveIgnoresMessagesForDifferentUrl() throws LuaException, InterruptedException {
        StubConnection conn = new StubConnection();
        ILuaContext ctx = makeContext(
            // Different URL — must be skipped
            new Object[] { "websocket_message", "ws://other.com", "not-for-us", false },
            // Correct URL — must be returned
            new Object[] { "websocket_message", "ws://example.com", "hello", false });

        Object[] result = handle(conn).callMethod(ctx, METHOD_RECEIVE, new Object[0]);

        assertNotNull(result);
        assertEquals("hello", result[0]);
    }

    @Test
    void receiveIgnoresUnrelatedEvents() throws LuaException, InterruptedException {
        StubConnection conn = new StubConnection();
        ILuaContext ctx = makeContext(
            new Object[] { "http_success", "ws://example.com", "not-ws" },
            new Object[] { "websocket_message", "ws://example.com", "target", false });

        Object[] result = handle(conn).callMethod(ctx, METHOD_RECEIVE, new Object[0]);

        assertNotNull(result);
        assertEquals("target", result[0]);
    }

    // =========================================================================
    // receive — timeout
    // =========================================================================

    @Test
    void receiveReturnsNullAfterTimeoutExpires() throws LuaException, InterruptedException {
        StubConnection conn = new StubConnection();
        // An unrelated event is returned; after it the wall-clock deadline (0 s) is past.
        ILuaContext ctx = makeContext(new Object[] { "unrelated_event" });

        // timeout = 0.0 → deadline is already in the past after the first yield
        Object[] result = handle(conn).callMethod(ctx, METHOD_RECEIVE, new Object[] { 0.0 });

        assertNull(result);
    }

    @Test
    void receiveWithTimeoutStillReturnsMessageBeforeDeadline() throws LuaException, InterruptedException {
        StubConnection conn = new StubConnection();
        // Plenty of time (60 s); the message should be returned immediately.
        ILuaContext ctx = makeContext(new Object[] { "websocket_message", "ws://example.com", "hi", false });

        Object[] result = handle(conn).callMethod(ctx, METHOD_RECEIVE, new Object[] { 60.0 });

        assertNotNull(result);
        assertEquals("hi", result[0]);
    }
}
