package dan200.computercraft.shared.peripheral.common;

import net.minecraft.item.ItemStack;

import dan200.computercraft.shared.peripheral.PeripheralType;

public interface IPeripheralItem {

    PeripheralType getPeripheralType(ItemStack var1);
}
