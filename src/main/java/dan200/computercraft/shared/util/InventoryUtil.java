package dan200.computercraft.shared.util;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Facing;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class InventoryUtil {
   public static boolean areItemsEqual(ItemStack a, ItemStack b) {
      return areItemsStackable(a, b) && (a == null || a.stackSize == b.stackSize);
   }

   public static boolean areItemsStackable(ItemStack a, ItemStack b) {
      return a == b
         ? true
         : a != null
            && b != null
            && a.getItem() == b.getItem()
            && a.getItemDamage() == b.getItemDamage()
            && (
               a.field_77990_d == null && b.field_77990_d == null
                  || a.field_77990_d != null && b.field_77990_d != null && a.field_77990_d.equals(b.field_77990_d)
            );
   }

   public static ItemStack copyItem(ItemStack a) {
      return a != null ? a.copy() : null;
   }

   public static IInventory getInventory(World world, int x, int y, int z, int side) {
      if (y >= 0 && y < world.getHeight()) {
         TileEntity tileEntity = world.getTileEntity(x, y, z);
         if (tileEntity != null && tileEntity instanceof IInventory) {
            Block block = world.getBlock(x, y, z);
            if (block == Blocks.field_150486_ae || block == Blocks.field_150447_bR) {
               if (world.getBlock(x - 1, y, z) == block) {
                  return new InventoryLargeChest("Large chest", (IInventory)world.getTileEntity(x - 1, y, z), (IInventory)tileEntity);
               }

               if (world.getBlock(x + 1, y, z) == block) {
                  return new InventoryLargeChest("Large chest", (IInventory)tileEntity, (IInventory)world.getTileEntity(x + 1, y, z));
               }

               if (world.getBlock(x, y, z - 1) == block) {
                  return new InventoryLargeChest("Large chest", (IInventory)world.getTileEntity(x, y, z - 1), (IInventory)tileEntity);
               }

               if (world.getBlock(x, y, z + 1) == block) {
                  return new InventoryLargeChest("Large chest", (IInventory)tileEntity, (IInventory)world.getTileEntity(x, y, z + 1));
               }
            }

            return (IInventory)tileEntity;
         }
      }

      int dir = Facing.oppositeSide[side];
      Vec3 vecStart = Vec3.createVectorHelper(
         x + 0.5 + 0.6 * Facing.offsetsXForSide[side], y + 0.5 + 0.6 * Facing.offsetsYForSide[side], z + 0.5 + 0.6 * Facing.offsetsZForSide[side]
      );
      Vec3 vecDir = Vec3.createVectorHelper(Facing.offsetsXForSide[dir], Facing.offsetsYForSide[dir], Facing.offsetsZForSide[dir]);
      Entity entity = WorldUtil.rayTraceEntities(world, vecStart, vecDir, 1.1);
      return entity != null && entity instanceof IInventory ? (IInventory)entity : null;
   }

   public static ItemStack storeItems(ItemStack itemstack, IInventory inventory, int start, int range, int begin) {
      int[] slots = makeSlotList(start, range, begin);
      return storeItems(itemstack, inventory, slots, -1);
   }

   public static ItemStack storeItems(ItemStack itemstack, IInventory inventory, int side) {
      if (inventory instanceof ISidedInventory) {
         ISidedInventory sidedInventory = (ISidedInventory)inventory;
         int[] slots = sidedInventory.getAccessibleSlotsFromSide(side);
         return storeItems(itemstack, inventory, slots, side);
      } else {
         int[] slots = makeSlotList(0, inventory.getSizeInventory(), 0);
         return storeItems(itemstack, inventory, slots, side);
      }
   }

   public static ItemStack takeItems(int count, IInventory inventory, int start, int range, int begin) {
      int[] slots = makeSlotList(start, range, begin);
      return takeItems(count, inventory, slots, -1);
   }

   public static ItemStack takeItems(int count, IInventory inventory, int side) {
      if (inventory instanceof ISidedInventory) {
         ISidedInventory sidedInventory = (ISidedInventory)inventory;
         int[] slots = sidedInventory.getAccessibleSlotsFromSide(side);
         return takeItems(count, inventory, slots, side);
      } else {
         int[] slots = makeSlotList(0, inventory.getSizeInventory(), 0);
         return takeItems(count, inventory, slots, side);
      }
   }

   private static int[] makeSlotList(int start, int range, int begin) {
      if (start >= 0 && range != 0) {
         int[] slots = new int[range];

         for (int n = 0; n < slots.length; n++) {
            slots[n] = start + (n + (begin - start)) % range;
         }

         return slots;
      } else {
         return null;
      }
   }

   private static ItemStack storeItems(ItemStack stack, IInventory inventory, int[] slots, int face) {
      if (slots != null && slots.length != 0) {
         if (stack != null && stack.stackSize != 0) {
            ItemStack remainder = stack;

            for (int n = 0; n < slots.length; n++) {
               int slot = slots[n];
               if (canPlaceItemThroughFace(inventory, slot, remainder, face)) {
                  ItemStack slotContents = inventory.getStackInSlot(slot);
                  if (slotContents == null) {
                     int space = inventory.getInventoryStackLimit();
                     if (space >= remainder.stackSize) {
                        inventory.setInventorySlotContents(slot, remainder);
                        inventory.markDirty();
                        return null;
                     }

                     remainder = remainder.copy();
                     inventory.setInventorySlotContents(slot, remainder.splitStack(space));
                  } else if (areItemsStackable(slotContents, remainder)) {
                     int space = Math.min(slotContents.getMaxStackSize(), inventory.getInventoryStackLimit()) - slotContents.stackSize;
                     if (space >= remainder.stackSize) {
                        slotContents.stackSize = slotContents.stackSize + remainder.stackSize;
                        inventory.setInventorySlotContents(slot, slotContents);
                        inventory.markDirty();
                        return null;
                     }

                     if (space > 0) {
                        remainder = remainder.copy();
                        remainder.stackSize -= space;
                        slotContents.stackSize += space;
                        inventory.setInventorySlotContents(slot, slotContents);
                     }
                  }
               }
            }

            if (remainder != stack) {
               inventory.markDirty();
            }

            return remainder;
         } else {
            return null;
         }
      } else {
         return stack;
      }
   }

   private static boolean canPlaceItemThroughFace(IInventory inventory, int slot, ItemStack itemstack, int face) {
      if (inventory.isItemValidForSlot(slot, itemstack)) {
         if (face >= 0 && inventory instanceof ISidedInventory) {
            ISidedInventory sided = (ISidedInventory)inventory;
            return sided.canInsertItem(slot, itemstack, face);
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   private static ItemStack takeItems(int count, IInventory inventory, int[] slots, int face) {
      if (slots == null) {
         return null;
      } else {
         ItemStack partialStack = null;
         int countRemaining = count;

         for (int n = 0; n < slots.length; n++) {
            int slot = slots[n];
            if (countRemaining > 0) {
               ItemStack stack = inventory.getStackInSlot(slot);
               if (stack != null && canTakeItemThroughFace(inventory, slot, stack, face) && (partialStack == null || areItemsStackable(stack, partialStack))) {
                  if (countRemaining >= stack.stackSize) {
                     inventory.setInventorySlotContents(slot, null);
                     if (partialStack == null) {
                        partialStack = stack;
                        countRemaining = Math.min(countRemaining, stack.getItem().getItemStackLimit(stack)) - stack.stackSize;
                     } else {
                        partialStack.stackSize = partialStack.stackSize + stack.stackSize;
                        countRemaining -= stack.stackSize;
                     }
                  } else {
                     ItemStack splitStack = stack.splitStack(countRemaining);
                     if (partialStack == null) {
                        partialStack = splitStack;
                        countRemaining = Math.min(countRemaining, splitStack.getItem().getItemStackLimit(splitStack)) - splitStack.stackSize;
                     } else {
                        partialStack.stackSize = partialStack.stackSize + splitStack.stackSize;
                        countRemaining -= splitStack.stackSize;
                     }
                  }
               }
            }
         }

         if (partialStack != null) {
            inventory.markDirty();
            return partialStack;
         } else {
            return null;
         }
      }
   }

   private static boolean canTakeItemThroughFace(IInventory inventory, int slot, ItemStack itemstack, int face) {
      if (face >= 0 && inventory instanceof ISidedInventory) {
         ISidedInventory sided = (ISidedInventory)inventory;
         return sided.canExtractItem(slot, itemstack, face);
      } else {
         return true;
      }
   }
}
