package dan200.computercraft.shared.turtle.core;

import java.util.Arrays;
import java.util.OptionalInt;

import net.minecraft.item.ItemStack;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleAnimation;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.impl.TurtleRefuelHandlers;

/**
 * {@code turtle.refuel([count])}. Consumes items from the turtle's selected slot and converts them to fuel through the
 * registered {@link dan200.computercraft.api.turtle.TurtleRefuelHandler}s.
 *
 * <p>
 * The command owns the rules that are about the <em>turtle</em> rather than about the item: whether the action is
 * disabled by config, whether there is anything in the selected slot, the "no fuel gained means no animation" rule, and
 * the fuel added. A handler only answers "what is this item worth, and how do I take it".
 * </p>
 */
public class TurtleRefuelCommand implements ITurtleCommand {

    /** The name this command is matched against in {@code ComputerCraft.turtleDisabledActions}. */
    private static final String COMMANDNAME = "refuel";

    private final int m_limit;

    public TurtleRefuelCommand(int limit) {
        this.m_limit = limit;
    }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        if (Arrays.asList(ComputerCraft.turtleDisabledActions)
            .contains(COMMANDNAME)) {
            return TurtleCommandResult.failure("Turtle action \"" + COMMANDNAME + "\" is disabled");
        }

        int slot = turtle.getSelectedSlot();
        ItemStack stack = turtle.getInventory()
            .getStackInSlot(slot);
        if (stack == null) {
            return TurtleCommandResult.failure("No items to combust");
        }

        OptionalInt refuelled = TurtleRefuelHandlers.refuel(turtle, stack, slot, this.m_limit);
        if (!refuelled.isPresent()) {
            return TurtleCommandResult.failure("Items not combustible");
        }

        int fuel = refuelled.getAsInt();
        if (fuel != 0) {
            turtle.addFuel(fuel);
            turtle.playAnimation(TurtleAnimation.Wait);
        }

        return TurtleCommandResult.success();
    }
}
