package dan200.computercraft.client.gui;

import dan200.computercraft.shared.peripheral.printer.ContainerPrinter;
import dan200.computercraft.shared.peripheral.printer.TilePrinter;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class GuiPrinter extends GuiContainer {
   private static final ResourceLocation background = new ResourceLocation("computercraft", "textures/gui/printer.png");
   private TilePrinter m_printer;
   private ContainerPrinter m_container;

   public GuiPrinter(InventoryPlayer inventoryplayer, TilePrinter printer) {
      super(new ContainerPrinter(inventoryplayer, printer));
      this.m_printer = printer;
      this.m_container = (ContainerPrinter)this.inventorySlots;
   }

   protected void drawGuiContainerForegroundLayer(int par1, int par2) {
      String title = this.m_printer.hasCustomInventoryName() ? this.m_printer.getInventoryName() : I18n.format(this.m_printer.getInventoryName(), new Object[0]);
      this.fontRendererObj.drawString(title, (this.xSize - this.fontRendererObj.getStringWidth(title)) / 2, 6, 4210752);
      this.fontRendererObj.drawString(I18n.format("container.inventory", new Object[0]), 8, this.ySize - 96 + 2, 4210752);
   }

   protected void drawGuiContainerBackgroundLayer(float f, int i, int j) {
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      this.mc.getTextureManager().bindTexture(background);
      int startX = (this.width - this.xSize) / 2;
      int startY = (this.height - this.ySize) / 2;
      this.drawTexturedModalRect(startX, startY, 0, 0, this.xSize, this.ySize);
      boolean printing = this.m_container.isPrinting();
      if (printing) {
         this.drawTexturedModalRect(startX + 34, startY + 21, 176, 0, 25, 45);
      }
   }
}
