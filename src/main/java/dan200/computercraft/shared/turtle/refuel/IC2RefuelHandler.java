package dan200.computercraft.shared.turtle.refuel;

import java.util.OptionalInt;

import net.minecraft.item.ItemStack;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleRefuelHandler;
import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItem;

/**
 * Refuels a turtle from IC2 electric items: RE batteries, energy crystals, lapotrons and anything else implementing
 * {@link IElectricItem}.
 *
 * <p>
 * A battery is drained, not consumed — it stays in the slot with less charge — so this handler takes no items and lets
 * IC2's item do the bookkeeping. {@code ComputerCraft.turtleEuRefuelAmount} sets the EU price of one fuel unit and
 * doubles as the off switch: a non-positive value disables the integration.
 * </p>
 *
 * <p>
 * All {@code ic2.api.item.*} references are confined to this class; {@link ElectricRefuelIntegration} only constructs
 * it once IC2 is known to be present, so a game without IC2 never resolves them.
 * </p>
 */
public class IC2RefuelHandler implements TurtleRefuelHandler {

    @Override
    public OptionalInt refuel(ITurtleAccess turtle, ItemStack stack, int slot, int limit) {
        int euPerFuel = ComputerCraft.turtleEuRefuelAmount;
        if (euPerFuel <= 0) {
            return OptionalInt.empty();
        }

        if (stack == null || !(stack.getItem() instanceof IElectricItem)) {
            return OptionalInt.empty();
        }

        if (limit == 0) {
            // refuel(0): this item is fuel, but we may not touch it.
            return OptionalInt.of(0);
        }

        if (ElectricItem.manager == null) {
            // IC2 is loaded but its item manager has not been created yet.
            return OptionalInt.empty();
        }

        int fuelUnits = EnergyRefuel.fuelUnits(turtle, limit, euPerFuel);
        if (fuelUnits <= 0) {
            return OptionalInt.of(0);
        }

        // Pay only for whole units: a nearly-empty battery must not be drained for fuel that then rounds down to
        // nothing, which would delete charge the player never got fuel for.
        int affordable = EnergyRefuel.unitsAffordable(ElectricItem.manager.getCharge(stack), euPerFuel);
        if (affordable <= 0) {
            return OptionalInt.of(0);
        }

        fuelUnits = Math.min(fuelUnits, affordable);

        // Charging from a battery in the turtle's own inventory: no tier limit, no external transfer limit,
        // and never a dry run.
        double discharged = ElectricItem.manager
            .discharge(stack, EnergyRefuel.energy(fuelUnits, euPerFuel), Integer.MAX_VALUE, true, true, false);

        return OptionalInt.of(EnergyRefuel.fuelFrom(clampToInt(discharged), euPerFuel));
    }

    private static long clampToInt(double value) {
        if (value <= 0) {
            return 0;
        }

        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (long) value;
    }
}
