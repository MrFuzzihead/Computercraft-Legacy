package dan200.computercraft.api.core;

import dan200.computercraft.api.turtle.ITurtleCommand;

public class TurtleCommandQueueEntry {
   public final int callbackID;
   public final ITurtleCommand command;

   public TurtleCommandQueueEntry(int callbackID, ITurtleCommand command) {
      this.callbackID = callbackID;
      this.command = command;
   }
}
