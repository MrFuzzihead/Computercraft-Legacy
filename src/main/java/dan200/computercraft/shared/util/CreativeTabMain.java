package dan200.computercraft.shared.util;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import dan200.computercraft.ComputerCraft;

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
