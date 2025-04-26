package dan200.computercraft.shared.computer.items;

import dan200.computercraft.shared.computer.core.ComputerFamily;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public abstract class ItemComputerBase extends ItemBlock implements IComputerItem {
   protected ItemComputerBase(Block block) {
      super(block);
   }

   public abstract ComputerFamily getFamily(int var1);

   public final int getMetadata(int damage) {
      return damage;
   }

   public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean debug) {
      if (debug) {
         int id = this.getComputerID(stack);
         if (id >= 0) {
            list.add("(Computer ID: " + id + ")");
         }
      }
   }

   @Override
   public abstract int getComputerID(ItemStack var1);

   @Override
   public String getLabel(ItemStack stack) {
      return stack.hasDisplayName() ? stack.getDisplayName() : null;
   }

   @Override
   public final ComputerFamily getFamily(ItemStack stack) {
      int damage = stack.getItemDamage();
      return this.getFamily(damage);
   }
}
