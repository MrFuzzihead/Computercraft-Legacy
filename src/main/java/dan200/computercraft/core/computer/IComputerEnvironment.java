package dan200.computercraft.core.computer;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;

public interface IComputerEnvironment {
   int getDay();

   double getTimeOfDay();

   boolean isColour();

   long getComputerSpaceLimit();

   int assignNewID();

   IWritableMount createSaveDirMount(String var1, long var2);

   IMount createResourceMount(String var1, String var2);
}
