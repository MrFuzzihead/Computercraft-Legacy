package dan200.computercraft.shared.peripheral.generic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EnergyStoragePeripheral} using anonymous {@link IEnergyStorageAdapter}
 * stubs — no Minecraft world or CoFH classes required.
 */
class EnergyStoragePeripheralTest {

    private static EnergyStoragePeripheral makePeripheral(int energy, int capacity) {
        return new EnergyStoragePeripheral(new IEnergyStorageAdapter() {

            @Override
            public int getEnergy() {
                return energy;
            }

            @Override
            public int getEnergyCapacity() {
                return capacity;
            }
        });
    }

    // =========================================================================
    // Peripheral type
    // =========================================================================

    @Test
    void peripheralTypeIsEnergyStorage() {
        assertEquals("energy_storage", makePeripheral(0, 0).getType());
    }

    // =========================================================================
    // Method surface
    // =========================================================================

    @Test
    void methodCountIsTwo() {
        assertEquals(2, makePeripheral(0, 0).getMethodNames().length);
    }

    @Test
    void getEnergyAtIndexZero() {
        assertEquals("getEnergy", makePeripheral(0, 0).getMethodNames()[0]);
    }

    @Test
    void getEnergyCapacityAtIndexOne() {
        assertEquals("getEnergyCapacity", makePeripheral(0, 0).getMethodNames()[1]);
    }

    // =========================================================================
    // callMethod behaviour
    // =========================================================================

    @Test
    void getEnergyReturnsAdapterValue() throws Exception {
        EnergyStoragePeripheral p = makePeripheral(1234, 9999);
        Object[] result = p.callMethod(null, null, 0, new Object[0]);
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals(1234, result[0]);
    }

    @Test
    void getEnergyCapacityReturnsAdapterValue() throws Exception {
        EnergyStoragePeripheral p = makePeripheral(1234, 9999);
        Object[] result = p.callMethod(null, null, 1, new Object[0]);
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals(9999, result[0]);
    }

    // =========================================================================
    // equals(IPeripheral) contract
    // =========================================================================

    @Test
    void equalsReturnsTrueForSameAdapter() {
        IEnergyStorageAdapter adapter = new IEnergyStorageAdapter() {

            @Override
            public int getEnergy() {
                return 0;
            }

            @Override
            public int getEnergyCapacity() {
                return 0;
            }
        };
        EnergyStoragePeripheral p1 = new EnergyStoragePeripheral(adapter);
        EnergyStoragePeripheral p2 = new EnergyStoragePeripheral(adapter);
        assertTrue(p1.equals(p2));
    }

    @Test
    void equalsReturnsFalseForDifferentAdapter() {
        EnergyStoragePeripheral p1 = makePeripheral(0, 0);
        EnergyStoragePeripheral p2 = makePeripheral(0, 0);
        assertFalse(p1.equals(p2));
    }
}
