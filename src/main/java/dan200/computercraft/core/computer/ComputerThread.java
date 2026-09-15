package dan200.computercraft.core.computer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.WeakHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import dan200.computercraft.ComputerCraft;

public class ComputerThread {

    private static Object m_lock = new Object();
    private static Thread m_thread = null;
    private static WeakHashMap<Object, LinkedBlockingQueue<ITask>> m_computerTasks = new WeakHashMap<>();
    private static ArrayList<LinkedBlockingQueue<ITask>> m_computerTasksActive = new ArrayList<>();
    private static ArrayList<LinkedBlockingQueue<ITask>> m_computerTasksPending = new ArrayList<>();
    private static Object m_defaultQueue = new Object();
    private static Object m_monitor = new Object();
    private static boolean m_busy = false;
    private static boolean m_running = false;
    private static boolean m_stopped = false;

    // Queue-overflow drop accounting (finding C2); all accesses happen inside
    // synchronized (m_computerTasksPending).
    private static long m_lastDropLogTime = 0L;
    private static long m_droppedTasks = 0L;

    public static void start() {
        synchronized (m_lock) {
            if (m_running) {
                m_stopped = false;
            } else {
                // A brand-new thread must not inherit a previous stop request,
                // otherwise a restart after stop() (e.g. reloading a world in
                // the same JVM) would spawn a thread that exits on the first
                // queued task (finding C4 in docs/CODEBASE_ANALYSIS.md).
                m_stopped = false;
                m_thread = new Thread(new Runnable() {

                    @Override
                    public void run() {
                        while (true) {
                            synchronized (ComputerThread.m_computerTasksPending) {
                                if (!ComputerThread.m_computerTasksPending.isEmpty()) {
                                    for (Iterator<LinkedBlockingQueue<ITask>> it = ComputerThread.m_computerTasksPending
                                        .iterator(); it.hasNext(); it.remove()) {
                                        LinkedBlockingQueue<ITask> queue = it.next();
                                        if (!ComputerThread.m_computerTasksActive.contains(queue)) {
                                            ComputerThread.m_computerTasksActive.add(queue);
                                        }
                                    }
                                }
                            }

                            Iterator<LinkedBlockingQueue<ITask>> itx = ComputerThread.m_computerTasksActive.iterator();

                            while (itx.hasNext()) {
                                LinkedBlockingQueue<ITask> queue = itx.next();
                                if (queue != null && !queue.isEmpty()) {
                                    synchronized (ComputerThread.m_lock) {
                                        if (ComputerThread.m_stopped) {
                                            ComputerThread.m_running = false;
                                            ComputerThread.m_thread = null;
                                            return;
                                        }
                                    }

                                    try {
                                        final ITask task = queue.take();
                                        ComputerThread.m_busy = true;
                                        Thread worker = new Thread(new Runnable() {

                                            @Override
                                            public void run() {
                                                try {
                                                    task.execute();
                                                } catch (Throwable var2) {
                                                    System.out.println("ComputerCraft: Error running task.");
                                                    var2.printStackTrace();
                                                }
                                            }
                                        });
                                        worker.start();
                                        worker.join(ComputerCraft.computerThreadTimeout);
                                        if (worker.isAlive()) {
                                            Computer computer = task.getOwner();
                                            if (computer != null) {
                                                computer.abort(false);
                                                worker.join(1500L);
                                                if (worker.isAlive()) {
                                                    computer.abort(true);
                                                    worker.join(1500L);
                                                }
                                            }

                                            if (worker.isAlive()) {
                                                worker.interrupt();
                                            }
                                        }
                                    } catch (InterruptedException var19) {
                                        continue;
                                    } finally {
                                        ComputerThread.m_busy = false;
                                    }

                                    synchronized (queue) {
                                        if (queue.isEmpty()) {
                                            itx.remove();
                                        }
                                    }
                                }
                            }

                            while (ComputerThread.m_computerTasksActive.isEmpty()
                                && ComputerThread.m_computerTasksPending.isEmpty()) {
                                synchronized (ComputerThread.m_monitor) {
                                    try {
                                        ComputerThread.m_monitor.wait();
                                    } catch (InterruptedException var16) {}
                                }
                            }
                        }
                    }
                }, "Computer Dispatch Thread");
                m_thread.start();
                m_running = true;
            }
        }
    }

    public static void stop() {
        synchronized (m_lock) {
            if (m_running) {
                m_stopped = true;
                m_thread.interrupt();
            }
        }
    }

    /**
     * Queues a task for execution by the dispatch thread.
     *
     * @param _task    the task to run
     * @param computer the owning computer, used to key the per-computer task
     *                 queue ({@code null} for the shared default queue)
     * @return {@code true} if the task was queued, {@code false} if it was
     *         dropped because the queue was full (the owning computer is
     *         wedged or flooding events; the drop is logged)
     */
    public static boolean queueTask(ITask _task, Computer computer) {
        Object queueObject = computer;
        if (computer == null) {
            queueObject = m_defaultQueue;
        }

        LinkedBlockingQueue<ITask> queue;
        // m_computerTasks is a WeakHashMap, which is not thread-safe, while
        // queueTask is called from many threads (server thread, HTTP worker
        // threads, websocket threads, CNPC event dispatch). The get/put pair
        // must be atomic under m_lock so concurrent producers cannot corrupt
        // the map's internal state (finding C1 in docs/CODEBASE_ANALYSIS.md).
        // The weak keys are load-bearing — they let an unloaded computer's
        // queue be collected — so a ConcurrentHashMap is not a drop-in
        // replacement here.
        synchronized (m_lock) {
            queue = m_computerTasks.get(queueObject);
            if (queue == null) {
                m_computerTasks.put(queueObject, queue = new LinkedBlockingQueue<>(256));
            }
        }

        boolean queued;
        synchronized (m_computerTasksPending) {
            queued = queue.offer(_task);
            if (queued) {
                if (!m_computerTasksPending.contains(queue)) {
                    m_computerTasksPending.add(queue);
                }
            } else {
                // The queue is full: the owning computer is wedged or
                // flooding events. Blocking the producer here (put()) could
                // stall the server main thread for as long as the dispatcher
                // needs to drain the queue (up to computerThreadTimeout per
                // wedged task), so the task is dropped — but loudly and
                // throttled, rather than silently (finding C2 in
                // docs/CODEBASE_ANALYSIS.md). A full queue is always already
                // tracked in the pending/active lists, so it needs no
                // re-registration here.
                m_droppedTasks++;
                long now = System.currentTimeMillis();
                if (now - m_lastDropLogTime >= 5000L) {
                    m_lastDropLogTime = now;
                    ComputerCraft.logger.warn(
                        "ComputerCraft: task queue overflow — dropped {} task(s) in the last 5s (capacity 256);"
                            + " a computer may be wedged or flooding events",
                        m_droppedTasks);
                    m_droppedTasks = 0;
                } else {
                    ComputerCraft.logger.debug(
                        "ComputerCraft: task queue overflow, dropping task for computer {}",
                        computer != null ? computer.getID() : "default");
                }
            }
        }

        if (queued) {
            synchronized (m_monitor) {
                m_monitor.notify();
            }
        }

        return queued;
    }
}
