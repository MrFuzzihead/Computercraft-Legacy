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

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.core.terminal.Terminal;

/**
 * Concurrency tests for {@link ComputerThread}.
 *
 * <p>
 * Covers the C1 finding (the task map used to be a {@code WeakHashMap} read and
 * written without any lock, while {@code queueTask} is called from many
 * producer threads), the C2 finding (a full queue used to silently discard
 * tasks) and the P1 finding (the old dispatch thread ran every computer
 * one-at-a-time via a fresh thread per task).
 * </p>
 *
 * <p>
 * Tasks queued with {@code computer == null} all share the default lane, which
 * is the map entry raced over by every producer in the C1 test; the C2 test
 * keeps its fill below the capacity of that lane.
 * </p>
 */
class ComputerThreadTest {

    private static final int PRODUCER_THREADS = 8;
    private static final int TASKS_PER_THREAD = 16; // 128 total, under the 256 queue cap

    @AfterAll
    static void stopComputerWorkers() {
        // The pool's worker/watchdog threads are daemons, so this is
        // belt-and-braces cleanup rather than a JVM-exit requirement.
        ComputerThread.stop();
    }

    /**
     * Builds a real (but never turned on) {@link Computer} to act as a lane
     * key for the P1 tests. The environment is inert.
     */
    private static Computer newComputer(int id) {
        return new Computer(new IComputerEnvironment() {

            @Override
            public int getDay() {
                return 1;
            }

            @Override
            public double getTimeOfDay() {
                return 0.0;
            }

            @Override
            public boolean isColour() {
                return true;
            }

            @Override
            public long getComputerSpaceLimit() {
                return 1024 * 1024;
            }

            @Override
            public int assignNewID() {
                return id;
            }

            @Override
            public IWritableMount createSaveDirMount(String path, long capacity) {
                return null;
            }

            @Override
            public IMount createResourceMount(String domain, String path) {
                return null;
            }
        }, new Terminal(51, 19), id);
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

        CountDownLatch workerBlocked = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();

        // Occupy a worker with this lane until we release it. The lane is
        // marked as serviced, so no other worker in the pool can pick it up
        // while this task runs, making the queue fill below deterministic
        // (nothing can drain the lane).
        assertTrue(ComputerThread.queueTask(new ITask() {

            @Override
            public Computer getOwner() {
                return null;
            }

            @Override
            public void execute() {
                workerBlocked.countDown();
                try {
                    releaseWorker.await();
                } catch (InterruptedException e) {
                    Thread.currentThread()
                        .interrupt();
                }

                executions.incrementAndGet();
            }
        }, null), "the blocking task must be accepted into an empty queue");
        assertTrue(workerBlocked.await(10, TimeUnit.SECONDS), "a worker must pick up the blocking task");

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
        // The lane stays full while its worker is blocked, so further drops
        // are deterministic, not transient.
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

        // Release the worker and verify every accepted task (plus the
        // blocking one) executes exactly once — the drop must not disturb the
        // rest of the pipeline.
        releaseWorker.countDown();
        long deadline = System.currentTimeMillis() + 15000;
        while (executions.get() < accepted + 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        assertEquals(accepted + 1, executions.get(), "the blocking task and all accepted tasks must execute");
    }

    // =========================================================================
    // P1: worker pool with per-computer lanes
    // =========================================================================

    @Test
    void tasksForDifferentComputersRunConcurrently() throws Exception {
        ComputerThread.start();

        int laneCount = 3; // <= the default pool size of 4
        Computer[] computers = new Computer[laneCount];
        CountDownLatch[] started = new CountDownLatch[laneCount];
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(laneCount);
        for (int i = 0; i < laneCount; i++) {
            computers[i] = newComputer(i);
            started[i] = new CountDownLatch(1);
        }

        for (int i = 0; i < laneCount; i++) {
            final int index = i;
            ComputerThread.queueTask(new ITask() {

                @Override
                public Computer getOwner() {
                    return computers[index];
                }

                @Override
                public void execute() {
                    started[index].countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread()
                            .interrupt();
                    } finally {
                        finished.countDown();
                    }
                }
            }, computers[index]);
        }

        // Under the old thread-per-task dispatcher (single lane) only the
        // first of these could ever start, so this awaiting all three proves
        // both that the pool runs lanes in parallel and that the computers are
        // keyed to distinct lanes.
        for (int i = 0; i < laneCount; i++) {
            assertTrue(started[i].await(10, TimeUnit.SECONDS), "task for computer " + i + " must run concurrently");
        }

        release.countDown();
        assertTrue(finished.await(10, TimeUnit.SECONDS), "all three tasks must finish after release");
    }

    @Test
    void tasksForTheSameComputerRunSerially() throws Exception {
        ComputerThread.start();

        Computer computer = newComputer(0);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger secondExecutions = new AtomicInteger();

        ComputerThread.queueTask(new ITask() {

            @Override
            public Computer getOwner() {
                return computer;
            }

            @Override
            public void execute() {
                firstStarted.countDown();
                try {
                    releaseFirst.await();
                } catch (InterruptedException e) {
                    Thread.currentThread()
                        .interrupt();
                }
            }
        }, computer);
        assertTrue(firstStarted.await(10, TimeUnit.SECONDS), "the first task must start");

        ComputerThread.queueTask(new ITask() {

            @Override
            public Computer getOwner() {
                return computer;
            }

            @Override
            public void execute() {
                secondExecutions.incrementAndGet();
                secondStarted.countDown();
            }
        }, computer);

        // While the first task is blocked, the second must not start even
        // though other workers in the pool are idle: a computer's Lua state
        // must only ever be touched by one thread at a time.
        Thread.sleep(300);
        assertEquals(0, secondExecutions.get(), "same-computer tasks must never run concurrently");

        releaseFirst.countDown();
        assertTrue(secondStarted.await(10, TimeUnit.SECONDS), "the second task must run after the first completes");
    }

    @Test
    void watchdogInterruptsARunawayTaskAfterTheEscalationLadder() throws Exception {
        // P1 replaced the dispatch thread's join(timeout) + abort ladder with a
        // watchdog thread. The terminal step of that ladder (interrupting the
        // runner) is observable, so it is asserted here: with a 200ms timeout
        // the interrupt must arrive ~3.2s after the task starts (timeout, then
        // two 1.5s escalation delays). Aborts are not observable with an off
        // computer, so this test targets the interrupt step.
        int savedTimeout = ComputerCraft.computerThreadTimeout;
        try {
            ComputerCraft.computerThreadTimeout = 200;
            ComputerThread.start();

            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch interrupted = new CountDownLatch(1);
            ComputerThread.queueTask(new ITask() {

                @Override
                public Computer getOwner() {
                    return null;
                }

                @Override
                public void execute() {
                    started.countDown();
                    try {
                        // Blocks far longer than the timeout unless interrupted.
                        Thread.sleep(60000);
                    } catch (InterruptedException e) {
                        interrupted.countDown();
                    }
                }
            }, null);

            assertTrue(started.await(10, TimeUnit.SECONDS), "the runaway task must start");
            assertTrue(
                interrupted.await(15, TimeUnit.SECONDS),
                "the watchdog must interrupt a task that exceeds the timeout escalation");
        } finally {
            ComputerCraft.computerThreadTimeout = savedTimeout;
        }
    }
}
