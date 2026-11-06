package dan200.computercraft.shared.turtle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleAnimation;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.impl.TurtleRefuelHandlers;
import dan200.computercraft.testsupport.RefuelHandlers;
import dan200.computercraft.testsupport.StubInventory;

/**
 * Tests for {@link TurtleRefuelCommand}: the rules that belong to the turtle rather than to the item.
 *
 * <p>
 * The command is where the refuel contract is enforced — the disabled-actions config, the "nothing in the slot" guard,
 * and the fact that no fuel means no animation. Handlers are stubbed so the test is about those rules; the handlers
 * themselves are covered by {@code FurnaceRefuelHandlerTest} and the electric tests.
 * </p>
 */
class TurtleRefuelCommandTest {

    private static final int SLOT = 2;

    /** A stand-in fuel item: vanilla's {@code Items} fields are empty until the game bootstraps its item registry. */
    private static final Item FUEL = new Item();

    private StubInventory inventory;
    private ITurtleAccess turtle;
    private String[] savedDisabledActions;

    @BeforeEach
    void setUp() {
        this.savedDisabledActions = ComputerCraft.turtleDisabledActions;
        ComputerCraft.turtleDisabledActions = new String[0];

        this.inventory = new StubInventory(9);
        this.turtle = mock(ITurtleAccess.class);
        when(this.turtle.getInventory()).thenReturn(this.inventory);
        when(this.turtle.getSelectedSlot()).thenReturn(SLOT);
        when(this.turtle.getFuelLimit()).thenReturn(1000);
        when(this.turtle.getFuelLevel()).thenReturn(0);
        this.inventory.setStack(SLOT, new ItemStack(FUEL, 5, 0));
    }

    @AfterEach
    void tearDown() {
        RefuelHandlers.reset();
        ComputerCraft.turtleDisabledActions = this.savedDisabledActions;
    }

    private void handler(OptionalInt result) {
        TurtleRefuelHandlers.register((turtle, stack, slot, limit) -> result);
    }

    // =========================================================================
    // Guards
    // =========================================================================

    @Test
    void refusesWhenTheActionIsDisabled() {
        ComputerCraft.turtleDisabledActions = new String[] { "refuel" };

        TurtleCommandResult result = new TurtleRefuelCommand(1).execute(this.turtle);

        assertFalse(result.isSuccess());
        assertTrue(
            result.getErrorMessage()
                .contains("disabled"),
            result.getErrorMessage());
    }

    @Test
    void refusesWhenTheSelectedSlotIsEmpty() {
        this.inventory.setStack(SLOT, null);

        TurtleCommandResult result = new TurtleRefuelCommand(1).execute(this.turtle);

        assertFalse(result.isSuccess());
        assertEquals("No items to combust", result.getErrorMessage());
    }

    @Test
    void refusesWhenNoHandlerAcceptsTheItem() {
        TurtleCommandResult result = new TurtleRefuelCommand(1).execute(this.turtle);

        assertFalse(result.isSuccess());
        assertEquals("Items not combustible", result.getErrorMessage());
    }

    // =========================================================================
    // Fuel accounting
    // =========================================================================

    @Test
    void addsTheFuelTheHandlerReports() {
        handler(OptionalInt.of(25));

        TurtleCommandResult result = new TurtleRefuelCommand(1).execute(this.turtle);

        assertTrue(result.isSuccess());
        verify(this.turtle).addFuel(25);
        verify(this.turtle).playAnimation(TurtleAnimation.Wait);
    }

    @Test
    void doesNotAnimateWhenNoFuelIsGained() {
        handler(OptionalInt.of(0));

        TurtleCommandResult result = new TurtleRefuelCommand(1).execute(this.turtle);

        assertTrue(result.isSuccess(), "a full tank is not a failure");
        verify(this.turtle, never()).addFuel(anyInt());
        verify(this.turtle, never()).playAnimation(TurtleAnimation.Wait);
    }

    @Test
    void doesNotAnimateOnTheZeroLimitProbe() {
        handler(OptionalInt.of(0));

        assertTrue(
            new TurtleRefuelCommand(0).execute(this.turtle)
                .isSuccess());
        verify(this.turtle, never()).playAnimation(TurtleAnimation.Wait);
    }

    // =========================================================================
    // Arguments handed to the handler
    // =========================================================================

    @Test
    void passesTheSelectedSlotAndTheLimit() {
        AtomicReference<int[]> seen = new AtomicReference<>();
        TurtleRefuelHandlers.register((turtle, stack, slot, limit) -> {
            seen.set(new int[] { slot, limit });
            return OptionalInt.of(1);
        });

        new TurtleRefuelCommand(7).execute(this.turtle);

        assertEquals(SLOT, seen.get()[0], "slot");
        assertEquals(7, seen.get()[1], "limit");
    }

    @Test
    void passesTheBlockInTheSelectedSlot() {
        AtomicReference<ItemStack> seen = new AtomicReference<>();
        TurtleRefuelHandlers.register((turtle, stack, slot, limit) -> {
            seen.set(stack);
            return OptionalInt.of(1);
        });

        new TurtleRefuelCommand(1).execute(this.turtle);

        assertEquals(
            FUEL,
            seen.get()
                .getItem());
        assertEquals(5, seen.get().stackSize);
    }
}
