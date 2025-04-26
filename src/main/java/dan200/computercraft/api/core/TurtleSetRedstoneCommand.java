package dan200.computercraft.api.core;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleAnimation;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.core.apis.IAPIEnvironment;

public class TurtleSetRedstoneCommand implements ITurtleCommand {
   private final IAPIEnvironment m_environment;
   private final InteractDirection m_direction;
   private final int m_value;

   public TurtleSetRedstoneCommand(IAPIEnvironment environment, InteractDirection direction, int value) {
      this.m_environment = environment;
      this.m_direction = direction;
      this.m_value = value;
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

      this.m_environment.setOutput(redstoneSide, this.m_value);
      turtle.playAnimation(TurtleAnimation.ShortWait);
      return TurtleCommandResult.success();
   }
}
