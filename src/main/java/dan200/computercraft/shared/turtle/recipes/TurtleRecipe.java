package dan200.computercraft.shared.turtle.recipes;

import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.items.IComputerItem;
import dan200.computercraft.shared.turtle.items.TurtleItemFactory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

public class TurtleRecipe implements IRecipe {
   private final Item[] m_recipe;
   private final ComputerFamily m_family;

   public TurtleRecipe(Item[] recipe, ComputerFamily family) {
      this.m_recipe = recipe;
      this.m_family = family;
   }

   public int getRecipeSize() {
      return 9;
   }

   public ItemStack getRecipeOutput() {
      return TurtleItemFactory.create(-1, null, null, this.m_family, null, null, 0, null, null);
   }

   public boolean matches(InventoryCrafting _inventory, World world) {
      return this.getCraftingResult(_inventory) != null;
   }

   public ItemStack getCraftingResult(InventoryCrafting inventory) {
      int computerID = -1;
      String label = null;

      for (int y = 0; y < 3; y++) {
         for (int x = 0; x < 3; x++) {
            ItemStack item = inventory.getStackInRowAndColumn(x, y);
            if (item == null || item.getItem() != this.m_recipe[x + y * 3]) {
               return null;
            }

            if (item.getItem() instanceof IComputerItem) {
               IComputerItem itemComputer = (IComputerItem)item.getItem();
               if (this.m_family != ComputerFamily.Beginners && itemComputer.getFamily(item) != this.m_family) {
                  return null;
               }

               computerID = itemComputer.getComputerID(item);
               label = itemComputer.getLabel(item);
            }
         }
      }

      return this.m_family != ComputerFamily.Beginners
         ? TurtleItemFactory.create(computerID, label, null, this.m_family, null, null, 0, null, null)
         : TurtleItemFactory.create(-1, label, null, this.m_family, null, null, 0, null, null);
   }
}
