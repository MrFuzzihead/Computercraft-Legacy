import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // The file-drop watcher has org.lwjgl:lwjgl-sdl on the test runtime classpath so that it can be
    // loaded at all; LWJGL then calls System.loadLibrary, which modern JDKs warn about. The wrappers
    // it will load do not exist here (that is the failure path the test exercises), so the warning is
    // pure noise.
    //
    // The test *JVM* is the toolchain (Java 25) even under jvmDowngrader; only the test classes are
    // downgraded to Java 8. --enable-native-access is therefore still a valid flag.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

// ---------------------------------------------------------------------------------------------
// Strip the JVM Downgrader multi-release overlay.
//
// FML 1.7.10's JarDiscoverer feeds *every* .class entry in a mod jar to ASM 5.0.3 via
// ASMModParser. ASM 5.0.3 understands class file versions up to 52 (Java 8); anything newer makes
// it throw IllegalArgumentException, after which FML logs "Zip file ... failed to read properly, it
// will be ignored" and discards the entire mod. The mod then does not load at all -- no crash, no
// other symptom, computers simply never turn on.
//
// JVM Downgrader emits a META-INF/versions/N/ overlay that trips this. The build cannot be asked to
// omit it: an empty jvmDowngraderMultiReleaseVersions falls back to the 21,25 default, and the
// convention rejects values below 9. So multiReleaseVersions = 9 in gradle.properties keeps the
// build satisfied and the overlay is removed here instead.
//
// Removing it loses nothing for a 1.7.10 mod: downgradeJar already writes a fully downgraded copy of
// every overlay class at the jar root. Those root copies are what Java 8 would have used anyway, and
// they are semantically equivalent -- the overlay merely avoids the jvmdg API stubs for the classes
// that use Java 9+ stdlib calls (in practice only Arrays.mismatch/Arrays.equals in LuaString and the
// java.lang.Record supertype of Cobalt's records).
//
// The overlay is stripped by rewriting the finished archive rather than with a CopySpec exclude,
// because entries contributed through the JvmDowngrader task chain do not pick the pattern up
// reliably. The same pass then asserts the result, so a regression fails the build rather than
// silently shipping a mod FML will discard.
// ---------------------------------------------------------------------------------------------

// Only the archives that actually ship are rewritten and checked. "jar" and "shadowJar" are the
// pre-downgrade inputs (class file version 69) and must be left alone.
val downgradedArchives = setOf("downgradeJar", "shadeDowngradedApi", "downgradeApiJar", "reobfJar")

