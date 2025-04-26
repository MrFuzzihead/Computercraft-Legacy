package dan200.computercraft.shared.util;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.world.World;

public class ImpostorRecipe extends ShapedRecipes {
   public ImpostorRecipe(int width, int height, ItemStack[] ingredients, ItemStack result) {
      super(width, height, ingredients, result);
   }

   public boolean matches(InventoryCrafting inv, World world) {
      return false;
   }

   public ItemStack getCraftingResult(InventoryCrafting _inventory) {
      return null;
   }
}
