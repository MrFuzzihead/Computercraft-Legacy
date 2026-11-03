package dan200.computercraft.shared.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.NetHandlerPlayServer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientCustomPacketEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ServerCustomPacketEvent;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.computer.upload.ServerFileUploadHandler;
import io.netty.buffer.ByteBuf;

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
            EntityPlayer player = ((NetHandlerPlayServer) event.handler).playerEntity;
            ByteBuf payload = event.packet.payload();

            // Uploads have their own codec (the generic packet's payload cannot express a file list
            // plus slices), so they are routed by their type byte before decoding.
            if (payload != null && payload.readableBytes() > 0
                && payload.getByte(payload.readerIndex()) == ComputerCraftPacket.FileUpload) {
                ServerFileUploadHandler.handle(player, payload);
                return;
            }

            ComputerCraftPacket packet = new ComputerCraftPacket();
            packet.fromBytes(payload);
            ComputerCraft.handlePacket(packet, player);
        } catch (Exception var3) {
            ComputerCraft.logger.error("ComputerCraft: failed to decode or handle server-bound packet", var3);
        }
    }
}
