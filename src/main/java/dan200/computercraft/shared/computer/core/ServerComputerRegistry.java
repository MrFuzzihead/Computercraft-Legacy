package dan200.computercraft.shared.computer.core;

import java.util.Iterator;

public class ServerComputerRegistry extends ComputerRegistry<ServerComputer> {
   public void update() {
      Iterator<ServerComputer> it = this.getComputers().iterator();

      while (it.hasNext()) {
         ServerComputer computer = it.next();
         if (computer.hasTimedOut()) {
            computer.unload();
            computer.broadcastDelete();
            it.remove();
         } else {
            computer.update();
            if (computer.hasTerminalChanged() || computer.hasOutputChanged()) {
               computer.broadcastState();
            }
         }
      }
   }

   public void add(int instanceID, ServerComputer computer) {
      super.add(instanceID, computer);
      computer.broadcastState();
   }

   @Override
   public void remove(int instanceID) {
      ServerComputer computer = this.get(instanceID);
      if (computer != null) {
         computer.unload();
         computer.broadcastDelete();
      }

      super.remove(instanceID);
   }

   @Override
   public void reset() {
      for (ServerComputer computer : this.getComputers()) {
         computer.unload();
      }

      super.reset();
   }
}
