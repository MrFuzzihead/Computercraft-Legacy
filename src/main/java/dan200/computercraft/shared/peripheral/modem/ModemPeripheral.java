package dan200.computercraft.shared.peripheral.modem;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.util.Vec3;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

public abstract class ModemPeripheral implements IPeripheral {

    private INetwork m_network = null;
    private volatile IComputerAccess m_computer = null;
    private final Map<Integer, IReceiver> m_receivers = new HashMap<>();
    private final ModemState m_state;

    protected ModemPeripheral() {
        this(new ModemState());
    }

    protected ModemPeripheral(ModemState state) {
        this.m_state = state;
        state.addModem(this);
    }

    public ModemState getModemState() {
        return this.m_state;
    }

    private void setNetwork(INetwork network) {
        synchronized (this.m_receivers) {
            if (this.m_network != network) {
                if (this.m_network != null) {
                    Iterator<IReceiver> it = this.m_receivers.values()
                        .iterator();

                    while (it.hasNext()) {
                        this.m_network.removeReceiver(it.next());
                    }
                }

                this.m_network = network;
                if (this.m_network != null) {
                    Iterator<IReceiver> it = this.m_receivers.values()
                        .iterator();

                    while (it.hasNext()) {
                        this.m_network.addReceiver(it.next());
                    }
                }
            }
        }
    }

    protected void switchNetwork() {
        this.setNetwork(this.getNetwork());
    }

    protected abstract Vec3 getPosition();

    void addReceiver(int channel) {
        synchronized (this.m_receivers) {
            if (!this.m_receivers.containsKey(channel)) {
                IReceiver receiver = new ModemPeripheral.SingleChannelReceiver(this, channel);
                this.m_receivers.put(channel, receiver);
                if (this.m_network != null) {
                    this.m_network.addReceiver(receiver);
                }
            }
        }
    }

    void removeReceiver(int channel) {
        synchronized (this.m_receivers) {
            IReceiver receiver = this.m_receivers.remove(channel);
            if (receiver != null && this.m_network != null) {
                this.m_network.removeReceiver(receiver);
            }
        }
    }

    public void destroy() {
        this.m_state.removeModem(this);
        synchronized (this.m_receivers) {
            this.m_network = null;
            this.m_receivers.clear();
        }
        this.m_computer = null;
    }

    public boolean pollChanged() {
        return this.m_state.pollChanged();
    }

    protected abstract double getTransmitRange();

    public boolean isActive() {
        return this.m_computer != null && this.m_state.isOpen();
    }

    public synchronized Vec3 getWorldPosition() {
        return this.getPosition();
    }

    public synchronized double getReceiveRange() {
        return this.getTransmitRange();
    }

    public void receive(int channel, int replyChannel, Object payload, double distance) {
        // The receiver itself only exists while this channel is registered on the network, so there is no need to
        // consult ModemState here. Doing so would take the state lock while the network holds its receiver lock,
        // which is the opposite order to ModemState.open(). A concurrent close may deliver one final message; that is
        // preferable to a deadlock.
        IComputerAccess computer = this.m_computer;
        if (computer != null) {
            computer.queueEvent(
                "modem_message",
                new Object[] { computer.getAttachmentName(), channel, replyChannel, payload, distance });
        }
    }

    protected abstract INetwork getNetwork();

    @Override
    public String getType() {
        return "modem";
    }

    @Override
    public String[] getMethodNames() {
        return new String[] { "open", "isOpen", "close", "closeAll", "transmit", "isWireless" };
    }

    private static int parseChannel(Object[] arguments, int index) throws LuaException {
        if (arguments.length > index && arguments[index] instanceof Double) {
            int channel = (int) ((Double) arguments[index]).doubleValue();
            if (channel >= 0 && channel <= 65535) {
                return channel;
            } else {
                throw new LuaException("Expected number in range 0-65535");
            }
        } else {
            throw new LuaException("Expected number");
        }
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        switch (method) {
            case 0:
                this.m_state.open(parseChannel(arguments, 0));
                return null;
            case 1:
                return new Object[] { this.m_state.isOpen(parseChannel(arguments, 0)) };
            case 2:
                this.m_state.close(parseChannel(arguments, 0));
                return null;
            case 3:
                this.m_state.closeAll();
                return null;
            case 4:
                int channel4 = parseChannel(arguments, 0);
                int replyChannel = parseChannel(arguments, 1);
                Object payload = arguments.length >= 3 ? arguments[2] : null;
                Vec3 position = this.getPosition();
                INetwork network;
                synchronized (this.m_receivers) {
                    network = this.m_network;
                }
                if (position != null && network != null) {
                    network.transmit(
                        channel4,
                        replyChannel,
                        payload,
                        this.getTransmitRange(),
                        position.xCoord,
                        position.yCoord,
                        position.zCoord,
                        this);
                }
                return null;
            case 5:
                INetwork current;
                synchronized (this.m_receivers) {
                    current = this.m_network;
                }
                if (current != null) {
                    return new Object[] { current.isWireless() };
                }

                return new Object[] { false };
            default:
                return null;
        }
    }

    @Override
    public void attach(IComputerAccess computer) {
        this.m_computer = computer;
        this.setNetwork(this.getNetwork());
    }

    @Override
    public void detach(IComputerAccess computer) {
        this.setNetwork(null);
        this.m_computer = null;
    }

    @Override
    public abstract boolean equals(IPeripheral var1);

    public IComputerAccess getComputer() {
        return this.m_computer;
    }

    private static class SingleChannelReceiver implements IReceiver {

        private ModemPeripheral m_owner;
        private int m_channel;

        public SingleChannelReceiver(ModemPeripheral owner, int channel) {
            this.m_owner = owner;
            this.m_channel = channel;
        }

        @Override
        public int getChannel() {
            return this.m_channel;
        }

        @Override
        public Vec3 getWorldPosition() {
            return this.m_owner.getWorldPosition();
        }

        @Override
        public double getReceiveRange() {
            return this.m_owner.getReceiveRange();
        }

        @Override
        public void receive(int replyChannel, Object payload, double distance, Object senderObject) {
            if (senderObject != this.m_owner) {
                this.m_owner.receive(this.m_channel, replyChannel, payload, distance);
            }
        }
    }
}
