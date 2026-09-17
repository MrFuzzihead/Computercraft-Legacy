package dan200.computercraft.core.apis;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.LuaException;

public class HTTPRequest {

    /**
     * Shared, bounded executor for HTTP requests (finding P2 in
     * {@code docs/CODEBASE_ANALYSIS.md}). Requests from all computers run on
     * {@code http_threads} daemon threads instead of one fresh thread per
     * request; excess requests queue in the pool until a thread is free (still
     * bounded per computer by {@code http_max_requests}). The pool is rebuilt
     * if the configured thread count changes (config reload / tests).
     */
    private static final AtomicInteger EXECUTOR_THREAD_ID = new AtomicInteger();
    private static ExecutorService m_executor;
    private static int m_executorThreads = -1;

    private static synchronized ExecutorService getExecutor() {
        int threads = Math.max(1, ComputerCraft.http_threads);
        if (m_executor == null || m_executorThreads != threads) {
            if (m_executor != null) {
                // Only reachable when the configured size changed; in-flight
                // requests are interrupted and will report failure through
                // their normal error path.
                m_executor.shutdownNow();
            }

            m_executor = Executors.newFixedThreadPool(threads, r -> {
                Thread t = new Thread(r, "computercraft-http-" + EXECUTOR_THREAD_ID.incrementAndGet());
                t.setDaemon(true);
                return t;
            });
            m_executorThreads = threads;
        }

        return m_executor;
    }

    static final DomainPatternCache WHITELIST = new DomainPatternCache();
    static final DomainPatternCache BLACKLIST = new DomainPatternCache();

    /** Precompile configured domains at startup; checks also detect later config changes. */
    public static void prepareDomainPatterns() {
        WHITELIST.get(ComputerCraft.http_whitelist);
        BLACKLIST.get(ComputerCraft.http_blacklist);
    }

    public static URL checkURL(String urlString) throws LuaException {
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            throw new LuaException("URL malformed");
        }

        String protocol = url.getProtocol()
            .toLowerCase();
        if (!protocol.equals("http") && !protocol.equals("https")) throw new LuaException("URL not http");

        if (!WHITELIST.get(ComputerCraft.http_whitelist)
            .matches(url.getHost())) throw new LuaException("Domain not permitted");
        if (BLACKLIST.get(ComputerCraft.http_blacklist)
            .matches(url.getHost())) throw new LuaException("Domain blocked");

