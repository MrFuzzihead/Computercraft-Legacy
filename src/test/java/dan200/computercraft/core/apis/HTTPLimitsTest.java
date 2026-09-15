package dan200.computercraft.core.apis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.LuaException;

/**
 * Unit tests for the HTTP request limits added to close the S2 DoS vector and
 * the P2 shared-executor work ({@code docs/CODEBASE_ANALYSIS.md}):
 *
 * <ul>
 * <li>{@code http_blacklist} — domain blocking for {@code http} and websockets.</li>
 * <li>{@code http_max_requests} — in-flight request capping in {@link HTTPAPI}.</li>
 * <li>{@code http_max_download} — response-body aborting in {@link HTTPRequest},
 * verified against a local in-process HTTP server (no external network).</li>
 * <li>{@code http_threads} — the shared bounded request pool (P2): no thread
 * per request, bounded concurrency, cancelled queued requests never connect.</li>
 * <li>{@code http_timeout} — the default request timeout (P2).</li>
 * </ul>
 */
class HTTPLimitsTest {

    private static String savedWhitelist;
    private static String savedBlacklist;
    private static int savedMaxRequests;
    private static int savedMaxWebsockets;
    private static int savedMaxDownload;
    private static int savedHttpThreads;
    private static int savedHttpTimeout;

    @BeforeAll
    static void saveConfig() {
        savedWhitelist = ComputerCraft.http_whitelist;
        savedBlacklist = ComputerCraft.http_blacklist;
        savedMaxRequests = ComputerCraft.http_max_requests;
        savedMaxWebsockets = ComputerCraft.http_max_websockets;
        savedMaxDownload = ComputerCraft.http_max_download;
        savedHttpThreads = ComputerCraft.http_threads;
        savedHttpTimeout = ComputerCraft.http_timeout;
    }

    /** Reset to permissive defaults before every test so tests are order-independent. */
    @BeforeEach
    void resetConfig() {
        ComputerCraft.http_whitelist = "*";
        ComputerCraft.http_blacklist = "";
        ComputerCraft.http_max_requests = 16;
        ComputerCraft.http_max_websockets = 4;
        ComputerCraft.http_max_download = 16 * 1024 * 1024;
        ComputerCraft.http_threads = 8;
        ComputerCraft.http_timeout = 30000;
    }

    @AfterAll
    static void restoreConfig() {
        ComputerCraft.http_whitelist = savedWhitelist;
        ComputerCraft.http_blacklist = savedBlacklist;
        ComputerCraft.http_max_requests = savedMaxRequests;
        ComputerCraft.http_max_websockets = savedMaxWebsockets;
        ComputerCraft.http_max_download = savedMaxDownload;
        ComputerCraft.http_threads = savedHttpThreads;
        ComputerCraft.http_timeout = savedHttpTimeout;
    }

    // =========================================================================
    // http_blacklist
    // =========================================================================

    @Test
    void checkURLAcceptsAnyHostWithDefaultWildcardWhitelist() throws LuaException {
        assertEquals(
            "http://example.com/x",
            HTTPRequest.checkURL("http://example.com/x")
                .toString());
        assertEquals(
            "https://anything.example.org",
            HTTPRequest.checkURL("https://anything.example.org")
                .toString());
    }

    @Test
    void checkURLRejectsHostOutsideWhitelist() {
        ComputerCraft.http_whitelist = "*.pastebin.com;*.github.com";
        LuaException e = assertThrows(LuaException.class, () -> HTTPRequest.checkURL("http://example.com/"));
        assertEquals("Domain not permitted", e.getMessage());
    }

    @Test
    void checkURLAcceptsHostInsideWhitelist() throws LuaException {
        ComputerCraft.http_whitelist = "*.pastebin.com;*.github.com";
        // Note: "*.pastebin.com" matches any subdomain (incl. the apex's own
        // prefix via ".*"), matching the original CC wildcard semantics.
        HTTPRequest.checkURL("http://www.pastebin.com/raw.php?i=abc");
        HTTPRequest.checkURL("http://api.github.com");
        HTTPRequest.checkURL("http://a.b.pastebin.com");
    }

