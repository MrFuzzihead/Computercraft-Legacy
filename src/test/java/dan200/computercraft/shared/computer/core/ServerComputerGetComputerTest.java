package dan200.computercraft.shared.computer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import net.minecraft.world.World;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.ComputerThread;

/**
 * Tests for {@link ServerComputer#getComputer()}, the accessor addons and the in-tree debugger use to reach the core
 * computer behind a server-side wrapper.
 *
 * <p>
 * The contract is a plain pass-through, so the tests assert identity and that the returned object really is the one
 * driving the wrapper's own state (same id, same label).
 * </p>
 */
class ServerComputerGetComputerTest {

    @AfterEach
    void stopThread() {
        ComputerThread.stop();
    }

    private static ServerComputer serverComputer() {
        return new ServerComputer(mock(World.class), 42, "pocket", 7, ComputerFamily.Normal, 51, 19);
    }

    @Test
    void returnsTheCoreComputer() {
        ServerComputer server = serverComputer();

        Computer computer = server.getComputer();

        assertNotNull(computer);
        assertEquals(42, computer.getID());
        assertEquals("pocket", computer.getLabel());
    }

    @Test
    void returnsTheSameInstanceEveryTime() {
        ServerComputer server = serverComputer();

        assertSame(server.getComputer(), server.getComputer());
    }

    @Test
    void isTheComputerTheWrapperDrives() {
        ServerComputer server = serverComputer();

        server.getComputer()
            .setLabel("renamed");

        assertEquals("renamed", server.getLabel(), "the wrapper must report state written through the accessor");
    }
}
