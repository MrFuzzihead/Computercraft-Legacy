package dan200.computercraft.core.apis;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObject;
import dan200.computercraft.api.lua.LuaException;

public class HTTPAPI implements ILuaAPI {

    private IAPIEnvironment m_apiEnvironment;
    private List<HTTPRequest> m_httpRequests;
    /** WebSocket connections whose initial connect attempt has not yet settled. */
    private List<WebSocketRequest> m_pendingWebsockets;
    /** WebSocket connections that have successfully opened (needed for shutdown cleanup). */
    private List<WebSocketRequest> m_activeWebsockets;

    public HTTPAPI(IAPIEnvironment environment) {
        this.m_apiEnvironment = environment;
        this.m_httpRequests = new ArrayList<>();
        this.m_pendingWebsockets = new ArrayList<>();
        this.m_activeWebsockets = new ArrayList<>();
    }

    @Override
    public String[] getNames() {
        return new String[] { "http" };
    }

    @Override
    public void startup() {}

    @Override
    public void advance(double dt) {
        synchronized (m_httpRequests) {
            Iterator<HTTPRequest> it = m_httpRequests.iterator();
            while (it.hasNext()) {
                HTTPRequest h = it.next();
                if (h.isComplete()) {
                    String url = h.getURL();
                    if (h.wasSuccessful()) {
                        m_apiEnvironment.queueEvent("http_success", new Object[] { url, h.asResponse() });
                    } else {
                        m_apiEnvironment
                            .queueEvent("http_failure", new Object[] { url, "Could not connect", h.asResponse() });
                    }
                    it.remove();
                }
            }
        }

        synchronized (m_pendingWebsockets) {
            Iterator<WebSocketRequest> wsIt = m_pendingWebsockets.iterator();
            while (wsIt.hasNext()) {
                WebSocketRequest ws = wsIt.next();
                if (ws.isConnectComplete()) {
                    String url = ws.getURL();
                    if (ws.wasConnectSuccessful()) {
                        m_apiEnvironment
                            .queueEvent("websocket_success", new Object[] { url, new WebSocketHandle(url, ws) });
                        synchronized (m_activeWebsockets) {
                            m_activeWebsockets.add(ws);
                        }
                    } else {
                        m_apiEnvironment.queueEvent("websocket_failure", new Object[] { url, ws.getConnectError() });
                    }
                    wsIt.remove();
                }
            }
        }

        // Remove any active WebSockets that have since closed, to avoid memory leaks.
        synchronized (m_activeWebsockets) {
            m_activeWebsockets.removeIf(ws -> !ws.isConnectionOpen());
        }
    }

    private static ILuaObject wrapBufferedReader(final BufferedReader reader, final int responseCode) {
        return new ILuaObject() {

            @Override
            public String[] getMethodNames() {
                return new String[] { "readLine", "readAll", "close", "getResponseCode" };
            }

            @Override
            public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
                switch (method) {
                    case 0:
                        try {
                            String line = reader.readLine();
                            if (line != null) {
                                return new Object[] { line };
                            }

                            return null;
                        } catch (IOException var8) {
                            return null;
                        }
                    case 1:
                        try {
                            StringBuilder result = new StringBuilder("");
                            String line = reader.readLine();

                            while (line != null) {
                                result.append(line);
                                line = reader.readLine();
                                if (line != null) {
                                    result.append("\n");
                                }
                            }

                            return new Object[] { result.toString() };
                        } catch (IOException var7) {
                            return null;
                        }
                    case 2:
                        try {
                            reader.close();
                            return null;
                        } catch (IOException var6) {
                            return null;
                        }
                    case 3:
                        return new Object[] { responseCode };
                    default:
                        return null;
                }
            }
        };
    }

    @Override
    public void shutdown() {
        synchronized (this.m_httpRequests) {
            for (HTTPRequest r : this.m_httpRequests) {
                r.cancel();
            }
            this.m_httpRequests.clear();
        }

        synchronized (this.m_pendingWebsockets) {
            for (WebSocketRequest ws : this.m_pendingWebsockets) {
                ws.closeConnection();
            }
            this.m_pendingWebsockets.clear();
        }

        synchronized (this.m_activeWebsockets) {
            for (WebSocketRequest ws : this.m_activeWebsockets) {
                ws.closeConnection();
            }
            this.m_activeWebsockets.clear();
        }
    }

    @Override
    public String[] getMethodNames() {
        return new String[] { "request", "fetch", "checkURL", "websocket" };
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
        switch (method) {
            case 0: // request
            case 1: {
                if (args.length < 1 || !(args[0] instanceof String)) {
                    throw new LuaException("Expected string");
                }

                String urlString = args[0].toString();
                String data = args.length > 1 && args[1] instanceof String ? (String) args[1] : null;
                String verb = args.length > 3 && args[3] instanceof String ? (String) args[3] : null;

                HashMap<String, String> headers = null;
                if (args.length >= 3 && args[2] instanceof Map) {
                    Map<?, ?> argHeader = (Map<?, ?>) args[2];
                    headers = new HashMap<String, String>(argHeader.size());

                    for (Object key : argHeader.keySet()) {
                        Object value = argHeader.get(key);
                        if (key instanceof String && value instanceof String) {
                            headers.put((String) key, (String) value);
                        }
                    }
                }

                try {
                    HTTPRequest request = new HTTPRequest(urlString, data, headers, verb);
                    synchronized (this.m_httpRequests) {
                        this.m_httpRequests.add(request);
                    }

                    return new Object[] { Boolean.valueOf(true) };
                } catch (LuaException e) {
                    return new Object[] { Boolean.valueOf(false), e.getMessage() };
                }
            }
            case 2: { // checkURL
                if (args.length < 1 || !(args[0] instanceof String)) {
                    throw new LuaException("Expected string");
                }
                String urlString = args[0].toString();

                try {
                    HTTPRequest.checkURL(urlString);
                    return new Object[] { Boolean.valueOf(true) };
                } catch (LuaException e) {
                    return new Object[] { Boolean.valueOf(false), e.getMessage() };
                }
            }
            case 3: { // websocket(url [, headers])
                if (args.length < 1 || !(args[0] instanceof String)) {
                    throw new LuaException("Expected string");
                }
                String urlString = args[0].toString();

                HashMap<String, String> headers = null;
                if (args.length >= 2 && args[1] instanceof Map) {
                    Map<?, ?> argHeader = (Map<?, ?>) args[1];
                    headers = new HashMap<>(argHeader.size());
                    for (Object key : argHeader.keySet()) {
                        Object value = argHeader.get(key);
                        if (key instanceof String && value instanceof String) {
                            headers.put((String) key, (String) value);
                        }
                    }
                }

                try {
                    WebSocketRequest request = new WebSocketRequest(urlString, headers, this.m_apiEnvironment);
                    synchronized (this.m_pendingWebsockets) {
                        this.m_pendingWebsockets.add(request);
                    }
                    return new Object[] { Boolean.valueOf(true) };
                } catch (LuaException e) {
                    return new Object[] { Boolean.valueOf(false), e.getMessage() };
                }
            }
            default:
                return null;
        }
    }
}
