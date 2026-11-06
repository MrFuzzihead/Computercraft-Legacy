package dan200.computercraft.core.computer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.core.lua.ILuaMachine;
import dan200.computercraft.core.terminal.Terminal;

/**
 * Tests for {@link Computer#getLuaMachine()}, the accessor the debugger uses to reach the running Lua runtime.
 *
 * <p>
 * The machine only exists while the computer is on, so the important half of the contract is the {@code null} an
 * off computer reports. The other half — "returns the live machine, not a copy" — is pinned by identity.
 * </p>
 */
class ComputerGetLuaMachineTest {

    private Computer computer;

    @AfterEach
    void stopThread() {
        if (computer != null) {
            computer.unload();
        }
        ComputerThread.stop();
    }

    private Computer newComputer() {
        computer = new Computer(mock(IComputerEnvironment.class), new Terminal(51, 19), 5);
        return computer;
    }

    @Test
    void anOffComputerHasNoLuaMachine() {
        assertNull(newComputer().getLuaMachine());
    }

    @Test
    void returnsWhicheverMachineIsCurrentlyHostingTheRuntime() throws Exception {
        Computer machineHost = newComputer();
        ILuaMachine machine = mock(ILuaMachine.class);
        Field field = Computer.class.getDeclaredField("m_machine");
        field.setAccessible(true);
        field.set(machineHost, machine);

        assertSame(machine, machineHost.getLuaMachine());
        assertNotNull(machineHost.getLuaMachine());
    }

    @Test
    void rebootingReplacesTheMachineRatherThanMutatingIt() throws Exception {
        Computer machineHost = newComputer();
        ILuaMachine boot = mock(ILuaMachine.class);
        ILuaMachine reboot = mock(ILuaMachine.class);
        Field field = Computer.class.getDeclaredField("m_machine");
        field.setAccessible(true);

        field.set(machineHost, boot);
        assertSame(boot, machineHost.getLuaMachine());

        field.set(machineHost, reboot);
        assertSame(reboot, machineHost.getLuaMachine(), "callers must re-read the machine across a boot");
    }
}