    @Test
    void checkURLRejectsBlacklistedHostEvenWhenWhitelisted() {
        ComputerCraft.http_blacklist = "*.evil.example";
        LuaException e = assertThrows(LuaException.class, () -> HTTPRequest.checkURL("http://sub.evil.example/x"));
        assertEquals("Domain blocked", e.getMessage());
    }

    @Test
    void checkURLAllowsHostWhenBlacklistDoesNotMatch() throws LuaException {
        ComputerCraft.http_blacklist = "*.evil.example";
        HTTPRequest.checkURL("http://good.example/x");
        HTTPRequest.checkURL("http://evil.example.com"); // suffix differs from "*.evil.example"
    }

    @Test
    void checkURLTreatsEmptyBlacklistAsAllowAll() throws LuaException {
        HTTPRequest.checkURL("http://anything.example");
    }

    @Test
    void websocketURLFollowsSameBlacklistRules() {
        ComputerCraft.http_blacklist = "*.blocked.example";

        LuaException e = assertThrows(
            LuaException.class,
            () -> HTTPRequest.checkWebSocketURL("ws://chat.blocked.example/ws"));
        assertEquals("Domain blocked", e.getMessage());

        LuaException e2 = assertThrows(
            LuaException.class,
            () -> HTTPRequest.checkWebSocketURL("wss://chat.blocked.example"));
        assertEquals("Domain blocked", e2.getMessage());
    }

    @Test
    void websocketURLAllowsNonBlacklistedHost() throws LuaException {
        ComputerCraft.http_blacklist = "*.blocked.example";
        HTTPRequest.checkWebSocketURL("ws://chat.example.org/ws");
    }

    // =========================================================================
    // http_max_requests — cap enforced by HTTPAPI before starting a request
    // =========================================================================

    @SuppressWarnings("unchecked")
    private static List<HTTPRequest> requestList(HTTPAPI api) throws Exception {
        Field f = HTTPAPI.class.getDeclaredField("m_httpRequests");
        f.setAccessible(true);
        return (List<HTTPRequest>) f.get(api);
    }

    @Test
    void requestCapRejectsWhenListIsFull() throws Exception {
        ComputerCraft.http_max_requests = 1;
        HTTPAPI api = new HTTPAPI(new StubEnv());

        List<HTTPRequest> list = requestList(api);
        list.add(mock(HTTPRequest.class)); // simulate one request already in flight

        Object[] result = api.callMethod(null, 0, new Object[] { "http://127.0.0.1:1/" });
        assertEquals(Boolean.FALSE, result[0]);
        assertEquals("Too many ongoing HTTP requests", result[1]);
        assertEquals(1, list.size(), "Rejected request must not be tracked");
    }

    @Test
    void requestCapZeroMeansUnlimited() throws Exception {
        ComputerCraft.http_max_requests = 0;
        HTTPAPI api = new HTTPAPI(new StubEnv());

        // With no cap the API must proceed to construct the request (which
        // targets a closed local port — fails fast, no external traffic).
        Object[] result = api.callMethod(null, 0, new Object[] { "http://127.0.0.1:1/" });
        assertEquals(Boolean.TRUE, result[0]);
        assertEquals(1, requestList(api).size());

        api.shutdown(); // cancel + clear the in-flight request
    }

    @Test
    void underCapRequestIsAcceptedAndTracked() throws Exception {
        ComputerCraft.http_max_requests = 2;
        HTTPAPI api = new HTTPAPI(new StubEnv());

        Object[] result = api.callMethod(null, 0, new Object[] { "http://127.0.0.1:1/" });
        assertEquals(Boolean.TRUE, result[0]);
        assertEquals(1, requestList(api).size());

        api.shutdown();
    }

    // =========================================================================
    // http_max_download — response aborting against a local HTTP server
    // =========================================================================

    private static HttpServer server;

