package dan200.computercraft.api.lua;

public interface ILuaObject {

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
     * The default delegates to {@link #callMethod}, so existing implementations -- including
     * every companion mod written against ComputerCraft 1.7.10 -- compile and behave exactly as
     * before. Override it to return a {@link MethodResult} directly instead of the single-element
     * sentinel array.
     *
     * @return An {@code Object[]} of values, a {@link MethodResult} to suspend, or null for no
     *         values.
     */
    default Object callMethodResult(ILuaContext context, int method, Object[] args)
        throws LuaException, InterruptedException {
        return callMethod(context, method, args);
    }

    Object[] callMethod(ILuaContext var1, int var2, Object[] var3) throws LuaException, InterruptedException;
}
