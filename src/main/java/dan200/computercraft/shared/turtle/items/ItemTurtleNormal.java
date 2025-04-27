package dan200.computercraft.shared.turtle.items;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.util.Colour;

public class ItemTurtleNormal extends ItemTurtleBase {

    public ItemTurtleNormal(Block block) {
        super(block);
        this.setUnlocalizedName("computercraft:turtle");
        this.setCreativeTab(ComputerCraft.mainCreativeTab);
    }

    @Override
    public ItemStack create(int id, String label, Colour colour, ITurtleUpgrade leftUpgrade,
        ITurtleUpgrade rightUpgrade, int fuelLevel, ResourceLocation overlay, ResourceLocation hatOverlay) {
        int leftUpgradeID = 0;
        int rightUpgradeID = 0;
        if (leftUpgrade != null) {
            leftUpgradeID = leftUpgrade.getUpgradeID();
        }

        if (rightUpgrade != null) {
            rightUpgradeID = rightUpgrade.getUpgradeID();
        }

        ItemStack stack = new ItemStack(this, 1, 0);
        NBTTagCompound nbt = new NBTTagCompound();
        if (leftUpgradeID > 0) {
            nbt.setShort("leftUpgrade", (short) leftUpgradeID);
        }

        if (rightUpgradeID > 0) {
            nbt.setShort("rightUpgrade", (short) rightUpgradeID);
        }

        if (id >= 0) {
            nbt.setInteger("computerID", id);
        }

        if (fuelLevel > 0) {
            nbt.setInteger("fuelLevel", fuelLevel);
        }

        if (colour != null) {
            nbt.setInteger("colourIndex", colour.ordinal());
        }

        if (overlay != null && hatOverlay != null) {
            nbt.setString("overlay_mod", overlay.getResourceDomain());
            nbt.setString("overlay_path", overlay.getResourcePath());
            nbt.setString("overlay_hatPath", hatOverlay.getResourcePath());
        } else if (overlay != null) {
            nbt.setString("overlay_mod", overlay.getResourceDomain());
            nbt.setString("overlay_path", overlay.getResourcePath());
        } else if (hatOverlay != null) {
            nbt.setString("overlay_mod", hatOverlay.getResourceDomain());
            nbt.setString("overlay_hatPath", hatOverlay.getResourcePath());
        }

        stack.setTagCompound(nbt);
        if (label != null) {
            stack.setStackDisplayName(label);
        }

        return stack;
    }

    @Override
    public int getComputerID(ItemStack stack) {
        if (stack.hasTagCompound()) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt.hasKey("computerID")) {
                return nbt.getInteger("computerID");
            }
        }

        return -1;
    }

    @Override
    public ComputerFamily getFamily(int damage) {
        return ComputerFamily.Normal;
    }

    @Override
    public ITurtleUpgrade getUpgrade(ItemStack stack, TurtleSide side) {
        if (stack.hasTagCompound()) {
            int upgradeID = 0;
            NBTTagCompound nbt = stack.getTagCompound();
            switch (side) {
                case Left:
                    if (nbt.hasKey("leftUpgrade")) {
                        upgradeID = nbt.getShort("leftUpgrade");
                    }
                    break;
                case Right:
                    if (nbt.hasKey("rightUpgrade")) {
                        upgradeID = nbt.getShort("rightUpgrade");
                    }
            }

            if (upgradeID > 0) {
                return ComputerCraft.getTurtleUpgrade(upgradeID);
            }
        }

        return null;
    }

    @Override
    public Colour getColour(ItemStack stack) {
        if (stack.hasTagCompound()) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt.hasKey("colourIndex")) {
                int index = nbt.getInteger("colourIndex") & 15;
                return Colour.values()[index];
            }
        }

        return null;
    }

    @Override
    public ResourceLocation getOverlay(ItemStack stack) {
        if (stack.hasTagCompound()) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt.hasKey("overlay_mod") && nbt.hasKey("overlay_path")) {
                String overlay_mod = nbt.getString("overlay_mod");
                String overlay_path = nbt.getString("overlay_path");
                return new ResourceLocation(overlay_mod, overlay_path);
            }
        }

        return null;
    }

    @Override
    public ResourceLocation getHatOverlay(ItemStack stack) {
        if (stack.hasTagCompound()) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt.hasKey("overlay_mod") && nbt.hasKey("overlay_hatPath")) {
                String overlay_mod = nbt.getString("overlay_mod");
                String overlay_hatPath = nbt.getString("overlay_hatPath");
                return new ResourceLocation(overlay_mod, overlay_hatPath);
            }
        }

        return null;
    }

    @Override
    public int getFuelLevel(ItemStack stack) {
        if (stack.hasTagCompound()) {
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt.hasKey("fuelLevel")) {
                return nbt.getInteger("fuelLevel");
            }
        }

        return 0;
    }
}
