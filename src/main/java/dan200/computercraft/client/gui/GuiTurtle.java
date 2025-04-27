package dan200.computercraft.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.client.gui.widgets.WidgetTerminal;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.core.IComputer;
import dan200.computercraft.shared.computer.core.IComputerContainer;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;
import dan200.computercraft.shared.turtle.inventory.ContainerTurtle;

public class GuiTurtle extends GuiContainer {

    private static final ResourceLocation background = new ResourceLocation("computercraft", "textures/gui/turtle.png");
    private static final ResourceLocation backgroundAdvanced = new ResourceLocation(
        "computercraft",
        "textures/gui/turtle2.png");
    protected World m_world;
    protected ContainerTurtle m_container;
    protected final ComputerFamily m_family;
    protected final ITurtleAccess m_turtle;
    protected final IComputer m_computer;
    protected WidgetTerminal m_terminalGui;

    public GuiTurtle(World world, InventoryPlayer inventoryplayer, TileTurtle turtle) {
        this(world, turtle, new ContainerTurtle(inventoryplayer, turtle.getAccess()));
    }

    protected GuiTurtle(World world, TileTurtle turtle, ContainerTurtle container) {
        super(container);
        this.m_world = world;
        this.m_container = container;
        this.m_family = turtle.getFamily();
        this.m_turtle = turtle.getAccess();
        this.m_computer = turtle.createComputer();
        this.xSize = 254;
        this.ySize = 217;
    }

    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        this.m_terminalGui = new WidgetTerminal(
            (this.width - this.xSize) / 2 + 8,
            (this.height - this.ySize) / 2 + 8,
            39,
            13,
            new IComputerContainer() {

                @Override
                public IComputer getComputer() {
                    return GuiTurtle.this.m_computer;
                }
            });
        this.m_terminalGui.setAllowFocusLoss(false);
    }

    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    public void updateScreen() {
        super.updateScreen();
        this.m_terminalGui.update();
    }

    protected void keyTyped(char c, int k) {
        if (k == 1) {
            super.keyTyped(c, k);
        } else {
            this.m_terminalGui.keyTyped(c, k);
        }
    }

    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        this.m_terminalGui.mouseClicked(x, y, button);
    }

    public void handleMouseInput() {
        super.handleMouseInput();
        int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        this.m_terminalGui.handleMouseInput(x, y);
    }

    public void handleKeyboardInput() {
        super.handleKeyboardInput();
        this.m_terminalGui.handleKeyboardInput();
    }

    protected void drawSelectionSlot(boolean advanced) {
        int x = (this.width - this.xSize) / 2;
        int y = (this.height - this.ySize) / 2;
        int slot = this.m_container.getSelectedSlot();
        if (slot >= 0) {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            int slotX = slot % 4;
            int slotY = slot / 4;
            this.mc.getTextureManager()
                .bindTexture(advanced ? backgroundAdvanced : background);
            this.drawTexturedModalRect(
                x + this.m_container.m_turtleInvStartX - 2 + slotX * 18,
                y + this.m_container.m_playerInvStartY - 2 + slotY * 18,
                0,
                217,
                24,
                24);
        }
    }

    protected void drawGuiContainerBackgroundLayer(float f, int mouseX, int mouseY) {
        boolean advanced = this.m_family == ComputerFamily.Advanced;
        this.m_terminalGui.draw(Minecraft.getMinecraft(), 0, 0, mouseX, mouseY);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager()
            .bindTexture(advanced ? backgroundAdvanced : background);
        int x = (this.width - this.xSize) / 2;
        int y = (this.height - this.ySize) / 2;
        this.drawTexturedModalRect(x, y, 0, 0, this.xSize, this.ySize);
        this.drawSelectionSlot(advanced);
    }
}
