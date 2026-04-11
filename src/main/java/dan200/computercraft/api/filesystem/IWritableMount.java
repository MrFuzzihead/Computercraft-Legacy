package dan200.computercraft.api.filesystem;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

public interface IWritableMount extends IMount {

    void makeDirectory(String var1) throws IOException;

    void delete(String var1) throws IOException;

    OutputStream openForWrite(String var1) throws IOException;

    OutputStream openForAppend(String var1) throws IOException;

    long getRemainingSpace() throws IOException;

    /**
     * Returns the total capacity of this mount in bytes, or {@code -1} if
     * the mount has no enforced capacity limit (e.g. the computer's ROM).
     */
    default long getCapacity() throws IOException {
        return -1L;
    }

    /**
     * Open the file at {@code path} for simultaneous reading and writing.
     *
     * <p>When {@code truncate} is {@code true} (i.e. {@code "w+"} mode) the file is created if it
     * does not exist, and its contents are discarded if it does. When {@code truncate} is
     * {@code false} (i.e. {@code "r+"} mode) the file must already exist; an {@link IOException}
     * is thrown if it does not.</p>
     *
     * <p>The default implementation always throws {@link IOException} — mounts that do not back
     * physical files on disk are not required to support this mode.</p>
     *
     * @param path     the mount-relative path to open
     * @param truncate {@code true} to create/truncate ({@code "w+"}), {@code false} to require an
     *                 existing file ({@code "r+"})
     * @return an open {@link RandomAccessFile} positioned at byte 0
     * @throws IOException if the operation is unsupported or an I/O error occurs
     */
    default RandomAccessFile openForReadWrite(String path, boolean truncate) throws IOException {
        throw new IOException("Read-write mode not supported by this mount");
    }
}
