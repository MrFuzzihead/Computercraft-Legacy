package dan200.computercraft.shared.peripheral.generic.energy.rf;

import net.minecraftforge.common.util.ForgeDirection;

import cofh.api.energy.IEnergyProvider;
import cofh.api.energy.IEnergyReceiver;
import cofh.api.energy.IEnergyStorage;
import dan200.computercraft.shared.peripheral.generic.IEnergyStorageAdapter;

/**
 * {@link IEnergyStorageAdapter} backed by CoFH's RF API.
 *
 * <p>
 * Three paths, tried in priority order by {@link RFEnergyAdapterFactory}:
 * </p>
 * <ol>
 * <li>{@link IEnergyReceiver} (preferred, directional) — covers {@code IEnergyHandler} too,
 *     since {@code IEnergyHandler extends IEnergyReceiver}.</li>
 * <li>{@link IEnergyProvider} (directional, generator/source-only blocks).</li>
 * <li>{@link IEnergyStorage} (non-directional fallback).</li>
 * </ol>
 * <p>
 * Directional calls use {@link ForgeDirection#UNKNOWN}; most machines accept this as a
 * side-agnostic query.
 * </p>
 */
public class RFEnergyStorageAdapter implements IEnergyStorageAdapter {

    private final IEnergyReceiver m_receiver;
    private final IEnergyProvider m_provider;
    private final IEnergyStorage m_storage;

    /** Construct an adapter backed by {@link IEnergyReceiver} (primary path). */
    public RFEnergyStorageAdapter(IEnergyReceiver receiver) {
        this.m_receiver = receiver;
        this.m_provider = null;
        this.m_storage = null;
    }

    /** Construct an adapter backed by {@link IEnergyProvider} (secondary path). */
    public RFEnergyStorageAdapter(IEnergyProvider provider) {
        this.m_receiver = null;
        this.m_provider = provider;
        this.m_storage = null;
    }

    /** Construct an adapter backed by {@link IEnergyStorage} (fallback path). */
    public RFEnergyStorageAdapter(IEnergyStorage storage) {
        this.m_receiver = null;
        this.m_provider = null;
        this.m_storage = storage;
    }

    @Override
    public int getEnergy() {
        if (m_receiver != null) {
            return m_receiver.getEnergyStored(ForgeDirection.UNKNOWN);
        }
        if (m_provider != null) {
            return m_provider.getEnergyStored(ForgeDirection.UNKNOWN);
        }
        return m_storage.getEnergyStored();
    }

    @Override
    public int getEnergyCapacity() {
        if (m_receiver != null) {
            return m_receiver.getMaxEnergyStored(ForgeDirection.UNKNOWN);
        }
        if (m_provider != null) {
            return m_provider.getMaxEnergyStored(ForgeDirection.UNKNOWN);
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
        Object mine = m_receiver != null ? m_receiver : m_provider != null ? m_provider : m_storage;
        Object theirs = other.m_receiver != null ? other.m_receiver : other.m_provider != null ? other.m_provider : other.m_storage;
        return mine == theirs;
    }

    @Override
    public int hashCode() {
        Object target = m_receiver != null ? m_receiver : m_provider != null ? m_provider : m_storage;
        return System.identityHashCode(target);
    }
}
