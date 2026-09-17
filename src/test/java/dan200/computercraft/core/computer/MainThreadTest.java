package dan200.computercraft.core.computer;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Queue-only tests: no Forge proxy or server lifecycle is required. */
class MainThreadTest {

    @BeforeEach
    @AfterEach
    void clearQueue() throws Exception {
        Field field = MainThread.class.getDeclaredField("m_outstandingTasks");
        field.setAccessible(true);
        LinkedList<?> queue = (LinkedList<?>) field.get(null);
        synchronized (queue) {
            queue.clear();
        }
    }

    private static ITask task(Runnable action) {
        return new ITask() {
            @Override
            public Computer getOwner() {
                return null;
            }

            @Override
            public void execute() {
                action.run();
            }
        };
    }

    @Test
    void tasksRunInFifoOrderOnTheDrainingThreadWithPerTickBudget() {
        List<Integer> executed = new ArrayList<>();
        Thread owner = Thread.currentThread();
        for (int i = 0; i < 1001; i++) {
            int index = i;
            assertTrue(MainThread.queueTask(task(() -> {
                assertSame(owner, Thread.currentThread());
                executed.add(index);
            })));
        }
        assertTrue(executed.isEmpty());
        MainThread.executePendingTasks();
        assertEquals(1000, executed.size());
        MainThread.executePendingTasks();
        MainThread.executePendingTasks();
        assertEquals(1001, executed.size());
        for (int i = 0; i < 1001; i++) assertEquals(i, executed.get(i).intValue());
    }

    @Test
    void capacityRejectsOverflowAndDrainingReleasesSlots() {
        AtomicInteger executions = new AtomicInteger();
        ITask task = task(executions::incrementAndGet);
        for (int i = 0; i < 50000; i++) assertTrue(MainThread.queueTask(task));
        assertFalse(MainThread.queueTask(task));
        MainThread.executePendingTasks();
        assertEquals(1000, executions.get());
        assertTrue(MainThread.queueTask(task));
        for (int i = 0; i < 50; i++) MainThread.executePendingTasks();
        assertEquals(50001, executions.get());
    }

    @Test
    void callbackCanEnqueueAnotherTask() {
        List<Integer> executed = new ArrayList<>();
        MainThread.queueTask(task(() -> {
            executed.add(1);
            assertTrue(MainThread.queueTask(task(() -> executed.add(2))));
        }));
        MainThread.executePendingTasks();
        assertEquals(java.util.Arrays.asList(1, 2), executed);
    }

    @Test
    void concurrentProducersReceiveUniqueIdsAndExecuteExactlyOnce() throws Exception {
        ExecutorService producers = Executors.newFixedThreadPool(4);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        AtomicInteger executions = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) futures.add(producers.submit(() -> {
                for (int j = 0; j < 250; j++) {
                    assertTrue(ids.add(MainThread.getUniqueTaskID()));
                    assertTrue(MainThread.queueTask(task(executions::incrementAndGet)));
                }
            }));
            for (Future<?> future : futures) future.get(5, TimeUnit.SECONDS);
            MainThread.executePendingTasks();
            MainThread.executePendingTasks();
            assertEquals(1000, ids.size());
            assertEquals(1000, executions.get());
        } finally {
            producers.shutdownNow();
            assertTrue(producers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
