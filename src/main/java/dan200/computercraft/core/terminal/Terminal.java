package dan200.computercraft.core.terminal;

import net.minecraft.nbt.NBTTagCompound;

public class Terminal {

    private static final String base16 = "0123456789abcdef";
    // Default palette hex values (blit index c → Colour.values()[15-c])
    // c=0: white, c=1: orange, ..., c=15: black — matches FixedWidthFontRenderer ordering.
    private static final int[] DEFAULT_PALETTE_HEX = { 0xF0F0F0, // 0 white
        0xF2B233, // 1 orange
        0xE57FD8, // 2 magenta
        0x99B2F2, // 3 lightBlue
        0xDEDE6C, // 4 yellow
        0x7FCC19, // 5 lime
        0xF2B2CC, // 6 pink
        0x4C4C4C, // 7 gray
        0x999999, // 8 lightGray
        0x4C99B2, // 9 cyan
        0xB266E5, // 10 purple
        0x3366CC, // 11 blue
        0x7F664C, // 12 brown
        0x57A64E, // 13 green
        0xCC4C4C, // 14 red
        0x191919, // 15 black
    };

    private int m_cursorX;
    private int m_cursorY;
    private boolean m_cursorBlink;
    private int m_cursorColour;
    private int m_cursorBackgroundColour;
    private int m_width;
    private int m_height;
    private TextBuffer[] m_text;
    private TextBuffer[] m_textColour;
    private TextBuffer[] m_backgroundColour;
    private boolean m_changed;
    private double[][] m_palette;

    public Terminal(int width, int height) {
        this.m_width = width;
        this.m_height = height;
        this.m_cursorColour = 0;
        this.m_cursorBackgroundColour = 15;
        this.m_text = new TextBuffer[this.m_height];
        this.m_textColour = new TextBuffer[this.m_height];
        this.m_backgroundColour = new TextBuffer[this.m_height];

        for (int i = 0; i < this.m_height; i++) {
            this.m_text[i] = new TextBuffer(' ', this.m_width);
            this.m_textColour[i] = new TextBuffer("0123456789abcdef".charAt(this.m_cursorColour), this.m_width);
            this.m_backgroundColour[i] = new TextBuffer(
                "0123456789abcdef".charAt(this.m_cursorBackgroundColour),
                this.m_width);
        }

        this.m_cursorX = 0;
        this.m_cursorY = 0;
        this.m_cursorBlink = false;
        this.m_changed = false;
        this.m_palette = new double[16][3];
        this.resetPalette();
    }

    public void reset() {
        this.m_cursorColour = 0;
        this.m_cursorBackgroundColour = 15;
        this.m_cursorX = 0;
        this.m_cursorY = 0;
        this.m_cursorBlink = false;
        this.resetPalette();
        this.clear();
        this.m_changed = true;
    }

    public int getWidth() {
        return this.m_width;
    }

    public int getHeight() {
        return this.m_height;
    }

    public void resize(int width, int height) {
        if (width != this.m_width || height != this.m_height) {
            int oldHeight = this.m_height;
            int oldWidth = this.m_width;
            TextBuffer[] oldText = this.m_text;
            TextBuffer[] oldTextColour = this.m_textColour;
            TextBuffer[] oldBackgroundColour = this.m_backgroundColour;
            this.m_width = width;
            this.m_height = height;
            this.m_text = new TextBuffer[this.m_height];
            this.m_textColour = new TextBuffer[this.m_height];
            this.m_backgroundColour = new TextBuffer[this.m_height];

            for (int i = 0; i < this.m_height; i++) {
                if (i >= oldHeight) {
                    this.m_text[i] = new TextBuffer(' ', this.m_width);
                    this.m_textColour[i] = new TextBuffer("0123456789abcdef".charAt(this.m_cursorColour), this.m_width);
                    this.m_backgroundColour[i] = new TextBuffer(
                        "0123456789abcdef".charAt(this.m_cursorBackgroundColour),
                        this.m_width);
                } else if (this.m_width == oldWidth) {
                    this.m_text[i] = oldText[i];
                    this.m_textColour[i] = oldTextColour[i];
                    this.m_backgroundColour[i] = oldBackgroundColour[i];
                } else {
                    this.m_text[i] = new TextBuffer(' ', this.m_width);
                    this.m_textColour[i] = new TextBuffer("0123456789abcdef".charAt(this.m_cursorColour), this.m_width);
                    this.m_backgroundColour[i] = new TextBuffer(
                        "0123456789abcdef".charAt(this.m_cursorBackgroundColour),
                        this.m_width);
                    this.m_text[i].write(oldText[i]);
                    this.m_textColour[i].write(oldTextColour[i]);
                    this.m_backgroundColour[i].write(oldBackgroundColour[i]);
                }
            }

            this.m_changed = true;
        }
    }

