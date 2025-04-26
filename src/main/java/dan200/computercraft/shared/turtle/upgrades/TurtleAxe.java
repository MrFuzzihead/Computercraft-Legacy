package dan200.computercraft.shared.turtle.upgrades;

import net.minecraft.item.Item;

public class TurtleAxe extends TurtleTool {
   public TurtleAxe(int id, String adjective, Item item) {
      super(id, adjective, item);
   }

   @Override
   protected float getDamageMultiplier() {
      return 6.0F;
   }
}
