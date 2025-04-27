package dan200.computercraft.shared.common;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import dan200.computercraft.api.redstone.IBundledRedstoneProvider;

public class DefaultBundledRedstoneProvider implements IBundledRedstoneProvider {

    @Override
    public int getBundledRedstoneOutput(World world, int x, int y, int z, int side) {
        return getDefaultBundledRedstoneOutput(world, x, y, z, side);
    }

    public static int getDefaultBundledRedstoneOutput(World world, int x, int y, int z, int side) {
        Block block = world.getBlock(x, y, z);
        if (block != null && block instanceof BlockGeneric) {
            BlockGeneric generic = (BlockGeneric) block;
            if (generic.getBundledRedstoneConnectivity(world, x, y, z, side)) {
                return generic.getBundledRedstoneOutput(world, x, y, z, side);
            }
        }

        return -1;
    }
}
