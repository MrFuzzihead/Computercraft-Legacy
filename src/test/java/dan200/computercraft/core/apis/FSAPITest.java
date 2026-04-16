package dan200.computercraft.core.apis;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.lua.ILuaObject;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.IComputerEnvironment;
import dan200.computercraft.core.filesystem.FileMount;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.filesystem.FileSystemException;
import dan200.computercraft.core.terminal.Terminal;

/**
 * Unit tests for the {@code fs} API methods in {@link FSAPI}.
 *
 * <p>
 * Tests call {@link FSAPI#callMethod} directly with a {@code null}
 * {@link dan200.computercraft.api.lua.ILuaContext}; none of these methods schedule
 * work on the main thread, so this is safe.
 * </p>
 */
class FSAPITest {

    // Method indices must match the order declared in FSAPI.getMethodNames().
    private static final int METHOD_COMBINE = 1;
    private static final int METHOD_OPEN = 11;
    private static final int METHOD_FIND = 14;
    private static final int METHOD_GET_CAPACITY = 16;
    private static final int METHOD_ATTRIBUTES = 17;
    private static final int METHOD_IS_DRIVE_ROOT = 18;

    // ReaderObject method indices (Groups 7)
    private static final int READER_READ = 0;
    private static final int READER_READLINE = 1;
    private static final int READER_READALL = 2;
    private static final int READER_CLOSE = 3;
    private static final int READER_SEEK = 4;

    // WriterObject method indices (Group 8)
    private static final int WRITER_WRITE = 0;
    private static final int WRITER_CLOSE = 2;
    private static final int WRITER_SEEK = 4;

    // wrapReadWrite (r+/w+ modes) method indices (Group 9)
    private static final int RW_READ = 0;
    private static final int RW_READLINE = 1;
    private static final int RW_WRITE = 3;
    private static final int RW_SEEK = 4;
    private static final int RW_CLOSE = 6;

    @TempDir
    Path tempDir;

    private FSAPI api;
    private FileSystem fileSystem;

    @BeforeEach
    void setUp() throws FileSystemException {
        FileMount mount = new FileMount(tempDir.toFile(), 1024 * 1024L); // 1 MiB
        fileSystem = new FileSystem("hdd", mount);
        api = new FSAPI(new StubEnv(fileSystem));
        api.startup();
    }

    // =========================================================================
    // fs.getCapacity
    // =========================================================================

    @Test
    void getCapacityThrowsForMissingArg() {
        assertThrows(LuaException.class, () -> api.callMethod(null, METHOD_GET_CAPACITY, new Object[0]));
    }

    @Test
    void getCapacityThrowsForNonStringArg() {
        assertThrows(LuaException.class, () -> api.callMethod(null, METHOD_GET_CAPACITY, new Object[] { 42 }));
    }

