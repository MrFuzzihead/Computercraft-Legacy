package dan200.computercraft.core.computer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import dan200.computercraft.ComputerCraft;

/**
 * Executes computer tasks on a small pool of worker threads (finding P1 in
 * {@code docs/CODEBASE_ANALYSIS.md}).
 *
 * <p>
 * Tasks are queued per computer ("lanes") and executed strictly serially
 * within a lane — the Lua machine state of a computer is only ever touched by
 * one thread at a time. Different computers, however, run concurrently on the
 * pool, so one busy or wedged computer no longer delays every other computer
 * on the server, and high event rates no longer pay a thread-creation cost per
 * task.
 * </p>
 *
 * <p>
 * Timeout enforcement (the old dispatch thread's {@code join(timeout)} plus
 * abort ladder) is done by a dedicated watchdog thread: a task that runs
 * longer than {@code computerThreadTimeout} is soft-aborted, hard-aborted 1.5s
 * later, and its runner interrupted after another 1.5s — the same escalation
 * as before, but applied per lane without blocking the pool.
 * </p>
 *
 * <p>
 * Worker and watchdog threads are daemons: no production shutdown path calls
 * {@link #stop()} (nothing in the mod does), and the previous single
 * non-daemon dispatch thread could only ever be reaped by the JVM exiting.
 * </p>
 */
public class ComputerThread {

    private static final int QUEUE_CAPACITY = 256;
    private static final long WATCHDOG_INTERVAL_MS = 50;
    private static final long ABORT_ESCALATION_DELAY_MS = 1500;

    // Guards the WeakHashMap (finding C1). Never held nested with m_ready.
    private static final Object m_lock = new Object();
    private static final WeakHashMap<Object, TaskQueue> m_computerQueues = new WeakHashMap<>();
    private static final Object m_defaultQueue = new Object();

    // Guards all per-lane scheduling state, and is also the workers' wait
    // monitor.
    private static final Object m_ready = new Object();
    private static final ArrayDeque<TaskQueue> m_readyQueues = new ArrayDeque<>();
    private static final ArrayList<TaskQueue> m_busyQueues = new ArrayList<>();

    private static volatile Object m_pool = null; // pool generation token
    private static volatile boolean m_stopped = false;
    private static boolean m_running = false; // guarded by m_lock

    // Queue-overflow drop accounting (finding C2); guarded by m_ready.
    private static long m_lastDropLogTime = 0L;
    private static long m_droppedTasks = 0L;

    private ComputerThread() {}

    public static void start() {
        synchronized (m_lock) {
            if (m_running) {
                m_stopped = false;
                return;
            }

            // A brand-new pool must not inherit a previous stop request, and a
            // fresh generation token makes any straggler from an old pool exit
            // at its next check (finding C4 in docs/CODEBASE_ANALYSIS.md).
            m_stopped = false;
            Object pool = new Object();
            m_pool = pool;

            int threads = Math.max(1, ComputerCraft.computerThreads);
            for (int i = 0; i < threads; i++) {
                Thread worker = new Thread(() -> workerLoop(pool), "Computer Worker-" + (i + 1));
                worker.setDaemon(true);
                worker.start();
            }

            Thread watchdog = new Thread(() -> watchdogLoop(pool), "Computer Watchdog");
            watchdog.setDaemon(true);
            watchdog.start();

            m_running = true;
        }
    }

    public static void stop() {
        synchronized (m_lock) {
            if (!m_running) {
                return;
            }

            m_running = false;
            m_stopped = true;
        }

        // Wake any workers parked on the ready queue: the idle path must
        // observe the stop request (finding C4).
        synchronized (m_ready) {
            m_ready.notifyAll();
        }
    }

