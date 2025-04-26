package dan200.computercraft.shared.computer.items;

import dan200.computercraft.shared.computer.core.ComputerFamily;
import net.minecraft.item.ItemStack;

public interface IComputerItem {
   int getComputerID(ItemStack var1);

   String getLabel(ItemStack var1);

   ComputerFamily getFamily(ItemStack var1);
}
