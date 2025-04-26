package dan200.computercraft.client.gui.widgets;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.client.gui.FixedWidthFontRenderer;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.core.terminal.TextBuffer;
import dan200.computercraft.shared.computer.core.IComputer;
import dan200.computercraft.shared.computer.core.IComputerContainer;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class WidgetTerminal extends Widget {
   private static final ResourceLocation background = new ResourceLocation("computercraft", "textures/gui/terminal.png");
   private IComputerContainer m_computer;
   private float m_terminateTimer;
   private float m_rebootTimer;
   private float m_shutdownTimer;
   private int m_lastClickButton;
   private int m_lastClickX;
   private int m_lastClickY;
   private boolean m_focus;
   private boolean m_allowFocusLoss;
   private boolean m_locked;
   private int m_leftMargin;
   private int m_rightMargin;
   private int m_topMargin;
   private int m_bottomMargin;
   private ArrayList<Integer> m_keysDown;

   public WidgetTerminal(int x, int y, int termWidth, int termHeight, IComputerContainer computer) {
      this(x, y, termWidth, termHeight, computer, 2, 2, 2, 2);
   }

   public WidgetTerminal(
      int x, int y, int termWidth, int termHeight, IComputerContainer computer, int leftMargin, int rightMargin, int topMargin, int bottomMargin
   ) {
      super(
         x,
         y,
         leftMargin + rightMargin + termWidth * FixedWidthFontRenderer.FONT_WIDTH,
         topMargin + bottomMargin + termHeight * FixedWidthFontRenderer.FONT_HEIGHT
      );
      this.m_computer = computer;
      this.m_terminateTimer = 0.0F;
      this.m_rebootTimer = 0.0F;
      this.m_shutdownTimer = 0.0F;
      this.m_lastClickButton = -1;
      this.m_lastClickX = -1;
      this.m_lastClickY = -1;
      this.m_focus = false;
      this.m_allowFocusLoss = true;
      this.m_locked = false;
      this.m_leftMargin = leftMargin;
      this.m_rightMargin = rightMargin;
      this.m_topMargin = topMargin;
      this.m_bottomMargin = bottomMargin;
      this.m_keysDown = new ArrayList<>();
   }

   public void setAllowFocusLoss(boolean allowFocusLoss) {
      this.m_allowFocusLoss = allowFocusLoss;
      this.m_focus = this.m_focus || !allowFocusLoss;
   }

   public void setLocked(boolean locked) {
      this.m_locked = locked;
   }

   @Override
   public void keyTyped(char ch, int key) {
      if (this.m_focus && !this.m_locked) {
         if (ch == 22) {
            String clipboard = GuiScreen.getClipboardString();
            if (clipboard != null) {
               int newLineIndex1 = clipboard.indexOf("\r");
               int newLineIndex2 = clipboard.indexOf("\n");
               if (newLineIndex1 >= 0 && newLineIndex2 >= 0) {
                  clipboard = clipboard.substring(0, Math.min(newLineIndex1, newLineIndex2));
               } else if (newLineIndex1 >= 0) {
                  clipboard = clipboard.substring(0, newLineIndex1);
               } else if (newLineIndex2 >= 0) {
                  clipboard = clipboard.substring(0, newLineIndex2);
               }

               clipboard = ChatAllowedCharacters.filerAllowedCharacters(clipboard);
               if (!clipboard.isEmpty()) {
                  if (clipboard.length() > 512) {
                     clipboard = clipboard.substring(0, 512);
                  }

                  this.queueEvent("paste", new Object[]{clipboard});
               }
            }

            return;
         }

         if (this.m_terminateTimer < 0.5F && this.m_rebootTimer < 0.5F && this.m_shutdownTimer < 0.5F) {
            boolean repeat = Keyboard.isRepeatEvent();
            if (key > 0) {
               if (!repeat) {
                  this.m_keysDown.add(key);
               }

               this.queueEvent("key", new Object[]{key, repeat});
            }

            if (ch >= ' ' && ch <= '~') {
               this.queueEvent("char", new Object[]{Character.toString(ch)});
            }
         }
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int button) {
      if (mouseX >= this.getXPosition()
         && mouseX < this.getXPosition() + this.getWidth()
         && mouseY >= this.getYPosition()
         && mouseY < this.getYPosition() + this.getHeight()) {
         if (!this.m_focus && button == 0) {
            this.m_focus = true;
         }

         if (this.m_focus) {
            IComputer computer = this.m_computer.getComputer();
            if (!this.m_locked && computer != null && computer.isColour() && button >= 0 && button <= 2) {
               Terminal term = computer.getTerminal();
               if (term != null) {
                  int charX = (mouseX - (this.getXPosition() + this.m_leftMargin)) / FixedWidthFontRenderer.FONT_WIDTH;
                  int charY = (mouseY - (this.getYPosition() + this.m_topMargin)) / FixedWidthFontRenderer.FONT_HEIGHT;
                  charX = Math.min(Math.max(charX, 0), term.getWidth() - 1);
                  charY = Math.min(Math.max(charY, 0), term.getHeight() - 1);
                  computer.queueEvent("mouse_click", new Object[]{button + 1, charX + 1, charY + 1});
                  this.m_lastClickButton = button;
                  this.m_lastClickX = charX;
                  this.m_lastClickY = charY;
               }
            }
         }
      } else if (this.m_focus && button == 0 && this.m_allowFocusLoss) {
         this.m_focus = false;
      }
   }

   @Override
   public void handleKeyboardInput() {
      for (int i = this.m_keysDown.size() - 1; i >= 0; i--) {
         int key = this.m_keysDown.get(i);
         if (!Keyboard.isKeyDown(key)) {
            this.m_keysDown.remove(i);
            if (this.m_focus && !this.m_locked) {
               this.queueEvent("key_up", new Object[]{key});
            }
         }
      }
   }

   @Override
   public void handleMouseInput(int mouseX, int mouseY) {
      IComputer computer = this.m_computer.getComputer();
      if (mouseX >= this.getXPosition()
         && mouseX < this.getXPosition() + this.getWidth()
         && mouseY >= this.getYPosition()
         && mouseY < this.getYPosition() + this.getHeight()
         && computer != null
         && computer.isColour()) {
         Terminal term = computer.getTerminal();
         if (term != null) {
            int charX = (mouseX - (this.getXPosition() + this.m_leftMargin)) / FixedWidthFontRenderer.FONT_WIDTH;
            int charY = (mouseY - (this.getYPosition() + this.m_topMargin)) / FixedWidthFontRenderer.FONT_HEIGHT;
            charX = Math.min(Math.max(charX, 0), term.getWidth() - 1);
            charY = Math.min(Math.max(charY, 0), term.getHeight() - 1);
            if (this.m_lastClickButton >= 0 && !Mouse.isButtonDown(this.m_lastClickButton)) {
               if (this.m_focus && !this.m_locked) {
                  computer.queueEvent("mouse_up", new Object[]{this.m_lastClickButton + 1, charX + 1, charY + 1});
               }

               this.m_lastClickButton = -1;
            }

            int wheelChange = Mouse.getEventDWheel();
            if (wheelChange == 0 && this.m_lastClickButton == -1) {
               return;
            }

            if (this.m_focus && !this.m_locked) {
               if (wheelChange < 0) {
                  computer.queueEvent("mouse_scroll", new Object[]{1, charX + 1, charY + 1});
               } else if (wheelChange > 0) {
                  computer.queueEvent("mouse_scroll", new Object[]{-1, charX + 1, charY + 1});
               }

               if (this.m_lastClickButton >= 0 && (charX != this.m_lastClickX || charY != this.m_lastClickY)) {
                  computer.queueEvent("mouse_drag", new Object[]{this.m_lastClickButton + 1, charX + 1, charY + 1});
                  this.m_lastClickX = charX;
                  this.m_lastClickY = charY;
               }
            }
         }
      }
   }

   @Override
   public void update() {
      if (this.m_focus && !this.m_locked && (Keyboard.isKeyDown(29) || Keyboard.isKeyDown(157))) {
         if (Keyboard.isKeyDown(20)) {
            if (this.m_terminateTimer < 1.0F) {
               this.m_terminateTimer += 0.05F;
               if (this.m_terminateTimer >= 1.0F) {
                  this.queueEvent("terminate");
               }
            }
         } else {
            this.m_terminateTimer = 0.0F;
         }

         if (Keyboard.isKeyDown(19)) {
            if (this.m_rebootTimer < 1.0F) {
               this.m_rebootTimer += 0.05F;
               if (this.m_rebootTimer >= 1.0F) {
                  IComputer computer = this.m_computer.getComputer();
                  if (computer != null) {
                     computer.reboot();
                  }
               }
            }
         } else {
            this.m_rebootTimer = 0.0F;
         }

         if (Keyboard.isKeyDown(31)) {
            if (this.m_shutdownTimer < 1.0F) {
               this.m_shutdownTimer += 0.05F;
               if (this.m_shutdownTimer >= 1.0F) {
                  IComputer computer = this.m_computer.getComputer();
                  if (computer != null) {
                     computer.shutdown();
                  }
               }
            }
         } else {
            this.m_shutdownTimer = 0.0F;
         }
      } else {
         this.m_terminateTimer = 0.0F;
         this.m_rebootTimer = 0.0F;
         this.m_shutdownTimer = 0.0F;
      }
   }

   @Override
   public void draw(Minecraft mc, int xOrigin, int yOrigin, int mouseX, int mouseY) {
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      int startX = xOrigin + this.getXPosition();
      int startY = yOrigin + this.getYPosition();
      mc.func_110434_K().bindTexture(background);
      this.drawTexturedModalRect(startX, startY, 0, 0, this.getWidth(), this.getHeight());
      IComputer computer = this.m_computer.getComputer();
      Terminal terminal = computer != null ? computer.getTerminal() : null;
      if (terminal != null) {
         boolean greyscale = !computer.isColour();
         synchronized (terminal) {
            FixedWidthFontRenderer fontRenderer = (FixedWidthFontRenderer)ComputerCraft.getFixedWidthFontRenderer();
            boolean tblink = this.m_focus && terminal.getCursorBlink() && ComputerCraft.getGlobalCursorBlink();
            int tw = terminal.getWidth();
            int th = terminal.getHeight();
            int tx = terminal.getCursorX();
            int ty = terminal.getCursorY();
            int x = startX + this.m_leftMargin;
            int y = startY + this.m_topMargin;
            TextBuffer emptyLine = new TextBuffer(' ', tw);
            if (this.m_topMargin > 0) {
               fontRenderer.drawString(
                  emptyLine, x, startY, terminal.getTextColourLine(0), terminal.getBackgroundColourLine(0), this.m_leftMargin, this.m_rightMargin, greyscale
               );
            }

            if (this.m_bottomMargin > 0) {
               fontRenderer.drawString(
                  emptyLine,
                  x,
                  startY + 2 * this.m_bottomMargin + (th - 1) * FixedWidthFontRenderer.FONT_HEIGHT,
                  terminal.getTextColourLine(th - 1),
                  terminal.getBackgroundColourLine(th - 1),
                  this.m_leftMargin,
                  this.m_rightMargin,
                  greyscale
               );
            }

            for (int line = 0; line < th; line++) {
               TextBuffer text = terminal.getLine(line);
               TextBuffer colour = terminal.getTextColourLine(line);
               TextBuffer backgroundColour = terminal.getBackgroundColourLine(line);
               fontRenderer.drawString(text, x, y, colour, backgroundColour, this.m_leftMargin, this.m_rightMargin, greyscale);
               if (tblink && ty == line && tx >= 0 && tx < tw) {
                  TextBuffer cursor = new TextBuffer('_', 1);
                  TextBuffer cursorColour = new TextBuffer("0123456789abcdef".charAt(terminal.getTextColour()), 1);
                  fontRenderer.drawString(cursor, x + FixedWidthFontRenderer.FONT_WIDTH * tx, y, cursorColour, null, 0.0F, 0.0F, greyscale);
               }

               y += FixedWidthFontRenderer.FONT_HEIGHT;
            }
         }
      }
   }

   @Override
   public boolean suppressKeyPress(char c, int k) {
      return this.m_focus ? k != 1 : false;
   }

   private void queueEvent(String event) {
      IComputer computer = this.m_computer.getComputer();
      if (computer != null) {
         computer.queueEvent(event);
      }
   }

   private void queueEvent(String event, Object[] args) {
      IComputer computer = this.m_computer.getComputer();
      if (computer != null) {
         computer.queueEvent(event, args);
      }
   }
}
