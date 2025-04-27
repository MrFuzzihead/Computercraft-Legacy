package dan200.computercraft.core.apis;

import dan200.computercraft.api.lua.ILuaObject;

public interface ILuaAPI extends ILuaObject {

    String[] getNames();

    void startup();

    void advance(double var1);

    void shutdown();
}
