package dan200.computercraft.shared.peripheral.modem;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.minecraft.util.Vec3;

import org.junit.jupiter.api.Test;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

class ModemStateTest {

    private static final class TestModem extends ModemPeripheral {

        private final ModemState m_state;

        TestModem(ModemState state) {
            super(state);
            this.m_state = state;
        }

        @Override
        protected Vec3 getPosition() {
            return Vec3.createVectorHelper(0.0, 0.0, 0.0);
        }

        @Override
        protected double getTransmitRange() {
            return 64.0;
        }

        @Override
        protected INetwork getNetwork() {
            return null;
        }

        @Override
        public boolean equals(IPeripheral other) {
            return this == other;
        }
    }

    @Test
    void openAndCloseTrackChannels() throws Exception {
        ModemState state = new ModemState();

        state.open(5);
        assertTrue(state.isOpen());
        assertTrue(state.isOpen(5));
        assertFalse(state.isOpen(6));

        state.close(5);
        assertFalse(state.isOpen());
        assertFalse(state.isOpen(5));
    }

    @Test
    void openingTheSameChannelTwiceIsIdempotent() throws Exception {
        ModemState state = new ModemState();
        state.open(5);
        state.open(5);
        assertTrue(state.isOpen(5));
    }

    @Test
    void theChannelLimitIs128() throws Exception {
        ModemState state = new ModemState();
        for (int channel = 0; channel < 128; channel++) {
            state.open(channel);
        }

        assertThrows(LuaException.class, () -> state.open(128));
    }

    @Test
    void pollChangedReportsOnlyOncePerChange() throws Exception {
        ModemState state = new ModemState();
        assertTrue(state.pollChanged(), "a new state starts changed");
        assertFalse(state.pollChanged());

        state.open(1);
        assertTrue(state.pollChanged());
        assertFalse(state.pollChanged());

        state.close(1);
        assertTrue(state.pollChanged());
    }

    @Test
    void allModemsSharingAStateSeeTheSameChannels() throws Exception {
        ModemState state = new ModemState();
        TestModem first = new TestModem(state);
        TestModem second = new TestModem(state);

        state.open(7);

        assertSame(first.getModemState(), second.getModemState());
        assertTrue(
            first.getModemState()
                .isOpen(7));
        assertTrue(
            second.getModemState()
                .isOpen(7));
    }

    @Test
    void detachKeepsChannelsOpenButStopsTheModemBeingActive() throws Exception {
        ModemState state = new ModemState();
        TestModem modem = new TestModem(state);
        IComputerAccess computer = mock(IComputerAccess.class);

        modem.attach(computer);
        state.open(3);
        assertTrue(modem.isActive());

        modem.detach(computer);

        assertTrue(state.isOpen(3), "upstream keeps channels open across detach");
        assertFalse(modem.isActive());
    }

    @Test
    void receiveQueuesWhenAttached() throws Exception {
        ModemState state = new ModemState();
        TestModem modem = new TestModem(state);
        IComputerAccess computer = mock(IComputerAccess.class);
        when(computer.getAttachmentName()).thenReturn("modem");

        modem.receive(1, 2, "payload", 1.0);
        verify(computer, never()).queueEvent("modem_message", new Object[] { "modem", 1, 2, "payload", 1.0 });

        modem.attach(computer);
        state.open(1);
        modem.receive(1, 2, "payload", 1.0);

        verify(computer).queueEvent("modem_message", new Object[] { "modem", 1, 2, "payload", 1.0 });
    }

    @Test
    void destroyingTheLastModemClosesTheSharedState() throws Exception {
        ModemState state = new ModemState();
        TestModem first = new TestModem(state);
        TestModem second = new TestModem(state);
        state.open(4);

        first.destroy();
        assertTrue(state.isOpen(4), "the state is still used by the second modem");

        second.destroy();
        assertFalse(state.isOpen());
        assertTrue(state.pollChanged(), "removing the last modem marks the state changed");
        assertFalse(state.isOpen(4));
    }
}
