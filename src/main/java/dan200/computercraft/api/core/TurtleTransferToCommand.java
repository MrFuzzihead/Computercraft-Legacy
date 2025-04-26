package dan200.computercraft.api.core;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleAnimation;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.util.InventoryUtil;
import net.minecraft.item.ItemStack;

public class TurtleTransferToCommand implements ITurtleCommand {
   private final int m_slot;
   private final int m_quantity;

   public TurtleTransferToCommand(int slot, int limit) {
      this.m_slot = slot;
      this.m_quantity = limit;
   }

   @Override
   public TurtleCommandResult execute(ITurtleAccess turtle) {
      ItemStack stack = InventoryUtil.takeItems(this.m_quantity, turtle.getInventory(), turtle.getSelectedSlot(), 1, turtle.getSelectedSlot());
      if (stack == null) {
         turtle.playAnimation(TurtleAnimation.Wait);
         return TurtleCommandResult.success();
      } else {
         ItemStack remainder = InventoryUtil.storeItems(stack, turtle.getInventory(), this.m_slot, 1, this.m_slot);
         if (remainder != null) {
            InventoryUtil.storeItems(remainder, turtle.getInventory(), turtle.getSelectedSlot(), 1, turtle.getSelectedSlot());
         }

         if (remainder != stack) {
            turtle.playAnimation(TurtleAnimation.Wait);
            return TurtleCommandResult.success();
         } else {
            return TurtleCommandResult.failure("No space for items");
         }
      }
   }
}
