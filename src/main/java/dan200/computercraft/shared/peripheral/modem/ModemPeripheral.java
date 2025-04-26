package dan200.computercraft.shared.peripheral.modem;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.util.Vec3;

public abstract class ModemPeripheral implements IPeripheral {
   private INetwork m_network = null;
   private IComputerAccess m_computer = null;
   private Map<Integer, IReceiver> m_channels = new HashMap<>();
   private boolean m_open = false;
   private boolean m_changed = true;

   private synchronized void setNetwork(INetwork network) {
      if (this.m_network != network) {
         if (this.m_network != null) {
            Iterator<IReceiver> it = this.m_channels.values().iterator();

            while (it.hasNext()) {
               this.m_network.removeReceiver(it.next());
            }
         }

         this.m_network = network;
         if (this.m_network != null) {
            Iterator<IReceiver> it = this.m_channels.values().iterator();

            while (it.hasNext()) {
               this.m_network.addReceiver(it.next());
            }
         }
      }
   }

   protected void switchNetwork() {
      this.setNetwork(this.getNetwork());
   }

   protected abstract Vec3 getPosition();

   public synchronized void destroy() {
      this.setNetwork(null);
      this.m_channels.clear();
      this.m_open = false;
   }

   public synchronized boolean pollChanged() {
      if (this.m_changed) {
         this.m_changed = false;
         return true;
      } else {
         return false;
      }
   }

   protected abstract double getTransmitRange();

   public synchronized boolean isActive() {
      return this.m_computer != null && this.m_open;
   }

   public synchronized Vec3 getWorldPosition() {
      return this.getPosition();
   }

   public synchronized double getReceiveRange() {
      return this.getTransmitRange();
   }

   public void receive(int channel, int replyChannel, Object payload, double distance) {
      synchronized (this.m_channels) {
         if (this.m_computer != null && this.m_channels.containsKey(channel)) {
            this.m_computer.queueEvent("modem_message", new Object[]{this.m_computer.getAttachmentName(), channel, replyChannel, payload, distance});
         }
      }
   }

   protected abstract INetwork getNetwork();

   @Override
   public String getType() {
      return "modem";
   }

   @Override
   public String[] getMethodNames() {
      return new String[]{"open", "isOpen", "close", "closeAll", "transmit", "isWireless"};
   }

   private static int parseChannel(Object[] arguments, int index) throws LuaException {
      if (arguments.length > index && arguments[index] instanceof Double) {
         int channel = (int)((Double)arguments[index]).doubleValue();
         if (channel >= 0 && channel <= 65535) {
            return channel;
         } else {
            throw new LuaException("Expected number in range 0-65535");
         }
      } else {
         throw new LuaException("Expected number");
      }
   }

   @Override
   public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments) throws LuaException, InterruptedException {
      switch (method) {
         case 0:
            int channel = parseChannel(arguments, 0);
            synchronized (this) {
               if (!this.m_channels.containsKey(channel)) {
                  if (this.m_channels.size() >= 128) {
                     throw new LuaException("Too many open channels");
                  }

                  IReceiver receiverx = new ModemPeripheral.SingleChannelReceiver(this, channel);
                  this.m_channels.put(channel, receiverx);
                  if (this.m_network != null) {
                     this.m_network.addReceiver(receiverx);
                  }

                  if (!this.m_open) {
                     this.m_open = true;
                     this.m_changed = true;
                  }
               }

               return null;
            }
         case 1:
            int channel1 = parseChannel(arguments, 0);
            synchronized (this) {
               boolean open = this.m_channels.containsKey(channel1);
               return new Object[]{open};
            }
         case 2:
            int channel2 = parseChannel(arguments, 0);
            synchronized (this) {
               if (this.m_channels.containsKey(channel2)) {
                  IReceiver receiver = this.m_channels.get(channel2);
                  if (this.m_network != null) {
                     this.m_network.removeReceiver(receiver);
                  }

                  this.m_channels.remove(channel2);
                  if (this.m_channels.size() == 0) {
                     this.m_open = false;
                     this.m_changed = true;
                  }
               }

               return null;
            }
         case 3:
            synchronized (this) {
               if (this.m_channels.size() > 0) {
                  if (this.m_network != null) {
                     Iterator<IReceiver> it = this.m_channels.values().iterator();

                     while (it.hasNext()) {
                        this.m_network.removeReceiver(it.next());
                     }
                  }

                  this.m_channels.clear();
                  if (this.m_open) {
                     this.m_open = false;
                     this.m_changed = true;
                  }
               }

               return null;
            }
         case 4:
            int channel4 = parseChannel(arguments, 0);
            int replyChannel = parseChannel(arguments, 1);
            Object payload = arguments.length >= 3 ? arguments[2] : null;
            synchronized (this) {
               Vec3 position = this.getPosition();
               if (position != null && this.m_network != null) {
                  this.m_network
                     .transmit(
                        channel4, replyChannel, payload, this.getTransmitRange(), position.xCoord, position.yCoord, position.zCoord, this
                     );
               }

               return null;
            }
         case 5:
            synchronized (this) {
               if (this.m_network != null) {
                  return new Object[]{this.m_network.isWireless()};
               }
            }

            return new Object[]{false};
         default:
            return null;
      }
   }

   @Override
   public synchronized void attach(IComputerAccess computer) {
      this.m_computer = computer;
      this.setNetwork(this.getNetwork());
      this.m_open = !this.m_channels.isEmpty();
   }

   @Override
   public synchronized void detach(IComputerAccess computer) {
      if (this.m_network != null) {
         Iterator<IReceiver> it = this.m_channels.values().iterator();

         while (it.hasNext()) {
            this.m_network.removeReceiver(it.next());
         }

         this.m_channels.clear();
         this.m_network = null;
      }

      this.m_computer = null;
      if (this.m_open) {
         this.m_open = false;
         this.m_changed = true;
      }
   }

   @Override
   public abstract boolean equals(IPeripheral var1);

   public IComputerAccess getComputer() {
      return this.m_computer;
   }

   private static class SingleChannelReceiver implements IReceiver {
      private ModemPeripheral m_owner;
      private int m_channel;

      public SingleChannelReceiver(ModemPeripheral owner, int channel) {
         this.m_owner = owner;
         this.m_channel = channel;
      }

      @Override
      public int getChannel() {
         return this.m_channel;
      }

      @Override
      public Vec3 getWorldPosition() {
         return this.m_owner.getWorldPosition();
      }

      @Override
      public double getReceiveRange() {
         return this.m_owner.getReceiveRange();
      }

      @Override
      public void receive(int replyChannel, Object payload, double distance, Object senderObject) {
         if (senderObject != this.m_owner) {
            this.m_owner.receive(this.m_channel, replyChannel, payload, distance);
         }
      }
   }
}
