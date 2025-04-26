package dan200.computercraft.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public abstract class Widget extends Gui {
   protected static RenderItem s_renderItem = new RenderItem();
   private WidgetContainer m_parent = null;
   private boolean m_visible = true;
   private int m_xPosition;
   private int m_yPosition;
   private int m_width;
   private int m_height;

   protected Widget(int x, int y, int width, int height) {
      this.m_xPosition = x;
      this.m_yPosition = y;
      this.m_width = width;
      this.m_height = height;
   }

   public WidgetContainer getRoot() {
      if (this.m_parent != null) {
         return this.m_parent.getRoot();
      } else {
         return this instanceof WidgetContainer ? (WidgetContainer)this : null;
      }
   }

   public WidgetContainer getParent() {
      return this.m_parent;
   }

   public void setParent(WidgetContainer parent) {
      this.m_parent = parent;
   }

   public boolean isObscured() {
      if (this.m_parent != null) {
         Widget parentModalWidget = this.m_parent.getModalWidget();
         return parentModalWidget == null ? this.m_parent.isObscured() : parentModalWidget != this;
      } else {
         return false;
      }
   }

   public boolean isVisible() {
      return this.m_visible && (this.m_parent == null || this.m_parent.isVisible());
   }

   public void setVisible(boolean visible) {
      this.m_visible = visible;
   }

   public int getXPosition() {
      return this.m_xPosition;
   }

   public int getYPosition() {
      return this.m_yPosition;
   }

   public int getAbsoluteXPosition() {
      return this.m_xPosition + (this.m_parent != null ? this.m_parent.getAbsoluteXPosition() : 0);
   }

   public int getAbsoluteYPosition() {
      return this.m_yPosition + (this.m_parent != null ? this.m_parent.getAbsoluteYPosition() : 0);
   }

   public int getWidth() {
      return this.m_width;
   }

   public int getHeight() {
      return this.m_height;
   }

   public void setPosition(int x, int y) {
      this.m_xPosition = x;
      this.m_yPosition = y;
   }

   public void resize(int width, int height) {
      this.m_width = width;
      this.m_height = height;
   }

   public void update() {
   }

   public void draw(Minecraft mc, int xOrigin, int yOrigin, int mouseX, int mouseY) {
   }

   public void drawForeground(Minecraft mc, int xOrigin, int yOrigin, int mouseX, int mouseY) {
   }

   public void modifyMousePosition(MousePos pos) {
   }

   public void handleMouseInput(int mouseX, int mouseY) {
   }

   public void handleKeyboardInput() {
   }

   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
   }

   public void keyTyped(char c, int k) {
   }

   public boolean suppressItemTooltips(Minecraft mc, int xOrigin, int yOrigin, int mouseX, int mouseY) {
      return false;
   }

   public boolean suppressKeyPress(char c, int k) {
      return false;
   }

   protected void drawFullImage(int x, int y, int w, int h) {
      Tessellator tessellator = Tessellator.field_78398_a;
      tessellator.func_78382_b();
      tessellator.addVertexWithUV(x + 0, y + h, this.field_73735_i, 0.0, 1.0);
      tessellator.addVertexWithUV(x + w, y + h, this.field_73735_i, 1.0, 1.0);
      tessellator.addVertexWithUV(x + w, y + 0, this.field_73735_i, 1.0, 0.0);
      tessellator.addVertexWithUV(x + 0, y + 0, this.field_73735_i, 0.0, 0.0);
      tessellator.func_78381_a();
   }

   protected void drawT3(int x, int y, int w, int h, int u, int v, int uw, int vh) {
      int partW = uw / 3;
      super.drawTexturedModalRect(x, y, u, v, partW, vh);
      int middleBits = Math.max((w - 2 * partW) / partW, 0);

      for (int j = 0; j < middleBits; j++) {
         super.drawTexturedModalRect(x + (j + 1) * partW, y, u + partW, v, partW, vh);
      }

      int endW = w - (middleBits + 1) * partW;
      super.drawTexturedModalRect(x + w - endW, y, u + uw - endW, v, endW, vh);
   }

   protected void drawT9(int x, int y, int w, int h, int u, int v, int uw, int vh) {
      int partH = vh / 3;
      this.drawT3(x, y, w, partH, u, v, uw, partH);
      int middleBits = Math.max((h - 2 * partH) / partH, 0);

      for (int j = 0; j < middleBits; j++) {
         this.drawT3(x, y + (j + 1) * partH, w, partH, u, v + partH, uw, partH);
      }

      int endH = h - (middleBits + 1) * partH;
      this.drawT3(x, y + h - endH, w, endH, u, v + vh - endH, uw, endH);
   }

   protected void drawInsetBorder(int x, int y, int w, int h) {
      this.func_73728_b(x, y - 1, y + h - 1, -13224394);
      this.func_73728_b(x + w - 1, y, y + h, -1);
      this.func_73730_a(x, x + w - 2, y, -13224394);
      this.func_73730_a(x + 1, x + w - 1, y + h - 1, -1);
      this.func_73730_a(x, x, y + h - 1, -7697782);
      this.func_73730_a(x + w - 1, x + w - 1, y, -7697782);
   }

   protected void drawFlatBorder(int x, int y, int w, int h, int colour) {
      colour |= -16777216;
      this.func_73728_b(x, y - 1, y + h - 1, colour);
      this.func_73728_b(x + w - 1, y, y + h, colour);
      this.func_73730_a(x, x + w - 2, y, colour);
      this.func_73730_a(x + 1, x + w - 1, y + h - 1, colour);
      this.func_73730_a(x, x, y + h - 1, colour);
      this.func_73730_a(x + w - 1, x + w - 1, y, colour);
   }

   protected void drawTooltip(String line, int x, int y) {
      this.drawTooltip(new String[]{line}, x, y);
   }

   protected void drawTooltip(String[] lines, int x, int y) {
      Minecraft mc = Minecraft.getMinecraft();
      FontRenderer fontRenderer = mc.field_71466_p;
      int width = 0;

      for (int i = 0; i < lines.length; i++) {
         String line = lines[i];
         width = Math.max(fontRenderer.getStringWidth(line), width);
      }

      int startX = x + 12;
      int startY = y - 12;
      if (startX + width + 4 > mc.field_71462_r.width) {
         startX -= width + 24;
         if (startX < 24) {
            startX = 24;
         }
      }

      float oldZLevel = this.field_73735_i;
      this.field_73735_i = 300.0F;
      int height = 10 * lines.length - 2;
      int j1 = -267386864;
      this.func_73733_a(startX - 3, startY - 4, startX + width + 3, startY - 3, j1, j1);
      this.func_73733_a(startX - 3, startY + height + 3, startX + width + 3, startY + height + 4, j1, j1);
      this.func_73733_a(startX - 3, startY - 3, startX + width + 3, startY + height + 3, j1, j1);
      this.func_73733_a(startX - 4, startY - 3, startX - 3, startY + height + 3, j1, j1);
      this.func_73733_a(startX + width + 3, startY - 3, startX + width + 4, startY + height + 3, j1, j1);
      int k1 = 1347420415;
      int l1 = (k1 & 16711422) >> 1 | k1 & 0xFF000000;
      this.func_73733_a(startX - 3, startY - 3 + 1, startX - 3 + 1, startY + height + 3 - 1, k1, l1);
      this.func_73733_a(startX + width + 2, startY - 3 + 1, startX + width + 3, startY + height + 3 - 1, k1, l1);
      this.func_73733_a(startX - 3, startY - 3, startX + width + 3, startY - 3 + 1, k1, k1);
      this.func_73733_a(startX - 3, startY + height + 2, startX + width + 3, startY + height + 3, l1, l1);
      GL11.glDisable(2929);

      for (int i = 0; i < lines.length; i++) {
         String line = lines[i];
         fontRenderer.func_78261_a(line, startX, startY + i * 10, -1);
      }

      GL11.glEnable(2929);
      this.field_73735_i = oldZLevel;
   }

   protected void drawItemStack(int x, int y, ItemStack stack) {
      if (stack != null) {
         GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
         GL11.glEnable(3042);
         GL11.glEnable(2896);
         GL11.glEnable(32826);
         Minecraft mc = Minecraft.getMinecraft();
         RenderHelper.func_74520_c();
         OpenGlHelper.func_77475_a(OpenGlHelper.field_77476_b, 240.0F, 240.0F);
         s_renderItem.func_82406_b(mc.field_71466_p, mc.func_110434_K(), stack, x, y);
         s_renderItem.func_77021_b(mc.field_71466_p, mc.func_110434_K(), stack, x, y);
         RenderHelper.func_74518_a();
         GL11.glDisable(32826);
         GL11.glDisable(2896);
         GL11.glEnable(3042);
         GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      }
   }

   protected void playClickSound() {
      Minecraft mc = Minecraft.getMinecraft();
      mc.func_147118_V().func_147682_a(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
   }
}
