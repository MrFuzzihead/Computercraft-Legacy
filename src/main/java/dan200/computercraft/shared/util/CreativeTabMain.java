package dan200.computercraft.shared.util;

import dan200.computercraft.ComputerCraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

public class CreativeTabMain extends CreativeTabs {
   public CreativeTabMain(int i) {
      super(i, "ComputerCraft");
   }

   public Item getTabIconItem() {
      return Item.getItemFromBlock(ComputerCraft.Blocks.computer);
   }

   public String getTranslatedTabLabel() {
      return this.getTabLabel();
   }
}
