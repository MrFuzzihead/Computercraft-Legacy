package dan200.computercraft.client.upload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dan200.computercraft.shared.computer.upload.FileUpload;
import dan200.computercraft.shared.computer.upload.UploadState;

/**
 * Tests for turning dropped paths into uploads.
 *
 * <p>
 * Deliberately not the drop hook itself: everything from the path onwards is plain file IO, and every
 * branch here is a case a player hits (dropping a folder, a file that is too big, more than the limit's
 * worth of files). The SDL watcher in front of it cannot be driven without a window, so it is kept as
 * thin as possible and everything behind it is tested.
 * </p>
 */
class FileUploadSenderTest {

    private static Path write(Path dir, String name, int size) throws IOException {
        Path path = dir.resolve(name);
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) ('a' + (i % 26));
        }

        Files.write(path, bytes);
        return path;
    }

    @Test
    void readsADroppedFileAndItsDigest(@TempDir Path dir) throws IOException {
        Path path = write(dir, "hello.txt", 11);

        List<FileUpload> uploads = FileUploadSender.read(new String[] { path.toString() }, 1024);

        assertEquals(1, uploads.size());
        assertEquals(
            "hello.txt",
            uploads.get(0)
                .getName());
        assertArrayEquals(
            Files.readAllBytes(path),
            uploads.get(0)
                .getBytes());
        assertTrue(
            uploads.get(0)
                .checksumMatches());
    }

    @Test
    void keepsEveryDroppedFileInOrder(@TempDir Path dir) throws IOException {
        Path first = write(dir, "a.txt", 3);
        Path second = write(dir, "b.txt", 5);

        List<FileUpload> uploads = FileUploadSender.read(new String[] { first.toString(), second.toString() }, 1024);

        assertEquals(2, uploads.size());
        assertEquals(
            "a.txt",
            uploads.get(0)
                .getName());
        assertEquals(
            "b.txt",
            uploads.get(1)
                .getName());
    }

    @Test
    void skipsDirectoriesInsteadOfFailingTheDrop(@TempDir Path dir) throws IOException {
        Path file = write(dir, "real.txt", 4);
        Path folder = Files.createDirectory(dir.resolve("folder"));

        List<FileUpload> uploads = FileUploadSender.read(new String[] { folder.toString(), file.toString() }, 1024);

        assertEquals(1, uploads.size());
        assertEquals(
            "real.txt",
            uploads.get(0)
                .getName());
    }

    @Test
    void ignoresAPathThatNoLongerExists(@TempDir Path dir) {
        List<FileUpload> uploads = assertDoesNotThrowRead(
            new String[] { dir.resolve("gone.txt")
                .toString() },
            1024);
        assertTrue(uploads.isEmpty());
    }

    @Test
    void refusesASingleFileOverTheLimit(@TempDir Path dir) throws IOException {
        Path path = write(dir, "big.bin", 2048);

        IOException error = assertThrows(
            IOException.class,
            () -> FileUploadSender.read(new String[] { path.toString() }, 1024));
        assertTrue(
            error.getMessage()
                .contains("larger than the upload limit"),
            error.getMessage());
    }

    @Test
    void refusesADropWhoseFilesTogetherExceedTheLimit(@TempDir Path dir) throws IOException {
        Path first = write(dir, "a.bin", 700);
        Path second = write(dir, "b.bin", 700);

        IOException error = assertThrows(
            IOException.class,
            () -> FileUploadSender.read(new String[] { first.toString(), second.toString() }, 1024));
        assertTrue(
            error.getMessage()
                .contains("total more than the upload limit"),
            error.getMessage());
    }

    @Test
    void refusesMoreFilesThanOneUploadCanCarry(@TempDir Path dir) throws IOException {
        String[] paths = new String[UploadState.MAX_FILES + 1];
        for (int i = 0; i < paths.length; i++) {
            paths[i] = write(dir, "f" + i + ".txt", 1).toString();
        }

        IOException error = assertThrows(IOException.class, () -> FileUploadSender.read(paths, 1024 * 1024));
        assertTrue(
            error.getMessage()
                .contains("Too many files"),
            error.getMessage());
    }

    @Test
    void anEmptyDropReadsNothing() {
        List<FileUpload> uploads = assertDoesNotThrowRead(new String[0], 1024);
        assertTrue(uploads.isEmpty());
    }

    private static List<FileUpload> assertDoesNotThrowRead(String[] paths, int maxSize) {
        try {
            return FileUploadSender.read(paths, maxSize);
        } catch (IOException e) {
            throw new AssertionError("expected no error, got " + e.getMessage(), e);
        }
    }

    @Test
    void aDroppedFileKeepsItsBaseNameOnly(@TempDir Path dir) throws IOException {
        Path nested = Files.createDirectories(dir.resolve("some/deep/dir"))
            .resolve("program.lua");
        Files.write(nested, "print('hi')".getBytes(StandardCharsets.UTF_8));

        List<FileUpload> uploads = FileUploadSender.read(new String[] { nested.toString() }, 1024);

        assertEquals(
            "program.lua",
            uploads.get(0)
                .getName(),
            "the computer must not see the client's directory structure");
        assertEquals(
            "program.lua",
            new File(
                uploads.get(0)
                    .getName()).getName());
    }
}
