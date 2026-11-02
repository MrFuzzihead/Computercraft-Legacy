package dan200.computercraft.shared.computer.inventory;

/**
 * A container that is showing one particular computer, which is what authorises a file upload.
 *
 * <p>
 * CC: Tweaked routes uploads through the open {@code ComputerMenu}, so only a player actually looking
 * at a computer can put files on it. The legacy containers are plain {@code Container}s with no shared
 * base class, so they opt in here instead; {@code ServerFileUploadHandler} refuses an upload whose
 * target does not match the sender's open container.
 * </p>
 */
public interface IContainerComputer {

    /**
     * The instance id of the computer this container is showing, or {@code -1} if it is not known
     * (for example on the client, where the container is only used for slot layout).
     */
    int getInstanceID();
}
