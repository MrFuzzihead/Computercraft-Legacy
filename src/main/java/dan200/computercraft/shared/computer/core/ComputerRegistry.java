package dan200.computercraft.shared.computer.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ComputerRegistry<TComputer extends IComputer> {
   private Map<Integer, TComputer> m_computers = new HashMap<>();
   private int m_nextUnusedInstanceID;
   private int m_sessionID;

   protected ComputerRegistry() {
      this.reset();
   }

   public int getSessionID() {
      return this.m_sessionID;
   }

   public int getUnusedInstanceID() {
      return this.m_nextUnusedInstanceID++;
   }

   public Collection<TComputer> getComputers() {
      return this.m_computers.values();
   }

   public TComputer get(int instanceID) {
      return instanceID >= 0 && this.m_computers.containsKey(instanceID) ? this.m_computers.get(instanceID) : null;
   }

   public TComputer lookup(int computerID) {
      if (computerID >= 0) {
         for (TComputer computer : this.getComputers()) {
            if (computer.getID() == computerID) {
               return computer;
            }
         }
      }

      return null;
   }

   public boolean contains(int instanceID) {
      return this.m_computers.containsKey(instanceID);
   }

   public void add(int instanceID, TComputer computer) {
      if (this.m_computers.containsKey(instanceID)) {
         this.remove(instanceID);
      }

      this.m_computers.put(instanceID, computer);
      this.m_nextUnusedInstanceID = Math.max(this.m_nextUnusedInstanceID, instanceID + 1);
   }

   public void remove(int instanceID) {
      if (this.m_computers.containsKey(instanceID)) {
         this.m_computers.remove(instanceID);
      }
   }

   public void reset() {
      this.m_computers.clear();
      this.m_nextUnusedInstanceID = 0;
      this.m_sessionID = new Random().nextInt();
   }
}