        return url;
    }

    public static URI checkWebSocketURL(String urlString) throws LuaException {
        URI uri;
        try {
            uri = new URI(urlString);
        } catch (URISyntaxException e) {
            throw new LuaException("URL malformed");
        }

        String scheme = uri.getScheme() != null ? uri.getScheme()
            .toLowerCase() : "";
        if (!scheme.equals("ws") && !scheme.equals("wss")) {
            throw new LuaException("URL must be ws or wss");
        }

        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new LuaException("URL malformed");
        }

        if (!WHITELIST.get(ComputerCraft.http_whitelist)
            .matches(host)) throw new LuaException("Domain not permitted");
        if (BLACKLIST.get(ComputerCraft.http_blacklist)
            .matches(host)) throw new LuaException("Domain blocked");

        return uri;
    }

    private static final String[] methods = { "GET", "POST", "HEAD", "OPTIONS", "PUT", "DELETE", "PATCH", "TRACE" };

    private static boolean checkMethod(String verb) {
        for (String method : methods) {
            if (verb.equals(method)) return true;
        }

        return false;
    }

    private final Object lock = new Object();
    private URL url;
    private final String urlString;
    private boolean complete = false;
    private boolean cancelled = false;
    private boolean success = false;
    private byte[] result;
    private Thread m_workerThread;
    // The HTTP status pair. asResponse() reads it under `lock`, so the worker must write
    // it under `lock` too — otherwise whoever first observes the completed request has no
    // guarantee of seeing it at all.
    private int responseCode = -1;
    private String responseMessage = "";
    private Map<String, String> responseHeaders;
    /**
     * Human-readable reason for a failure that is not a connection error (e.g.
     * the response exceeded {@code http_max_download}). {@code null} when the
     * request failed for the default reason or succeeded.
     */
    private String failureReason = null;
    /** Connection + read timeout in milliseconds. 0 means use the JVM default (no explicit timeout). */
    private final int m_timeout;
    /**
     * Whether the caller requested a binary response handle. Currently a no-op because
     * {@link HTTPResponse} always returns raw bytes; reserved for future text/binary mode
     * differentiation.
     */
    private final boolean m_binary;
    /** Optional internal correlation ID; null preserves legacy completion event shapes. */
    private final Number m_requestId;

    public HTTPRequest(final String url, final String postText, final Map<String, String> headers, final String verb,
        final int timeout, final boolean binary) throws LuaException {
        this(url, postText, headers, verb, timeout, binary, null);
    }

    public HTTPRequest(final String url, final String postText, final Map<String, String> headers, final String verb,
        final int timeout, final boolean binary, final Number requestId) throws LuaException {
        m_requestId = requestId;
        urlString = url;
        this.url = checkURL(url);
        this.m_timeout = timeout;
        this.m_binary = binary;

        if (verb != null && !checkMethod(verb)) throw new LuaException("No such verb: " + verb);

        getExecutor().execute(new Runnable() {

            @Override
            public void run() {
                // With a bounded pool a request can be cancelled before it
                // starts (queued behind other requests). It must never open a
                // connection in that case.
                synchronized (HTTPRequest.this.lock) {
                    if (HTTPRequest.this.cancelled) {
                        HTTPRequest.this.complete = true;
                        HTTPRequest.this.success = false;
                        return;
                    }

                    HTTPRequest.this.m_workerThread = Thread.currentThread();
                }

                try {
                    HttpURLConnection connection = (HttpURLConnection) HTTPRequest.this.url.openConnection();

                    if (m_timeout > 0) {
                        connection.setConnectTimeout(m_timeout);
                        connection.setReadTimeout(m_timeout);
                    }

                    { // Setup connection
                        if (verb != null) {
                            connection.setRequestMethod(verb);
                        } else if (postText != null) {
                            connection.setRequestMethod("POST");
                        } else {
                            connection.setRequestMethod("GET");
                        }

                        connection.setRequestProperty("accept-charset", "UTF-8");
                        if (postText != null) {
                            connection
                                .setRequestProperty("content-type", "application/x-www-form-urlencoded; charset=utf-8");
                            connection.setRequestProperty("content-encoding", "UTF-8");
                        }

                        if (headers != null) {
                            for (Map.Entry<String, String> header : headers.entrySet()) {
                                connection.setRequestProperty(header.getKey(), header.getValue());
                            }
                        }

                        if (postText != null) {
                            connection.setDoOutput(true);
                            OutputStream os = connection.getOutputStream();
                            OutputStreamWriter osr = new OutputStreamWriter(os);
                            BufferedWriter writer = new BufferedWriter(osr);
                            writer.write(postText, 0, postText.length());
                            writer.close();
                        }
                    }

                    int code = connection.getResponseCode();
                    String message = connection.getResponseMessage();

                    // Published under `lock` (see the fields above). A response with no
                    // reason phrase gives a null message, which HTTPResponse normalises.
                    synchronized (lock) {
                        responseCode = code;
                        responseMessage = message;
                    }

                    // If we get an error code then use the error stream instead
                    InputStream is;
                    boolean responseSuccess;
                    if (code >= 200 && code < 400) {
                        is = connection.getInputStream();
                        responseSuccess = true;
                    } else {
                        is = connection.getErrorStream();
                        responseSuccess = false;
                        if (is == null) {
                            // Server returned an error code with no body (e.g. a bare
                            // 404 with Content-Length: 0). Substitute an empty stream
                            // so asResponse() always returns a non-null handle — which
                            // is required by the 1.80pr1 "response handle on error" contract.
                            is = new ByteArrayInputStream(new byte[0]);
                        }
                    }

                    // Abort early if the server declares a body larger than the
                    // configured download limit.
                    long maxDownload = ComputerCraft.http_max_download;
                    if (maxDownload > 0) {
                        long contentLength = connection.getContentLengthLong();
                        if (contentLength > maxDownload) {
                            synchronized (lock) {
                                complete = true;
                                success = false;
                                result = null;
                                failureReason = "Download limit exceeded";
                            }
                            is.close();
                            connection.disconnect();
                            return;
                        }
                    }

                    // Read from the input stream
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.max(1024, is.available()));
                    int nRead;
                    byte[] data = new byte[1024];
                    long totalRead = 0;
                    boolean overLimit = false;
                    while ((nRead = is.read(data, 0, data.length)) != -1) {
                        synchronized (lock) {
                            if (cancelled) break;
                        }

                        totalRead += nRead;
                        if (maxDownload > 0 && totalRead > maxDownload) {
                            overLimit = true;
                            break;
                        }

                        buffer.write(data, 0, nRead);
                    }
                    is.close();

                    synchronized (lock) {
                        if (cancelled || overLimit) {
                            complete = true;
                            success = false;
                            result = null;
                            if (overLimit) failureReason = "Download limit exceeded";
                        } else {
                            complete = true;
                            success = responseSuccess;
                            result = buffer.toByteArray();

                            Map<String, String> headers = responseHeaders = new HashMap<>();
                            for (Map.Entry<String, List<String>> header : connection.getHeaderFields()
                                .entrySet()) {
                                if (header.getKey() == null) continue; // skip the HTTP status-line pseudo-header
                                headers.put(header.getKey(), String.join(", ", header.getValue()));
                            }
                        }
                    }

                    connection.disconnect();
                } catch (IOException e) {
                    synchronized (lock) {
                        complete = true;
                        success = false;
                        result = null;
                    }
                } catch (Exception e) {
                    ComputerCraft.logger.error("Unknown exception fetching {}", url, e);
                    synchronized (lock) {
                        complete = true;
                        success = false;
                        result = null;
                    }
                }
            }
        });
    }

    public Number getRequestId() {
        return m_requestId;
    }

    public String getURL() {
        return urlString;
    }

    /**
     * Returns the shared-pool thread that ran (or is running) this request, or
     * {@code null} if the request was cancelled before starting. Used for
     * diagnostics and tests (the worker must be a named
     * {@code computercraft-http-*} pool thread, never a fresh ad-hoc thread).
     */
    Thread getWorkerThread() {
        synchronized (lock) {
            return m_workerThread;
        }
    }

    /**
     * Returns the reason this request failed for a reason other than a
     * connection error (currently only {@code "Download limit exceeded"}), or
     * {@code null} if the request succeeded or failed to connect.
     */
    public String getFailureReason() {
        synchronized (lock) {
            return failureReason;
        }
    }

    public void cancel() {
        synchronized (lock) {
            cancelled = true;
        }
    }

    public boolean isComplete() {
        synchronized (lock) {
            return complete;
        }
    }

    public boolean wasSuccessful() {
        synchronized (lock) {
            return success;
        }
    }

    public HTTPResponse asResponse() {
        synchronized (lock) {
            return result == null ? null : new HTTPResponse(responseCode, responseMessage, result, responseHeaders);
        }
    }

}
