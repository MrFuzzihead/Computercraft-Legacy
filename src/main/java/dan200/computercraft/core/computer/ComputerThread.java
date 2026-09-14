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

    public static void queueTask(ITask _task, Computer computer) {
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

        synchronized (m_computerTasksPending) {
            queue.offer(_task);
            if (!m_computerTasksPending.contains(queue)) {
                m_computerTasksPending.add(queue);
            }
        }

        synchronized (m_monitor) {
            m_monitor.notify();
        }
    }
}
