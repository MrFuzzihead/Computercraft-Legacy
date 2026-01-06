package dan200.computercraft.client.gui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.IntBuffer;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import dan200.computercraft.core.terminal.TextBuffer;
import dan200.computercraft.shared.util.Colour;

public class FixedWidthFontRenderer implements IResourceManagerReloadListener {

    private static final ResourceLocation font = new ResourceLocation("minecraft", "textures/font/ascii.png");
    private static final ResourceLocation background = new ResourceLocation(
        "computercraft",
        "textures/gui/termwhite.png");
    public static int FONT_HEIGHT = 9;
    public static int FONT_WIDTH = 6;
    private final TextureManager m_textureManager;
    private final IntBuffer m_drawBuffer;
    private int m_firstDisplayList;

    public FixedWidthFontRenderer(TextureManager textureManager) {
        this.m_textureManager = textureManager;
        this.m_drawBuffer = GLAllocation.createDirectIntBuffer(1024);
        this.m_firstDisplayList = -1;
        this.reloadFont();
    }

    public void onResourceManagerReload(IResourceManager resourceManager) {
        this.reloadFont();
    }

    private void reloadFont() {
        if (this.m_firstDisplayList >= 0) {
            GLAllocation.deleteDisplayLists(this.m_firstDisplayList);
            this.m_firstDisplayList = -1;
        }

        double[] charWidths = new double[256];

        BufferedImage bufferedimage;
        try {
            bufferedimage = ImageIO.read(
                Minecraft.getMinecraft()
                    .getResourceManager()
                    .getResource(font)
                    .getInputStream());
        } catch (IOException var19) {
            throw new RuntimeException(var19);
        }

        int imageWidth = bufferedimage.getWidth();
        int imageHeight = bufferedimage.getHeight();
        int fullCharWidth = imageWidth / 16;
        int fullCharHeight = imageHeight / 16;
        int[] pixels = new int[imageWidth * imageHeight];
        bufferedimage.getRGB(0, 0, imageWidth, imageHeight, pixels, 0, imageWidth);

        for (int ascii = 0; ascii < 256; ascii++) {
            int column = ascii % 16;
            int row = ascii / 16;
            double charWidth = 0.0;

            for (int charX = fullCharWidth - 1; charX >= 0; charX--) {
                int x = column * fullCharWidth + charX;
                boolean columnEmpty = true;

                for (int charY = 0; charY < fullCharHeight; charY++) {
                    int y = row * fullCharHeight + charY;
                    int pixel = pixels[x + y * imageWidth];
                    if ((pixel >> 24 & 0xFF) > 0) {
                        columnEmpty = false;
                        break;
                    }
                }

                if (!columnEmpty) {
                    charWidth = (double) (charX + 1) / fullCharWidth;
                    break;
                }
            }

            if (ascii == 32) {
                charWidths[ascii] = 0.0;
            } else {
                charWidths[ascii] = charWidth;
            }
        }

        this.m_firstDisplayList = GLAllocation.generateDisplayLists(274);
        Tessellator tessellator = Tessellator.instance;

        for (int ascii = 0; ascii < 256; ascii++) {
            float charWidth = (float) Math.ceil(8.0 * charWidths[ascii]);
            float startSpace = (float) Math.floor((FONT_WIDTH - 8.0 * charWidths[ascii]) * 0.5);
            GL11.glNewList(this.m_firstDisplayList + ascii, 4864);
            GL11.glTranslatef(startSpace, 0.0F, 0.0F);
            if (charWidth > 0.0F) {
                tessellator.startDrawingQuads();
                int column = ascii % 16;
                int row = ascii / 16;
                float uvCharWidth = charWidth / 8.0F;
                tessellator.addVertexWithUV(0.0, 8.0, 0.0, column / 16.0F, (row + 1) / 16.0F);
                tessellator.addVertexWithUV(charWidth, 8.0, 0.0, (column + uvCharWidth) / 16.0F, (row + 1) / 16.0F);
                tessellator.addVertexWithUV(charWidth, 0.0, 0.0, (column + uvCharWidth) / 16.0F, row / 16.0F);
                tessellator.addVertexWithUV(0.0, 0.0, 0.0, column / 16.0F, row / 16.0F);
                tessellator.draw();
            }

            GL11.glTranslatef(FONT_WIDTH - startSpace, 0.0F, 0.0F);
            GL11.glEndList();
        }

        for (int c = 0; c < 16; c++) {
            Colour colour = Colour.values()[15 - c];
            GL11.glNewList(this.m_firstDisplayList + 256 + c, 4864);
            GL11.glColor3f(colour.getR(), colour.getG(), colour.getB());
            GL11.glEndList();
        }

        GL11.glNewList(this.m_firstDisplayList + 256 + 16, 4864);
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(0.0, FONT_HEIGHT, 0.0, 0.0, 1.0);
        tessellator.addVertexWithUV(FONT_WIDTH, FONT_HEIGHT, 0.0, 1.0, 1.0);
        tessellator.addVertexWithUV(FONT_WIDTH, 0.0, 0.0, 1.0, 0.0);
        tessellator.addVertexWithUV(0.0, 0.0, 0.0, 0.0, 0.0);
        tessellator.draw();
        GL11.glTranslatef(FONT_WIDTH, 0.0F, 0.0F);
        GL11.glEndList();
        GL11.glNewList(this.m_firstDisplayList + 256 + 17, 4864);
        GL11.glTranslatef(FONT_WIDTH, 0.0F, 0.0F);
        GL11.glEndList();
    }

