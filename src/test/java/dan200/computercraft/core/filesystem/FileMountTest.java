package dan200.computercraft.core.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link FileMount} hardening (B6).
 *
 * <p>
 * {@link File#list()} returns {@code null} when a directory cannot be read
 * (I/O error, or the path is not a directory at the moment of the call). The
 * pre-fix code iterated the result directly, so such a directory produced an
 * uncaught {@link NullPointerException} that killed the computer's coroutine.
 * These tests drive the exact call sites with a {@link File} stub whose
 * {@code list()} returns {@code null}, which cannot be produced portably on a
 * real filesystem.
 * </p>
 */
class FileMountTest {

    private static final long CAPACITY = 1024 * 1024L;
    /** {@code FileMount.MINIMUM_FILE_SIZE} — charged per file and per directory. */
    private static final long MINIMUM_FILE_SIZE = 500L;

    @TempDir
    Path tempDir;

    private FileMount mount;

    @BeforeEach
    void setUp() {
        mount = new FileMount(tempDir.toFile(), CAPACITY);
    }

    // =========================================================================
    // list — unreadable directory
    // =========================================================================

    @Test
    void listOnUnreadableDirectoryThrowsAccessDenied() {
        List<String> contents = new ArrayList<>();
        IOException error = assertThrows(
            IOException.class,
            () -> unreadableListingMount().list("docs", contents),
            "an unreadable directory must raise IOException, not NPE");

        assertEquals("Access denied", error.getMessage());
        assertTrue(contents.isEmpty(), "nothing must be added for an unreadable directory");
    }

    @Test
    void listOnFileStillReportsNotADirectory() throws IOException {
        Files.write(tempDir.resolve("plain.txt"), "hi".getBytes(StandardCharsets.UTF_8));

        IOException error = assertThrows(IOException.class, () -> mount.list("plain.txt", new ArrayList<>()));

        assertEquals("Not a directory", error.getMessage());
    }

    @Test
    void listOnNormalDirectoryStillWorks() throws IOException {
        Files.createDirectory(tempDir.resolve("docs"));
        Files.write(tempDir.resolve("docs/a.txt"), "a".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("docs/b.txt"), "b".getBytes(StandardCharsets.UTF_8));

        List<String> contents = new ArrayList<>();
        mount.list("docs", contents);

        contents.sort(null);
        assertEquals(Arrays.asList("a.txt", "b.txt"), contents);
    }

    // =========================================================================
    // delete — unreadable directory
    // =========================================================================

    @Test
    void deleteOnUnreadableDirectoryThrowsAccessDenied() {
        IOException error = assertThrows(
            IOException.class,
            () -> unreadableListingMount().delete("docs"),
            "deleting an unreadable directory must raise IOException, not NPE");

        assertEquals("Access denied", error.getMessage());
    }

    @Test
    void deleteOnUnreadableDirectoryLeavesUsedSpaceUnchanged() throws IOException {
        FileMount unreadable = unreadableListingMount();
        long before = unreadable.getRemainingSpace();

        assertThrows(IOException.class, () -> unreadable.delete("docs"));

        assertEquals(before, unreadable.getRemainingSpace(), "a failed delete must not change the space accounting");
    }

    // =========================================================================
    // measureUsedSpace (constructor path)
    // =========================================================================

    @Test
    void measureUsedSpaceHandlesUnreadableDirectory() throws Exception {
        // The constructor calls measureUsedSpace, which cannot declare IOException:
        // an unreadable subdirectory must be accounted for as itself only.
        Method measure = FileMount.class.getDeclaredMethod("measureUsedSpace", File.class);
        measure.setAccessible(true);

        Object result = measure.invoke(mount, new UnreadableDirectory());

        assertEquals(
            MINIMUM_FILE_SIZE,
            ((Number) result).longValue(),
            "an unreadable directory must count as just that directory");
    }

    @Test
    void measureUsedSpaceStillCountsReadableContents() throws IOException {
        Files.createDirectory(tempDir.resolve("docs"));
        long fileBytes = 2000L;
        Files.write(tempDir.resolve("docs/a.txt"), new byte[(int) fileBytes]);
        FileMount measured = new FileMount(tempDir.toFile(), CAPACITY);

        // Root dir + sub dir at MINIMUM_FILE_SIZE each, plus the file's own size.
        long expectedUsed = MINIMUM_FILE_SIZE + MINIMUM_FILE_SIZE + fileBytes;
        assertEquals(
            CAPACITY + MINIMUM_FILE_SIZE - expectedUsed,
            measured.getRemainingSpace(),
            "used space must still include readable directory contents");
    }

    // =========================================================================
    // Stubs
    // =========================================================================

    /**
     * A {@link FileMount} whose {@code getRealPath} hands back a stub directory
     * that fails to list — the state a real {@code list()} returning {@code null}
     * represents.
     */
    private FileMount unreadableListingMount() {
        File stub = new UnreadableDirectory();
        return new FileMount(tempDir.toFile(), CAPACITY) {

            @Override
            public File getRealPath(String path) {
                return stub;
            }
        };
    }

    /** Directory that exists and reports itself as a directory, but cannot be listed. */
    private static class UnreadableDirectory extends File {

        UnreadableDirectory() {
            super("unreadable");
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean isDirectory() {
            return true;
        }

        @Override
        public String[] list() {
            return null;
        }

        @Override
        public long length() {
            return 0L;
        }
    }
}
