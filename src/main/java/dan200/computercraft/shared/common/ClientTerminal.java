package dan200.computercraft.shared.common;

import dan200.computercraft.core.terminal.Terminal;
import net.minecraft.nbt.NBTTagCompound;

public class ClientTerminal implements ITerminal {
   private boolean m_colour;
   private Terminal m_terminal;
   private boolean m_terminalChanged;
   private boolean m_terminalChangedLastFrame;

   public ClientTerminal(boolean colour) {
      this.m_colour = colour;
      this.m_terminal = null;
      this.m_terminalChanged = false;
      this.m_terminalChangedLastFrame = false;
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

   public void readDescription(NBTTagCompound nbttagcompound) {
      this.m_colour = nbttagcompound.getBoolean("colour");
      if (nbttagcompound.hasKey("terminal")) {
         NBTTagCompound terminal = nbttagcompound.getCompoundTag("terminal");
         this.resizeTerminal(terminal.getInteger("term_width"), terminal.getInteger("term_height"));
         this.m_terminal.readFromNBT(terminal);
      } else {
         this.deleteTerminal();
      }
   }

   private void resizeTerminal(int width, int height) {
      if (this.m_terminal == null) {
         this.m_terminal = new Terminal(width, height);
         this.m_terminalChanged = true;
      } else {
         this.m_terminal.resize(width, height);
      }
   }

   private void deleteTerminal() {
      if (this.m_terminal != null) {
         this.m_terminal = null;
         this.m_terminalChanged = true;
      }
   }
}
