package dan200.computercraft.shared.peripheral.chatbox;

import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.api.peripheral.IComputerAccess;

/**
 * Unit tests for {@link PortableChatBoxPeripheral} attach/detach self-registration
 * and IChatBoxReceiver event routing.
 *
 * <p>
 * Uses a minimal concrete subclass with a fixed, unknown position so that
 * no Minecraft classes need to be loaded.
 * </p>
 */
class PortableChatBoxPeripheralTest {

    // -------------------------------------------------------------------------
    // Minimal concrete subclass — position irrelevant for these tests
    // -------------------------------------------------------------------------

    private static class StubPortable extends PortableChatBoxPeripheral {

        @Override
        protected double getPositionX() {
            return 0;
        }

        @Override
        protected double getPositionY() {
            return 0;
        }

        @Override
        protected double getPositionZ() {
            return 0;
        }

        @Override
        protected int getDimensionId() {
            return Integer.MIN_VALUE; // unknown — range checks always fail
        }

        @Override
        public boolean equals(dan200.computercraft.api.peripheral.IPeripheral other) {
            return other instanceof StubPortable;
        }
    }

    // Track the stub so we can unregister in @AfterEach if a test leaves it registered.
    private final StubPortable peripheral = new StubPortable();

    @AfterEach
    void cleanup() {
        // Defensive cleanup — ensures state does not bleed between tests.
        ChatBoxManager.unregister(peripheral);
    }

    // -------------------------------------------------------------------------
    // attach / detach → ChatBoxManager registration
    // -------------------------------------------------------------------------

    @Test
    void attach_firstComputer_registersWithManager() {
        IComputerAccess computer = mock(IComputerAccess.class);

        peripheral.attach(computer);

        // Verify registration: a dispatched event must reach the peripheral's computer.
        ChatBoxManager.dispatchChat("Player", "hello");
        verify(computer).queueEvent("chat", new Object[] { "Player", "hello" });

        peripheral.detach(computer);
    }

    @Test
    void detach_lastComputer_unregistersFromManager() {
        IComputerAccess computer = mock(IComputerAccess.class);
        peripheral.attach(computer);
        peripheral.detach(computer);

        // After full detach, no events should reach the computer.
        ChatBoxManager.dispatchChat("Player", "hello");
        verify(computer, never()).queueEvent(any(), any());
    }

    @Test
    void attach_secondComputer_doesNotDoubleRegister() {
        IComputerAccess compA = mock(IComputerAccess.class);
        IComputerAccess compB = mock(IComputerAccess.class);

        peripheral.attach(compA);
        peripheral.attach(compB);

        // Each event should be queued exactly once per computer, not twice.
        ChatBoxManager.dispatchChat("Player", "hello");
        verify(compA, times(1)).queueEvent("chat", new Object[] { "Player", "hello" });
        verify(compB, times(1)).queueEvent("chat", new Object[] { "Player", "hello" });

        peripheral.detach(compA);
        peripheral.detach(compB);
    }

    @Test
    void detach_oneOfTwoComputers_remainsRegistered() {
        IComputerAccess compA = mock(IComputerAccess.class);
        IComputerAccess compB = mock(IComputerAccess.class);

        peripheral.attach(compA);
        peripheral.attach(compB);
        peripheral.detach(compA); // still has compB

        ChatBoxManager.dispatchChat("Player", "hello");
        verify(compA, never()).queueEvent(any(), any());
        verify(compB, times(1)).queueEvent("chat", new Object[] { "Player", "hello" });

        peripheral.detach(compB);
    }

    // -------------------------------------------------------------------------
    // IChatBoxReceiver → IComputerAccess.queueEvent routing
    // -------------------------------------------------------------------------

    @Test
    void onChatEvent_queuesEventOnAllComputers() {
        IComputerAccess compA = mock(IComputerAccess.class);
        IComputerAccess compB = mock(IComputerAccess.class);
        peripheral.attach(compA);
        peripheral.attach(compB);

        peripheral.onChatEvent("Steve", "hi");

        verify(compA).queueEvent("chat", new Object[] { "Steve", "hi" });
        verify(compB).queueEvent("chat", new Object[] { "Steve", "hi" });

        peripheral.detach(compA);
        peripheral.detach(compB);
    }

    @Test
    void onDeathEvent_queuesDeathOnAllComputers() {
        IComputerAccess computer = mock(IComputerAccess.class);
        peripheral.attach(computer);

        peripheral.onDeathEvent("Steve", "Creeper", "explosion");

        verify(computer).queueEvent("death", new Object[] { "Steve", "Creeper", "explosion" });

        peripheral.detach(computer);
    }

    @Test
    void onCommandEvent_queuesCommandOnAllComputers() {
        IComputerAccess computer = mock(IComputerAccess.class);
        peripheral.attach(computer);

        Map<Integer, String> args = new java.util.LinkedHashMap<>();
        args.put(1, "tp");
        args.put(2, "Steve");

        peripheral.onCommandEvent("Alex", args);

        verify(computer).queueEvent("command", new Object[] { "Alex", args });

        peripheral.detach(computer);
    }

    // -------------------------------------------------------------------------
    // ChatBoxManager with a raw IChatBoxReceiver mock (not TileChatBox)
    // -------------------------------------------------------------------------

    @Test
    void manager_dispatchesToRawIChatBoxReceiver() {
        IChatBoxReceiver receiver = mock(IChatBoxReceiver.class);
        ChatBoxManager.register(receiver);

        ChatBoxManager.dispatchChat("Player", "test");
        verify(receiver).onChatEvent("Player", "test");

        ChatBoxManager.dispatchDeath("Player", "Zombie", "melee");
        verify(receiver).onDeathEvent("Player", "Zombie", "melee");

        Map<Integer, String> args = new java.util.LinkedHashMap<>();
        args.put(1, "help");
        ChatBoxManager.dispatchCommand("Player", args);
        verify(receiver).onCommandEvent("Player", args);

        ChatBoxManager.unregister(receiver);
    }
}
