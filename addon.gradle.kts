import org.gradle.api.tasks.testing.Test

tasks.withType<Test> {
    useJUnitPlatform()
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
