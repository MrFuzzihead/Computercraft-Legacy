package dan200.computercraft.shared.peripheral.generic.energy.rf;

import dan200.computercraft.shared.peripheral.generic.GenericPeripheralProvider;

/**
 * Class-load isolation shim for the CoFH RF integration.
 *
 * <p>
 * All {@code cofh.api.energy.*} imports are confined to this compilation unit. The JVM
 * defers loading this class until {@link #register} is actually invoked, so if CoFH Core is
 * absent at runtime the {@link NoClassDefFoundError} is caught at the call site and
 * integration is skipped silently.
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
    public static void register(GenericPeripheralProvider provider) {
        provider.addFactory(new RFEnergyAdapterFactory());
    }
}
