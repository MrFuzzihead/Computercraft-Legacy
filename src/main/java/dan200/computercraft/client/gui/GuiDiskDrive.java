package dan200.computercraft.client.gui;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import dan200.computercraft.shared.peripheral.diskdrive.ContainerDiskDrive;
import dan200.computercraft.shared.peripheral.diskdrive.TileDiskDrive;

public class GuiDiskDrive extends GuiContainer {

    private static final ResourceLocation background = new ResourceLocation(
        "computercraft",
        "textures/gui/diskdrive.png");
    private TileDiskDrive m_diskDrive;

    public GuiDiskDrive(InventoryPlayer inventoryplayer, TileDiskDrive diskDrive) {
        super(new ContainerDiskDrive(inventoryplayer, diskDrive));
        this.m_diskDrive = diskDrive;
    }

    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
        String title = this.m_diskDrive.hasCustomInventoryName() ? this.m_diskDrive.getInventoryName()
            : I18n.format(this.m_diskDrive.getInventoryName(), new Object[0]);
        this.fontRendererObj
            .drawString(title, (this.xSize - this.fontRendererObj.getStringWidth(title)) / 2, 6, 4210752);
        this.fontRendererObj
            .drawString(I18n.format("container.inventory", new Object[0]), 8, this.ySize - 96 + 2, 4210752);
    }

    protected void drawGuiContainerBackgroundLayer(float f, int i, int j) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager()
            .bindTexture(background);
        int l = (this.width - this.xSize) / 2;
        int i1 = (this.height - this.ySize) / 2;
        this.drawTexturedModalRect(l, i1, 0, 0, this.xSize, this.ySize);
    }
}
