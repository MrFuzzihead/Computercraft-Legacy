package dan200.computercraft.shared.turtle.core;

import net.minecraft.item.ItemStack;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.util.InventoryUtil;

public class TurtleCompareToCommand implements ITurtleCommand {

    private final int m_slot;

    public TurtleCompareToCommand(int slot) {
        this.m_slot = slot;
    }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        ItemStack selectedStack = turtle.getInventory()
            .getStackInSlot(turtle.getSelectedSlot());
        ItemStack stack = turtle.getInventory()
            .getStackInSlot(this.m_slot);
        return InventoryUtil.areItemsStackable(selectedStack, stack) ? TurtleCommandResult.success()
            : TurtleCommandResult.failure();
    }
}
