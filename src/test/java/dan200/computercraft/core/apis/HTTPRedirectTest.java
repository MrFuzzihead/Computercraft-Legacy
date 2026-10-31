package dan200.computercraft.core.apis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import dan200.computercraft.ComputerCraft;

/**
 * Tests for the {@code redirect} request option and for the redirect handling in
 * {@link HTTPRequest}.
 *
 * <p>
 * Redirects are followed inside the request itself rather than by {@link HttpURLConnection}, so
 * that every hop can be re-checked against the domain patterns and the local-address rule. These
 * tests pin both halves: which responses count as a redirect, and that the option is honoured end
 * to end.
 * </p>
 */
class HTTPRedirectTest {

    private boolean savedAllowLocal;
    private String savedWhitelist;
    private HttpServer server;
    private String base;

    /** A connection stub: only the response code and the Location header matter here. */
    private static HttpURLConnection stub(int code, String location) {
        return new HttpURLConnection(null) {

            @Override
            public String getHeaderField(String name) {
                return "Location".equalsIgnoreCase(name) ? location : null;
            }

            @Override
            public void connect() {}

            @Override
            public void disconnect() {}

            @Override
            public boolean usingProxy() {
                return false;
            }

            @Override
            public int getResponseCode() {
                return code;
            }

            @Override
            public String getResponseMessage() {
                return "";
            }

            @Override
            public void setRequestMethod(String method) {}
        };
    }

    private static URL url(String value) throws Exception {
        return new URL(value);
    }

    @BeforeEach
    void setUp() throws Exception {
        savedAllowLocal = ComputerCraft.http_allow_local;
        savedWhitelist = ComputerCraft.http_whitelist;
        // The test server is on loopback, which the local-address rule refuses by default.
        ComputerCraft.http_allow_local = true;
        ComputerCraft.http_whitelist = "127.0.0.1";

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI()
                .getPath();
            byte[] body;
            switch (path) {
                case "/redirect":
                    exchange.getResponseHeaders()
                        .add("Location", "/final");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                    return;
                case "/see-other":
                    exchange.getResponseHeaders()
                        .add("Location", "/final");
                    exchange.sendResponseHeaders(303, -1);
                    exchange.close();
                    return;
                case "/loop":
                    exchange.getResponseHeaders()
                        .add("Location", "/loop");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                    return;
                case "/final":
                    body = "final".getBytes(StandardCharsets.UTF_8);
                    break;
                default:
                    body = path.getBytes(StandardCharsets.UTF_8);
                    break;
            }
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress()
            .getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
        ComputerCraft.http_allow_local = savedAllowLocal;
        ComputerCraft.http_whitelist = savedWhitelist;
    }

    // -------------------------------------------------------------------------
    // redirectTarget: which responses redirect, and where to
    // -------------------------------------------------------------------------

    @Test
    void theRedirectStatusCodesAreRecognised() throws Exception {
        for (int code : new int[] { 301, 302, 303, 307, 308 }) {
            assertNotNull(
                HTTPRequest.redirectTarget(url("http://example.com/a"), stub(code, "/b"), code),
                "status " + code + " should redirect");
        }
    }

    @Test
    void otherStatusesDoNotRedirect() throws Exception {
        for (int code : new int[] { 200, 201, 204, 304, 400, 404, 500 }) {
            assertNull(
                HTTPRequest.redirectTarget(url("http://example.com/a"), stub(code, "/b"), code),
                "status " + code + " should not redirect");
        }
    }

    @Test
    void aMissingLocationIsNotARedirect() throws Exception {
        assertNull(HTTPRequest.redirectTarget(url("http://example.com/a"), stub(302, null), 302));
        assertNull(HTTPRequest.redirectTarget(url("http://example.com/a"), stub(302, ""), 302));
    }

    @Test
    void aRelativeLocationResolvesAgainstTheRequestUrl() throws Exception {
        URL target = HTTPRequest.redirectTarget(url("http://example.com/dir/page"), stub(302, "/other"), 302);
        assertEquals("http://example.com/other", target.toString());

        URL sibling = HTTPRequest.redirectTarget(url("http://example.com/dir/page"), stub(302, "next"), 302);
        assertEquals("http://example.com/dir/next", sibling.toString());
    }

