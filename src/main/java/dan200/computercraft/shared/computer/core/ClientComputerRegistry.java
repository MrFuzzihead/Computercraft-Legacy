package dan200.computercraft.shared.computer.core;

public class ClientComputerRegistry extends ComputerRegistry<ClientComputer> {
   public void update() {
      for (ClientComputer computer : this.getComputers()) {
         computer.update();
      }
   }

   public void add(int instanceID, ClientComputer computer) {
      super.add(instanceID, computer);
      computer.requestState();
   }

   @Override
   public void remove(int instanceID) {
      super.remove(instanceID);
   }

   @Override
   public void reset() {
      super.reset();
   }
}
