package dan200.computercraft.shared.pocket.recipes;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.pocket.items.ItemPocketComputer;
import dan200.computercraft.shared.pocket.items.PocketComputerItemFactory;

/**
 * Crafting recipe: Speaker (above) + Pocket Computer → Speaker Pocket Computer.
 * Mirrors {@link PocketComputerEnderUpgradeRecipe} but uses the Speaker block
 * and produces a pocket computer with {@code upgrade=3}.
 */
public class PocketComputerSpeakerUpgradeRecipe implements IRecipe {

    public int getRecipeSize() {
        return 2;
    }

    public ItemStack getRecipeOutput() {
        return PocketComputerItemFactory.createWithSpeaker(-1, null, ComputerFamily.Normal);
    }

    public boolean matches(InventoryCrafting inventory, World world) {
        return this.getCraftingResult(inventory) != null;
    }

    public ItemStack getCraftingResult(InventoryCrafting inventory) {
        ItemStack computer = null;
        int computerX = -1;
        int computerY = -1;

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                ItemStack item = inventory.getStackInRowAndColumn(x, y);
                if (item != null && item.getItem() instanceof ItemPocketComputer) {
                    computer = item;
                    computerX = x;
                    computerY = y;
                    break;
                }
            }

            if (computer != null) {
                break;
            }
        }

        if (computer == null) {
            return null;
        }

        ItemStack upgrade = null;

        for (int y = 0; y < 3; y++) {
            for (int xx = 0; xx < 3; xx++) {
                ItemStack item = inventory.getStackInRowAndColumn(xx, y);
                if (xx != computerX || y != computerY) {
                    if (xx == computerX && y == computerY - 1) {
                        if (item == null || item.getItem() != Item.getItemFromBlock(ComputerCraft.Blocks.speaker)) {
                            return null;
                        }

                        upgrade = item;
                    } else if (item != null) {
                        return null;
                    }
                }
            }
        }

        if (upgrade == null) {
            return null;
        }

        ItemPocketComputer itemComputer = (ItemPocketComputer) computer.getItem();
        // Prevent double-upgrading
        if (itemComputer.getHasModem(computer) || itemComputer.getHasEnderModem(computer)
            || itemComputer.getHasSpeaker(computer)) {
            return null;
        }

        ComputerFamily family = itemComputer.getFamily(computer);
        int computerID = itemComputer.getComputerID(computer);
        String label = itemComputer.getLabel(computer);
        return PocketComputerItemFactory.createWithSpeaker(computerID, label, family);
    }
}
