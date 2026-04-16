package dan200.computercraft.shared.peripheral.generic;

/**
 * Abstraction over an energy storage block, decoupled from any specific energy API.
 * Implementations bridge to CoFH RF ({@code IEnergyHandler}/{@code IEnergyStorage}),
 * or any future energy system added via {@link IEnergyAdapterFactory}.
 *
 * <h3>Identity contract</h3>
 * <p>
 * {@link EnergyStoragePeripheral#equals(dan200.computercraft.api.peripheral.IPeripheral)}
 * delegates peripheral identity to this adapter's {@link #equals}/{@link #hashCode}.
 * Peripheral identity is used by the CC runtime to decide whether to fire
 * {@code peripheral_detach} / {@code peripheral} (changed) events. Implementations
 * <strong>must</strong> base equality on the underlying target object's identity
 * (i.e. reference equality or a stable unique key), <em>not</em> on mutable energy
 * state. State-based equality would cause spurious attach/detach cycles whenever the
 * stored energy changes.
 * </p>
 * <p>
 * The simplest correct implementation:
 * </p>
 * 
 * <pre>
 * {@code
 * public boolean equals(Object o) {
 *     if (!(o instanceof MyAdapter)) return false;
 *     return ((MyAdapter) o).m_handler == this.m_handler; // reference equality
 * }
 * public int hashCode() { return System.identityHashCode(m_handler); }
 * }
 * </pre>
 */
public interface IEnergyStorageAdapter {

    /**
     * Returns the current energy stored, in RF (or equivalent units).
     */
    int getEnergy();

    /**
     * Returns the maximum energy capacity, in RF (or equivalent units).
     */
    int getEnergyCapacity();
}
