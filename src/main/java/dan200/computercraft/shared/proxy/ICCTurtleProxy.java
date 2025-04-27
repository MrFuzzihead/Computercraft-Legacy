package dan200.computercraft.shared.proxy;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.util.Colour;
import dan200.computercraft.shared.util.IEntityDropConsumer;

public interface ICCTurtleProxy {

    void preInit();

    void init();

    void registerTurtleUpgrade(ITurtleUpgrade var1);

    ITurtleUpgrade getTurtleUpgrade(int var1);

    ITurtleUpgrade getTurtleUpgrade(ItemStack var1);

    void addAllUpgradedTurtles(List<ItemStack> var1);

    void setEntityDropConsumer(Entity var1, IEntityDropConsumer var2);

    void clearEntityDropConsumer(Entity var1);

    void getTurtleModelTextures(List<ResourceLocation> var1, ComputerFamily var2, Colour var3);
}
