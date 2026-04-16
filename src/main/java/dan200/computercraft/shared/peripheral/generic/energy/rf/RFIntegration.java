package dan200.computercraft.shared.peripheral.generic.energy.rf;

import dan200.computercraft.shared.peripheral.common.DefaultPeripheralProvider;

/**
 * Class-load isolation shim for the CoFH RF integration.
 *
 * <p>
 * All {@code cofh.api.energy.*} imports are confined to the {@link RFEnergyAdapterFactory} and
 * {@link RFEnergyStorageAdapter} classes. The JVM defers loading those classes until
 * {@link #register} is actually invoked, so if CoFH Core is absent at runtime the
 * {@link NoClassDefFoundError} is caught at the call site and integration is skipped silently.
 * </p>
 */
public final class RFIntegration {

    private RFIntegration() {}

    /**
     * Registers the CoFH RF adapter factory with the given provider.
     * Call this inside a {@code try/catch (NoClassDefFoundError)} block.
     *
     * @param provider the provider to register the RF factory with
     */
    public static void register(DefaultPeripheralProvider provider) {
        provider.addEnergyFactory(new RFEnergyAdapterFactory());
    }
}
