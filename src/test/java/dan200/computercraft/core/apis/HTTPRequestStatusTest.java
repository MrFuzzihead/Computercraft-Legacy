package dan200.computercraft.core.apis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.LuaException;

/**
 * Tests for the way {@link HTTPRequest} publishes its HTTP status (finding B11 of
 * {@code docs/CODEBASE_ANALYSIS.md}).
 *
 * <p>
 * {@code responseCode} and {@code responseMessage} used to be written by the worker
 * thread <em>outside</em> {@code HTTPRequest.lock} while {@link HTTPRequest#asResponse()}
 * reads both <em>under</em> it. They are now assigned inside a synchronized block, so any
 * thread that can observe a completed request also observes its status pair.
 * </p>
 *
 * <h2>What these tests can, and cannot, prove</h2>
 * <p>
 * A missing happens-before edge is not reproducible on the hardware this runs on (HotSpot
 * on x86 publishes stores in program order, and {@code complete} is only set afterwards),
 * so <b>none of these tests fail against the old code</b> — they are guards, not a
 * reproduction. What they lock down is the observable contract the fix protects: the status
 * arrives <em>with</em> the response on every path (success, error status, and a status
 * line with no reason phrase), is never the {@code (-1, "")} sentinel once a handle
 * exists, stays consistent under concurrent readers, and is not visible at all when there
 * is no handle.
 * </p>
 *
 * <p>
 * Checked by mutation: dropping the publication fails
 * {@link #successfulResponsePublishesCodeAndMessage()}, and publishing only on the 2xx/3xx
 * path fails {@link #errorStatusIsPublishedWithTheErrorResponseHandle()}. Moving the
 * write after {@code complete} is set — still off-lock — was <em>not</em> caught here, so
 * the concurrency test should not be mistaken for a race detector.
 *
 * <p>
 * All requests target an in-process {@link HttpServer} (or a raw {@link ServerSocket} for
 * the status-line edge case), so no external network is involved.
 * </p>
 */
class HTTPRequestStatusTest {

    /** {@code HTTPResponse.getMethodNames()} indices. */
    private static final int RESPONSE_GET_CODE = 4;
    private static final int RESPONSE_GET_HEADERS = 5;

    private static String savedWhitelist;
    private static String savedBlacklist;
    private static int savedThreads;
    private static int savedTimeout;
    private static int savedMaxDownload;

    private HttpServer server;
    private final List<HTTPRequest> requests = new ArrayList<>();

    @BeforeAll
    static void saveConfig() {
        savedWhitelist = ComputerCraft.http_whitelist;
        savedBlacklist = ComputerCraft.http_blacklist;
        savedThreads = ComputerCraft.http_threads;
        savedTimeout = ComputerCraft.http_timeout;
        savedMaxDownload = ComputerCraft.http_max_download;
    }

    @AfterAll
    static void restoreConfig() {
        ComputerCraft.http_whitelist = savedWhitelist;
        ComputerCraft.http_blacklist = savedBlacklist;
        ComputerCraft.http_threads = savedThreads;
        ComputerCraft.http_timeout = savedTimeout;
        ComputerCraft.http_max_download = savedMaxDownload;
    }

    @BeforeEach
    void resetConfig() {
        ComputerCraft.http_whitelist = "*";
        ComputerCraft.http_blacklist = "";
        ComputerCraft.http_threads = 8;
        ComputerCraft.http_timeout = 30000;
        ComputerCraft.http_max_download = 16 * 1024 * 1024;
    }