    public void setCursorPos(int x, int y) {
        if (this.m_cursorX != x || this.m_cursorY != y) {
            this.m_cursorX = x;
            this.m_cursorY = y;
            this.m_changed = true;
        }
    }

    public void setCursorBlink(boolean blink) {
        if (this.m_cursorBlink != blink) {
            this.m_cursorBlink = blink;
            this.m_changed = true;
        }
    }

    public void setTextColour(int colour) {
        if (this.m_cursorColour != colour) {
            this.m_cursorColour = colour;
            this.m_changed = true;
        }
    }

    public void setBackgroundColour(int colour) {
        if (this.m_cursorBackgroundColour != colour) {
            this.m_cursorBackgroundColour = colour;
            this.m_changed = true;
        }
    }

    public int getCursorX() {
        return this.m_cursorX;
    }

    public int getCursorY() {
        return this.m_cursorY;
    }

    public boolean getCursorBlink() {
        return this.m_cursorBlink;
    }

    public int getTextColour() {
        return this.m_cursorColour;
    }

    public int getBackgroundColour() {
        return this.m_cursorBackgroundColour;
    }

    public void blit(String text, String textColour, String backgroundColour) {
        int x = this.m_cursorX;
        int y = this.m_cursorY;
        if (y >= 0 && y < this.m_height) {
            this.m_text[y].write(text, x);
            this.m_textColour[y].write(textColour, x);
            this.m_backgroundColour[y].write(backgroundColour, x);
            this.m_changed = true;
        }
    }

    public void write(String text) {
        int x = this.m_cursorX;
        int y = this.m_cursorY;
        if (y >= 0 && y < this.m_height) {
            this.m_text[y].write(text, x);
            this.m_textColour[y].fill("0123456789abcdef".charAt(this.m_cursorColour), x, x + text.length());
            this.m_backgroundColour[y]
                .fill("0123456789abcdef".charAt(this.m_cursorBackgroundColour), x, x + text.length());
            this.m_changed = true;
        }
    }

    public void scroll(int yDiff) {
        if (yDiff != 0) {
            TextBuffer[] newText = new TextBuffer[this.m_height];
            TextBuffer[] newTextColour = new TextBuffer[this.m_height];
            TextBuffer[] newBackgroundColour = new TextBuffer[this.m_height];

            for (int y = 0; y < this.m_height; y++) {
                int oldY = y + yDiff;
                if (oldY >= 0 && oldY < this.m_height) {
                    newText[y] = this.m_text[oldY];
                    newTextColour[y] = this.m_textColour[oldY];
                    newBackgroundColour[y] = this.m_backgroundColour[oldY];
                } else {
                    newText[y] = new TextBuffer(' ', this.m_width);
                    newTextColour[y] = new TextBuffer("0123456789abcdef".charAt(this.m_cursorColour), this.m_width);
                    newBackgroundColour[y] = new TextBuffer(
                        "0123456789abcdef".charAt(this.m_cursorBackgroundColour),
                        this.m_width);
                }
            }

            this.m_text = newText;
            this.m_textColour = newTextColour;
            this.m_backgroundColour = newBackgroundColour;
            this.m_changed = true;
        }
    }

    public void clear() {
        for (int y = 0; y < this.m_height; y++) {
            this.m_text[y].fill(' ');
            this.m_textColour[y].fill("0123456789abcdef".charAt(this.m_cursorColour));
            this.m_backgroundColour[y].fill("0123456789abcdef".charAt(this.m_cursorBackgroundColour));
        }

        this.m_changed = true;
    }

    public void clearLine() {
        int y = this.m_cursorY;
        if (y >= 0 && y < this.m_height) {
            this.m_text[y].fill(' ');
            this.m_textColour[y].fill("0123456789abcdef".charAt(this.m_cursorColour));
            this.m_backgroundColour[y].fill("0123456789abcdef".charAt(this.m_cursorBackgroundColour));
            this.m_changed = true;
        }
    }

    public TextBuffer getLine(int y) {
        return y >= 0 && y < this.m_height ? this.m_text[y] : null;
    }

    public void setLine(int y, String text, String textColour, String backgroundColour) {
        this.m_text[y].write(text);
        this.m_textColour[y].write(textColour);
        this.m_backgroundColour[y].write(backgroundColour);
        this.m_changed = true;
    }

    public TextBuffer getTextColourLine(int y) {
        return y >= 0 && y < this.m_height ? this.m_textColour[y] : null;
    }

    public TextBuffer getBackgroundColourLine(int y) {
        return y >= 0 && y < this.m_height ? this.m_backgroundColour[y] : null;
    }

    public boolean getChanged() {
        return this.m_changed;
    }

    public void clearChanged() {
        this.m_changed = false;
    }

    // =========================================================================
    // Palette
    // =========================================================================

    private void resetPalette() {
        for (int c = 0; c < 16; c++) {
            int hex = DEFAULT_PALETTE_HEX[c];
            this.m_palette[c][0] = ((hex >> 16) & 0xFF) / 255.0;
            this.m_palette[c][1] = ((hex >> 8) & 0xFF) / 255.0;
            this.m_palette[c][2] = (hex & 0xFF) / 255.0;
        }
        this.m_changed = true;
    }

