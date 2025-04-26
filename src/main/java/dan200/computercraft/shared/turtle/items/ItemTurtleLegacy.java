package dan200.computercraft.shared.turtle.items;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.items.ItemComputer;
import dan200.computercraft.shared.util.Colour;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class ItemTurtleLegacy extends ItemTurtleBase {
   public ItemTurtleLegacy(Block block) {
      super(block);
      this.setUnlocalizedName("computercraft:turtle");
      this.setCreativeTab(ComputerCraft.mainCreativeTab);
   }

   @Override
   public ItemStack create(
      int id,
      String label,
      Colour colour,
      ITurtleUpgrade leftUpgrade,
      ITurtleUpgrade rightUpgrade,
      int fuelLevel,
      ResourceLocation overlay,
      ResourceLocation hatOverlay
   ) {
      if ((leftUpgrade == null || leftUpgrade.getUpgradeID() == 5)
         && (rightUpgrade == null || rightUpgrade.getUpgradeID() == 1)
         && colour == null
         && overlay == null
         && hatOverlay == null) {
         int subType = 0;
         if (leftUpgrade != null) {
            subType++;
         }

         if (rightUpgrade != null) {
            subType += 2;
         }

         int damage = subType;
         if (id >= 0 && id <= ItemComputer.HIGHEST_DAMAGE_VALUE_ID) {
            damage = subType + (id + 1 << 2);
         }

         ItemStack stack = new ItemStack(this, 1, damage);
         if (fuelLevel > 0 || id > ItemComputer.HIGHEST_DAMAGE_VALUE_ID) {
            NBTTagCompound nbt = new NBTTagCompound();
            if (fuelLevel > 0) {
               nbt.setInteger("fuelLevel", fuelLevel);
            }

            if (id > ItemComputer.HIGHEST_DAMAGE_VALUE_ID) {
               nbt.setInteger("computerID", id);
            }

            stack.setTagCompound(nbt);
         }

         if (label != null) {
            stack.setStackDisplayName(label);
         }

         return stack;
      } else {
         return null;
      }
   }

   @Override
   public int getComputerID(ItemStack stack) {
      if (stack.hasTagCompound() && stack.getTagCompound().hasKey("computerID")) {
         return stack.getTagCompound().getInteger("computerID");
      } else {
         int damage = stack.getItemDamage();
         return ((damage & 65532) >> 2) - 1;
      }
   }

   @Override
   public ComputerFamily getFamily(int damage) {
      return ComputerFamily.Normal;
   }

   @Override
   public ITurtleUpgrade getUpgrade(ItemStack stack, TurtleSide side) {
      int damage = stack.getItemDamage();
      switch (side) {
         case Left:
            if ((damage & 1) > 0) {
               return ComputerCraft.getTurtleUpgrade(5);
            }
            break;
         case Right:
            if ((damage & 2) > 0) {
               return ComputerCraft.getTurtleUpgrade(1);
            }
      }

      return null;
   }

   @Override
   public Colour getColour(ItemStack stack) {
      return null;
   }

   @Override
   public ResourceLocation getOverlay(ItemStack stack) {
      return null;
   }

   @Override
   public ResourceLocation getHatOverlay(ItemStack stack) {
      return null;
   }

   @Override
   public int getFuelLevel(ItemStack stack) {
      if (stack.hasTagCompound()) {
         NBTTagCompound nbt = stack.getTagCompound();
         return nbt.getInteger("fuelLevel");
      } else {
         return 0;
      }
   }
}
