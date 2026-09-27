package dan200.computercraft.shared.turtle;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Guards the drop-capture release path in the two turtle entry points that use it.
 *
 * <p>
 * {@code TurtleTool#attack} and {@code TurtlePlaceCommand#deployOnEntity} both bracket a call that
 * can throw ({@code hitByEntity} / {@code attackEntityFrom} / {@code interactFirst}) with
 * {@code ComputerCraft.setEntityDropConsumer} and
 * {@code ComputerCraft.clearEntityDropConsumer}. {@code captureDrops} is a flag on the
 * <em>entity</em>, not on the consumer, so if the clear is skipped the entity stays captured with
 * a consumer nobody holds: every drop it then produces is diverted into
 * {@code Entity#capturedDrops} and never delivered, silently deleting it. A {@code finally} is
 * therefore the only thing making the release reliable.
 * </p>
 *
 * <p>
 * This is a source-level guard rather than a behavioural test. Exercising
 * {@code TurtleTool#attack} for real needs a {@code WorldServer}, a {@code TurtlePlayer}
 * (a {@code FakePlayer}, which registers itself with the game registry) and a
 * {@code WorldUtil.rayTraceEntities} that returns a specific entity — all of which need a booted
 * Forge environment this test suite does not have. Asserting on the source instead is the same
 * technique {@code LoggingTest} uses to ban direct console writes, and it does catch the
 * regression that matters: removing the {@code finally}. The trade-off is that it pins structure
 * rather than runtime behaviour, so it cannot prove the exception actually propagates — the
 * companion checks in {@code TurtleDropConsumerTest} cover the proxy-side semantics.
 * </p>
 */
class DropConsumerReleaseTest {

    private static final Path SOURCES = Paths.get("src/main/java");

    private static final List<String> CALL_SITES = Arrays.asList(
        "dan200/computercraft/shared/turtle/upgrades/TurtleTool.java",
        "dan200/computercraft/shared/turtle/core/TurtlePlaceCommand.java");

    @Test
    void sourceGuardScansTheExpectedTree() {
        assertTrue(Files.isDirectory(SOURCES), "source guard must not silently scan an absent directory");
    }

    @Test
    void bothDropConsumerCallSitesReleaseInAFinallyBlock() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (String relative : CALL_SITES) {
            Path path = SOURCES.resolve(relative);
            assertTrue(Files.isRegularFile(path), "expected source file: " + relative);
            String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

            if (!source.contains("setEntityDropConsumer(")) {
                offenders.add(relative + " (no drop consumer to release)");
                continue;
            }

            if (!releasesInFinally(source)) {
                offenders.add(relative + " (clearEntityDropConsumer is not inside a finally block)");
            }
        }
        if (!offenders.isEmpty()) {
            fail("Always release entity drop capture in a finally block: " + offenders);
        }
    }

    /**
     * True when the file's {@code clearEntityDropConsumer} call is the first statement of a
     * {@code finally} block, i.e. it runs even if the guarded call throws.
     */
    private static boolean releasesInFinally(String source) {
        int clear = source.indexOf("clearEntityDropConsumer(");
        if (clear < 0) {
            return false;
        }

        // The nearest preceding "finally" must open after the last "try", and the clear must come
        // before the matching brace is closed -- approximated by requiring the finally keyword to
        // appear between the last "try" and the clear, with only a comment and the clear call in
        // between. Good enough to pin the shape, and it fails loudly if the guard is removed.
        int lastTry = source.lastIndexOf("try {", clear);
        if (lastTry < 0) {
            return false;
        }

        String between = source.substring(lastTry, clear);
        return between.contains("finally {");
    }
}
