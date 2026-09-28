package dan200.computercraft.shared.turtle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TurtleCompareCommand#createStackedStack(Item, int)}, which builds the stack a
 * block contributes to a {@code turtle.compare} comparison.
 *
 * <p>
 * The null case is the regression that matters. {@code Item.registerItems()} skips an explicit
 * exclusion set when it gives blocks their item forms, so a number of vanilla blocks (redstone
 * wire, repeaters, comparators, signs, beds, doors, skulls, tripwire, brewing stands, cauldrons,
 * flower pots, cake, crops) have no item registered at their block id, and
 * {@code Item#getItemFromBlock} returns null for them. Handing that null to the
 * {@code ItemStack} constructor throws, and there is no try/catch between the turtle command and
 * the world tick, so a single {@code turtle.compare()} against such a block escaped as an
 * uncaught exception.
 * </p>
 *
 * <p>
 * These tests use plain {@code Item} instances rather than real blocks: the block and item
 * registries are not bootstrapped in a unit-test JVM, so the real block-to-item mapping cannot be
 * exercised here. The mapping itself is Minecraft's, not this mod's — what is under test is that
 * this method handles whatever the mapping returns, including nothing.
 * </p>
 */
class TurtleCompareCommandStackTest {

    @Test
    void aBlockWithNoItemFormProducesNoStackRatherThanThrowing() {
        // Every excluded vanilla block reaches this path as a null item.
        assertNull(
            TurtleCompareCommand.createStackedStack(null, 3),
            "a block with no item form must produce no stack, not throw");
    }

    @Test
    void aBlockWithNoItemFormProducesNoStackForEveryMetadata() {
        for (int metadata = 0; metadata < 16; metadata++) {
            assertNull(
                TurtleCompareCommand.createStackedStack(null, metadata),
                "metadata " + metadata + " must not turn a null item into a stack");
        }
    }

    @Test
    void anItemWithoutSubtypesAlwaysHasZeroDamage() {
        Item item = new Item();

        for (int metadata = 0; metadata < 16; metadata++) {
            ItemStack stack = TurtleCompareCommand.createStackedStack(item, metadata);
            assertNotNull(stack, "an item form should always produce a stack");
            assertSame(item, stack.getItem());
            assertEquals(1, stack.stackSize, "a comparison stack is always a single item");
            assertEquals(
                0,
                stack.getItemDamage(),
                "metadata " + metadata + " must be dropped for an item without subtypes");
        }
    }

    @Test
    void anItemWithSubtypesKeepsTheBlockMetadata() {
        Item item = new Item().setHasSubtypes(true);

        for (int metadata = 0; metadata < 16; metadata++) {
            ItemStack stack = TurtleCompareCommand.createStackedStack(item, metadata);
            assertNotNull(stack, "an item form should always produce a stack");
            assertEquals(metadata, stack.getItemDamage(), "metadata " + metadata + " must be preserved");
        }
    }

    @Test
    void negativeMetadataIsClampedByItemStackNotByUs() {
        // Block metadata is never negative in game, but ItemStack clamps it; pin that we pass the
        // value straight through rather than masking it.
        Item item = new Item().setHasSubtypes(true);
        assertEquals(
            0,
            TurtleCompareCommand.createStackedStack(item, -1)
                .getItemDamage());
    }
}
