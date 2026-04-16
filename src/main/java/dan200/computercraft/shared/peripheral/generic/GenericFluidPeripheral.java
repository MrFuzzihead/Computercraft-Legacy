package dan200.computercraft.shared.peripheral.generic;

import java.util.HashMap;
import java.util.Map;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.util.FluidUtil;

/**
 * Generic peripheral that wraps any tile entity implementing {@link IFluidHandler} and exposes it
 * as a {@code fluid_storage} peripheral with three methods:
 *
 * <ul>
 * <li>{@code tanks()} — snapshot of all tanks (capacity, and if non-empty: name and amount).</li>
 * <li>{@code pushFluid(toName [, limit [, fluidName]])} — push fluid to another fluid-storage
 * peripheral.</li>
 * <li>{@code pullFluid(fromName [, limit [, fluidName]])} — pull fluid from another
 * fluid-storage peripheral.</li>
 * </ul>
 *
 * <h3>Directionality</h3>
 * <p>
 * This peripheral is created with the {@link ForgeDirection} of the face the computer is
 * attached to (mirroring how CC:Tweaked uses per-face capability instances). All
 * {@code drain}/{@code fill} calls on <em>this</em> handler use that fixed face direction,
 * so machines that expose different tanks on different sides behave correctly when the
 * computer is placed against the appropriate face.
 * </p>
 * <p>
 * For the remote end of a transfer ({@code pushFluid}/{@code pullFluid}), the destination
 * or source handler is probed with the same direction-scanning strategy as
 * {@link FluidUtil#moveFluid}: {@link ForgeDirection#UNKNOWN} first, then all six cardinal
 * directions, stopping at the first that accepts the given fluid. If the remote peripheral
 * was also created with a fixed face direction (because it too has a computer on one of its
 * faces), its own face direction is used and no probing is needed.
 * </p>
 * <p>
 * Tile entities that already implement {@link dan200.computercraft.shared.peripheral.common.IPeripheralTile}
 * are given their own peripheral and are NOT wrapped by this class.
 * </p>
 */
public class GenericFluidPeripheral implements IFluidHandlerPeripheral {

    private static final String[] METHOD_NAMES = { "tanks", "pushFluid", "pullFluid" };
    private static final int TANKS = 0;
    private static final int PUSH_FLUID = 1;
    private static final int PULL_FLUID = 2;

    private final IFluidHandler m_handler;
    /**
     * The face of the tile entity this peripheral is attached to.
     * Used to scope {@code drain}/{@code fill} calls to the correct tank on
     * machines that expose different fluid tanks per face.
     */
    private final ForgeDirection m_face;

    public GenericFluidPeripheral(IFluidHandler handler, ForgeDirection face) {
        this.m_handler = handler;
        this.m_face = face;
    }

    /**
     * Convenience constructor for contexts where no specific face is known (e.g. unit tests).
     * Defaults to {@link ForgeDirection#UNKNOWN}, causing {@link FluidUtil} to probe all
     * directions when transferring fluid.
     */
    public GenericFluidPeripheral(IFluidHandler handler) {
        this(handler, ForgeDirection.UNKNOWN);
    }

    /** Exposes the underlying handler for cross-peripheral fluid transfer. */
    @Override
    public IFluidHandler getHandler() {
        return m_handler;
    }

    /**
     * Returns the face direction this peripheral was attached on.
     * Used by {@link FluidUtil#moveFluid} to scope drain/fill calls correctly.
     */
    public ForgeDirection getFace() {
        return m_face;
    }

    @Override
    public String getType() {
        return "fluid_storage";
    }

    @Override
    public String[] getMethodNames() {
        return METHOD_NAMES;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, final ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        switch (method) {
            case TANKS: {
                return context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        FluidTankInfo[] tanks = m_handler.getTankInfo(ForgeDirection.UNKNOWN);
                        Map<Object, Object> result = new HashMap<>();
                        if (tanks != null) {
                            for (int i = 0; i < tanks.length; i++) {
                                Map<Object, Object> tank = new HashMap<>();
                                tank.put("capacity", (double) tanks[i].capacity);
                                if (tanks[i].fluid != null && tanks[i].fluid.amount > 0) {
                                    tank.put(
                                        "name",
                                        tanks[i].fluid.getFluid()
                                            .getName());
                                    tank.put("amount", (double) tanks[i].fluid.amount);
                                }
                                result.put(i + 1, tank);
                            }
                        }
                        return new Object[] { result };
                    }
                });
            }

            case PUSH_FLUID: {
                final String toName = parseRequiredString(arguments, 0, "toName");
                final int limit = parseOptionalLimit(arguments, 1);
                final String fluidName = parseOptionalString(arguments, 2, "fluid name");

                return context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        IPeripheral target = computer.getAvailablePeripheral(toName);
                        if (target == null) {
                            throw new LuaException("Target '" + toName + "' does not exist");
                        }
                        if (!(target instanceof IFluidHandlerPeripheral)) {
                            throw new LuaException("Target '" + toName + "' is not a fluid storage");
                        }
                        IFluidHandler dest = ((IFluidHandlerPeripheral) target).getHandler();
                        ForgeDirection destFace = ((IFluidHandlerPeripheral) target).getFace();
                        int moved = FluidUtil.moveFluid(m_handler, m_face, dest, destFace, limit, fluidName);
                        return new Object[] { (double) moved };
                    }
                });
            }

            case PULL_FLUID: {
                final String fromName = parseRequiredString(arguments, 0, "fromName");
                final int limit = parseOptionalLimit(arguments, 1);
                final String fluidName = parseOptionalString(arguments, 2, "fluid name");

                return context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        IPeripheral source = computer.getAvailablePeripheral(fromName);
                        if (source == null) {
                            throw new LuaException("Target '" + fromName + "' does not exist");
                        }
                        if (!(source instanceof IFluidHandlerPeripheral)) {
                            throw new LuaException("Target '" + fromName + "' is not a fluid storage");
                        }
                        IFluidHandler src = ((IFluidHandlerPeripheral) source).getHandler();
                        ForgeDirection srcFace = ((IFluidHandlerPeripheral) source).getFace();
                        int moved = FluidUtil.moveFluid(src, srcFace, m_handler, m_face, limit, fluidName);
                        return new Object[] { (double) moved };
                    }
                });
            }

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
        if (other instanceof GenericFluidPeripheral) {
            return ((GenericFluidPeripheral) other).m_handler == this.m_handler;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Argument helpers
    // -------------------------------------------------------------------------

    private static String parseRequiredString(Object[] args, int index, String paramName) throws LuaException {
        if (args.length <= index || !(args[index] instanceof String)) {
            throw new LuaException("Expected string for " + paramName);
        }
        return (String) args[index];
    }

    private static int parseOptionalLimit(Object[] args, int index) throws LuaException {
        if (args.length <= index || args[index] == null) {
            return Integer.MAX_VALUE;
        }
        if (!(args[index] instanceof Number)) {
            throw new LuaException("Expected number for limit");
        }
        int limit = ((Number) args[index]).intValue();
        if (limit <= 0) {
            throw new LuaException("Limit must be a positive number");
        }
        return limit;
    }

    private static String parseOptionalString(Object[] args, int index, String paramName) throws LuaException {
        if (args.length <= index || args[index] == null) {
            return null;
        }
        if (!(args[index] instanceof String)) {
            throw new LuaException("Expected string for " + paramName);
        }
        return (String) args[index];
    }
}
