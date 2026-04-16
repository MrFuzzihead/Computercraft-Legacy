package dan200.computercraft.core.apis;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObject;
import dan200.computercraft.api.lua.LuaException;

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
                boolean hasTimeout = args != null && args.length > 0 && args[0] instanceof Number;

                // Schedule a wakeup event so pullEventRaw is guaranteed to return even on
                // an idle computer (one with no queued events). Without this, pullEventRaw
                // would block indefinitely and the timeout would never be observed.
                ScheduledFuture<?> timeoutFuture = null;
                final long timeoutId;
                if (hasTimeout) {
                    double seconds = ((Number) args[0]).doubleValue();
                    timeoutId = NEXT_TIMEOUT_ID.incrementAndGet();
                    long millis = Math.max(0, (long) (seconds * 1000));
                    timeoutFuture = TIMEOUT_SCHEDULER.schedule(
                        () -> m_environment.queueEvent(TIMEOUT_EVENT, new Object[] { (double) timeoutId }),
                        millis,
                        TimeUnit.MILLISECONDS);
                } else {
                    timeoutId = 0;
                }

                try {
                    while (true) {
                        Object[] event = context.pullEventRaw(null);

                        if (event != null && event.length >= 1 && event[0] instanceof String) {
                            String eventName = (String) event[0];

                            if ("websocket_message".equals(eventName) && event.length >= 4 && m_url.equals(event[1])) {
                                return new Object[] { event[2], event[3] };
                            }

                            if ("websocket_closed".equals(eventName) && event.length >= 2 && m_url.equals(event[1])) {
                                return new Object[] { null, null, "Connection closed" };
                            }

                            if (hasTimeout && TIMEOUT_EVENT.equals(eventName)
                                && event.length >= 2
                                && event[1] instanceof Number
                                && ((Number) event[1]).longValue() == timeoutId) {
                                return new Object[] { null, null, "Timeout" };
                            }
                        }
                    }
                } finally {
                    // Always cancel the scheduled task so it doesn't fire a spurious
                    // event into the computer's queue after receive has already returned.
                    if (timeoutFuture != null) {
                        timeoutFuture.cancel(false);
                    }
                }
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
}
