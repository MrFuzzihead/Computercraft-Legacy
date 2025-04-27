package dan200.computercraft.api.peripheral;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;

public interface IPeripheral {

    String getType();

    String[] getMethodNames();

    Object[] callMethod(IComputerAccess var1, ILuaContext var2, int var3, Object[] var4)
        throws LuaException, InterruptedException;

    void attach(IComputerAccess var1);

    void detach(IComputerAccess var1);

    boolean equals(IPeripheral var1);
}
