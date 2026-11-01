package dan200.computercraft.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FixedWidthFontRendererCharIndexTest {

    private static final String LEGACY_CHARS = "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000";

    /** The exact pre-optimization lookup, kept here as the behavioral specification. */
    private static int legacyGetIndex(char ch) {
        int index = LEGACY_CHARS.indexOf(ch);
        if (index < 0) {
            if (ch != '\t' && ch != '\r' && ch != '\n') {
                index = LEGACY_CHARS.indexOf('?');
            } else {
                index = LEGACY_CHARS.indexOf(' ');
            }
        }
        return index;
    }

    /**
     * The two code points where the index table deliberately deviates from the legacy scan.
     *
     * <p>
     * They are glyphs from CC:Tweaked's own terminal font that the vanilla sheet has no slot for,
     * and the ROM's error printer draws with them. They used to fall back to {@code ?}, which made
     * an annotated error print as {@code ?Line 1}. {@link FixedWidthFontRendererGlyphTest} covers
     * what they map to and why.
     * </p>
     */
    private static final char[] FONT_GLYPH_SUBSTITUTES = { 0x83, 0x95 };

    private static boolean isSubstituted(char ch) {
        for (char c : FONT_GLYPH_SUBSTITUTES) {
            if (c == ch) return true;
        }
        return false;
    }

    @Test
    void charIndexMatchesLegacyLinearScanForEveryCharacter() {
        for (int c = 0; c < 65536; c++) {
            char ch = (char) c;
            if (isSubstituted(ch)) continue;
            assertEquals(
                legacyGetIndex(ch),
                FixedWidthFontRenderer.getIndex(ch),
                "Mismatch for code point " + c + " ('" + ch + "')");
        }
    }

    @Test
    void theSubstitutedFontGlyphsNoLongerFallBackToQuestionMarks() {
        for (char ch : FONT_GLYPH_SUBSTITUTES) {
            assertEquals(
                false,
                FixedWidthFontRenderer.getIndex(ch) == legacyGetIndex(ch),
                "0x" + Integer.toHexString(ch) + " should map to a real glyph now, not the '?' fallback");
        }
    }

    @Test
    void duplicateCharactersAndFallbacksKeepTheirOriginalGlyph() {
        assertEquals(LEGACY_CHARS.indexOf('\u0000'), FixedWidthFontRenderer.getIndex('\u0000'));
        assertEquals(LEGACY_CHARS.indexOf('?'), FixedWidthFontRenderer.getIndex('★'));
        assertEquals(LEGACY_CHARS.indexOf(' '), FixedWidthFontRenderer.getIndex('\t'));
        assertEquals(LEGACY_CHARS.indexOf(' '), FixedWidthFontRenderer.getIndex('\r'));
        assertEquals(LEGACY_CHARS.indexOf(' '), FixedWidthFontRenderer.getIndex('\n'));
    }
}
