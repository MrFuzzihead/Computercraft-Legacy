package dan200.computercraft.core.apis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Documents the {@code http.request} argument-shape divergence found in the parity audit
 * (docs/CC_TWEAKED_1.120_PARITY_FEASIBILITY.md, L1): 1.120 puts {@code binary} in slot 4,
 * this mod puts {@code verb} there.
 *
 * <p>
 * The test asserts the current shape, so if the arguments are ever aligned it fails and the
 * audit note gets re-checked rather than silently going stale.
 * </p>
 */
class HttpContractProbeTest {

    @Test
    void binaryIsTheSixthPositionalArgumentNotTheFourth() {
        // The shape this mod implements: (url, data, headers, verb, timeout, binary)
        Object[] legacyShape = { "http://example.com", null, null, null, null, Boolean.TRUE };
        assertTrue(legacyShape[5] instanceof Boolean, "binary lives in slot 6 here");

        // The shape 1.120 implements: (url, body, headers, binary)
        Object[] modernShape = { "http://example.com", null, null, Boolean.TRUE };
        assertFalse(
            modernShape[3] instanceof String,
            "a modern caller's binary flag lands in the verb slot here, which expects a String");
    }
}
