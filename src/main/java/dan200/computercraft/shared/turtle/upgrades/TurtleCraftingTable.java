package dan200.computercraft.shared.turtle.upgrades;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleUpgradeType;
import dan200.computercraft.api.turtle.TurtleVerb;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

public class TurtleCraftingTable implements ITurtleUpgrade {
   private int m_id;
   private ItemStack m_item;

   public TurtleCraftingTable(int id) {
      this.m_id = id;
      this.m_item = new ItemStack(Blocks.crafting_table, 1, 0);
   }

   @Override
   public int getUpgradeID() {
      return this.m_id;
   }

   @Override
   public String getUnlocalisedAdjective() {
      return "upgrade.minecraft:crafting_table.adjective";
   }

   @Override
   public TurtleUpgradeType getType() {
      return TurtleUpgradeType.Peripheral;
   }

   @Override
   public ItemStack getCraftingItem() {
      return this.m_item;
   }

   @Override
   public IIcon getIcon(ITurtleAccess turtle, TurtleSide side) {
      return ComputerCraft.Blocks.peripheral.s_craftyUpgradeIcon;
   }

   @Override
   public IPeripheral createPeripheral(ITurtleAccess turtle, TurtleSide side) {
      return new CraftingTablePeripheral(turtle);
   }

   @Override
   public TurtleCommandResult useTool(ITurtleAccess turtle, TurtleSide side, TurtleVerb verb, int dir) {
      return null;
   }

   @Override
   public void update(ITurtleAccess turtle, TurtleSide side) {
   }
}
