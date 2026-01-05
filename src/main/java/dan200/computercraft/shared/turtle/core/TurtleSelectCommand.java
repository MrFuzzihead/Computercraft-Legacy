package dan200.computercraft.shared.turtle.core;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleCommandResult;

import java.util.Arrays;

public class TurtleSelectCommand implements ITurtleCommand {

    private final int m_slot;
    private final String COMMANDNAME = "select";

    public TurtleSelectCommand(int slot) {
        this.m_slot = slot;
    }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        if (Arrays.asList(ComputerCraft.turtleDisabledActions).contains(COMMANDNAME)) {
            return TurtleCommandResult.failure("Turtle action \"" + COMMANDNAME + "\" is disabled");
        }

        turtle.setSelectedSlot(this.m_slot);
        return TurtleCommandResult.success();
    }
}
