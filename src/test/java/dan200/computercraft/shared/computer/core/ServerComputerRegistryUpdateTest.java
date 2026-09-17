package dan200.computercraft.shared.computer.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.world.World;

import dan200.computercraft.core.computer.ComputerThread;

/** Q7: real timeout accounting, with only network broadcasts stubbed out. */
class ServerComputerRegistryUpdateTest {

    private final ServerComputerRegistry registry = new ServerComputerRegistry();

    @AfterEach
    void cleanup() {
        registry.reset();
        ComputerThread.stop();
    }

    private static World world(boolean loaded) {
        World world = mock(World.class);
        when(world.blockExists(anyInt(), anyInt(), anyInt())).thenReturn(loaded);
        return world;
    }

    private ServerComputer register(World world, boolean positioned) {
        ServerComputer computer = spy(new ServerComputer(world, 1, null, 100, ComputerFamily.Normal, 51, 19));
        if (positioned) computer.setPosition(0, 64, 0);
        doNothing().when(computer).broadcastState();
        doNothing().when(computer).broadcastDelete();
        registry.add(100, computer);
        clearInvocations(computer);
        return computer;
    }

    @Test
    void unloadedChunkSkipsUpdatesAndBroadcastsButStillTimesOut() {
        ServerComputer computer = register(world(false), true);
        computer.getTerminal().write("changed");
        for (int i = 0; i < 101; i++) registry.update();
        assertTrue(registry.contains(100));
        assertTrue(computer.hasTimedOut());
        verify(computer, never()).keepAlive();
        verify(computer, never()).update();
        verify(computer, never()).broadcastState();
        registry.update();
        assertFalse(registry.contains(100));
        verify(computer).unload();
        verify(computer).broadcastDelete();
        registry.update();
        verify(computer, times(1)).unload();
    }

    @Test
    void loadedChunkRemainsAliveAndBroadcastsChanges() {
        ServerComputer computer = register(world(true), true);
        computer.getTerminal().write("changed");
        registry.update();
        verify(computer).broadcastState();
        for (int i = 1; i < 110; i++) registry.update();
        verify(computer, times(110)).update();
        verify(computer, times(110)).keepAlive();
        assertFalse(computer.hasTimedOut());
        assertTrue(registry.contains(100));
    }

    @Test
    void pocketWithWorldButNoPositionUsesOwnerKeepAlive() {
        World world = world(false);
        ServerComputer computer = register(world, false);
        for (int i = 0; i < 110; i++) {
            computer.keepAlive(); // ItemPocketComputer's owner-driven ping.
            registry.update();
        }
        verify(computer, times(110)).update();
        verify(computer, times(110)).keepAlive();
        verify(world, never()).blockExists(anyInt(), anyInt(), anyInt());
        assertTrue(registry.contains(100));
    }

    @Test
    void unpositionedComputerWithoutWorldStillTicksAndExpiresWithoutPings() {
        ServerComputer computer = register(null, false);
        for (int i = 0; i < 102; i++) registry.update();
        verify(computer, times(101)).update();
        verify(computer, never()).keepAlive();
        verify(computer).unload();
        assertFalse(registry.contains(100));
    }

    @Test
    void positionedComputerWithoutWorldAgesWithoutUpdating() {
        ServerComputer computer = register(null, true);
        for (int i = 0; i < 102; i++) registry.update();
        verify(computer, never()).update();
        verify(computer).unload();
        assertFalse(registry.contains(100));
    }

    @Test
    void reloadedChunkResumesBeforeTimeoutRemoval() {
        World world = world(false);
        ServerComputer computer = register(world, true);
        computer.getTerminal().write("pending");
        for (int i = 0; i < 101; i++) registry.update();
        verify(computer, never()).update();
        when(world.blockExists(0, 64, 0)).thenReturn(true);
        registry.update();
        verify(computer).update();
        verify(computer).broadcastState();
        verify(computer, never()).unload();
        assertFalse(computer.hasTimedOut());
        assertTrue(registry.contains(100));
    }
}