    @Test
    void getCapacityReturnsLongForWritableMount() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_GET_CAPACITY, new Object[] { "" });

        assertNotNull(result);
        assertEquals(1, result.length);
        assertInstanceOf(Long.class, result[0]);
        assertTrue((Long) result[0] > 0L, "capacity should be positive");
    }

    @Test
    void getCapacityReturnsNullForReadOnlyMount() throws FileSystemException, LuaException {
        fileSystem.mount("rom", "rom", new ReadOnlyStubMount());

        Object[] result = api.callMethod(null, METHOD_GET_CAPACITY, new Object[] { "rom" });

        assertNotNull(result);
        assertEquals(1, result.length);
        assertNull(result[0], "read-only mount should report null capacity");
    }

    // =========================================================================
    // fs.attributes
    // =========================================================================

    @Test
    void attributesThrowsForMissingArg() {
        assertThrows(LuaException.class, () -> api.callMethod(null, METHOD_ATTRIBUTES, new Object[0]));
    }

    @Test
    void attributesThrowsForNonStringArg() {
        assertThrows(LuaException.class, () -> api.callMethod(null, METHOD_ATTRIBUTES, new Object[] { 42 }));
    }

    @Test
    void attributesThrowsForNonExistentPath() {
        assertThrows(
            LuaException.class,
            () -> api.callMethod(null, METHOD_ATTRIBUTES, new Object[] { "nonexistent.txt" }));
    }

    @Test
    void attributesForRootDirectory() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_ATTRIBUTES, new Object[] { "" });

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) result[0];
        assertEquals(Boolean.TRUE, attrs.get("isDir"));
        assertEquals(0L, attrs.get("size"));
        assertEquals(Boolean.FALSE, attrs.get("isReadOnly"));
        assertNotNull(attrs.get("created"));
        assertNotNull(attrs.get("modified"));
        assertNotNull(attrs.get("modification"), "modification alias should be present");
        assertEquals(attrs.get("modified"), attrs.get("modification"));
    }

    @Test
    void attributesForFile() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.write(file, "hello".getBytes(StandardCharsets.UTF_8));

        Object[] result = api.callMethod(null, METHOD_ATTRIBUTES, new Object[] { "test.txt" });

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) result[0];
        assertEquals(Boolean.FALSE, attrs.get("isDir"));
        assertEquals(5L, attrs.get("size"));
        assertEquals(Boolean.FALSE, attrs.get("isReadOnly"));
        assertTrue((Long) attrs.get("modified") > 0L, "modified timestamp should be positive");
        assertTrue((Long) attrs.get("created") > 0L, "created timestamp should be positive");
    }

    @Test
    void attributesIsReadOnlyForReadOnlyMount() throws FileSystemException, LuaException {
        fileSystem.mount("rom", "rom", new ReadOnlyStubMount());

        Object[] result = api.callMethod(null, METHOD_ATTRIBUTES, new Object[] { "rom" });

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) result[0];
        assertEquals(Boolean.TRUE, attrs.get("isDir"));
        assertEquals(Boolean.TRUE, attrs.get("isReadOnly"));
    }

    // =========================================================================
    // fs.isDriveRoot (Group 1)
    // =========================================================================

    @Test
    void isDriveRootThrowsForMissingArg() {
        assertThrows(LuaException.class, () -> api.callMethod(null, METHOD_IS_DRIVE_ROOT, new Object[0]));
    }

    @Test
    void isDriveRootRootIsAlwaysTrue() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_IS_DRIVE_ROOT, new Object[] { "" });
        assertNotNull(result);
        assertEquals(Boolean.TRUE, result[0], "root path should always be a drive root");
    }

    @Test
    void isDriveRootMountedPathIsTrue() throws FileSystemException, LuaException {
        fileSystem.mount("rom", "rom", new ReadOnlyStubMount());

        Object[] result = api.callMethod(null, METHOD_IS_DRIVE_ROOT, new Object[] { "rom" });
        assertNotNull(result);
        assertEquals(Boolean.TRUE, result[0], "mounted path should be a drive root");
    }

    @Test
    void isDriveRootNonMountedPathIsFalse() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_IS_DRIVE_ROOT, new Object[] { "nonexistent" });
        assertNotNull(result);
        assertEquals(Boolean.FALSE, result[0], "non-mounted path should not be a drive root");
    }

    // =========================================================================
    // fs.combine variadic (Group 1)
    // =========================================================================

    @Test
    void combineTwoArgs() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_COMBINE, new Object[] { "a", "b" });
        assertNotNull(result);
        assertEquals("a/b", result[0]);
    }

    @Test
    void combineThreeArgs() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_COMBINE, new Object[] { "a", "b", "c" });
        assertNotNull(result);
        assertEquals("a/b/c", result[0]);
    }

    @Test
    void combineFourArgs() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_COMBINE, new Object[] { "a", "b", "c", "d" });
        assertNotNull(result);
        assertEquals("a/b/c/d", result[0]);
    }

    @Test
    void combineThrowsForSingleArg() {
        assertThrows(LuaException.class, () -> api.callMethod(null, METHOD_COMBINE, new Object[] { "a" }));
    }

    @Test
    void combineThrowsIfNonStringInArgs() {
        assertThrows(LuaException.class, () -> api.callMethod(null, METHOD_COMBINE, new Object[] { "a", "b", 42 }));
    }

    // =========================================================================
    // fs.find with '?' wildcard (Group 2)
    // =========================================================================

    @Test
    void findWithQuestionMarkMatchesSingleChar() throws Exception {
        Files.write(tempDir.resolve("foo.txt"), new byte[0]);
        Files.write(tempDir.resolve("bar.txt"), new byte[0]);

        Object[] result = api.callMethod(null, METHOD_FIND, new Object[] { "f??.txt" });
        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<Object, Object> table = (Map<Object, Object>) result[0];
        assertEquals(1, table.size(), "only foo.txt should match f??.txt");
        assertTrue(
            table.values()
                .contains("foo.txt"),
            "foo.txt should be in results");
    }

    @Test
    void findWithQuestionMarkMatchesExactlyOneChar() throws Exception {
        Files.write(tempDir.resolve("a.txt"), new byte[0]);
        Files.write(tempDir.resolve("ab.txt"), new byte[0]);
        Files.write(tempDir.resolve("abc.txt"), new byte[0]);

        Object[] result = api.callMethod(null, METHOD_FIND, new Object[] { "?.txt" });
        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<Object, Object> table = (Map<Object, Object>) result[0];
        assertEquals(1, table.size(), "only a.txt should match ?.txt");
        assertTrue(
            table.values()
                .contains("a.txt"),
            "a.txt should be in results");
    }

    @Test
    void findWithQuestionMarkDoesNotMatchSeparator() throws Exception {
        Path subdir = tempDir.resolve("sub");
        Files.createDirectory(subdir);
        Files.write(subdir.resolve("file.txt"), new byte[0]);

        // '?' should not match '/' so "??b/file.txt" should NOT match "sub/file.txt" via '?' cross-dir
        Object[] result = api.callMethod(null, METHOD_FIND, new Object[] { "???file.txt" });
        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<Object, Object> table = (Map<Object, Object>) result[0];
        assertEquals(0, table.size(), "'?' must not match the path separator");
    }

    // =========================================================================
    // ReadHandle — read, readLine(withTrailing), seek (Groups 6, 7)
    // =========================================================================

    @Test
    void readHandleReadDefaultCountOneByte() throws Exception {
        Files.write(tempDir.resolve("data.txt"), "Hello".getBytes(StandardCharsets.UTF_8));

        Object[] handleResult = api.callMethod(null, METHOD_OPEN, new Object[] { "data.txt", "r" });
        assertNotNull(handleResult);
        ILuaObject handle = (ILuaObject) handleResult[0];

        Object[] readResult = handle.callMethod(null, READER_READ, new Object[0]);
        assertNotNull(readResult, "should return a byte at position 0");
        assertArrayEquals(new byte[] { 'H' }, (byte[]) readResult[0]);

        handle.callMethod(null, READER_CLOSE, new Object[0]);
    }

    @Test
    void readHandleReadMultipleBytes() throws Exception {
        Files.write(tempDir.resolve("data.txt"), "Hello".getBytes(StandardCharsets.UTF_8));

        Object[] handleResult = api.callMethod(null, METHOD_OPEN, new Object[] { "data.txt", "r" });
        ILuaObject handle = (ILuaObject) handleResult[0];

        Object[] readResult = handle.callMethod(null, READER_READ, new Object[] { 3 });
        assertNotNull(readResult);
        assertArrayEquals("Hel".getBytes(StandardCharsets.UTF_8), (byte[]) readResult[0]);

        handle.callMethod(null, READER_CLOSE, new Object[0]);
    }

    @Test
    void readHandleReadAtEofReturnsNull() throws Exception {
        Files.write(tempDir.resolve("data.txt"), new byte[0]); // empty file

        Object[] handleResult = api.callMethod(null, METHOD_OPEN, new Object[] { "data.txt", "r" });
        ILuaObject handle = (ILuaObject) handleResult[0];

        Object[] readResult = handle.callMethod(null, READER_READ, new Object[] { 1 });
        assertNull(readResult, "reading empty file should return nil");

        handle.callMethod(null, READER_CLOSE, new Object[0]);
    }

    @Test
    void readHandleReadLineStripsTrailingNewline() throws Exception {
        Files.write(tempDir.resolve("lines.txt"), "line1\nline2".getBytes(StandardCharsets.UTF_8));

        Object[] handleResult = api.callMethod(null, METHOD_OPEN, new Object[] { "lines.txt", "r" });
        ILuaObject handle = (ILuaObject) handleResult[0];

        // readLine() with no arg — default withTrailing=false
        Object[] lineResult = handle.callMethod(null, READER_READLINE, new Object[0]);
        assertNotNull(lineResult);
        assertArrayEquals("line1".getBytes(StandardCharsets.UTF_8), (byte[]) lineResult[0]);

        handle.callMethod(null, READER_CLOSE, new Object[0]);
    }

    @Test
    void readHandleReadLineWithTrailingIncludesNewline() throws Exception {
        Files.write(tempDir.resolve("lines.txt"), "line1\nline2".getBytes(StandardCharsets.UTF_8));

        Object[] handleResult = api.callMethod(null, METHOD_OPEN, new Object[] { "lines.txt", "r" });
        ILuaObject handle = (ILuaObject) handleResult[0];

        Object[] lineResult = handle.callMethod(null, READER_READLINE, new Object[] { Boolean.TRUE });
        assertNotNull(lineResult);
        assertArrayEquals("line1\n".getBytes(StandardCharsets.UTF_8), (byte[]) lineResult[0]);

        handle.callMethod(null, READER_CLOSE, new Object[0]);
    }

    @Test
    void readHandleSeekSetRewindsToStart() throws Exception {
        Files.write(tempDir.resolve("data.txt"), "Hello".getBytes(StandardCharsets.UTF_8));

        Object[] handleResult = api.callMethod(null, METHOD_OPEN, new Object[] { "data.txt", "r" });
        ILuaObject handle = (ILuaObject) handleResult[0];

        // Read 3 bytes
        handle.callMethod(null, READER_READ, new Object[] { 3 });

        // Seek back to start
        Object[] seekResult = handle.callMethod(null, READER_SEEK, new Object[] { "set", 0L });
        assertNotNull(seekResult);
        assertEquals(0L, seekResult[0]);

        // Read again from position 0
        Object[] readAgain = handle.callMethod(null, READER_READ, new Object[] { 3 });
        assertNotNull(readAgain);
        assertArrayEquals("Hel".getBytes(StandardCharsets.UTF_8), (byte[]) readAgain[0]);

        handle.callMethod(null, READER_CLOSE, new Object[0]);
    }

    @Test
    void readHandleSeekOnNonSeekableMountReturnsNilAndMessage()
        throws FileSystemException, LuaException, InterruptedException {
        fileSystem.mount("fixed", "fixed", new FixedContentMount());

        Object[] handleResult = api.callMethod(null, METHOD_OPEN, new Object[] { "fixed/hello.txt", "r" });
        assertNotNull(handleResult, "file on FixedContentMount should open");
        ILuaObject handle = (ILuaObject) handleResult[0];

        Object[] seekResult = handle.callMethod(null, READER_SEEK, new Object[] { "set", 0L });
        assertNotNull(seekResult, "seek on non-seekable mount should return {nil, msg}");
        assertNull(seekResult[0], "first return should be nil");
        assertNotNull(seekResult[1], "second return should be an error message");

        handle.callMethod(null, READER_CLOSE, new Object[0]);
    }

    // =========================================================================
    // WriteHandle — seek (Groups 6, 8)
    // =========================================================================

    @Test
    void writeHandleSeekOverwritesContent() throws Exception {
        Object[] handleResult = api.callMethod(null, METHOD_OPEN, new Object[] { "out.txt", "w" });
        assertNotNull(handleResult);
        ILuaObject handle = (ILuaObject) handleResult[0];

        handle.callMethod(null, WRITER_WRITE, new Object[] { "Hello".getBytes(StandardCharsets.UTF_8) });

        Object[] seekResult = handle.callMethod(null, WRITER_SEEK, new Object[] { "set", 0L });
        assertNotNull(seekResult);
        assertEquals(0L, seekResult[0]);

        handle.callMethod(null, WRITER_WRITE, new Object[] { "World".getBytes(StandardCharsets.UTF_8) });
        handle.callMethod(null, WRITER_CLOSE, new Object[0]);

        byte[] content = Files.readAllBytes(tempDir.resolve("out.txt"));
        assertArrayEquals("World".getBytes(StandardCharsets.UTF_8), content);
    }

    @Test
    void writeHandleSeekWithInvalidWhenceReturnsError() throws Exception {
        Object[] handleResult = api.callMethod(null, METHOD_OPEN, new Object[] { "out2.txt", "w" });
        ILuaObject handle = (ILuaObject) handleResult[0];

        Object[] seekResult = handle.callMethod(null, WRITER_SEEK, new Object[] { "bad", 0L });
        assertNotNull(seekResult);
        assertNull(seekResult[0], "invalid whence should return nil");
        assertNotNull(seekResult[1], "invalid whence should return error message");

        handle.callMethod(null, WRITER_CLOSE, new Object[0]);
    }

    // =========================================================================
    // ReadWriteHandle — read, readLine(withTrailing) (Groups 6, 9)
    // =========================================================================

    @Test
    void readWriteHandleReadBytesAfterWrite() throws Exception {
        Object[] handleResult = api.callMethod(null, METHOD_OPEN, new Object[] { "rw.txt", "w+" });
        assertNotNull(handleResult);
        ILuaObject handle = (ILuaObject) handleResult[0];

        handle.callMethod(null, RW_WRITE, new Object[] { "Hello".getBytes(StandardCharsets.UTF_8) });
        handle.callMethod(null, RW_SEEK, new Object[] { "set", 0L });

        Object[] readResult = handle.callMethod(null, RW_READ, new Object[] { 3 });
        assertNotNull(readResult);
        assertArrayEquals("Hel".getBytes(StandardCharsets.UTF_8), (byte[]) readResult[0]);

        handle.callMethod(null, RW_CLOSE, new Object[0]);
    }

    @Test
    void readWriteHandleReadLineWithTrailingIncludesNewline() throws Exception {
        Object[] handleResult = api.callMethod(null, METHOD_OPEN, new Object[] { "rw2.txt", "w+" });
        ILuaObject handle = (ILuaObject) handleResult[0];

        handle.callMethod(null, RW_WRITE, new Object[] { "line1\nline2".getBytes(StandardCharsets.UTF_8) });
        handle.callMethod(null, RW_SEEK, new Object[] { "set", 0L });

        Object[] lineResult = handle.callMethod(null, RW_READLINE, new Object[] { Boolean.TRUE });
        assertNotNull(lineResult);
        assertArrayEquals("line1\n".getBytes(StandardCharsets.UTF_8), (byte[]) lineResult[0]);

        handle.callMethod(null, RW_CLOSE, new Object[0]);
    }

    @Test
    void readWriteHandleReadLineWithoutTrailingStripsNewline() throws Exception {
        Object[] handleResult = api.callMethod(null, METHOD_OPEN, new Object[] { "rw3.txt", "w+" });
        ILuaObject handle = (ILuaObject) handleResult[0];

        handle.callMethod(null, RW_WRITE, new Object[] { "line1\nline2".getBytes(StandardCharsets.UTF_8) });
        handle.callMethod(null, RW_SEEK, new Object[] { "set", 0L });

        Object[] lineResult = handle.callMethod(null, RW_READLINE, new Object[0]);
        assertNotNull(lineResult);
        assertArrayEquals("line1".getBytes(StandardCharsets.UTF_8), (byte[]) lineResult[0]);

        handle.callMethod(null, RW_CLOSE, new Object[0]);
    }

    // =========================================================================
    // Stubs
    // =========================================================================

    private static class StubEnv implements IAPIEnvironment {

        private final FileSystem m_fs;

        StubEnv(FileSystem fs) {
            m_fs = fs;
        }

        @Override
        public FileSystem getFileSystem() {
            return m_fs;
        }

        @Override
        public Computer getComputer() {
            return null;
        }

        @Override
        public int getComputerID() {
            return 1;
        }

        @Override
        public IComputerEnvironment getComputerEnvironment() {
            return null;
        }

        @Override
        public Terminal getTerminal() {
            return null;
        }

        @Override
        public void shutdown() {}

        @Override
        public void reboot() {}

        @Override
        public void queueEvent(String event, Object[] args) {}

        @Override
        public void setOutput(int side, int output) {}

        @Override
        public int getOutput(int side) {
            return 0;
        }

        @Override
        public int getInput(int side) {
            return 0;
        }

        @Override
        public void setBundledOutput(int side, int output) {}

        @Override
        public int getBundledOutput(int side) {
            return 0;
        }

        @Override
        public int getBundledInput(int side) {
            return 0;
        }

        @Override
        public void setPeripheralChangeListener(IAPIEnvironment.IPeripheralChangeListener listener) {}

        @Override
        public IPeripheral getPeripheral(int side) {
            return null;
        }

        @Override
        public String getLabel() {
            return null;
        }

        @Override
        public void setLabel(String label) {}
    }

    /** Minimal read-only {@link IMount} exposing only its root directory. */
    private static class ReadOnlyStubMount implements IMount {

        @Override
        public boolean exists(String path) {
            return path.isEmpty();
        }

        @Override
        public boolean isDirectory(String path) {
            return path.isEmpty();
        }

        @Override
        public void list(String path, List<String> out) {}

        @Override
        public long getSize(String path) {
            return 0L;
        }

        @Override
        public java.io.InputStream openForRead(String path) throws java.io.IOException {
            throw new java.io.IOException("No such file");
        }
    }

    /**
     * A read-only {@link IMount} that exposes a single file {@code hello.txt} with fixed
     * content, but does NOT override {@link IMount#openForReadRandom} — so seek will
     * return {@code nil, msg} from Lua (uses the default-throw implementation).
     */
    private static class FixedContentMount implements IMount {

        private static final byte[] CONTENT = "hello".getBytes(StandardCharsets.UTF_8);

        @Override
        public boolean exists(String path) {
            return path.isEmpty() || path.equals("hello.txt");
        }

        @Override
        public boolean isDirectory(String path) {
            return path.isEmpty();
        }

        @Override
        public void list(String path, List<String> out) {
            if (path.isEmpty()) out.add("hello.txt");
        }

        @Override
        public long getSize(String path) {
            return CONTENT.length;
        }

        @Override
        public InputStream openForRead(String path) throws IOException {
            if (!path.equals("hello.txt")) throw new IOException("No such file");
            return new ByteArrayInputStream(CONTENT);
        }
        // openForReadRandom intentionally NOT overridden → uses default which throws IOException
    }
}
