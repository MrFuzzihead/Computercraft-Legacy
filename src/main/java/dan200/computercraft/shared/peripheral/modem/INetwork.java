package dan200.computercraft.shared.peripheral.modem;

public interface INetwork {
   void addReceiver(IReceiver var1);

   void removeReceiver(IReceiver var1);

   void transmit(int var1, int var2, Object var3, double var4, double var6, double var8, double var10, Object var12);

   boolean isWireless();
}
