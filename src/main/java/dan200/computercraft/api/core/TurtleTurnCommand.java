package dan200.computercraft.api.core;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleAnimation;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.util.DirectionUtil;

public class TurtleTurnCommand implements ITurtleCommand {
   private final TurnDirection m_direction;

   public TurtleTurnCommand(TurnDirection direction) {
      this.m_direction = direction;
   }

   @Override
   public TurtleCommandResult execute(ITurtleAccess turtle) {
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
