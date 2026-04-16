package dan200.computercraft.shared.peripheral.common;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.IFluidHandler;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;
import dan200.computercraft.shared.computer.blocks.ComputerPeripheral;
import dan200.computercraft.shared.computer.blocks.TileComputerBase;
import dan200.computercraft.shared.peripheral.generic.EnergyStoragePeripheral;
import dan200.computercraft.shared.peripheral.generic.GenericCombinedPeripheral;
import dan200.computercraft.shared.peripheral.generic.GenericFluidPeripheral;
import dan200.computercraft.shared.peripheral.generic.IEnergyAdapterFactory;
import dan200.computercraft.shared.peripheral.generic.IEnergyStorageAdapter;
import dan200.computercraft.shared.peripheral.inventory.InventoryPeripheral;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;

public class DefaultPeripheralProvider implements IPeripheralProvider {

    private final List<IEnergyAdapterFactory> m_energyFactories = new ArrayList<>();

    /**
     * Registers an energy-adapter factory. Factories are tested in insertion order; the first
     * non-null result wins. Call this before the provider is registered with
     * {@link dan200.computercraft.api.ComputerCraftAPI#registerPeripheralProvider}.
     */
    public void addEnergyFactory(IEnergyAdapterFactory factory) {
        m_energyFactories.add(factory);
    }

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

            // Build the list of applicable generic modules. Add new capability modules
            // here as additional entries.
            ForgeDirection face = ForgeDirection.getOrientation(side);
            List<IPeripheral> modules = new ArrayList<>();
            if (tile instanceof IInventory) {
                modules.add(new InventoryPeripheral(tile));
            }
            if (tile instanceof IFluidHandler) {
                modules.add(new GenericFluidPeripheral((IFluidHandler) tile, face));
            }
            // Energy: try each registered factory; use the first adapter that matches.
            for (IEnergyAdapterFactory factory : m_energyFactories) {
                IEnergyStorageAdapter adapter = factory.tryAdapt(tile);
                if (adapter != null) {
                    modules.add(new EnergyStoragePeripheral(adapter));
                    break;
                }
            }

            if (modules.size() > 1) {
                return new GenericCombinedPeripheral(modules, tile);
            }
            if (modules.size() == 1) {
                return modules.get(0);
            }
        }

        return null;
    }
}
