package dan200.computercraft.core.filesystem;

import java.io.IOException;

public interface IMountedFileNormal extends IMountedFile {

    byte[] readLine() throws IOException;

    void write(byte[] data, int start, int length, boolean newLine) throws IOException;

    @Override
    void close() throws IOException;

    void flush() throws IOException;

    byte[] readAll() throws IOException;
}
