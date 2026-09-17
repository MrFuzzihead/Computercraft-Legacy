package dan200.computercraft.core.filesystem;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.core.lua.binfs.LuaExceptionStub;

/**
 * Regression tests for the open-file accounting. {@code FileSystem} previously kept a
 * redundant {@code openFilesCount} field next to {@code m_openFiles}; the limit now uses
 * the set size directly. The limit itself must behave identically: opening one handle
 * beyond the cap is rejected and the rejected handle is closed, while closing handles
 * frees slots again.
 */
class FileSystemOpenFilesLimitTest {

    @TempDir
    Path tempDir;

    private FileSystem fileSystem;
    private int savedMaxHandles;

    @BeforeEach
    void setUp() throws Exception {
        Files.write(tempDir.resolve("f.txt"), new byte[] { 'x' });
        fileSystem = new FileSystem("hdd", new FileMount(tempDir.toFile(), 1024 * 1024L));
        savedMaxHandles = ComputerCraft.maxFilesHandles;
        ComputerCraft.maxFilesHandles = 3;
    }

    @AfterEach
    void tearDown() {
        ComputerCraft.maxFilesHandles = savedMaxHandles;
        if (fileSystem != null) fileSystem.unload();
    }

    @Test
    void openingBeyondLimitIsRejectedAndReleasesTheRejectedHandle() throws Exception {
        IMountedFileNormal first = fileSystem.openForRead("f.txt");
        IMountedFileNormal second = fileSystem.openForRead("f.txt");
        IMountedFileNormal third = fileSystem.openForRead("f.txt");
        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(third);

        // The 4th handle hits the limit of 3: rejected via LuaExceptionStub, and the
        // handle is closed inside addFile — so it must not remain tracked.
        assertThrows(LuaExceptionStub.class, () -> fileSystem.openForRead("f.txt"));
        assertEquals(3, openFileCount());

        // Rejection does not free any of the three accepted handles.
        assertThrows(LuaExceptionStub.class, () -> fileSystem.openForRead("f.txt"));
        assertEquals(3, openFileCount());
        first.close();
        assertNotNull(fileSystem.openForRead("f.txt"));
        assertEquals(3, openFileCount());
    }

    @Test
    void closingHandlesFreesSlotsAgain() throws Exception {
        IMountedFileNormal first = fileSystem.openForRead("f.txt");
        IMountedFileNormal second = fileSystem.openForRead("f.txt");
        IMountedFileNormal third = fileSystem.openForRead("f.txt");
        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(third);
        assertEquals(3, openFileCount());

        second.close();
        assertEquals(2, openFileCount());

        IMountedFileNormal fourth = fileSystem.openForRead("f.txt");
        assertNotNull(fourth);
        assertEquals(3, openFileCount());
    }

    @Test
    void closingTwiceCountsDownOnce() throws Exception {
        IMountedFileNormal first = fileSystem.openForRead("f.txt");
        assertNotNull(first);
        assertEquals(1, openFileCount());

        first.close();
        assertEquals(0, openFileCount());
        // Closing an already-removed handle must not corrupt the count.
        assertDoesNotThrow(() -> first.close());
        assertEquals(0, openFileCount());
        for (int i = 0; i < 3; i++) assertNotNull(fileSystem.openForRead("f.txt"));
        assertThrows(LuaExceptionStub.class, () -> fileSystem.openForRead("f.txt"));
    }

    private int openFileCount() throws Exception {
        java.lang.reflect.Field field = FileSystem.class.getDeclaredField("m_openFiles");
        field.setAccessible(true);
        return ((java.util.Set<?>) field.get(fileSystem)).size();
    }
}