    @AfterEach
    void tearDown() {
        for (HTTPRequest request : requests) {
            request.cancel();
        }
        requests.clear();
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    // =========================================================================
    // The status pair travels with the response handle
    // =========================================================================

    @Test
    void successfulResponsePublishesCodeAndMessage() throws Exception {
        int port = startServer("/ok", responds(200, "hi"));

        HTTPRequest request = newRequest(port, "/ok");
        assertTrue(awaitComplete(request), "request should complete");
        assertTrue(request.wasSuccessful());

        HTTPResponse response = request.asResponse();
        assertNotNull(response);
        assertStatus(response, 200, "OK");
        assertTrue(headerNames(response).contains("x-test"), "headers should be published with the status");
    }

    @Test
    void errorStatusIsPublishedWithTheErrorResponseHandle() throws Exception {
        // The 1.80pr1 contract: an error status still yields a handle, and the handle
        // must carry the status — the code/message are read before the error stream is
        // picked, so this is the path where a dropped publication would show up.
        int port = startServer("/gone", responds(404, "nope"));

        HTTPRequest request = newRequest(port, "/gone");
        assertTrue(awaitComplete(request));
        assertFalse(request.wasSuccessful(), "404 must not report success");

        HTTPResponse response = request.asResponse();
        assertNotNull(response, "an error status must still yield a response handle");
        assertStatus(response, 404, "Not Found");
    }

    @Test
    void statusLineWithoutAReasonPhraseYieldsAnEmptyMessage() throws Exception {
        // The reason phrase is optional; HttpServer always sends one, so serve the
        // status line by hand. HttpURLConnection reports a missing phrase as null —
        // it must never reach Lua as null.
        int port = serveRaw("HTTP/1.1 200\r\nContent-Length: 0\r\n\r\n");

        HTTPRequest request = newRequest(port, "/");
        assertTrue(awaitComplete(request), "request should complete");
        assertTrue(request.wasSuccessful());

        HTTPResponse response = request.asResponse();
        assertNotNull(response);
        assertStatus(response, 200, "");
    }

    @Test
    void failedConnectionPublishesNoHandleAndNoStatus() throws Exception {
        // Port 1 on loopback refuses the connection: no status line was ever read, so
        // asResponse() (the only reader of the status pair) must return nil.
        HTTPRequest request = new HTTPRequest("http://127.0.0.1:1/", null, null, null, 5000, false);
        requests.add(request);

        assertTrue(awaitComplete(request));
        assertFalse(request.wasSuccessful());
        assertNull(request.asResponse(), "a request that never got a status must not yield a handle");
    }

    @Test
    void requestCancelledBeforeItStartsPublishesNoStatus() throws Exception {
        // One pool thread, parked in the handler, so the second request is provably
        // still queued when it is cancelled: it never connects and must settle without
        // a handle (its status pair stays at the sentinel).
        ComputerCraft.http_threads = 1;
        CountDownLatch inHandler = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/blocked", exchange -> {
            inHandler.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
            try {
                exchange.sendResponseHeaders(200, -1);
            } catch (IOException ignored) {}
            exchange.close();
        });
        server.createContext("/ok", responds(200, "hi"));
        server.start();
        int port = server.getAddress()
            .getPort();

        HTTPRequest blocked = newRequest(port, "/blocked");
        assertTrue(inHandler.await(5, TimeUnit.SECONDS), "the first request must be in the handler");

        HTTPRequest queued = newRequest(port, "/ok");
        queued.cancel();
        release.countDown();

