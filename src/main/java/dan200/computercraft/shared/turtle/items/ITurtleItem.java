package dan200.computercraft.shared.turtle.items;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.shared.computer.items.IComputerItem;
import dan200.computercraft.shared.util.Colour;

public interface ITurtleItem extends IComputerItem {

    ITurtleUpgrade getUpgrade(ItemStack var1, TurtleSide var2);

    int getFuelLevel(ItemStack var1);

    Colour getColour(ItemStack var1);

    ResourceLocation getOverlay(ItemStack var1);

    ResourceLocation getHatOverlay(ItemStack var1);
}
