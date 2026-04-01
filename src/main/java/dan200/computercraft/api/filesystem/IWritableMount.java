package dan200.computercraft.api.filesystem;

import java.io.IOException;
import java.io.OutputStream;

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
}