    @Test
    void downloadLimitAbortsOversizedBodyAndReportsReason() throws Exception {
        server = HttpServer.create(new java.net.InetSocketAddress(0), 0);
        byte[] body = new byte[64 * 1024]; // 64 KiB
        java.util.Arrays.fill(body, (byte) 'x');
        server.createContext("/big", exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody()
                .write(body);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress()
                .getPort();
            ComputerCraft.http_max_download = 1024; // 1 KiB cap, far below the body size

            HTTPRequest request = new HTTPRequest("http://127.0.0.1:" + port + "/big", null, null, null, 5000, false);
            assertTrue(awaitComplete(request), "request should complete quickly");
            assertFalse(request.wasSuccessful());
            assertEquals("Download limit exceeded", request.getFailureReason());
            assertNull(request.asResponse());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void smallDownloadsUnderTheLimitStillSucceed() throws Exception {
        server = HttpServer.create(new java.net.InetSocketAddress(0), 0);
        byte[] body = new byte[512];
        java.util.Arrays.fill(body, (byte) 'y');
        server.createContext("/small", exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody()
                .write(body);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress()
                .getPort();
            ComputerCraft.http_max_download = 1024; // above the body size

            HTTPRequest request = new HTTPRequest("http://127.0.0.1:" + port + "/small", null, null, null, 5000, false);
            assertTrue(awaitComplete(request));
            assertTrue(request.wasSuccessful());
            assertNull(request.getFailureReason());
            // HTTPResponse exposes no content accessor (see HTTPResponseTest for
            // its Lua surface); a non-null handle proves the body was buffered.
            assertNotNull(request.asResponse());
        } finally {
            server.stop(0);
        }
    }

    // =========================================================================
    // P2: shared bounded executor + default timeout
    // =========================================================================

    private static void startServer(String path, HttpHandler handler) throws IOException {
        server = HttpServer.create(new java.net.InetSocketAddress(0), 0);
        server.createContext(path, handler);
        server.start();
    }

    @Test
    void requestsRunOnABoundedReusedThreadPool() throws Exception {
        ComputerCraft.http_threads = 2;
        startServer("/x", exchange -> {
            try {
                exchange.sendResponseHeaders(200, 3);
                exchange.getResponseBody()
                    .write(new byte[] { 1, 2, 3 });
                exchange.close();
            } catch (IOException ignored) {}
        });

        int port = server.getAddress()
            .getPort();
        List<HTTPRequest> requests = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            requests.add(new HTTPRequest("http://127.0.0.1:" + port + "/x", null, null, null, 5000, false));
        }

        for (HTTPRequest request : requests) {
            assertTrue(awaitComplete(request), "all requests should complete");
        }

        // Thread-per-request would produce 6 distinct threads; the shared
        // executor must serve all 6 requests from at most 2 named pool threads.
        Set<String> workerThreads = new HashSet<>();
        for (HTTPRequest request : requests) {
            Thread worker = request.getWorkerThread();
            assertNotNull(worker, "every request must have run on a pool thread");
            workerThreads.add(worker.getName());
        }

        assertTrue(workerThreads.size() <= 2, "requests must reuse at most http_threads threads, saw " + workerThreads);
        for (String name : workerThreads) {
            assertTrue(name.startsWith("computercraft-http-"), "unexpected worker thread: " + name);
        }
    }

    @Test
    void executorBoundsConcurrentRequests() throws Exception {
        ComputerCraft.http_threads = 2;
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        startServer("/slow", exchange -> {
            int now = active.incrementAndGet();
            maxActive.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {}
            active.decrementAndGet();
            try {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            } catch (IOException ignored) {}
        });

        int port = server.getAddress()
            .getPort();
        List<HTTPRequest> requests = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            requests.add(new HTTPRequest("http://127.0.0.1:" + port + "/slow", null, null, null, 10000, false));
        }

        for (HTTPRequest request : requests) {
            assertTrue(awaitComplete(request), "all requests should complete");
        }

        assertTrue(maxActive.get() <= 2, "handler concurrency must be bounded by the pool, was " + maxActive.get());
    }

