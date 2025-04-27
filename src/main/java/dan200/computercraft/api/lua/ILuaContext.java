package dan200.computercraft.api.lua;

public interface ILuaContext {

    Object[] pullEvent(String var1) throws LuaException, InterruptedException;

    Object[] pullEventRaw(String var1) throws InterruptedException;

    Object[] yield(Object[] var1) throws InterruptedException;

    Object[] executeMainThreadTask(ILuaTask var1) throws LuaException, InterruptedException;

    long issueMainThreadTask(ILuaTask var1) throws LuaException;
}