    /**
     * Queues a task for execution by the worker pool.
     *
     * @param _task    the task to run
     * @param computer the owning computer, used to key the per-computer task
     *                 lane ({@code null} for the shared default lane)
     * @return {@code true} if the task was queued, {@code false} if it was
     *         dropped because the lane's queue was full (the computer is
     *         wedged or flooding events; the drop is logged)
     */
    public static boolean queueTask(ITask _task, Computer computer) {
        Object queueObject = computer;
        if (computer == null) {
            queueObject = m_defaultQueue;
        }

        TaskQueue lane;
        // m_computerQueues is a WeakHashMap, which is not thread-safe, while
        // queueTask is called from many threads (server thread, HTTP worker
        // threads, websocket threads, CNPC event dispatch). The get/put pair
        // must be atomic under m_lock so concurrent producers cannot corrupt
        // the map's internal state (finding C1). The weak keys are
        // load-bearing — they let an unloaded computer's lane be collected —
        // so a ConcurrentHashMap is not a drop-in replacement here.
        synchronized (m_lock) {
            lane = m_computerQueues.get(queueObject);
            if (lane == null) {
                m_computerQueues.put(queueObject, lane = new TaskQueue());
            }
        }

        boolean queued;
        synchronized (m_ready) {
            queued = lane.m_tasks.offer(_task);
            if (queued) {
                if (!lane.m_inReady && !lane.m_servicing) {
                    lane.m_inReady = true;
                    m_readyQueues.add(lane);
                }

                m_ready.notifyAll();
            } else {
                // The lane's queue is full: the computer is wedged or
                // flooding events. Blocking the producer here (put()) could
                // stall the server main thread for as long as the pool takes
                // to drain the queue (up to computerThreadTimeout per wedged
                // task), so the task is dropped — but loudly and throttled,
                // rather than silently (finding C2). A full lane is always
                // already tracked as busy or ready, so it needs no
                // re-registration here.
                m_droppedTasks++;
                long now = System.currentTimeMillis();
                if (now - m_lastDropLogTime >= 5000L) {
                    m_lastDropLogTime = now;
                    ComputerCraft.logger.warn(
                        "ComputerCraft: task queue overflow — dropped {} task(s) in the last 5s (capacity {});"
                            + " a computer may be wedged or flooding events",
                        m_droppedTasks,
                        QUEUE_CAPACITY);
                    m_droppedTasks = 0;
                } else {
                    ComputerCraft.logger.debug(
                        "ComputerCraft: task queue overflow, dropping task for computer {}",
                        computer != null ? computer.getID() : "default");
                }
            }
        }

        return queued;
    }

    private static void workerLoop(Object pool) {
        while (true) {
            TaskQueue lane;
            synchronized (m_ready) {
                while (true) {
                    if (m_stopped || m_pool != pool) {
                        return;
                    }

                    if (!m_readyQueues.isEmpty()) {
                        // Taking the lane out of the ready queue and marking
                        // it as serviced happens atomically, so two workers
                        // can never run the same computer's tasks at once.
                        lane = m_readyQueues.removeFirst();
                        lane.m_inReady = false;
                        lane.m_servicing = true;
                        m_busyQueues.add(lane);
                        break;
                    }

                    try {
                        m_ready.wait();
                    } catch (InterruptedException e) {
                        // Re-check the exit conditions above.
                    }
                }
            }

            runNextTask(lane);
        }
    }

    private static void runNextTask(TaskQueue lane) {
        final ITask task = lane.m_tasks.poll();
        synchronized (m_ready) {
            if (task == null) {
                // Defensive: the lane became empty between pickup and poll.
                lane.m_servicing = false;
                m_busyQueues.remove(lane);
                m_ready.notifyAll();
                return;
            }

            // Start of a fresh task run: a stale interrupt from a previous
            // escalation must not leak into it, and the watchdog bookkeeping
            // must be reset.
            Thread.interrupted();
            lane.m_current = task;
            lane.m_runner = Thread.currentThread();
            lane.m_startNanos = System.nanoTime();
            lane.m_softAborted = false;
            lane.m_hardAborted = false;
        }

        try {
            task.execute();
        } catch (Throwable t) {
            ComputerCraft.logger.error("ComputerCraft: Error running task", t);
        } finally {
            synchronized (m_ready) {
                lane.m_current = null;
                lane.m_runner = null;
                lane.m_servicing = false;
                m_busyQueues.remove(lane);
                if (!m_stopped && !lane.m_tasks.isEmpty() && !lane.m_inReady) {
                    lane.m_inReady = true;
                    m_readyQueues.add(lane);
                }

                m_ready.notifyAll();
            }
        }
    }

