package dan200.computercraft.shared.turtle.upgrades;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.world.World;

public class TurtleSword extends TurtleTool {
   public TurtleSword(int id, String adjective, Item item) {
      super(id, adjective, item);
   }

   @Override
   protected boolean canBreakBlock(World world, int x, int y, int z) {
      if (!super.canBreakBlock(world, x, y, z)) {
         return false;
      } else {
         Block block = world.getBlock(x, y, z);
         return block.getMaterial() == Material.plants
            || block.getMaterial() == Material.leaves
            || block.getMaterial() == Material.vine
            || block.getMaterial() == Material.cloth
            || block.getMaterial() == Material.web;
      }
   }

   @Override
   protected float getDamageMultiplier() {
      return 9.0F;
   }
}
