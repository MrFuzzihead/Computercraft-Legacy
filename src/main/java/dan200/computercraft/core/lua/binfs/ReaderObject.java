package dan200.computercraft.core.lua.binfs;

import java.io.IOException;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObjectWithArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.filesystem.IMountedFileNormal;

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
        return new String[] { "readLine", "readAll", "close" };
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] args)
        throws LuaException, InterruptedException {
        switch (method) {
            case 0:
                try {
                    byte[] result = stream.readLine();
                    if (result != null) return new Object[] { result };
                    return null;
                } catch (IOException ignored) {}
                return null;
            case 1:
                try {
                    byte[] result = stream.readAll();
                    if (result != null) return new Object[] { result };
                    return null;
                } catch (IOException ignored) {}
                return null;
            case 2:
                try {
                    stream.close();
                } catch (IOException ignored) {}
                return null;
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
