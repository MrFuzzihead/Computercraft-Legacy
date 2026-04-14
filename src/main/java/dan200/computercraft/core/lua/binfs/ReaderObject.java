package dan200.computercraft.core.lua.binfs;

import java.io.IOException;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObjectWithArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.filesystem.IMountedFileNormal;
import dan200.computercraft.core.lua.lib.BinaryConverter;

/**
 * Basic file objects
 */
public class ReaderObject implements ILuaObjectWithArguments {

    private final IMountedFileNormal stream;

    public ReaderObject(IMountedFileNormal stream) {
        this.stream = stream;
    }

    @Override
    public String[] getMethodNames() {
        return new String[] { "read", "readLine", "readAll", "close", "seek" };
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] args)
        throws LuaException, InterruptedException {
        switch (method) {
            case 0: { // read([count])
                int count = args.length > 0 && args[0] instanceof Number ? ((Number) args[0]).intValue() : 1;
                try {
                    byte[] result = stream.read(count);
                    return result != null ? new Object[] { result } : null;
                } catch (IOException ignored) {}
                return null;
            }
            case 1: { // readLine([withTrailing])
                boolean withTrailing = args.length > 0 && args[0] instanceof Boolean && (Boolean) args[0];
                try {
                    byte[] result = stream.readLine(withTrailing);
                    return result != null ? new Object[] { result } : null;
                } catch (IOException ignored) {}
                return null;
            }
            case 2: // readAll
                try {
                    byte[] result = stream.readAll();
                    return result != null ? new Object[] { result } : null;
                } catch (IOException ignored) {}
                return null;
            case 3: // close
                try {
                    stream.close();
                } catch (IOException ignored) {}
                return null;
            case 4: { // seek([whence[, offset]])
                String whence = extractWhence(args, 0, "cur");
                long offset = args.length > 1 && args[1] instanceof Number ? ((Number) args[1]).longValue() : 0L;
                try {
                    long pos = stream.seek(whence, offset);
                    return new Object[] { pos };
                } catch (IOException e) {
                    return new Object[] { null, e.getMessage() };
                }
            }
            default:
                return null;
        }
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, IArguments arguments)
        throws LuaException, InterruptedException {
        return callMethod(context, method, arguments.asBinary());
    }

    /**
     * Extract a string argument from {@code args[index]}, handling both the {@link String} and
     * {@code byte[]} forms that Cobalt may deliver depending on whether the calling path used
     * {@link dan200.computercraft.api.lua.IArguments#asBinary()} (binary mode, returns byte[]) or
     * the string conversion path.
     */
    public static String extractWhence(Object[] args, int index, String defaultValue) {
        if (args.length <= index || args[index] == null) return defaultValue;
        Object v = args[index];
        if (v instanceof String) return (String) v;
        if (v instanceof byte[]) return BinaryConverter.decodeString((byte[]) v);
        return defaultValue;
    }
}
