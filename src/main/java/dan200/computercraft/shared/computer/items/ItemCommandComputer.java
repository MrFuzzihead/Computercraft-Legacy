package dan200.computercraft.shared.computer.items;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class ItemCommandComputer extends ItemComputer {
   public ItemCommandComputer(Block block) {
      super(block);
      this.setMaxStackSize(64);
      this.setHasSubtypes(true);
      this.setUnlocalizedName("computercraft:command_computer");
      this.setCreativeTab(ComputerCraft.mainCreativeTab);
   }

   @Override
   public ItemStack create(int id, String label, ComputerFamily family) {
      if (family != ComputerFamily.Command) {
         return null;
      } else {
         ItemStack result = new ItemStack(this, 1, 0);
         NBTTagCompound nbt = new NBTTagCompound();
         nbt.setInteger("computerID", id);
         result.setTagCompound(nbt);
         if (label != null) {
            result.setStackDisplayName(label);
         }

         return result;
      }
   }

   @Override
   public void getSubItems(Item itemID, CreativeTabs tabs, List list) {
      list.add(ComputerItemFactory.create(-1, null, ComputerFamily.Command));
   }

   @Override
   public int getComputerID(ItemStack stack) {
      return stack.hasTagCompound() && stack.getTagCompound().hasKey("computerID") ? stack.getTagCompound().getInteger("computerID") : -1;
   }

   @Override
   public ComputerFamily getFamily(int damage) {
      return ComputerFamily.Command;
   }
}
