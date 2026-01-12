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
public class WriterObject implements ILuaObjectWithArguments {

    private final IMountedFileNormal stream;

    public WriterObject(IMountedFileNormal stream) {
        this.stream = stream;
    }

    @Override
    public String[] getMethodNames() {
        return new String[] { "write", "writeLine", "close", "flush" };
    }

    private void write(Object[] args, boolean newLine) throws LuaException {
        byte[] result;
        if (args.length > 0 && args[0] != null) {
            result = args[0] instanceof byte[] ? (byte[]) args[0] : BinaryConverter.toBytes(args[0].toString());
        } else {
            result = new byte[0];
        }

        try {
            stream.write(result, 0, result.length, newLine);
        } catch (IOException var8) {
            throw new LuaException(var8.getMessage());
        }
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
        switch (method) {
            case 0: {
                write(args, false);
                return null;
            }
            case 1:
                write(args, true);
                return null;
            case 2:
                try {
                    stream.close();
                    return null;
                } catch (IOException ignored) {
                    return null;
                }
            case 3:
                try {
                    stream.flush();
                    return null;
                } catch (IOException ignored) {
                    return null;
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
}
