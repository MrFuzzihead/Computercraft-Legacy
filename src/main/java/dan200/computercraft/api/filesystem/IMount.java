package dan200.computercraft.api.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.List;

public interface IMount {

    boolean exists(String var1) throws IOException;

    boolean isDirectory(String var1) throws IOException;

    void list(String var1, List<String> var2) throws IOException;

    long getSize(String var1) throws IOException;

    InputStream openForRead(String var1) throws IOException;

    /**
     * Returns the creation time of the file or directory at {@code path} in
     * milliseconds since the Unix epoch, or {@code 0} if unavailable.
     */
    default long getCreationTime(String path) throws IOException {
        return 0L;
    }

    /**
     * Returns the last-modification time of the file or directory at
     * {@code path} in milliseconds since the Unix epoch, or {@code 0} if
     * unavailable.
     */
    default long getLastModified(String path) throws IOException {
        return 0L;
    }

    /**
     * Open the file at {@code path} for random-access (seekable) reading.
     *
     * <p>
     * The default implementation always throws {@link IOException} — mounts backed by
     * resources that cannot be memory-mapped or file-mapped (e.g. JARs) are not required
     * to support this mode. Callers should fall back to {@link #openForRead(String)} when
     * this throws.
     * </p>
     *
     * @param path the mount-relative path to open
     * @return an open {@link RandomAccessFile} positioned at byte 0, in read-only mode
     * @throws IOException if the operation is unsupported or an I/O error occurs
     */
    default RandomAccessFile openForReadRandom(String path) throws IOException {
        throw new IOException("seek not supported by this mount");
    }
}
