package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

/**
 * Drives {@code rom/programs/edit} through the real BIOS, shell and terminal.
 *
 * <h2>Why this is not a mocked unit test</h2>
 * <p>
 * The editor's input handling is one loop over raw events, and the behaviours that matter are what
 * a keystroke or a click does to the buffer. A mock of {@code term} can check that a function was
 * called, but not that Tab leaves four spaces in the saved file, or that clicking the footer opens
 * a menu that can then save. With the real ROM the assertion is the file on disk.
 * </p>
 *
 * <p>
 * Note that Tab accepts a completion when one is showing (that is upstream behaviour), so the
 * indent tests start from an empty line, where nothing is offered.
 * </p>
 */
class EditProgramInputTest {

    private static File file(BiosInputHarness harness, String name) {
        return new File(harness.hdd, name);
    }

    private static String read(BiosInputHarness harness, String name) throws Exception {
        File f = file(harness, name);
        assertTrue(f.isFile(), name + " should exist. Screen:\n" + harness.screen());
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /** Opens the editor on {@code name} and waits for it to be ready. */
    private static void openEditor(BiosInputHarness harness, String name) {
        harness.pump(5);
        harness.run("edit " + name);
    }

    /** Saves through the menu's letter hotkey. */
    private static void saveWithHotkey(BiosInputHarness harness) {
        harness.press(BiosInputHarness.KEY_LEFT_CTRL);
        harness.pump(5);
        // The menu module selects an item by its first letter.
        harness.type("s");
        harness.pump(10);
    }

    @Test
    void tabIndentsByFourSpaces() throws Exception {
        BiosInputHarness h = BiosInputHarness.boot();
        try {
            openEditor(h, "indent.txt");
            h.press(BiosInputHarness.KEY_TAB);
            h.type("x");
            saveWithHotkey(h);

            assertEquals(
                "    x\n",
                read(h, "indent.txt"),
                "Tab must insert one four-space indent level. Screen:\n" + h.screen());
        } finally {
            h.close();
        }
    }

    @Test
    void backspaceRemovesAWholeIndentInLeadingWhitespace() throws Exception {
        BiosInputHarness h = BiosInputHarness.boot();
        try {
            openEditor(h, "dedent.txt");
            h.press(BiosInputHarness.KEY_TAB);
            h.type("x");
            // Cursor is just after the "x"; one step left puts it inside the leading whitespace.
            h.press(BiosInputHarness.KEY_LEFT);
            h.press(BiosInputHarness.KEY_BACKSPACE);
            saveWithHotkey(h);

            assertEquals(
                "x\n",
                read(h, "dedent.txt"),
                "backspacing inside the indent should remove all four spaces, not one. Screen:\n" + h.screen());
        } finally {
            h.close();
        }
    }

    @Test
    void backspaceAfterTextRemovesOneCharacter() throws Exception {
        BiosInputHarness h = BiosInputHarness.boot();
        try {
            openEditor(h, "single.txt");
            h.type("abc");
            // Cursor is at the end of "abc": backspace there must remove one character only, not
            // try to treat the (non-leading) text as indentation.
            h.press(BiosInputHarness.KEY_BACKSPACE);
            saveWithHotkey(h);

            assertEquals("ab\n", read(h, "single.txt"));
        } finally {
            h.close();
        }
    }

    @Test
    void numPadEnterInsertsANewline() throws Exception {
        BiosInputHarness h = BiosInputHarness.boot();
        try {
            openEditor(h, "numpad.txt");
            h.type("one");
            h.press(BiosInputHarness.KEY_NUM_PAD_ENTER);
            h.type("two");
            saveWithHotkey(h);

            assertEquals(
                "one\ntwo\n",
                read(h, "numpad.txt"),
                "numpad Enter must behave like Enter. Screen:\n" + h.screen());
        } finally {
            h.close();
        }
    }

    @Test
    void clickingTheFooterOpensTheMenu() throws Exception {
        BiosInputHarness h = BiosInputHarness.boot();
        try {
            openEditor(h, "footer.txt");
            int footer = h.height();

            assertFalse(
                h.line(footer)
                    .contains("Save"),
                "the menu should be closed to begin with. Screen:\n" + h.screen());

            h.click(2, footer);
            h.pump(5);

            String footerRow = h.line(footer);
            assertTrue(
                footerRow.contains("Save"),
                "clicking the footer should draw the menu. Footer was: '" + footerRow + "'");
        } finally {
            h.close();
        }
    }

    @Test
    void clickingAMenuItemSaves() throws Exception {
        BiosInputHarness h = BiosInputHarness.boot();
        try {
            openEditor(h, "clickedit.txt");
            h.type("hi");

            int footer = h.height();
            h.click(2, footer);
            h.pump(5);

            // "Save" is the first item, drawn at columns 1..6 as "[Save]".
            h.click(2, footer);
            h.pump(10);

            assertEquals(
                "hi\n",
                read(h, "clickedit.txt"),
                "clicking Save in the menu must save the buffer. Screen:\n" + h.screen());
        } finally {
            h.close();
        }
    }

    @Test
    void aFileOnAReadOnlyMountSaysSoInTheFooter() throws Exception {
        // The ROM is mounted read-only, so fs.isReadOnly is true for its files. (A read-only *file*
        // inside a writable mount is not reported as read-only by the filesystem.)
        BiosInputHarness h = BiosInputHarness.boot();
        try {
            h.pump(5);
            h.run("edit /rom/startup");
            String footer = h.line(h.height());
            assertTrue(
                footer.contains("read only") || footer.contains("read-only"),
                "a read-only file should say so instead of inviting a save that cannot work. Footer was: '" + footer
                    + "'");
        } finally {
            h.close();
        }
    }

    @Test
    void caretMovesWithArrowKeysAndTypesWhereExpected() throws Exception {
        BiosInputHarness h = BiosInputHarness.boot();
        try {
            openEditor(h, "arrows.txt");
            h.type("abc");
            h.press(BiosInputHarness.KEY_LEFT);
            h.press(BiosInputHarness.KEY_LEFT);
            h.type("Z");
            saveWithHotkey(h);

            assertEquals("aZbc\n", read(h, "arrows.txt"), "Screen:\n" + h.screen());
        } finally {
            h.close();
        }
    }

    @Test
    void aSavedBufferReportsSuccessInTheFooter() throws Exception {
        BiosInputHarness h = BiosInputHarness.boot();
        try {
            openEditor(h, "status.txt");
            h.type("hi");
            saveWithHotkey(h);

            String footer = h.line(h.height());
            assertTrue(
                footer.contains("Saved to"),
                "a successful save should be reported in the footer. Footer was: '" + footer + "'");
        } finally {
            h.close();
        }
    }
}
