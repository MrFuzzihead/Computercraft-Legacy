package dan200.computercraft.shared.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BlockStateBridge}.
 *
 * <p>
 * GTNHLib is not on the test classpath (it is an optional runtime dependency, looked up by name),
 * so these pin the fallback contract: the caller always gets a table, and it always carries the
 * metadata integer. The success path needs a world and GTNHLib installed, so it is covered by the
 * in-game pack instead.
 * </p>
 */
class BlockStateBridgeTest {

    @Test
    void metadataFallbackCarriesTheMetadata() {
        Map<Object, Object> state = BlockStateBridge.metadataFallback(7);
        assertEquals(1, state.size(), "the fallback is exactly one entry");
        assertEquals(7, ((Number) state.get("metadata")).intValue());
    }

    @Test
    void metadataFallbackHandlesZeroAndHighValues() {
        assertEquals(
            0,
            ((Number) BlockStateBridge.metadataFallback(0)
                .get("metadata")).intValue());
        assertEquals(
            15,
            ((Number) BlockStateBridge.metadataFallback(15)
                .get("metadata")).intValue());
    }

    @Test
    void aMissingWorldFallsBackToMetadata() {
        // Without a world there is nothing to query, so the bridge must not even try.
        Map<Object, Object> state = BlockStateBridge.getState(null, 0, 0, 0, null, 3);
        assertNotNull(state);
        assertEquals(3, ((Number) state.get("metadata")).intValue());
    }

    @Test
    void gtnhLibAbsenceIsDetected() {
        // Not on the test classpath; if this ever fails, the bridge's reflection needs revisiting.
        assertFalse(BlockStateBridge.hasGtnhLib(), "GTNHLib should not be on the unit test classpath");
    }

    @Test
    void queryingWithoutGtnhLibStillReturnsMetadata() {
        Map<Object, Object> state = BlockStateBridge.getState(null, 0, 0, 0, null, 11);
        assertTrue(state.containsKey("metadata"));
        assertEquals(11, ((Number) state.get("metadata")).intValue());
    }
}
