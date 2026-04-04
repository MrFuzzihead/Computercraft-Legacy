package dan200.computercraft.api.filesystem;

import java.io.IOException;
import java.io.InputStream;
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
}
