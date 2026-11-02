package dan200.computercraft.client.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/**
 * Tests that the SDL drop hook fails safely.
 *
 * <p>
 * lwjgl3ify is a hard dependency, so a real client always has the SDL3 bindings and their native
 * libraries. A test JVM has the bindings (they are on the test runtime classpath) but no natives, so
 * what this pins is the guard: installing must report itself unavailable and log once, rather than
 * throwing into the client tick or leaving the game unable to start. That guard is also what protects
 * a real client whose SDL install is broken.
 * </p>
 *
 * <p>
 * The queue itself is plain Java and needs no SDL at all.
 * </p>
 */
class SdlFileDropWatcherTest {

    @Test
    void installingWithoutNativeSdlFailsSafely() {
        SdlFileDropWatcher.install();

        assertFalse(SdlFileDropWatcher.isAvailable(), "there are no SDL natives in a test JVM");
        assertEquals(0, SdlFileDropWatcher.pollDrops().length);
    }

    @Test
    void installingTwiceDoesNotThrow() {
        SdlFileDropWatcher.install();
        SdlFileDropWatcher.install();

        assertFalse(SdlFileDropWatcher.isAvailable());
    }

    @Test
    void pollingWithNothingQueuedReturnsAnEmptyArray() {
        SdlFileDropWatcher.clear();

        assertEquals(0, SdlFileDropWatcher.pollDrops().length);
        assertEquals(0, SdlFileDropWatcher.pollDrops().length, "polling must be idempotent when nothing arrives");
    }
}
