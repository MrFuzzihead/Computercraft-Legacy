package dan200.computercraft.shared.turtle.core;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.core.apis.IAPIEnvironment;

public class TurtleCheckRedstoneCommand implements ITurtleCommand {

    private final IAPIEnvironment m_environment;
    private final InteractDirection m_direction;

    public TurtleCheckRedstoneCommand(IAPIEnvironment environment, InteractDirection direction) {
        this.m_environment = environment;
        this.m_direction = direction;
    }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        int redstoneSide;
        switch (this.m_direction) {
            case Forward:
            default:
                redstoneSide = 3;
                break;
            case Up:
                redstoneSide = 1;
                break;
            case Down:
                redstoneSide = 2;
        }

        int power = this.m_environment.getInput(redstoneSide);
        return power > 0 ? TurtleCommandResult.success() : TurtleCommandResult.failure();
    }
}
