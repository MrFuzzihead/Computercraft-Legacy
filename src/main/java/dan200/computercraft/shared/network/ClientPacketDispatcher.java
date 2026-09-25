package dan200.computercraft.shared.network;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;

import dan200.computercraft.ComputerCraft;

/** Serializes client mutations and invalidates queued work when the connection changes. */
public final class ClientPacketDispatcher {

    private final Consumer<Runnable> m_schedule;
    private final Runnable m_reset;
    private final Consumer<ComputerCraftPacket> m_handle;
    private final Queue<Runnable> m_pending = new ArrayDeque<>();
    private Object m_connection;
    private boolean m_scheduled;

    public ClientPacketDispatcher(Consumer<Runnable> schedule, Runnable reset, Consumer<ComputerCraftPacket> handle) {
        m_schedule = schedule;
        m_reset = reset;
        m_handle = handle;
    }

    public void connected(Object connection) {
        synchronized (this) {
            m_connection = connection;
            m_pending.clear();
            m_pending.add(m_reset);
        }
        schedule();
    }

    public void disconnected(Object connection) {
        synchronized (this) {
            if (connection != m_connection) return;
            m_connection = null;
            m_pending.clear();
            m_pending.add(m_reset);
        }
        schedule();
    }

    public void receive(Object connection, ComputerCraftPacket packet) {
        synchronized (this) {
            if (connection == null || connection != m_connection) return;
            m_pending.add(() -> m_handle.accept(packet));
        }
        schedule();
    }

    private void schedule() {
        synchronized (this) {
            if (m_scheduled) return;
            m_scheduled = true;
        }
        // Never acquire Minecraft's scheduling-queue lock while holding our monitor.
        m_schedule.accept(this::drain);
    }

    private synchronized void drain() {
        try {
            Runnable action;
            while ((action = m_pending.poll()) != null) {
                try {
                    action.run();
                } catch (Exception e) {
                    ComputerCraft.logger
                        .error("ComputerCraft: failed to handle client-bound packet or connection reset", e);
                }
            }
        } finally {
            m_scheduled = false;
        }
    }
}