    @Test
    void cancelledQueuedRequestNeverConnects() throws Exception {
        ComputerCraft.http_threads = 1;
        CountDownLatch blockHandler = new CountDownLatch(1);
        AtomicInteger blockHits = new AtomicInteger();
        AtomicInteger neverHits = new AtomicInteger();
        server = HttpServer.create(new java.net.InetSocketAddress(0), 0);
        server.createContext("/block", exchange -> {
            blockHits.incrementAndGet();
            try {
                blockHandler.await();
            } catch (InterruptedException ignored) {}
            try {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            } catch (IOException ignored) {}
        });
        server.createContext("/never", exchange -> {
            neverHits.incrementAndGet();
            try {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            } catch (IOException ignored) {}
        });
        server.start();

        int port = server.getAddress()
            .getPort();
        HTTPRequest blocked = new HTTPRequest("http://127.0.0.1:" + port + "/block", null, null, null, 10000, false);

        // Wait until the blocked request occupies the single pool thread (the
        // server handler is running, so the client thread is parked in
        // getResponseCode()).
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (blockHits.get() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, blockHits.get(), "the blocked request must be in the handler");

        // With the only pool thread taken, this request is queued, never
        // started; cancelling it must prevent any connection.
        HTTPRequest queued = new HTTPRequest("http://127.0.0.1:" + port + "/never", null, null, null, 5000, false);
        queued.cancel();
        Thread.sleep(300);
        assertEquals(0, neverHits.get(), "a cancelled queued request must never connect");

        blockHandler.countDown();
        assertTrue(awaitComplete(blocked), "the blocked request should complete after release");
        assertTrue(blocked.wasSuccessful());
        assertTrue(awaitComplete(queued), "the cancelled request must settle");
        assertEquals(0, neverHits.get(), "the cancelled request must never have connected");
    }

    @Test
    void defaultTimeoutAbortsSlowRequests() throws Exception {
        ComputerCraft.http_timeout = 300;
        startServer("/stalled", exchange -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {}
            try {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            } catch (IOException ignored) {}
        });

        int port = server.getAddress()
            .getPort();
        HTTPAPI api = new HTTPAPI(new StubEnv());
        // No explicit timeout argument: the http_timeout default must apply.
        Object[] result = api.callMethod(null, 0, new Object[] { "http://127.0.0.1:" + port + "/stalled" });
        assertEquals(Boolean.TRUE, result[0]);

        List<HTTPRequest> list = requestList(api);
        assertEquals(1, list.size());
        HTTPRequest request = list.get(0);
        assertTrue(awaitComplete(request), "the default timeout must abort the slow request");
        assertFalse(request.wasSuccessful(), "a request exceeding http_timeout must fail");
        assertNull(request.asResponse());

        api.shutdown();
    }

    private static boolean awaitComplete(HTTPRequest request) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (request.isComplete()) return true;
            Thread.sleep(10);
        }
        return request.isComplete();
    }

    // =========================================================================
    // Stubs
    // =========================================================================

    /** Minimal {@link IAPIEnvironment}; only queueEvent/labels are unused here. */
    private static class StubEnv implements IAPIEnvironment {

        @Override
        public dan200.computercraft.core.computer.Computer getComputer() {
            return null;
        }

        @Override
        public int getComputerID() {
            return 1;
        }

        @Override
        public dan200.computercraft.core.computer.IComputerEnvironment getComputerEnvironment() {
            return null;
        }

        @Override
        public dan200.computercraft.core.terminal.Terminal getTerminal() {
            return null;
        }

        @Override
        public dan200.computercraft.core.filesystem.FileSystem getFileSystem() {
            return null;
        }

        @Override
        public void shutdown() {}

        @Override
        public void reboot() {}

        @Override
        public void queueEvent(String event, Object[] args) {}

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
        public void setBundledOutput(int side, int output) {}

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
        public dan200.computercraft.api.peripheral.IPeripheral getPeripheral(int side) {
            return null;
        }

        @Override
        public String getLabel() {
            return null;
        }

        @Override
        public void setLabel(String label) {}
    }
}
