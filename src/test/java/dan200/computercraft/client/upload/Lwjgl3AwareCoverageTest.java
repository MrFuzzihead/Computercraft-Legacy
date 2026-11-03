package dan200.computercraft.client.upload;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards the one build-time trap in using LWJGL3 from this mod: lwjgl3ify's namespace rewrite.
 *
 * <p>
 * lwjgl3ify rewrites every {@code org/lwjgl/} reference in mod bytecode to {@code org/lwjglx/} so
 * that legacy LWJGL2 calls reach its shims. LWJGL3 shares that package prefix, so a class using the
 * real LWJGL3 gets rewritten into a namespace with no such class - and the client dies on the first
 * tick with {@code NoClassDefFoundError: org/lwjglx/sdl/SDL_EventFilter}. {@code @Lwjgl3Aware} is the
 * documented opt-out, and it is <strong>per class file</strong>: an anonymous class implementing an
 * SDL interface is rewritten even when its outer class is exempt. That is not a hypothetical - it is
 * exactly how this first failed.
 * </p>
 *
 * <p>
 * So: scan the compiled classes, and require every one that mentions {@code org.lwjgl.sdl} or
 * {@code org.lwjgl.system} (LWJGL3-only packages) to carry the annotation. When this test fails, the
 * fix is to add {@code @Lwjgl3Aware} - moving the code into a named nested class first if it is
 * currently anonymous.
 * </p>
 */
class Lwjgl3AwareCoverageTest {

    private static final String ANNOTATION = "me/eigenraven/lwjgl3ify/api/Lwjgl3Aware";

    /** LWJGL3-only package prefixes. LWJGL2's own packages are deliberately not listed: those *should* be rewritten. */
    private static final String[] LWJGL3_PACKAGES = { "org/lwjgl/sdl/", "org/lwjgl/system/" };

    @Test
    void everyClassThatUsesLwjgl3IsAware() throws IOException {
        Path classes = Path.of("build", "classes", "java", "main");
        assumeTrue(Files.isDirectory(classes), "compiled classes are not available: " + classes.toAbsolutePath());

        List<Path> classFiles;
        try (Stream<Path> walk = Files.walk(classes)) {
            classFiles = walk.filter(
                p -> p.toString()
                    .endsWith(".class"))
                .toList();
        }

        List<String> offences = new ArrayList<>();
        int checked = 0;

        for (Path file : classFiles) {
            String contents = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            boolean usesLwjgl3 = false;
            for (String pkg : LWJGL3_PACKAGES) {
                if (contents.contains(pkg)) {
                    usesLwjgl3 = true;
                    break;
                }
            }

            if (!usesLwjgl3) {
                continue;
            }

            checked++;
            if (!contents.contains(ANNOTATION)) {
                offences.add(
                    classes.relativize(file)
                        .toString());
            }
        }

        assertFalse(
            offences.isEmpty() && classFiles.isEmpty(),
            "no classes were scanned, so this test is not checking anything");

        assertFalse(
            checked == 0,
            "expected at least the file drop watcher to use LWJGL3; did the drop hook get removed?");
        assertFalse(
            !offences.isEmpty(),
            "these classes use LWJGL3 but are not @Lwjgl3Aware, so lwjgl3ify will rewrite them into a "
                + "namespace that has no such classes (crash at runtime): "
                + offences);
    }
}
