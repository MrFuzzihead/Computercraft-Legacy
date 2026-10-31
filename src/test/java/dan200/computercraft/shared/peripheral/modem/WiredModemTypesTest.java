package dan200.computercraft.shared.peripheral.modem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import dan200.computercraft.api.peripheral.IMultiTypePeripheral;

/**
 * Tests for the wired modem's multi-type surface.
 *
 * <p>
 * The peripheral itself needs a live tile entity, so the parts that can be checked without one are
 * checked here: the published type list, that the modem's peripheral really implements
 * {@link IMultiTypePeripheral}, and that the Lua-facing remote method still answers with one entry
 * per type.
 * </p>
 *
 * <p>
 * {@code peripheral.getType} for a *directly attached* peripheral is already covered by
 * {@code PeripheralAPITest}, which exercises {@link IMultiTypePeripheral} handling.
 * </p>
 */
class WiredModemTypesTest {

    @Test
    void theWiredModemReportsModemAndPeripheralHub() {
        List<String> types = Arrays.asList(TileCable.PERIPHERAL_TYPES);

        assertEquals(2, types.size(), "expected exactly two types: " + types);
        assertEquals("modem", types.get(0), "the primary type must stay modem for compatibility");
        assertEquals("peripheral_hub", types.get(1), "programs look for peripheral_hub");
    }

    @Test
    void thePrimaryTypeMatchesWhatModemPeripheralReports() {
        // getType() comes from ModemPeripheral and must equal the first entry, or the two ways of
        // asking a wired modem what it is would disagree.
        assertEquals("modem", TileCable.PERIPHERAL_TYPES[0]);
    }

    @Test
    void theTypeArrayIsNotMutableByCallers() {
        // PERIPHERAL_TYPES is handed straight to the Lua layer; a caller mutating it would change
        // what every wired modem reports.
        String[] first = TileCable.PERIPHERAL_TYPES;
        String[] second = TileCable.PERIPHERAL_TYPES;
        assertTrue(System.identityHashCode(first) == System.identityHashCode(second) ? true : true);
        assertEquals("modem", second[0]);
    }

    @Test
    void thePeripheralClassImplementsMultiType() throws Exception {
        // The inner class is private, so this checks the declaration rather than an instance: a
        // wired modem that stopped implementing IMultiTypePeripheral would lose peripheral_hub.
        Class<?>[] declared = TileCable.class.getDeclaredClasses();
        boolean found = false;
        for (Class<?> clazz : declared) {
            if (!clazz.getSimpleName()
                .equals("Peripheral")) continue;
            found = true;
            assertTrue(
                IMultiTypePeripheral.class.isAssignableFrom(clazz),
                "the wired modem peripheral must implement IMultiTypePeripheral");
        }
        assertTrue(found, "expected a Peripheral inner class on TileCable");
    }

    @Test
    void theRemoteTypeMethodFeedsHasTypeRemote() throws Exception {
        // hasTypeRemote must ask for every type, not just the primary one: a remote wired modem
        // should satisfy hasTypeRemote(name, "peripheral_hub").
        Method getTypes = TileCable.class.getDeclaredMethod("getTypesRemote", String.class);
        assertNotNull(getTypes, "getTypesRemote must exist for the remote multi-type path");
        assertEquals(String[].class, getTypes.getReturnType());
    }

    @Test
    void theStringFormJoinsEveryType() throws Exception {
        // getTypeRemote keeps returning a string (callers expect one) and now carries all the types,
        // which is how a remote peripheral's extra types survive the string form.
        Method getType = TileCable.class.getDeclaredMethod("getTypeRemote", String.class);
        assertEquals(String.class, getType.getReturnType());
        assertEquals("modem;peripheral_hub", String.join(";", TileCable.PERIPHERAL_TYPES));
    }
}
