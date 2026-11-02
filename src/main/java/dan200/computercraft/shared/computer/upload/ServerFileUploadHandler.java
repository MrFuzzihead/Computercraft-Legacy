package dan200.computercraft.shared.computer.upload;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.core.apis.transfer.TransferredFiles;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.computer.inventory.IContainerComputer;
import dan200.computercraft.shared.network.ComputerCraftPacket;
import dan200.computercraft.shared.network.FileUploadPacket;
import io.netty.buffer.ByteBuf;

/**
 * Receives file uploads on the server and turns them into a {@code file_transfer} event.
 *
 * <p>
 * The counterpart of CC: Tweaked's {@code ServerInputState} upload handling (MPL-2.0; see NOTICE):
 * the first message of an upload is held until the last one arrives, the checksums are verified, and
 * the files are queued on the target computer as one event.
 * </p>
 */
public final class ServerFileUploadHandler {

    /**
     * Uploads in flight, keyed by the player sending them. A weak map, so an upload abandoned by a
     * player who disconnects cannot outlive them.
     */
    private static final Map<EntityPlayer, UploadState> pending = Collections
        .synchronizedMap(new WeakHashMap<EntityPlayer, UploadState>());

    private ServerFileUploadHandler() {}

    /**
     * Handles one upload message from a client.
     *
     * @param player  The sending player, as resolved by the network handler.
     * @param payload The message, positioned at its type byte.
     */
    public static void handle(EntityPlayer player, ByteBuf payload) {
        if (player == null) {
            return;
        }

        FileUploadPacket packet = FileUploadPacket.decode(payload, ComputerCraft.upload_size_limit);

        if (packet.isFirst()) {
            handleFirst(player, packet);
        } else {
            handleContinue(player, packet);
        }
    }

    private static void handleFirst(EntityPlayer player, FileUploadPacket packet) {
        if (ComputerCraft.upload_size_limit <= 0) {
            // Documented as "0 disables uploads"; the client honours it too, but a modified one might
            // not, and nothing should be queued in that case.
            pending.remove(player);
            return;
        }

        int instanceID = packet.getInstanceID();
        if (!canUpload(player, instanceID)) {
            // Not the player's computer, or not even open. Do not even tell the client why: it may be
            // probing for computers it cannot see.
            ComputerCraft.logger.debug(
                "Refusing file upload from {} to computer {}: not open for that player",
                player.getCommandSenderName(),
                instanceID);
            pending.remove(player);
            return;
        }

        ServerComputer computer = ComputerCraft.serverComputerRegistry.get(instanceID);
        if (computer == null) {
            pending.remove(player);
            return;
        }

        if (!computer.isOn()) {
            pending.remove(player);
            reject(player, instanceID, UploadResult.COMPUTER_OFF, "Computer is off");
            return;
        }

        UploadState upload = new UploadState(packet.getUploadID(), packet.getFiles());
        if (!upload.applySlices(packet.getSlices())) {
            pending.remove(player);
            reject(player, instanceID, UploadResult.ERROR, "Malformed upload");
            return;
        }

        pending.put(player, upload);

        if (packet.isLast()) {
            finish(player, instanceID, upload);
        }
    }

    private static void handleContinue(EntityPlayer player, FileUploadPacket packet) {
        UploadState upload = pending.get(player);
        if (upload == null || !upload.matches(packet.getUploadID())) {
            // A continuation with no matching first message, or one from another upload.
            return;
        }

        if (!upload.applySlices(packet.getSlices())) {
            pending.remove(player);
            reject(player, packet.getInstanceID(), UploadResult.ERROR, "Malformed upload");
            return;
        }

        if (packet.isLast()) {
            finish(player, packet.getInstanceID(), upload);
        }
    }

    private static void finish(EntityPlayer player, int instanceID, UploadState upload) {
        pending.remove(player);

        String error = upload.finish();
        if (error != null) {
            reject(player, instanceID, UploadResult.ERROR, error);
            return;
        }

        ServerComputer computer = ComputerCraft.serverComputerRegistry.get(instanceID);
        if (computer == null || !computer.isOn()) {
            reject(player, instanceID, UploadResult.COMPUTER_OFF, "Computer is off");
            return;
        }

        final String playerName = player.getCommandSenderName();
        TransferredFiles files = new TransferredFiles(upload.toTransferredFiles(), () -> {
            // Runs on the computer thread, possibly long after the upload, when the player may have
            // logged out: the ack is best-effort, so a failure must not surface as a task error.
            try {
                sendResult(player, instanceID, UploadResult.CONSUMED, playerName + " transferred files");
            } catch (RuntimeException e) {
                ComputerCraft.logger.debug("Could not acknowledge a file upload", e);
            }
        });

        computer.queueEvent(TransferredFiles.EVENT, new Object[] { files });
        sendResult(player, instanceID, UploadResult.QUEUED, null);
    }

    /**
     * Whether this upload may target this computer: the sender must have that computer open, which is
     * the same rule upstream's container-based routing enforces.
     */
    static boolean canUpload(EntityPlayer player, int instanceID) {
        if (instanceID < 0) {
            return false;
        }

        Container container = player.openContainer;
        return container instanceof IContainerComputer
            && ((IContainerComputer) container).getInstanceID() == instanceID;
    }

    private static void reject(EntityPlayer player, int instanceID, UploadResult result, String message) {
        ComputerCraft.logger.info("Refusing file upload to computer {}: {}", instanceID, message);
        sendResult(player, instanceID, result, message);
    }

    private static void sendResult(EntityPlayer player, int instanceID, UploadResult result, String message) {
        ComputerCraftPacket packet = new ComputerCraftPacket();
        packet.m_packetType = ComputerCraftPacket.FileUploadResult;
        packet.m_dataInt = new int[] { instanceID, result.getId() };
        packet.m_dataString = new String[] { message };
        ComputerCraft.sendToPlayer(player, packet);
    }
}
