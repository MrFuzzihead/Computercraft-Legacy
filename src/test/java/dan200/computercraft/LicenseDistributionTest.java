package dan200.computercraft;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The license and provenance files are duplicated from the repository root into
 * {@code src/main/resources}, so that the released jar carries them. Two
 * obligations make that necessary: CCPL section 5 requires a distribution to
 * carry the terms it is offered under, and the MIT-licensed libraries shaded
 * into the jar (Cobalt, Java-WebSocket, slf4j-api) each require their licence
 * text to travel with every copy.
 *
 * <p>
 * Being a plain copy, the duplication can drift: edit a licence at the root and
 * the copy in the jar silently keeps the old text. These tests fail the build
 * when that happens, and when a licence is added at the root without being
 * copied into the resources.
 * </p>
 */
class LicenseDistributionTest {

    /**
     * A file in the project root is a licence file if it is named {@code NOTICE}
     * or begins with {@code LICENSE}.
     */
    private static boolean isLicenseName(String name) {
        return name.equals("NOTICE") || name.startsWith("LICENSE");
    }

    /**
     * Walk up from the working directory to the project root, so the test does
     * not depend on being launched from a particular directory.
     */
    private static Path projectRoot() {
        Path dir = Paths.get("")
            .toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("settings.gradle.kts"))
                && Files.isRegularFile(dir.resolve("gradlew"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
            "Could not locate the project root above " + Paths.get("")
                .toAbsolutePath());
    }

    private static List<String> licenseNamesIn(Path dir) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(Files::isRegularFile)
                .map(
                    path -> path.getFileName()
                        .toString())
                .filter(LicenseDistributionTest::isLicenseName)
                .sorted()
                .collect(Collectors.toList());
        }
    }

    private static byte[] read(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    /**
     * The mod targets Java 8 bytecode, so {@link InputStream#readAllBytes()} is
     * not available here.
     */
    private static byte[] read(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int read; (read = is.read(buffer)) != -1;) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    void everyRootLicenseIsCopiedIntoResources() throws IOException {
        Path root = projectRoot();
        List<String> expected = licenseNamesIn(root);
        List<String> actual = licenseNamesIn(
            root.resolve("src")
                .resolve("main")
                .resolve("resources"));

        assertFalse(expected.isEmpty(), "no licence files found in " + root + "; the name filter is broken");
        assertEquals(
            expected,
            actual,
            "src/main/resources must hold a copy of exactly the licence files in the project root. "
                + "A licence added at the root has to be copied, and a copy with no root original has to be deleted.");
    }

    @Test
    void resourceCopiesAreIdenticalToTheRootOriginals() throws IOException {
        Path root = projectRoot();
        Path resources = root.resolve("src")
            .resolve("main")
            .resolve("resources");

        for (String name : licenseNamesIn(root)) {
            assertArrayEquals(
                read(root.resolve(name)),
                read(resources.resolve(name)),
                name + " has drifted from the project root copy; update src/main/resources/" + name + " to match");
        }
    }

    @Test
    void licenseCopiesAreOnTheClasspathAndSoShip() throws IOException {
        Path root = projectRoot();

        for (String name : licenseNamesIn(root)) {
            byte[] expected = read(root.resolve(name));
            try (InputStream is = LicenseDistributionTest.class.getResourceAsStream("/" + name)) {
                assertNotNull(is, name + " is not on the test classpath, so it will not reach the jar");
                assertArrayEquals(
                    expected,
                    read(is),
                    "the packaged copy of " + name + " differs from the root original");
            }
        }
    }

    @Test
    void theProjectIsNotLicensedMit() throws IOException {
        // Regression guard. This project's ComputerCraft code is decompiled from
        // a CCPL binary, and CCPL section 5 requires every distribution of the
        // mod to remain CCPL. A root MIT licence would purport to relicense
        // Daniel Ratcliffe's ComputerCraft code, and the MPL-2.0 backports, to
        // terms the CCPL forbids.
        String license = new String(read(projectRoot().resolve("LICENSE")), StandardCharsets.UTF_8);
        int firstNewline = license.indexOf('\n');
        String firstLine = firstNewline < 0 ? license : license.substring(0, firstNewline);
        assertTrue(
            license.startsWith("ComputerCraft Public License"),
            "the root LICENSE must be the ComputerCraft Public License, not '" + firstLine + "'");
    }
}
