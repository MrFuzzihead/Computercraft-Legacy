package dan200.computercraft.shared.turtle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.OptionalInt;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.testsupport.StubInventory;

/**
 * Tests for {@link FurnaceRefuelHandler}, the built-in "burn the item" source of turtle fuel.
 *
 * <p>
 * Burn time is overridden rather than looked up: {@code TileEntityFurnace.getItemBurnTime} reads Forge's fuel registry,
 * which is only populated in a running game. Every rule this class owns — the probe, the limit, the tank cap, the
 * container item — lives above that lookup.
 * </p>
 */
class FurnaceRefuelHandlerTest {

    /**
     * Stand-in items. Vanilla's {@code Items} fields are only populated once the game has bootstrapped its item
     * registry, so a headless test instances its own: all these tests need is an item with a stack limit.
     */
    private static final Item COAL = new Item();

    private static final Item BUCKET = new Item();

    /** Fuel per item that {@link TestHandler} reports. */
    private static final int FUEL_PER_ITEM = 25;

    private static final int SLOT = 3;

    /** Overrides the burn-time lookup so the test needs no fuel registry. */
    private static final class TestHandler extends FurnaceRefuelHandler {

        private final int fuelPerItem;

        TestHandler(int fuelPerItem) {
            this.fuelPerItem = fuelPerItem;
        }

        @Override
        protected int getFuelPerItem(ItemStack stack) {
            return this.fuelPerItem;
        }
    }

    /** An item that leaves something behind when burned, like a lava bucket. */
    private static final class ContainerItem extends Item {

        @Override
        public ItemStack getContainerItem(ItemStack stack) {
            return new ItemStack(BUCKET, 1, 0);
        }
    }

    private StubInventory inventory;
    private ITurtleAccess turtle;

    @BeforeEach
    void setUp() {
        this.inventory = new StubInventory(9);
        this.turtle = mock(ITurtleAccess.class);
        when(this.turtle.getInventory()).thenReturn(this.inventory);
        when(this.turtle.getFuelLimit()).thenReturn(1000);
        when(this.turtle.getFuelLevel()).thenReturn(0);
    }

    /** A burnable stack: a real item, so {@code InventoryUtil} can ask it for a stack limit. */
    private static ItemStack fuel(int size) {
        return new ItemStack(COAL, size, 0);
    }

    // =========================================================================
    // Recognition
    // =========================================================================

    @Test
    void rejectsItemsWithNoBurnTime() {
        this.inventory.setStack(SLOT, fuel(1));

        assertFalse(
            new TestHandler(0).refuel(this.turtle, fuel(1), SLOT, 1)
                .isPresent());
    }

    @Test
    void theProbeRecognisesFuelWithoutConsumingIt() {
        this.inventory.setStack(SLOT, fuel(4));

        OptionalInt fuel = new TestHandler(FUEL_PER_ITEM).refuel(this.turtle, fuel(4), SLOT, 0);

        assertEquals(0, fuel.getAsInt(), "refuel(0) reports fuel but gains none");
        assertEquals(4, this.inventory.count(SLOT), "the probe must not consume anything");
    }

    // =========================================================================
    // Consumption
    // =========================================================================

    @Test
    void consumesUpToTheLimitAndReturnsFuelPerItem() {
        this.inventory.setStack(SLOT, fuel(10));

        OptionalInt fuel = new TestHandler(FUEL_PER_ITEM).refuel(this.turtle, fuel(10), SLOT, 4);

        assertEquals(FUEL_PER_ITEM * 4, fuel.getAsInt());
        assertEquals(6, this.inventory.count(SLOT));
    }

    @Test
    void consumesOnlyWhatIsThere() {
        this.inventory.setStack(SLOT, fuel(2));

        OptionalInt fuel = new TestHandler(FUEL_PER_ITEM).refuel(this.turtle, fuel(2), SLOT, 10);

        assertEquals(FUEL_PER_ITEM * 2, fuel.getAsInt());
        assertEquals(0, this.inventory.count(SLOT));
    }

    @Test
    void onlyReadsTheSelectedSlot() {
        this.inventory.setStack(0, fuel(8));
        this.inventory.setStack(SLOT, fuel(1));

        OptionalInt fuel = new TestHandler(FUEL_PER_ITEM).refuel(this.turtle, fuel(1), SLOT, 8);

        assertEquals(FUEL_PER_ITEM, fuel.getAsInt(), "the neighbouring stack must not be burned");
        assertEquals(8, this.inventory.count(0), "fuel outside the selected slot must survive");
    }

    @Test
    void doesNothingWhenTheSlotIsAlreadyEmpty() {
        OptionalInt fuel = new TestHandler(FUEL_PER_ITEM).refuel(this.turtle, fuel(1), SLOT, 1);

        assertFalse(fuel.isPresent());
    }

    // =========================================================================
    // Tank cap
    // =========================================================================

    @Test
    void takesNoItemsWhenTheTankIsFull() {
        when(this.turtle.getFuelLevel()).thenReturn(1000);
        this.inventory.setStack(SLOT, fuel(16));

        OptionalInt fuel = new TestHandler(FUEL_PER_ITEM).refuel(this.turtle, fuel(16), SLOT, 16);

        assertEquals(0, fuel.getAsInt(), "a full tank reports handled-but-nothing rather than failing");
        assertEquals(16, this.inventory.count(SLOT), "a full tank must not eat fuel items");
    }

    @Test
    void takesOnlyTheItemsThatFitInTheTank() {
        // 20 fuel of space left at 25 per item: one item fits, the second would overshoot.
        when(this.turtle.getFuelLevel()).thenReturn(980);
        this.inventory.setStack(SLOT, fuel(16));

        OptionalInt fuel = new TestHandler(FUEL_PER_ITEM).refuel(this.turtle, fuel(16), SLOT, 16);

        assertEquals(FUEL_PER_ITEM, fuel.getAsInt());
        assertEquals(15, this.inventory.count(SLOT), "only the item that fits may be consumed");
    }

    @Test
    void takingNothingLeavesTheInventoryUntouched() {
        when(this.turtle.getFuelLevel()).thenReturn(1000);
        this.inventory.setStack(SLOT, fuel(16));

        new TestHandler(FUEL_PER_ITEM).refuel(this.turtle, fuel(16), SLOT, 16);

        assertEquals(0, this.inventory.dirtyCalls(), "a refused refuel must not mark the inventory dirty");
    }

    // =========================================================================
    // Container items
    // =========================================================================

    @Test
    void returnsTheContainerItemToTheInventory() {
        this.inventory.setStack(SLOT, new ItemStack(new ContainerItem(), 1, 0));

        OptionalInt fuel = new TestHandler(FUEL_PER_ITEM)
            .refuel(this.turtle, this.inventory.getStackInSlot(SLOT), SLOT, 1);

        assertTrue(fuel.isPresent());
        assertEquals(1, this.inventory.count(SLOT), "the empty bucket must be put back");
        assertEquals(
            BUCKET,
            this.inventory.getStackInSlot(SLOT)
                .getItem());
    }
}
