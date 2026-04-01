package dan200.computercraft.core.apis;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.IComputerEnvironment;
import dan200.computercraft.core.filesystem.FileMount;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.filesystem.FileSystemException;
import dan200.computercraft.core.terminal.Terminal;

/**
 * Unit tests for the {@code fs.getCapacity} and {@code fs.attributes} methods
 * added to {@link FSAPI}.
 *
 * <p>
 * Tests call {@link FSAPI#callMethod} directly with a {@code null}
 * {@link dan200.computercraft.api.lua.ILuaContext}; neither method schedules
 * work on the main thread, so this is safe.
 * </p>
 */
class FSAPITest {

    // Method indices must match the order declared in FSAPI.getMethodNames().
    private static final int METHOD_GET_CAPACITY = 16;
    private static final int METHOD_ATTRIBUTES = 17;

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
}
