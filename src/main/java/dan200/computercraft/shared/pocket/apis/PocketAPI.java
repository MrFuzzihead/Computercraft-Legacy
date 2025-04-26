package dan200.computercraft.shared.pocket.apis;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.apis.ILuaAPI;

public class PocketAPI implements ILuaAPI {
   @Override
   public String[] getNames() {
      return new String[]{"pocket"};
   }

   @Override
   public void startup() {
   }

   @Override
   public void advance(double dt) {
   }

   @Override
   public void shutdown() {
   }

   @Override
   public String[] getMethodNames() {
      return new String[0];
   }

   @Override
   public Object[] callMethod(ILuaContext context, int method, Object[] arguments) throws LuaException {
      return null;
   }
}
