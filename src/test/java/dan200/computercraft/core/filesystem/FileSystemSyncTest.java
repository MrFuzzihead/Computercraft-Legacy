package dan200.computercraft.core.filesystem;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dan200.computercraft.api.filesystem.IMount;

/**
 * Unit tests for {@link FileSystem} locking discipline (B8).
 *
 * <p>
 * Every other public {@link FileSystem} method is {@code synchronized}; B8
 * tracks {@code getFreeSpace} as the one exception. The reflection audit fails
 * if any public method (other than the deliberately unsynchronized handle
 * bookkeeping trio) loses its {@code synchronized} modifier, and the
 * concurrency hammer exercises {@code getFreeSpace} against mutating callers.
 * </p>
 */
class FileSystemSyncTest {

    /**
     * Public methods that are intentionally not synchronized:
     * <ul>
     * <li>{@code unload}/{@code addFile}/{@code removeFile} operate on the
     * open-handle set rather than the mount tree (see the B8 finding).</li>
     * <li>{@code getName}/{@code contains}/{@code getDirectory}/{@code toLocal}
     * are pure path helpers that read no mutable state.</li>
     * </ul>
     */
    private static final Set<String> UNSYNCHRONIZED_METHODS = new HashSet<>(
        Arrays.asList("unload", "addFile", "removeFile", "getName", "contains", "getDirectory", "toLocal"));

    @TempDir
    Path tempDir;

    private FileSystem fileSystem;

    @BeforeEach
    void setUp() throws FileSystemException {
        fileSystem = new FileSystem("hdd", new dan200.computercraft.core.filesystem.FileMount(tempDir.toFile(), 1024 * 1024L));
    }

    @Test
    void allPublicMethodsExceptHandleBookkeepingAreSynchronized() {
        List<String> violations = new ArrayList<>();
        Arrays.stream(FileSystem.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .filter(method -> !method.isSynthetic() && !method.isBridge())
            .filter(method -> !UNSYNCHRONIZED_METHODS.contains(method.getName()))
            .forEach(method -> {
                if (!Modifier.isSynchronized(method.getModifiers())) {
                    violations.add(method.getName());
                }
            });

        assertTrue(violations.isEmpty(), "Unsynchronized public FileSystem methods: " + violations);
    }

    @Test
    void getFreeSpaceIsSynchronizedLikeItsSiblings() throws Exception {
        assertTrue(
            Modifier.isSynchronized(FileSystem.class.getMethod("getFreeSpace", String.class).getModifiers()),
            "getFreeSpace must be synchronized like every other public FileSystem method");
    }

    @Test
    void getFreeSpaceReturnsRemainingCapacity() throws Exception {
        // Fresh 1 MiB mount — free space must be positive and shrink after a
        // write performed through the FileSystem API (FileMount accounts usage
        // via its own writes, not raw disk inspection).
        long initial = fileSystem.getFreeSpace("");
        assertTrue(initial > 0L && initial <= 1024 * 1024L);

        dan200.computercraft.core.filesystem.IMountedFileNormal handle = fileSystem.openForWrite("blob.txt", false);
        handle.write("0123456789".getBytes(StandardCharsets.UTF_8), 0, 10, false);
        handle.close();

        long after = fileSystem.getFreeSpace("");
        assertTrue(after < initial, "free space must shrink after writing, " + after + " >= " + initial);
    }

    @Test
    void concurrentGetFreeSpaceSurvivesDirectoryChurn() throws Exception {
        int readers = 6;
        int iterations = 400;
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean(false);
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < readers; i++) {
            Thread reader = new Thread(() -> {
                try {
                    start.await();
                    for (int n = 0; n < iterations && !stop.get(); n++) {
                        fileSystem.getFreeSpace("");
                        fileSystem.getFreeSpace("churn");
                        fileSystem.getCapacity("");
                        fileSystem.exists("churn");
                    }
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
            reader.setDaemon(true);
            threads.add(reader);
        }

        Thread mutator = new Thread(() -> {
            try {
                start.await();
                for (int n = 0; n < 60 && !stop.get(); n++) {
                    try {
                        fileSystem.makeDir("churn");
                        fileSystem.delete("churn");
                    } catch (FileSystemException expectedDuringDelete) {
                        // A concurrent reader may hold nothing; delete of a missing
                        // path throws and is acceptable under contention.
                    }
                }
            } catch (Throwable t) {
                failures.add(t);
            }
        });
        mutator.setDaemon(true);
        threads.add(mutator);

        for (Thread thread : threads) thread.start();
        start.countDown();
        for (Thread thread : threads) thread.join(20_000);
        stop.set(true);

        for (Thread thread : threads) assertFalse(thread.isAlive(), "thread must not be stuck on the FS lock");

        assertTrue(failures.isEmpty(), "concurrent FileSystem access must not throw unexpected exceptions");
    }
}