    private boolean isGreyScale(int colour) {
        return !(colour != 0 && colour != 15 && colour != 7 && colour != 8);
    }

    public void drawString(TextBuffer s, int x, int y, TextBuffer textColour, TextBuffer backgroundColour,
        float leftMarginSize, float rightMarginSize, boolean greyScale) {
        if (s != null) {
            boolean hasBackgrounds = backgroundColour != null;
            if (hasBackgrounds) {
                this.m_textureManager.bindTexture(background);
                int lastColour = -1;
                if (leftMarginSize > 0.0F) {
                    float marginSquish = leftMarginSize / FONT_WIDTH;
                    int colour1 = "0123456789abcdef".indexOf(backgroundColour.charAt(0));
                    if (colour1 < 0 || greyScale && !this.isGreyScale(colour1)) {
                        colour1 = 15;
                    }

                    GL11.glPushMatrix();
                    GL11.glScalef(marginSquish, 1.0F, 1.0F);
                    GL11.glTranslatef((x - leftMarginSize) / marginSquish, y, 0.0F);
                    GL11.glCallList(this.m_firstDisplayList + 256 + colour1);
                    GL11.glCallList(this.m_firstDisplayList + 256 + 16);
                    GL11.glPopMatrix();
                }

                if (rightMarginSize > 0.0F) {
                    float marginSquish = rightMarginSize / FONT_WIDTH;
                    int colour2 = "0123456789abcdef".indexOf(backgroundColour.charAt(s.length() - 1));
                    if (colour2 < 0 || greyScale && !this.isGreyScale(colour2)) {
                        colour2 = 15;
                    }

                    GL11.glPushMatrix();
                    GL11.glScalef(marginSquish, 1.0F, 1.0F);
                    GL11.glTranslatef((x + s.length() * FONT_WIDTH) / marginSquish, y, 0.0F);
                    GL11.glCallList(this.m_firstDisplayList + 256 + colour2);
                    GL11.glCallList(this.m_firstDisplayList + 256 + 16);
                    GL11.glPopMatrix();
                }

                GL11.glPushMatrix();
                GL11.glTranslatef(x, y, 0.0F);
                ((Buffer) this.m_drawBuffer).clear();

                for (int i = 0; i < s.length(); i++) {
                    int colour = "0123456789abcdef".indexOf(backgroundColour.charAt(i));
                    if (colour < 0 || greyScale && !this.isGreyScale(colour)) {
                        colour = 15;
                    }

                    if (colour != lastColour) {
                        this.m_drawBuffer.put(this.m_firstDisplayList + 256 + colour);
                        if (this.m_drawBuffer.remaining() == 0) {
                            ((Buffer) this.m_drawBuffer).flip();
                            GL11.glCallLists(this.m_drawBuffer);
                            ((Buffer) this.m_drawBuffer).clear();
                        }

                        lastColour = colour;
                    }

                    this.m_drawBuffer.put(this.m_firstDisplayList + 256 + 16);
                    if (this.m_drawBuffer.remaining() == 0) {
                        ((Buffer) this.m_drawBuffer).flip();
                        GL11.glCallLists(this.m_drawBuffer);
                        ((Buffer) this.m_drawBuffer).clear();
                    }
                }

                ((Buffer) this.m_drawBuffer).flip();
                GL11.glCallLists(this.m_drawBuffer);
                GL11.glPopMatrix();
            }

            this.m_textureManager.bindTexture(font);
            int lastColourx = -1;
            GL11.glPushMatrix();
            GL11.glTranslatef(x, y, 0.0F);
            ((Buffer) this.m_drawBuffer).clear();

            for (int i = 0; i < s.length(); i++) {
                int colourx = "0123456789abcdef".indexOf(textColour.charAt(i));
                if (colourx < 0 || greyScale && !this.isGreyScale(colourx)) {
                    colourx = 0;
                }

                if (colourx != lastColourx) {
                    this.m_drawBuffer.put(this.m_firstDisplayList + 256 + colourx);
                    if (this.m_drawBuffer.remaining() == 0) {
                        ((Buffer) this.m_drawBuffer).flip();
                        GL11.glCallLists(this.m_drawBuffer);
                        ((Buffer) this.m_drawBuffer).clear();
                    }

                    lastColourx = colourx;
                }

                char ch = s.charAt(i);
                int index = getIndex(ch);

                this.m_drawBuffer.put(this.m_firstDisplayList + index);
                if (this.m_drawBuffer.remaining() == 0) {
                    ((Buffer) this.m_drawBuffer).flip();
                    GL11.glCallLists(this.m_drawBuffer);
                    ((Buffer) this.m_drawBuffer).clear();
                }
            }

            ((Buffer) this.m_drawBuffer).flip();
            GL11.glCallLists(this.m_drawBuffer);
            GL11.glPopMatrix();
        }
    }

    private static int getIndex(char ch) {
        String defaultChars = "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000";
        int index = defaultChars.indexOf(ch);
        if (index < 0) {
            if (ch != '\t' && ch != '\r' && ch != '\n') {
                index = defaultChars.indexOf(63);
            } else {
                index = defaultChars.indexOf(32);
            }
        }
        return index;
    }

    public int getStringWidth(String s) {
        return s == null ? 0 : s.length() * FONT_WIDTH;
    }
}
