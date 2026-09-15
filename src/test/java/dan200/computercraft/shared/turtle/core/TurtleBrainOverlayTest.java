package dan200.computercraft.shared.turtle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.junit.jupiter.api.Test;

import dan200.computercraft.shared.turtle.blocks.TileTurtle;

/**
 * Unit tests for {@link TurtleBrain#setOverlay(ResourceLocation, ResourceLocation)}
 * (finding B3 in {@code docs/CODEBASE_ANALYSIS.md}).
 *
 * <p>
 * The change detection used to compare the hat-overlay *field* against the
 * body-overlay *argument*, so:
 * </p>
 *
 * <ul>
 * <li>an identical re-set still fired a spurious {@code updateBlock()} whenever
 * the two overlay textures differ, and</li>
 * <li>a hat-only change was silently dropped when both fields already equalled
 * the new body overlay.</li>
 * </ul>
 *
 * <p>
 * {@code TileGeneric.updateBlock()} is final, so the tests use a real
 * {@link TileTurtle} whose {@code worldObj} is a Mockito mock of
 * {@link World}: block updates become verifiable as
 * {@code World.markBlockForUpdate} invocations.
 * </p>
 */
class TurtleBrainOverlayTest {

    private static TileTurtle newTurtle(World world) {
        TileTurtle turtle = new TileTurtle();
        try {
            Field worldField = TileEntity.class.getDeclaredField("worldObj");
            worldField.setAccessible(true);
            worldField.set(turtle, world);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not inject world into tile", e);
        }

        turtle.xCoord = 1;
        turtle.yCoord = 2;
        turtle.zCoord = 3;
        return turtle;
    }

    @Test
    void changedValuesUpdateFieldsAndBlock() {
        World world = mock(World.class);
        TurtleBrain brain = new TurtleBrain(newTurtle(world));
        ResourceLocation overlay = new ResourceLocation("computercraft", "overlay");
        ResourceLocation hatOverlay = new ResourceLocation("computercraft", "hat");

        brain.setOverlay(overlay, hatOverlay);

        assertEquals(overlay, brain.getOverlay());
        assertEquals(hatOverlay, brain.getHatOverlay());
        verify(world, times(1)).markBlockForUpdate(1, 2, 3);
    }

    @Test
    void identicalResetIsANoOp() {
        World world = mock(World.class);
        TurtleBrain brain = new TurtleBrain(newTurtle(world));
        ResourceLocation overlay = new ResourceLocation("computercraft", "overlay");
        ResourceLocation hatOverlay = new ResourceLocation("computercraft", "hat");

        brain.setOverlay(overlay, hatOverlay);
        verify(world, times(1)).markBlockForUpdate(1, 2, 3);

        // Regression: with the B3 bug this still fired a second, spurious
        // updateBlock() because the hat field was compared against the
        // body-overlay argument.
        brain.setOverlay(overlay, hatOverlay);

        verify(world, times(1)).markBlockForUpdate(1, 2, 3);
        assertEquals(overlay, brain.getOverlay());
        assertEquals(hatOverlay, brain.getHatOverlay());
    }

    @Test
    void hatOnlyChangeIsApplied() {
        World world = mock(World.class);
        TurtleBrain brain = new TurtleBrain(newTurtle(world));
        ResourceLocation same = new ResourceLocation("computercraft", "same");

        brain.setOverlay(same, same);
        verify(world, times(1)).markBlockForUpdate(1, 2, 3);

        // Regression: with the B3 bug the whole condition was false here (both
        // fields already equalled the body-overlay argument), so the new hat
        // was silently dropped.
        ResourceLocation newHat = new ResourceLocation("computercraft", "newhat");
        brain.setOverlay(same, newHat);

        assertEquals(newHat, brain.getHatOverlay(), "a hat-only change must be applied");
        assertEquals(same, brain.getOverlay());
        verify(world, times(2)).markBlockForUpdate(1, 2, 3);
    }
}
