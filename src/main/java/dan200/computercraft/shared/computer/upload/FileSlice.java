package dan200.computercraft.shared.computer.upload;

/**
 * One contiguous run of bytes belonging to one of the files in an upload.
 *
 * <p>
 * Ported from CC: Tweaked's {@code dan200.computercraft.shared.computer.upload.FileSlice}
 * (MPL-2.0; see NOTICE): a single upload is split across several messages so that no one message
 * carries the whole payload.
 * </p>
 */
public class FileSlice {

    private final int fileId;
    private final int offset;
    private final byte[] bytes;

    public FileSlice(int fileId, int offset, byte[] bytes) {
        this.fileId = fileId;
        this.offset = offset;
        this.bytes = bytes;
    }

    public int getFileId() {
        return this.fileId;
    }

    public int getOffset() {
        return this.offset;
    }

    public byte[] getBytes() {
        return this.bytes;
    }

    /**
     * Copies this slice into the file it belongs to.
     *
     * @param uploads The files of the upload being assembled.
     * @return {@code false} if the slice does not fit its file, which means the packet lied about
     *         the file's length and must be rejected rather than allowed to throw out of the netty
     *         thread.
     */
    public boolean apply(FileUpload[] uploads) {
        if (this.fileId < 0 || this.fileId >= uploads.length) {
            return false;
        }

        byte[] target = uploads[this.fileId].getBytes();
        if (this.offset < 0 || this.bytes.length > target.length - this.offset) {
            return false;
        }

        System.arraycopy(this.bytes, 0, target, this.offset, this.bytes.length);
        return true;
    }
}
