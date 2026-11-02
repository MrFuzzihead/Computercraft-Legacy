package dan200.computercraft.core.apis.transfer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dan200.computercraft.api.lua.LuaException;

/**
 * Tests for the Lua-visible objects of the {@code file_transfer} event.
 *
 * <p>
 * These are the methods a program actually calls, and their contract is the one a {@code "rb"} file
 * handle has, so the interesting cases are the awkward ones: {@code read()} with no argument returns a
 * byte rather than a string, {@code read(0)} probes for end of file, {@code \r\n} collapsing in
 * {@code readLine}, and every method refusing a closed file.
 * </p>
 *
 * <p>
 * Nothing here needs a {@link dan200.computercraft.api.lua.ILuaContext}: the objects never suspend, so
 * the context is passed as {@code null} exactly as the tests for other handles do.
 * </p>
 */
class TransferredFileTest {

    private static TransferredFile file(String name, String contents) {
        return new TransferredFile(name, contents.getBytes(StandardCharsets.UTF_8));
    }

    private static int asByte(Object[] result) {
        assertNotNull(result);
        return ((Number) result[0]).intValue();
    }

    private static String asString(Object[] result) {
        assertNotNull(result);
        return new String((byte[]) result[0], StandardCharsets.UTF_8);
    }

    @Test
    void reportsItsName() throws LuaException {
        TransferredFile file = file("notes.txt", "abc");
        assertArrayEquals(new Object[] { "notes.txt" }, file.callMethod(null, 0, new Object[0]));
    }

    @Test
    void aBareReadReturnsOneByteAsANumber() throws LuaException {
        TransferredFile file = file("a.txt", "AB");

        assertEquals('A', asByte(file.callMethod(null, 1, new Object[0])));
        assertEquals('B', asByte(file.callMethod(null, 1, new Object[0])));
        assertNull(file.callMethod(null, 1, new Object[0]), "past the end it must return nil, not 0");
    }

    @Test
    void readWithACountReturnsAString() throws LuaException {
        TransferredFile file = file("a.txt", "abcdef");

        assertEquals("abcd", asString(file.callMethod(null, 1, new Object[] { 4D })));
        assertEquals("ef", asString(file.callMethod(null, 1, new Object[] { 10D })), "short reads are allowed");
        assertNull(file.callMethod(null, 1, new Object[] { 1D }));
    }

    @Test
    void readZeroProbesForTheEndOfTheFile() throws LuaException {
        TransferredFile file = file("a.txt", "a");

        assertArrayEquals(new Object[] { "" }, file.callMethod(null, 1, new Object[] { 0D }));
        file.callMethod(null, 1, new Object[0]);
        assertNull(file.callMethod(null, 1, new Object[] { 0D }));
    }

    @Test
    void readRejectsNegativeAndFractionalCounts() {
        TransferredFile file = file("a.txt", "abc");

        LuaException negative = assertThrows(LuaException.class, () -> file.callMethod(null, 1, new Object[] { -1D }));
        assertEquals("Cannot read a negative number of bytes", negative.getMessage());

        assertThrows(LuaException.class, () -> file.callMethod(null, 1, new Object[] { 1.5D }));
        assertThrows(LuaException.class, () -> file.callMethod(null, 1, new Object[] { "two" }));
    }

    @Test
    void readAllReturnsTheRestOfTheFile() throws LuaException {
        TransferredFile file = file("a.txt", "abcdef");

        file.callMethod(null, 1, new Object[] { 2D });
        assertEquals("cdef", asString(file.callMethod(null, 3, new Object[0])));
        assertEquals("", asString(file.callMethod(null, 3, new Object[0])), "at the end readAll is empty, not nil");
    }

    @Test
    void readLineCollapsesCarriageReturnsUnlessAskedToKeepThem() throws LuaException {
        TransferredFile file = file("a.txt", "one\r\ntwo\nthree");

        assertEquals("one", asString(file.callMethod(null, 2, new Object[0])));
        assertEquals("two" + (char) 10, asString(file.callMethod(null, 2, new Object[] { true })));

        TransferredFile trailing = file("b.txt", "one\r\ntwo");
        assertEquals("one\r\n", asString(trailing.callMethod(null, 2, new Object[] { true })));
    }

    @Test
    void readLineKeepsACarriageReturnThatIsNotPartOfALineEnding() throws LuaException {
        TransferredFile file = file("a.txt", "a\rb");

        assertEquals("a\rb", asString(file.callMethod(null, 2, new Object[0])));
    }

