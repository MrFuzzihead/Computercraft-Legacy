package dan200.computercraft.shared.turtle.core;

import com.google.common.base.Optional;

import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleVerb;

public class TurtleDigCommand extends TurtleToolCommand {

    public TurtleDigCommand(InteractDirection direction, Optional<TurtleSide> side) {
        super(TurtleVerb.Dig, direction, side);
    }
}
