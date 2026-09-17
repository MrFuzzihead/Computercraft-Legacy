package dan200.computercraft.shared.network;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.NBTTagCompound;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.shared.common.ClientTerminal;
import dan200.computercraft.shared.computer.core.ClientComputer;
import dan200.computercraft.shared.computer.core.ClientComputerRegistry;
import dan200.computercraft.shared.proxy.ComputerCraftProxyCommon;

/**
 * C6 regression tests for the client-packet dispatcher: valid client-bound
 * packets must be applied on the client thread in FIFO order, stale packets
 * from an older connection must be dropped, and a handler failure must not
 * break the queue. The test drives a real {@link ClientTerminal} update
 * path; only {@link ClientComputer#requestState()} is stubbed because the
 * FML network channel cannot initialize outside the game.
 */
class ClientPacketDispatcherTest {

    private final Queue<Runnable> scheduled = new ConcurrentLinkedQueue<>();
    private final Object connection = new Object();
    private final Thread clientThread = Thread.currentThread();
    private final ClientComputerRegistry savedRegistry = ComputerCraft.clientComputerRegistry;
    private final ClientComputerRegistry registry = new ClientComputerRegistry() {
        @Override
        public void add(int instanceID, ClientComputer computer) {
            ClientComputer registered = spy(computer);
            doNothing().when(registered).requestState();
            super.add(instanceID, registered);
        }
    };

    @BeforeEach
    void setUp() {
        ComputerCraft.clientComputerRegistry = registry;
    }

    @AfterEach
    void tearDown() {
        ComputerCraft.clientComputerRegistry = savedRegistry;
    }

    private void drain() {
        Runnable action;
        while ((action = scheduled.poll()) != null) action.run();
    }

    private static ComputerCraftPacket description(int width, String text) {
        ComputerCraftPacket packet = new ComputerCraftPacket();
        packet.m_packetType = 7;
        packet.m_dataInt = new int[] { 42 };
        packet.m_dataNBT = new NBTTagCompound();
        packet.m_dataNBT.setBoolean("colour", true);
        Terminal terminal = new Terminal(width, 2);
        terminal.write(text);
        NBTTagCompound nbt = new NBTTagCompound();
        // Mirror ServerTerminal.writeDescription: term_width/term_height wrap the
        // terminal's own writeToNBT payload; writeToNBT alone does not set them.
        nbt.setInteger("term_width", width);
        nbt.setInteger("term_height", 2);
        terminal.writeToNBT(nbt);
        packet.m_dataNBT.setTag("terminal", nbt);
        return packet;
    }

    private static ComputerCraftPacket deletePacket() {
        ComputerCraftPacket packet = new ComputerCraftPacket();
        packet.m_packetType = 8;
        packet.m_dataInt = new int[] { 42 };
        return packet;
    }

    @Test
    void workerOnlyEnqueuesAndClientThreadAppliesRealTerminalUpdatesInOrder() throws Exception {
        List<Integer> widths = new ArrayList<>();
        ComputerCraftProxyCommon proxy = mock(ComputerCraftProxyCommon.class, CALLS_REAL_METHODS);
        ClientPacketDispatcher dispatcher = new ClientPacketDispatcher(scheduled::add, () -> {
            assertSame(clientThread, Thread.currentThread(), "resets must apply on the client thread");
            registry.reset();
        }, packet -> {
            assertSame(clientThread, Thread.currentThread(), "packets must apply on the client thread");
            proxy.handlePacket(packet, null);
            ClientComputer computer = registry.get(42);
            Terminal terminal = computer == null ? null : computer.getTerminal();
            widths.add(terminal == null ? 0 : terminal.getWidth());
        });

        // Queue valid updates from a netty-like worker. Nothing may touch the
        // registry before the designated client thread drains the scheduler.
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(() -> {
                dispatcher.connected(connection);
                dispatcher.receive(connection, description(4, "old"));
                dispatcher.receive(connection, description(8, "new"));
                dispatcher.receive(connection, deletePacket());
                dispatcher.receive(connection, description(3, "abc"));
                dispatcher.receive(connection, description(6, "netty"));
            }).get(5, TimeUnit.SECONDS);
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }

        assertEquals(1, scheduled.size(), "one scheduled drain preserves FIFO");
        assertFalse(registry.contains(42), "nothing may apply before the client thread drains");
        drain();
        assertEquals(java.util.Arrays.asList(4, 8, 0, 3, 6), widths);
        assertEquals("netty ", registry.get(42).getTerminal().getLine(0).toString());
        dispatcher.receive(connection, deletePacket());
        drain();
        assertFalse(registry.contains(42), "the queued delete must remove the computer");
    }

    @Test
    void disconnectAndReconnectDiscardQueuedAndLateOldPacketsAndResetFirst() {
        List<String> actions = new ArrayList<>();
        ClientPacketDispatcher dispatcher = new ClientPacketDispatcher(scheduled::add,
            () -> actions.add("reset"), packet -> actions.add("packet"));
        dispatcher.connected(connection);
        drain();
        actions.clear();
        dispatcher.receive(connection, description(1, ""));
        dispatcher.disconnected(connection);
        Object replacement = new Object();
        dispatcher.connected(replacement);
        dispatcher.receive(connection, description(1, ""));
        dispatcher.disconnected(connection); // A late close must not invalidate the new connection.
        dispatcher.receive(replacement, description(1, ""));
        drain();
        assertEquals(java.util.Arrays.asList("reset", "packet"), actions);
        actions.clear();
        dispatcher.disconnected(replacement);
        dispatcher.receive(replacement, description(1, ""));
        drain();
        assertEquals(java.util.Arrays.asList("reset"), actions);
    }

    @Test
    void handlerExceptionDoesNotPreventSubsequentPacketsOrFutureDrains() {
        List<Integer> handled = new ArrayList<>();
        ClientPacketDispatcher dispatcher = new ClientPacketDispatcher(scheduled::add, () -> {}, packet -> {
            if (packet.m_packetType == 99) throw new IllegalStateException("test handler failure");
            handled.add((int) packet.m_packetType);
        });
        dispatcher.connected(connection);
        ComputerCraftPacket bad = new ComputerCraftPacket();
        bad.m_packetType = 99;
        dispatcher.receive(connection, bad);
        dispatcher.receive(connection, description(1, ""));
        assertDoesNotThrow(this::drain);
        dispatcher.receive(connection, description(1, ""));
        drain();
        assertEquals(java.util.Arrays.asList(7, 7), handled);
    }

    @Test
    void inlineSchedulerStillPreservesOrder() {
        List<String> actions = new ArrayList<>();
        ClientPacketDispatcher dispatcher = new ClientPacketDispatcher(Runnable::run,
            () -> actions.add("reset"), packet -> actions.add("packet"));
        dispatcher.connected(connection);
        dispatcher.receive(connection, description(1, ""));
        dispatcher.disconnected(connection);
        assertEquals(java.util.Arrays.asList("reset", "packet", "reset"), actions);
    }
}
