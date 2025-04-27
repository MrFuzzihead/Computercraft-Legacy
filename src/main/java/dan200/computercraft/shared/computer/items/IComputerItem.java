package dan200.computercraft.shared.computer.items;

import net.minecraft.item.ItemStack;

import dan200.computercraft.shared.computer.core.ComputerFamily;

public interface IComputerItem {

    int getComputerID(ItemStack var1);

    String getLabel(ItemStack var1);

    ComputerFamily getFamily(ItemStack var1);
}
