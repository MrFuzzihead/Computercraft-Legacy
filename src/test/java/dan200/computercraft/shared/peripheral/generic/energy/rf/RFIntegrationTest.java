package dan200.computercraft.shared.peripheral.generic.energy.rf;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.junit.jupiter.api.Test;

import dan200.computercraft.shared.peripheral.common.DefaultPeripheralProvider;

/**
 * Regression tests for optional CoFH RF class loading.
 *
 * <p>
 * {@link RFIntegration} must decide whether CoFH is present <em>without</em> resolving any
 * {@code cofh.api.energy.*} class, because those references live in {@link RFEnergyAdapterFactory}
 * and {@link RFEnergyStorageAdapter} and would fail to link in a runtime without CoFH Core.
 * </p>
 *
 * <p>
 * The availability probe asks FML whether the {@code CoFHCore} modid is loaded rather than
 * class-loading the API. That is stricter about not touching the API classes, but it means the
 * probe itself depends on FML's {@code Loader} being populated — which is only true inside a
 * running game. In a bare unit-test JVM {@code Loader.instance()} is null, so the probe throws;
 * {@code RFIntegration} treats that as "not available" rather than propagating. This class
 * therefore also pins that a throwing probe is tolerated, which is the difference between this
 * working in tests and not.
 * </p>
 *
 * <p>
 * <b>Not covered here: the modid string itself.</b> Because the probe throws before the modid is
 * ever compared in a bare JVM, no unit test here can distinguish a correct modid from a wrong one,
 * and substituting a wrong one leaves every test in this class green. The value was taken from
 * CoFH Core's own {@code mcmod.info} ({@code "modid": "CoFHCore"}). A regression there is
 * <em>silent</em> — RF support would simply stop registering in a live game with CoFH installed —
 * so verifying an RF peripheral against real CoFH in game is the only check that covers it.
 * </p>
 */
class RFIntegrationTest {

    @Test
    void missingRFAccessDoesNotCrashGenericPeripheralLookup() {
        assumeFalse(cofhApiIsPresent(), "CoFH API is present in this test runtime");

        DefaultPeripheralProvider provider = new DefaultPeripheralProvider();
        RFIntegration.register(provider);

        World world = mock(World.class);
        TileEntity tile = mock(TileEntity.class);
        when(world.getTileEntity(0, 0, 0)).thenReturn(tile);

        assertDoesNotThrow(() -> provider.getPeripheral(world, 0, 0, 0, 0));
    }

    @Test
    void noRFEnergyFactoryIsRegisteredWhenCofhIsAbsent() {
        assumeFalse(cofhApiIsPresent(), "CoFH API is present in this test runtime");

        DefaultPeripheralProvider provider = new DefaultPeripheralProvider();
        RFIntegration.register(provider);

        // Registering the factory is what would pull the cofh classes into the picture, so with
        // CoFH absent the list must stay empty.
        assertTrue(energyFactories(provider).isEmpty(), "no RF adapter factory should be registered without CoFH");
    }

    @Test
    void aThrowingAvailabilityProbeIsTreatedAsUnavailable() {
        // Documented contract: the probe is allowed to blow up (no FML Loader outside a game) and
        // must degrade to "not available" rather than propagating. This is what keeps the
        // unit tests above meaningful in a JVM with no FML bootstrap at all.
        assertTrue(energyFactories(registerWithoutFml()).isEmpty(), "a failing probe must not register the factory");
    }

    private static DefaultPeripheralProvider registerWithoutFml() {
        DefaultPeripheralProvider provider = new DefaultPeripheralProvider();
        RFIntegration.register(provider);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static List<?> energyFactories(DefaultPeripheralProvider provider) {
        try {
            Field field = DefaultPeripheralProvider.class.getDeclaredField("m_energyFactories");
            field.setAccessible(true);
            return (List<Object>) field.get(provider);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to read m_energyFactories", e);
        }
    }

    private static boolean cofhApiIsPresent() {
        try {
            Class.forName("cofh.api.energy.IEnergyReceiver", false, RFIntegrationTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
