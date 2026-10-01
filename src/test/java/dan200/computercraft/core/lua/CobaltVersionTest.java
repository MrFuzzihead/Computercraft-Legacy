package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards {@code _COBALT_VERSION} against the drift that let it read "0.6" while the Cobalt
 * dependency was on 0.9.9.
 *
 * <p>
 * Cobalt publishes no runtime version constant, and shadowing discards the jar's manifest
 * metadata, so {@link dan200.computercraft.core.lua.lib.cobalt.CobaltMachine} hardcodes the
 * string. That makes the two easy to desynchronise on a Cobalt bump, and nothing else in the
 * build notices: no compile error, no test failure, just Lua code reading a wrong version.
 * Reading the declaration here closes that gap.
 */
class CobaltVersionTest {

    /** Matches the cobalt coordinate in dependencies.gradle, capturing just the version. */
    private static final Pattern COBALT_DEP = Pattern.compile("cc\\.tweaked:cobalt:([0-9][^\\s\"']*)");

    /** Matches the literal passed to {@code _COBALT_VERSION}. */
    private static final Pattern COBALT_GLOBAL = Pattern
        .compile("_COBALT_VERSION\"\\s*,\\s*valueOf\\(\"([0-9][^\"]*)\"\\)");

    private static final Path DEPENDENCIES = Path.of("dependencies.gradle");

    private static final Path MACHINE = Path
        .of("src", "main", "java", "dan200", "computercraft", "core", "lua", "lib", "cobalt", "CobaltMachine.java");

    private static String read(Path path) throws Exception {
        assertTrue(Files.exists(path), () -> "expected to find " + path.toAbsolutePath());
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("dependencies.gradle declares exactly one cc.tweaked:cobalt coordinate")
    void singleCobaltDependency() throws Exception {
        Matcher matcher = COBALT_DEP.matcher(read(DEPENDENCIES));
        assertTrue(matcher.find(), "no cc.tweaked:cobalt dependency found in dependencies.gradle");
        assertTrue(
            !matcher.find(),
            () -> "dependencies.gradle declares more than one cobalt coordinate: " + matcher.group(1));
    }

    @Test
    @DisplayName("_COBALT_VERSION matches the cobalt version in dependencies.gradle")
    void versionMatchesDependency() throws Exception {
        Matcher dep = COBALT_DEP.matcher(read(DEPENDENCIES));
        assertTrue(dep.find(), "no cc.tweaked:cobalt dependency found in dependencies.gradle");
        String declared = dep.group(1);

        Matcher global = COBALT_GLOBAL.matcher(read(MACHINE));
        assertTrue(global.find(), "CobaltMachine no longer sets _COBALT_VERSION");
        String reported = global.group(1);

        assertEquals(
            declared,
            reported,
            () -> "_COBALT_VERSION is stale. CobaltMachine reports \"" + reported
                + "\" but dependencies.gradle pins "
                + declared
                + ". Update the literal in CobaltMachine, or Lua code will read the wrong version.");
    }
}
