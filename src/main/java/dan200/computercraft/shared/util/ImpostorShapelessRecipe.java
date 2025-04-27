package dan200.computercraft.shared.util;

import java.util.ArrayList;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.world.World;

public class ImpostorShapelessRecipe extends ShapelessRecipes {

    public ImpostorShapelessRecipe(ItemStack result, ArrayList<ItemStack> ingredients) {
        super(result, ingredients);
    }

    public boolean matches(InventoryCrafting inv, World world) {
        return false;
    }

    public ItemStack getCraftingResult(InventoryCrafting _inventory) {
        return null;
    }
}
