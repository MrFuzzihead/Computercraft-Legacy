package dan200.computercraft.shared.turtle.refuel;

import java.util.OptionalInt;

import net.minecraft.item.ItemStack;

import cofh.api.energy.IEnergyContainerItem;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleRefuelHandler;

/**
 * Refuels a turtle from Redstone Flux energy items: energy cells, Flux capacitors and anything else implementing
 * {@link IEnergyContainerItem}.
 *
 * <p>
 * Like the IC2 handler this drains the item rather than consuming it. {@code ComputerCraft.turtleRfRefuelAmount} sets
 * the RF price of one fuel unit and doubles as the off switch.
 * </p>
 *
 * <p>
 * All {@code cofh.api.energy.*} references are confined to this class; {@link ElectricRefuelIntegration} only
 * constructs it once CoFH Core is known to be present.
 * </p>
 */
public class RFRefuelHandler implements TurtleRefuelHandler {

    @Override
    public OptionalInt refuel(ITurtleAccess turtle, ItemStack stack, int slot, int limit) {
        int rfPerFuel = ComputerCraft.turtleRfRefuelAmount;
        if (rfPerFuel <= 0) {
            return OptionalInt.empty();
        }

        if (stack == null || !(stack.getItem() instanceof IEnergyContainerItem)) {
            return OptionalInt.empty();
        }

        if (limit == 0) {
            return OptionalInt.of(0);
        }

        int fuelUnits = EnergyRefuel.fuelUnits(turtle, limit, rfPerFuel);
        if (fuelUnits <= 0) {
            return OptionalInt.of(0);
        }

        IEnergyContainerItem container = (IEnergyContainerItem) stack.getItem();
        int affordable = EnergyRefuel.unitsAffordable(container.getEnergyStored(stack), rfPerFuel);
        if (affordable <= 0) {
            // An empty cell: recognised, but there is nothing worth taking.
            return OptionalInt.of(0);
        }

        int rfLimit = EnergyRefuel.energy(Math.min(fuelUnits, affordable), rfPerFuel);
        int taken = 0;
        int step = 1;
        // Extract in a loop: containers are allowed to hand back less than asked for, and a single call would then
        // leave most of the charge behind on a refuel that asked for the lot.
        while (taken < rfLimit && step > 0) {
            step = container.extractEnergy(stack, rfLimit - taken, false);
            taken += step;
        }

        return OptionalInt.of(EnergyRefuel.fuelFrom(taken, rfPerFuel));
    }
}
