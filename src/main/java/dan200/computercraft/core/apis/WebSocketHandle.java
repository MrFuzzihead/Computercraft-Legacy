package dan200.computercraft.core.apis;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import dan200.computercraft.api.lua.ILuaCallback;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObject;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;

/**
 * The Lua-visible WebSocket handle returned by {@code websocket_success}.
 *
 * <p>
 * Exposes four methods to Lua:
 * <ol>
 * <li>{@code receive([timeout])} — blocks until a {@code websocket_message} or
 * {@code websocket_closed} event arrives for this URL, or the optional
 * wall-clock deadline expires.</li>
 * <li>{@code send(message [, binary])} — sends a text or binary frame.</li>
 * <li>{@code close()} — initiates a close handshake.</li>
 * <li>{@code getResponseHeaders()} — returns the HTTP response headers
 * received during the WebSocket handshake.</li>
 * </ol>
 */
class WebSocketHandle implements ILuaObject {

    private static final int METHOD_RECEIVE = 0;
    private static final int METHOD_SEND = 1;
    private static final int METHOD_CLOSE = 2;
    private static final int METHOD_GETRESPONSEHEADERS = 3;

    private static final String[] METHOD_NAMES = { "receive", "send", "close", "getResponseHeaders" };

    /**
     * Internal event name used to wake up a blocking {@code receive(timeout)} call.
     * Each call uses a unique numeric ID (see {@link #NEXT_TIMEOUT_ID}) to avoid
     * cross-contamination between concurrent or sequential receive calls.
     */
    private static final String TIMEOUT_EVENT = "websocket_receive_timeout";

    /**
     * Shared single-thread daemon executor that schedules timeout wakeup events.
     * One thread is sufficient because the scheduled tasks are tiny (a single
     * {@link IAPIEnvironment#queueEvent} call).
     */
    private static final ScheduledExecutorService TIMEOUT_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "computercraft-ws-timeout");
        t.setDaemon(true);
        return t;
    });

    /** Monotonically increasing counter that makes each timeout event unique. */
    private static final AtomicLong NEXT_TIMEOUT_ID = new AtomicLong(0);

    private final String m_url;
    private final IWebSocketConnection m_connection;
    private final IAPIEnvironment m_environment;

    WebSocketHandle(String url, IWebSocketConnection connection, IAPIEnvironment environment) {
        this.m_url = url;
        this.m_connection = connection;
        this.m_environment = environment;
    }

    @Override
    public String[] getMethodNames() {
        return METHOD_NAMES;
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] args)
        throws LuaException, InterruptedException {
        switch (method) {
            case METHOD_RECEIVE: {
                // receive([timeout])
                //
                // This cannot block: the caller's thread is the one that has to deliver the
                // websocket event. Instead we suspend the Lua call and get handed every event by
                // ReceiveCallback, which picks the message, the close, or the timeout. Cancelling
                // the scheduled timeout happens in the callback, which is the only place it can be
                // correct -- a finally block cannot survive a coroutine suspension.
                boolean hasTimeout = args != null && args.length > 0 && args[0] instanceof Number;
                final double timeoutId;
                if (hasTimeout) {
                    timeoutId = NEXT_TIMEOUT_ID.incrementAndGet();
                } else {
                    timeoutId = 0;
                }

                ScheduledFuture<?> timeoutFuture = hasTimeout ? TIMEOUT_SCHEDULER.schedule(
                    () -> m_environment.queueEvent(TIMEOUT_EVENT, new Object[] { timeoutId }),
                    Math.max(0, (long) ((((Number) args[0]).doubleValue()) * 1000)),
                    TimeUnit.MILLISECONDS) : null;

                return new Object[] { new ReceiveCallback(hasTimeout, timeoutId, timeoutFuture).pull };
            }

            case METHOD_SEND: {
                // send(message [, binary])
                if (args == null || args.length < 1 || !(args[0] instanceof String)) {
                    throw new LuaException("Expected string");
                }
                if (!m_connection.isConnectionOpen()) {
                    throw new LuaException("WebSocket is closed");
                }

                String message = (String) args[0];
                boolean binary = args.length > 1 && Boolean.TRUE.equals(args[1]);

                if (binary) {
                    // Lua strings are byte sequences decoded by the Cobalt bridge as
                    // ISO-8859-1 (each char == one byte, 0x00-0xFF). Using UTF-8 here
                    // would double-encode bytes 0x80-0xFF into two-byte sequences and
                    // corrupt the payload. ISO_8859_1 is the correct inverse.
                    m_connection.sendBinary(message.getBytes(StandardCharsets.ISO_8859_1));
                } else {
                    m_connection.sendText(message);
                }
                return null;
            }

            case METHOD_CLOSE: {
                m_connection.closeConnection();
                return null;
            }

            case METHOD_GETRESPONSEHEADERS: {
                return new Object[] { m_connection.getResponseHeaders() };
            }

            default:
                return null;
        }
    }

    /**
     * Handles a suspended {@code ws.receive}, deciding which event ends the wait.
     *
     * <p>
     * Mirrors CC: Tweaked's {@code WebsocketHandle.ReceiveCallback}: it is handed every event the
     * computer receives while the call is parked, and returns {@code pull} again for anything that
     * is not its own so the call keeps waiting.
     */
    private final class ReceiveCallback implements ILuaCallback {

        /** The suspending result handed to Lua; returned again to keep waiting. */
        final MethodResult pull = MethodResult.pullEvent(this);

        private final boolean hasTimeout;

        private final double timeoutId;

        private final ScheduledFuture<?> timeoutFuture;

        ReceiveCallback(boolean hasTimeout, double timeoutId, ScheduledFuture<?> timeoutFuture) {
            this.hasTimeout = hasTimeout;
            this.timeoutId = timeoutId;
            this.timeoutFuture = timeoutFuture;
        }

        /** Cancel the scheduled timeout, so it cannot fire into the computer after we are done. */
        private void cancelTimeout() {
            if (timeoutFuture != null) {
                timeoutFuture.cancel(false);
            }
        }

        @Override
        public MethodResult resume(Object[] event) {
            if (event.length >= 4 && "websocket_message".equals(event[0]) && m_url.equals(event[1])) {
                cancelTimeout();
                return MethodResult.of(event[2], event[3]);
            }

            if (event.length >= 2 && "websocket_closed".equals(event[0]) && m_url.equals(event[1])) {
                cancelTimeout();
                return MethodResult.of(null, null, "Connection closed");
            }

            if (hasTimeout && event.length >= 2
                && TIMEOUT_EVENT.equals(event[0])
                && event[1] instanceof Number
                && ((Number) event[1]).doubleValue() == timeoutId) {
                return MethodResult.of(null, null, "Timeout");
            }

            // Not ours: keep waiting.
            return pull;
        }
    }
}
