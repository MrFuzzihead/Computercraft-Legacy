package dan200.computercraft.core.apis;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObject;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.filesystem.FileSystemException;
import dan200.computercraft.core.filesystem.IMountedFileBinary;
import dan200.computercraft.core.filesystem.IMountedFileNormal;
import dan200.computercraft.core.filesystem.IMountedFileReadWrite;
import dan200.computercraft.core.lua.binfs.ReaderObject;
import dan200.computercraft.core.lua.binfs.WriterObject;
import dan200.computercraft.core.lua.lib.BinaryConverter;

public class FSAPI implements ILuaAPI {

    private IAPIEnvironment m_env;
    private FileSystem m_fileSystem;

    public FSAPI(IAPIEnvironment _env) {
        this.m_env = _env;
        this.m_fileSystem = null;
    }

    @Override
    public String[] getNames() {
        return new String[] { "fs" };
    }

    @Override
    public void startup() {
        this.m_fileSystem = this.m_env.getFileSystem();
    }

    @Override
    public void advance(double _dt) {}

    @Override
    public void shutdown() {
        this.m_fileSystem = null;
    }

    @Override
    public String[] getMethodNames() {
        return new String[] { "list", "combine", "getName", "getSize", "exists", "isDir", "isReadOnly", "makeDir",
            "move", "copy", "delete", "open", "getDrive", "getFreeSpace", "find", "getDir", "getCapacity", "attributes",
            "isDriveRoot" };
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
        switch (method) {
            case 0:
                if (args.length == 1 && args[0] != null && args[0] instanceof String) {
                    String path = (String) args[0];

                    try {
                        String[] results = this.m_fileSystem.list(path);
                        Map<Object, Object> table = new HashMap<>();

                        for (int i = 0; i < results.length; i++) {
                            table.put(i + 1, results[i]);
                        }

                        return new Object[] { table };
                    } catch (FileSystemException var20) {
                        throw new LuaException(var20.getMessage());
                    }
                } else {
                    throw new LuaException("Expected string");
                }
            case 1:
                if (args.length >= 2 && args[0] instanceof String) {
                    String acc = (String) args[0];
                    for (int i = 1; i < args.length; i++) {
                        if (!(args[i] instanceof String)) {
                            throw new LuaException("Expected string");
                        }
                        acc = this.m_fileSystem.combine(acc, (String) args[i]);
                    }
                    return new Object[] { acc };
                }

                throw new LuaException("Expected string, string");
            case 2:
                if (args.length == 1 && args[0] != null && args[0] instanceof String) {
                    String path = (String) args[0];
                    return new Object[] { FileSystem.getName(path) };
                }

                throw new LuaException("Expected string");
            case 3:
                if (args.length == 1 && args[0] != null && args[0] instanceof String) {
                    String path = (String) args[0];

                    try {
                        return new Object[] { this.m_fileSystem.getSize(path) };
                    } catch (FileSystemException var18) {
                        throw new LuaException(var18.getMessage());
                    }
                }

                throw new LuaException("Expected string");
            case 4:
                if (args.length == 1 && args[0] != null && args[0] instanceof String) {
                    String path = (String) args[0];

                    try {
                        return new Object[] { this.m_fileSystem.exists(path) };
                    } catch (FileSystemException var17) {
                        return new Object[] { false };
                    }
                }

                throw new LuaException("Expected string");
            case 5:
                if (args.length == 1 && args[0] != null && args[0] instanceof String) {
                    String path = (String) args[0];

                    try {
                        return new Object[] { this.m_fileSystem.isDir(path) };
                    } catch (FileSystemException var16) {
                        return new Object[] { false };
                    }
                }

                throw new LuaException("Expected string");
            case 6:
                if (args.length == 1 && args[0] != null && args[0] instanceof String) {
                    String path = (String) args[0];

                    try {
                        return new Object[] { this.m_fileSystem.isReadOnly(path) };
                    } catch (FileSystemException var15) {
                        return new Object[] { false };
                    }
                }

                throw new LuaException("Expected string");
            case 7:
                if (args.length == 1 && args[0] != null && args[0] instanceof String) {
                    String path = (String) args[0];

                    try {
                        this.m_fileSystem.makeDir(path);
                        return null;
                    } catch (FileSystemException var14) {
                        throw new LuaException(var14.getMessage());
                    }
                }

                throw new LuaException("Expected string");
            case 8:
                if (args.length == 2 && args[0] != null
                    && args[0] instanceof String
                    && args[1] != null
                    && args[1] instanceof String) {
                    String path = (String) args[0];
                    String dest = (String) args[1];

                    try {
                        this.m_fileSystem.move(path, dest);
                        return null;
                    } catch (FileSystemException var13) {
                        throw new LuaException(var13.getMessage());
                    }
                }

                throw new LuaException("Expected string, string");
            case 9:
                if (args.length == 2 && args[0] != null
                    && args[0] instanceof String
                    && args[1] != null
                    && args[1] instanceof String) {
                    String path = (String) args[0];
                    String dest = (String) args[1];

                    try {
                        this.m_fileSystem.copy(path, dest);
                        return null;
                    } catch (FileSystemException var12) {
                        throw new LuaException(var12.getMessage());
                    }
                }

                throw new LuaException("Expected string, string");
            case 10:
                if (args.length == 1 && args[0] != null && args[0] instanceof String) {
                    String path = (String) args[0];

                    try {
                        this.m_fileSystem.delete(path);
                        return null;
                    } catch (FileSystemException var11) {
                        throw new LuaException(var11.getMessage());
                    }
                }

                throw new LuaException("Expected string");
            case 11:
                if (args.length == 2 && args[0] != null
                    && args[0] instanceof String
                    && args[1] != null
                    && args[1] instanceof String) {
                    String path = (String) args[0];
                    String mode = (String) args[1];

                    try {
                        return openFile(path, mode);
                    } catch (FileSystemException e) {
                        // Every mode shares one error contract: nil, message. A mount that
                        // reports no message (a null IOException detail) still gets a diagnostic.
                        String message = e.getMessage();
                        return errorResult(message != null ? message : "Could not open file");
                    }
                }

                throw new LuaException("Expected string, string");
            case 12:
                if (args.length == 1 && args[0] != null && args[0] instanceof String) {
                    String path = (String) args[0];

                    try {
                        if (!this.m_fileSystem.exists(path)) {
                            return null;
                        }

                        return new Object[] { this.m_fileSystem.getMountLabel(path) };
                    } catch (FileSystemException var9) {
                        throw new LuaException(var9.getMessage());
                    }
                }

                throw new LuaException("Expected string");
            case 13:
                if (args.length == 1 && args[0] != null && args[0] instanceof String) {
                    String path = (String) args[0];

                    try {
                        long freeSpace = this.m_fileSystem.getFreeSpace(path);
                        if (freeSpace >= 0L) {
                            return new Object[] { freeSpace };
                        }

                        return new Object[] { "unlimited" };
                    } catch (FileSystemException var8) {
                        throw new LuaException(var8.getMessage());
                    }
                }

                throw new LuaException("Expected string");
            case 14:
                if (args.length == 1 && args[0] != null && args[0] instanceof String) {
                    String path = (String) args[0];

                    try {
                        String[] results = this.m_fileSystem.find(path);
                        Map<Object, Object> table = new HashMap<>();

                        for (int i = 0; i < results.length; i++) {
                            table.put(i + 1, results[i]);
                        }

                        return new Object[] { table };
                    } catch (FileSystemException var19) {
                        throw new LuaException(var19.getMessage());
                    }
                }

                throw new LuaException("Expected string");
            case 15:
                if (args.length == 1 && args[0] != null && args[0] instanceof String) {
                    String path = (String) args[0];
                    return new Object[] { FileSystem.getDirectory(path) };
                }

                throw new LuaException("Expected string");
            case 16:
                if (args.length == 1 && args[0] != null && args[0] instanceof String path) {

                    try {
                        long capacity = this.m_fileSystem.getCapacity(path);
                        if (capacity >= 0L) {
                            return new Object[] { capacity };
                        }

                        return new Object[] { null };
                    } catch (FileSystemException e) {
                        throw new LuaException(e.getMessage());
                    }
                }

                throw new LuaException("Expected string");
            case 17:
                if (args.length == 1 && args[0] != null && args[0] instanceof String path) {

                    try {
                        Map<String, Object> attrs = this.m_fileSystem.getAttributes(path);
                        return new Object[] { attrs };
                    } catch (FileSystemException e) {
                        throw new LuaException(e.getMessage());
                    }
                }

                throw new LuaException("Expected string");
            case 18:
                if (args.length == 1 && args[0] != null && args[0] instanceof String path) {
                    return new Object[] { this.m_fileSystem.isDriveRoot(path) };
                }

                throw new LuaException("Expected string");
            default:
                assert false;

                return null;
        }
    }

