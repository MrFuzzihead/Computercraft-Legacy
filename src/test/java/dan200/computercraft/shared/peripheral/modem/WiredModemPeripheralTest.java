package dan200.computercraft.shared.peripheral.modem;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the wired-modem-specific additions to {@link TileCable}'s
 * inner {@code Peripheral} class:
 *
 * <ul>
 * <li>{@code getTypeRemote} — now returns multiple values (one per type in a
 * {@code ";"}-delimited string).</li>
 * <li>{@code hasTypeRemote} — new method; checks whether a remote peripheral
 * has a given type, throws when the peripheral is absent.</li>
 * <li>{@code getNameLocal} — new method; returns the local peripheral name
 * of this wired modem, or {@code nil} when none is connected.</li>
 * </ul>
 *
 * <h2>Scope</h2>
 * <p>
 * Only the method-surface (names, count, indices) can be verified without a
 * running Minecraft server, because every {@code callMethod} invocation
 * requires a live {@link TileCable} tile entity with an active
 * {@link net.minecraft.world.World}. Behavioural paths are therefore covered
 * by the in-game script {@code test_wired_modem.lua}.
 * </p>
 *
 * <h2>Reflection note</h2>
 * <p>
 * {@code TileCable.Peripheral} is a {@code private static} inner class.
 * We access it via {@link Class#getDeclaredClasses()} and construct it with
 * {@code entity = null}, which is safe because {@link ModemPeripheral} field
 * initialisation does not touch Minecraft state and {@code getMethodNames()}
 * never dereferences the entity reference.
 * </p>
 */
class WiredModemPeripheralTest {

    /** Base-class methods declared by {@link ModemPeripheral}. */
    private static final int BASE_METHOD_COUNT = 6;

    /** Offset of wired-only methods inside the full array. */
    private static final int WIRED_OFFSET = BASE_METHOD_COUNT;

    // Expected indices for the wired-only segment.
    private static final int IDX_GET_NAMES_REMOTE = WIRED_OFFSET;
    private static final int IDX_IS_PRESENT_REMOTE = WIRED_OFFSET + 1;
    private static final int IDX_GET_TYPE_REMOTE = WIRED_OFFSET + 2;
    private static final int IDX_GET_METHODS_REMOTE = WIRED_OFFSET + 3;
    private static final int IDX_CALL_REMOTE = WIRED_OFFSET + 4;
    private static final int IDX_HAS_TYPE_REMOTE = WIRED_OFFSET + 5;
    private static final int IDX_GET_NAME_LOCAL = WIRED_OFFSET + 6;

    private static ModemPeripheral peripheral;

    @BeforeAll
    static void buildPeripheral() throws Exception {
        // Locate the private static inner class TileCable$Peripheral.
        Class<?> peripheralClass = Arrays.stream(TileCable.class.getDeclaredClasses())
            .filter(
                c -> c.getSimpleName()
                    .equals("Peripheral"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("TileCable$Peripheral not found"));

        Constructor<?> ctor = peripheralClass.getDeclaredConstructor(TileCable.class);
        ctor.setAccessible(true);

        // null entity is safe: getMethodNames() never touches m_entity.
        peripheral = (ModemPeripheral) ctor.newInstance((TileCable) null);
    }

    // =========================================================================
    // Method count
    // =========================================================================

    @Test
    void totalMethodCountIsThirteen() {
        assertEquals(13, peripheral.getMethodNames().length);
    }

    // =========================================================================
    // Base methods (inherited from ModemPeripheral) — must not shift
    // =========================================================================

    @Test
    void baseMethodsAreUnchanged() {
        List<String> names = Arrays.asList(peripheral.getMethodNames());
        assertEquals("open", names.get(0));
        assertEquals("isOpen", names.get(1));
        assertEquals("close", names.get(2));
        assertEquals("closeAll", names.get(3));
        assertEquals("transmit", names.get(4));
        assertEquals("isWireless", names.get(5));
    }

    // =========================================================================
    // Wired-only methods — pre-existing five must not shift
    // =========================================================================

    @Test
    void getNamesRemoteAtExpectedIndex() {
        assertEquals("getNamesRemote", peripheral.getMethodNames()[IDX_GET_NAMES_REMOTE]);
    }

    @Test
    void isPresentRemoteAtExpectedIndex() {
        assertEquals("isPresentRemote", peripheral.getMethodNames()[IDX_IS_PRESENT_REMOTE]);
    }

    @Test
    void getTypeRemoteAtExpectedIndex() {
        assertEquals("getTypeRemote", peripheral.getMethodNames()[IDX_GET_TYPE_REMOTE]);
    }

    @Test
    void getMethodsRemoteAtExpectedIndex() {
        assertEquals("getMethodsRemote", peripheral.getMethodNames()[IDX_GET_METHODS_REMOTE]);
    }

    @Test
    void callRemoteAtExpectedIndex() {
        assertEquals("callRemote", peripheral.getMethodNames()[IDX_CALL_REMOTE]);
    }

    // =========================================================================
    // New methods
    // =========================================================================

    @Test
    void hasTypeRemoteIsPresentAtExpectedIndex() {
        assertEquals("hasTypeRemote", peripheral.getMethodNames()[IDX_HAS_TYPE_REMOTE]);
    }

    @Test
    void getNameLocalIsPresentAtExpectedIndex() {
        assertEquals("getNameLocal", peripheral.getMethodNames()[IDX_GET_NAME_LOCAL]);
    }

    @Test
    void getNameLocalIsLastMethod() {
        String[] names = peripheral.getMethodNames();
        assertEquals("getNameLocal", names[names.length - 1]);
    }

    // =========================================================================
    // Type — sanity check
    // =========================================================================

    @Test
    void peripheralTypeIsModem() {
        assertEquals("modem", peripheral.getType());
    }

}
