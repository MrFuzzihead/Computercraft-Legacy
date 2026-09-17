package dan200.computercraft.client.gui;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void charIndexMatchesLegacyLinearScanForEveryCharacter() {
        for (int c = 0; c < 65536; c++) {
            char ch = (char) c;
            assertEquals(
                legacyGetIndex(ch),
                FixedWidthFontRenderer.getIndex(ch),
                "Mismatch for code point " + c + " ('" + ch + "')");
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