    @Test
    void readLineReturnsNilOnlyWhenThereIsNothingLeft() throws LuaException {
        TransferredFile file = file("a.txt", "x");

        assertEquals("x", asString(file.callMethod(null, 2, new Object[0])));
        assertNull(file.callMethod(null, 2, new Object[0]));
    }

    @Test
    void seekUnderstandsSetCurrentAndEnd() throws LuaException {
        TransferredFile file = file("a.txt", "abcdef");

        assertArrayEquals(new Object[] { 2L }, file.callMethod(null, 5, new Object[] { "set", 2D }));
        assertEquals("c", asString(file.callMethod(null, 1, new Object[] { 1D })));

        assertArrayEquals(new Object[] { 5L }, file.callMethod(null, 5, new Object[] { "cur", 2D }));
        assertArrayEquals(new Object[] { 3L }, file.callMethod(null, 5, new Object[] { "end", -3D }));
        assertEquals("def", asString(file.callMethod(null, 3, new Object[0])));
    }

    @Test
    void seekDefaultsToTheCurrentPosition() throws LuaException {
        TransferredFile file = file("a.txt", "abcdef");

        assertArrayEquals(new Object[] { 0L }, file.callMethod(null, 5, new Object[0]));
    }

    @Test
    void seekRejectsAnUnknownWhenceAndReportsANegativePosition() throws LuaException {
        TransferredFile file = file("a.txt", "abcdef");

        LuaException bad = assertThrows(
            LuaException.class,
            () -> file.callMethod(null, 5, new Object[] { "sideways" }));
        assertTrue(
            bad.getMessage()
                .contains("invalid option"),
            bad.getMessage());

        Object[] negative = file.callMethod(null, 5, new Object[] { "set", -1D });
        assertNotNull(negative);
        assertNull(negative[0]);
        assertEquals("Position is negative", negative[1]);

        Object[] tooLarge = file.callMethod(null, 5, new Object[] { "set", 4e9 });
        assertNotNull(tooLarge);
        assertNull(tooLarge[0]);
        assertEquals("Position is too large", tooLarge[1]);
    }

    @Test
    void aClosedFileRefusesEverything() throws LuaException {
        TransferredFile file = file("a.txt", "abc");

        file.callMethod(null, 4, new Object[0]);

        LuaException error = assertThrows(LuaException.class, () -> file.callMethod(null, 1, new Object[0]));
        assertEquals("attempt to use a closed file", error.getMessage());
        assertThrows(LuaException.class, () -> file.callMethod(null, 2, new Object[0]));
        assertThrows(LuaException.class, () -> file.callMethod(null, 3, new Object[0]));
        assertThrows(LuaException.class, () -> file.callMethod(null, 5, new Object[0]));
        assertThrows(LuaException.class, () -> file.callMethod(null, 4, new Object[0]));

        // getName still works: it reads no data.
        assertArrayEquals(new Object[] { "a.txt" }, file.callMethod(null, 0, new Object[0]));
    }

    @Test
    void theMethodNamesAreTheHandleOnesAndStayInOrder() {
        TransferredFile file = file("a.txt", "");
        assertArrayEquals(
            new String[] { "getName", "read", "readLine", "readAll", "close", "seek" },
            file.getMethodNames());
    }

    @Test
    void getFilesHandsLuaAListAndMarksTheUploadConsumedOnce() throws LuaException {
        AtomicInteger consumed = new AtomicInteger();
        List<TransferredFile> files = new ArrayList<>();
        files.add(file("a.txt", "a"));
        files.add(file("b.txt", "b"));

        TransferredFiles transferred = new TransferredFiles(files, consumed::incrementAndGet);
        assertArrayEquals(new String[] { "getFiles" }, transferred.getMethodNames());

        Object[] result = transferred.callMethod(null, 0, new Object[0]);
        assertNotNull(result);

        // The legacy bridge converts maps to Lua tables and knows nothing about Lists, so getFiles
        // hands over 1-based keys - which is also what #list measures on the Lua side.
        Map<?, ?> luaList = (Map<?, ?>) result[0];
        assertEquals(2, luaList.size());
        assertEquals("a.txt", ((TransferredFile) luaList.get(1)).getName());
        assertEquals("b.txt", ((TransferredFile) luaList.get(2)).getName());
        assertEquals(1, consumed.get());

        // The direct getter is the same path, and the ack must not fire twice.
        assertEquals(
            2,
            transferred.getFiles()
                .size());
        assertEquals(1, consumed.get(), "the client is told the upload was consumed exactly once");
    }
}
