package dan200.computercraft.core.filesystem;

import java.io.IOException;

/**
 * Extension of {@link IMountedFileNormal} that adds random-access seeking, used by
 * {@code fs.open} when the mode is {@code "r+"} or {@code "w+"}.
 */
public interface IMountedFileReadWrite extends IMountedFileNormal {

    /**
     * Seek to a position in the file.
     *
     * @param whence one of {@code "set"} (from beginning), {@code "cur"} (from current position),
     *               {@code "end"} (from end of file)
     * @param offset byte offset relative to {@code whence}
     * @return the new absolute byte position in the file
     * @throws IOException on I/O error or invalid {@code whence} value
     */
    long seek(String whence, long offset) throws IOException;
}

