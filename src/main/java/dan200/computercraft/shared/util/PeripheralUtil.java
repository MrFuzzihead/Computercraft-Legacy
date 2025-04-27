package dan200.computercraft.shared.util;

import net.minecraft.world.World;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IPeripheral;

public class PeripheralUtil {

    public static IPeripheral getPeripheral(World world, int x, int y, int z, int side) {
        return y >= 0 && y < world.getHeight() && !world.isRemote ? ComputerCraft.getPeripheralAt(world, x, y, z, side)
            : null;
    }
}
