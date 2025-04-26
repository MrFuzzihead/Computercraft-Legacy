package dan200.computercraft.shared.peripheral.printer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.shared.media.items.ItemPrintout;
import dan200.computercraft.shared.peripheral.common.TilePeripheralBase;
import dan200.computercraft.shared.util.InventoryUtil;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.IIcon;

public class TilePrinter extends TilePeripheralBase implements IInventory, ISidedInventory {
   private static final IIcon[] s_icons = new IIcon[6];
   private static final int[] bottomSlots = new int[]{7, 8, 9, 10, 11, 12};
   private static final int[] topSlots = new int[]{1, 2, 3, 4, 5, 6};
   private static final int[] sideSlots = new int[]{0};
   private final ItemStack[] m_inventory = new ItemStack[13];
   private final Terminal m_page = new Terminal(25, 21);
   private String m_pageTitle = "";
   private boolean m_printing = false;

   @SideOnly(Side.CLIENT)
   public static void registerIcons(IIconRegister iconRegister) {
      s_icons[0] = iconRegister.registerIcon("computercraft:printerTop");
      s_icons[1] = iconRegister.registerIcon("computercraft:printerSide");
      s_icons[2] = iconRegister.registerIcon("computercraft:printerFrontEmpty");
      s_icons[3] = iconRegister.registerIcon("computercraft:printerFrontTopTray");
      s_icons[4] = iconRegister.registerIcon("computercraft:printerFrontBottomTray");
      s_icons[5] = iconRegister.registerIcon("computercraft:printerFrontBothTrays");
   }

   public static IIcon getItemTexture(int side) {
      return getItemTexture(side, s_icons);
   }

   public TilePrinter() {
      super(s_icons);
   }

   @Override
   public void destroy() {
      this.ejectContents();
   }

   @Override
   public boolean onActivate(EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
      if (!player.isSneaking()) {
         if (!this.getWorldObj().isRemote) {
            ComputerCraft.openPrinterGUI(player, this);
         }

         return true;
      } else {
         return false;
      }
   }

   @Override
   public void readFromNBT(NBTTagCompound nbttagcompound) {
      super.readFromNBT(nbttagcompound);
      synchronized (this.m_page) {
         this.m_printing = nbttagcompound.getBoolean("printing");
         this.m_pageTitle = nbttagcompound.getString("pageTitle");
         this.m_page.readFromNBT(nbttagcompound);
      }

      synchronized (this.m_inventory) {
         NBTTagList nbttaglist = nbttagcompound.getTagList("Items", 10);

         for (int i = 0; i < nbttaglist.tagCount(); i++) {
            NBTTagCompound itemTag = nbttaglist.getCompoundTagAt(i);
            int j = itemTag.getByte("Slot") & 255;
            if (j >= 0 && j < this.m_inventory.length) {
               this.m_inventory[j] = ItemStack.loadItemStackFromNBT(itemTag);
            }
         }
      }
   }

   @Override
   public void writeToNBT(NBTTagCompound nbttagcompound) {
      super.writeToNBT(nbttagcompound);
      synchronized (this.m_page) {
         nbttagcompound.setBoolean("printing", this.m_printing);
         nbttagcompound.setString("pageTitle", this.m_pageTitle);
         this.m_page.writeToNBT(nbttagcompound);
      }

      synchronized (this.m_inventory) {
         NBTTagList nbttaglist = new NBTTagList();

         for (int i = 0; i < this.m_inventory.length; i++) {
            if (this.m_inventory[i] != null) {
               NBTTagCompound itemtag = new NBTTagCompound();
               itemtag.setByte("Slot", (byte)i);
               this.m_inventory[i].writeToNBT(itemtag);
               nbttaglist.appendTag(itemtag);
            }
         }

         nbttagcompound.setTag("Items", nbttaglist);
      }
   }

   @Override
   public final void readDescription(NBTTagCompound nbttagcompound) {
      super.readDescription(nbttagcompound);
      this.updateBlock();
   }

