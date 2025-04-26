package dan200.computercraft.api.core;

import dan200.computercraft.api.turtle.ITurtleAccess;

public enum InteractDirection {
   Forward,
   Up,
   Down;

   public int toWorldDir(ITurtleAccess turtle) {
      switch (this) {
         case Forward:
         default:
            return turtle.getDirection();
         case Up:
            return 1;
         case Down:
            return 0;
      }
   }
}
