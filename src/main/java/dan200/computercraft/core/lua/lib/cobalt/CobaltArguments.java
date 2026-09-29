package dan200.computercraft.core.lua.lib.cobalt;

import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaString;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.Varargs;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;

public class CobaltArguments implements IArguments {

    private final Varargs args;

    public CobaltArguments(Varargs args) {
        this.args = args;
    }

    @Override
    public int size() {
        return args.count();
    }

    @Override
    public double getNumber(int index) throws LuaException {
        LuaValue value = args.arg(index + 1);
        if (value.isNumber()) {
            return value.toDouble();
        } else {
            throw new LuaException("Expected number");
        }
    }

    @Override
    public boolean getBoolean(int index) throws LuaException {
        LuaValue value = args.arg(index + 1);
        if (value.type() == Constants.TBOOLEAN) {
            return value.toBoolean();
        } else {
            throw new LuaException("Expected boolean");
        }
    }

    @Override
    public String getString(int index) throws LuaException {
        LuaValue value = args.arg(index + 1);
        if (value.isString()) {
            return value.toString();
        } else {
            throw new LuaException("Expected string");
        }
    }

    @Override
    public byte[] getStringBytes(int index) throws LuaException {
        LuaValue value = args.arg(index + 1);
        if (value.isString()) {
            // LuaString is final and is the only string type, so a TSTRING value is always a
            // LuaString; its backing array is private.
            return CobaltConverter.toByteArray((LuaString) value);
        } else {
            throw new LuaException("Expected string");
        }
    }

    @Override
    public Object getArgumentBinary(int index) {
        try {
            return CobaltConverter.toObject(args.arg(index + 1), true);
        } catch (LuaError e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object getArgument(int index) {
        try {
            return CobaltConverter.toObject(args.arg(index + 1), false);
        } catch (LuaError e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object[] asArguments() {
        try {
            return CobaltConverter.toObjects(args, 1, false);
        } catch (LuaError e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object[] asBinary() {
        try {
            return CobaltConverter.toObjects(args, 1, true);
        } catch (LuaError e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public IArguments subArgs(int offset) {
        return new CobaltArguments(args.subargs(offset + 1));
    }
}
