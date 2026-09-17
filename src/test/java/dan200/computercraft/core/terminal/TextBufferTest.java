package dan200.computercraft.core.terminal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TextBuffer}.
 *
 * <p>
 * The main regression target is the {@code fill} family: filling with an empty
 * pattern used to reach {@code (i - pos) % textLength} with
 * {@code textLength == 0} and crash with {@code ArithmeticException: / by zero}
 * (the B1 finding in {@code docs/CODEBASE_ANALYSIS.md}).
 * </p>
 */
class TextBufferTest {

    @Test
    void fillWithEmptyStringIsANoOp() {
        TextBuffer buffer = new TextBuffer("abcabc", 1);

        assertDoesNotThrow(() -> buffer.fill("", 0, buffer.length()));
        assertEquals("abcabc", buffer.toString());
    }

    @Test
    void fillWithEmptyTextBufferIsANoOp() {
        TextBuffer buffer = new TextBuffer("abcabc", 1);

        assertDoesNotThrow(() -> buffer.fill(new TextBuffer(""), 0, buffer.length()));
        assertEquals("abcabc", buffer.toString());
    }

    @Test
    void fillWithEmptyStringViaOneArgOverloadIsANoOp() {
        TextBuffer buffer = new TextBuffer("abc", 1);

        assertDoesNotThrow(() -> buffer.fill(""));
        assertEquals("abc", buffer.toString());
    }

    @Test
    void fillTilesThePatternAcrossTheRange() {
        TextBuffer buffer = new TextBuffer("------", 1);

        buffer.fill("ab", 0, buffer.length());
        assertEquals("ababab", buffer.toString());
    }

    @Test
    void fillHonoursStartAndEndBounds() {
        TextBuffer buffer = new TextBuffer("abcdef", 1);

        buffer.fill("x", 2, 4);
        assertEquals("abxxef", buffer.toString());
    }

    @Test
    void fillClampsOutOfBoundsRanges() {
        TextBuffer buffer = new TextBuffer("abc", 1);

        // Negative start clamps to 0; end beyond the length clamps to length.
        buffer.fill("z", -5, 99);
        assertEquals("zzz", buffer.toString());
    }

    @Test
    void writeBasics() {
        TextBuffer buffer = new TextBuffer("hello", 1);

        buffer.write("XY", 1);
        assertEquals("hXYlo", buffer.toString());
    }

    @Test
    void writeClippingPreservesSourceOffsetForBothOverloads() {
        for (boolean useBuffer : new boolean[] { false, true }) {
            for (Object[] example : new Object[][] {
                { -2, 99, "cdef--" }, { 4, 99, "----ab" }, { 1, 3, "-ab---" },
                { 1, 99, "-abcde" }, { -8, 99, "------" }, { 9, 99, "------" },
                { 3, 1, "------" } }) {
                TextBuffer buffer = new TextBuffer("------");
                int start = (Integer) example[0];
                int end = (Integer) example[1];
                if (useBuffer) {
                    buffer.write(new TextBuffer("abcdef"), start, end);
                } else {
                    buffer.write("abcdef", start, end);
                }
                assertEquals(example[2], buffer.toString(), "buffer=" + useBuffer + ", start=" + start + ", end=" + end);
            }
        }
    }

    @Test
    void readHonoursStartAndEnd() {
        TextBuffer buffer = new TextBuffer("abcdef", 1);

        assertEquals("bcd", buffer.read(1, 4));
        assertEquals("abcdef", buffer.read());
    }

    @Test
    void repeatedConstructorTilesTheText() {
        TextBuffer buffer = new TextBuffer("ab", 3);

        assertEquals(6, buffer.length());
        assertEquals("ababab", buffer.toString());
    }
}
