package dan200.computercraft.core.apis;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.LuaException;

/**
 * Manages a single WebSocket connection on behalf of {@link HTTPAPI}.
 *
 * <p>
 * The connection is opened asynchronously in the constructor (via
 * {@link WebSocketClient#connect()}). {@link HTTPAPI#advance} polls
 * {@link #isConnectComplete()} each game tick; once settled it fires either
 * {@code websocket_success(url, handle)} or {@code websocket_failure(url, err)}.
 * </p>
 *
 * <p>
 * After the connection is open, incoming messages are forwarded directly to the
 * computer's event queue as {@code websocket_message(url, message, isBinary)}
 * and close events as {@code websocket_closed(url)}.
 * </p>
 *
 * <p>
 * Thread-safety: all mutable state that is read from the main game thread
 * (in {@code advance()}) is declared {@code volatile}.
 * </p>
 */
public class WebSocketRequest extends WebSocketClient implements IWebSocketConnection {

    private final IAPIEnvironment m_environment;
    private final String m_urlString;

    /** True between {@code onOpen} and {@code onClose}. Volatile for cross-thread visibility. */
    private volatile boolean m_open = false;

    /** Set to true once the connection attempt has resolved (either way). */
    private volatile boolean m_connectComplete = false;

    /** True if {@code onOpen} was called (connection succeeded). */
    private volatile boolean m_connectSuccess = false;

    /** Non-null failure reason when {@code m_connectComplete && !m_connectSuccess}. */
    private volatile String m_connectError = null;

    public WebSocketRequest(String urlString, Map<String, String> headers, IAPIEnvironment environment)
        throws LuaException {
        super(HTTPRequest.checkWebSocketURL(urlString), nullToEmpty(headers));
        this.m_environment = environment;
        this.m_urlString = urlString;

        if ("wss".equalsIgnoreCase(getURI().getScheme())) {
            try {
                setSocketFactory(
                    SSLContext.getDefault()
                        .getSocketFactory());
            } catch (NoSuchAlgorithmException e) {
                ComputerCraft.logger.error("Failed to initialize SSL for WebSocket connection to {}", urlString, e);
            }
        }

        connect(); // async; callbacks arrive on the WebSocketClient thread
    }

    private static Map<String, String> nullToEmpty(Map<String, String> map) {
        return map != null ? map : Collections.emptyMap();
    }

    // -------------------------------------------------------------------------
    // WebSocketClient callbacks
    // -------------------------------------------------------------------------

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        m_open = true;
        m_connectSuccess = true;
        m_connectComplete = true; // written last so advance() sees a consistent view
    }

    @Override
    public void onMessage(String message) {
        if (m_open) {
            m_environment.queueEvent("websocket_message", new Object[] { m_urlString, message, false });
        }
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        if (m_open) {
            byte[] data = new byte[bytes.remaining()];
            bytes.get(data);
            m_environment.queueEvent("websocket_message", new Object[] { m_urlString, data, true });
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (m_open) {
            // Normal close after a successful connection.
            m_open = false;
            m_environment.queueEvent("websocket_closed", new Object[] { m_urlString });
        } else if (!m_connectComplete) {
            // Connection failed before it ever opened.
            m_connectError = (reason != null && !reason.isEmpty()) ? reason : "Connection failed";
            m_connectComplete = true;
        }
    }

    @Override
    public void onError(Exception ex) {
        // Record the error message; onClose will mark connectComplete.
        if (!m_open && !m_connectComplete) {
            m_connectError = ex.getMessage() != null ? ex.getMessage() : "Unknown error";
        }
    }

    // -------------------------------------------------------------------------
    // IWebSocketConnection
    // -------------------------------------------------------------------------

    @Override
    public boolean isConnectionOpen() {
        return m_open;
    }

    @Override
    public void sendText(String message) {
        send(message);
    }

    @Override
    public void sendBinary(byte[] data) {
        send(data);
    }

    @Override
    public void closeConnection() {
        try {
            close();
        } catch (Exception e) {
            // Connection may already be closed — ignore.
        }
    }

    // -------------------------------------------------------------------------
    // Polling API used by HTTPAPI.advance()
    // -------------------------------------------------------------------------

    public boolean isConnectComplete() {
        return m_connectComplete;
    }

    public boolean wasConnectSuccessful() {
        return m_connectSuccess;
    }

    public String getConnectError() {
        return m_connectError != null ? m_connectError : "Connection refused";
    }

    public String getURL() {
        return m_urlString;
    }
}
