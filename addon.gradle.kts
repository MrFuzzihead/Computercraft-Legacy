import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test

tasks.withType<Test> {
    useJUnitPlatform()
}

// FML 1.7.10's JarDiscoverer feeds every .class entry in a mod jar to ASM 5.0.3
// (asm-debug-all-5.0.3) via ASMModParser. ASM 5.0.3 rejects any class file version above
// Java 8 with IllegalArgumentException, after which FML logs "Zip file ... failed to read
// properly, it will be ignored" and discards the entire mod. The mod then silently does not
// load at all -- no crash, no other symptom, computers simply never turn on.
//
// JVM Downgrader emits a multi-release overlay (META-INF/versions/N) that trips this. The jar
// root is already fully downgraded to Java 8 by downgradeJar, so the overlay gains nothing for
// a 1.7.10 mod. jvmDowngraderMultiReleaseVersions = 9 in gradle.properties keeps the build
// itself satisfied, since the convention rejects values below 9.
//
// The overlay is stripped by rewriting the finished archive rather than with a CopySpec
// exclude, because entries contributed through the JvmDowngrader task chain do not pick the
// pattern up reliably. The rewrite also asserts the result, so a regression here fails the
// build rather than silently shipping a mod FML will discard.
tasks.withType<Jar>().configureEach {
    doLast {
        val jarFile = archiveFile.get().asFile
        val tmpFile = File(jarFile.parentFile, jarFile.name + ".stripped")
        java.util.zip.ZipFile(jarFile).use { zin ->
            java.util.zip.ZipOutputStream(tmpFile.outputStream()).use { zout ->
                zin.entries().asSequence().forEach { entry ->
                    if (!entry.name.startsWith("META-INF/versions/")) {
                        zout.putNextEntry(java.util.zip.ZipEntry(entry.name))
                        if (!entry.isDirectory) {
                            zin.getInputStream(entry).use { it.copyTo(zout) }
                        }
                        zout.closeEntry()
                    }
                }
            }
        }
        java.nio.file.Files.move(
            tmpFile.toPath(), jarFile.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING
        )

        // Belt and braces: refuse to ship anything FML's ASM 5.0.3 would reject.
        java.util.zip.ZipFile(jarFile).use { zin ->
            val tooNew = zin.entries().asSequence()
                .filter { it.name.endsWith(".class") }
                .filter { entry ->
                    val header = ByteArray(8)
                    zin.getInputStream(entry).use { s -> s.read(header) }
                    header[0] == 0xCA.toByte() && header[1] == 0xFE.toByte() &&
                        (header[6].toInt() and 0xFF) > 52
                }
                .map { it.name }
                .toList()
            check(tooNew.isEmpty()) {
                "Mod jar contains class files newer than Java 8, which FML 1.7.10's ASM 5.0.3 " +
                    "cannot read; the mod would be ignored at load time: $tooNew"
            }
        }
    }
}

// GTNHConvention's JVMDowngraderModule (only active when
// enableModernJavaSyntax = jvmDowngrader) re-points the test task at the JvmDowngrader
// output and puts the downgraded main classes on the runtime classpath. That is the
// behaviour we want: the suite exercises the bytecode we actually ship.
//
// The gap is that Test's @SkipWhenEmpty source input is snapshotted before the
// `downgradeTestClasses` task that populates it runs. In a freshly cleaned tree that input
// is therefore empty, Test is skipped with NO-SOURCE, and `check` still reports
// BUILD SUCCESSFUL having run zero tests. Verified: with jvmDowngrader a wiped
// build/tmp + build/classes/java/test yields 0 results on the first invocation and full
// results on the second; the same wiped state under jabel runs all 1394 tests immediately.
//
// We cannot schedule around @SkipWhenEmpty from here, so fail loudly instead of silently
// reporting green. Note that we deliberately do NOT override testClassesDirs: doing so
// changes the first-run failure from "skipped" into a confusing
// "Could not execute test class ..." error. Leaving the convention's wiring alone keeps the
// symptom clean (tests simply do not run on the first invocation after a clean) and lets the
// guard below name the real cause and the fix.
//
// This block is inert unless the JvmDowngrader tasks exist, leaving plain jabel mode
// exactly as the convention set it up.
afterEvaluate {
    if (tasks.findByName("downgradeTestClasses") != null) {
        tasks.named<Test>("test").configure { dependsOn("downgradeTestClasses") }

        // Deliberately captures only a Provider<Directory> (configuration-cache safe). Reading
        // Test.state would require holding the task, which the configuration cache refuses to
        // serialize. Presence of result files covers both ways the suite can silently not run:
        // dropped as NO-SOURCE, or considered up-to-date having executed nothing.
        val testResults = layout.buildDirectory.dir("test-results/test")

        val verifyTestSuiteExecuted = tasks.register("verifyTestSuiteExecuted") {
            group = "verification"
            description = "Fails unless the test suite actually produced results."
            dependsOn("test")
            doLast {
                val results = testResults.get().asFile
                val ran = results.isDirectory && results.listFiles { f ->
                    f.isFile && f.name.endsWith(".xml")
                }?.isNotEmpty() == true

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

        tasks.named("check") { dependsOn(verifyTestSuiteExecuted) }
    }
}
