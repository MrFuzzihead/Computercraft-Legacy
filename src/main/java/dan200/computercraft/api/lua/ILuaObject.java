package dan200.computercraft.api.lua;

public interface ILuaObject {

    String[] getMethodNames();

    Object[] callMethod(ILuaContext var1, int var2, Object[] var3) throws LuaException, InterruptedException;
}
