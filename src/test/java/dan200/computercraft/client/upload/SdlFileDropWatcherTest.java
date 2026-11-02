package dan200.computercraft.client.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/**
 * Tests that the SDL drop hook is genuinely optional.
 *
 * <p>
 * The watcher reaches into {@code org.lwjgl.sdl} by reflection precisely so that the mod still loads
 * and runs on a client without lwjgl3ify, and so that a future lwjgl3ify change cannot break startup.
 * This test runs on a plain JVM with no LWJGL3 on the classpath at all, which is exactly the
 * "vanilla LWJGL2" case: installing must be a quiet no-op rather than a crash or an exception.
 * </p>
 */
class SdlFileDropWatcherTest {

    @Test
    void installingWithoutLwjgl3ifyIsANoOp() {
        SdlFileDropWatcher.install();

        assertFalse(SdlFileDropWatcher.isAvailable(), "SDL3 is not on the test classpath, so drops are unavailable");
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
