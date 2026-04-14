package dan200.computercraft.shared.peripheral.generic.energy.rf;

import net.minecraftforge.common.util.ForgeDirection;

import cofh.api.energy.IEnergyHandler;
import cofh.api.energy.IEnergyStorage;
import dan200.computercraft.shared.peripheral.generic.IEnergyStorageAdapter;

/**
 * {@link IEnergyStorageAdapter} backed by CoFH's RF API.
 *
 * <p>
 * {@link IEnergyHandler} is preferred (directional, used by most GTNH machines such as
 * Thermal Expansion and EnderIO) with {@link ForgeDirection#UNKNOWN}. {@link IEnergyStorage}
 * is used as a fallback for blocks that only implement the simpler storage interface.
 * </p>
 */
public class RFEnergyStorageAdapter implements IEnergyStorageAdapter {

    private final IEnergyHandler m_handler;
    private final IEnergyStorage m_storage;

    /** Construct an adapter backed by {@link IEnergyHandler} (preferred path). */
    public RFEnergyStorageAdapter(IEnergyHandler handler) {
        this.m_handler = handler;
        this.m_storage = null;
    }

    /** Construct an adapter backed by {@link IEnergyStorage} (fallback path). */
    public RFEnergyStorageAdapter(IEnergyStorage storage) {
        this.m_handler = null;
        this.m_storage = storage;
    }

    @Override
    public int getEnergy() {
        if (m_handler != null) {
            return m_handler.getEnergyStored(ForgeDirection.UNKNOWN);
        }
        return m_storage.getEnergyStored();
    }

    @Override
    public int getEnergyCapacity() {
        if (m_handler != null) {
            return m_handler.getMaxEnergyStored(ForgeDirection.UNKNOWN);
        }
        return m_storage.getMaxEnergyStored();
    }

    /**
     * Two adapters are equal when they wrap the same underlying object instance, preventing
     * spurious peripheral-changed events when the same tile entity is queried multiple times.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RFEnergyStorageAdapter)) return false;
        RFEnergyStorageAdapter other = (RFEnergyStorageAdapter) obj;
        if (m_handler != null) {
            return m_handler == other.m_handler;
        }
        return m_storage == other.m_storage;
    }

    @Override
    public int hashCode() {
        return m_handler != null ? System.identityHashCode(m_handler) : System.identityHashCode(m_storage);
    }
}
