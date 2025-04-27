package dan200.computercraft.shared.util;

public enum Colour {

    Black(1644825),
    Red(13388876),
    Green(5744206),
    Brown(8349260),
    Blue(3368652),
    Purple(11691749),
    Cyan(5020082),
    LightGrey(10066329),
    Grey(5000268),
    Pink(15905484),
    Lime(8375321),
    Yellow(14605932),
    LightBlue(10072818),
    Magenta(15040472),
    Orange(15905331),
    White(15790320);

    private int m_hex;
    private float[] m_rgb;

    public static Colour fromInt(int colour) {
        return colour >= 0 && colour < 16 ? values()[colour] : null;
    }

    private Colour(int hex) {
        this.m_hex = hex;
        this.m_rgb = new float[] { (hex >> 16 & 0xFF) / 255.0F, (hex >> 8 & 0xFF) / 255.0F, (hex & 0xFF) / 255.0F };
    }

    public Colour getNext() {
        return values()[(this.ordinal() + 1) % 16];
    }

    public Colour getPrevious() {
        return values()[(this.ordinal() + 15) % 16];
    }

    public int getHex() {
        return this.m_hex;
    }

    public float[] getRGB() {
        return this.m_rgb;
    }

    public float getR() {
        return this.m_rgb[0];
    }

    public float getG() {
        return this.m_rgb[1];
    }

    public float getB() {
        return this.m_rgb[2];
    }
}
