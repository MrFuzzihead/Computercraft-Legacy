package dan200.computercraft.shared.common;

import dan200.computercraft.core.terminal.Terminal;
import net.minecraft.nbt.NBTTagCompound;

public class ServerTerminal implements ITerminal {
   private final boolean m_colour;
   private Terminal m_terminal;
   private boolean m_terminalChanged;
   private boolean m_terminalChangedLastFrame;

   public ServerTerminal(boolean colour) {
      this.m_colour = colour;
      this.m_terminal = null;
      this.m_terminalChanged = false;
      this.m_terminalChangedLastFrame = false;
   }

   public ServerTerminal(boolean colour, int terminalWidth, int terminalHeight) {
      this.m_colour = colour;
      this.m_terminal = new Terminal(terminalWidth, terminalHeight);
      this.m_terminalChanged = false;
      this.m_terminalChangedLastFrame = false;
   }

   public void resize(int width, int height) {
      if (this.m_terminal == null) {
         this.m_terminal = new Terminal(width, height);
         this.m_terminalChanged = true;
      } else {
         this.m_terminal.resize(width, height);
      }
   }

   public void delete() {
      if (this.m_terminal != null) {
         this.m_terminal = null;
         this.m_terminalChanged = true;
      }
   }

   public void update() {
      this.m_terminalChangedLastFrame = this.m_terminalChanged || this.m_terminal != null && this.m_terminal.getChanged();
      if (this.m_terminal != null) {
         this.m_terminal.clearChanged();
      }

      this.m_terminalChanged = false;
   }

   public boolean hasTerminalChanged() {
      return this.m_terminalChangedLastFrame;
   }

   @Override
   public Terminal getTerminal() {
      return this.m_terminal;
   }

   @Override
   public boolean isColour() {
      return this.m_colour;
   }

   public void writeDescription(NBTTagCompound nbttagcompound) {
      nbttagcompound.setBoolean("colour", this.m_colour);
      if (this.m_terminal != null) {
         NBTTagCompound terminal = new NBTTagCompound();
         terminal.setInteger("term_width", this.m_terminal.getWidth());
         terminal.setInteger("term_height", this.m_terminal.getHeight());
         this.m_terminal.writeToNBT(terminal);
         nbttagcompound.setTag("terminal", terminal);
      }
   }
}
