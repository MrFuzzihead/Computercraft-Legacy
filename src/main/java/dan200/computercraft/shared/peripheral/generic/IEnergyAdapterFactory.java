package dan200.computercraft.shared.peripheral.generic;

import net.minecraft.tileentity.TileEntity;

/**
 * Factory that attempts to adapt a {@link TileEntity} into an {@link IEnergyStorageAdapter}.
 * Return {@code null} when the tile entity does not implement the relevant energy API.
 */
public interface IEnergyAdapterFactory {

    /**
     * @param tileEntity the tile entity to test
     * @return an adapter if the tile implements a supported energy API, otherwise {@code null}
     */
    IEnergyStorageAdapter tryAdapt(TileEntity tileEntity);
}
