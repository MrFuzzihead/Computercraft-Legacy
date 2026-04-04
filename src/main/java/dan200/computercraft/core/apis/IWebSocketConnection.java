package dan200.computercraft.core.apis;

/**
 * Minimal abstraction over an active WebSocket connection, used by
 * {@code WebSocketHandle} so that the handle can be unit-tested without a live
 * network connection.
 */
interface IWebSocketConnection {

    /** Send a text-frame message. */
    void sendText(String message);

    /** Send a binary-frame message. */
    void sendBinary(byte[] data);

    /** Initiate a close handshake. May be called more than once safely. */
    void closeConnection();

    /**
     * Returns {@code true} while the connection is in the OPEN state (after
     * {@code onOpen} and before {@code onClose}).
     */
    boolean isConnectionOpen();
}
