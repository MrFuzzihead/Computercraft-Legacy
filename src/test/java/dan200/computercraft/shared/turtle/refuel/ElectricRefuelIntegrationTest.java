package dan200.computercraft.shared.turtle.refuel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import net.minecraft.item.Item;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.impl.TurtleRefuelHandlers;
import dan200.computercraft.testsupport.RefuelHandlers;

/**
 * Tests for {@link ElectricRefuelIntegration}: the guard that keeps IC2 and CoFH classes off the classpath when those
 * mods are absent.
 *
 * <p>
 * This is the failure mode the shim exists for. A unit test JVM has no FML {@code Loader}, so {@code isModLoaded} blows
 * up rather than answering — which is exactly what an early-startup call looks like — and the integration must decline
 * instead of propagating the exception or resolving the absent API classes.
 * </p>
 */
class ElectricRefuelIntegrationTest {

    @AfterEach
    void clearHandlers() {
        RefuelHandlers.reset();
    }

    @Test
    void reportsAnAbsentModAsUnavailable() {
        assertFalse(ElectricRefuelIntegration.isLoaded("computercraft:nonexistent-modid"));
    }

    @Test
    void registeringOutsideAGameIsANoOp() {
        assertDoesNotThrow(ElectricRefuelIntegration::register);
    }

    @Test
    void registersNothingWhenNeitherModIsLoaded() {
        ElectricRefuelIntegration.register();

        ITurtleAccess turtle = mock(ITurtleAccess.class);

        assertFalse(
            TurtleRefuelHandlers.refuel(turtle, new net.minecraft.item.ItemStack((Item) null, 1, 0), 0, 1)
                .isPresent(),
            "no handler may be registered when IC2 and CoFH Core are absent");
    }
}
