package dan200.computercraft.shared.peripheral.commandblock;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityCommandBlock;
import net.minecraft.world.World;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;

public class CommandBlockPeripheralProvider implements IPeripheralProvider {

    @Override
    public IPeripheral getPeripheral(World world, int x, int y, int z, int side) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile != null && tile instanceof TileEntityCommandBlock) {
            TileEntityCommandBlock commandBlock = (TileEntityCommandBlock) tile;
            return new CommandBlockPeripheral(commandBlock);
        } else {
            return null;
        }
    }
}
