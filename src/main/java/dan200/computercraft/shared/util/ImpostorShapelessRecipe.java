package dan200.computercraft.shared.util;

import java.util.ArrayList;
import java.util.Arrays;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.world.World;

public class ImpostorShapelessRecipe extends ShapelessRecipes {

    public ImpostorShapelessRecipe(ItemStack result, Object[] ingredients) {
        super(result, new ArrayList(Arrays.asList(ingredients)));
    }

    public boolean matches(InventoryCrafting inv, World world) {
        return false;
    }

    public ItemStack getCraftingResult(InventoryCrafting _inventory) {
        return null;
    }
}
