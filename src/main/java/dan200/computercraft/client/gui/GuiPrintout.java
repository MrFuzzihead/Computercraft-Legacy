package dan200.computercraft.client.gui;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.core.terminal.TextBuffer;
import dan200.computercraft.shared.media.inventory.ContainerHeldItem;
import dan200.computercraft.shared.media.items.ItemPrintout;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class GuiPrintout extends GuiContainer {
   private static final ResourceLocation background = new ResourceLocation("computercraft", "textures/gui/printout.png");
   private static final int xSize = 172;
   private static final int ySize = 209;
   private final boolean m_book;
   private final int m_pages;
   private final TextBuffer[] m_text;
   private final TextBuffer[] m_colours;
   private int m_page;

   public GuiPrintout(ContainerHeldItem container) {
      super(container);
      this.m_book = ItemPrintout.getType(container.getStack()) == ItemPrintout.Type.Book;
      String[] text = ItemPrintout.getText(container.getStack());
      this.m_text = new TextBuffer[text.length];

      for (int i = 0; i < this.m_text.length; i++) {
         this.m_text[i] = new TextBuffer(text[i]);
      }

      String[] colours = ItemPrintout.getColours(container.getStack());
      this.m_colours = new TextBuffer[colours.length];

      for (int i = 0; i < this.m_colours.length; i++) {
         this.m_colours[i] = new TextBuffer(colours[i]);
      }

      this.m_pages = Math.max(this.m_text.length / 21, 1);
      this.m_page = 0;
   }

   public void func_73866_w_() {
      super.func_73866_w_();
   }

   public void func_146281_b() {
      super.func_146281_b();
   }

   public boolean func_73868_f() {
      return false;
   }

   public void func_73876_c() {
      super.func_73876_c();
   }

   protected void func_73869_a(char c, int k) {
      super.func_73869_a(c, k);
      if (k == 205) {
         if (this.m_page < this.m_pages - 1) {
            this.m_page++;
         }
      } else if (k == 203 && this.m_page > 0) {
         this.m_page--;
      }
   }

   public void func_146274_d() {
      super.func_146274_d();
      int mouseWheelChange = Mouse.getEventDWheel();
      if (mouseWheelChange < 0) {
         if (this.m_page < this.m_pages - 1) {
            this.m_page++;
         }
      } else if (mouseWheelChange > 0 && this.m_page > 0) {
         this.m_page--;
      }
   }

   protected void drawGuiContainerForegroundLayer(int par1, int par2) {
   }

   protected void drawGuiContainerBackgroundLayer(float var1, int var2, int var3) {
   }

   public void func_73863_a(int mouseX, int mouseY, float f) {
      this.func_146276_q_();
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      this.mc.func_110434_K().bindTexture(background);
      int startY = (this.height - 209) / 2;
      int startX = (this.width - (172 + (this.m_pages - 1) * 8)) / 2;
      if (this.m_book) {
         this.drawTexturedModalRect(startX - 8, startY - 8, 220, 0, 12, 233);
         this.drawTexturedModalRect(startX + 172 + (this.m_pages - 1) * 8 - 4, startY - 8, 232, 0, 12, 233);
         this.drawTexturedModalRect(startX, startY - 8, 0, 209, 172, 12);
         this.drawTexturedModalRect(startX, startY + 209 - 4, 0, 221, 172, 12);

         for (int n = 1; n < this.m_pages; n++) {
            this.drawTexturedModalRect(startX + 172 + (n - 1) * 8, startY - 8, 0, 209, 8, 12);
            this.drawTexturedModalRect(startX + 172 + (n - 1) * 8, startY + 209 - 4, 0, 221, 8, 12);
         }
      }

      if (this.m_page == 0) {
         this.drawTexturedModalRect(startX, startY, 24, 0, 86, 209);
         this.drawTexturedModalRect(startX, startY, 0, 0, 12, 209);
      } else {
         this.drawTexturedModalRect(startX, startY, 0, 0, 12, 209);

         for (int n = 1; n < this.m_page; n++) {
            this.drawTexturedModalRect(startX + n * 8, startY, 12, 0, 12, 209);
         }

         this.drawTexturedModalRect(startX + this.m_page * 8, startY, 24, 0, 86, 209);
      }

      if (this.m_page == this.m_pages - 1) {
         this.drawTexturedModalRect(startX + this.m_page * 8 + 86, startY, 110, 0, 86, 209);
         this.drawTexturedModalRect(startX + this.m_page * 8 + 160, startY, 208, 0, 12, 209);
      } else {
         this.drawTexturedModalRect(startX + (this.m_pages - 1) * 8 + 160, startY, 208, 0, 12, 209);

         for (int n = this.m_pages - 2; n >= this.m_page; n--) {
            this.drawTexturedModalRect(startX + n * 8 + 160, startY, 196, 0, 12, 209);
         }

         this.drawTexturedModalRect(startX + this.m_page * 8 + 86, startY, 110, 0, 86, 209);
      }

      FixedWidthFontRenderer fontRenderer = (FixedWidthFontRenderer)ComputerCraft.getFixedWidthFontRenderer();
      int x = startX + this.m_page * 8 + 13;
      int y = startY + 11;

      for (int line = 0; line < 21; line++) {
         int lineIdx = 21 * this.m_page + line;
         if (lineIdx >= 0 && lineIdx < this.m_text.length) {
            fontRenderer.drawString(this.m_text[lineIdx], x, y, this.m_colours[lineIdx], null, 0.0F, 0.0F, false);
         }

         y += FixedWidthFontRenderer.FONT_HEIGHT;
      }
   }
}
