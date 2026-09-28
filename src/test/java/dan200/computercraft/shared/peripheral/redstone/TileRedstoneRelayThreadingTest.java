package dan200.computercraft.shared.peripheral.redstone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.block.Block;
import net.minecraft.util.Facing;
import net.minecraft.world.World;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.shared.util.DirectionUtil;

/**
 * Regression tests for main-thread ownership of the Redstone Relay's world updates.
 *
 * <p>
 * {@code setOutput} and {@code setBundledOutput} are reached from {@link RedstoneRelayPeripheral},
 * so they run on a computer worker thread. They must not touch the world: neighbour notification
 * and {@code markDirty} both reach into {@link World} and belong on the server main thread. The
 * relay already did this for input via its dirty flag; these tests pin the output side, which
 * previously propagated inline.
 * </p>
 *
 * <p>
 * The tell-tale for the old behaviour is {@code World#notifyBlockOfNeighborChange}, reached from
 * {@code RedstoneUtil#propogateRedstoneOutput}. Every test here asserts it is absent until
 * {@link TileRedstoneRelay#updateEntity()} runs and present afterwards, so restoring inline
 * propagation fails these tests rather than passing quietly.
 * </p>
 */
class TileRedstoneRelayThreadingTest {

    /** A local side index (0-5) in the relay's own side ordering. */
    private static final int LOCAL_SIDE = 2;

    private TileRedstoneRelay tile;
    private World world;

    @BeforeEach
    void setUp() {
        tile = new TileRedstoneRelay();
        world = mock(World.class);
        // Return a block for every coordinate so RedstoneUtil reaches the notification call. A
        // mock's isNormalCube is false, so only notifyBlockOfNeighborChange fires, which keeps the
        // verification to a single signal.
        when(world.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(mock(Block.class));
        // isRemote is a public field, not a method, so it is assigned rather than stubbed.
        world.isRemote = false;
        tile.setWorldObj(world);
        tile.xCoord = 10;
        tile.yCoord = 64;
        tile.zCoord = 10;
    }

    /** The world-facing side that maps to {@code localSide} through the relay's own mapping. */
    private int worldSideFor(int localSide) {
        for (int worldSide = 0; worldSide < 6; worldSide++) {
            if (DirectionUtil.toLocal(tile, worldSide) == localSide) {
                return worldSide;
            }
        }
        throw new AssertionError("no world side maps to local side " + localSide);
    }

    private void assertWorldNotNotified() {
        verify(world, never()).notifyBlockOfNeighborChange(anyInt(), anyInt(), anyInt(), any(Block.class));
    }

    // Setters must not touch the world.

    @Test
    void setOutputDoesNotTouchTheWorld() {
        tile.setOutput(LOCAL_SIDE, 15);
        assertWorldNotNotified();
    }

    @Test
    void setBundledOutputDoesNotTouchTheWorld() {
        tile.setBundledOutput(LOCAL_SIDE, 0x0F);
        assertWorldNotNotified();
    }

    @Test
    void settersCalledFromAWorkerThreadDoNotTouchTheWorld() throws Exception {
        // This is the real shape of the bug: the setters run on a computer worker, not the
        // server thread, so the world must stay untouched until the main thread ticks.
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try {
                tile.setOutput(LOCAL_SIDE, 15);
                tile.setBundledOutput(LOCAL_SIDE, 0x03);
            } catch (Throwable t) {
                thrown.set(t);
            } finally {
                done.countDown();
            }
        }, "fake-computer-worker");
        worker.start();
        assertTrue(done.await(10, TimeUnit.SECONDS), "worker thread did not finish");
        worker.join();

