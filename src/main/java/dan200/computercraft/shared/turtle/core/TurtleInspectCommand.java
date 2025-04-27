package dan200.computercraft.shared.turtle.core;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.util.WorldUtil;

public class TurtleInspectCommand implements ITurtleCommand {

    private final InteractDirection m_direction;
    private final boolean m_failOnAir;

    public TurtleInspectCommand(InteractDirection direction, boolean failOnAir) {
        this.m_direction = direction;
        this.m_failOnAir = failOnAir;
    }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        int direction = this.m_direction.toWorldDir(turtle);
        World world = turtle.getWorld();
        ChunkCoordinates oldPosition = turtle.getPosition();
        ChunkCoordinates newPosition = WorldUtil.moveCoords(oldPosition, direction);
        if (!WorldUtil.isBlockInWorld(world, newPosition)
            || this.m_failOnAir && world.isAirBlock(newPosition.posX, newPosition.posY, newPosition.posZ)) {
            if (!this.m_failOnAir) {
                Map<Object, Object> table = new HashMap<>();
                table.put("name", "minecraft:air");
                table.put("metadata", 0);
                return TurtleCommandResult.success(new Object[] { table });
            } else {
                return TurtleCommandResult.failure("No block to inspect");
            }
        } else {
            Block block = world.getBlock(newPosition.posX, newPosition.posY, newPosition.posZ);
            String name = Block.blockRegistry.getNameForObject(block);
            int metadata = world.getBlockMetadata(newPosition.posX, newPosition.posY, newPosition.posZ);
            Map<Object, Object> table = new HashMap<>();
            table.put("name", name);
            table.put("metadata", metadata);
            return TurtleCommandResult.success(new Object[] { table });
        }
    }
}
