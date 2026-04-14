package dan200.computercraft.core.filesystem;

import java.io.IOException;

public interface IMountedFileNormal extends IMountedFile {

    byte[] readLine() throws IOException;

    /**
     * Read one line. When {@code withTrailing} is {@code true} the terminating {@code \n} byte is
     * included in the returned array (i.e. {@code \r\n} is normalised to {@code \n}).
     * The default implementation ignores the flag and delegates to {@link #readLine()} for
     * backward compatibility with stream-backed handles that do not support seek.
     */
    default byte[] readLine(boolean withTrailing) throws IOException {
        return readLine();
    }

    /**
     * Read up to {@code count} raw bytes from the current position.
     *
     * @param count maximum number of bytes to read; must be &gt;= 1
     * @return the bytes read, or {@code null} at end-of-file
     * @throws IOException on I/O error
     */
    default byte[] read(int count) throws IOException {
        throw new IOException("read not supported by this handle");
    }

    /**
     * Seek to a position in the file.
     *
     * <p>
     * The default implementation always throws so that stream-backed handles that do not
     * support seek return {@code nil, msg} to Lua rather than crashing.
     * </p>
     *
     * @param whence one of {@code "set"} (from beginning), {@code "cur"} (from current position),
     *               {@code "end"} (from end of file)
     * @param offset byte offset relative to {@code whence}
     * @return the new absolute byte position in the file
     * @throws IOException always, unless overridden
     */
    default long seek(String whence, long offset) throws IOException {
        throw new IOException("seek not supported by this handle");
    }

    void write(byte[] data, int start, int length, boolean newLine) throws IOException;

    @Override
    void close() throws IOException;

    void flush() throws IOException;

    byte[] readAll() throws IOException;
}