    /**
     * Open a file and wrap it in the Lua handle for the requested mode.
     *
     * <p>
     * All eight modes share one contract, matching CC:Tweaked: on success {@code fs.open}
     * returns just the handle, on failure it returns {@code nil} followed by the reason.
     * A mount that throws (denied access, missing file, path is a directory) and a mount
     * that hands back a {@code null} stream are reported the same way — the latter used to
     * produce a bare {@code nil}, or even a handle wrapped around {@code null}.
     * </p>
     *
     * <p>
     * An unsupported mode is a programming error rather than an I/O failure, so it raises a
     * Lua error as elsewhere in this API.
     * </p>
     *
     * @param path the path to open
     * @param mode one of {@code r}, {@code w}, {@code a}, {@code rb}, {@code wb}, {@code ab},
     *             {@code r+} or {@code w+}
     * @return the Lua return values of {@code fs.open}: a file handle, or {@code nil, message}
     * @throws FileSystemException if the file could not be opened
     * @throws LuaException        if {@code mode} is not supported
     */
    private Object[] openFile(String path, String mode) throws FileSystemException, LuaException {
        switch (mode) {
            case "r": {
                IMountedFileNormal reader = this.m_fileSystem.openForReadSeekable(path);
                return reader != null ? wrapBufferedReader(reader) : errorResult("No such file");
            }
            case "w": {
                IMountedFileNormal writer = this.m_fileSystem.openForWriteSeekable(path, false);
                return writer != null ? wrapBufferedWriter(writer) : errorResult("Failed to open file");
            }
            case "a": {
                IMountedFileNormal writer = this.m_fileSystem.openForWriteSeekable(path, true);
                return writer != null ? wrapBufferedWriter(writer) : errorResult("Failed to open file");
            }
            case "rb": {
                IMountedFileBinary reader = this.m_fileSystem.openForBinaryRead(path);
                return reader != null ? wrapInputStream(reader) : errorResult("No such file");
            }
            case "wb": {
                IMountedFileBinary writer = this.m_fileSystem.openForBinaryWrite(path, false);
                return writer != null ? wrapOutputStream(writer) : errorResult("Failed to open file");
            }
            case "ab": {
                IMountedFileBinary writer = this.m_fileSystem.openForBinaryWrite(path, true);
                return writer != null ? wrapOutputStream(writer) : errorResult("Failed to open file");
            }
            case "r+": {
                IMountedFileReadWrite rwFile = this.m_fileSystem.openForReadWrite(path, false);
                return rwFile != null ? wrapReadWrite(rwFile) : errorResult("No such file");
            }
            case "w+": {
                IMountedFileReadWrite rwFile = this.m_fileSystem.openForReadWrite(path, true);
                return rwFile != null ? wrapReadWrite(rwFile) : errorResult("Failed to open file");
            }
            default:
                throw new LuaException("Unsupported mode");
        }
    }

