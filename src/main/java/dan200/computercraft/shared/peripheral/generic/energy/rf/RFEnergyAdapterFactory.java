package dan200.computercraft.shared.peripheral.generic.energy.rf;

import net.minecraft.tileentity.TileEntity;

import cofh.api.energy.IEnergyHandler;
import cofh.api.energy.IEnergyStorage;
import dan200.computercraft.shared.peripheral.generic.IEnergyAdapterFactory;
import dan200.computercraft.shared.peripheral.generic.IEnergyStorageAdapter;

/**
 * {@link IEnergyAdapterFactory} that recognises CoFH RF blocks.
 *
 * <p>
 * Checks {@link IEnergyHandler} first (preferred, directional) and falls back to
 * {@link IEnergyStorage} (non-directional). Returns {@code null} for any tile that
 * implements neither.
 * </p>
 */
public class RFEnergyAdapterFactory implements IEnergyAdapterFactory {

    @Override
    public IEnergyStorageAdapter tryAdapt(TileEntity tileEntity) {
        if (tileEntity instanceof IEnergyHandler) {
            return new RFEnergyStorageAdapter((IEnergyHandler) tileEntity);
        }
        if (tileEntity instanceof IEnergyStorage) {
            return new RFEnergyStorageAdapter((IEnergyStorage) tileEntity);
        }
        return null;
    }
}
