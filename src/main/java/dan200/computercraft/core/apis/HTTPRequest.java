package dan200.computercraft.core.apis;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.LuaException;

public class HTTPRequest {

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

        boolean allowed = false;
        String whitelistString = ComputerCraft.http_whitelist;
        String[] allowedURLs = whitelistString.split(";");
        for (String allowedURL : allowedURLs) {
            Pattern allowedURLPattern = Pattern.compile("^\\Q" + allowedURL.replaceAll("\\*", "\\\\E.*\\\\Q") + "\\E$");
            if (allowedURLPattern.matcher(url.getHost())
                .matches()) {
                allowed = true;
                break;
            }
        }

        if (!allowed) throw new LuaException("Domain not permitted");

        return url;
    }

    private final Object lock = new Object();
    private URL url;
    private final String urlString;
    private boolean complete = false;
    private boolean cancelled = false;
    private boolean success = false;
    private byte[] result;
    private int responseCode = -1;
    private Map<String, Map<Integer, String>> responseHeaders;

    private static final String[] methods = { "GET", "POST", "HEAD", "OPTIONS", "PUT", "DELETE", "TRACE" };

    private static boolean checkMethod(String verb) {
        for (String method : methods) {
            if (verb.equals(method)) return true;
        }

        return false;
    }

    public HTTPRequest(final String url, final String postText, final Map<String, String> headers, final String verb)
        throws LuaException {
        urlString = url;
        this.url = checkURL(url);

        if (verb != null && !checkMethod(verb)) throw new LuaException("No such verb: " + verb);

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    HttpURLConnection connection = (HttpURLConnection) HTTPRequest.this.url.openConnection();

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

                    int code = responseCode = connection.getResponseCode();

                    // If we get an error code then use the error stream instead
                    InputStream is;
                    boolean responseSuccess;
                    if (code >= 200 && code < 400) {
                        is = connection.getInputStream();
                        responseSuccess = true;
                    } else {
                        is = connection.getErrorStream();
                        responseSuccess = false;
                    }

                    // Read from the input stream
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.max(1024, is.available()));
                    int nRead;
                    byte[] data = new byte[1024];
                    while ((nRead = is.read(data, 0, data.length)) != -1) {
                        synchronized (lock) {
                            if (cancelled) break;
                        }

                        buffer.write(data, 0, nRead);
                    }
                    is.close();

                    synchronized (lock) {
                        if (cancelled) {
                            complete = true;
                            success = false;
                            result = null;
                        } else {
                            complete = true;
                            success = responseSuccess;
                            result = buffer.toByteArray();

                            Map<String, Map<Integer, String>> headers = responseHeaders = new HashMap<String, Map<Integer, String>>();
                            for (Map.Entry<String, List<String>> header : connection.getHeaderFields()
                                .entrySet()) {
                                Map<Integer, String> values = new HashMap<Integer, String>();

                                int i = 0;
                                for (String value : header.getValue()) {
                                    values.put(++i, value);
                                }

                                headers.put(header.getKey(), values);
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
                    // Logger.error("Unknown exception fetching " + url, e);
                    synchronized (lock) {
                        complete = true;
                        success = false;
                        result = null;
                    }
                }
            }
        });
        thread.start();
    }

    public String getURL() {
        return urlString;
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
            return result == null ? null : new HTTPResponse(responseCode, result, responseHeaders);
        }
    }

}
