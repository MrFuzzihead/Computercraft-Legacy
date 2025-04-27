package dan200.computercraft.shared.turtle.recipes;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.proxy.CCTurtleProxyCommon;
import dan200.computercraft.shared.turtle.items.ITurtleItem;
import dan200.computercraft.shared.turtle.items.TurtleItemFactory;
import dan200.computercraft.shared.util.Colour;

public class TurtleUpgradeRecipe implements IRecipe {

    public int getRecipeSize() {
        return 3;
    }

    public ItemStack getRecipeOutput() {
        return TurtleItemFactory.create(-1, null, null, ComputerFamily.Normal, null, null, 0, null, null);
    }

    public boolean matches(InventoryCrafting inventory, World world) {
        return this.getCraftingResult(inventory) != null;
    }

    public ItemStack getCraftingResult(InventoryCrafting inventory) {
        ItemStack leftItem = null;
        ItemStack turtle = null;
        ItemStack rightItem = null;

        for (int y = 0; y < 3; y++) {
            if (turtle != null) {
                for (int x = 0; x < 3; x++) {
                    ItemStack item = inventory.getStackInRowAndColumn(x, y);
                    if (item != null) {
                        return null;
                    }
                }
            } else {
                boolean finishedRow = false;

                for (int xx = 0; xx < 3; xx++) {
                    ItemStack item = inventory.getStackInRowAndColumn(xx, y);
                    if (item != null) {
                        if (finishedRow) {
                            return null;
                        }

                        if (item.getItem() instanceof ITurtleItem) {
                            if (turtle != null) {
                                return null;
                            }

                            turtle = item;
                        } else if (turtle == null && leftItem == null) {
                            leftItem = item;
                        } else {
                            if (turtle == null || rightItem != null) {
                                return null;
                            }

                            rightItem = item;
                        }
                    } else if (leftItem != null || turtle != null) {
                        finishedRow = true;
                    }
                }

                if (turtle == null && (leftItem != null || rightItem != null)) {
                    return null;
                }
            }
        }

        if (turtle != null && (leftItem != null || rightItem != null)) {
            ITurtleItem itemTurtle = (ITurtleItem) turtle.getItem();
            ComputerFamily family = itemTurtle.getFamily(turtle);
            ITurtleUpgrade[] upgrades = new ITurtleUpgrade[] { itemTurtle.getUpgrade(turtle, TurtleSide.Left),
                itemTurtle.getUpgrade(turtle, TurtleSide.Right) };
            ItemStack[] items = new ItemStack[] { rightItem, leftItem };

            for (int i = 0; i < 2; i++) {
                if (items[i] != null) {
                    ITurtleUpgrade itemUpgrade = ComputerCraft.getTurtleUpgrade(items[i]);
                    if (itemUpgrade == null) {
                        return null;
                    }

                    if (upgrades[i] != null) {
                        return null;
                    }

                    if (!CCTurtleProxyCommon.isUpgradeSuitableForFamily(family, itemUpgrade)) {
                        return null;
                    }

                    upgrades[i] = itemUpgrade;
                }
            }

            int computerID = itemTurtle.getComputerID(turtle);
            String label = itemTurtle.getLabel(turtle);
            int fuelLevel = itemTurtle.getFuelLevel(turtle);
            Colour colour = itemTurtle.getColour(turtle);
            ResourceLocation overlay = itemTurtle.getOverlay(turtle);
            ResourceLocation hatOverlay = itemTurtle.getHatOverlay(turtle);
            return TurtleItemFactory
                .create(computerID, label, colour, family, upgrades[0], upgrades[1], fuelLevel, overlay, hatOverlay);
        } else {
            return null;
        }
    }
}
