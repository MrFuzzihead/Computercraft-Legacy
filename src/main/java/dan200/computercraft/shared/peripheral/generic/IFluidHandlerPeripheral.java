package dan200.computercraft.shared.peripheral.generic;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.IFluidHandler;

import dan200.computercraft.api.peripheral.IPeripheral;

/**
 * Optional interface for peripherals that expose an {@link IFluidHandler} for
 * cross-peripheral fluid transfer.
 *
 * <p>
 * Implemented by {@link GenericFluidPeripheral} directly and delegated to by
 * {@link GenericCombinedPeripheral} (which searches its module list for the first
 * module implementing this interface). The {@code pushFluid}/{@code pullFluid} methods
 * of {@link GenericFluidPeripheral} use an {@code instanceof} check against this
 * interface rather than the concrete class, so fluid transfer works regardless of
 * whether the target peripheral is a standalone {@code fluid_storage} or a combined
 * peripheral that also exposes other types.
 * </p>
 *
 * <p>
 * {@link #getFace()} exposes the face the peripheral was created for, allowing
 * {@link dan200.computercraft.shared.util.FluidUtil#moveFluid} to use the correct
 * side for machines that expose different tanks per face (e.g. dual-fluid processors).
 * When the face is not known, {@link ForgeDirection#UNKNOWN} is returned and
 * {@code FluidUtil} falls back to direction probing.
 * </p>
 */
public interface IFluidHandlerPeripheral extends IPeripheral {

    /**
     * Returns the underlying {@link IFluidHandler} that backs this peripheral.
     *
     * @return the fluid handler; never {@code null}
     */
    IFluidHandler getHandler();

    /**
     * Returns the face direction this peripheral is scoped to, or
     * {@link ForgeDirection#UNKNOWN} if no specific face is known.
     */
    ForgeDirection getFace();
}
