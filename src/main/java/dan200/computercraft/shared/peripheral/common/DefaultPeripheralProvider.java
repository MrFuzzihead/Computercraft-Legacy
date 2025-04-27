package dan200.computercraft.shared.peripheral.common;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;
import dan200.computercraft.shared.computer.blocks.ComputerPeripheral;
import dan200.computercraft.shared.computer.blocks.TileComputerBase;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;

public class DefaultPeripheralProvider implements IPeripheralProvider {

    @Override
    public IPeripheral getPeripheral(World world, int x, int y, int z, int side) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile != null) {
            if (tile instanceof IPeripheralTile) {
                IPeripheralTile peripheralTile = (IPeripheralTile) tile;
                return peripheralTile.getPeripheral(side);
            }

            if (tile instanceof TileComputerBase) {
                TileComputerBase computerTile = (TileComputerBase) tile;
                if (!(tile instanceof TileTurtle)) {
                    return new ComputerPeripheral("computer", computerTile.createServerComputer());
                }

                if (!((TileTurtle) tile).hasMoved()) {
                    return new ComputerPeripheral("turtle", computerTile.createServerComputer());
                }
            }
        }

        return null;
    }
}
