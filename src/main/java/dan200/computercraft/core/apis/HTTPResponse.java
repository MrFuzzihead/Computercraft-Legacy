package dan200.computercraft.core.apis;

import java.util.Arrays;
import java.util.Map;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObject;
import dan200.computercraft.api.lua.LuaException;

public class HTTPResponse implements ILuaObject {

    private final int responseCode;
    private final String m_responseMessage;
    private final byte[] result;
    private final Map<String, String> headers;
    private int index = 0;
    private boolean closed = false;

    public HTTPResponse(int responseCode, String responseMessage, byte[] result, Map<String, String> headers) {
        this.responseCode = responseCode;
        this.m_responseMessage = responseMessage != null ? responseMessage : "";
        this.result = result;
        this.headers = headers;
    }

    @Override
    public String[] getMethodNames() {
        return new String[] { "readLine", "readAll", "read", "close", "getResponseCode", "getResponseHeaders" };
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] args)
        throws LuaException, InterruptedException {
        switch (method) {
            case 0: {
                // readLine([withTrailingNewline])
                if (closed) return null;
                if (index >= result.length) return new Object[1];

                boolean withTrailing = args.length > 0 && Boolean.TRUE.equals(args[0]);

                int start = index, end = -1, newIndex = -1;

                for (int i = start; i < result.length; i++) {
                    if (result[i] == '\r') {
                        if (i + 1 < result.length && result[i + 1] == '\n') {
                            end = i;
                            newIndex = i + 2;
                            break;
                        } else {
                            end = i;
                            newIndex = i + 1;
                            break;
                        }
                    } else if (result[i] == '\n') {
                        end = i;
                        newIndex = i + 1;
                        break;
                    }
                }

                int returnEnd;
                if (end == -1) {
                    // No newline found — consume to end of buffer
                    returnEnd = result.length;
                    index = result.length;
                } else {
                    index = newIndex;
                    returnEnd = withTrailing ? newIndex : end;
                }

                if (returnEnd <= start) return new Object[] { "" };
                return new Object[] { Arrays.copyOfRange(result, start, returnEnd) };
            }
            case 1: {
                if (closed) return null;
                if (index >= result.length) return new Object[] { "" };

                int start = index;
                int end = result.length;
                index = end;

                return new Object[] { Arrays.copyOfRange(result, start, end) };
            }
            case 2: {
                // read([count])
                if (closed) return null;
                if (index >= result.length) return new Object[1];

                int count = 1;
                if (args.length > 0 && args[0] instanceof Number) {
                    count = Math.max(1, (int) ((Number) args[0]).doubleValue());
                }

                int end = Math.min(index + count, result.length);
                byte[] bytes = Arrays.copyOfRange(result, index, end);
                index = end;
                return new Object[] { bytes };
            }
            case 3:
                closed = true;
                break;
            case 4:
                return new Object[] { responseCode, m_responseMessage };
            case 5:
                return new Object[] { headers };
        }

        return null;
    }
}
