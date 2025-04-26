package dan200.computercraft.shared.turtle.upgrades;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.core.TurtleCraftCommand;

public class CraftingTablePeripheral implements IPeripheral {
   private final ITurtleAccess m_turtle;

   public CraftingTablePeripheral(ITurtleAccess turtle) {
      this.m_turtle = turtle;
   }

   @Override
   public String getType() {
      return "workbench";
   }

   @Override
   public String[] getMethodNames() {
      return new String[]{"craft"};
   }

   private int parseCount(Object[] arguments) throws LuaException {
      if (arguments.length < 1) {
         return 64;
      } else if (!(arguments[0] instanceof Number)) {
         throw new LuaException("Expected number");
      } else {
         int count = ((Number)arguments[0]).intValue();
         if (count >= 0 && count <= 64) {
            return count;
         } else {
            throw new LuaException("Crafting count " + count + " out of range");
         }
      }
   }

   @Override
   public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments) throws LuaException, InterruptedException {
      switch (method) {
         case 0:
            int limit = this.parseCount(arguments);
            return this.m_turtle.executeCommand(context, new TurtleCraftCommand(limit));
         default:
            return null;
      }
   }

   @Override
   public void attach(IComputerAccess computer) {
   }

   @Override
   public void detach(IComputerAccess computer) {
   }

   @Override
   public boolean equals(IPeripheral other) {
      return other != null && other.getClass() == this.getClass();
   }
}
