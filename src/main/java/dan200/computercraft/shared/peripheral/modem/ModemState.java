package dan200.computercraft.shared.peripheral.modem;

import java.util.HashSet;
import java.util.Set;

import dan200.computercraft.api.lua.LuaException;

/**
 * The shared open-channel state of one or more {@link ModemPeripheral}s.
 *
 * <p>
 * A normal wireless or wall modem owns one state and one peripheral. The full wired modem owns one state and exposes
 * one peripheral on each of its six faces, so opening a channel on any face opens it on all of them, matching
 * CC:Tweaked's {@code ModemState}.
 * </p>
 */
public class ModemState {

    private final Set<Integer> m_channels = new HashSet<>();
    private final Set<ModemPeripheral> m_modems = new HashSet<>();
    private boolean m_open = false;
    private boolean m_changed = true;

    synchronized void addModem(ModemPeripheral modem) {
        if (this.m_modems.add(modem)) {
            for (Integer channel : this.m_channels) {
                modem.addReceiver(channel);
            }
        }
    }

    synchronized void removeModem(ModemPeripheral modem) {
        if (this.m_modems.remove(modem)) {
            for (Integer channel : this.m_channels) {
                modem.removeReceiver(channel);
            }
        }

        if (this.m_modems.isEmpty()) {
            this.m_channels.clear();
            this.m_open = false;
            this.m_changed = true;
        }
    }

    public synchronized boolean pollChanged() {
        if (this.m_changed) {
            this.m_changed = false;
            return true;
        } else {
            return false;
        }
    }

    public synchronized boolean isOpen() {
        return this.m_open;
    }

    public synchronized boolean isOpen(int channel) {
        return this.m_channels.contains(channel);
    }

    public synchronized void open(int channel) throws LuaException {
        if (!this.m_channels.contains(channel)) {
            if (this.m_channels.size() >= 128) {
                throw new LuaException("Too many open channels");
            }

            this.m_channels.add(channel);
            if (!this.m_open) {
                this.m_open = true;
                this.m_changed = true;
            }

            for (ModemPeripheral modem : this.m_modems) {
                modem.addReceiver(channel);
            }
        }
    }

    public synchronized void close(int channel) {
        if (this.m_channels.remove(channel)) {
            for (ModemPeripheral modem : this.m_modems) {
                modem.removeReceiver(channel);
            }

            if (this.m_channels.isEmpty() && this.m_open) {
                this.m_open = false;
                this.m_changed = true;
            }
        }
    }

    public synchronized void closeAll() {
        if (!this.m_channels.isEmpty()) {
            Set<Integer> channels = new HashSet<>(this.m_channels);
            this.m_channels.clear();
            for (ModemPeripheral modem : this.m_modems) {
                for (Integer channel : channels) {
                    modem.removeReceiver(channel);
                }
            }

            if (this.m_open) {
                this.m_open = false;
                this.m_changed = true;
            }
        }
    }
}
