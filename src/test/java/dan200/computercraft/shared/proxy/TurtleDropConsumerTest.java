package dan200.computercraft.shared.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.util.Colour;
import dan200.computercraft.shared.util.IEntityDropConsumer;

/**
 * Regression tests for the entity drop-capture path in {@link CCTurtleProxyCommon}.
 *
 * <p>
 * {@code Entity#captureDrops} and {@code Entity#capturedDrops} are public fields that
 * {@code CCTurtleProxyCommon} reads and writes directly. While {@code captureDrops} is set,
 * {@code Entity#entityDropItem} diverts spawned {@code EntityItem}s into {@code capturedDrops}
 * instead of the world, and the registered consumer is handed each of them instead. This is what
 * stops turtle tools from leaving loose items on the ground.
 * </p>
 *
 * <p>
 * These tests pin the guard semantics rather than the reflection mechanism:
 * </p>
 * <ul>
 * <li>ComputerCraft must never take over a capture that is already enabled — that would redirect
 * another mod's drops.</li>
 * <li>ComputerCraft must not claim drops that were already captured when it was asked to, since
 * they belong to whoever enabled capture first.</li>
 * <li>Clearing must be safe for entities ComputerCraft does not manage, and for a null
 * {@code capturedDrops}.</li>
 * </ul>
 *
 * <p>
 * The map backing this path is a {@link java.util.WeakHashMap} keyed by {@code Entity}, so every
 * entity used by a test is held in {@link #keepAlive} for the duration of the test. Real entities
 * are required rather than mocks: {@code Entity#equals} compares {@code entityId}, and an
 * uninitialized mock would report {@code 0} for every instance, collapsing all of them into a
 * single map key.
 * </p>
 */
class TurtleDropConsumerTest {

    /**
     * Strong references to every entity handed to the proxy, so the proxy's weak-keyed map cannot
     * drop an entry mid-test.
     */
    private final List<Entity> keepAlive = new ArrayList<>();

    private CCTurtleProxyCommon proxy;

