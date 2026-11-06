package dan200.computercraft.shared.peripheral.modem;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import dan200.computercraft.ComputerCraft;

/**
 * Item form of {@link BlockWiredModemFull}. It shares the display name "Wired Modem" with the wall-mounted wired modem,
 * exactly as CC:Tweaked does.
 */
public class ItemWiredModemFull extends ItemBlock {

    public ItemWiredModemFull(Block block) {
        super(block);
        this.setUnlocalizedName("computercraft:wired_modem_full");
        this.setCreativeTab(ComputerCraft.mainCreativeTab);
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "tile.computercraft:wired_modem_full";
    }

    @Override
    public void getSubItems(Item item, CreativeTabs tabs, List list) {
        list.add(new ItemStack(this, 1, 0));
    }
}
