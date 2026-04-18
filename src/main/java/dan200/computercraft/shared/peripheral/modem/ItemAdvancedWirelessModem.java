package dan200.computercraft.shared.peripheral.modem;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.ItemPeripheralBase;

public class ItemAdvancedWirelessModem extends ItemPeripheralBase {

    public ItemAdvancedWirelessModem(Block block) {
        super(block);
        this.setUnlocalizedName("computercraft:advanced_wireless_modem");
        this.setCreativeTab(ComputerCraft.mainCreativeTab);
    }

    @Override
    public PeripheralType getPeripheralType(int damage) {
        return PeripheralType.AdvancedWirelessModem;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "tile.computercraft:advanced_wireless_modem";
    }

    @Override
    public boolean func_150936_a(World world, int x, int y, int z, int side, EntityPlayer player, ItemStack stack) {
        return world.isSideSolid(x, y, z, ForgeDirection.getOrientation(side));
    }

    @Override
    public void getSubItems(net.minecraft.item.Item itemID, net.minecraft.creativetab.CreativeTabs tabs, List list) {
        list.add(new ItemStack(this, 1, 0));
    }
}
