package dan200.computercraft.shared.turtle.core;

import java.util.Arrays;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleAnimation;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.util.DirectionUtil;

public class TurtleTurnCommand implements ITurtleCommand {

    private final TurnDirection m_direction;
    private final String COMMANDNAME = "turn";

    public TurtleTurnCommand(TurnDirection direction) {
        this.m_direction = direction;
    }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        if (Arrays.asList(ComputerCraft.turtleDisabledActions)
            .contains(COMMANDNAME)) {
            return TurtleCommandResult.failure("Turtle action \"" + COMMANDNAME + "\" is disabled");
        }

        switch (this.m_direction) {
            case Left:
                turtle.setDirection(DirectionUtil.rotateLeft(turtle.getDirection()));
                turtle.playAnimation(TurtleAnimation.TurnLeft);
                return TurtleCommandResult.success();
            case Right:
                turtle.setDirection(DirectionUtil.rotateRight(turtle.getDirection()));
                turtle.playAnimation(TurtleAnimation.TurnRight);
                return TurtleCommandResult.success();
            default:
                return TurtleCommandResult.failure("Unknown direction");
        }
    }
}
