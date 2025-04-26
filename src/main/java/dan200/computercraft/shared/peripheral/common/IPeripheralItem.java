package dan200.computercraft.shared.peripheral.common;

import dan200.computercraft.shared.peripheral.PeripheralType;
import net.minecraft.item.ItemStack;

public interface IPeripheralItem {
   PeripheralType getPeripheralType(ItemStack var1);
}
