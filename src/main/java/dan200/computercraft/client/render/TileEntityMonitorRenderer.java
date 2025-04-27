package dan200.computercraft.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.client.gui.FixedWidthFontRenderer;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.core.terminal.TextBuffer;
import dan200.computercraft.shared.common.ClientTerminal;
import dan200.computercraft.shared.peripheral.monitor.TileMonitor;

public class TileEntityMonitorRenderer extends TileEntitySpecialRenderer {

    private static final ResourceLocation grey = new ResourceLocation("computercraft", "textures/gui/termgrey.png");
    private static final ResourceLocation black = new ResourceLocation("computercraft", "textures/gui/terminal.png");

    public void renderTileEntityMonitorAt(TileMonitor monitor, double xPos, double yPos, double zPos, float f) {
        boolean redraw = monitor.pollChanged();
        boolean origin = monitor.getXIndex() == 0 && monitor.getYIndex() == 0;
        if (!origin) {
            if (monitor.m_renderDisplayList >= 0) {
                GL11.glDeleteLists(monitor.m_renderDisplayList, 2);
                monitor.m_renderDisplayList = -1;
            }
        } else {
            ClientTerminal clientTerminal = (ClientTerminal) monitor.getTerminal();
            redraw = redraw || clientTerminal != null && clientTerminal.hasTerminalChanged();
            if (monitor.m_renderDisplayList < 0) {
                monitor.m_renderDisplayList = GL11.glGenLists(2);
                redraw = true;
            }

            int dir = monitor.getDir() % 6;
            int dirAngle = monitor.getRenderFace();
            float angle = 0.0F;
            float rot = 0.0F;
            switch (dir) {
                case 2:
                    rot = 180.0F;
                    break;
                case 3:
                    rot = 0.0F;
                    break;
                case 4:
                    rot = 90.0F;
                    break;
                case 5:
                    rot = 270.0F;
            }

            switch (dirAngle) {
                case 0:
                    angle = 270.0F;
                    break;
                case 1:
                    angle = 90.0F;
            }

            GL11.glPushMatrix();
            GL11.glTranslatef((float) xPos + 0.5F, (float) yPos + 0.5F, (float) zPos + 0.5F);
            GL11.glRotatef(-rot, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(-angle, 1.0F, 0.0F, 0.0F);
            GL11.glTranslatef(-0.34375F, monitor.getHeight() - 0.5F - 0.15625F, 0.5F);
            float xSize = monitor.getWidth() - 0.3125F;
            float ySize = monitor.getHeight() - 0.3125F;
            Tessellator tessellator = Tessellator.instance;
            GL11.glDepthMask(false);
            Terminal terminal = clientTerminal != null ? clientTerminal.getTerminal() : null;
            if (terminal != null) {
                boolean greyscale = !clientTerminal.isColour();
                float xScale = xSize / (terminal.getWidth() * FixedWidthFontRenderer.FONT_WIDTH);
                float yScale = ySize / (terminal.getHeight() * FixedWidthFontRenderer.FONT_HEIGHT);
                GL11.glPushMatrix();
                GL11.glScalef(xScale, -yScale, 1.0F);
                FixedWidthFontRenderer fontRenderer = (FixedWidthFontRenderer) ComputerCraft
                    .getFixedWidthFontRenderer();
                int width = terminal.getWidth();
                int height = terminal.getHeight();
                if (redraw) {
                    int cursorX = terminal.getCursorX();
                    int cursorY = terminal.getCursorY();
                    TextBuffer emptyLine = new TextBuffer(' ', width);
                    GL11.glNewList(monitor.m_renderDisplayList, 4864);
                    float marginXSize = 0.03125F / xScale;
                    float marginYSize = 0.03125F / yScale;
                    float marginSquash = marginYSize / FixedWidthFontRenderer.FONT_HEIGHT;
                    GL11.glPushMatrix();
                    GL11.glScalef(1.0F, marginSquash, 1.0F);
                    GL11.glTranslatef(0.0F, -marginYSize / marginSquash, 0.0F);
                    fontRenderer.drawString(
                        emptyLine,
                        0,
                        0,
                        terminal.getTextColourLine(0),
                        terminal.getBackgroundColourLine(0),
                        marginXSize,
                        marginXSize,
                        greyscale);
                    GL11.glTranslatef(
                        0.0F,
                        (marginYSize + height * FixedWidthFontRenderer.FONT_HEIGHT) / marginSquash,
                        0.0F);
                    fontRenderer.drawString(
                        emptyLine,
                        0,
                        0,
                        terminal.getTextColourLine(height - 1),
                        terminal.getBackgroundColourLine(height - 1),
                        marginXSize,
                        marginXSize,
                        greyscale);
                    GL11.glPopMatrix();

                    for (int y = 0; y < height; y++) {
                        fontRenderer.drawString(
                            terminal.getLine(y),
                            0,
                            FixedWidthFontRenderer.FONT_HEIGHT * y,
                            terminal.getTextColourLine(y),
                            terminal.getBackgroundColourLine(y),
                            marginXSize,
                            marginXSize,
                            greyscale);
                    }

                    GL11.glEndList();
                    GL11.glNewList(monitor.m_renderDisplayList + 1, 4864);
                    if (terminal.getCursorBlink() && cursorX >= 0
                        && cursorX < width
                        && cursorY >= 0
                        && cursorY < height) {
                        TextBuffer cursor = new TextBuffer("_");
                        TextBuffer cursorColour = new TextBuffer(
                            "0123456789abcdef".charAt(terminal.getTextColour()),
                            1);
                        fontRenderer.drawString(
                            cursor,
                            FixedWidthFontRenderer.FONT_WIDTH * cursorX,
                            FixedWidthFontRenderer.FONT_HEIGHT * cursorY,
                            cursorColour,
                            null,
                            0.0F,
                            0.0F,
                            greyscale);
                    }

                    GL11.glEndList();
                }

                GL11.glBlendFunc(1, 0);
                GL11.glDisable(2896);
                GL11.glDepthMask(false);
                GL11.glCallList(monitor.m_renderDisplayList);
                if (ComputerCraft.getGlobalCursorBlink()) {
                    GL11.glCallList(monitor.m_renderDisplayList + 1);
                }

                GL11.glPopMatrix();
            } else {
                Minecraft mc = Minecraft.getMinecraft();
                mc.getTextureManager()
                    .bindTexture(grey);
                tessellator.startDrawingQuads();
                tessellator.setNormal(0.0F, 0.0F, 1.0F);
                tessellator.addVertexWithUV(-0.03125, -ySize - 0.03125F, 0.0, 0.0, 1.0);
                tessellator.addVertexWithUV(xSize + 0.03125F, -ySize - 0.03125F, 0.0, 1.0, 1.0);
                tessellator.addVertexWithUV(xSize + 0.03125F, 0.03125, 0.0, 1.0, 0.0);
                tessellator.addVertexWithUV(-0.03125, 0.03125, 0.0, 0.0, 0.0);
                tessellator.draw();
            }

            GL11.glDepthMask(true);
            GL11.glColorMask(false, false, false, false);
            GL11.glDisable(3008);
            Minecraft mc = Minecraft.getMinecraft();
            mc.getTextureManager()
                .bindTexture(black);
            tessellator.startDrawingQuads();
            tessellator.setNormal(0.0F, 0.0F, 1.0F);
            tessellator.addVertexWithUV(-0.03125, -ySize - 0.03125F, 0.0, 0.0, 1.0);
            tessellator.addVertexWithUV(xSize + 0.03125F, -ySize - 0.03125F, 0.0, 1.0, 1.0);
            tessellator.addVertexWithUV(xSize + 0.03125F, 0.03125, 0.0, 1.0, 0.0);
            tessellator.addVertexWithUV(-0.03125, 0.03125, 0.0, 0.0, 0.0);
            tessellator.draw();
            GL11.glDepthMask(true);
            GL11.glColorMask(true, true, true, true);
            GL11.glEnable(3008);
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(2896);
            GL11.glPopMatrix();
        }
    }

    public void renderTileEntityAt(TileEntity tileentity, double d, double d1, double d2, float f) {
        this.renderTileEntityMonitorAt((TileMonitor) tileentity, d, d1, d2, f);
    }
}
