package dan200.computercraft.api.peripheral;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;

public interface IPeripheral {

    String getType();

    String[] getMethodNames();

    /**
     * Invoke a method, allowing the call to suspend by returning a {@link MethodResult}.
     *
     * <p>
     * This is the form the Lua bridge actually calls. It exists because {@link #callMethod}'s
     * {@code Object[]} return cannot express a suspension -- a peripheral that has to wait for an
     * event cannot block the thread that would deliver it. See {@link MethodResult}.
     *
     * <p>
     * The default delegates to {@link #callMethod}, so existing implementations compile and behave
     * exactly as before. Override it to return a {@link MethodResult} directly.
     *
     * @return An {@code Object[]} of values, a {@link MethodResult} to suspend, or null for no
     *         values.
     */
    default Object callMethodResult(IComputerAccess computer, ILuaContext context, int method, Object[] args)
        throws LuaException, InterruptedException {
        return callMethod(computer, context, method, args);
    }

    Object[] callMethod(IComputerAccess var1, ILuaContext var2, int var3, Object[] var4)
        throws LuaException, InterruptedException;

    void attach(IComputerAccess var1);

    void detach(IComputerAccess var1);

    boolean equals(IPeripheral var1);
}
