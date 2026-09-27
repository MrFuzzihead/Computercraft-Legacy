package dan200.computercraft.shared.peripheral.generic.energy.rf;

import cpw.mods.fml.common.Loader;
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

    /**
     * The modid of CoFH Core, which provides {@code cofh.api.energy.*}. Declared as a
     * compile-only dependency, so it is absent unless the player installs it.
     */
    private static final String COFH_MODID = "CoFHCore";

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
            return Loader.isModLoaded(COFH_MODID);
        } catch (RuntimeException | LinkageError ignored) {
            // FML's Loader is only populated inside a running game, so this can fail outside one
            // (unit tests, an early preInit). Treat that as "not available", which is the safe
            // answer: it skips RF integration rather than resolving the absent cofh classes.
            return false;
        }
    }
}
