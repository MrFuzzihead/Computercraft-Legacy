package dan200.computercraft.shared.network;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientCustomPacketEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ServerCustomPacketEvent;
import dan200.computercraft.ComputerCraft;
import net.minecraft.network.NetHandlerPlayServer;

public class PacketHandler {
   @SubscribeEvent
   public void onClientPacket(ClientCustomPacketEvent event) {
      try {
         ComputerCraftPacket packet = new ComputerCraftPacket();
         packet.fromBytes(event.packet.payload());
         ComputerCraft.handlePacket(packet, null);
      } catch (Exception var3) {
         var3.printStackTrace();
      }
   }

   @SubscribeEvent
   public void onServerPacket(ServerCustomPacketEvent event) {
      try {
         ComputerCraftPacket packet = new ComputerCraftPacket();
         packet.fromBytes(event.packet.payload());
         ComputerCraft.handlePacket(packet, ((NetHandlerPlayServer)event.handler).playerEntity);
      } catch (Exception var3) {
         var3.printStackTrace();
      }
   }
}
