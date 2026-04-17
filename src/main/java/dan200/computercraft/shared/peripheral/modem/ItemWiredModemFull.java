package dan200.computercraft.shared.peripheral.modem;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.ItemPeripheralBase;

/**
 * Item class for {@link BlockWiredModemFull}.
 * Must extend {@link ItemPeripheralBase} so that
 * {@link dan200.computercraft.shared.peripheral.common.BlockPeripheralBase#getItemTexture(int, int)}
 * can safely cast {@code Item.getItemFromBlock(block)} to {@code ItemPeripheralBase}.
 */
public class ItemWiredModemFull extends ItemPeripheralBase {

    public ItemWiredModemFull(Block block) {
        super(block);
        this.setUnlocalizedName("computercraft:wired_modem_full");
        this.setCreativeTab(ComputerCraft.mainCreativeTab);
    }

    @Override
    public PeripheralType getPeripheralType(int damage) {
        return PeripheralType.WiredModemWithCable;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "tile.computercraft:wired_modem_full";
    }

    @Override
    public void getSubItems(net.minecraft.item.Item item, net.minecraft.creativetab.CreativeTabs tabs, List list) {
        list.add(new ItemStack(this, 1, 0));
    }
}

