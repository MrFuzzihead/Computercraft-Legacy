package dan200.computercraft.api.turtle;

import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

public interface ITurtleUpgrade {
   int getUpgradeID();

   String getUnlocalisedAdjective();

   TurtleUpgradeType getType();

   ItemStack getCraftingItem();

   IPeripheral createPeripheral(ITurtleAccess var1, TurtleSide var2);

   TurtleCommandResult useTool(ITurtleAccess var1, TurtleSide var2, TurtleVerb var3, int var4);

   IIcon getIcon(ITurtleAccess var1, TurtleSide var2);

   void update(ITurtleAccess var1, TurtleSide var2);
}
