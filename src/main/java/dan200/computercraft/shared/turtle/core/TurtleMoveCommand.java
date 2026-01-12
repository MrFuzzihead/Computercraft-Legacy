package dan200.computercraft.shared.turtle.core;

import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Facing;
import net.minecraft.world.World;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleAnimation;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.util.WorldUtil;

public class TurtleMoveCommand implements ITurtleCommand {

    private final MoveDirection m_direction;
    private final String COMMANDNAME = "move";

    public TurtleMoveCommand(MoveDirection direction) {
        this.m_direction = direction;
    }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        if (Arrays.asList(ComputerCraft.turtleDisabledActions)
            .contains(COMMANDNAME)) {
            return TurtleCommandResult.failure("Turtle action \"" + COMMANDNAME + "\" is disabled");
        }

        int direction = this.m_direction.toWorldDir(turtle);
        World oldWorld = turtle.getWorld();
        ChunkCoordinates oldPosition = turtle.getPosition();
        ChunkCoordinates newPosition = WorldUtil.moveCoords(oldPosition, direction);
        TurtlePlayer turtlePlayer = TurtlePlaceCommand.createPlayer(turtle, oldPosition, direction);
        TurtleCommandResult canEnterResult = this.canEnter(turtlePlayer, oldWorld, newPosition);
        if (!canEnterResult.isSuccess()) {
            return canEnterResult;
        } else {
            Block block = oldWorld.getBlock(newPosition.posX, newPosition.posY, newPosition.posZ);
            if (block != null && !oldWorld.isAirBlock(newPosition.posX, newPosition.posY, newPosition.posZ)
                && !WorldUtil.isLiquidBlock(oldWorld, newPosition)
                && !block.isReplaceable(oldWorld, newPosition.posX, newPosition.posY, newPosition.posZ)) {
                return TurtleCommandResult.failure("Movement obstructed");
            } else {
                AxisAlignedBB aabb = ((TurtleBrain) turtle).getOwner()
                    .getBounds();
                aabb.minX = aabb.minX + newPosition.posX;
                aabb.maxX = aabb.maxX + newPosition.posX;
                aabb.minY = aabb.minY + newPosition.posY;
                aabb.maxY = aabb.maxY + newPosition.posY;
                aabb.minZ = aabb.minZ + newPosition.posZ;
                aabb.maxZ = aabb.maxZ + newPosition.posZ;
                if (!oldWorld.checkNoEntityCollision(aabb)) {
                    if (!ComputerCraft.turtlesCanPush || this.m_direction == MoveDirection.Up
                        || this.m_direction == MoveDirection.Down) {
                        return TurtleCommandResult.failure("Movement obstructed");
                    }

                    List list = oldWorld.getEntitiesWithinAABBExcludingEntity((Entity) null, aabb);

                    for (int i = 0; i < list.size(); i++) {
                        Entity entity = (Entity) list.get(i);
                        if (!entity.isDead && entity.preventEntitySpawning && entity.boundingBox != null) {
                            AxisAlignedBB pushedBB = entity.boundingBox.addCoord(
                                Facing.offsetsXForSide[direction],
                                Facing.offsetsYForSide[direction],
                                Facing.offsetsZForSide[direction]);
                            if (!oldWorld.func_147461_a(pushedBB)
                                .isEmpty()) {
                                return TurtleCommandResult.failure("Movement obstructed");
                            }
                        }
                    }
                }

                if (turtle.isFuelNeeded() && turtle.getFuelLevel() < 1) {
                    return TurtleCommandResult.failure("Out of fuel");
                } else if (turtle.teleportTo(oldWorld, newPosition.posX, newPosition.posY, newPosition.posZ)) {
                    turtle.consumeFuel(1);
                    switch (this.m_direction) {
                        case Forward:
                        default:
                            turtle.playAnimation(TurtleAnimation.MoveForward);
                            break;
                        case Back:
                            turtle.playAnimation(TurtleAnimation.MoveBack);
                            break;
                        case Up:
                            turtle.playAnimation(TurtleAnimation.MoveUp);
                            break;
                        case Down:
                            turtle.playAnimation(TurtleAnimation.MoveDown);
                    }

                    return TurtleCommandResult.success();
                } else {
                    return TurtleCommandResult.failure("Movement failed");
                }
            }
        }
    }

    private TurtleCommandResult canEnter(TurtlePlayer turtlePlayer, World world, ChunkCoordinates position) {
        if (position.posY < 0) {
            return TurtleCommandResult.failure("Too low to move");
        } else if (position.posY > world.getHeight() - 1) {
            return TurtleCommandResult.failure("Too high to move");
        } else if (ComputerCraft.turtlesObeyBlockProtection
            && !ComputerCraft.isBlockEnterable(world, position.posX, position.posY, position.posZ, turtlePlayer)) {
                return TurtleCommandResult.failure("Cannot enter protected area");
            } else {
                return !world.blockExists(position.posX, position.posY, position.posZ)
                    ? TurtleCommandResult.failure("Cannot leave loaded world")
                    : TurtleCommandResult.success();
            }
    }
}
