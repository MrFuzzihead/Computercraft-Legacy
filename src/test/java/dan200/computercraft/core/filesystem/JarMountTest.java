package dan200.computercraft.core.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link JarMount} root discovery (B7).
 *
 * <p>
 * The pre-fix constructor only recognised the mount root if it encountered an
 * entry that resolved to the empty local path, and it accepted any entry whose
 * name merely <em>started with</em> the sub-path. If the root was never
 * resolved, {@code m_root} stayed {@code null} and every public method NPE'd on
 * first use. These tests pin the fixed contract: children enumerated before
 * their parent directory are still mounted, prefix siblings such as
 * {@code "romario/x"} are not mounted under {@code "rom"}, and a sub-path that
 * cannot be resolved fails with {@link IOException} rather than an NPE.
 * </p>
 */
class JarMountTest {

    @TempDir
    Path tempDir;

    private final List<JarMount> mounts = new ArrayList<>();

    /**
     * Releases the {@link ZipFile} handle each mount opened. {@link JarMount}
     * intentionally exposes no {@code close()} (resource packs live for the
     * process lifetime), but on Windows the open handle blocks JUnit's
     * temp-directory cleanup, so tests must release it explicitly.
     */
    @AfterEach
    void releaseJarHandles() throws Exception {
        Field zipFileField = JarMount.class.getDeclaredField("m_zipFile");
        zipFileField.setAccessible(true);
        for (JarMount mount : mounts) {
            ZipFile zipFile = (ZipFile) zipFileField.get(mount);
            if (zipFile != null) {
                zipFile.close();
            }
        }
        mounts.clear();
    }

    /** Constructs and registers a mount for handle cleanup. */
    private JarMount openMount(File jar, String subPath) throws IOException {
        JarMount mount = new JarMount(jar, subPath);
        mounts.add(mount);
        return mount;
    }

    // =========================================================================
    // Root discovery / tree building
    // =========================================================================

    @Test
    void childrenEnumeratedBeforeRootAreStillMounted() throws IOException {
        // The file entry is written (and therefore enumerated) before "rom/".
        File jar = writeJar("child-first.zip", "rom/a.txt", "rom/");

        JarMount mount = openMount(jar, "rom");

        assertTrue(mount.exists("a.txt"), "a child seen before the root entry must still be mounted");
        assertEquals(Arrays.asList("a.txt"), list(mount, ""));
    }

    @Test
    void nestedChildrenAttachParentsFirstRegardlessOfOrder() throws IOException {
        // Deepest entry first, its parent directory second, the root last.
        File jar = writeJar("deep-first.zip", "rom/x/y.txt", "rom/x/", "rom/");

        JarMount mount = openMount(jar, "rom");

        assertTrue(mount.isDirectory("x"), "intermediate directory must exist");
        assertEquals(Arrays.asList("x"), list(mount, ""));
        assertEquals(Arrays.asList("y.txt"), list(mount, "x"), "nested child must be attached under its parent");
        assertTrue(mount.exists("x/y.txt"));
    }

    @Test
    void prefixSiblingsAreNotMounted() throws IOException {
        // "romario/fake.txt" starts with "rom" but is not inside it. The pre-fix
        // startsWith() check passed it to FileSystem.toLocal, whose contains()
        // assertion fails (and without assertions it silently mirrored the file).
        File jar = writeJar("prefix-sibling.zip", "rom/", "rom/real.txt", "romario/fake.txt");

        JarMount mount = openMount(jar, "rom");

        assertEquals(Arrays.asList("real.txt"), list(mount, ""), "prefix siblings must not appear in the mount");
        assertFalse(mount.exists("fake.txt"));
        assertFalse(mount.exists("ario/fake.txt"));
    }

    // =========================================================================
    // Failure modes
    // =========================================================================

