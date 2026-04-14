package dan200.computercraft.shared.peripheral.generic;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;

/**
 * {@link IPeripheralProvider} that wraps third-party tile entities as generic CC peripherals.
 *
 * <p>
 * This provider is registered <em>after</em> {@code DefaultPeripheralProvider}, so any
 * tile that already exposes an {@code IPeripheralTile} (CC's own blocks, etc.) will have been
 * claimed before this provider is consulted. No explicit {@code IPeripheralTile} guard is
 * needed here.
 * </p>
 *
 * <p>
 * Energy-API adapters are plugged in via {@link #addFactory}; call
 * {@link dan200.computercraft.shared.peripheral.generic.energy.rf.RFIntegration#register} to
 * register the CoFH RF adapter before this provider is handed to
 * {@link dan200.computercraft.api.ComputerCraftAPI#registerPeripheralProvider}.
 * </p>
 */
public class GenericPeripheralProvider implements IPeripheralProvider {

    private final List<IEnergyAdapterFactory> m_factories = new ArrayList<>();

    /**
     * Registers an energy-adapter factory. Factories are tested in insertion order; the first
     * non-null result wins.
     */
    public void addFactory(IEnergyAdapterFactory factory) {
        m_factories.add(factory);
    }

    @Override
    public IPeripheral getPeripheral(World world, int x, int y, int z, int side) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile == null) {
            return null;
        }

        for (IEnergyAdapterFactory factory : m_factories) {
            IEnergyStorageAdapter adapter = factory.tryAdapt(tile);
            if (adapter != null) {
                return new EnergyStoragePeripheral(adapter);
            }
        }

        return null;
    }
}
