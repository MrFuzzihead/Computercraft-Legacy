package dan200.computercraft.shared.peripheral.generic;

/**
 * Abstraction over an energy storage block, decoupled from any specific energy API.
 * Implementations bridge to CoFH RF ({@code IEnergyHandler}/{@code IEnergyStorage}),
 * or any future energy system added via {@link IEnergyAdapterFactory}.
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
