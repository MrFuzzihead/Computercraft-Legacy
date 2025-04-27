package dan200.computercraft.shared.media.recipes;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

import dan200.computercraft.shared.media.items.ItemDiskLegacy;
import dan200.computercraft.shared.util.Colour;

public class DiskRecipe implements IRecipe {

    public boolean matches(InventoryCrafting inventory, World world) {
        boolean diskFound = false;
        boolean paperFound = false;
        boolean redstoneFound = false;
        boolean dyeFound = false;

        for (int var5 = 0; var5 < inventory.getSizeInventory(); var5++) {
            ItemStack var6 = inventory.getStackInSlot(var5);
            if (var6 != null) {
                if (var6.getItem() instanceof ItemDiskLegacy) {
                    if (diskFound || redstoneFound || paperFound) {
                        return false;
                    }

                    diskFound = true;
                } else if (var6.getItem() == Items.dye) {
                    dyeFound = true;
                } else if (var6.getItem() == Items.paper) {
                    if (paperFound || diskFound) {
                        return false;
                    }

                    paperFound = true;
                } else {
                    if (var6.getItem() != Items.redstone) {
                        return false;
                    }

                    if (redstoneFound || diskFound) {
                        return false;
                    }

                    redstoneFound = true;
                }
            }
        }

        return redstoneFound && paperFound || diskFound && dyeFound;
    }

    public ItemStack getCraftingResult(InventoryCrafting par1InventoryCrafting) {
        int diskID = -1;
        String diskLabel = null;
        int[] var3 = new int[3];
        int var4 = 0;
        int var5 = 0;
        ItemDiskLegacy var6 = null;
        boolean dyeFound = false;

        for (int var7 = 0; var7 < par1InventoryCrafting.getSizeInventory(); var7++) {
            ItemStack var8 = par1InventoryCrafting.getStackInSlot(var7);
            if (var8 != null) {
                if (var8.getItem() instanceof ItemDiskLegacy) {
                    var6 = (ItemDiskLegacy) var8.getItem();
                    diskID = var6.getDiskID(var8);
                    diskLabel = var6.getLabel(var8);
                } else if (var8.getItem() == Items.dye) {
                    dyeFound = true;
                    float[] var14 = Colour.values()[var8.getItemDamage() & 15].getRGB();
                    int var16 = (int) (var14[0] * 255.0F);
                    int var15 = (int) (var14[1] * 255.0F);
                    int var17 = (int) (var14[2] * 255.0F);
                    var4 += Math.max(var16, Math.max(var15, var17));
                    var3[0] += var16;
                    var3[1] += var15;
                    var3[2] += var17;
                    var5++;
                } else if (var8.getItem() == Items.paper && var8.getItem() == Items.redstone) {
                    return null;
                }
            }
        }

        if (!dyeFound) {
            return ItemDiskLegacy.createFromIDAndColour(diskID, diskLabel, Colour.Blue.getHex());
        } else {
            int var19 = var3[0] / var5;
            int var13 = var3[1] / var5;
            int var9 = var3[2] / var5;
            float var10 = (float) var4 / var5;
            float var11 = Math.max(var19, Math.max(var13, var9));
            var19 = (int) (var19 * var10 / var11);
            var13 = (int) (var13 * var10 / var11);
            var9 = (int) (var9 * var10 / var11);
            int var17 = (var19 << 8) + var13;
            var17 = (var17 << 8) + var9;
            return ItemDiskLegacy.createFromIDAndColour(diskID, diskLabel, var17);
        }
    }

    public int getRecipeSize() {
        return 2;
    }

    public ItemStack getRecipeOutput() {
        return ItemDiskLegacy.createFromIDAndColour(-1, null, Colour.Blue.getHex());
    }
}
