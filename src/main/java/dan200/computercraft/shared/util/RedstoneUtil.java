package dan200.computercraft.shared.util;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.Facing;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import dan200.computercraft.ComputerCraft;

public class RedstoneUtil {

    private static Block getBlock(IBlockAccess world, int x, int y, int z) {
        return y >= 0 ? world.getBlock(x, y, z) : null;
    }

    public static int getRedstoneOutput(World world, int x, int y, int z, int side) {
        int power = 0;
        Block block = getBlock(world, x, y, z);
        if (block != null) {
            if (block == Blocks.redstone_wire) {
                int metadata = world.getBlockMetadata(x, y, z);
                power = side != 1 ? metadata : 0;
            } else if (block.canProvidePower()) {
                int testSide = Facing.oppositeSide[side];
                power = block.isProvidingWeakPower(world, x, y, z, testSide);
            }

            if (block.isNormalCube(world, x, y, z)) {
                for (int i = 0; i < 6; i++) {
                    if (i != side) {
                        int testX = x + Facing.offsetsXForSide[i];
                        int testY = y + Facing.offsetsYForSide[i];
                        int testZ = z + Facing.offsetsZForSide[i];
                        Block neighbour = getBlock(world, testX, testY, testZ);
                        if (neighbour != null && neighbour.canProvidePower()) {
                            power = Math.max(power, neighbour.isProvidingStrongPower(world, testX, testY, testZ, i));
                        }
                    }
                }
            }
        }

        return power;
    }

    public static int getBundledRedstoneOutput(World world, int x, int y, int z, int side) {
        int signal = ComputerCraft.getBundledRedstoneOutput(world, x, y, z, side);
        return signal >= 0 ? signal : 0;
    }

    public static void propogateRedstoneOutput(World world, int x, int y, int z, int side) {
        Block block = getBlock(world, x, y, z);
        int neighbourX = x + Facing.offsetsXForSide[side];
        int neighbourY = y + Facing.offsetsYForSide[side];
        int neighbourZ = z + Facing.offsetsZForSide[side];
        Block neighbour = getBlock(world, neighbourX, neighbourY, neighbourZ);
        if (neighbour != null) {
            world.notifyBlockOfNeighborChange(neighbourX, neighbourY, neighbourZ, block);
            if (neighbour.isNormalCube(world, neighbourX, neighbourY, neighbourZ)) {
                world.notifyBlocksOfNeighborChange(
                    neighbourX,
                    neighbourY,
                    neighbourZ,
                    neighbour,
                    Facing.oppositeSide[side]);
            }
        }
    }
}
