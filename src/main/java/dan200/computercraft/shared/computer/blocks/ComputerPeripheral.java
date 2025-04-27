package dan200.computercraft.shared.computer.blocks;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.computer.core.ServerComputer;

public class ComputerPeripheral implements IPeripheral {

    private final String m_type;
    private final ServerComputer m_computer;

    public ComputerPeripheral(String type, ServerComputer computer) {
        this.m_type = type;
        this.m_computer = computer;
    }

    @Override
    public String getType() {
        return this.m_type;
    }

    @Override
    public String[] getMethodNames() {
        return new String[] { "turnOn", "shutdown", "reboot", "getID", "isOn" };
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException {
        switch (method) {
            case 0:
                this.m_computer.turnOn();
                return null;
            case 1:
                this.m_computer.shutdown();
                return null;
            case 2:
                this.m_computer.reboot();
                return null;
            case 3:
                return new Object[] { this.m_computer.assignID() };
            case 4:
                return new Object[] { this.m_computer.isOn() };
            default:
                return null;
        }
    }

    @Override
    public void attach(IComputerAccess computer) {}

    @Override
    public void detach(IComputerAccess computer) {}

    @Override
    public boolean equals(IPeripheral other) {
        return other != null && other.getClass() == this.getClass();
    }
}
