package dan200.computercraft.shared.peripheral.generic.energy.rf;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.junit.jupiter.api.Test;

import dan200.computercraft.shared.peripheral.common.DefaultPeripheralProvider;

/**
 * Regression tests for optional CoFH RF class loading.
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

    private static boolean cofhApiIsPresent() {
        try {
            Class.forName("cofh.api.energy.IEnergyReceiver", false, RFIntegrationTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
