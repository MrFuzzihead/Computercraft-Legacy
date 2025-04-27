package dan200.computercraft.shared.util;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;

public interface IEntityDropConsumer {

    void consumeDrop(Entity var1, ItemStack var2);
}
