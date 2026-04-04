package dan200.computercraft.core.apis;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.IComputerEnvironment;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.terminal.Terminal;

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

    /**
     * Minimal {@link IAPIEnvironment} stub. The only method exercised by
     * {@link WebSocketHandle} is {@link #queueEvent}, which feeds events directly
     * into {@link #sharedQueue} so the scheduler's timeout event is visible to the
     * paired {@link ILuaContext} returned by {@link #makeContextFromQueue}.
     */
    static class StubEnvironment implements IAPIEnvironment {

        final LinkedBlockingQueue<Object[]> sharedQueue;

        StubEnvironment(LinkedBlockingQueue<Object[]> sharedQueue) {
            this.sharedQueue = sharedQueue;
        }

        @Override
        public void queueEvent(String name, Object[] args) {
            Object[] event = new Object[1 + (args != null ? args.length : 0)];
            event[0] = name;
            if (args != null) System.arraycopy(args, 0, event, 1, args.length);
            sharedQueue.add(event);
        }

        @Override
        public Computer getComputer() {
            return null;
        }

        @Override
        public int getComputerID() {
            return 0;
        }

        @Override
        public IComputerEnvironment getComputerEnvironment() {
            return null;
        }

        @Override
        public Terminal getTerminal() {
            return null;
        }

        @Override
        public FileSystem getFileSystem() {
            return null;
        }

        @Override
        public void shutdown() {}

        @Override
        public void reboot() {}

        @Override
        public void setOutput(int side, int output) {}

        @Override
        public int getOutput(int side) {
            return 0;
        }

        @Override
        public int getInput(int side) {
            return 0;
        }

        @Override
        public void setBundledOutput(int side, int combination) {}

        @Override
        public int getBundledOutput(int side) {
            return 0;
        }

        @Override
        public int getBundledInput(int side) {
            return 0;
        }

        @Override
        public void setPeripheralChangeListener(IAPIEnvironment.IPeripheralChangeListener listener) {}

        @Override
        public IPeripheral getPeripheral(int side) {
            return null;
        }

        @Override
        public String getLabel() {
            return null;
        }

        @Override
        public void setLabel(String label) {}
    }

    /**
     * Returns an {@link ILuaContext} whose {@code pullEventRaw} blocks on
     * {@code queue} until an event is available, or throws
     * {@link InterruptedException} after 2 s as a safety valve.
     *
     * <p>
     * Used by timeout tests so events queued by the
     * {@code TIMEOUT_SCHEDULER} background thread (via
     * {@link StubEnvironment#queueEvent}) are visible to the blocking receive loop.
     */
    private static ILuaContext makeContextFromQueue(LinkedBlockingQueue<Object[]> queue) {
        return new ILuaContext() {

            @Override
            public Object[] pullEvent(String filter) throws LuaException, InterruptedException {
                return pullEventRaw(filter);
            }

            @Override
            public Object[] pullEventRaw(String filter) throws InterruptedException {
                Object[] event = queue.poll(2, TimeUnit.SECONDS);
                if (event == null) throw new InterruptedException("test timed out waiting for event");
                return event;
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
        return new WebSocketHandle("ws://example.com", conn, new StubEnvironment(new LinkedBlockingQueue<>()));
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
        // The implementation uses ISO_8859_1 (one byte per char), which is the
        // correct inverse of the Cobalt bridge's text-mode string conversion.
        assertArrayEquals("hello".getBytes(StandardCharsets.ISO_8859_1), conn.binarySent.get(0));
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
        // timeout = 0 → the scheduler fires immediately, calling StubEnvironment.queueEvent
        // which feeds websocket_receive_timeout into the shared queue, waking up pullEventRaw.
        LinkedBlockingQueue<Object[]> sharedQueue = new LinkedBlockingQueue<>();
        StubEnvironment env = new StubEnvironment(sharedQueue);
        WebSocketHandle h = new WebSocketHandle("ws://example.com", new StubConnection(), env);
        ILuaContext ctx = makeContextFromQueue(sharedQueue);

        Object[] result = h.callMethod(ctx, METHOD_RECEIVE, new Object[] { 0.0 });

        assertNull(result);
    }

    @Test
    void receiveWithTimeoutStillReturnsMessageBeforeDeadline() throws LuaException, InterruptedException {
        // Pre-populate the queue with a websocket_message so receive returns
        // immediately, long before the 60 s scheduler fires.
        LinkedBlockingQueue<Object[]> sharedQueue = new LinkedBlockingQueue<>();
        sharedQueue.add(new Object[] { "websocket_message", "ws://example.com", "hi", false });
        StubEnvironment env = new StubEnvironment(sharedQueue);
        WebSocketHandle h = new WebSocketHandle("ws://example.com", new StubConnection(), env);
        ILuaContext ctx = makeContextFromQueue(sharedQueue);

        Object[] result = h.callMethod(ctx, METHOD_RECEIVE, new Object[] { 60.0 });

        assertNotNull(result);
        assertEquals("hi", result[0]);
    }
}
