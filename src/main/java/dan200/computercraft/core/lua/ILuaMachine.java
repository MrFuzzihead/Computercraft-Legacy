package dan200.computercraft.core.lua;

import java.io.InputStream;
import java.io.OutputStream;

import dan200.computercraft.core.apis.ILuaAPI;

public interface ILuaMachine {

    void addAPI(ILuaAPI var1);

    void loadBios(InputStream var1);

    void handleEvent(String var1, Object[] var2);

    void softAbort(String var1);

    void hardAbort(String var1);

    boolean saveState(OutputStream var1);

    boolean restoreState(InputStream var1);

    boolean isFinished();

    void unload();
}
