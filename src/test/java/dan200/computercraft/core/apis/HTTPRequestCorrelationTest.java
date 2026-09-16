package dan200.computercraft.core.apis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import com.sun.net.httpserver.HttpServer;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.LuaException;

/** Completion correlation is opt-in: untagged native requests retain their exact legacy event arity. */
class HTTPRequestCorrelationTest {

    private String savedWhitelist;
    private String savedBlacklist;
    private int savedMaxRequests;
    private int savedMaxWebsockets;
    private int savedMaxDownload;
    private HttpServer server;
    private IAPIEnvironment environment;
    private HTTPAPI api;

    @BeforeEach
    void setUp() throws Exception {
        savedWhitelist = ComputerCraft.http_whitelist;
        savedBlacklist = ComputerCraft.http_blacklist;
        savedMaxRequests = ComputerCraft.http_max_requests;
        savedMaxWebsockets = ComputerCraft.http_max_websockets;
        savedMaxDownload = ComputerCraft.http_max_download;
        ComputerCraft.http_whitelist = "127.0.0.1";
        ComputerCraft.http_blacklist = "";
        ComputerCraft.http_max_requests = 16;
        ComputerCraft.http_max_websockets = 16;
        ComputerCraft.http_max_download = 1024;
        environment = mock(IAPIEnvironment.class);
        api = new HTTPAPI(environment);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                int status = exchange.getRequestURI()
                    .getPath()
                    .equals("/failure") ? 404 : 200;
                exchange.sendResponseHeaders(status, 1);
                exchange.getResponseBody()
                    .write('x');
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (api != null) api.shutdown();
        if (server != null) server.stop(0);
        ComputerCraft.http_whitelist = savedWhitelist;
        ComputerCraft.http_blacklist = savedBlacklist;
        ComputerCraft.http_max_requests = savedMaxRequests;
        ComputerCraft.http_max_websockets = savedMaxWebsockets;
        ComputerCraft.http_max_download = savedMaxDownload;
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void httpSuccessShape(boolean tagged) throws Exception {
        Number id = tagged ? Double.valueOf(1001.25) : null;
        HTTPRequest request = startHttp(0, "/success", id, tagged);
        Object[] event = completion("http_success");
        assertEquals(tagged ? 3 : 2, event.length);
        assertEquals(request.getURL(), event[0]);
        assertResponse(event[1], 200);
        if (tagged) assertSame(id, event[2]);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void httpFailureShape(boolean tagged) throws Exception {
        Number id = tagged ? Long.valueOf(1002) : null;
        HTTPRequest request = startHttp(0, "/failure", id, tagged);
        Object[] event = completion("http_failure");
        assertEquals(tagged ? 4 : 3, event.length);
        assertEquals(request.getURL(), event[0]);
        assertEquals("Could not connect", event[1]);
        assertResponse(event[2], 404);
        if (tagged) assertSame(id, event[3]);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void httpFailureWithoutResponseKeepsResponseSlot(boolean tagged) throws Exception {
        Number id = tagged ? Integer.valueOf(1003) : null;
        HTTPRequest request = mock(HTTPRequest.class);
        when(request.isComplete()).thenReturn(true);
        when(request.getURL()).thenReturn(url("/failure"));
        when(request.getRequestId()).thenReturn(id);
        when(request.getFailureReason()).thenReturn("Download limit exceeded");
        requests("m_httpRequests").add(request);
        Object[] event = completion("http_failure");
        assertEquals(tagged ? 4 : 3, event.length);
        assertEquals(request.getURL(), event[0]);
        assertEquals("Download limit exceeded", event[1]);
        assertNull(event[2]);
        if (tagged) assertSame(id, event[3]);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void websocketSuccessShape(boolean tagged) throws Exception {
        Number id = tagged ? Double.valueOf(1004.5) : null;
        WebSocketRequest request = completedWebsocket(id, true);
        Object[] event = completion("websocket_success");
        assertEquals(tagged ? 3 : 2, event.length);
        assertEquals(request.getURL(), event[0]);
        assertInstanceOf(WebSocketHandle.class, event[1]);
        if (tagged) assertSame(id, event[2]);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void websocketFailureShape(boolean tagged) throws Exception {
        Number id = tagged ? Long.valueOf(1005) : null;
        WebSocketRequest request = completedWebsocket(id, false);
        Object[] event = completion("websocket_failure");
        assertEquals(tagged ? 3 : 2, event.length);
        assertEquals(request.getURL(), event[0]);
        assertEquals("Connection refused", event[1]);
        if (tagged) assertSame(id, event[2]);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1 })
    void nativeRequestAndFetchForwardOnlyNumericIds(int method) throws Exception {
        for (Object id : new Object[] { Double.valueOf(42.5), Long.valueOf(9001), null, "not an ID" }) {
            HTTPRequest request = startHttp(method, "/success", id, true);
            assertSame(id instanceof Number ? id : null, request.getRequestId());
            api.advance(0);
        }
        assertNull(startHttp(method, "/success", null, false).getRequestId());
    }

    @Test
    void nativeWebsocketForwardsOnlyNumericIds() throws Exception {
        for (Object id : new Object[] { Double.valueOf(43.5), Long.valueOf(9002), null, "not an ID" }) {
            WebSocketRequest request = startWebsocket(id, true);
            assertSame(id instanceof Number ? id : null, request.getRequestId());
        }
        assertNull(startWebsocket(null, false).getRequestId());
    }

    @Test
    void oldConstructorsRemainUntaggedAndOverloadsRetainNumber() throws Exception {
        HTTPRequest legacy = new HTTPRequest(url("/success"), null, null, null, 2000, false);
        requests("m_httpRequests").add(legacy);
        awaitHttp(legacy);
        assertNull(legacy.getRequestId());
        Number id = Double.valueOf(1234.5);
        HTTPRequest tagged = new HTTPRequest(url("/success"), null, null, null, 2000, false, id);
        requests("m_httpRequests").add(tagged);
        awaitHttp(tagged);
        assertSame(id, tagged.getRequestId());
        assertNull(new UnconnectedWebsocket(environment).getRequestId());
        assertSame(id, new UnconnectedWebsocket(environment, id).getRequestId());
    }

    /** Override the constructor's async connect only for direct constructor compatibility checks. */
    private static class UnconnectedWebsocket extends WebSocketRequest {

        UnconnectedWebsocket(IAPIEnvironment environment) throws LuaException {
            super("ws://127.0.0.1/", null, environment);
        }

        UnconnectedWebsocket(IAPIEnvironment environment, Number id) throws LuaException {
            super("ws://127.0.0.1/", null, environment, id);
        }

        @Override
        public void connect() {}
    }

    private HTTPRequest startHttp(int method, String path, Object id, boolean includeId) throws Exception {
        Object[] args = { url(path), null, null, null, 2.0, false, id };
        assertArrayEquals(
            new Object[] { true },
            api.callMethod(null, method, includeId ? args : Arrays.copyOf(args, 6)));
        List<HTTPRequest> pending = requests("m_httpRequests");
        HTTPRequest request = pending.get(pending.size() - 1);
        awaitHttp(request);
        return request;
    }

    private WebSocketRequest startWebsocket(Object id, boolean includeId) throws Exception {
        String url = url("/success").replace("http:", "ws:");
        Object[] args = includeId ? new Object[] { url, null, id } : new Object[] { url };
        assertArrayEquals(new Object[] { true }, api.callMethod(null, 3, args));
        List<WebSocketRequest> pending = requests("m_pendingWebsockets");
        WebSocketRequest request = pending.get(pending.size() - 1);
        // The local HTTP server rejects the WebSocket handshake; no external network is used.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!request.isConnectComplete() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(request.isConnectComplete(), "local WebSocket handshake must settle");
        request.closeBlocking();
        return request;
    }

    private WebSocketRequest completedWebsocket(Number id, boolean success) throws Exception {
        WebSocketRequest request = mock(WebSocketRequest.class);
        when(request.getURL()).thenReturn("ws://127.0.0.1/same-url");
        when(request.getRequestId()).thenReturn(id);
        when(request.isConnectComplete()).thenReturn(true);
        when(request.wasConnectSuccessful()).thenReturn(success);
        when(request.isConnectionOpen()).thenReturn(success);
        when(request.getConnectError()).thenReturn("Connection refused");
        requests("m_pendingWebsockets").add(request);
        return request;
    }

    private Object[] completion(String name) {
        api.advance(0);
        api.advance(0); // Settled requests must emit exactly once.
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(environment).queueEvent(eq(name), arguments.capture());
        verifyNoMoreInteractions(environment);
        return arguments.getValue();
    }

    private static void assertResponse(Object value, int status) throws Exception {
        assertInstanceOf(HTTPResponse.class, value);
        Object[] result = ((HTTPResponse) value).callMethod(null, 4, new Object[0]);
        assertEquals(status, ((Number) result[0]).intValue());
    }

    private static void awaitHttp(HTTPRequest request) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!request.isComplete() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(request.isComplete(), "local HTTP request must settle");
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress()
            .getPort() + path;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> requests(String fieldName) throws Exception {
        Field field = HTTPAPI.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (List<T>) field.get(api);
    }
}
