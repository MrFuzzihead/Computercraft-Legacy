package dan200.computercraft.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Checks that the characters the ROM's error printer draws have glyphs.
 *
 * <h2>Why this exists</h2>
 * <p>
 * This renderer indexes glyphs through a CP437 table into the vanilla font sheet, so a character
 * the sheet has no counterpart for renders as {@code ?}. CC:Tweaked has its own font, and
 * {@code cc.internal.error_printer} uses two of its private glyphs:
 * {@code \x95} for the rule drawn beside an annotated line and {@code \x83} for the bar under the
 * token that failed. Neither is in the vanilla sheet, so an annotated error printed as
 * {@code ?Line 1} — visible in game, invisible to every other test.
 * </p>
 * <p>
 * The renderer now maps those two onto the nearest glyph it does have. These tests pin both the
 * mapping and the reason for it: they scan the printer for the high characters it actually emits,
 * so if upstream starts using a third one, this fails instead of silently printing {@code ?}.
 * </p>
 */
class FixedWidthFontRendererGlyphTest {

    private static final Path ERROR_PRINTER = Path
        .of("src/main/resources/assets/computercraft/lua/rom/modules/main/cc/internal/error_printer.lua");

    @Test
    void theAnnotationRuleHasALineGlyph() {
        assertNotEquals(FixedWidthFontRenderer.getIndex('?'), FixedWidthFontRenderer.getIndex('\u0095'));
        assertEquals(FixedWidthFontRenderer.getIndex('|'), FixedWidthFontRenderer.getIndex('\u0095'));
    }

    @Test
    void theIndicatorBarHasACaretAtTheTopOfItsCell() {
        // 0x83 in CC:Tweaked's font is a solid bar across the top of its cell, so it touches the
        // source line above it. A caret is the closest thing the vanilla sheet draws in that
        // position; an underscore would sit at the bottom of the cell and look detached.
        assertNotEquals(FixedWidthFontRenderer.getIndex('?'), FixedWidthFontRenderer.getIndex('\u0083'));
        assertEquals(FixedWidthFontRenderer.getIndex('^'), FixedWidthFontRenderer.getIndex('\u0083'));
    }

    @Test
    void everyHighCharacterTheErrorPrinterEmitsIsRenderable() throws IOException {
        Set<String> emitted = highCharacters(ERROR_PRINTER);
        assertTrue(!emitted.isEmpty(), "the error printer should still use its annotation glyphs");

        StringBuilder missing = new StringBuilder();
        for (String hex : emitted) {
            char ch = (char) Integer.parseInt(hex, 16);
            if (FixedWidthFontRenderer.getIndex(ch) == FixedWidthFontRenderer.getIndex('?')) {
                if (missing.length() > 0) missing.append(", ");
                missing.append("\\x")
                    .append(hex);
            }
        }

        assertEquals(
            "",
            missing.toString(),
            "these characters come from CC:Tweaked's font and have no glyph here, so they would print as "
                + "'?': add a mapping in FixedWidthFontRenderer.buildCharIndex");
    }

    /** The {@code \xNN} escapes above 0x7F in a ROM file. */
    private static Set<String> highCharacters(Path file) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("\\\\x([0-9a-fA-F]{2})")
            .matcher(source);
        while (m.find()) {
            if (Integer.parseInt(m.group(1), 16) > 0x7F) out.add(
                m.group(1)
                    .toLowerCase());
        }
        return out;
    }
}