    private static void watchdogLoop(Object pool) {
        while (true) {
            try {
                Thread.sleep(WATCHDOG_INTERVAL_MS);
            } catch (InterruptedException e) {
                // Exit conditions checked below.
            }

            if (m_stopped || m_pool != pool) {
                return;
            }

            long timeoutMs = ComputerCraft.computerThreadTimeout;
            if (timeoutMs <= 0) {
                // Matches the old semantics of join(0) blocking forever.
                continue;
            }

            List<TaskQueue> busy;
            synchronized (m_ready) {
                busy = new ArrayList<>(m_busyQueues);
            }

            long now = System.nanoTime();
            for (TaskQueue lane : busy) {
                escalateIfNeeded(lane, now, timeoutMs);
            }
        }
    }

    /**
     * Applies the abort escalation ladder to a single busy lane: soft abort at
     * {@code timeoutMs}, hard abort {@value #ABORT_ESCALATION_DELAY_MS}ms
     * later, and a runner interrupt after another
     * {@value #ABORT_ESCALATION_DELAY_MS}ms — the same terminal sequence the
     * old dispatch thread applied with its {@code join} ladder. All decisions
     * are made from the lane's live state under the lock, so a task that
     * finished (or a lane that moved on to its next task) is never acted on
     * with stale timings.
     */
    private static void escalateIfNeeded(TaskQueue lane, long now, long timeoutMs) {
        ITask task;
        boolean soft = false;
        boolean hard = false;
        boolean interrupt = false;
        synchronized (m_ready) {
            if (lane.m_current == null || lane.m_runner == null) {
                return;
            }

            task = lane.m_current;
            long elapsedMs = (now - lane.m_startNanos) / 1_000_000L;
            if (elapsedMs < timeoutMs) {
                return;
            }

            if (!lane.m_softAborted) {
                lane.m_softAborted = true;
                soft = true;
            } else if (!lane.m_hardAborted && elapsedMs >= timeoutMs + ABORT_ESCALATION_DELAY_MS) {
                lane.m_hardAborted = true;
                hard = true;
            } else if (lane.m_hardAborted && elapsedMs >= timeoutMs + 2 * ABORT_ESCALATION_DELAY_MS) {
                interrupt = true;
            }

            if (interrupt) {
                // Interrupting is quick and must not race a task switch, so it
                // happens while the lane state is still verified.
                lane.m_runner.interrupt();
            }
        }

        if (soft || hard) {
            // Deliberately outside the lock: abort() synchronizes on the
            // computer and its Lua machine. The old dispatch thread called it
            // from its own thread as well.
            Computer owner = task.getOwner();
            if (owner != null) {
                owner.abort(hard);
            }
        }
    }

    /**
     * A per-computer task lane. All mutable scheduling state is guarded by
     * {@code ComputerThread.m_ready}.
     */
    private static final class TaskQueue {

        private final LinkedBlockingQueue<ITask> m_tasks = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

        private boolean m_inReady; // present in m_readyQueues
        private boolean m_servicing; // a worker is executing this lane's head task
        private ITask m_current; // task being executed, for the watchdog
        private Thread m_runner; // worker thread executing m_current
        private long m_startNanos; // when m_current started
        private boolean m_softAborted;
        private boolean m_hardAborted;
    }
}
