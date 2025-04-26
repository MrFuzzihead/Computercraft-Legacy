package dan200.computercraft.api.core;

import com.google.common.base.Optional;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleAnimation;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleUpgradeType;
import dan200.computercraft.api.turtle.TurtleVerb;

public class TurtleToolCommand implements ITurtleCommand {
   private final TurtleVerb m_verb;
   private final InteractDirection m_direction;
   private final Optional<TurtleSide> m_side;

   public TurtleToolCommand(TurtleVerb verb, InteractDirection direction, Optional<TurtleSide> side) {
      this.m_verb = verb;
      this.m_direction = direction;
      this.m_side = side;
   }

   @Override
   public TurtleCommandResult execute(ITurtleAccess turtle) {
      TurtleCommandResult firstFailure = null;

      for (TurtleSide side : TurtleSide.values()) {
         if (!this.m_side.isPresent() || this.m_side.get() == side) {
            ITurtleUpgrade upgrade = turtle.getUpgrade(side);
            if (upgrade != null && upgrade.getType() == TurtleUpgradeType.Tool) {
               TurtleCommandResult result = upgrade.useTool(turtle, side, this.m_verb, this.m_direction.toWorldDir(turtle));
               if (result.isSuccess()) {
                  switch (side) {
                     case Left:
                        turtle.playAnimation(TurtleAnimation.SwingLeftTool);
                        break;
                     case Right:
                        turtle.playAnimation(TurtleAnimation.SwingRightTool);
                        break;
                     default:
                        turtle.playAnimation(TurtleAnimation.Wait);
                  }

                  return result;
               }

               if (firstFailure == null) {
                  firstFailure = result;
               }
            }
         }
      }

      return firstFailure != null ? firstFailure : TurtleCommandResult.failure("No tool to " + this.m_verb.toString().toLowerCase() + " with");
   }
}