   public boolean isPrinting() {
      return this.m_printing;
   }

   public int getSizeInventory() {
      return this.m_inventory.length;
   }

   public ItemStack getStackInSlot(int i) {
      synchronized (this.m_inventory) {
         return this.m_inventory[i];
      }
   }

   public ItemStack getStackInSlotOnClosing(int i) {
      synchronized (this.m_inventory) {
         ItemStack result = this.m_inventory[i];
         this.m_inventory[i] = null;
         this.updateAnim();
         return result;
      }
   }

   public ItemStack decrStackSize(int i, int j) {
      synchronized (this.m_inventory) {
         if (this.m_inventory[i] == null) {
            return null;
         } else if (this.m_inventory[i].getMaxStackSize() <= j) {
            ItemStack itemstack = this.m_inventory[i];
            this.m_inventory[i] = null;
            this.markDirty();
            this.updateAnim();
            return itemstack;
         } else {
            ItemStack part = this.m_inventory[i].splitStack(j);
            if (this.m_inventory[i].stackSize == 0) {
               this.m_inventory[i] = null;
               this.updateAnim();
            }

            this.markDirty();
            return part;
         }
      }
   }

   public void setInventorySlotContents(int i, ItemStack stack) {
      synchronized (this.m_inventory) {
         this.m_inventory[i] = stack;
         this.markDirty();
         this.updateAnim();
      }
   }

   public String getInventoryName() {
      return this.hasCustomInventoryName() ? this.getLabel() : "tile.computercraft:printer.name";
   }

   public int getInventoryStackLimit() {
      return 64;
   }

   public void openInventory() {
   }

   public void closeInventory() {
   }

   public boolean hasCustomInventoryName() {
      return this.getLabel() != null;
   }

   public boolean isItemValidForSlot(int slot, ItemStack itemstack) {
      return true;
   }

   public boolean isUseableByPlayer(EntityPlayer player) {
      return this.isUsable(player, false);
   }

   public int[] getAccessibleSlotsFromSide(int side) {
      switch (side) {
         case 0:
            return bottomSlots;
         case 1:
            return topSlots;
         default:
            return sideSlots;
      }
   }

   public boolean canInsertItem(int slot, ItemStack itemstack, int face) {
      return this.isItemValidForSlot(slot, itemstack);
   }

   public boolean canExtractItem(int slot, ItemStack itemstack, int face) {
      return true;
   }

   @Override
   public IPeripheral getPeripheral(int side) {
      return new PrinterPeripheral(this);
   }

   public Terminal getCurrentPage() {
      return this.m_printing ? this.m_page : null;
   }

   public boolean startNewPage() {
      synchronized (this.m_inventory) {
         if (this.canInputPage()) {
            if (this.m_printing && !this.outputPage()) {
               return false;
            }

            if (this.inputPage()) {
               return true;
            }
         }

         return false;
      }
   }

   public boolean endCurrentPage() {
      synchronized (this.m_inventory) {
         return this.m_printing && this.outputPage();
      }
   }

   public int getInkLevel() {
      synchronized (this.m_inventory) {
         ItemStack inkStack = this.m_inventory[0];
         return inkStack != null && this.isInk(inkStack) ? inkStack.stackSize : 0;
      }
   }

   public int getPaperLevel() {
      int count = 0;
      synchronized (this.m_inventory) {
         for (int i = 1; i < 7; i++) {
            ItemStack paperStack = this.m_inventory[i];
            if (paperStack != null && this.isPaper(paperStack)) {
               count += paperStack.stackSize;
            }
         }

         return count;
      }
   }

   public void setPageTitle(String title) {
      if (this.m_printing) {
         this.m_pageTitle = title;
      }
   }

   private boolean isInk(ItemStack stack) {
      return stack.getItem() == Items.dye;
   }

   private boolean isPaper(ItemStack stack) {
      Item item = stack.getItem();
      return item == Items.paper || item instanceof ItemPrintout && ItemPrintout.getType(stack) == ItemPrintout.Type.Single;
   }

