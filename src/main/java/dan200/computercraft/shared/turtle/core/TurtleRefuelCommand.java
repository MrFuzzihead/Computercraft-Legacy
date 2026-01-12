package dan200.computercraft.shared.turtle.core;

import java.util.Arrays;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleAnimation;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.util.InventoryUtil;

public class TurtleRefuelCommand implements ITurtleCommand {

    private final int m_limit;
    private final String COMMANDNAME = "refuel";

    public TurtleRefuelCommand(int limit) {
        this.m_limit = limit;
    }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        if (Arrays.asList(ComputerCraft.turtleDisabledActions)
            .contains(COMMANDNAME)) {
            return TurtleCommandResult.failure("Turtle action \"" + COMMANDNAME + "\" is disabled");
        }

        if (this.m_limit == 0) {
            ItemStack dummyStack = turtle.getInventory()
                .getStackInSlot(turtle.getSelectedSlot());
            if (dummyStack != null) {
                return this.refuel(turtle, dummyStack, true);
            }
        } else {
            ItemStack stack = InventoryUtil
                .takeItems(this.m_limit, turtle.getInventory(), turtle.getSelectedSlot(), 1, turtle.getSelectedSlot());
            if (stack != null) {
                TurtleCommandResult result = this.refuel(turtle, stack, false);
                if (!result.isSuccess()) {
                    InventoryUtil.storeItems(
                        stack,
                        turtle.getInventory(),
                        0,
                        turtle.getInventory()
                            .getSizeInventory(),
                        turtle.getSelectedSlot());
                }

                return result;
            }
        }

        return TurtleCommandResult.failure("No items to combust");
    }

    private int getFuelPerItem(ItemStack stack) {
        return TileEntityFurnace.getItemBurnTime(stack) * 5 / 100;
    }

    private TurtleCommandResult refuel(ITurtleAccess turtle, ItemStack stack, boolean testOnly) {
        int fuelPerItem = this.getFuelPerItem(stack);
        if (fuelPerItem <= 0) {
            return TurtleCommandResult.failure("Items not combustible");
        } else {
            if (!testOnly) {
                int fuelToGive = fuelPerItem * stack.stackSize;
                ItemStack replacementStack = stack.getItem()
                    .getContainerItem(stack);
                turtle.addFuel(fuelToGive);
                if (replacementStack != null) {
                    InventoryUtil.storeItems(
                        replacementStack,
                        turtle.getInventory(),
                        0,
                        turtle.getInventory()
                            .getSizeInventory(),
                        turtle.getSelectedSlot());
                }

                turtle.playAnimation(TurtleAnimation.Wait);
            }

            return TurtleCommandResult.success();
        }
    }
}
