package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.filesystem.FileSystemException;
import dan200.computercraft.core.filesystem.IMountedFileNormal;

/**
 * Tests for the {@code readLine} implementation inside
 * {@link FileSystem#openForRead(String)}.
 *
 * <p>
 * The {@code readLine} method was updated to use a {@link java.io.PushbackInputStream}
 * so that CR+LF pairs are consumed as a single line terminator (matching the
 * behaviour of standard Lua's {@code io} library on Windows). These tests cover:
 * <ul>
 * <li>Unix LF-only line endings</li>
 * <li>Windows CR+LF line endings</li>
 * <li>Lone CR line endings (classic Mac)</li>
 * <li>Mixed line endings in the same file</li>
 * <li>Edge cases: empty file, trailing newlines, consecutive empty lines</li>
 * </ul>
 */
class FileSystemReadLineTest {

    // -------------------------------------------------------------------------
    // Helper: build a FileSystem backed by a single in-memory "testfile"
    // -------------------------------------------------------------------------

    private static FileSystem makeFs(byte[] content) throws FileSystemException {
        IMount mount = new IMount() {

            @Override
            public boolean exists(String path) {
                return path.isEmpty() || "testfile".equals(path);
            }

            @Override
            public boolean isDirectory(String path) {
                return path.isEmpty();
            }

            @Override
            public void list(String path, List<String> out) {
                if (path.isEmpty()) out.add("testfile");
            }

            @Override
            public long getSize(String path) {
                return "testfile".equals(path) ? content.length : 0;
            }

            @Override
            public InputStream openForRead(String path) throws IOException {
                if ("testfile".equals(path)) return new ByteArrayInputStream(content);
                throw new IOException("No such file: " + path);
            }
        };
        return new FileSystem("root", mount);
    }

    /** Decodes a {@code readLine} result (byte[]) to a String, or {@code null} for EOF. */
    private static String line(byte[] bytes) {
        return bytes == null ? null : new String(bytes, StandardCharsets.ISO_8859_1);
    }

    // -------------------------------------------------------------------------
    // Unix (LF) line endings
    // -------------------------------------------------------------------------

    @Test
    void testUnixLineEndings() throws Exception {
        FileSystem fs = makeFs("line1\nline2\nline3".getBytes(StandardCharsets.ISO_8859_1));
        IMountedFileNormal reader = fs.openForRead("testfile");
        assertEquals("line1", line(reader.readLine()));
        assertEquals("line2", line(reader.readLine()));
        assertEquals("line3", line(reader.readLine()));
        assertNull(line(reader.readLine()), "EOF must return null");
        reader.close();
    }

    @Test
    void testUnixLineEndingsWithTrailingNewline() throws Exception {
        // A trailing '\n' must NOT produce an extra empty line.
        FileSystem fs = makeFs("line1\nline2\n".getBytes(StandardCharsets.ISO_8859_1));
        IMountedFileNormal reader = fs.openForRead("testfile");
        assertEquals("line1", line(reader.readLine()));
        assertEquals("line2", line(reader.readLine()));
        assertNull(line(reader.readLine()), "Trailing LF must not produce a spurious empty line");
        reader.close();
    }

    // -------------------------------------------------------------------------
    // Windows (CR+LF) line endings
    // -------------------------------------------------------------------------

    @Test
    void testCrlfLineEndings() throws Exception {
        FileSystem fs = makeFs("line1\r\nline2\r\nline3".getBytes(StandardCharsets.ISO_8859_1));
        IMountedFileNormal reader = fs.openForRead("testfile");
        assertEquals("line1", line(reader.readLine()));
        assertEquals("line2", line(reader.readLine()));
        assertEquals("line3", line(reader.readLine()));
        assertNull(line(reader.readLine()), "EOF must return null");
        reader.close();
    }

    @Test
    void testCrlfLineEndingsWithTrailingNewline() throws Exception {
        // A trailing '\r\n' must NOT produce an extra empty line.
        FileSystem fs = makeFs("line1\r\nline2\r\n".getBytes(StandardCharsets.ISO_8859_1));
        IMountedFileNormal reader = fs.openForRead("testfile");
        assertEquals("line1", line(reader.readLine()));
        assertEquals("line2", line(reader.readLine()));
        assertNull(line(reader.readLine()), "Trailing CR+LF must not produce a spurious empty line");
        reader.close();
    }