    @BeforeEach
    void setUp() {
        proxy = new CCTurtleProxyCommon() {

            @Override
            public void getTurtleModelTextures(List<ResourceLocation> list, ComputerFamily family, Colour colour) {
                // Client-only render hook, implemented by CCTurtleProxyClient. Unrelated to drop
                // capture: ICCTurtleProxy declares it but CCTurtleProxyCommon leaves it to the
                // client subclass.
            }
        };
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * A concrete {@link Entity} with no world. The {@code Entity(World)} constructor only
     * dereferences a non-null world for the provider lookup, and none of the accessors exercised
     * here touch {@code worldObj}.
     */
    private static final class TestEntity extends Entity {

        TestEntity() {
            super(null);
        }

        @Override
        protected void entityInit() {}

        @Override
        protected void readEntityFromNBT(NBTTagCompound tag) {}

        @Override
        protected void writeEntityToNBT(NBTTagCompound tag) {}
    }

    /** A real entity, retained in {@link #keepAlive}. */
    private Entity newEntity() {
        Entity entity = new TestEntity();
        keepAlive.add(entity);
        return entity;
    }

    /**
     * A payload stack tagged with {@code marker} as its damage value, so a delivered drop can be
     * traced back to the {@code EntityItem} that produced it. {@code ItemStack} is final and cannot
     * be mocked, and its only public constructors require a non-null {@link Item}.
     */
    private static ItemStack payload(int marker) {
        return new ItemStack(new Item(), 1, marker);
    }

    /** A real dropped item carrying {@code stack}. */
    private static EntityItem newDrop(ItemStack stack) {
        return new EntityItem(null, 0, 0, 0, stack);
    }

    /** Records the payloads a drop consumer was handed, in delivery order. */
    private static final class RecordingConsumer implements IEntityDropConsumer {

        final List<ItemStack> received = new ArrayList<>();

        @Override
        public void consumeDrop(Entity entity, ItemStack stack) {
            received.add(stack);
        }
    }

    // -------------------------------------------------------------------------
    // setEntityDropConsumer
    // -------------------------------------------------------------------------

    @Test
    void setEntityDropConsumerEnablesCaptureOnAnUnclaimedEntity() {
        Entity entity = newEntity();
        entity.capturedDrops.add(newDrop(payload(1)));

        proxy.setEntityDropConsumer(entity, new RecordingConsumer());

        assertTrue(entity.captureDrops, "ComputerCraft should enable capture on the entity");
    }

    @Test
    void setEntityDropConsumerDeclinesWhenAnotherModAlreadyCaptures() {
        Entity entity = newEntity();
        entity.captureDrops = true;
        entity.capturedDrops.add(newDrop(payload(1)));

        proxy.setEntityDropConsumer(entity, new RecordingConsumer());

        // The capture belongs to whoever enabled it first, so it must be left entirely alone.
        proxy.clearEntityDropConsumer(entity);
        assertTrue(entity.captureDrops, "a foreign capture must not be disabled by clearEntityDropConsumer");
        assertEquals(1, entity.capturedDrops.size(), "drops captured by another mod must be left alone");
    }

    @Test
    void setEntityDropConsumerDoesNotStealACaptureThatHasNotDroppedYet() {
        Entity entity = newEntity();
        RecordingConsumer consumer = new RecordingConsumer();
        // Another mod has enabled capture but has not dropped anything. The list is empty, so
        // this state is only distinguishable from "unclaimed" by the captureDrops flag itself:
        // claiming it would redirect that mod's future drops and disable its capture on clear.
        entity.captureDrops = true;

        proxy.setEntityDropConsumer(entity, consumer);

        proxy.clearEntityDropConsumer(entity);
        assertTrue(consumer.received.isEmpty(), "no consumer should be registered for a foreign capture");
        assertTrue(entity.captureDrops, "a foreign capture must survive clearEntityDropConsumer");
    }

    @Test
    void setEntityDropConsumerDoesNotClaimAlreadyCapturedDrops() {
        Entity entity = newEntity();
        RecordingConsumer consumer = new RecordingConsumer();
        // Capture is off, but the list already holds a drop: whoever filled it owns it, so
        // ComputerCraft must not register itself as the consumer for it.
        entity.capturedDrops.add(newDrop(payload(1)));

        proxy.setEntityDropConsumer(entity, consumer);

        proxy.clearEntityDropConsumer(entity);
        assertTrue(
            consumer.received.isEmpty(),
            "pre-existing captured drops must not be delivered to a newly added consumer");
        assertEquals(1, entity.capturedDrops.size(), "pre-existing captured drops must not be consumed");
    }

    @Test
    void setEntityDropConsumerKeepsTheFirstConsumerForAnEntity() {
        Entity entity = newEntity();
        RecordingConsumer first = new RecordingConsumer();
        RecordingConsumer second = new RecordingConsumer();
        ItemStack stack = payload(1);

        proxy.setEntityDropConsumer(entity, first);
        entity.capturedDrops.add(newDrop(stack));
        proxy.setEntityDropConsumer(entity, second);

        proxy.clearEntityDropConsumer(entity);
        assertEquals(1, first.received.size(), "the first registered consumer should receive the drops");
        assertSame(stack, first.received.get(0), "the first consumer should receive the captured drop");
        assertTrue(second.received.isEmpty(), "a repeat registration must not replace the existing consumer");
    }

    @Test
    void setEntityDropConsumerDoesNotReplaceAClaimedEntityWhoseCaptureWasDisabled() {
        Entity entity = newEntity();
        RecordingConsumer first = new RecordingConsumer();
        RecordingConsumer second = new RecordingConsumer();

        proxy.setEntityDropConsumer(entity, first);
        // Something outside ComputerCraft turned capture back off without going through
        // clearEntityDropConsumer. The entity is still claimed, so the registration guard — not
        // the captureDrops flag — is what must stop the second consumer taking it over.
        entity.captureDrops = false;
        proxy.setEntityDropConsumer(entity, second);

        // Re-enable capture so clear() actually dispatches, then see which consumer owns the drops.
        entity.captureDrops = true;
        entity.capturedDrops.add(newDrop(payload(1)));
        proxy.clearEntityDropConsumer(entity);

        assertEquals(1, first.received.size(), "the first consumer should still own the entity");
        assertTrue(second.received.isEmpty(), "a repeat registration must not replace the existing consumer");
    }

    // -------------------------------------------------------------------------
    // clearEntityDropConsumer
    // -------------------------------------------------------------------------

    @Test
    void clearEntityDropConsumerDeliversCapturedDropsAndRestoresNormalDropping() {
        Entity entity = newEntity();
        RecordingConsumer consumer = new RecordingConsumer();
        ItemStack first = payload(1);
        ItemStack second = payload(2);

        proxy.setEntityDropConsumer(entity, consumer);
        entity.capturedDrops.add(newDrop(first));
        entity.capturedDrops.add(newDrop(second));

        proxy.clearEntityDropConsumer(entity);

        assertFalse(entity.captureDrops, "capture should be disabled once the consumer is cleared");
        assertEquals(2, consumer.received.size(), "every captured drop should be delivered exactly once");
        assertSame(first, consumer.received.get(0), "drops should be delivered in capture order");
        assertSame(second, consumer.received.get(1), "drops should be delivered in capture order");
        assertTrue(entity.capturedDrops.isEmpty(), "the captured drop list should be emptied");
    }

    @Test
    void clearEntityDropConsumerIgnoresEntitiesItDoesNotManage() {
        Entity entity = newEntity();
        entity.captureDrops = true;
        entity.capturedDrops.add(newDrop(payload(1)));

        proxy.clearEntityDropConsumer(entity);

        assertTrue(entity.captureDrops, "an unmanaged entity's capture flag must not be cleared");
        assertEquals(1, entity.capturedDrops.size(), "an unmanaged entity's drops must be left alone");
    }

    @Test
    void clearEntityDropConsumerToleratesNullCapturedDrops() {
        Entity entity = newEntity();
        RecordingConsumer consumer = new RecordingConsumer();
        proxy.setEntityDropConsumer(entity, consumer);

        // capturedDrops is a public field, so a third-party mod can null it out from under us.
        entity.capturedDrops = null;
        proxy.clearEntityDropConsumer(entity);

        assertFalse(entity.captureDrops, "capture should still be disabled");
        assertTrue(consumer.received.isEmpty());
    }

    @Test
    void clearEntityDropConsumerIsSafeToCallTwice() {
        Entity entity = newEntity();
        RecordingConsumer consumer = new RecordingConsumer();
        proxy.setEntityDropConsumer(entity, consumer);
        entity.capturedDrops.add(newDrop(payload(1)));

        proxy.clearEntityDropConsumer(entity);
        proxy.clearEntityDropConsumer(entity);

        assertEquals(1, consumer.received.size(), "drops must not be delivered twice");
        assertFalse(entity.captureDrops);
    }
}
