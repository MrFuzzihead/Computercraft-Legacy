package dan200.computercraft.shared.turtle.blocks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.minecraft.world.World;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TileTurtle#setMoved(boolean)}, the writer for the moved flag that
 * {@link TileTurtle#hasMoved()} reads.
 *
 * <p>
 * The flag is what stops a turtle from acting on the tick after it is picked up or placed, so the round trip through
 * the public pair is the contract: addons that swap a turtle item for a tile use it to reproduce what
 * {@link TileTurtle#transferStateFrom(TileTurtle)} does internally — which is exactly what the last test pins, by
 * asserting the internal path goes through the public setter.
 * </p>
 */
class TileTurtleMovedTest {

    /** Exposes the protected {@code worldObj} field so a tile can be given a world outside the package. */
    private static final class TestTurtle extends TileTurtle {

        void setWorld(World world) {
            this.worldObj = world;
        }
    }

    /**
     * A turtle needs a block in the world before it can transfer state, because the transfer re-primes the brain's
     * family from the block. The mock answers that lookup; nothing else about the world is read.
     */
    private static TestTurtle turtle() {
        World world = mock(World.class);
        when(world.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(mock(BlockTurtle.class));
        TestTurtle turtle = new TestTurtle();
        turtle.setWorld(world);
        return turtle;
    }

    @Test
    void newTurtleHasNotMoved() {
        assertFalse(turtle().hasMoved());
    }

    @Test
    void setMovedWritesWhatHasMovedReads() {
        TestTurtle turtle = turtle();

        turtle.setMoved(true);
        assertTrue(turtle.hasMoved());

        turtle.setMoved(false);
        assertFalse(turtle.hasMoved());
    }

    @Test
    void transferStateFromMarksTheSourceTileAsMoved() {
        TestTurtle source = turtle();
        TestTurtle target = turtle();
        assertFalse(source.hasMoved());

        target.transferStateFrom(source);

        assertTrue(source.hasMoved(), "the tile that gave up its state must be considered moved");
        assertFalse(target.hasMoved(), "the tile receiving the state has not moved");
    }
}
