package dan200.computercraft.shared.turtle.core;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleCommandResult;

public class TurtleSelectCommand implements ITurtleCommand {

    private final int m_slot;

    public TurtleSelectCommand(int slot) {
        this.m_slot = slot;
    }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        turtle.setSelectedSlot(this.m_slot);
        return TurtleCommandResult.success();
    }
}