    @Test
    void missingSubPathThrowsIOException() throws IOException {
        File jar = writeJar("no-such-path.zip", "other/a.txt");

        IOException error = assertThrows(IOException.class, () -> new JarMount(jar, "rom"));

        assertEquals("Zip does not contain path", error.getMessage());
    }

    @Test
    void missingJarThrowsFileNotFoundException() {
        File missing = tempDir.resolve("absent.zip")
            .toFile();

        assertThrows(FileNotFoundException.class, () -> new JarMount(missing, "rom"));
    }

    @Test
    void nonZipFileThrowsIOException() throws IOException {
        Path notAZip = tempDir.resolve("corrupt.zip");
        Files.write(notAZip, "this is not a zip archive".getBytes(StandardCharsets.UTF_8));

        IOException error = assertThrows(IOException.class, () -> new JarMount(notAZip.toFile(), "rom"));

        assertEquals("Error loading zip file", error.getMessage());
    }

    // =========================================================================
    // Reading through a valid mount
    // =========================================================================

    @Test
    void validMountReadsContents() throws IOException {
        File jar = writeJar("valid.zip", "rom/", "rom/hello.txt");

        JarMount mount = openMount(jar, "rom");

        assertTrue(mount.exists(""));
        assertTrue(mount.isDirectory(""));
        assertEquals(0L, mount.getSize(""), "a directory reports size 0");
        assertEquals("content:rom/hello.txt".length(), mount.getSize("hello.txt"));
        try (InputStream in = mount.openForRead("hello.txt")) {
            assertNotNull(in);
            byte[] read = readAll(in);
            assertEquals("content:rom/hello.txt", new String(read, StandardCharsets.UTF_8));
        }
    }

    @Test
    void rootFileMountIsUsable() throws IOException {
        // A sub-path that points at a zip *file* entry: the root is a file, and
        // every method must still work (the pre-fix code broke out of the loop
        // immediately, which is fine, but the root must be usable afterwards).
        File jar = writeJar("root-file.zip", "rom");

        JarMount mount = openMount(jar, "rom");

        assertTrue(mount.exists(""));
        assertFalse(mount.isDirectory(""));
        assertEquals("content:rom".length(), mount.getSize(""));
        try (InputStream in = mount.openForRead("")) {
            assertNotNull(in);
            assertEquals("content:rom", new String(readAll(in), StandardCharsets.UTF_8));
        }
    }

    @Test
    void missingEntriesReportCleanErrors() throws IOException {
        File jar = writeJar("missing.zip", "rom/", "rom/hello.txt");
        JarMount mount = openMount(jar, "rom");

        assertFalse(mount.exists("nope.txt"));
        assertFalse(mount.isDirectory("nope.txt"));
        assertEquals("No such file", assertThrows(IOException.class, () -> mount.getSize("nope.txt")).getMessage());
        assertEquals("No such file", assertThrows(IOException.class, () -> mount.openForRead("nope.txt")).getMessage());
        assertEquals(
            "No such file",
            assertThrows(IOException.class, () -> mount.openForRead("")).getMessage(),
            "a directory cannot be opened for reading");
        assertEquals(
            "Not a directory",
            assertThrows(IOException.class, () -> mount.list("hello.txt", new ArrayList<>())).getMessage());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[512];
        int read;
        while ((read = in.read(chunk)) > 0) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private static List<String> list(JarMount mount, String path) throws IOException {
        List<String> contents = new ArrayList<>();
        mount.list(path, contents);
        return contents;
    }

    /**
     * Writes a zip with the given entries, in order. Entries ending in
     * {@code /} are directories; everything else is a file containing its own name.
     */
    private File writeJar(String name, String... entries) throws IOException {
        File jar = tempDir.resolve(name)
            .toFile();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(jar))) {
            for (String entry : entries) {
                out.putNextEntry(new ZipEntry(entry));
                if (!entry.endsWith("/")) {
                    out.write(("content:" + entry).getBytes(StandardCharsets.UTF_8));
                }
                out.closeEntry();
            }
        }
        return jar;
    }
}
