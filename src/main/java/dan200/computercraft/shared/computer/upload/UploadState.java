package dan200.computercraft.shared.computer.upload;

import java.util.ArrayList;
import java.util.List;

/**
 * The server side of one in-flight upload: the files announced by the first message, into which the
 * following messages' slices are copied.
 *
 * <p>
 * The equivalent of CC: Tweaked's {@code ServerInputState} upload fields and
 * {@code UploadFileMessage#send} contract (MPL-2.0; see NOTICE), factored out so it can be driven
 * with plain Java rather than a real player — every rejection path here is reachable from a hostile
 * packet, and all of them are pinned by tests.
 * </p>
 */
public class UploadState {

    /** The most files a single upload may carry. Matches upstream's {@code UploadFileMessage.MAX_FILES}. */
    public static final int MAX_FILES = 32;

    /** The longest file name, in encoded bytes. Matches upstream's {@code MAX_FILE_NAME}. */
    public static final int MAX_FILE_NAME = 128;

    private final int uploadID;
    private final FileUpload[] files;

    public UploadState(int uploadID, List<FileUpload> files) {
        this.uploadID = uploadID;
        this.files = files.toArray(new FileUpload[0]);
    }

    public int getUploadID() {
        return this.uploadID;
    }

    public FileUpload[] getFiles() {
        return this.files;
    }

    /**
     * Whether a continuation message belongs to this upload. A message that does not is dropped
     * rather than applied to some other player's upload.
     */
    public boolean matches(int uploadID) {
        return this.uploadID == uploadID;
    }

    /**
     * Copies the slices of one message into the files they belong to.
     *
     * @return {@code false} if a slice did not fit — the file's declared length was a lie — in which
     *         case the whole upload is discarded.
     */
    public boolean applySlices(List<FileSlice> slices) {
        for (FileSlice slice : slices) {
            if (!slice.apply(this.files)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Completes the upload.
     *
     * @return {@code null} if every file arrived intact, otherwise the reason the upload failed.
     */
    public String finish() {
        for (FileUpload file : this.files) {
            if (!file.checksumMatches()) {
                return "Checksum failed to match for " + file.getName();
            }
        }

        return null;
    }

    /** Builds the Lua-visible files of a completed upload. */
    public List<dan200.computercraft.core.apis.transfer.TransferredFile> toTransferredFiles() {
        List<dan200.computercraft.core.apis.transfer.TransferredFile> transferred = new ArrayList<>(this.files.length);
        for (FileUpload file : this.files) {
            transferred
                .add(new dan200.computercraft.core.apis.transfer.TransferredFile(file.getName(), file.getBytes()));
        }

        return transferred;
    }

    /**
     * The total number of bytes announced by an upload, used to enforce the configured limit before
     * anything is allocated.
     */
    public static int totalSize(List<FileUpload> files) {
        int total = 0;
        for (FileUpload file : files) {
            total += file.getLength();
        }

        return total;
    }
}
