package dan200.computercraft.shared.peripheral.generic.energy.rf;

import net.minecraft.tileentity.TileEntity;

import cofh.api.energy.IEnergyProvider;
import cofh.api.energy.IEnergyReceiver;
import cofh.api.energy.IEnergyStorage;
import dan200.computercraft.shared.peripheral.generic.IEnergyAdapterFactory;
import dan200.computercraft.shared.peripheral.generic.IEnergyStorageAdapter;

/**
 * {@link IEnergyAdapterFactory} that recognises CoFH RF blocks.
 *
 * <p>
 * Check order (first match wins):
 * <ol>
 * <li>{@link IEnergyReceiver} — directional consumer; also catches {@link cofh.api.energy.IEnergyHandler}
 *     since it extends {@code IEnergyReceiver}. Used by processing machines such as
 *     Thermal Expansion's Redstone Furnace.</li>
 * <li>{@link IEnergyProvider} — directional generator/source-only blocks.</li>
 * <li>{@link IEnergyStorage} — non-directional storage blocks.</li>
 * </ol>
 * Returns {@code null} for any tile that implements none of the above.
 * </p>
 */
public class RFEnergyAdapterFactory implements IEnergyAdapterFactory {

    @Override
    public IEnergyStorageAdapter tryAdapt(TileEntity tileEntity) {
        if (tileEntity instanceof IEnergyReceiver) {
            return new RFEnergyStorageAdapter((IEnergyReceiver) tileEntity);
        }
        if (tileEntity instanceof IEnergyProvider) {
            return new RFEnergyStorageAdapter((IEnergyProvider) tileEntity);
        }
        if (tileEntity instanceof IEnergyStorage) {
            return new RFEnergyStorageAdapter((IEnergyStorage) tileEntity);
        }
        return null;
    }
}
