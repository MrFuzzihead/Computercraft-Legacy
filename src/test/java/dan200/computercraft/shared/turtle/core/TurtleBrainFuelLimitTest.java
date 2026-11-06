package dan200.computercraft.shared.turtle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.minecraft.world.World;

import org.junit.jupiter.api.Test;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.turtle.blocks.BlockTurtle;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;

/**
 * Tests that a turtle's fuel limit can be read without touching the world.
 *
 * <p>
 * {@code turtle.getFuelLimit()} and {@code turtle.getFuelLevel()} run on a computer worker thread, and the limit
 * depends
 * on the turtle's family, which lives in the world block. Reading that block from the worker is what HodgePodge reports
 * as {@code Off-thread read from Computer Worker-N - serving from snapshot}: harmless in itself (it serves a snapshot)
 * but a real race it only papers over. The brain caches the family; these tests pin that the fuel limit uses that
 * cache,
 * and that the cache is primed before the computer can run.
 * </p>
 */
class TurtleBrainFuelLimitTest {

    /** Exposes the priming step, which normally runs from {@code updateEntity} on the main thread. */
    private static final class TestTurtle extends TileTurtle {

        void setWorld(World world) {
            this.worldObj = world;
        }

        TurtleBrain brain() {
            return (TurtleBrain) this.getAccess();
        }

        void prime() {
            this.primeFamilyCache();
        }
    }

    // =========================================================================
    // The worker-thread path
    // =========================================================================

    @Test
    void aPrimedTurtleReadsItsLimitWithoutAWorld() {
        TestTurtle turtle = new TestTurtle();
        turtle.brain()
            .setFamily(ComputerFamily.Advanced);

        // No world at all: the old code called getBlock() here and threw.
        assertEquals(
            ComputerCraft.advancedTurtleFuelLimit,
            turtle.brain()
                .getFuelLimit());
    }

    @Test
    void aPrimedNormalTurtleReadsTheNormalLimit() {
        TestTurtle turtle = new TestTurtle();
        turtle.brain()
            .setFamily(ComputerFamily.Normal);

        assertEquals(
            ComputerCraft.turtleFuelLimit,
            turtle.brain()
                .getFuelLimit());
    }

    @Test
    void theFuelLevelClampsToTheLimitWithoutAWorld() {
        TestTurtle turtle = new TestTurtle();
        turtle.brain()
            .setFamily(ComputerFamily.Normal);

        assertEquals(
            0,
            turtle.brain()
                .getFuelLevel());

        turtle.brain()
            .setFuelLevel(ComputerCraft.turtleFuelLimit * 2);

        assertEquals(
            ComputerCraft.turtleFuelLimit,
            turtle.brain()
                .getFuelLevel());
    }

    // =========================================================================
    // Priming
    // =========================================================================

    @Test
    void primingReadsTheBlockAndCachesTheFamily() {
        World world = worldWithTurtle(ComputerFamily.Advanced);
        TestTurtle turtle = new TestTurtle();
        turtle.setWorld(world);
        assertFalse(
            turtle.brain()
                .hasFamily());

        turtle.prime();

        assertTrue(
            turtle.brain()
                .hasFamily());
        assertEquals(
            ComputerFamily.Advanced,
            turtle.brain()
                .getFamily());
    }

    @Test
    void primingOnlyReadsTheWorldOnce() {
        World world = worldWithTurtle(ComputerFamily.Normal);
        TestTurtle turtle = new TestTurtle();
        turtle.setWorld(world);

        turtle.prime();
        turtle.prime();
        turtle.prime();

        verify(world, times(1)).getBlock(anyInt(), anyInt(), anyInt());
    }

    @Test
    void primingDoesNothingWithoutATurtleBlock() {
        World world = mock(World.class);
        TestTurtle turtle = new TestTurtle();
        turtle.setWorld(world);

        turtle.prime();

        assertFalse(
            turtle.brain()
                .hasFamily(),
            "an unloaded block must not be guessed at");
        verify(world, never()).getBlockMetadata(anyInt(), anyInt(), anyInt());
    }

    // =========================================================================
    // Fallback
    // =========================================================================

    @Test
    void anUnprimedTurtleStillAnswersFromTheWorld() {
        World world = worldWithTurtle(ComputerFamily.Advanced);
        TestTurtle turtle = new TestTurtle();
        turtle.setWorld(world);

        assertEquals(
            ComputerFamily.Advanced,
            turtle.brain()
                .getFamily());
    }

    private static World worldWithTurtle(ComputerFamily family) {
        // BlockComputerBase#getFamily(world, x, y, z) is final, so it cannot be stubbed: stub the metadata
        // overload it delegates to instead, and let the final one run for real.
        BlockTurtle block = mock(BlockTurtle.class);
        when(block.getFamily(anyInt())).thenReturn(family);

        World world = mock(World.class);
        when(world.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(world.getBlockMetadata(anyInt(), anyInt(), anyInt())).thenReturn(0);
        return world;
    }
}
