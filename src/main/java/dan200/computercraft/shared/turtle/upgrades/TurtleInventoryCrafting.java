package dan200.computercraft.shared.turtle.upgrades;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.core.TurtlePlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public class TurtleInventoryCrafting extends InventoryCrafting {
   private ITurtleAccess m_turtle;
   private int m_xOffset;
   private int m_yOffset;

   public TurtleInventoryCrafting(ITurtleAccess turtle) {
      super(null, 0, 0);
      this.m_turtle = turtle;
      this.m_xOffset = 0;
      this.m_yOffset = 0;
   }

   private int modifyIndex(int index) {
      int x = index % 3 + this.m_xOffset;
      int y = index / 3 + this.m_yOffset;
      return x >= 0 && x < 4 && y >= 0 && y < 4 ? x + y * 4 : -1;
   }

   private ItemStack tryCrafting(int xOffset, int yOffset) {
      int emptyColumn = (4 + xOffset - 1) % 4;
      int emptyRow = (4 + yOffset - 1) % 4;

      for (int i = 0; i < 4; i++) {
         if (this.m_turtle.getInventory().getStackInSlot(emptyColumn + i * 4) != null || this.m_turtle.getInventory().getStackInSlot(i + emptyRow * 4) != null) {
            return null;
         }
      }

      this.m_xOffset = xOffset;
      this.m_yOffset = yOffset;
      return CraftingManager.getInstance().findMatchingRecipe(this, this.m_turtle.getWorld());
   }

   public ItemStack doCrafting(World world, int maxCount) {
      if (!world.isRemote && world instanceof WorldServer) {
         ItemStack result = this.tryCrafting(0, 0);
         if (result == null) {
            result = this.tryCrafting(0, 1);
         }

         if (result == null) {
            result = this.tryCrafting(1, 0);
         }

         if (result == null) {
            result = this.tryCrafting(1, 1);
         }

         if (result != null) {
            if (maxCount == 0) {
               result.stackSize = 0;
               return result;
            }

            int numToCraft = 1;
            int size = this.getSizeInventory();
            if (maxCount > 1) {
               int minStackSize = 0;

               for (int n = 0; n < size; n++) {
                  ItemStack stack = this.getStackInSlot(n);
                  if (stack != null && (minStackSize == 0 || minStackSize > stack.stackSize)) {
                     minStackSize = stack.stackSize;
                  }
               }

               if (minStackSize > 1) {
                  int var10 = Math.min(minStackSize, result.getMaxStackSize() / result.stackSize);
                  numToCraft = Math.min(var10, maxCount);
                  result.stackSize *= numToCraft;
               }
            }

            TurtlePlayer turtlePlayer = new TurtlePlayer((WorldServer)world);
            result.onCrafting(world, turtlePlayer, numToCraft);

            for (int nx = 0; nx < size; nx++) {
               ItemStack stack = this.getStackInSlot(nx);
               if (stack != null) {
                  this.decrStackSize(nx, numToCraft);
                  ItemStack replacement = stack.getItem().getContainerItem(stack);
                  if (replacement != null
                     && this.getStackInSlot(nx) == null
                     && (!replacement.isItemStackDamageable() && replacement.getItemDamage() >= replacement.getMaxDamage())) {
                     this.setInventorySlotContents(nx, replacement);
                  }
               }
            }
         }

         return result;
      } else {
         return null;
      }
   }

   public ItemStack getStackInRowAndColumn(int x, int y) {
      return x >= 0 && x < 3 ? this.getStackInSlot(x + y * 3) : null;
   }

   public int getSizeInventory() {
      return 9;
   }

   public ItemStack getStackInSlot(int i) {
      i = this.modifyIndex(i);
      return this.m_turtle.getInventory().getStackInSlot(i);
   }

   public String getName() {
      return "Turtle Crafting";
   }

   public ItemStack removeStackFromSlot(int i) {
      i = this.modifyIndex(i);
      return this.m_turtle.getInventory().removeStackFromSlot(i);
   }

   public ItemStack decrStackSize(int i, int size) {
      i = this.modifyIndex(i);
      return this.m_turtle.getInventory().decrStackSize(i, size);
   }

   public void setInventorySlotContents(int i, ItemStack stack) {
      i = this.modifyIndex(i);
      this.m_turtle.getInventory().setInventorySlotContents(i, stack);
   }

   public int getInventoryStackLimit() {
      return this.m_turtle.getInventory().getInventoryStackLimit();
   }

   public void markDirty() {
      this.m_turtle.getInventory().markDirty();
   }

   public boolean isUsableByPlayer(EntityPlayer player) {
      return true;
   }

   public void openInventory() {
   }

   public void closeInventory() {
   }
}
