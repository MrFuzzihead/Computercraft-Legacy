package dan200.computercraft.core.apis;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.computer.Computer;
import java.util.HashMap;
import java.util.Map;

public class RedstoneAPI implements ILuaAPI {
   private IAPIEnvironment m_environment;

   public RedstoneAPI(IAPIEnvironment environment) {
      this.m_environment = environment;
   }

   @Override
   public String[] getNames() {
      return new String[]{"rs", "redstone"};
   }

   @Override
   public void startup() {
   }

   @Override
   public void advance(double _dt) {
   }

   @Override
   public void shutdown() {
   }

   @Override
   public String[] getMethodNames() {
      return new String[]{
         "getSides",
         "setOutput",
         "getOutput",
         "getInput",
         "setBundledOutput",
         "getBundledOutput",
         "getBundledInput",
         "testBundledInput",
         "setAnalogOutput",
         "setAnalogueOutput",
         "getAnalogOutput",
         "getAnalogueOutput",
         "getAnalogInput",
         "getAnalogueInput"
      };
   }

   @Override
   public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
      switch (method) {
         case 0:
            Map<Object, Object> table = new HashMap<>();

            for (int i = 0; i < Computer.s_sideNames.length; i++) {
               table.put(i + 1, Computer.s_sideNames[i]);
            }

            return new Object[]{table};
         case 1:
            if (args.length >= 2 && args[0] != null && args[0] instanceof String && args[1] != null && args[1] instanceof Boolean) {
               int sidex = this.parseSide(args);
               boolean output = (Boolean)args[1];
               this.m_environment.setOutput(sidex, output ? 15 : 0);
               return null;
            }

            throw new LuaException("Expected string, boolean");
         case 2: {
            int side = this.parseSide(args);
            return new Object[]{this.m_environment.getOutput(side) > 0};
         }
         case 3: {
            int side = this.parseSide(args);
            return new Object[]{this.m_environment.getInput(side) > 0};
         }
         case 4:
            if (args.length >= 2 && args[0] != null && args[0] instanceof String && args[1] != null && args[1] instanceof Double) {
               int sidex = this.parseSide(args);
               int output = ((Double)args[1]).intValue();
               this.m_environment.setBundledOutput(sidex, output);
               return null;
            }

            throw new LuaException("Expected string, number");
         case 5: {
            int side = this.parseSide(args);
            return new Object[]{this.m_environment.getBundledOutput(side)};
         }
         case 6: {
            int side = this.parseSide(args);
            return new Object[]{this.m_environment.getBundledInput(side)};
         }
         case 7:
            if (args.length >= 2 && args[0] != null && args[0] instanceof String && args[1] != null && args[1] instanceof Double) {
               int sidex = this.parseSide(args);
               int mask = ((Double)args[1]).intValue();
               int input = this.m_environment.getBundledInput(sidex);
               return new Object[]{(input & mask) == mask};
            }

            throw new LuaException("Expected string, number");
         case 8:
         case 9:
            if (args.length >= 2 && args[0] != null && args[0] instanceof String && args[1] != null && args[1] instanceof Double) {
               int sidex = this.parseSide(args);
               int output = ((Double)args[1]).intValue();
               if (output >= 0 && output <= 15) {
                  this.m_environment.setOutput(sidex, output);
                  return null;
               }

               throw new LuaException("Expected number in range 0-15");
            }

            throw new LuaException("Expected string, number");
         case 10:
         case 11: {
            int side = this.parseSide(args);
            return new Object[]{this.m_environment.getOutput(side)};
         }
         case 12:
         case 13: {
            int side = this.parseSide(args);
            return new Object[]{this.m_environment.getInput(side)};
         }
         default:
            return null;
      }
   }

   private int parseSide(Object[] args) throws LuaException {
      if (args.length >= 1 && args[0] != null && args[0] instanceof String) {
         String side = (String)args[0];

         for (int n = 0; n < Computer.s_sideNames.length; n++) {
            if (side.equals(Computer.s_sideNames[n])) {
               return n;
            }
         }

         throw new LuaException("Invalid side.");
      } else {
         throw new LuaException("Expected string");
      }
   }
}