    @Test
    void anAbsoluteLocationIsKept() throws Exception {
        URL target = HTTPRequest
            .redirectTarget(url("http://example.com/a"), stub(302, "https://example.org/b?x=1"), 302);
        assertEquals("https://example.org/b?x=1", target.toString());
    }

    @Test
    void aRedirectToTheSameUrlIsIgnored() throws Exception {
        // Otherwise the hop budget is the only thing that stops the loop.
        assertNull(HTTPRequest.redirectTarget(url("http://example.com/a"), stub(302, "/a"), 302));
    }

    @Test
    void onlyHttpRedirectsAreFollowed() throws Exception {
        // Chasing a file: or jar: redirect would be a way out of the http API entirely.
        assertNull(HTTPRequest.redirectTarget(url("http://example.com/a"), stub(302, "file:///etc/passwd"), 302));
        assertNull(HTTPRequest.redirectTarget(url("http://example.com/a"), stub(302, "ftp://example.com/x"), 302));
        assertNull(HTTPRequest.redirectTarget(url("http://example.com/a"), stub(302, "jar:file:///x.jar!/y"), 302));
    }

    // -------------------------------------------------------------------------
    // End to end, against a real local server
    // -------------------------------------------------------------------------

    /** Runs a request to completion and returns it. */
    private HTTPRequest complete(String urlString, boolean followRedirects) throws Exception {
        HTTPRequest request = new HTTPRequest(urlString, null, null, null, 5000, false, null, false, followRedirects);
        long deadline = System.currentTimeMillis() + 10_000;
        while (!request.isComplete() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(request.isComplete(), "the request did not complete in time");
        return request;
    }

    /** readAll(), i.e. what a Lua program would see. */
    private static String body(HTTPRequest request) throws Exception {
        HTTPResponse response = request.asResponse();
        assertNotNull(response, "expected a response handle");
        Object[] result = response.callMethod(null, 1, new Object[0]);
        return new String((byte[]) result[0], StandardCharsets.UTF_8);
    }

    /** getResponseCode(). */
    private static int statusOf(HTTPRequest request) throws Exception {
        HTTPResponse response = request.asResponse();
        assertNotNull(response, "expected a response handle");
        return (Integer) response.callMethod(null, 4, new Object[0])[0];
    }

    /** getResponseHeaders(). */
    @SuppressWarnings("unchecked")
    private static Map<String, String> headers(HTTPRequest request) {
        HTTPResponse response = request.asResponse();
        if (response == null) return new HashMap<>();
        try {
            return (Map<String, String>) response.callMethod(null, 5, new Object[0])[0];
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    @Test
    void redirectsAreFollowedByDefault() throws Exception {
        HTTPRequest request = complete(base + "/redirect", true);
        assertTrue(request.wasSuccessful());
        assertEquals("final", body(request));
        assertEquals(200, statusOf(request));
    }

    @Test
    void redirectsAreNotFollowedWhenTheOptionIsOff() throws Exception {
        HTTPRequest request = complete(base + "/redirect", false);
        assertEquals(302, statusOf(request));
        assertEquals("/final", headers(request).get("Location"));
    }

    @Test
    void aSeeOtherIsFollowedAsGet() throws Exception {
        // 303 means "fetch the result with GET"; the body must be the final page, not the redirect.
        HTTPRequest request = complete(base + "/see-other", true);
        assertEquals("final", body(request));
    }

    @Test
    void aRedirectLoopStopsAtTheHopBudget() throws Exception {
        HTTPRequest request = complete(base + "/loop", true);
        // Either the hop budget runs out and the last 302 is returned, or the same-URL guard
        // stops it at once. Both are a 302 response, never a hang.
        assertNotNull(request.asResponse());
        assertEquals(302, statusOf(request));
    }

    @Test
    void aRequestWithoutRedirectsNeverSeesTheFinalPage() throws Exception {
        HTTPRequest request = complete(base + "/see-other", false);
        assertEquals(303, statusOf(request));
    }

    @Test
    void theRedirectTargetHeaderIsVisibleToLuaOnAFinalResponse() throws Exception {
        HTTPRequest request = complete(base + "/redirect", false);
        assertTrue(headers(request).containsKey("Location"));
    }
}
