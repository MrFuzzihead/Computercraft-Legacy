package dan200.computercraft.core.apis;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import dan200.computercraft.ComputerCraft;

public class HTTPRequest {

    private Object m_lock = new Object();
    private URL m_url;
    private final String m_urlString;
    private boolean m_complete;
    private boolean m_cancelled;
    private boolean m_success;
    private String m_result;
    private int m_responseCode;

    public static URL checkURL(String urlString) throws HTTPRequestException {
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException var9) {
            throw new HTTPRequestException("URL malformed");
        }

        String protocol = url.getProtocol()
            .toLowerCase();
        if (!protocol.equals("http") && !protocol.equals("https")) {
            throw new HTTPRequestException("URL not http");
        } else {
            boolean allowed = false;
            String whitelistString = ComputerCraft.http_whitelist;
            String[] allowedURLs = whitelistString.split(";");

            for (int i = 0; i < allowedURLs.length; i++) {
                String allowedURL = allowedURLs[i];
                Pattern allowedURLPattern = Pattern
                    .compile("^\\Q" + allowedURL.replaceAll("\\*", "\\\\E.*\\\\Q") + "\\E$");
                if (allowedURLPattern.matcher(url.getHost())
                    .matches()) {
                    allowed = true;
                    break;
                }
            }

            if (!allowed) {
                throw new HTTPRequestException("Domain not permitted");
            } else {
                return url;
            }
        }
    }

    public HTTPRequest(String url, final String postText, final Map<String, String> headers)
        throws HTTPRequestException {
        this.m_urlString = url;
        this.m_url = checkURL(this.m_urlString);
        this.m_cancelled = false;
        this.m_complete = false;
        this.m_success = false;
        this.m_result = null;
        this.m_responseCode = -1;
        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    HttpURLConnection connection = (HttpURLConnection) HTTPRequest.this.m_url.openConnection();
                    if (postText != null) {
                        connection.setRequestMethod("POST");
                        connection.setDoOutput(true);
                    } else {
                        connection.setRequestMethod("GET");
                    }

                    if (headers != null) {
                        for (Entry<String, String> header : headers.entrySet()) {
                            connection.setRequestProperty(header.getKey(), header.getValue());
                        }
                    }

                    if (postText != null) {
                        OutputStream os = connection.getOutputStream();
                        OutputStreamWriter osr = new OutputStreamWriter(os);
                        BufferedWriter writer = new BufferedWriter(osr);
                        writer.write(postText, 0, postText.length());
                        writer.close();
                    }

                    InputStream is = connection.getInputStream();
                    InputStreamReader isr = new InputStreamReader(is);
                    BufferedReader reader = new BufferedReader(isr);
                    StringBuilder result = new StringBuilder();

                    while (true) {
                        synchronized (HTTPRequest.this.m_lock) {
                            if (HTTPRequest.this.m_cancelled) {
                                break;
                            }
                        }

                        String line = reader.readLine();
                        if (line == null) {
                            break;
                        }

                        result.append(line);
                        result.append('\n');
                    }

                    reader.close();
                    synchronized (HTTPRequest.this.m_lock) {
                        if (HTTPRequest.this.m_cancelled) {
                            HTTPRequest.this.m_complete = true;
                            HTTPRequest.this.m_success = false;
                            HTTPRequest.this.m_result = null;
                        } else {
                            HTTPRequest.this.m_complete = true;
                            HTTPRequest.this.m_success = true;
                            HTTPRequest.this.m_result = result.toString();
                            HTTPRequest.this.m_responseCode = connection.getResponseCode();
                        }
                    }

                    connection.disconnect();
                } catch (IOException var13) {
                    synchronized (HTTPRequest.this.m_lock) {
                        HTTPRequest.this.m_complete = true;
                        HTTPRequest.this.m_success = false;
                        HTTPRequest.this.m_result = null;
                    }
                }
            }
        });
        thread.start();
    }

    public String getURL() {
        return this.m_urlString;
    }

    public void cancel() {
        synchronized (this.m_lock) {
            this.m_cancelled = true;
        }
    }

    public boolean isComplete() {
        synchronized (this.m_lock) {
            return this.m_complete;
        }
    }

    public int getResponseCode() {
        synchronized (this.m_lock) {
            return this.m_responseCode;
        }
    }

    public boolean wasSuccessful() {
        synchronized (this.m_lock) {
            return this.m_success;
        }
    }

    public BufferedReader getContents() {
        String result = null;
        synchronized (this.m_lock) {
            result = this.m_result;
        }

        return result != null ? new BufferedReader(new StringReader(result)) : null;
    }
}
