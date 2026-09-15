package dan200.computercraft.core.computer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * Concurrency tests for {@link ComputerThread#queueTask} (finding C1 in
 * {@code docs/CODEBASE_ANALYSIS.md}).
 *
 * <p>
 * The task map used to be a {@code WeakHashMap} read and written without any
 * lock, while {@code queueTask} is called from many producer threads (server
 * thread, HTTP workers, websocket threads, CNPC event dispatch). These tests
 * hammer {@code queueTask} from many threads simultaneously and verify that
 * every task is still executed exactly once by the dispatch thread.
 * </p>
 *
 * <p>
 * All tasks are queued with {@code computer == null}, which routes them through
 * the shared default-queue key — the same map entry raced over by every
 * producer. The task count stays below the 256-capacity queue so the separate
 * silent-{@code offer()} drop issue (finding C2) cannot interfere with these
 * assertions.
 * </p>
 */
class ComputerThreadTest {

    private static final int PRODUCER_THREADS = 8;
    private static final int TASKS_PER_THREAD = 16; // 128 total, under the 256 queue cap

    @AfterAll
    static void stopComputerDispatchThread() {
        // The dispatch thread is non-daemon, and stop() cannot wake an idle
        // waiter (finding C4). Queue one last task *after* stopping: when the
        // thread picks the queue up it observes m_stopped and exits, letting
        // the test JVM terminate. (start() now clears m_stopped, so any stale
        // stop request left here is harmless to later test classes.)
        ComputerThread.stop();
        ComputerThread.queueTask(new ITask() {

            @Override
            public Computer getOwner() {
                return null;
            }

            @Override
            public void execute() {}
        }, null);
    }

    @Test
    void queueTaskIsSafeFromManyProducerThreads() throws Exception {
        ComputerThread.start();

        int totalTasks = PRODUCER_THREADS * TASKS_PER_THREAD;
        CountDownLatch queued = new CountDownLatch(totalTasks);
        CountDownLatch executed = new CountDownLatch(totalTasks);
        AtomicInteger executions = new AtomicInteger();

        List<Thread> producers = new ArrayList<>();
        for (int t = 0; t < PRODUCER_THREADS; t++) {
            producers.add(new Thread(() -> {
                for (int i = 0; i < TASKS_PER_THREAD; i++) {
                    // If the task map were corrupted by the concurrent
                    // get/put, queueTask would throw here, the producer would
                    // die, and `queued` would never reach zero.
                    ComputerThread.queueTask(new ITask() {

                        @Override
                        public Computer getOwner() {
                            return null;
                        }

                        @Override
                        public void execute() {
                            executions.incrementAndGet();
                            executed.countDown();
                        }
                    }, null);
                    queued.countDown();
                }
            }, "queue-task-producer-" + t));
        }

        for (Thread producer : producers) {
            producer.start();
        }

        assertTrue(queued.await(10, TimeUnit.SECONDS), "all producers must finish queueing without map corruption");
        assertTrue(executed.await(10, TimeUnit.SECONDS), "every queued task must be executed");
        for (Thread producer : producers) {
            producer.join(5000);
        }

        assertEquals(totalTasks, executions.get(), "every task must execute exactly once");
    }

    @Test
    void startAfterStopRunsNewlyQueuedTasks() throws Exception {
        // Regression test for the m_stopped-inheritance bug in start(): a
        // thread created after a previous stop() used to inherit
        // m_stopped == true and exit on the first queued task, killing all
        // computer execution after a world reload in the same JVM.
        ComputerThread.stop(); // ensure a stopped state exists first
        ComputerThread.start();

        CountDownLatch executed = new CountDownLatch(1);
        ComputerThread.queueTask(new ITask() {

            @Override
            public Computer getOwner() {
                return null;
            }

            @Override
            public void execute() {
                executed.countDown();
            }
        }, null);

        assertTrue(executed.await(10, TimeUnit.SECONDS), "tasks queued after a start() following stop() must execute");
    }

    @Test
    void queueTaskReportsDropsWhenTheQueueIsFull() throws Exception {
        // Regression test for C2: a full queue used to silently discard the
        // task. queueTask must now report the drop, and every *accepted* task
        // must still execute exactly once.
        ComputerThread.start();

        CountDownLatch dispatcherBlocked = new CountDownLatch(1);
        CountDownLatch releaseDispatcher = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();

        // Occupy the dispatcher's single lane until we release it, so the
        // queue fill below is deterministic (the dispatcher provably cannot
        // take anything from the queue while this task is running).
        assertTrue(ComputerThread.queueTask(new ITask() {

            @Override
            public Computer getOwner() {
                return null;
            }

            @Override
            public void execute() {
                dispatcherBlocked.countDown();
                try {
                    releaseDispatcher.await();
                } catch (InterruptedException e) {
                    Thread.currentThread()
                        .interrupt();
                }

                executions.incrementAndGet();
            }
        }, null), "the blocking task must be accepted into an empty queue");
        assertTrue(dispatcherBlocked.await(10, TimeUnit.SECONDS), "the dispatcher must pick up the blocking task");

        // Fill the queue (capacity 256; the running task is not in it) and
        // then overflow it by exactly one.
        int accepted = 0;
        boolean dropped = false;
        for (int i = 0; i < 300; i++) {
            boolean ok = ComputerThread.queueTask(new ITask() {

                @Override
                public Computer getOwner() {
                    return null;
                }

                @Override
                public void execute() {
                    executions.incrementAndGet();
                }
            }, null);

            if (ok) {
                accepted++;
            } else {
                dropped = true;
                break;
            }
        }

        assertTrue(dropped, "queueTask must report a drop once the queue is full");
        assertEquals(256, accepted, "exactly 256 tasks must be accepted into the full queue");
        // The queue stays full while the dispatcher is blocked, so further
        // drops are deterministic, not transient.
        assertFalse(ComputerThread.queueTask(new ITask() {

            @Override
            public Computer getOwner() {
                return null;
            }

            @Override
            public void execute() {
                executions.incrementAndGet();
            }
        }, null), "a task offered to a still-full queue must be dropped as well");

        // Release the dispatcher and verify every accepted task (plus the
        // blocking one) executes exactly once — the drop must not disturb the
        // rest of the pipeline.
        releaseDispatcher.countDown();
        long deadline = System.currentTimeMillis() + 15000;
        while (executions.get() < accepted + 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        assertEquals(accepted + 1, executions.get(), "the blocking task and all accepted tasks must execute");
    }
}
