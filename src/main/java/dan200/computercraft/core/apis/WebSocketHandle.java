package dan200.computercraft.core.apis;

import java.nio.charset.StandardCharsets;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObject;
import dan200.computercraft.api.lua.LuaException;

/**
 * The Lua-visible WebSocket handle returned by {@code websocket_success}.
 *
 * <p>
 * Exposes three methods to Lua:
 * <ol>
 * <li>{@code receive([timeout])} — blocks until a {@code websocket_message} or
 * {@code websocket_closed} event arrives for this URL, or the optional
 * wall-clock deadline expires.</li>
 * <li>{@code send(message [, binary])} — sends a text or binary frame.</li>
 * <li>{@code close()} — initiates a close handshake.</li>
 * </ol>
 */
class WebSocketHandle implements ILuaObject {

    private static final int METHOD_RECEIVE = 0;
    private static final int METHOD_SEND = 1;
    private static final int METHOD_CLOSE = 2;

    private static final String[] METHOD_NAMES = { "receive", "send", "close" };

    private final String m_url;
    private final IWebSocketConnection m_connection;

    WebSocketHandle(String url, IWebSocketConnection connection) {
        this.m_url = url;
        this.m_connection = connection;
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
                long deadline = hasTimeout
                    ? System.currentTimeMillis() + (long) (((Number) args[0]).doubleValue() * 1000)
                    : Long.MAX_VALUE;

                while (true) {
                    Object[] event = context.pullEventRaw(null);

                    if (event != null && event.length >= 1 && event[0] instanceof String) {
                        String eventName = (String) event[0];

                        if ("websocket_message".equals(eventName) && event.length >= 4 && m_url.equals(event[1])) {
                            // { message, isBinary }
                            return new Object[] { event[2], event[3] };
                        }

                        if ("websocket_closed".equals(eventName) && event.length >= 2 && m_url.equals(event[1])) {
                            return null; // EOF sentinel — matches CC:Tweaked
                        }
                    }

                    if (hasTimeout && System.currentTimeMillis() >= deadline) {
                        return null;
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
                    m_connection.sendBinary(message.getBytes(StandardCharsets.UTF_8));
                } else {
                    m_connection.sendText(message);
                }
                return null;
            }

            case METHOD_CLOSE: {
                m_connection.closeConnection();
                return null;
            }

            default:
                return null;
        }
    }
}
