package dan200.computercraft.shared.peripheral.modem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class WirelessNetwork implements INetwork {
   private static Map<World, WirelessNetwork> s_networks = new WeakHashMap<>();
   private Map<Integer, Set<IReceiver>> m_receivers = new HashMap<>();

   public static WirelessNetwork get(World world) {
      if (world != null) {
         WirelessNetwork network = s_networks.get(world);
         if (network == null) {
            network = new WirelessNetwork();
            s_networks.put(world, network);
         }

         return network;
      } else {
         return null;
      }
   }

   private WirelessNetwork() {
   }

   @Override
   public synchronized void addReceiver(IReceiver receiver) {
      int channel = receiver.getChannel();
      Set<IReceiver> receivers = this.m_receivers.get(channel);
      if (receivers == null) {
         receivers = new HashSet<>();
         this.m_receivers.put(channel, receivers);
      }

      receivers.add(receiver);
   }

   @Override
   public synchronized void removeReceiver(IReceiver receiver) {
      int channel = receiver.getChannel();
      Set<IReceiver> receivers = this.m_receivers.get(channel);
      if (receivers != null) {
         receivers.remove(receiver);
      }
   }

   @Override
   public synchronized void transmit(int channel, int replyChannel, Object payload, double range, double xPos, double yPos, double zPos, Object senderObject) {
      Set<IReceiver> receivers = this.m_receivers.get(channel);
      if (receivers != null) {
         for (IReceiver receiver : receivers) {
            this.tryTransmit(receiver, replyChannel, payload, range, xPos, yPos, zPos, senderObject);
         }
      }
   }

   private void tryTransmit(IReceiver receiver, int replyChannel, Object payload, double range, double xPos, double yPos, double zPos, Object senderObject) {
      Vec3 position = receiver.getWorldPosition();
      double receiveRange = Math.max(range, receiver.getReceiveRange());
      double distanceSq = position.squareDistanceTo(xPos, yPos, zPos);
      if (distanceSq <= receiveRange * receiveRange) {
         receiver.receive(replyChannel, payload, Math.sqrt(distanceSq), senderObject);
      }
   }

   @Override
   public boolean isWireless() {
      return true;
   }
}