   private boolean canInputPage() {
      synchronized (this.m_inventory) {
         ItemStack inkStack = this.m_inventory[0];
         return inkStack == null || !this.isInk(inkStack) ? false : this.getPaperLevel() > 0;
      }
   }

   private boolean inputPage() {
      synchronized (this.m_inventory) {
         ItemStack inkStack = this.m_inventory[0];
         if (inkStack != null && this.isInk(inkStack)) {
            for (int i = 1; i < 7; i++) {
               ItemStack paperStack = this.m_inventory[i];
               if (paperStack != null && this.isPaper(paperStack)) {
                  inkStack.stackSize--;
                  if (inkStack.stackSize <= 0) {
                     this.m_inventory[0] = null;
                  }

                  paperStack.stackSize--;
                  if (paperStack.stackSize <= 0) {
                     this.m_inventory[i] = null;
                     this.updateAnim();
                  }

                  int colour = inkStack.getItemDamage();
                  if (colour >= 0 && colour < 16) {
                     this.m_page.setTextColour(15 - colour);
                  } else {
                     this.m_page.setTextColour(15);
                  }

                  this.m_page.clear();
                  if (paperStack.getItem() instanceof ItemPrintout) {
                     this.m_pageTitle = ItemPrintout.getTitle(paperStack);
                     String[] text = ItemPrintout.getText(paperStack);
                     String[] textColour = ItemPrintout.getColours(paperStack);

                     for (int y = 0; y < this.m_page.getHeight(); y++) {
                        this.m_page.setLine(y, text[y], textColour[y], "");
                     }
                  } else {
                     this.m_pageTitle = "";
                  }

                  this.m_page.setCursorPos(0, 0);
                  this.markDirty();
                  this.m_printing = true;
                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      }
   }

   private boolean outputPage() {
      synchronized (this.m_page) {
         int height = this.m_page.getHeight();
         String[] lines = new String[height];
         String[] colours = new String[height];

         for (int i = 0; i < height; i++) {
            lines[i] = this.m_page.getLine(i).toString();
            colours[i] = this.m_page.getTextColourLine(i).toString();
         }

         ItemStack stack = ItemPrintout.createSingleFromTitleAndText(this.m_pageTitle, lines, colours);
         synchronized (this.m_inventory) {
            ItemStack remainder = InventoryUtil.storeItems(stack, this, 7, 6, 7);
            if (remainder == null) {
               this.m_printing = false;
               return true;
            }
         }

         return false;
      }
   }

   private void ejectContents() {
      synchronized (this.m_inventory) {
         for (int i = 0; i < 13; i++) {
            ItemStack stack = this.m_inventory[i];
            if (stack != null) {
               this.setInventorySlotContents(i, null);
               double x = this.xCoord + 0.5;
               double y = this.yCoord + 0.75;
               double z = this.zCoord + 0.5;
               EntityItem entityitem = new EntityItem(this.getWorldObj(), x, y, z, stack);
               entityitem.motionX = this.getWorldObj().rand.nextFloat() * 0.2 - 0.1;
               entityitem.motionY = this.getWorldObj().rand.nextFloat() * 0.2 - 0.1;
               entityitem.motionZ = this.getWorldObj().rand.nextFloat() * 0.2 - 0.1;
               this.getWorldObj().spawnEntityInWorld(entityitem);
            }
         }
      }
   }

   private void updateAnim() {
      synchronized (this.m_inventory) {
         int anim = 0;

         for (int i = 1; i < 7; i++) {
            ItemStack stack = this.m_inventory[i];
            if (stack != null && this.isPaper(stack)) {
               anim++;
               break;
            }
         }

         for (int ix = 7; ix < 13; ix++) {
            ItemStack stack = this.m_inventory[ix];
            if (stack != null && this.isPaper(stack)) {
               anim += 2;
               break;
            }
         }

         this.setAnim(anim);
      }
   }
}
