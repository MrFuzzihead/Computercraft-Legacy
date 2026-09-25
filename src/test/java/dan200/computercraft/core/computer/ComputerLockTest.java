package dan200.computercraft.core.computer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.core.lua.ILuaMachine;
import dan200.computercraft.core.terminal.Terminal;

/** C3 regression tests using real lifecycle tasks and bounded, explicitly released worker gates. */
class ComputerLockTest {

    private Computer computer;
    private ILuaAPI api;
    private String savedBiosPath;
    private Object savedRom;
    private final List<CountDownLatch> gates = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        savedBiosPath = ComputerCraft.biosPath;
        savedRom = field("s_romMount").get(null);
        ComputerCraft.biosPath = "/computer-lock-test-bios.lua";
        field("s_romMount").set(null, mock(IMount.class));
        IComputerEnvironment environment = mock(IComputerEnvironment.class);
        when(environment.createSaveDirMount(anyString(), anyLong())).thenReturn(mock(IWritableMount.class));
        computer = new Computer(environment, new Terminal(51, 19), 123);
        api = mock(ILuaAPI.class);
        when(api.getNames()).thenReturn(new String[] { "probe" });
        when(api.getMethodNames()).thenReturn(new String[] { "run" });
        // Isolate Computer's lifecycle from unrelated APIs, keeping the real Cobalt boot path.
        List<ILuaAPI> apis = new ArrayList<>();
        apis.add(api);
        field("m_apis").set(computer, apis);
    }

    @AfterEach
    void tearDown() throws Exception {
        for (CountDownLatch gate : gates) gate.countDown();
        try {
            drain();
            computer.unload();
            drain();
        } finally {
            ComputerThread.stop();
            ComputerCraft.biosPath = savedBiosPath;
            field("s_romMount").set(null, savedRom);
        }
    }

    @Test
    void startupLocksComputerButFirstLuaResumeDoesNot() throws Exception {
        AtomicReference<Boolean> startupLocked = new AtomicReference<>();
        AtomicReference<Boolean> luaLocked = new AtomicReference<>();
        CountDownLatch enteredLua = new CountDownLatch(1);
        CountDownLatch releaseLua = gate();
        doAnswer(invocation -> {
            startupLocked.set(Thread.holdsLock(computer));
            return null;
        }).when(api)
            .startup();
        when(api.callMethod(any(), eq(0), any())).thenAnswer(invocation -> {
            luaLocked.set(Thread.holdsLock(computer));
            enteredLua.countDown();
            await(releaseLua);
            return null;
        });

        computer.turnOn();
        computer.advance(0.05);
        await(enteredLua);
        try {
            assertEquals(Boolean.TRUE, startupLocked.get(), "startup must use the Computer monitor");
            assertEquals(Boolean.FALSE, luaLocked.get(), "Lua must not block the watchdog's Computer monitor");
            // This is the same entry point the watchdog calls. It must finish even while Lua is running.
            runAsync(() -> computer.abort(false)).get(5, TimeUnit.SECONDS);
        } finally {
            releaseLua.countDown();
        }
        drain();
        assertTrue(computer.isOn());
    }

    @Test
    void shutdownLocksComputerAndClearsMachineAndOutputs() throws Exception {
        ILuaMachine machine = runningMachine();
        AtomicReference<Boolean> shutdownLocked = new AtomicReference<>();
        AtomicReference<Boolean> unloadLocked = new AtomicReference<>();
        doAnswer(invocation -> {
            shutdownLocked.set(Thread.holdsLock(computer));
            return null;
        }).when(api)
            .shutdown();
        doAnswer(invocation -> {
            unloadLocked.set(Thread.holdsLock(computer));
            return null;
        }).when(machine)
            .unload();
        computer.getAPIEnvironment()
            .setOutput(0, 15);
        computer.getAPIEnvironment()
            .setBundledOutput(0, 123);

        computer.unload();
        drain();

        assertEquals(Boolean.TRUE, shutdownLocked.get());
        assertEquals(Boolean.TRUE, unloadLocked.get());
        assertFalse(computer.isOn());
        assertNull(field("m_machine").get(computer));
        assertEquals(0, ((int[]) field("m_output").get(computer))[0]);
        assertEquals(0, ((int[]) field("m_bundledOutput").get(computer))[0]);
        verify(api).shutdown();
        verify(machine).unload();
    }

    @Test
    void advanceWaitsForShutdownLifecycleSection() throws Exception {
        runningMachine();
        CountDownLatch shuttingDown = new CountDownLatch(1);
        CountDownLatch release = gate();
        doAnswer(invocation -> {
            shuttingDown.countDown();
            await(release);
            return null;
        }).when(api)
            .shutdown();
        computer.shutdown();
        await(shuttingDown);
        AtomicReference<Thread> main = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        CompletableFuture<Void> advanced = runAsync(() -> {
            main.set(Thread.currentThread());
            started.countDown();
            computer.advance(0.05);
        });
        try {
            await(started);
            assertBlockedOn(main.get(), computer);
        } finally {
            release.countDown();
        }
        advanced.get(5, TimeUnit.SECONDS);
        drain();
        verify(api, never()).advance(anyDouble());
        assertFalse(computer.isOn());
    }

    @Test
    void rebootStillSchedulesStartupOnNextAdvance() throws Exception {
        ILuaMachine oldMachine = runningMachine();
        computer.reboot();
        drain();
        assertFalse(computer.isOn());
        verify(oldMachine).unload();
        verify(api).shutdown();

        computer.advance(0.05);
        drain();
        assertTrue(computer.isOn());
        verify(api).startup();
        verify(api).callMethod(any(), eq(0), any());
        assertNotSame(oldMachine, field("m_machine").get(computer));
    }

    @Test
    void queuedEventRechecksStateUnderComputerMonitorAndDropsAfterShutdown() throws Exception {
        ILuaMachine machine = runningMachine();
        synchronized (computer) {
            computer.queueEvent("stale", new Object[] { 42 });
            // Lanes preserve ordering, not worker affinity. Observe the event task itself,
            // rather than assuming it runs on the worker used by a preceding task.
            assertComputerTaskBlockedOn(computer);
            computer.shutdown();
        }
        drain();
        verify(machine, never()).handleEvent(any(), any());
        verify(machine).unload();
        assertFalse(computer.isOn());
    }

    @Test
    void eventResumeLeavesComputerMonitorAvailableForAbortAndAdvance() throws Exception {
        ILuaMachine machine = runningMachine();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = gate();
        AtomicReference<Boolean> locked = new AtomicReference<>();
        doAnswer(invocation -> {
            locked.set(Thread.holdsLock(computer));
            entered.countDown();
            await(release);
            return null;
        }).when(machine)
            .handleEvent(eq("event"), any());

        computer.queueEvent("event", new Object[] { 42 });
        await(entered);
        try {
            assertEquals(Boolean.FALSE, locked.get());
            runAsync(() -> {
                computer.advance(0.05);
                computer.abort(false);
                computer.abort(true);
            }).get(5, TimeUnit.SECONDS);
            verify(api).advance(0.05);
            verify(machine).softAbort("Too long without yielding");
            verify(machine).hardAbort("Too long without yielding");
        } finally {
            release.countDown();
        }
        drain();
        verify(machine).handleEvent(eq("event"), aryEq(new Object[] { 42 }));
    }

    @Test
    void outputAndBlinkGettersAcquireComputerBeforeChildMonitor() throws Exception {
        runningMachine();
        computer.getAPIEnvironment()
            .setOutput(0, 15);
        computer.getAPIEnvironment()
            .setBundledOutput(0, 123);
        assertGetterLockOrder("m_output", () -> assertEquals(15, computer.getRedstoneOutput(0)));
        assertGetterLockOrder("m_output", () -> assertEquals(123, computer.getBundledRedstoneOutput(0)));
        assertGetterLockOrder("m_terminal", () -> assertFalse(computer.isBlinking()));
        computer.shutdown();
        drain();
        assertEquals(0, computer.getRedstoneOutput(0));
        assertEquals(0, computer.getBundledRedstoneOutput(0));
        assertFalse(computer.isBlinking());
    }

    private void assertGetterLockOrder(String childField, Runnable getter) throws Exception {
        Object child = field(childField).get(computer);
        AtomicReference<Thread> reader = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        CompletableFuture<Void> completed;
        synchronized (child) {
            completed = runAsync(() -> {
                reader.set(Thread.currentThread());
                started.countDown();
                getter.run();
            });
            await(started);
            assertBlockedOn(reader.get(), child);
            ThreadInfo info = ManagementFactory.getThreadMXBean()
                .getThreadInfo(
                    new long[] { reader.get()
                        .getId() },
                    true,
                    true)[0];
            assertTrue(
                java.util.Arrays.stream(info.getLockedMonitors())
                    .anyMatch(lock -> lock.getIdentityHashCode() == System.identityHashCode(computer)),
                "getter must acquire Computer before terminal/output, like lifecycle tasks");
        }
        completed.get(5, TimeUnit.SECONDS);
    }

    private ILuaMachine runningMachine() throws Exception {
        ILuaMachine machine = mock(ILuaMachine.class);
        synchronized (computer) {
            field("m_machine").set(computer, machine);
            Field state = field("m_state");
            for (Object value : state.getType()
                .getEnumConstants()) {
                if (value.toString()
                    .equals("Running")) state.set(computer, value);
            }
        }
        return machine;
    }

    private static Field field(String name) throws Exception {
        Field field = Computer.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private CountDownLatch gate() {
        CountDownLatch gate = new CountDownLatch(1);
        gates.add(gate);
        return gate;
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(5, TimeUnit.SECONDS), "timed out waiting for worker");
    }

    private static void assertBlockedOn(Thread thread, Object monitor) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            ThreadInfo info = ManagementFactory.getThreadMXBean()
                .getThreadInfo(thread.getId());
            if (info != null && info.getThreadState() == Thread.State.BLOCKED
                && info.getLockInfo() != null
                && info.getLockInfo()
                    .getIdentityHashCode() == System.identityHashCode(monitor))
                return;
            Thread.sleep(1);
        }
        fail("thread did not block on the expected monitor");
    }

    private static void assertComputerTaskBlockedOn(Computer computer) throws InterruptedException {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            for (ThreadInfo info : threads.getThreadInfo(threads.getAllThreadIds(), 32)) {
                if (info == null || info.getThreadState() != Thread.State.BLOCKED
                    || info.getLockInfo() == null
                    || info.getLockInfo()
                        .getIdentityHashCode() != System.identityHashCode(computer)
                    || info.getLockOwnerId() != Thread.currentThread()
                        .getId())
                    continue;
                for (StackTraceElement frame : info.getStackTrace()) {
                    // Exclude the watchdog: abort() can also block on Computer, but is not
                    // evidence that the queued event checked lifecycle state under its lock.
                    if (frame.getClassName()
                        .startsWith(Computer.class.getName() + "$")
                        && frame.getMethodName()
                            .equals("execute"))
                        return;
                }
            }
            Thread.sleep(1);
        }
        fail("computer task did not block on the expected monitor");
    }

    // Daemon helpers cannot pin the test JVM if a lock-order regression deadlocks them.
    private static CompletableFuture<Void> runAsync(Runnable action) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                action.run();
                future.complete(null);
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        }, "computer-lock-test");
        thread.setDaemon(true);
        thread.start();
        return future;
    }

    private void queue(ThrowingRunnable action) {
        assertTrue(ComputerThread.queueTask(new ITask() {

            @Override
            public Computer getOwner() {
                return computer;
            }

            @Override
            public void execute() {
                try {
                    action.run();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            }
        }, computer));
    }

    private void drain() throws Exception {
        CountDownLatch drained = new CountDownLatch(1);
        queue(drained::countDown);
        await(drained);
    }

    private interface ThrowingRunnable {

        void run() throws Exception;
    }
}