        assertTrue(awaitComplete(blocked), "the blocked request completes once released");
        assertTrue(awaitComplete(queued), "the cancelled request must settle");
        assertFalse(queued.wasSuccessful());
        assertNull(queued.asResponse(), "a request that never ran must not yield a handle");
    }

    // =========================================================================
    // Concurrent readers
    // =========================================================================

    @Test
    void concurrentReadersNeverSeeAnIncompleteStatusPair() throws Exception {
        ComputerCraft.http_threads = 8;
        int port = startServer("/ok", responds(200, "body"));

        List<HTTPRequest> inFlight = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            inFlight.add(newRequest(port, "/ok"));
        }

        Queue<String> problems = new ConcurrentLinkedQueue<>();
        AtomicBoolean stop = new AtomicBoolean();
        AtomicInteger observed = new AtomicInteger();
        List<Thread> readers = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            Thread reader = new Thread(() -> {
                while (!stop.get()) {
                    for (HTTPRequest request : inFlight) {
                        // asResponse() is the publication point: a handle is only ever
                        // visible together with its status, never before it.
                        HTTPResponse response = request.asResponse();
                        if (response == null) continue;
                        observed.incrementAndGet();
                        try {
                            Object[] status = response.callMethod(null, RESPONSE_GET_CODE, new Object[0]);
                            if (status == null || status.length != 2) {
                                problems
                                    .add("status not published as two values: " + java.util.Arrays.toString(status));
                            } else {
                                int code = ((Number) status[0]).intValue();
                                Object message = status[1];
                                if (code != 200 || !"OK".equals(message)) {
                                    problems.add("observed code=" + code + " message=" + message);
                                }
                            }
                        } catch (Exception e) {
                            problems.add("reading the status threw " + e);
                        }
                    }
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ignored) {
                        return;
                    }
                }
            }, "status-reader-" + r);
            reader.setDaemon(true);
            reader.start();
            readers.add(reader);
        }

        for (HTTPRequest request : inFlight) {
            assertTrue(awaitComplete(request), "every request should complete");
            assertTrue(request.wasSuccessful());
        }
        stop.set(true);
        for (Thread reader : readers) {
            reader.join(TimeUnit.SECONDS.toMillis(10));
        }

        assertTrue(observed.get() > 0, "the readers must have raced the publication at least once");
        assertTrue(problems.isEmpty(), "inconsistent status publication: " + problems);

        // Final sweep: once complete, every handle reports the server's status.
        for (HTTPRequest request : inFlight) {
            assertStatus(request.asResponse(), 200, "OK");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private HTTPRequest newRequest(int port, String path) throws LuaException {
        HTTPRequest request = new HTTPRequest("http://127.0.0.1:" + port + path, null, null, null, 5000, false);
        requests.add(request);
        return request;
    }

    private int startServer(String path, HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, handler);
        server.start();
        return server.getAddress()
            .getPort();
    }

    /** A handler that answers with {@code status}, a small body and an {@code x-test} header. */
    private static HttpHandler responds(int status, String body) {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        return exchange -> {
            exchange.getResponseHeaders()
                .set("x-test", "ok");
            try {
                exchange.sendResponseHeaders(status, payload.length == 0 ? -1 : payload.length);
                if (payload.length > 0) {
                    exchange.getResponseBody()
                        .write(payload);
                }
            } catch (IOException ignored) {} finally {
                exchange.close();
            }
        };
    }

    /**
     * Serve one canned response on a raw socket — needed because {@link HttpServer} always
     * writes a reason phrase and there is no way to ask it for a bare status line.
     */
    private static int serveRaw(String response) throws IOException {
        ServerSocket socket = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
        socket.setSoTimeout(10_000);
        Thread thread = new Thread(() -> {
            try (Socket client = socket.accept()) {
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
                String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    // Drain the request head.
                }
                OutputStream out = client.getOutputStream();
                out.write(response.getBytes(StandardCharsets.US_ASCII));
                out.flush();
                // Let the client read the response before the socket goes away.
                Thread.sleep(250);
            } catch (InterruptedException ignored) {
                Thread.currentThread()
                    .interrupt();
            } catch (IOException ignored) {} finally {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }, "raw-http-responder");
        thread.setDaemon(true);
        thread.start();
        return socket.getLocalPort();
    }

    private static void assertStatus(HTTPResponse response, int expectedCode, String expectedMessage) throws Exception {
        assertNotNull(response, "expected a response handle");
        Object[] status = response.callMethod(null, RESPONSE_GET_CODE, new Object[0]);
        assertNotNull(status, "getResponseCode must return values");
        assertEquals(2, status.length, "getResponseCode returns the code and the message");
        assertEquals(expectedCode, ((Number) status[0]).intValue(), "wrong status code");
        assertEquals(expectedMessage, status[1], "wrong reason phrase");
    }

    @SuppressWarnings("unchecked")
    private static List<String> headerNames(HTTPResponse response) throws Exception {
        Object[] result = response.callMethod(null, RESPONSE_GET_HEADERS, new Object[0]);
        assertNotNull(result, "getResponseHeaders must return a value");
        assertInstanceOf(Map.class, result[0], "getResponseHeaders must return a table");
        List<String> names = new ArrayList<>();
        for (Object key : ((Map<Object, Object>) result[0]).keySet()) {
            names.add(
                String.valueOf(key)
                    .toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private static boolean awaitComplete(HTTPRequest request) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (request.isComplete()) return true;
            Thread.sleep(5);
        }
        return request.isComplete();
    }
}
