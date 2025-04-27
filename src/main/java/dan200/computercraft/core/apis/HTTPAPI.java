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

    public HTTPAPI(IAPIEnvironment environment) {
        this.m_apiEnvironment = environment;
        this.m_httpRequests = new ArrayList<>();
    }

    @Override
    public String[] getNames() {
        return new String[] { "http" };
    }

    @Override
    public void startup() {}

    @Override
    public void advance(double _dt) {
        synchronized (this.m_httpRequests) {
            Iterator<HTTPRequest> it = this.m_httpRequests.iterator();

            while (it.hasNext()) {
                HTTPRequest h = it.next();
                if (h.isComplete()) {
                    String url = h.getURL();
                    if (h.wasSuccessful()) {
                        BufferedReader contents = h.getContents();
                        int responseCode = h.getResponseCode();
                        Object result = wrapBufferedReader(contents, responseCode);
                        this.m_apiEnvironment.queueEvent("http_success", new Object[] { url, result });
                    } else {
                        this.m_apiEnvironment.queueEvent("http_failure", new Object[] { url, "Could not connect" });
                    }

                    it.remove();
                }
            }
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
    }

    @Override
    public String[] getMethodNames() {
        return new String[] { "request", "checkURL" };
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
        switch (method) {
            case 0:
                if (args.length >= 1 && args[0] instanceof String) {
                    String urlString = args[0].toString();
                    String postString = null;
                    if (args.length >= 2 && args[1] instanceof String) {
                        postString = args[1].toString();
                    }

                    Map<String, String> headers = null;
                    if (args.length >= 3 && args[2] instanceof Map) {
                        Map table = (Map) args[2];
                        headers = new HashMap<>(table.size());

                        for (Object key : table.keySet()) {
                            Object value = table.get(key);
                            if (key instanceof String && value instanceof String) {
                                headers.put((String) key, (String) value);
                            }
                        }
                    }

                    try {
                        HTTPRequest request = new HTTPRequest(urlString, postString, headers);
                        synchronized (this.m_httpRequests) {
                            this.m_httpRequests.add(request);
                        }

                        return new Object[] { true };
                    } catch (HTTPRequestException var14) {
                        return new Object[] { false, var14.getMessage() };
                    }
                } else {
                    throw new LuaException("Expected string");
                }
            case 1:
                if (args.length >= 1 && args[0] instanceof String) {
                    String urlString = args[0].toString();

                    try {
                        HTTPRequest.checkURL(urlString);
                        return new Object[] { true };
                    } catch (HTTPRequestException var12) {
                        return new Object[] { false, var12.getMessage() };
                    }
                }

                throw new LuaException("Expected string");
            default:
                return null;
        }
    }
}
