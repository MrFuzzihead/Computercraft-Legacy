package dan200.computercraft.core.terminal;

import net.minecraft.nbt.NBTTagCompound;

public class Terminal {
   private static final String base16 = "0123456789abcdef";
   private int m_cursorX;
   private int m_cursorY;
   private boolean m_cursorBlink;
   private int m_cursorColour;
   private int m_cursorBackgroundColour;
   private int m_width;
   private int m_height;
   private TextBuffer[] m_text;
   private TextBuffer[] m_textColour;
   private TextBuffer[] m_backgroundColour;
   private boolean m_changed;

   public Terminal(int width, int height) {
      this.m_width = width;
      this.m_height = height;
      this.m_cursorColour = 0;
      this.m_cursorBackgroundColour = 15;
      this.m_text = new TextBuffer[this.m_height];
      this.m_textColour = new TextBuffer[this.m_height];
      this.m_backgroundColour = new TextBuffer[this.m_height];

      for (int i = 0; i < this.m_height; i++) {
         this.m_text[i] = new TextBuffer(' ', this.m_width);
         this.m_textColour[i] = new TextBuffer(base16.charAt(this.m_cursorColour), this.m_width);
         this.m_backgroundColour[i] = new TextBuffer(base16.charAt(this.m_cursorBackgroundColour), this.m_width);
      }

      this.m_cursorX = 0;
      this.m_cursorY = 0;
      this.m_cursorBlink = false;
      this.m_changed = false;
   }

   public void reset() {
      this.m_cursorColour = 0;
      this.m_cursorBackgroundColour = 15;
      this.m_cursorX = 0;
      this.m_cursorY = 0;
      this.m_cursorBlink = false;
      this.clear();
      this.m_changed = true;
   }

   public int getWidth() {
      return this.m_width;
   }

   public int getHeight() {
      return this.m_height;
   }

   public void resize(int width, int height) {
      if (width != this.m_width || height != this.m_height) {
         int oldHeight = this.m_height;
         int oldWidth = this.m_width;
         TextBuffer[] oldText = this.m_text;
         TextBuffer[] oldTextColour = this.m_textColour;
         TextBuffer[] oldBackgroundColour = this.m_backgroundColour;
         this.m_width = width;
         this.m_height = height;
         this.m_text = new TextBuffer[this.m_height];
         this.m_textColour = new TextBuffer[this.m_height];
         this.m_backgroundColour = new TextBuffer[this.m_height];

         for (int i = 0; i < this.m_height; i++) {
            if (i >= oldHeight) {
               this.m_text[i] = new TextBuffer(' ', this.m_width);
               this.m_textColour[i] = new TextBuffer(base16.charAt(this.m_cursorColour), this.m_width);
               this.m_backgroundColour[i] = new TextBuffer(base16.charAt(this.m_cursorBackgroundColour), this.m_width);
            } else if (this.m_width == oldWidth) {
               this.m_text[i] = oldText[i];
               this.m_textColour[i] = oldTextColour[i];
               this.m_backgroundColour[i] = oldBackgroundColour[i];
            } else {
               this.m_text[i] = new TextBuffer(' ', this.m_width);
               this.m_textColour[i] = new TextBuffer(base16.charAt(this.m_cursorColour), this.m_width);
               this.m_backgroundColour[i] = new TextBuffer(base16.charAt(this.m_cursorBackgroundColour), this.m_width);
               this.m_text[i].write(oldText[i]);
               this.m_textColour[i].write(oldTextColour[i]);
               this.m_backgroundColour[i].write(oldBackgroundColour[i]);
            }
         }

         this.m_changed = true;
      }
   }

   public void setCursorPos(int x, int y) {
      if (this.m_cursorX != x || this.m_cursorY != y) {
         this.m_cursorX = x;
         this.m_cursorY = y;
         this.m_changed = true;
      }
   }

   public void setCursorBlink(boolean blink) {
      if (this.m_cursorBlink != blink) {
         this.m_cursorBlink = blink;
         this.m_changed = true;
      }
   }

   public void setTextColour(int colour) {
      if (this.m_cursorColour != colour) {
         this.m_cursorColour = colour;
         this.m_changed = true;
      }
   }

   public void setBackgroundColour(int colour) {
      if (this.m_cursorBackgroundColour != colour) {
         this.m_cursorBackgroundColour = colour;
         this.m_changed = true;
      }
   }

   public int getCursorX() {
      return this.m_cursorX;
   }

   public int getCursorY() {
      return this.m_cursorY;
   }

   public boolean getCursorBlink() {
      return this.m_cursorBlink;
   }

   public int getTextColour() {
      return this.m_cursorColour;
   }

   public int getBackgroundColour() {
      return this.m_cursorBackgroundColour;
   }

   public void blit(String text, String textColour, String backgroundColour) {
      int x = this.m_cursorX;
      int y = this.m_cursorY;
      if (y >= 0 && y < this.m_height) {
         this.m_text[y].write(text, x);
         this.m_textColour[y].write(textColour, x);
         this.m_backgroundColour[y].write(backgroundColour, x);
         this.m_changed = true;
      }
   }

