package dan200.computercraft.shared.util;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import dan200.computercraft.api.lua.LuaException;

/**
 * Utilities for moving fluids between {@link IFluidHandler} instances.
 *
 * <p>
 * Mirrors the simulate-then-execute transfer pattern from CC:Tweaked's
 * {@code AbstractFluidMethods.moveFluid()}, adapted for the Forge 1.7.10
 * {@link IFluidHandler} API.
 * </p>
 */
public final class FluidUtil {

    private FluidUtil() {}

    /**
     * Directions probed when querying an {@link IFluidHandler} with no known face.
     * {@link ForgeDirection#UNKNOWN} is tried first (accepted by vanilla tanks and most
     * omnidirectional handlers); the six cardinal directions follow as fallbacks for
     * machines that only expose fluid on specific faces (e.g. Thermal Expansion machines).
     */
    private static final ForgeDirection[] PROBE_DIRECTIONS = { ForgeDirection.UNKNOWN, ForgeDirection.DOWN,
        ForgeDirection.UP, ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.EAST, ForgeDirection.WEST, };

    /**
     * Directions probed for the drain side (source).
     * UNKNOWN leads because most tanks drain happily from any side; the specific
     * face follows so directional tanks are still covered.
     */
    private static ForgeDirection[] drainDirectionsToTry(ForgeDirection face) {
        if (face == null || face == ForgeDirection.UNKNOWN) {
            return PROBE_DIRECTIONS;
        }
        // UNKNOWN first (works for most tanks), then the specific face, then remaining cardinals.
        ForgeDirection[] dirs = new ForgeDirection[PROBE_DIRECTIONS.length + 1];
        dirs[0] = ForgeDirection.UNKNOWN;
        dirs[1] = face;
        int pos = 2;
        for (ForgeDirection d : PROBE_DIRECTIONS) {
            if (d != ForgeDirection.UNKNOWN && d != face) {
                dirs[pos++] = d;
            }
        }
        return dirs;
    }

    /**
     * Transfer up to {@code limit} millibuckets of fluid from {@code from} to {@code to},
     * using the supplied face hints to scope drain/fill calls.
     *
     * <p>
     * When a face hint is {@link ForgeDirection#UNKNOWN} it means "not known"; the method
     * will probe all seven directions (UNKNOWN + 6 cardinals) for that side until one
     * works. When a real cardinal face is supplied it is tried first; if it yields
     * nothing the method still falls back to probing, so callers never need to handle
     * directionality themselves.
     * </p>
     *
     * <p>
     * The transfer is performed in five steps:
     * </p>
     * <ol>
     * <li>Simulate a drain from {@code from} to discover what is available.</li>
     * <li>Simulate a fill into {@code to} to discover how much it accepts.</li>
     * <li>Build the actual transfer stack limited by what the destination accepts.</li>
     * <li>Execute the drain.</li>
     * <li>Execute the fill and return the amount inserted.</li>
     * </ol>
     *
     * @param from      source fluid handler
     * @param fromFace  the face of {@code from} to drain through, or
     *                  {@link ForgeDirection#UNKNOWN} to probe all directions
     * @param to        destination fluid handler
     * @param toFace    the face of {@code to} to fill through, or
     *                  {@link ForgeDirection#UNKNOWN} to probe all directions
     * @param limit     maximum amount to transfer in mB; use {@link Integer#MAX_VALUE} for unlimited
     * @param fluidName if non-null, only the fluid with this Forge registry name is transferred;
     *                  pass {@code null} to transfer whatever fluid is available
     * @return the number of millibuckets actually transferred
     * @throws LuaException if {@code fluidName} is non-null and does not name a registered fluid
     */
    public static int moveFluid(IFluidHandler from, ForgeDirection fromFace, IFluidHandler to, ForgeDirection toFace,
        int limit, String fluidName) throws LuaException {
        Fluid filterFluid = null;
        if (fluidName != null) {
            filterFluid = FluidRegistry.getFluid(fluidName);
            if (filterFluid == null) {
                throw new LuaException("Unknown fluid: " + fluidName);
            }
        }

        // Step 1: Simulate drain — use UNKNOWN first (works for most tanks), then the specific
        // face, then remaining cardinals.
        FluidStack simDrained = null;
        ForgeDirection fromDir = null;
        for (ForgeDirection dir : drainDirectionsToTry(fromFace)) {
            FluidStack result = filterFluid != null ? from.drain(dir, new FluidStack(filterFluid, limit), false)
                : from.drain(dir, limit, false);
            if (result != null && result.amount > 0) {
                simDrained = result;
                fromDir = dir;
                break;
            }
        }
        if (simDrained == null) {
            return 0;
        }

        // Step 2: Simulate fill — use the known face first, then probe if needed.
        int canFill = 0;
        ForgeDirection toDir = null;
        for (ForgeDirection dir : directionsToTry(toFace)) {
            int result = to.fill(dir, simDrained, false);
            if (result > 0) {
                canFill = result;
                toDir = dir;
                break;
            }
        }
        if (canFill <= 0) {
            return 0;
        }

        // Step 3: Build the actual transfer stack limited by what the destination accepts.
        FluidStack toTransfer = new FluidStack(simDrained.getFluid(), canFill);

        // Step 4: Execute drain.
        FluidStack actualDrained = from.drain(fromDir, toTransfer, true);
        if (actualDrained == null || actualDrained.amount <= 0) {
            return 0;
        }

        // Step 5: Execute fill and return the amount inserted.
        int filled = to.fill(toDir, actualDrained, true);
        // Safety: if the destination accepted less than drained, return the remainder.
        if (filled < actualDrained.amount) {
            from.fill(fromDir, new FluidStack(actualDrained.getFluid(), actualDrained.amount - filled), true);
        }
        return filled;
    }

    /**
     * Convenience overload that probes all directions on both sides.
     * Equivalent to {@code moveFluid(from, UNKNOWN, to, UNKNOWN, limit, fluidName)}.
     */
    public static int moveFluid(IFluidHandler from, IFluidHandler to, int limit, String fluidName) throws LuaException {
        return moveFluid(from, ForgeDirection.UNKNOWN, to, ForgeDirection.UNKNOWN, limit, fluidName);
    }

    /**
     * Returns the directions to probe for a given face hint.
     * If the hint is a real cardinal direction it appears first (preferred), followed by
     * the full probe set so we always fall back gracefully.
     */
    private static ForgeDirection[] directionsToTry(ForgeDirection face) {
        if (face == null || face == ForgeDirection.UNKNOWN) {
            return PROBE_DIRECTIONS;
        }
        // Real face: try it first, then the full probe set as fallback.
        ForgeDirection[] dirs = new ForgeDirection[PROBE_DIRECTIONS.length + 1];
        dirs[0] = face;
        System.arraycopy(PROBE_DIRECTIONS, 0, dirs, 1, PROBE_DIRECTIONS.length);
        return dirs;
    }
}
