package dan200.computercraft.shared.computer.upload;

/**
 * The outcome of an upload, reported back to the client that sent it.
 *
 * <p>
 * Ported from CC: Tweaked's {@code dan200.computercraft.shared.computer.upload.UploadResult}
 * (MPL-2.0; see NOTICE). Upstream drives GUI toasts from these; the 1.7.10 client only logs them and,
 * for {@link #ERROR}, surfaces the reason in chat.
 * </p>
 */
public enum UploadResult {

    /** The files were accepted and the {@code file_transfer} event has been queued. */
    QUEUED(0),
    /** The computer called {@code getFiles()}, so the upload really arrived. */
    CONSUMED(1),
    /** The target computer is not running, so an event would be lost. */
    COMPUTER_OFF(2),
    /** The upload was rejected; the message says why. */
    ERROR(3);

    private final int id;

    UploadResult(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }

    public static UploadResult byId(int id) {
        for (UploadResult result : values()) {
            if (result.id == id) {
                return result;
            }
        }

        return ERROR;
    }
}
