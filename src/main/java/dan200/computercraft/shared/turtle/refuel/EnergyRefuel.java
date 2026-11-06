package dan200.computercraft.shared.turtle.refuel;

import dan200.computercraft.api.turtle.ITurtleAccess;

/**
 * The arithmetic shared by the electric refuel handlers: how much energy to take for a given {@code refuel(n)} call,
 * and how much fuel that energy is worth.
 *
 * <p>
 * Kept separate from the IC2 and CoFH adapters so the part that can silently go wrong — capping the request to the
 * turtle's remaining tank, and not overflowing an {@code int} on the way — is testable without either mod present.
 * </p>
 *
 * <p>
 * Both handlers work in "fuel units": one unit is one point of turtle fuel, and {@code energyPerFuel} says how much EU
 * or RF buys one. {@code refuel(n)} therefore asks to spend at most {@code n} units' worth of energy, capped by the
 * space left in the tank.
 * </p>
 */
final class EnergyRefuel {

    private EnergyRefuel() {}

    /**
     * How many fuel units a request may charge for.
     *
     * @param turtle        the turtle being refuelled
     * @param limit         the requested limit, as passed to {@code refuel(n)}; {@code refuel()} passes 64
     * @param energyPerFuel EU or RF per fuel unit; non-positive means the integration is disabled
     * @return the number of fuel units to charge for, never negative
     */
    static int fuelUnits(ITurtleAccess turtle, int limit, int energyPerFuel) {
        if (energyPerFuel <= 0 || limit <= 0) {
            return 0;
        }

        int fuelSpace = Math.max(0, turtle.getFuelLimit() - turtle.getFuelLevel());
        return Math.min(limit, fuelSpace);
    }

    /**
     * The energy to ask for, saturating rather than wrapping.
     *
     * @param fuelUnits     units of fuel to pay for
     * @param energyPerFuel EU or RF per fuel unit
     * @return {@code fuelUnits * energyPerFuel}, clamped to {@link Integer#MAX_VALUE}
     */
    static int energy(int fuelUnits, int energyPerFuel) {
        long energy = (long) fuelUnits * (long) energyPerFuel;
        return energy > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(energy, 0);
    }

    /**
     * The whole fuel units an amount of energy buys. The remainder is left in the item: charging a turtle for more
     * than it received would lose energy on every refuel.
     *
     * @param energyTaken   EU or RF actually taken from the item
     * @param energyPerFuel EU or RF per fuel unit
     * @return the fuel gained, never negative
     */
    static int fuelFrom(long energyTaken, int energyPerFuel) {
        if (energyPerFuel <= 0 || energyTaken <= 0) {
            return 0;
        }

        return (int) Math.min(energyTaken / energyPerFuel, Integer.MAX_VALUE);
    }
}
