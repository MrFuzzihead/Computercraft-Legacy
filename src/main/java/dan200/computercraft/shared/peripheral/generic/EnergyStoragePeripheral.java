package dan200.computercraft.shared.peripheral.generic;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

/**
 * CC peripheral that exposes energy storage information for blocks that implement a supported
 * energy API (e.g. CoFH RF). Peripheral type is {@code "energy_storage"}, matching CC:Tweaked's
 * generic peripheral module.
 *
 * <p>
 * Methods (indices stable):
 * <ul>
 * <li>0 — {@code getEnergy()} → number</li>
 * <li>1 — {@code getEnergyCapacity()} → number</li>
 * </ul>
 */
public class EnergyStoragePeripheral implements IPeripheral {

    private static final String[] METHOD_NAMES = { "getEnergy", "getEnergyCapacity" };

    private final IEnergyStorageAdapter m_adapter;

    public EnergyStoragePeripheral(IEnergyStorageAdapter adapter) {
        this.m_adapter = adapter;
    }

    @Override
    public String getType() {
        return "energy_storage";
    }

    @Override
    public String[] getMethodNames() {
        return METHOD_NAMES;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        switch (method) {
            case 0:
                return new Object[] { m_adapter.getEnergy() };
            case 1:
                return new Object[] { m_adapter.getEnergyCapacity() };
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
        if (!(other instanceof EnergyStoragePeripheral)) {
            return false;
        }
        return m_adapter.equals(((EnergyStoragePeripheral) other).m_adapter);
    }
}
