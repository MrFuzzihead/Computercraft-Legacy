package dan200.computercraft.shared.peripheral.printer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class ContainerPrinter extends Container {
   private TilePrinter m_printer;
   private boolean m_lastPrinting;

   public ContainerPrinter(IInventory playerInventory, TilePrinter printer) {
      this.m_printer = printer;
      this.m_lastPrinting = false;
      this.addSlotToContainer(new Slot(this.m_printer, 0, 13, 35));

      for (int i = 0; i < 6; i++) {
         this.addSlotToContainer(new Slot(this.m_printer, i + 1, 61 + i * 18, 22));
      }

      for (int i = 0; i < 6; i++) {
         this.addSlotToContainer(new Slot(this.m_printer, i + 7, 61 + i * 18, 49));
      }

      for (int j = 0; j < 3; j++) {
         for (int i1 = 0; i1 < 9; i1++) {
            this.addSlotToContainer(new Slot(playerInventory, i1 + j * 9 + 9, 8 + i1 * 18, 84 + j * 18));
         }
      }

      for (int k = 0; k < 9; k++) {
         this.addSlotToContainer(new Slot(playerInventory, k, 8 + k * 18, 142));
      }
   }

   public boolean isPrinting() {
      return this.m_lastPrinting;
   }

   public void addCraftingToCrafters(ICrafting crafting) {
      super.addCraftingToCrafters(crafting);
      boolean printing = this.m_printer.isPrinting();
      crafting.sendProgressBarUpdate(this, 0, printing ? 1 : 0);
   }

   public void detectAndSendChanges() {
      super.detectAndSendChanges();
      if (!this.m_printer.getWorldObj().isRemote) {
         boolean printing = this.m_printer.isPrinting();

         for (int i = 0; i < this.crafters.size(); i++) {
            ICrafting icrafting = (ICrafting)this.crafters.get(i);
            if (printing != this.m_lastPrinting) {
               icrafting.sendProgressBarUpdate(this, 0, printing ? 1 : 0);
            }
         }

         this.m_lastPrinting = printing;
      }
   }

   public void updateProgressBar(int i, int j) {
      if (this.m_printer.getWorldObj().isRemote) {
         this.m_lastPrinting = j > 0;
      }
   }

   public boolean canInteractWith(EntityPlayer player) {
      return this.m_printer.isUseableByPlayer(player);
   }

   public ItemStack transferStackInSlot(EntityPlayer par1EntityPlayer, int i) {
      ItemStack itemstack = null;
      Slot slot = (Slot)this.inventorySlots.get(i);
      if (slot != null && slot.getHasStack()) {
         ItemStack itemstack1 = slot.getStack();
         itemstack = itemstack1.copy();
         if (i < 13) {
            if (!this.mergeItemStack(itemstack1, 13, 49, true)) {
               return null;
            }
         } else if (itemstack1.getItem() == Items.dye) {
            if (!this.mergeItemStack(itemstack1, 0, 1, false)) {
               return null;
            }
         } else if (!this.mergeItemStack(itemstack1, 1, 13, false)) {
            return null;
         }

         if (itemstack1.stackSize == 0) {
            slot.putStack(null);
         } else {
            slot.onSlotChanged();
         }

         if (itemstack1.stackSize == itemstack.stackSize) {
            return null;
         }

         slot.onPickupFromSlot(par1EntityPlayer, itemstack1);
      }

      return itemstack;
   }
}
