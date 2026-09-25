package dan200.computercraft.shared.peripheral.generic.energy.rf;

import dan200.computercraft.shared.peripheral.common.DefaultPeripheralProvider;

/**
 * Class-load isolation shim for the CoFH RF integration.
 *
 * <p>
 * All {@code cofh.api.energy.*} imports are confined to the {@link RFEnergyAdapterFactory} and
 * {@link RFEnergyStorageAdapter} classes. {@link #register} checks for the optional CoFH API
 * before constructing the factory, so a runtime without CoFH Core never resolves those
 * classes and simply skips RF integration.
 * </p>
 */
public final class RFIntegration {

    private RFIntegration() {}

    private static final String[] RF_API_CLASSES = { "cofh.api.energy.IEnergyReceiver",
        "cofh.api.energy.IEnergyProvider", "cofh.api.energy.IEnergyStorage", };

    /**
     * Registers the CoFH RF adapter factory with the given provider.
     *
     * <p>
     * The RF API is an optional compile-time dependency. Do not instantiate the
     * adapter factory when CoFH is absent: its {@code instanceof} checks are resolved
     * only when a tile is queried, long after this method returns. Callers may still
     * wrap this call in a {@code try/catch (NoClassDefFoundError)} as a final guard.
     * </p>
     *
     * @param provider the provider to register the RF factory with
     */
    public static void register(DefaultPeripheralProvider provider) {
        if (!isRFAvailable()) {
            return;
        }

        provider.addEnergyFactory(new RFEnergyAdapterFactory());
    }

    private static boolean isRFAvailable() {
        try {
            for (String className : RF_API_CLASSES) {
                Class.forName(className, false, RFIntegration.class.getClassLoader());
            }
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