    /** The {@code nil, message} return value shared by every {@code fs.open} failure path. */
    private static Object[] errorResult(String message) {
        return new Object[] { null, message };
    }

    private static Object[] wrapBufferedReader(final IMountedFileNormal reader) {
        return new Object[] { new ReaderObject(reader) };
    }

    private static Object[] wrapBufferedWriter(final IMountedFileNormal writer) {
        return new Object[] { new WriterObject(writer) };
    }

    private static Object[] wrapInputStream(final IMountedFileBinary reader) {
        return new Object[] { new ILuaObject() {

            @Override
            public String[] getMethodNames() {
                return new String[] { "read", "close" };
            }

            @Override
            public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
                switch (method) {
                    case 0:
                        try {
                            int b = reader.read();
                            if (b != -1) {
                                return new Object[] { b };
                            }

                            return null;
                        } catch (IOException var6) {
                            return null;
                        }
                    case 1:
                        try {
                            reader.close();
                            return null;
                        } catch (IOException var5) {
                            return null;
                        }
                    default:
                        assert false;

                        return null;
                }
            }
        } };
    }

    private static Object[] wrapOutputStream(final IMountedFileBinary writer) {
        return new Object[] { new ILuaObject() {

            @Override
            public String[] getMethodNames() {
                return new String[] { "write", "close", "flush" };
            }

            @Override
            public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
                switch (method) {
                    case 0:
                        try {
                            if (args.length > 0 && args[0] instanceof Number) {
                                int number = ((Number) args[0]).intValue();
                                writer.write(number);
                            }

                            return null;
                        } catch (IOException var7) {
                            throw new LuaException(var7.getMessage());
                        }
                    case 1:
                        try {
                            writer.close();
                            return null;
                        } catch (IOException var6) {
                            return null;
                        }
                    case 2:
                        try {
                            writer.flush();
                            return null;
                        } catch (IOException var5) {
                            return null;
                        }
                    default:
                        assert false;

                        return null;
                }
            }
        } };
    }

    private static Object[] wrapReadWrite(final IMountedFileReadWrite file) {
        return new Object[] { new ILuaObject() {

            @Override
            public String[] getMethodNames() {
                return new String[] { "read", "readLine", "readAll", "write", "seek", "flush", "close" };
            }

            @Override
            public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
                switch (method) {
                    case 0: // read
                        try {
                            int count = args.length > 0 && args[0] instanceof Number ? ((Number) args[0]).intValue()
                                : 1;
                            byte[] data = file.read(count);
                            return data != null ? new Object[] { data } : null;
                        } catch (IOException e) {
                            return null;
                        }
                    case 1: // readLine
                        try {
                            boolean withTrailing = args.length > 0 && args[0] instanceof Boolean && (Boolean) args[0];
                            byte[] line = file.readLine(withTrailing);
                            return line != null ? new Object[] { line } : null;
                        } catch (IOException e) {
                            return null;
                        }
                    case 2: // readAll
                        try {
                            byte[] all = file.readAll();
                            return all != null ? new Object[] { all } : null;
                        } catch (IOException e) {
                            return null;
                        }
                    case 3: // write
                        try {
                            if (args.length > 0 && args[0] != null) {
                                byte[] writeData = args[0] instanceof byte[] ? (byte[]) args[0]
                                    : BinaryConverter.toBytes(args[0].toString());
                                file.write(writeData, 0, writeData.length, false);
                            }
                            return null;
                        } catch (IOException e) {
                            throw new LuaException(e.getMessage());
                        }
                    case 4: // seek
                        try {
                            String whence = ReaderObject.extractWhence(args, 0, "cur");
                            long offset = args.length > 1 && args[1] instanceof Number ? ((Number) args[1]).longValue()
                                : 0L;
                            long pos = file.seek(whence, offset);
                            return new Object[] { pos };
                        } catch (IOException e) {
                            return new Object[] { null, e.getMessage() };
                        }
                    case 5: // flush
                        try {
                            file.flush();
                            return null;
                        } catch (IOException e) {
                            return null;
                        }
                    case 6: // close
                        try {
                            file.close();
                            return null;
                        } catch (IOException e) {
                            return null;
                        }
                    default:
                        assert false;
                        return null;
                }
            }
        } };
    }
}
