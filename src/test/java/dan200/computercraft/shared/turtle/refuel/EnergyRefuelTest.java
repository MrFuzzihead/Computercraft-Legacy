package dan200.computercraft.shared.turtle.refuel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.api.turtle.ITurtleAccess;

/**
 * Tests for {@link EnergyRefuel}, the arithmetic behind the IC2 and RF handlers.
 *
 * <p>
 * This is the part of the electric integrations that can go wrong quietly: an uncapped request burns a battery for
 * fuel the tank cannot hold, and an {@code int} multiply that wraps turns a full lapotron into negative fuel.
 * </p>
 */
class EnergyRefuelTest {

    private ITurtleAccess turtle;

    @BeforeEach
    void setUp() {
        this.turtle = mock(ITurtleAccess.class);
        when(this.turtle.getFuelLimit()).thenReturn(1000);
        when(this.turtle.getFuelLevel()).thenReturn(0);
    }

    // =========================================================================
    // fuelUnits
    // =========================================================================

    @Test
    void takesTheRequestedUnits() {
        assertEquals(4, EnergyRefuel.fuelUnits(this.turtle, 4, 25));
    }

    @Test
    void capsAtTheRemainingTankSpace() {
        when(this.turtle.getFuelLevel()).thenReturn(990);

        assertEquals(10, EnergyRefuel.fuelUnits(this.turtle, 64, 25));
    }

    @Test
    void takesNothingFromAFullTank() {
        when(this.turtle.getFuelLevel()).thenReturn(1000);

        assertEquals(0, EnergyRefuel.fuelUnits(this.turtle, 64, 25));
    }

    @Test
    void takesNothingWhenOverFilled() {
        // A tank shrunk by config, with more fuel in it than it now holds.
        when(this.turtle.getFuelLevel()).thenReturn(2000);

        assertEquals(0, EnergyRefuel.fuelUnits(this.turtle, 8, 25), "never negative");
    }

    @Test
    void takesNothingWhenTheIntegrationIsDisabled() {
        assertEquals(0, EnergyRefuel.fuelUnits(this.turtle, 8, 0));
        assertEquals(0, EnergyRefuel.fuelUnits(this.turtle, 8, -1));
    }

    @Test
    void takesNothingOnTheZeroLimitProbe() {
        assertEquals(0, EnergyRefuel.fuelUnits(this.turtle, 0, 25));
    }

    // =========================================================================
    // energy
    // =========================================================================

    @Test
    void multipliesUnitsByCost() {
        assertEquals(100, EnergyRefuel.energy(4, 25));
    }

    @Test
    void saturatesInsteadOfWrappingAround() {
        assertEquals(Integer.MAX_VALUE, EnergyRefuel.energy(Integer.MAX_VALUE, 25));
        assertEquals(Integer.MAX_VALUE, EnergyRefuel.energy(100_000_000, 1_000));
    }

    @Test
    void neverReturnsNegativeEnergy() {
        assertEquals(0, EnergyRefuel.energy(0, 25));
        assertEquals(0, EnergyRefuel.energy(-4, 25));
    }

    // =========================================================================
    // unitsAffordable
    // =========================================================================

    @Test
    void countsTheWholeUnitsAnItemCanPayFor() {
        // A 10,000 EU RE battery at 25 EU per fuel is worth 400 units.
        assertEquals(400, EnergyRefuel.unitsAffordable(10_000, 25));
        assertEquals(40, EnergyRefuel.unitsAffordable(10_000, 250));
    }

    @Test
    void refusesAPartialUnitRatherThanDrainingForNothing() {
        // 24 EU at 25 per unit: taking it would delete the charge and grant no fuel.
        assertEquals(0, EnergyRefuel.unitsAffordable(24, 25));
        assertEquals(1, EnergyRefuel.unitsAffordable(25, 25));
        assertEquals(1, EnergyRefuel.unitsAffordable(49, 25));
    }

    @Test
    void handlesAnEmptyOrMisconfiguredItem() {
        assertEquals(0, EnergyRefuel.unitsAffordable(0, 25));
        assertEquals(0, EnergyRefuel.unitsAffordable(-5, 25));
        assertEquals(0, EnergyRefuel.unitsAffordable(10_000, 0));
        assertEquals(0, EnergyRefuel.unitsAffordable(10_000, -1));
    }

    @Test
    void saturatesRatherThanOverflowing() {
        assertEquals(Integer.MAX_VALUE, EnergyRefuel.unitsAffordable(Integer.MAX_VALUE * 4.0, 1));
    }

    // =========================================================================
    // fuelFrom
    // =========================================================================

    @Test
    void convertsWholeUnitsOnly() {
        assertEquals(4, EnergyRefuel.fuelFrom(100, 25));
        assertEquals(4, EnergyRefuel.fuelFrom(124, 25), "the remainder stays in the item");
    }

    @Test
    void convertsNothingForNothing() {
        assertEquals(0, EnergyRefuel.fuelFrom(0, 25));
        assertEquals(0, EnergyRefuel.fuelFrom(24, 25));
        assertEquals(0, EnergyRefuel.fuelFrom(-100, 25));
        assertEquals(0, EnergyRefuel.fuelFrom(100, 0));
    }
}