tasks.withType<Jar>().configureEach {
    val isDowngradedArchive = name in downgradedArchives
    val archive = archiveFile

    doLast {
        val jarFile = archive.get().asFile

        // Drop META-INF/versions/** first. Rewriting in place needs a temporary file, because the
        // source archive is open for the whole copy.
        val stripped = File(jarFile.parentFile, jarFile.name + ".stripped")
        var removed = 0
        java.util.zip.ZipFile(jarFile).use { input ->
            java.util.zip.ZipOutputStream(stripped.outputStream()).use { output ->
                input.entries().asSequence().forEach { entry ->
                    if (entry.name.startsWith("META-INF/versions/")) {
                        removed++
                    } else {
                        output.putNextEntry(java.util.zip.ZipEntry(entry.name))
                        if (!entry.isDirectory) {
                            input.getInputStream(entry).use { it.copyTo(output) }
                        }
                        output.closeEntry()
                    }
                }
            }
        }
        java.nio.file.Files.move(
            stripped.toPath(),
            jarFile.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING
        )

        if (!isDowngradedArchive) return@doLast

        // Refuse to ship anything FML's ASM 5.0.3 would reject. The class file major version is a
        // big-endian u2 at offset 6, so both bytes matter: Java 8 is 0x0034 and Java 25 is 0x0045.
        // (An earlier revision of this check only read byte 6, which is always 0x00, so it could
        // never fire. Do not simplify it back.)
        java.util.zip.ZipFile(jarFile).use { input ->
            val tooNew = input.entries().asSequence()
                .filter { it.name.endsWith(".class") }
                .mapNotNull { entry ->
                    val header = ByteArray(8)
                    val read = input.getInputStream(entry).use { it.read(header) }
                    if (read < 8 || header[0] != 0xCA.toByte() || header[1] != 0xFE.toByte()) return@mapNotNull null
                    val major = ((header[6].toInt() and 0xFF) shl 8) or (header[7].toInt() and 0xFF)
                    if (major > 52) "${entry.name} (version $major)" else null
                }
                .toList()
            check(tooNew.isEmpty()) {
                "$name contains class files newer than Java 8, which FML 1.7.10's ASM 5.0.3 cannot " +
                    "read; the mod would be silently ignored at load time: $tooNew"
            }
        }

        val remaining = java.util.zip.ZipFile(jarFile).use { input ->
            input.entries().asSequence().count { it.name.startsWith("META-INF/versions/") }
        }
        check(remaining == 0) {
            "$name still contains $remaining META-INF/versions/ entries after stripping; FML 1.7.10 " +
                "would discard the whole mod."
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Fail the build if the test suite silently executed nothing.
//
// GTNHConvention's JVMDowngraderModule re-points the test task at the JvmDowngrader output and puts
// the downgraded main classes on the runtime classpath. That is the behaviour we want: the suite
// exercises the bytecode we actually ship.
//
// The gap is that Test's @SkipWhenEmpty source input is snapshotted before the downgradeTestClasses
// task that populates it runs. In a freshly cleaned tree that input is therefore empty, Test is
// dropped as NO-SOURCE, and `check` still reports BUILD SUCCESSFUL having run zero tests. Verified
// on this tree: wiping build/tmp/downgradeTestClasses + build/classes/java/test + build/test-results
// yields BUILD SUCCESSFUL with no XML result files on the first invocation, and the full suite on
// --rerun-tasks.
//
// Only registers when the downgrader is in use, so `modern` and `jabel` builds keep exactly the
// behaviour the convention set up.
// ---------------------------------------------------------------------------------------------
val usingJvmDowngrader = providers.gradleProperty("enableModernJavaSyntax")
    .map { it.equals("jvmDowngrader", ignoreCase = true) }
    .orElse(false)

if (usingJvmDowngrader.get()) {
    // Deliberately captures only Providers (configuration-cache safe). Reading Test.state would
    // require holding the task, which the configuration cache refuses to serialize.
    val testResults = layout.buildDirectory.dir("test-results/test")
    val testHasFilters = tasks.named<Test>("test")
        .map { it.filter.includePatterns.isNotEmpty() || it.filter.excludePatterns.isNotEmpty() }

    val verifyTestSuiteExecuted = tasks.register("verifyTestSuiteExecuted") {
        group = "verification"
        description = "Fails unless the test suite actually produced results."
        dependsOn("test")
        doLast {
            // An explicit --tests/--include filter can legitimately match nothing; do not turn that
            // into a build failure.
            if (testHasFilters.get()) return@doLast

            val results = testResults.get().asFile
            val ran = results.isDirectory &&
                results.listFiles { f -> f.isFile && f.name.endsWith(".xml") }?.isNotEmpty() == true

            check(ran) {
                """
                |The test suite did not run: no results in $results.
                |This happens on the first build after build/tmp is cleared, because
                |JvmDowngrader's downgradeTestClasses has not run yet when :test's source
                |input is snapshotted, so :test is dropped with NO-SOURCE. The build would
                |otherwise report success with zero tests executed.
                |Re-run the build, or use --rerun-tasks, to execute the suite.
                """.trimMargin()
            }
        }
    }

    tasks.matching { it.name == "check" }.configureEach { dependsOn(verifyTestSuiteExecuted) }
}

// ---------------------------------------------------------------------------------------------
// Make the plain `runServer` dev task able to start this mod at all.
//
// `runServer` is RFG's Java 8 task: its classpath is the ordinary runtimeClasspath, which does not
// contain lwjgl3ify (the convention only puts that in `java17Dependencies`, which is what
// runServer17/21/25 use). This mod declares `required-after:lwjgl3ify`, and lwjgl3ify needs
// Java 17+, so `runServer` can never start it.
//
// It did not fail loudly either. Observed on `main` (enableModernJavaSyntax = modern):
//
//   [FML]: There was a problem reading the entry dan200/computercraft/ComputerCraft$Blocks.class
//          in the jar ComputerCraft-3.1.0-dev-preshadow.jar - probably a corrupt zip
//   [FML]: Zip file ComputerCraft-3.1.0-dev-preshadow.jar failed to read properly, it will be ignored
//   [fml.ModTracker]: This world was saved with mod ComputerCraft which appears to be missing
//
// The only ComputerCraft jar that task could see was the `-dev-preshadow` intermediate, whose class
// files are newer than Java 8, so JarDiscoverer discarded it and the server started *without* the
// mod. The GTNH CI workflow hardcodes `runServer`, so its "Test no errors reported during server
// run" step was green because it tested a server with no ComputerCraft on it.
//
// Forward the task to runServer25 rather than ship a dev task that silently drops the mod. This is
// safe to do: RunHotswappableMinecraftTask's `superTask` field is only a label and is never read
// anywhere in GTNHGradle, so runServer25 does not depend on runServer and there is no cycle.
// `runClient` is left alone; use runClient25.
// ---------------------------------------------------------------------------------------------
afterEvaluate {
    if (tasks.findByName("runServer25") != null) {
        tasks.named("runServer") {
            description = "Runs the modded dedicated server on Java 25 with lwjgl3ify (forwards to runServer25)"
            dependsOn("runServer25")
            // The Java 8 body must not run: it would boot a second server with no ComputerCraft on it.
            onlyIf("runServer is a no-op alias for runServer25 in this project") { false }
        }
    }
}