        assertNull(thrown.get(), "setters must not fail when called off the main thread");
        assertWorldNotNotified();
    }

    // Propagation still happens, on the tick.

    @Test
    void updateEntityPropagatesAPendingOutputChange() {
        tile.setOutput(LOCAL_SIDE, 15);
        tile.updateEntity();

        verify(world, atLeastOnce()).notifyBlockOfNeighborChange(anyInt(), anyInt(), anyInt(), any(Block.class));
    }

    @Test
    void updateEntityPropagatesAPendingBundledOutputChange() {
        tile.setBundledOutput(LOCAL_SIDE, 0x0F);
        tile.updateEntity();

        verify(world, atLeastOnce()).notifyBlockOfNeighborChange(anyInt(), anyInt(), anyInt(), any(Block.class));
    }

    @Test
    void aTickWithNothingPendingDoesNotNotify() {
        tile.updateEntity();
        assertWorldNotNotified();
    }

    @Test
    void pendingPropagationIsNotRepeatedOnLaterTicks() {
        tile.setOutput(LOCAL_SIDE, 15);
        tile.updateEntity();
        verify(world, atLeastOnce()).notifyBlockOfNeighborChange(anyInt(), anyInt(), anyInt(), any(Block.class));
        clearInvocations(world);

        // The flag is consumed by the first tick, so later idle ticks stay silent.
        tile.updateEntity();
        assertWorldNotNotified();
    }

    @Test
    void aNoOpSetDoesNotSchedulePropagation() {
        // Level 0 is the initial value, so setting it changes nothing and must not notify.
        tile.setOutput(LOCAL_SIDE, 0);
        tile.updateEntity();
        assertWorldNotNotified();
    }

    @Test
    void severalChangesToOneSideNotifyOncePerTick() {
        // A chatty Lua program must not be able to flood the world with duplicate notifications.
        tile.setOutput(LOCAL_SIDE, 15);
        tile.setOutput(LOCAL_SIDE, 0);
        tile.updateEntity();
        clearInvocations(world);

        tile.setOutput(LOCAL_SIDE, 15);
        tile.setOutput(LOCAL_SIDE, 15);
        tile.updateEntity();
        verify(world, times(1)).notifyBlockOfNeighborChange(anyInt(), anyInt(), anyInt(), any(Block.class));
    }

    @Test
    void onlyTheChangedSideIsNotified() {
        // Pins per-side granularity: the old inline code notified just the side that changed, and
        // notifying all six instead would be a behaviour change (extra neighbour updates, and
        // visible on circuitry that reacts to them).
        int worldSide = worldSideFor(LOCAL_SIDE);
        int nx = 10 + Facing.offsetsXForSide[worldSide];
        int ny = 64 + Facing.offsetsYForSide[worldSide];
        int nz = 10 + Facing.offsetsZForSide[worldSide];

        tile.setOutput(LOCAL_SIDE, 15);
        tile.updateEntity();

        verify(world, times(1)).notifyBlockOfNeighborChange(eq(nx), eq(ny), eq(nz), any(Block.class));
        verify(world, times(1)).notifyBlockOfNeighborChange(anyInt(), anyInt(), anyInt(), any(Block.class));
    }

    @Test
    void twoChangedSidesAreBothNotified() {
        int first = worldSideFor(LOCAL_SIDE);
        int second = first == 0 ? worldSideFor(3) : worldSideFor(0);
        assertNotEquals(first, second, "the two sides under test must differ");

        tile.setOutput(LOCAL_SIDE, 15);
        tile.setOutput(DirectionUtil.toLocal(tile, second), 7);
        tile.updateEntity();

        verify(world, times(1)).notifyBlockOfNeighborChange(
            eq(10 + Facing.offsetsXForSide[first]),
            eq(64 + Facing.offsetsYForSide[first]),
            eq(10 + Facing.offsetsZForSide[first]),
            any(Block.class));
        verify(world, times(1)).notifyBlockOfNeighborChange(
            eq(10 + Facing.offsetsXForSide[second]),
            eq(64 + Facing.offsetsYForSide[second]),
            eq(10 + Facing.offsetsZForSide[second]),
            any(Block.class));
    }

    // The value itself is still written synchronously.

    @Test
    void theOutputValueIsReadableBeforeTheTick() {
        // Deferring the notification must not defer the value: callers may read it straight back
        // through the peripheral, and redstone comparisons read it on the main thread.
        tile.setOutput(LOCAL_SIDE, 15);

        assertEquals(15, tile.getOutput(LOCAL_SIDE), "getOutput must see the new value immediately");
        assertEquals(
            15,
            tile.getRedstoneOutput(worldSideFor(LOCAL_SIDE)),
            "getRedstoneOutput must see the new value immediately");
    }

    @Test
    void theBundledOutputValueIsReadableBeforeTheTick() {
        tile.setBundledOutput(LOCAL_SIDE, 0x0F);

        assertEquals(0x0F, tile.getBundledOutput(LOCAL_SIDE));
        assertEquals(0x0F, tile.getBundledRedstoneOutput(worldSideFor(LOCAL_SIDE)));
    }
}
