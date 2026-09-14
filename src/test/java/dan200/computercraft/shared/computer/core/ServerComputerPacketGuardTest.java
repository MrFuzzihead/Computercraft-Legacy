package dan200.computercraft.shared.computer.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.ComputerThread;
import dan200.computercraft.core.computer.ITask;
import dan200.computercraft.shared.network.ComputerCraftPacket;

/**
 * Unit tests for {@link ServerComputer#handlePacket} payload validation.
 *
 * <p>
 * Payload arrays of a packet decoded from the network are attacker-controlled;
 * a modified client can send a {@code QueueEvent} packet with no strings, which
 * previously crashed {@code ServerComputer.handlePacket} with an NPE on
 * {@code packet.m_dataString[0]} (the S4 finding in
 * {@code docs/CODEBASE_ANALYSIS.md}). Malformed packets must now be dropped
 * silently.
 * </p>
 */
class ServerComputerPacketGuardTest {

    private static ServerComputer newComputer() {
        return new ServerComputer(null, 1, null, 100, ComputerFamily.Normal, 51, 19);
    }

    @AfterAll
    static void stopComputerDispatchThread() {
        // Constructing a ServerComputer constructs a core Computer, which calls
        // ComputerThread.start(). Its dispatch thread is non-daemon, and
        // ComputerThread.stop() cannot wake an idle waiter (finding C4 in the
        // codebase analysis). Queue one last task *after* stopping: when the
        // thread picks the queue up it observes m_stopped and exits, letting
        // the test JVM terminate.
        ComputerThread.stop();
        ComputerThread.queueTask(new ITask() {

            @Override
            public Computer getOwner() {
                return null;
            }

            @Override
            public void execute() {}
        }, null);
    }

    @Test
    void queueEventPacketWithoutStringsIsDroppedInsteadOfThrowing() {
        // Previously: NPE on packet.m_dataString[0].
        ServerComputer computer = newComputer();
        ComputerCraftPacket packet = new ComputerCraftPacket();
        packet.m_packetType = ComputerCraftPacket.QueueEvent;
        packet.m_dataInt = new int[] { computer.getInstanceID() };
        packet.m_dataString = null;

        assertDoesNotThrow(() -> computer.handlePacket(packet, null));
        assertFalse(computer.isOn());
    }

    @Test
    void queueEventPacketWithNullEventNameIsDroppedInsteadOfThrowing() {
        // A decoded packet can carry an explicit null string element; queueing
        // a null event name into Lua would be just as broken as crashing.
        ServerComputer computer = newComputer();
        ComputerCraftPacket packet = new ComputerCraftPacket();
        packet.m_packetType = ComputerCraftPacket.QueueEvent;
        packet.m_dataInt = new int[] { computer.getInstanceID() };
        packet.m_dataString = new String[] { null };

        assertDoesNotThrow(() -> computer.handlePacket(packet, null));
        assertFalse(computer.isOn());
    }

    @Test
    void validQueueEventPacketIsHandledWithoutThrowing() {
        ServerComputer computer = newComputer();
        ComputerCraftPacket packet = new ComputerCraftPacket();
        packet.m_packetType = ComputerCraftPacket.QueueEvent;
        packet.m_dataInt = new int[] { computer.getInstanceID() };
        packet.m_dataString = new String[] { "custom_event" };
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("n", 5);
        packet.m_dataNBT = nbt;

        assertDoesNotThrow(() -> computer.handlePacket(packet, null));
        // The computer is off, so the core Computer drops the event, but the
        // packet must still have passed validation rather than being rejected
        // by a guard regression.
        assertFalse(computer.isOn());
    }

    @Test
    void controlPacketsWithoutPayloadDoNotThrow() {
        ServerComputer computer = newComputer();
        for (byte type : new byte[] { ComputerCraftPacket.TurnOn, ComputerCraftPacket.Reboot,
            ComputerCraftPacket.Shutdown, ComputerCraftPacket.SetLabel }) {
            ComputerCraftPacket packet = new ComputerCraftPacket();
            packet.m_packetType = type;

            assertDoesNotThrow(() -> computer.handlePacket(packet, null), "packet type " + type);
        }

        assertFalse(computer.isOn());
    }

    @Test
    void setLabelPacketStillSetsAndClearsTheLabel() {
        // Guards must not break the legitimate SetLabel flow; the pre-existing
        // case-6 behavior tolerates a null string (label cleared).
        ServerComputer computer = newComputer();

        ComputerCraftPacket set = new ComputerCraftPacket();
        set.m_packetType = ComputerCraftPacket.SetLabel;
        set.m_dataInt = new int[] { computer.getInstanceID() };
        set.m_dataString = new String[] { "My Label" };
        assertDoesNotThrow(() -> computer.handlePacket(set, null));
        assertEquals("My Label", computer.getLabel());

        ComputerCraftPacket clear = new ComputerCraftPacket();
        clear.m_packetType = ComputerCraftPacket.SetLabel;
        clear.m_dataInt = new int[] { computer.getInstanceID() };
        clear.m_dataString = new String[] { null };
        assertDoesNotThrow(() -> computer.handlePacket(clear, null));
        assertNull(computer.getLabel());
    }
}