    /**
     * Sets a palette entry.
     *
     * @param index blit index (0–15)
     * @param r     red component in [0, 1]
     * @param g     green component in [0, 1]
     * @param b     blue component in [0, 1]
     */
    public void setPaletteColour(int index, double r, double g, double b) {
        this.m_palette[index][0] = r;
        this.m_palette[index][1] = g;
        this.m_palette[index][2] = b;
        this.m_changed = true;
    }

    /**
     * Returns a copy of the current palette entry for the given blit index.
     *
     * @param index blit index (0–15)
     * @return {@code {r, g, b}} copy
     */
    public double[] getPaletteColour(int index) {
        return new double[] { this.m_palette[index][0], this.m_palette[index][1], this.m_palette[index][2] };
    }

    /**
     * Returns the factory-default palette entry for a blit index.
     * Always reads from {@link #DEFAULT_PALETTE_HEX} — unaffected by {@link #setPaletteColour}.
     *
     * @param index blit index (0–15)
     * @return {@code {r, g, b}}
     */
    public static double[] getDefaultPaletteColour(int index) {
        int hex = DEFAULT_PALETTE_HEX[index];
        return new double[] { ((hex >> 16) & 0xFF) / 255.0, ((hex >> 8) & 0xFF) / 255.0, (hex & 0xFF) / 255.0 };
    }

    /**
     * Returns a direct reference to the internal palette array (16 × 3).
     * Callers <em>must not</em> mutate the returned array.
     *
     * @return direct palette reference for read-only use by the renderer
     */
    public double[][] getPalette() {
        return this.m_palette;
    }

    public void writeToNBT(NBTTagCompound nbttagcompound) {
        nbttagcompound.setInteger("term_cursorX", this.m_cursorX);
        nbttagcompound.setInteger("term_cursorY", this.m_cursorY);
        nbttagcompound.setBoolean("term_cursorBlink", this.m_cursorBlink);
        nbttagcompound.setInteger("term_textColour", this.m_cursorColour);
        nbttagcompound.setInteger("term_bgColour", this.m_cursorBackgroundColour);

        for (int n = 0; n < this.m_height; n++) {
            nbttagcompound.setString("term_text_" + n, this.m_text[n].toString());
            nbttagcompound.setString("term_textColour_" + n, this.m_textColour[n].toString());
            nbttagcompound.setString("term_textBgColour_" + n, this.m_backgroundColour[n].toString());
        }

        int[] packedPalette = new int[16];
        for (int c = 0; c < 16; c++) {
            int r = (int) Math.round(this.m_palette[c][0] * 255);
            int g = (int) Math.round(this.m_palette[c][1] * 255);
            int b = (int) Math.round(this.m_palette[c][2] * 255);
            packedPalette[c] = (r << 16) | (g << 8) | b;
        }
        nbttagcompound.setIntArray("term_palette", packedPalette);
    }

    public void readFromNBT(NBTTagCompound nbttagcompound) {
        this.m_cursorX = nbttagcompound.getInteger("term_cursorX");
        this.m_cursorY = nbttagcompound.getInteger("term_cursorY");
        this.m_cursorBlink = nbttagcompound.getBoolean("term_cursorBlink");
        this.m_cursorColour = nbttagcompound.getInteger("term_textColour");
        this.m_cursorBackgroundColour = nbttagcompound.getInteger("term_bgColour");

        for (int n = 0; n < this.m_height; n++) {
            this.m_text[n].fill(' ');
            if (nbttagcompound.hasKey("term_text_" + n)) {
                this.m_text[n].write(nbttagcompound.getString("term_text_" + n));
            }

            this.m_textColour[n].fill("0123456789abcdef".charAt(this.m_cursorColour));
            if (nbttagcompound.hasKey("term_textColour_" + n)) {
                this.m_textColour[n].write(nbttagcompound.getString("term_textColour_" + n));
            }

            this.m_backgroundColour[n].fill("0123456789abcdef".charAt(this.m_cursorBackgroundColour));
            if (nbttagcompound.hasKey("term_textBgColour_" + n)) {
                this.m_backgroundColour[n].write(nbttagcompound.getString("term_textBgColour_" + n));
            }
        }

        if (nbttagcompound.hasKey("term_palette")) {
            int[] packed = nbttagcompound.getIntArray("term_palette");
            for (int c = 0; c < Math.min(packed.length, 16); c++) {
                this.m_palette[c][0] = ((packed[c] >> 16) & 0xFF) / 255.0;
                this.m_palette[c][1] = ((packed[c] >> 8) & 0xFF) / 255.0;
                this.m_palette[c][2] = (packed[c] & 0xFF) / 255.0;
            }
        }

        this.m_changed = true;
    }
}