   public void write(String text) {
      int x = this.m_cursorX;
      int y = this.m_cursorY;
      if (y >= 0 && y < this.m_height) {
         this.m_text[y].write(text, x);
         this.m_textColour[y].fill(base16.charAt(this.m_cursorColour), x, x + text.length());
         this.m_backgroundColour[y].fill(base16.charAt(this.m_cursorBackgroundColour), x, x + text.length());
         this.m_changed = true;
      }
   }

   public void scroll(int yDiff) {
      if (yDiff != 0) {
         TextBuffer[] newText = new TextBuffer[this.m_height];
         TextBuffer[] newTextColour = new TextBuffer[this.m_height];
         TextBuffer[] newBackgroundColour = new TextBuffer[this.m_height];

         for (int y = 0; y < this.m_height; y++) {
            int oldY = y + yDiff;
            if (oldY >= 0 && oldY < this.m_height) {
               newText[y] = this.m_text[oldY];
               newTextColour[y] = this.m_textColour[oldY];
               newBackgroundColour[y] = this.m_backgroundColour[oldY];
            } else {
               newText[y] = new TextBuffer(' ', this.m_width);
               newTextColour[y] = new TextBuffer(base16.charAt(this.m_cursorColour), this.m_width);
               newBackgroundColour[y] = new TextBuffer(base16.charAt(this.m_cursorBackgroundColour), this.m_width);
            }
         }

         this.m_text = newText;
         this.m_textColour = newTextColour;
         this.m_backgroundColour = newBackgroundColour;
         this.m_changed = true;
      }
   }

   public void clear() {
      for (int y = 0; y < this.m_height; y++) {
         this.m_text[y].fill(' ');
         this.m_textColour[y].fill(base16.charAt(this.m_cursorColour));
         this.m_backgroundColour[y].fill(base16.charAt(this.m_cursorBackgroundColour));
      }

      this.m_changed = true;
   }

   public void clearLine() {
      int y = this.m_cursorY;
      if (y >= 0 && y < this.m_height) {
         this.m_text[y].fill(' ');
         this.m_textColour[y].fill(base16.charAt(this.m_cursorColour));
         this.m_backgroundColour[y].fill(base16.charAt(this.m_cursorBackgroundColour));
         this.m_changed = true;
      }
   }

   public TextBuffer getLine(int y) {
      return y >= 0 && y < this.m_height ? this.m_text[y] : null;
   }

   public void setLine(int y, String text, String textColour, String backgroundColour) {
      this.m_text[y].write(text);
      this.m_textColour[y].write(textColour);
      this.m_backgroundColour[y].write(backgroundColour);
      this.m_changed = true;
   }

   public TextBuffer getTextColourLine(int y) {
      return y >= 0 && y < this.m_height ? this.m_textColour[y] : null;
   }

   public TextBuffer getBackgroundColourLine(int y) {
      return y >= 0 && y < this.m_height ? this.m_backgroundColour[y] : null;
   }

   public boolean getChanged() {
      return this.m_changed;
   }

   public void clearChanged() {
      this.m_changed = false;
   }

   public void writeToNBT(NBTTagCompound nbttagcompound) {
      nbttagcompound.setInteger("term_cursorX", this.m_cursorX);
      nbttagcompound.setInteger("term_cursorY", this.m_cursorY);
      nbttagcompound.setBoolean("term_cursorBlink", this.m_cursorBlink);
      nbttagcompound.setInteger("term_textColour", this.m_cursorColour);
      nbttagcompound.setInteger("term_bgColour", this.m_cursorBackgroundColour);

      for (int n = 0; n < this.m_height; n++) {
         nbttagcompound.setString("term_text_" + n, this.m_text[n].toString());
         nbttagcompound.setString("term_textColour_" + n, this.m_textColour[n].toString());
         nbttagcompound.setString("term_textBgColour_" + n, this.m_backgroundColour[n].toString());
      }
   }

   public void readFromNBT(NBTTagCompound nbttagcompound) {
      this.m_cursorX = nbttagcompound.getInteger("term_cursorX");
      this.m_cursorY = nbttagcompound.getInteger("term_cursorY");
      this.m_cursorBlink = nbttagcompound.getBoolean("term_cursorBlink");
      this.m_cursorColour = nbttagcompound.getInteger("term_textColour");
      this.m_cursorBackgroundColour = nbttagcompound.getInteger("term_bgColour");

      for (int n = 0; n < this.m_height; n++) {
         this.m_text[n].fill(' ');
         if (nbttagcompound.hasKey("term_text_" + n)) {
            this.m_text[n].write(nbttagcompound.getString("term_text_" + n));
         }

         this.m_textColour[n].fill(base16.charAt(this.m_cursorColour));
         if (nbttagcompound.hasKey("term_textColour_" + n)) {
            this.m_textColour[n].write(nbttagcompound.getString("term_textColour_" + n));
         }

         this.m_backgroundColour[n].fill(base16.charAt(this.m_cursorBackgroundColour));
         if (nbttagcompound.hasKey("term_textBgColour_" + n)) {
            this.m_backgroundColour[n].write(nbttagcompound.getString("term_textBgColour_" + n));
         }
      }

      this.m_changed = true;
   }
}
