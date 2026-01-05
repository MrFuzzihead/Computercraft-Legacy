package dan200.computercraft.shared.turtle.core;

import dan200.computercraft.ComputerCraft;
import net.minecraft.item.ItemStack;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.util.InventoryUtil;

import java.util.Arrays;

public class TurtleCompareToCommand implements ITurtleCommand {

    private final int m_slot;
    private final String COMMANDNAME = "compareTo";

    public TurtleCompareToCommand(int slot) {
        this.m_slot = slot;
    }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        if (Arrays.asList(ComputerCraft.turtleDisabledActions).contains(COMMANDNAME)) {
            return TurtleCommandResult.failure("Turtle action \"" + COMMANDNAME + "\" is disabled");
        }

        ItemStack selectedStack = turtle.getInventory()
            .getStackInSlot(turtle.getSelectedSlot());
        ItemStack stack = turtle.getInventory()
            .getStackInSlot(this.m_slot);
        return InventoryUtil.areItemsStackable(selectedStack, stack) ? TurtleCommandResult.success()
            : TurtleCommandResult.failure();
    }
}
