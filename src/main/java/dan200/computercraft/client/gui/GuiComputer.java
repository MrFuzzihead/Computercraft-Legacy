package dan200.computercraft.client.gui;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import dan200.computercraft.client.gui.widgets.WidgetTerminal;
import dan200.computercraft.shared.computer.blocks.TileComputer;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.core.IComputer;
import dan200.computercraft.shared.computer.core.IComputerContainer;
import dan200.computercraft.shared.computer.inventory.ContainerComputer;

public class GuiComputer extends GuiContainer {

    private static final ResourceLocation background = new ResourceLocation(
        "computercraft",
        "textures/gui/corners.png");
    private static final ResourceLocation backgroundAdvanced = new ResourceLocation(
        "computercraft",
        "textures/gui/corners2.png");
    private static final ResourceLocation backgroundCommand = new ResourceLocation(
        "computercraft",
        "textures/gui/cornersCommand.png");
    private final ComputerFamily m_family;
    private final IComputer m_computer;
    private final int m_termWidth;
    private final int m_termHeight;
    private WidgetTerminal m_terminal;

    protected GuiComputer(Container container, ComputerFamily family, IComputer computer, int termWidth,
        int termHeight) {
        super(container);
        this.m_family = family;
        this.m_computer = computer;
        this.m_termWidth = termWidth;
        this.m_termHeight = termHeight;
        this.m_terminal = null;
    }

    public GuiComputer(TileComputer computer) {
        this(new ContainerComputer(computer), computer.getFamily(), computer.createComputer(), 51, 19);
    }

    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        this.m_terminal = new WidgetTerminal(0, 0, this.m_termWidth, this.m_termHeight, new IComputerContainer() {

            @Override
            public IComputer getComputer() {
                return GuiComputer.this.m_computer;
            }
        });
        this.m_terminal.setAllowFocusLoss(false);
        this.xSize = this.m_terminal.getWidth() + 24;
        this.ySize = this.m_terminal.getHeight() + 24;
    }

    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    public boolean doesGuiPauseGame() {
        return false;
    }

    public void updateScreen() {
        super.updateScreen();
        this.m_terminal.update();
    }

    protected void keyTyped(char c, int k) {
        if (k == 1) {
            super.keyTyped(c, k);
        } else {
            this.m_terminal.keyTyped(c, k);
        }
    }

    protected void mouseClicked(int x, int y, int button) {
        int startX = (this.width - this.m_terminal.getWidth()) / 2;
        int startY = (this.height - this.m_terminal.getHeight()) / 2;
        this.m_terminal.mouseClicked(x - startX, y - startY, button);
    }

    public void handleMouseInput() {
        super.handleMouseInput();
        int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        int startX = (this.width - this.m_terminal.getWidth()) / 2;
        int startY = (this.height - this.m_terminal.getHeight()) / 2;
        this.m_terminal.handleMouseInput(x - startX, y - startY);
    }

    public void handleKeyboardInput() {
        super.handleKeyboardInput();
        this.m_terminal.handleKeyboardInput();
    }

    protected void drawGuiContainerForegroundLayer(int par1, int par2) {}

    protected void drawGuiContainerBackgroundLayer(float var1, int var2, int var3) {}

    public void drawScreen(int mouseX, int mouseY, float f) {
        int startX = (this.width - this.m_terminal.getWidth()) / 2;
        int startY = (this.height - this.m_terminal.getHeight()) / 2;
        int endX = startX + this.m_terminal.getWidth();
        int endY = startY + this.m_terminal.getHeight();
        this.drawDefaultBackground();
        this.m_terminal.draw(this.mc, startX, startY, mouseX, mouseY);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        switch (this.m_family) {
            case Normal:
            default:
                this.mc.getTextureManager()
                    .bindTexture(background);
                break;
            case Advanced:
                this.mc.getTextureManager()
                    .bindTexture(backgroundAdvanced);
                break;
            case Command:
                this.mc.getTextureManager()
                    .bindTexture(backgroundCommand);
        }

        this.drawTexturedModalRect(startX - 12, startY - 12, 12, 28, 12, 12);
        this.drawTexturedModalRect(startX - 12, endY, 12, 40, 12, 16);
        this.drawTexturedModalRect(endX, startY - 12, 24, 28, 12, 12);
        this.drawTexturedModalRect(endX, endY, 24, 40, 12, 16);
        this.drawTexturedModalRect(startX, startY - 12, 0, 0, endX - startX, 12);
        this.drawTexturedModalRect(startX, endY, 0, 12, endX - startX, 16);
        this.drawTexturedModalRect(startX - 12, startY, 0, 28, 12, endY - startY);
        this.drawTexturedModalRect(endX, startY, 36, 28, 12, endY - startY);
    }
}
