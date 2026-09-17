package dan200.computercraft.core.filesystem;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dan200.computercraft.api.filesystem.IMount;

class FileSystemReadHandleTest {

    @TempDir
    Path directory;
    private FileSystem fs;

    private IMountedFileNormal open(String kind, String content) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.ISO_8859_1);
        if (kind.equals("stream") || kind.equals("legacy")) {
            IMount mount = mock(IMount.class);
            when(mount.exists("test")).thenReturn(true);
            when(mount.openForRead("test")).thenAnswer(invocation -> new ByteArrayInputStream(bytes));
            fs = new FileSystem("rom", mount);
        } else {
            Files.write(directory.resolve("test"), bytes);
            fs = new FileSystem("hdd", new FileMount(directory.toFile(), 1024 * 1024));
        }
        if (kind.equals("legacy")) return fs.openForRead("test");
        if (kind.equals("rw")) return fs.openForReadWrite("test", false);
        if (kind.equals("write")) return fs.openForWriteSeekable("test", false);
        return fs.openForReadSeekable("test");
    }

    @AfterEach
    void cleanup() {
        if (fs != null) fs.unload();
    }

    private static void assertBytes(String expected, byte[] actual) {
        assertArrayEquals(expected.getBytes(StandardCharsets.ISO_8859_1), actual);
    }

    @ParameterizedTest
    @ValueSource(strings = { "legacy", "stream", "raf", "rw" })
    void sharedLineReaderPreservesDelimitersLookaheadAndEof(String kind) throws Exception {
        IMountedFileNormal handle = open(kind, "a\r\nb\rc\n\n\u00ff\r");
        assertBytes("a", handle.readLine());
        assertBytes("b", handle.readLine());
        assertBytes("c", handle.readLine());
        assertBytes("", handle.readLine());
        assertBytes("\u00ff", handle.readLine());
        assertNull(handle.readLine());
        assertBytes("", handle.readAll());
    }

    @ParameterizedTest
    @ValueSource(strings = { "legacy", "stream", "raf", "rw" })
    void trailingNewlineFlagAndUnterminatedLinePreserveLegacyBehavior(String kind) throws Exception {
        IMountedFileNormal handle = open(kind, "a\r\nb\rc\ntail");
        String suffix = kind.equals("legacy") ? "" : "\n";
        assertBytes("a" + suffix, handle.readLine(true));
        assertBytes("b" + suffix, handle.readLine(true));
        assertBytes("c" + suffix, handle.readLine(true));
        assertBytes("tail", handle.readLine(true));
        assertNull(handle.readLine(true));
    }

    @ParameterizedTest
    @ValueSource(strings = { "stream", "raf", "rw" })
    void mixedReadsSharePositionAndPreserveBinaryBytes(String kind) throws Exception {
        IMountedFileNormal handle = open(kind, "a\rb\u0000\u00fftail");
        assertBytes("a", handle.readLine());
        assertBytes("", handle.read(0));
        assertBytes("b\u0000", handle.read(2));
        assertBytes("\u00fftail", handle.readAll());
        assertNull(handle.read(1));
        assertBytes("", handle.read(-1));
    }

    @ParameterizedTest
    @ValueSource(strings = { "legacy", "stream", "raf", "rw" })
    void crLookaheadWorksAcrossBufferBoundary(String kind) throws Exception {
        char[] chars = new char[8191];
        java.util.Arrays.fill(chars, 'x');
        String prefix = new String(chars);
        IMountedFileNormal handle = open(kind, prefix + "\rZ\n");
        assertBytes(prefix, handle.readLine());
        assertBytes("Z", handle.readLine());
        assertNull(handle.readLine());
    }

    @ParameterizedTest
    @ValueSource(strings = { "raf", "rw", "write" })
    void seekModesAndErrorsUseSharedImplementation(String kind) throws Exception {
        IMountedFileNormal handle = open(kind, "abcdef");
        long length = kind.equals("write") ? 0 : 6;
        assertEquals(2, handle.seek("set", 2));
        assertEquals(1, handle.seek("cur", -1));
        assertEquals(length, handle.seek("end", 0));
        assertEquals("Invalid whence value", assertThrows(IOException.class, () -> handle.seek("bad", 0)).getMessage());
        assertEquals(
            "Cannot seek before the beginning of the file",
            assertThrows(IOException.class, () -> handle.seek("set", -1)).getMessage());
        assertEquals(length, handle.seek("cur", 0));
    }

    @ParameterizedTest
    @ValueSource(strings = { "legacy", "stream", "raf" })
    void readOnlyHandlesRejectWriteAndFlush(String kind) throws Exception {
        IMountedFileNormal handle = open(kind, "a");
        assertThrows(UnsupportedOperationException.class, () -> handle.write(new byte[] { 1 }, 0, 1, false));
        assertThrows(UnsupportedOperationException.class, handle::flush);
        if (!kind.equals("raf")) {
            assertEquals(
                "seek not supported by this handle",
                assertThrows(IOException.class, () -> handle.seek("set", 0)).getMessage());
        }
        if (kind.equals("legacy")) {
            assertEquals(
                "read not supported by this handle",
                assertThrows(IOException.class, () -> handle.read(1)).getMessage());
        }
    }

    @Test
    void writeOnlyHandleRejectsReadingAndWritesNewlines() throws Exception {
        IMountedFileNormal handle = open("write", "old");
        assertThrows(UnsupportedOperationException.class, handle::readLine);
        assertThrows(UnsupportedOperationException.class, () -> handle.readLine(true));
        assertThrows(UnsupportedOperationException.class, handle::readAll);
        assertThrows(IOException.class, () -> handle.read(0));
        handle.write(new byte[] { 'a', 'b', 'c' }, 1, 1, true);
        handle.flush();
        assertEquals(2, handle.seek("cur", 0));
        handle.close();
        assertBytes("b\n", Files.readAllBytes(directory.resolve("test")));
    }
}
