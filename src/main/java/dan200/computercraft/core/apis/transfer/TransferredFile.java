package dan200.computercraft.core.apis.transfer;

import java.io.ByteArrayOutputStream;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObject;
import dan200.computercraft.api.lua.LuaException;

/**
 * A file that a player dropped onto a computer, as received by the {@code file_transfer} event.
 *
 * <p>
 * This is a port of CC: Tweaked's {@code dan200.computercraft.core.apis.transfer.TransferredFile}
 * (MPL-2.0; see NOTICE). Upstream models it as a {@code ReadHandle} exposed through
 * {@code ObjectSource#getExtra}, which the 1.7.10 Lua bridge has no equivalent of, so the read
 * methods are declared directly on this object instead. The method set, ordering and behaviour are
 * otherwise the ones a {@code "rb"} file handle exposes: {@code read} with no argument returns the
 * next byte as a number, and with a count returns a string.
 * </p>
 */
public class TransferredFile implements ILuaObject {

    private static final String[] METHOD_NAMES = { "getName", "read", "readLine", "readAll", "close", "seek" };

    private final String name;
    private final byte[] contents;
    private int position;
    private boolean closed;

    public TransferredFile(String name, byte[] contents) {
        this.name = name;
        this.contents = contents;
    }

    /**
     * Returns the name of this file being transferred.
     *
     * @return The file's name.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Returns the bytes behind this file. Used by callers that want to write the contents out without
     * going through the Lua bridge.
     */
    public byte[] getContents() {
        return this.contents;
    }

    @Override
    public String[] getMethodNames() {
        return METHOD_NAMES;
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
        switch (method) {
            case 0: // getName
                return new Object[] { this.name };
            case 1: // read([count])
                return this.read(args);
            case 2: // readLine([withTrailing])
                return this.readLine(args);
            case 3: // readAll
                this.checkOpen();
                return new Object[] { this.readBytes(this.contents.length - this.position) };
            case 4: // close
                this.checkOpen();
                this.closed = true;
                return null;
            case 5: // seek([whence[, offset]])
                return this.seek(args);
            default:
                return null;
        }
    }

    private Object[] read(Object[] args) throws LuaException {
        this.checkOpen();

        if (args.length == 0 || args[0] == null) {
            // Binary handle: a bare read() returns the next byte as a number, not a one-char string.
            if (this.position >= this.contents.length) {
                return null;
            }

            return new Object[] { this.contents[this.position++] & 0xFF };
        }

        int count;
        if (args[0] instanceof Number number) {
            double value = number.doubleValue();
            if (value != Math.floor(value) || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
                throw new LuaException("bad argument #1 to 'read' (number has no integer representation)");
            }

            count = number.intValue();
        } else {
            throw new LuaException("bad argument #1 to 'read' (number expected, got " + typeName(args[0]) + ")");
        }

        if (count < 0) {
            throw new LuaException("Cannot read a negative number of bytes");
        }

        if (count == 0) {
            return this.position >= this.contents.length ? null : new Object[] { "" };
        }

        if (this.position >= this.contents.length) {
            return null;
        }

        return new Object[] { this.readBytes(Math.min(count, this.contents.length - this.position)) };
    }

    private Object[] readLine(Object[] args) throws LuaException {
        this.checkOpen();

        boolean withTrailing = false;
        if (args.length > 0 && args[0] != null) {
            if (!(args[0] instanceof Boolean)) {
                throw new LuaException(
                    "bad argument #1 to 'readLine' (boolean expected, got " + typeName(args[0]) + ")");
            }

            withTrailing = (Boolean) args[0];
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        boolean readAnything = false;
        boolean readRc = false;
        while (this.position < this.contents.length) {
            byte chr = this.contents[this.position++];
            readAnything = true;

            if (chr == '\n') {
                if (withTrailing) {
                    if (readRc) {
                        stream.write('\r');
                    }

                    stream.write(chr);
                }

                return new Object[] { stream.toByteArray() };
            }

            // Skip \r\n, but keep a \r that is not followed by \n. Upstream does the same, deliberately
            // non-standard, to match the old encodings of the io library.
            if (readRc) {
                stream.write('\r');
            }

            readRc = chr == '\r';
            if (!readRc) {
                stream.write(chr);
            }
        }

        if (readRc) {
            stream.write('\r');
        }

        return readAnything ? new Object[] { stream.toByteArray() } : null;
    }

    private Object[] seek(Object[] args) throws LuaException {
        this.checkOpen();

        String whence = "cur";
        if (args.length > 0 && args[0] != null) {
            if (!(args[0] instanceof String)) {
                throw new LuaException("bad argument #1 to 'seek' (string expected, got " + typeName(args[0]) + ")");
            }

            whence = (String) args[0];
        }

        long offset = 0L;
        if (args.length > 1 && args[1] != null) {
            if (!(args[1] instanceof Number number)) {
                throw new LuaException("bad argument #2 to 'seek' (number expected, got " + typeName(args[1]) + ")");
            }

            offset = number.longValue();
        }

        long newPosition;
        switch (whence) {
            case "set":
                newPosition = offset;
                break;
            case "cur":
                newPosition = this.position + offset;
                break;
            case "end":
                newPosition = this.contents.length + offset;
                break;
            default:
                throw new LuaException("bad argument #1 to 'seek' (invalid option '" + whence + "')");
        }

        if (newPosition < 0) {
            return new Object[] { null, "Position is negative" };
        }

        if (newPosition > Integer.MAX_VALUE) {
            // Files live in a byte[], so they cannot exceed this anyway; say so rather than claiming
            // the position is negative.
            return new Object[] { null, "Position is too large" };
        }

        this.position = (int) newPosition;
        return new Object[] { (long) this.position };
    }

    private byte[] readBytes(int count) {
        byte[] result = new byte[count];
        System.arraycopy(this.contents, this.position, result, 0, count);
        this.position += count;
        return result;
    }

    private void checkOpen() throws LuaException {
        if (this.closed) {
            throw new LuaException("attempt to use a closed file");
        }
    }

    private static String typeName(Object value) {
        if (value instanceof String) {
            return "string";
        } else if (value instanceof Boolean) {
            return "boolean";
        } else if (value instanceof Number) {
            return "number";
        }

        return "nil";
    }
}