    @Test
    void testCrlfDoesNotLeakCarriageReturn() throws Exception {
        // The '\r' in a CR+LF pair must be stripped; it must not appear in the line content.
        FileSystem fs = makeFs("hello\r\nworld".getBytes(StandardCharsets.ISO_8859_1));
        IMountedFileNormal reader = fs.openForRead("testfile");
        String first = line(reader.readLine());
        assertNotNull(first);
        assertFalse(first.contains("\r"), "CR must be stripped from CR+LF terminated line");
        assertEquals("hello", first);
        reader.close();
    }

    // -------------------------------------------------------------------------
    // Lone CR (classic Mac) line endings
    // -------------------------------------------------------------------------

    @Test
    void testLoneCrLineEndings() throws Exception {
        FileSystem fs = makeFs("line1\rline2\rline3".getBytes(StandardCharsets.ISO_8859_1));
        IMountedFileNormal reader = fs.openForRead("testfile");
        assertEquals("line1", line(reader.readLine()));
        assertEquals("line2", line(reader.readLine()));
        assertEquals("line3", line(reader.readLine()));
        assertNull(line(reader.readLine()), "EOF must return null");
        reader.close();
    }

    // -------------------------------------------------------------------------
    // Mixed line endings
    // -------------------------------------------------------------------------

    @Test
    void testMixedLineEndings() throws Exception {
        // Real-world files occasionally mix CR+LF and LF in the same file.
        byte[] content = "first\r\nsecond\nthird\r\nfourth".getBytes(StandardCharsets.ISO_8859_1);
        FileSystem fs = makeFs(content);
        IMountedFileNormal reader = fs.openForRead("testfile");
        assertEquals("first", line(reader.readLine()));
        assertEquals("second", line(reader.readLine()));
        assertEquals("third", line(reader.readLine()));
        assertEquals("fourth", line(reader.readLine()));
        assertNull(line(reader.readLine()), "EOF must return null");
        reader.close();
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    void testEmptyFile() throws Exception {
        FileSystem fs = makeFs(new byte[0]);
        IMountedFileNormal reader = fs.openForRead("testfile");
        assertNull(line(reader.readLine()), "Empty file must return null on first read");
        reader.close();
    }

    @Test
    void testSingleLineNoTrailingNewline() throws Exception {
        FileSystem fs = makeFs("only one line".getBytes(StandardCharsets.ISO_8859_1));
        IMountedFileNormal reader = fs.openForRead("testfile");
        assertEquals("only one line", line(reader.readLine()));
        assertNull(line(reader.readLine()), "EOF after single line must return null");
        reader.close();
    }

    @Test
    void testEmptyLinesProducedByConsecutiveNewlines() throws Exception {
        // "\n\nfoo" -> ["", "", "foo"]
        FileSystem fs = makeFs("\n\nfoo".getBytes(StandardCharsets.ISO_8859_1));
        IMountedFileNormal reader = fs.openForRead("testfile");
        assertEquals("", line(reader.readLine()), "First empty line");
        assertEquals("", line(reader.readLine()), "Second empty line");
        assertEquals("foo", line(reader.readLine()));
        assertNull(line(reader.readLine()), "EOF must return null");
        reader.close();
    }

    @Test
    void testEmptyLinesProducedByConsecutiveCrlf() throws Exception {
        // "\r\n\r\nfoo" -> ["", "", "foo"]
        FileSystem fs = makeFs("\r\n\r\nfoo".getBytes(StandardCharsets.ISO_8859_1));
        IMountedFileNormal reader = fs.openForRead("testfile");
        assertEquals("", line(reader.readLine()), "First empty line (CR+LF)");
        assertEquals("", line(reader.readLine()), "Second empty line (CR+LF)");
        assertEquals("foo", line(reader.readLine()));
        assertNull(line(reader.readLine()), "EOF must return null");
        reader.close();
    }
}
