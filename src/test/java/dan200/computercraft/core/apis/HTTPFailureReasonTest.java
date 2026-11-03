package dan200.computercraft.core.apis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import dan200.computercraft.ComputerCraft;

/**
 * Tests for what a request reports when it fails for a <em>policy</em> reason, and for the body
 * surviving a non-POST verb.
 *
 * <p>
 * Both were bugs found by re-reading Batch H rather than by a failing test, so they are pinned
 * here.
 * </p>
 */
class HTTPFailureReasonTest {

    private boolean savedAllowLocal;
    private String savedWhitelist;
    private HttpServer server;
    private String base;

    /** Records what the server actually received. */
    private final List<String> methods = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        savedAllowLocal = ComputerCraft.http_allow_local;
        savedWhitelist = ComputerCraft.http_whitelist;
        ComputerCraft.http_whitelist = "*";

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            methods.add(exchange.getRequestMethod());
            bodies.add(new String(readBody(exchange), StandardCharsets.UTF_8));
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress()
            .getPort();
    }

    private static byte[] readBody(HttpExchange exchange) throws java.io.IOException {
        return exchange.getRequestBody()
            .readAllBytes();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
        ComputerCraft.http_allow_local = savedAllowLocal;
        ComputerCraft.http_whitelist = savedWhitelist;
    }

    private static HTTPRequest complete(HTTPRequest request) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!request.isComplete() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(request.isComplete(), "the request did not complete in time");
        return request;
    }

    // -------------------------------------------------------------------------
    // Policy refusals must name the reason
    // -------------------------------------------------------------------------

    @Test
    void aBlockedAddressReportsWhyItWasBlocked() throws Exception {
        // Allow-local stays false: this is the default a server ships with.
        HTTPRequest request = complete(
            new HTTPRequest("http://127.0.0.1:1/", null, null, null, 2000, false, null, false, true));

        assertTrue(!request.wasSuccessful(), "a blocked request must not succeed");
        assertEquals(
            "Domain not permitted",
            request.getFailureReason(),
            "without a failure reason, HTTPAPI falls back to \"Could not connect\" and the program "
                + "cannot tell a policy refusal from a dead server");
    }

    @Test
    void aBlockedAddressYieldsNoResponseHandle() throws Exception {
        HTTPRequest request = complete(
            new HTTPRequest("http://10.0.0.5/", null, null, null, 2000, false, null, false, true));
        assertEquals(null, request.asResponse());
    }

    @Test
    void anAddressThatBecomesBlockedOnRedirectNamesTheReason() throws Exception {
        // The first hop is allowed (local addresses are permitted here), the redirect target is
        // refused because the whitelist no longer matches. Re-checking each hop is what makes this
        // an error at all -- Java's own follow-redirects would have gone ahead.
        ComputerCraft.http_allow_local = true;
        ComputerCraft.http_whitelist = "127.0.0.1";

        HttpServer redirecting = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        redirecting.createContext("/", exchange -> {
            exchange.getResponseHeaders()
                .add("Location", "http://not-whitelisted.example/final");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        redirecting.start();
        try {
            String url = "http://127.0.0.1:" + redirecting.getAddress()
                .getPort() + "/";
            HTTPRequest request = complete(new HTTPRequest(url, null, null, null, 2000, false, null, false, true));
            assertTrue(!request.wasSuccessful());
            assertEquals("Domain not permitted", request.getFailureReason());
        } finally {
            redirecting.stop(0);
        }
    }

    // -------------------------------------------------------------------------
    // The body must survive any verb that is not downgraded to GET
    // -------------------------------------------------------------------------

    @Test
    void aPutKeepsItsBody() throws Exception {
        ComputerCraft.http_allow_local = true;
        HTTPRequest request = complete(
            new HTTPRequest(base + "/put", "payload", null, "PUT", 5000, false, null, false, true));

        assertTrue(request.wasSuccessful());
        assertEquals("PUT", methods.get(0));
        assertEquals("payload", bodies.get(0));
    }

    @Test
    void aPatchIsRejectedWithAClearReason() throws Exception {
        // HttpURLConnection refuses PATCH outright (its method whitelist predates it). The failure
        // must say so, rather than looking like an unreachable server, and must not log a stack
        // trace: the caller picked an unsupported method, which is not a fault in the mod.
        ComputerCraft.http_allow_local = true;
        HTTPRequest request = complete(
            new HTTPRequest(base + "/patch", "patch-body", null, "PATCH", 5000, false, null, false, true));

        assertTrue(!request.wasSuccessful());
        assertTrue(
            String.valueOf(request.getFailureReason())
                .contains("Unsupported HTTP method"),
            "got: " + request.getFailureReason());
    }

    @Test
    void aPostKeepsItsBody() throws Exception {
        ComputerCraft.http_allow_local = true;
        complete(new HTTPRequest(base + "/post", "post-body", null, null, 5000, false, null, false, true));

        assertEquals("POST", methods.get(0));
        assertEquals("post-body", bodies.get(0));
    }

    @Test
    void aGetSendsNoBody() throws Exception {
        ComputerCraft.http_allow_local = true;
        complete(new HTTPRequest(base + "/get", null, null, null, 5000, false, null, false, true));

        assertEquals("GET", methods.get(0));
        assertEquals("", bodies.get(0));
    }

    @Test
    void aSeeOtherDropsTheBodyAndSwitchesToGet() throws Exception {
        ComputerCraft.http_allow_local = true;
        HttpServer seeOther = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> seen = new ArrayList<>();
        List<String> seenBodies = new ArrayList<>();
        seeOther.createContext("/", exchange -> {
            seen.add(exchange.getRequestMethod());
            seenBodies.add(new String(readBody(exchange), StandardCharsets.UTF_8));
            if (exchange.getRequestURI()
                .getPath()
                .equals("/start")) {
                exchange.getResponseHeaders()
                    .add("Location", "/final");
                exchange.sendResponseHeaders(303, -1);
            } else {
                byte[] body = "done".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        seeOther.start();
        try {
            String url = "http://127.0.0.1:" + seeOther.getAddress()
                .getPort() + "/start";
            HTTPRequest request = complete(
                new HTTPRequest(url, "body-should-be-dropped", null, null, 5000, false, null, false, true));
            assertTrue(request.wasSuccessful());

            assertEquals(2, seen.size(), "expected the request and one redirect");
            assertEquals("POST", seen.get(0));
            assertEquals("body-should-be-dropped", seenBodies.get(0));
            // 303 means "GET the result": the body must not be resent.
            assertEquals("GET", seen.get(1));
            assertEquals("", seenBodies.get(1));
        } finally {
            seeOther.stop(0);
        }
    }

    @Test
    void aTemporaryRedirectKeepsTheBody() throws Exception {
        // 307/308 preserve the method *and* the body, unlike 303.
        ComputerCraft.http_allow_local = true;
        HttpServer redirecting = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> seenBodies = new ArrayList<>();
        List<String> seenMethods = new ArrayList<>();
        redirecting.createContext("/", exchange -> {
            seenMethods.add(exchange.getRequestMethod());
            seenBodies.add(new String(readBody(exchange), StandardCharsets.UTF_8));
            if (exchange.getRequestURI()
                .getPath()
                .equals("/start")) {
                exchange.getResponseHeaders()
                    .add("Location", "/final");
                exchange.sendResponseHeaders(307, -1);
            } else {
                byte[] body = "done".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        redirecting.start();
        try {
            String url = "http://127.0.0.1:" + redirecting.getAddress()
                .getPort() + "/start";
            complete(new HTTPRequest(url, "keep-me", null, "PUT", 5000, false, null, false, true));

            assertEquals("PUT", seenMethods.get(0));
            assertEquals("PUT", seenMethods.get(1));
            assertEquals("keep-me", seenBodies.get(1));
        } finally {
            redirecting.stop(0);
        }
    }

    // -------------------------------------------------------------------------
    // The private-address table gained a case
    // -------------------------------------------------------------------------

    @Test
    void anAllDigitHostIsTreatedAsALiteral() {
        assertTrue(PrivateAddresses.isLiteralShape("1234"));
        assertTrue(PrivateAddresses.isLiteralShape("10"));
        assertTrue(PrivateAddresses.isLiteralShape("127.1"));
        assertNotNull(Map.of());
    }
}
