package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

/**
 * Drives {@code rom/programs/fun/advanced/paint} through the real BIOS and shell.
 *
 * <p>
 * Two things are checked: that {@code paint.default_extension} is honoured (a new file with no
 * extension becomes {@code .nfp}, which it never did before), and that the footer behaves like the
 * editor's — a click opens the shared menu bar.
 * </p>
 */
class PaintProgramInputTest {

    private static void openPaint(BiosInputHarness harness, String name) {
        harness.pump(5);
        harness.run("paint " + name);
    }

    @Test
    void aNewFileGetsTheDefaultExtension() throws Exception {
        BiosInputHarness h = BiosInputHarness.boot();
        try {
            openPaint(h, "picture");

            // Save through the menu's letter hotkey.
            h.press(BiosInputHarness.KEY_LEFT_CTRL);
            h.pump(5);
            h.type("s");
            h.pump(10);

            File painted = new File(h.hdd, "picture.nfp");
            assertTrue(
                painted.isFile(),
                "paint must append paint.default_extension to a new extensionless path. Screen:\n" + h.screen());
            assertFalse(new File(h.hdd, "picture").isFile(), "the extensionless path should not be created");
        } finally {
            h.close();
        }
    }

    @Test
    void anExistingExtensionIsKept() throws Exception {
        BiosInputHarness h = BiosInputHarness.boot(dir -> {
            try {
                Files.write(new File(dir, "art.png").toPath(), new byte[0]);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        try {
            openPaint(h, "art.png");
            h.press(BiosInputHarness.KEY_LEFT_CTRL);
            h.pump(5);
            h.type("s");
            h.pump(10);

            assertFalse(
                new File(h.hdd, "art.png.nfp").isFile(),
                "do not add a second extension. Screen:\n" + h.screen());
        } finally {
            h.close();
        }
    }

    @Test
    void theDefaultExtensionCanBeChanged() throws Exception {
        BiosInputHarness h = BiosInputHarness.boot(dir -> {
            // settings.load reads a bare table literal.
            try {
                Files.write(
                    new File(dir, ".settings").toPath(),
                    "{ [\"paint.default_extension\"] = \"img\" }\n".getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        try {
            openPaint(h, "picture2");
            h.press(BiosInputHarness.KEY_LEFT_CTRL);
            h.pump(5);
            h.type("s");
            h.pump(10);

            assertTrue(
                new File(h.hdd, "picture2.img").isFile(),
                "paint.default_extension should be honoured. Screen:\n" + h.screen());
        } finally {
            h.close();
        }
    }

    @Test
    void clickingTheFooterOpensTheMenu() throws Exception {
        BiosInputHarness h = BiosInputHarness.boot();
        try {
            openPaint(h, "clickpaint");
            int footer = h.height();

            assertFalse(
                h.line(footer)
                    .contains("Save"),
                "the menu starts closed. Screen:\n" + h.screen());

            h.click(2, footer);
            h.pump(5);

            String footerRow = h.line(footer);
            assertTrue(
                footerRow.contains("Save"),
                "clicking paint's footer should draw the menu. Footer was: '" + footerRow + "'");
        } finally {
            h.close();
        }
    }
}
