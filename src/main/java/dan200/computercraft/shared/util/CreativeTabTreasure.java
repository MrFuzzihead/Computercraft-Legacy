package dan200.computercraft.shared.util;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import dan200.computercraft.ComputerCraft;

public class CreativeTabTreasure extends CreativeTabs {

    public CreativeTabTreasure(int i) {
        super(i, "Treasure Disks");
    }

    public Item getTabIconItem() {
        return ComputerCraft.Items.treasureDisk;
    }

    public String getTranslatedTabLabel() {
        return this.getTabLabel();
    }
}
