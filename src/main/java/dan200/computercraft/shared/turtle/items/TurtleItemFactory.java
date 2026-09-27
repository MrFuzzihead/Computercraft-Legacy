package dan200.computercraft.shared.turtle.items;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.core.IComputer;
import dan200.computercraft.shared.turtle.blocks.ITurtleTile;
import dan200.computercraft.shared.util.Colour;

public class TurtleItemFactory {

    public static ItemStack create(ITurtleTile turtle) {
        ITurtleUpgrade leftUpgrade = turtle.getAccess()
            .getUpgrade(TurtleSide.Left);
        ITurtleUpgrade rightUpgrade = turtle.getAccess()
            .getUpgrade(TurtleSide.Right);
        IComputer computer = turtle.getComputer();
        if (computer != null) {
            String label = computer.getLabel();
            if (label != null) {
                if (turtle.getFamily() != ComputerFamily.Beginners) {
                    int id = computer.getID();
                    int fuelLevel = turtle.getAccess()
                        .getFuelLevel();
                    return create(
                        id,
                        label,
                        turtle.getColour(),
                        turtle.getFamily(),
                        leftUpgrade,
                        rightUpgrade,
                        fuelLevel,
                        turtle.getOverlay(),
                        turtle.getHatOverlay());
                }

                return create(
                    -1,
                    label,
                    turtle.getColour(),
                    turtle.getFamily(),
                    leftUpgrade,
                    rightUpgrade,
                    0,
                    turtle.getOverlay(),
                    turtle.getHatOverlay());
            }
        }

        return create(
            -1,
            null,
            turtle.getColour(),
            turtle.getFamily(),
            leftUpgrade,
            rightUpgrade,
            0,
            turtle.getOverlay(),
            turtle.getHatOverlay());
    }

    public static ItemStack create(int id, String label, Colour colour, ComputerFamily family,
        ITurtleUpgrade leftUpgrade, ITurtleUpgrade rightUpgrade, int fuelLevel, ResourceLocation overlay,
        ResourceLocation hatOverlay) {
        switch (family) {
            case Normal:
                ItemTurtleBase legacy = (ItemTurtleBase) Item.getItemFromBlock(ComputerCraft.Blocks.turtle);
                ItemTurtleBase normal = (ItemTurtleBase) Item.getItemFromBlock(ComputerCraft.Blocks.turtleExpanded);
                ItemStack legacyStack = legacy
                    .create(id, label, colour, leftUpgrade, rightUpgrade, fuelLevel, overlay, hatOverlay);
                return legacyStack != null ? legacyStack
                    : normal.create(id, label, colour, leftUpgrade, rightUpgrade, fuelLevel, overlay, hatOverlay);
            case Advanced:
                ItemTurtleBase advanced = (ItemTurtleBase) Item.getItemFromBlock(ComputerCraft.Blocks.turtleAdvanced);
                return advanced.create(id, label, colour, leftUpgrade, rightUpgrade, fuelLevel, overlay, hatOverlay);
            case Beginners:
                // The beginner ("Junior") turtle lives in ComputerCraft:Edu, a separate
                // optional mod that is not a dependency here and so cannot be named directly.
                // This previously reflectively probed
                // dan200.computercraftedu.ComputerCraftEdu$Blocks#turtleJunior, which always
                // resolved to null in practice: that class is absent unless the optional mod
                // happens to be installed, so the branch was dead code reached by a lookup that
                // could never succeed. Restoring support means ComputerCraft:Edu handing this
                // factory an item (an interface registered the way ComputerCraftAPI's providers
                // are), not a reflective field read.
                return null;
            default:
                return null;
        }
    }
}
