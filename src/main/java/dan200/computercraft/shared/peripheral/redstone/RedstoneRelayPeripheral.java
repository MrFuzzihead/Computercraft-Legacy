package dan200.computercraft.shared.peripheral.redstone;

import java.util.HashMap;
import java.util.Map;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralTargeted;
import dan200.computercraft.core.computer.Computer;

/**
 * Peripheral implementation for the Redstone Relay block. Exposes the same
 * 14-method surface as the {@code redstone} / {@code rs} global API so that
 * computers can read and write redstone signals on all six sides of the relay
 * through a wired or wireless modem.
 */
public class RedstoneRelayPeripheral implements IPeripheralTargeted {

    /** Method names mirror {@code RedstoneAPI.getMethodNames()} exactly. */
    private static final String[] METHOD_NAMES = new String[] { "getSides", "setOutput", "getOutput", "getInput",
        "setBundledOutput", "getBundledOutput", "getBundledInput", "testBundledInput", "setAnalogOutput",
        "setAnalogueOutput", "getAnalogOutput", "getAnalogueOutput", "getAnalogInput", "getAnalogueInput" };

    private final TileRedstoneRelay m_tile;

    public RedstoneRelayPeripheral(TileRedstoneRelay tile) {
        this.m_tile = tile;
    }

    // =========================================================================
    // IPeripheral
    // =========================================================================

    @Override
    public String getType() {
        return "redstone_relay";
    }

    @Override
    public String[] getMethodNames() {
        return METHOD_NAMES.clone();
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] args)
        throws LuaException {
        switch (method) {
            case 0: {
                // getSides()
                Map<Object, Object> table = new HashMap<>();
                for (int i = 0; i < Computer.s_sideNames.length; i++) {
                    table.put(i + 1, Computer.s_sideNames[i]);
                }
                return new Object[] { table };
            }
            case 1: {
                // setOutput(side, bool)
                if (args.length >= 2 && args[0] instanceof String && args[1] instanceof Boolean) {
                    int side = parseSide(args);
                    boolean output = (Boolean) args[1];
                    m_tile.setOutput(side, output ? 15 : 0);
                    return null;
                }
                throw new LuaException("Expected string, boolean");
            }
            case 2: {
                // getOutput(side)
                int side = parseSide(args);
                return new Object[] { m_tile.getOutput(side) > 0 };
            }
            case 3: {
                // getInput(side)
                int side = parseSide(args);
                return new Object[] { m_tile.getInput(side) > 0 };
            }
            case 4: {
                // setBundledOutput(side, number)
                if (args.length >= 2 && args[0] instanceof String && args[1] instanceof Double) {
                    int side = parseSide(args);
                    int mask = ((Double) args[1]).intValue();
                    m_tile.setBundledOutput(side, mask);
                    return null;
                }
                throw new LuaException("Expected string, number");
            }
            case 5: {
                // getBundledOutput(side)
                int side = parseSide(args);
                return new Object[] { m_tile.getBundledOutput(side) };
            }
            case 6: {
                // getBundledInput(side)
                int side = parseSide(args);
                return new Object[] { m_tile.getBundledInput(side) };
            }
            case 7: {
                // testBundledInput(side, mask)
                if (args.length >= 2 && args[0] instanceof String && args[1] instanceof Double) {
                    int side = parseSide(args);
                    int mask = ((Double) args[1]).intValue();
                    int input = m_tile.getBundledInput(side);
                    return new Object[] { (input & mask) == mask };
                }
                throw new LuaException("Expected string, number");
            }
            case 8:
            case 9: {
                // setAnalogOutput / setAnalogueOutput (shared body)
                if (args.length >= 2 && args[0] instanceof String && args[1] instanceof Double) {
                    int side = parseSide(args);
                    int level = ((Double) args[1]).intValue();
                    if (level >= 0 && level <= 15) {
                        m_tile.setOutput(side, level);
                        return null;
                    }
                    throw new LuaException("Expected number in range 0-15");
                }
                throw new LuaException("Expected string, number");
            }
            case 10:
            case 11: {
                // getAnalogOutput / getAnalogueOutput (shared body)
                int side = parseSide(args);
                return new Object[] { m_tile.getOutput(side) };
            }
            case 12:
            case 13: {
                // getAnalogInput / getAnalogueInput (shared body)
                int side = parseSide(args);
                return new Object[] { m_tile.getInput(side) };
            }
            default:
                return null;
        }
    }

    @Override
    public void attach(IComputerAccess computer) {
        m_tile.attachComputer(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        m_tile.detachComputer(computer);
    }

    @Override
    public boolean equals(IPeripheral other) {
        if (other instanceof RedstoneRelayPeripheral otherRelay) {
            return otherRelay.m_tile == this.m_tile;
        }
        return false;
    }

    // =========================================================================
    // IPeripheralTargeted
    // =========================================================================

    @Override
    public Object getTarget() {
        return m_tile;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private int parseSide(Object[] args) throws LuaException {
        if (args.length >= 1 && args[0] instanceof String) {
            String side = (String) args[0];
            for (int n = 0; n < Computer.s_sideNames.length; n++) {
                if (side.equals(Computer.s_sideNames[n])) {
                    return n;
                }
            }
            throw new LuaException("Invalid side.");
        }
        throw new LuaException("Expected string");
    }
}
