package dan200.computercraft.core.filesystem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Shared byte-oriented reading for stream and random-access text handles. */
abstract class AbstractReadHandle implements IMountedFileNormal {

    protected abstract int readByte() throws IOException;

    protected abstract int readBytes(byte[] buffer) throws IOException;

    /** Put back the single byte just read during CR/LF lookahead. */
    protected abstract void unreadByte(int value) throws IOException;

    @Override
    public byte[] readLine() throws IOException {
        return readLine(false);
    }

    @Override
    public byte[] readLine(boolean withTrailing) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
        int value;
        while ((value = readByte()) != -1) {
            if (value == '\r') {
                int next = readByte();
                if (next != '\n' && next != -1) unreadByte(next);
                if (withTrailing) buffer.write('\n');
                return buffer.toByteArray();
            } else if (value == '\n') {
                if (withTrailing) buffer.write('\n');
                return buffer.toByteArray();
            }
            buffer.write(value);
        }
        return buffer.size() > 0 ? buffer.toByteArray() : null;
    }

    @Override
    public byte[] read(int count) throws IOException {
        if (count <= 0) return new byte[0];
        byte[] buffer = new byte[count];
        int read = readBytes(buffer);
        return read == -1 ? null : read == count ? buffer : Arrays.copyOf(buffer, read);
    }

    @Override
    public byte[] readAll() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
        byte[] data = new byte[1024];
        int read;
        while ((read = readBytes(data)) != -1) buffer.write(data, 0, read);
        return buffer.toByteArray();
    }

    @Override
    public void write(byte[] data, int start, int length, boolean newLine) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void flush() throws IOException {
        throw new UnsupportedOperationException();
    }
}
