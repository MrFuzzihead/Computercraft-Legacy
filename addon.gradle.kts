import org.gradle.api.tasks.testing.Test

tasks.withType<Test> {
    useJUnitPlatform()

    // The file-drop watcher has org.lwjgl:lwjgl-sdl on the test runtime classpath so that it can be
    // loaded at all; LWJGL then calls System.loadLibrary, which modern JDKs warn about. The wrappers
    // it will load do not exist here (that is the failure path the test exercises), so the warning is
    // pure noise.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
