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
     * Transfer up to {@code limit} millibuckets of fluid from {@code from} to {@code to}.
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
     * @param fromDir   direction to drain from ({@link ForgeDirection#UNKNOWN} for omnidirectional)
     * @param to        destination fluid handler
     * @param toDir     direction to fill into ({@link ForgeDirection#UNKNOWN} for omnidirectional)
     * @param limit     maximum amount to transfer in mB; use {@link Integer#MAX_VALUE} for unlimited
     * @param fluidName if non-null, only the fluid with this Forge registry name is transferred;
     *                  pass {@code null} to transfer whatever fluid is available
     * @return the number of millibuckets actually transferred
     * @throws LuaException if {@code fluidName} is non-null and does not name a registered fluid
     */
    public static int moveFluid(IFluidHandler from, ForgeDirection fromDir, IFluidHandler to, ForgeDirection toDir,
        int limit, String fluidName) throws LuaException {
        // Step 1: Simulate drain to discover what is available.
        FluidStack simDrained;
        if (fluidName != null) {
            Fluid fluid = FluidRegistry.getFluid(fluidName);
            if (fluid == null) {
                throw new LuaException("Unknown fluid: " + fluidName);
            }
            simDrained = from.drain(fromDir, new FluidStack(fluid, limit), false);
        } else {
            simDrained = from.drain(fromDir, limit, false);
        }

        if (simDrained == null || simDrained.amount <= 0) {
            return 0;
        }

        // Step 2: Simulate fill to discover how much the destination accepts.
        int canFill = to.fill(toDir, simDrained, false);
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
        return to.fill(toDir, actualDrained, true);
    }
}
