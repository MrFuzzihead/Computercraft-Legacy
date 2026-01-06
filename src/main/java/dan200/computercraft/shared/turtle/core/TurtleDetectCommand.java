package dan200.computercraft.shared.turtle.core;

import java.util.Arrays;

import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.util.WorldUtil;

public class TurtleDetectCommand implements ITurtleCommand {

    private final InteractDirection m_direction;
    private final String COMMANDNAME = "detect";

    public TurtleDetectCommand(InteractDirection direction) {
        this.m_direction = direction;
    }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        if (Arrays.asList(ComputerCraft.turtleDisabledActions)
            .contains(COMMANDNAME)) {
            return TurtleCommandResult.failure("Turtle action \"" + COMMANDNAME + "\" is disabled");
        }

        int direction = this.m_direction.toWorldDir(turtle);
        World world = turtle.getWorld();
        ChunkCoordinates oldPosition = turtle.getPosition();
        ChunkCoordinates newPosition = WorldUtil.moveCoords(oldPosition, direction);
        return WorldUtil.isBlockInWorld(world, newPosition) && !WorldUtil.isLiquidBlock(world, newPosition)
            && !world.isAirBlock(newPosition.posX, newPosition.posY, newPosition.posZ) ? TurtleCommandResult.success()
                : TurtleCommandResult.failure();
    }
}
