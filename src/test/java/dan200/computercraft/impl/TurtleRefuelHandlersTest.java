package dan200.computercraft.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleRefuelHandler;
import dan200.computercraft.testsupport.RefuelHandlers;

/**
 * Tests for {@link TurtleRefuelHandlers}: the ordering and the two handler-bug guards.
 *
 * <p>
 * The guards matter more than they look. Without the {@code limit == 0} check, a handler that ignores the probe
 * consumes a battery on {@code turtle.refuel(0)}; without the negative check, a handler that returns garbage drains the
 * turtle's fuel. Both are bugs in someone else's code that would corrupt a player's turtle, so the registry turns them
 * into an immediate, attributable exception.
 * </p>
 */
class TurtleRefuelHandlersTest {

    private final ITurtleAccess turtle = mock(ITurtleAccess.class);
    private final ItemStack stack = new ItemStack((Item) null, 1, 0);

    @BeforeEach
    @AfterEach
    void clearHandlers() {
        RefuelHandlers.reset();
    }

    private static TurtleRefuelHandler handler(OptionalInt result) {
        return (turtle, stack, slot, limit) -> result;
    }

    @Test
    void refusesToRegisterNull() {
        assertThrows(NullPointerException.class, () -> TurtleRefuelHandlers.register(null));
    }

    @Test
    void reportsNothingWhenNoHandlerIsRegistered() {
        assertFalse(
            TurtleRefuelHandlers.refuel(turtle, stack, 0, 1)
                .isPresent());
    }

    @Test
    void returnsTheFirstHandlersVerdict() {
        TurtleRefuelHandlers.register(handler(OptionalInt.empty()));
        TurtleRefuelHandlers.register(handler(OptionalInt.of(25)));
        TurtleRefuelHandlers.register(handler(OptionalInt.of(99)));

        assertEquals(
            25,
            TurtleRefuelHandlers.refuel(turtle, stack, 0, 1)
                .getAsInt());
    }

    @Test
    void callsHandlersInRegistrationOrder() {
        AtomicReference<String> first = new AtomicReference<>();
        TurtleRefuelHandlers.register((turtle, stack, slot, limit) -> {
            first.compareAndSet(null, "one");
            return OptionalInt.empty();
        });
        TurtleRefuelHandlers.register((turtle, stack, slot, limit) -> {
            first.compareAndSet(null, "two");
            return OptionalInt.of(1);
        });

        TurtleRefuelHandlers.refuel(turtle, stack, 0, 1);

        assertEquals("one", first.get());
    }

    @Test
    void passesTheSlotAndLimitThrough() {
        AtomicReference<int[]> seen = new AtomicReference<>();
        TurtleRefuelHandlers.register((turtle, stack, slot, limit) -> {
            seen.set(new int[] { slot, limit });
            return OptionalInt.of(5);
        });

        TurtleRefuelHandlers.refuel(turtle, stack, 7, 3);

        assertEquals(7, seen.get()[0], "slot");
        assertEquals(3, seen.get()[1], "limit");
    }

    @Test
    void passesTheTurtleAndStackThrough() {
        AtomicReference<Object[]> seen = new AtomicReference<>();
        TurtleRefuelHandlers.register((t, s, slot, limit) -> {
            seen.set(new Object[] { t, s });
            return OptionalInt.of(5);
        });

        TurtleRefuelHandlers.refuel(turtle, stack, 0, 1);

        assertSame(turtle, seen.get()[0]);
        assertSame(stack, seen.get()[1]);
    }

    @Test
    void aPresentZeroMeansHandledWithNoFuel() {
        TurtleRefuelHandlers.register(handler(OptionalInt.of(0)));

        OptionalInt fuel = TurtleRefuelHandlers.refuel(turtle, stack, 0, 4);

        assertTrue(fuel.isPresent(), "of(0) means the handler recognised the item");
        assertEquals(0, fuel.getAsInt());
    }

    @Test
    void rejectsANegativeYield() {
        TurtleRefuelHandlers.register(handler(OptionalInt.of(-1)));

        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> TurtleRefuelHandlers.refuel(turtle, stack, 0, 1));

        assertTrue(
            error.getMessage()
                .contains("negative"),
            error.getMessage());
    }

    @Test
    void rejectsRefuellingDuringTheZeroLimitProbe() {
        TurtleRefuelHandlers.register(handler(OptionalInt.of(10)));

        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> TurtleRefuelHandlers.refuel(turtle, stack, 0, 0));

        assertTrue(
            error.getMessage()
                .contains("limit of 0"),
            error.getMessage());
    }

    @Test
    void allowsAPresentZeroDuringTheZeroLimitProbe() {
        TurtleRefuelHandlers.register(handler(OptionalInt.of(0)));

        assertEquals(
            0,
            TurtleRefuelHandlers.refuel(turtle, stack, 0, 0)
                .getAsInt());
    }
}
