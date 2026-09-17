package dan200.computercraft.shared.network;

import net.minecraft.network.NetHandlerPlayServer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientCustomPacketEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ServerCustomPacketEvent;
import dan200.computercraft.ComputerCraft;

public class PacketHandler {

    @SubscribeEvent
    public void onClientPacket(ClientCustomPacketEvent event) {
        try {
            ComputerCraftPacket packet = new ComputerCraftPacket();
            packet.fromBytes(event.packet.payload());
            ComputerCraft.proxy.handleClientPacket(event.manager, packet);
        } catch (Exception var3) {
            ComputerCraft.logger.error("ComputerCraft: failed to decode or handle client-bound packet", var3);
        }
    }

    @SubscribeEvent
    public void onServerPacket(ServerCustomPacketEvent event) {
        try {
            ComputerCraftPacket packet = new ComputerCraftPacket();
            packet.fromBytes(event.packet.payload());
            ComputerCraft.handlePacket(packet, ((NetHandlerPlayServer) event.handler).playerEntity);
        } catch (Exception var3) {
            ComputerCraft.logger.error("ComputerCraft: failed to decode or handle server-bound packet", var3);
        }
    }
}
