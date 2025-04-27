package dan200.computercraft.shared.media.recipes;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

import dan200.computercraft.shared.media.items.ItemPrintout;

public class PrintoutRecipe implements IRecipe {

    public int getRecipeSize() {
        return 3;
    }

    public ItemStack getRecipeOutput() {
        return ItemPrintout.createMultipleFromTitleAndText(null, null, null);
    }

    public boolean matches(InventoryCrafting _inventory, World world) {
        return this.getCraftingResult(_inventory) != null;
    }

    public ItemStack getCraftingResult(InventoryCrafting inventory) {
        int numPages = 0;
        int numPrintouts = 0;
        ItemStack[] printouts = null;
        boolean stringFound = false;
        boolean leatherFound = false;
        boolean printoutFound = false;

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                ItemStack stack = inventory.getStackInRowAndColumn(x, y);
                if (stack != null) {
                    Item item = stack.getItem();
                    if (item instanceof ItemPrintout && ItemPrintout.getType(stack) != ItemPrintout.Type.Book) {
                        if (printouts == null) {
                            printouts = new ItemStack[9];
                        }

                        printouts[numPrintouts] = stack;
                        numPages += ItemPrintout.getPageCount(stack);
                        numPrintouts++;
                        printoutFound = true;
                    } else if (item == Items.paper) {
                        if (printouts == null) {
                            printouts = new ItemStack[9];
                        }

                        printouts[numPrintouts] = stack;
                        numPages++;
                        numPrintouts++;
                    } else if (item == Items.string && !stringFound) {
                        stringFound = true;
                    } else {
                        if (item != Items.leather || leatherFound) {
                            return null;
                        }

                        leatherFound = true;
                    }
                }
            }
        }

        if (numPages <= 16 && stringFound && printoutFound && numPrintouts >= (leatherFound ? 1 : 2)) {
            String[] text = new String[numPages * 21];
            String[] colours = new String[numPages * 21];
            int line = 0;

            for (int printout = 0; printout < numPrintouts; printout++) {
                ItemStack stack = printouts[printout];
                if (stack.getItem() instanceof ItemPrintout) {
                    String[] pageText = ItemPrintout.getText(printouts[printout]);
                    String[] pageColours = ItemPrintout.getColours(printouts[printout]);

                    for (int pageLine = 0; pageLine < pageText.length; pageLine++) {
                        text[line] = pageText[pageLine];
                        colours[line] = pageColours[pageLine];
                        line++;
                    }
                } else {
                    for (int pageLine = 0; pageLine < 21; pageLine++) {
                        text[line] = "";
                        colours[line] = "";
                        line++;
                    }
                }
            }

            String title = null;
            if (printouts[0].getItem() instanceof ItemPrintout) {
                title = ItemPrintout.getTitle(printouts[0]);
            }

            return leatherFound ? ItemPrintout.createBookFromTitleAndText(title, text, colours)
                : ItemPrintout.createMultipleFromTitleAndText(title, text, colours);
        } else {
            return null;
        }
    }
}
