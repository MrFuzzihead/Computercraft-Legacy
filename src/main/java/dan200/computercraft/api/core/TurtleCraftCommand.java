package dan200.computercraft.api.core;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleAnimation;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.turtle.upgrades.TurtleInventoryCrafting;
import dan200.computercraft.shared.util.InventoryUtil;
import dan200.computercraft.shared.util.WorldUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;

public class TurtleCraftCommand implements ITurtleCommand {
   private final int m_limit;

   public TurtleCraftCommand(int limit) {
      this.m_limit = limit;
   }

   @Override
   public TurtleCommandResult execute(ITurtleAccess turtle) {
      TurtleInventoryCrafting crafting = new TurtleInventoryCrafting(turtle);
      ItemStack stack = crafting.doCrafting(turtle.getWorld(), this.m_limit);
      if (stack != null) {
         if (stack.stackSize > 0) {
            ItemStack remainder = InventoryUtil.storeItems(stack, turtle.getInventory(), 0, turtle.getInventory().getSizeInventory(), turtle.getSelectedSlot());
            if (remainder != null) {
               ChunkCoordinates position = turtle.getPosition();
               WorldUtil.dropItemStack(
                  remainder, turtle.getWorld(), position.posX, position.posY, position.posZ, turtle.getDirection()
               );
            }

            turtle.playAnimation(TurtleAnimation.Wait);
         }

         return TurtleCommandResult.success();
      } else {
         return TurtleCommandResult.failure("No matching recipes");
      }
   }
}
