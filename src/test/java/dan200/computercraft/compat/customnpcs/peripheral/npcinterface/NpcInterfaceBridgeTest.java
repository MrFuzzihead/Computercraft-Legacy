package dan200.computercraft.compat.customnpcs.peripheral.npcinterface;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the tick-throttle logic inside {@link NpcInterfaceBridge}.
 *
 * <p>
 * No running Minecraft world or CustomNPCs installation is required.
 * The Forge {@code @SubscribeEvent} handlers depend on {@code NpcEvent.*} types
 * from the CustomNPCs mod and are therefore not exercised directly. Instead,
 * the package-private helpers {@link NpcInterfaceBridge#handleTick},
 * {@link NpcInterfaceBridge#handleDied}, and {@link NpcInterfaceBridge#getTickCounter}
 * are called to cover all stateful behavior.
 * </p>
 *
 * <p>
 * Behaviours verified:
 * <ul>
 * <li>Counter increments on every tick and wraps mod 20.</li>
 * <li>{@code npc_tick} fires exactly on the 20th call and every 20 calls thereafter.</li>
 * <li>Different NPC UUIDs maintain independent counters.</li>
 * <li>{@link NpcInterfaceBridge#handleDied} removes the counter (memory reclaimed).</li>
 * <li>Calling {@code handleDied} for an unknown UUID is a safe no-op.</li>
 * <li>An NPC that despawns without dying leaves a stale but bounded entry.</li>
 * </ul>
 * </p>
 */
class NpcInterfaceBridgeTest {

    /** Stable test UUIDs — arbitrary strings, not true UUIDs. */
    private static final String UUID_A = "aaaaaaaa-0000-0000-0000-aaaaaaaaaaaa";
    private static final String UUID_B = "bbbbbbbb-0000-0000-0000-bbbbbbbbbbbb";

    private NpcInterfaceBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new NpcInterfaceBridge();
    }

    // =========================================================================
    // Tick counter — increment and wrap
    // =========================================================================

    @Test
    void firstTick_counterIsOne() {
        bridge.handleTick(UUID_A);
        assertEquals(1, bridge.getTickCounter(UUID_A));
    }

    @Test
    void fiveTicks_counterIsFive() {
        for (int i = 0; i < 5; i++) bridge.handleTick(UUID_A);
        assertEquals(5, bridge.getTickCounter(UUID_A));
    }

    @Test
    void twentiethTick_counterWrapsToZero() {
        for (int i = 0; i < 20; i++) bridge.handleTick(UUID_A);
        // After the 20th call the merge result is (19+1)%20 = 0
        assertEquals(0, bridge.getTickCounter(UUID_A));
    }

    @Test
    void twentyFirstTick_counterIsOne() {
        for (int i = 0; i < 21; i++) bridge.handleTick(UUID_A);
        assertEquals(1, bridge.getTickCounter(UUID_A));
    }

    // =========================================================================
    // Tick throttle — dispatch fires every 20 ticks
    // =========================================================================

    @Test
    void firstTick_doesNotFire() {
        assertFalse(bridge.handleTick(UUID_A), "tick 1 must not dispatch");
    }

    @Test
    void tick1Through19_noneDispatch() {
        for (int i = 1; i < 20; i++) {
            assertFalse(bridge.handleTick(UUID_A), "tick " + i + " must not dispatch");
        }
    }

    @Test
    void twentiethTick_fires() {
        for (int i = 0; i < 19; i++) bridge.handleTick(UUID_A);
        assertTrue(bridge.handleTick(UUID_A), "tick 20 must dispatch");
    }

    @Test
    void twentyFirstTick_doesNotFire() {
        for (int i = 0; i < 20; i++) bridge.handleTick(UUID_A);
        assertFalse(bridge.handleTick(UUID_A), "tick 21 must not dispatch");
    }

    @Test
    void fortiethTick_fires() {
        for (int i = 0; i < 39; i++) bridge.handleTick(UUID_A);
        assertTrue(bridge.handleTick(UUID_A), "tick 40 must dispatch");
    }

    @Test
    void sixtiethTick_fires() {
        for (int i = 0; i < 59; i++) bridge.handleTick(UUID_A);
        assertTrue(bridge.handleTick(UUID_A), "tick 60 must dispatch");
    }

    @Test
    void exactlyThreeDispatchesIn60Ticks() {
        int fired = 0;
        for (int i = 0; i < 60; i++) {
            if (bridge.handleTick(UUID_A)) fired++;
        }
        assertEquals(3, fired, "npc_tick must fire exactly 3 times in 60 ticks");
    }

    // =========================================================================
    // Independent counters per NPC UUID
    // =========================================================================

    @Test
    void differentNpcs_haveIndependentCounters() {
        // Tick A 20 times — should fire
        for (int i = 0; i < 20; i++) bridge.handleTick(UUID_A);
        // B has never been ticked — counter absent
        assertEquals(-1, bridge.getTickCounter(UUID_B));
    }

    @Test
    void differentNpcs_tickingOneDoesNotAffectOther() {
        // Advance A close to the firing point
        for (int i = 0; i < 19; i++) bridge.handleTick(UUID_A);
        // Tick B once — must not fire despite A being at 19
        assertFalse(bridge.handleTick(UUID_B), "B tick 1 must not dispatch");
        assertEquals(1, bridge.getTickCounter(UUID_B));
        assertEquals(19, bridge.getTickCounter(UUID_A));
    }

    @Test
    void differentNpcs_canFireIndependently() {
        // Fire A at tick 20
        for (int i = 0; i < 20; i++) bridge.handleTick(UUID_A);
        // Fire B at tick 20, independent of A
        for (int i = 0; i < 19; i++) bridge.handleTick(UUID_B);
        assertTrue(bridge.handleTick(UUID_B), "B tick 20 must dispatch independently of A");
    }

    // =========================================================================
    // handleDied — counter removed on NPC death
    // =========================================================================

    @Test
    void handleDied_removesCounter() {
        for (int i = 0; i < 5; i++) bridge.handleTick(UUID_A);
        assertEquals(5, bridge.getTickCounter(UUID_A));

        bridge.handleDied(UUID_A);

        assertEquals(-1, bridge.getTickCounter(UUID_A), "counter must be absent after death");
    }

    @Test
    void handleDied_unknownUuid_isNoOp() {
        // Must not throw for an NPC that was never ticked
        assertDoesNotThrow(() -> bridge.handleDied("unknown-uuid-xyz"));
    }

    @Test
    void handleDied_afterFullCycle_removesCounter() {
        // Advance through a full 20-tick cycle so the counter is at 0
        for (int i = 0; i < 20; i++) bridge.handleTick(UUID_A);
        assertEquals(0, bridge.getTickCounter(UUID_A));

        bridge.handleDied(UUID_A);

        assertEquals(-1, bridge.getTickCounter(UUID_A));
    }

    @Test
    void handleDied_onlyRemovesTargetNpc() {
        for (int i = 0; i < 7; i++) bridge.handleTick(UUID_A);
        for (int i = 0; i < 3; i++) bridge.handleTick(UUID_B);

        bridge.handleDied(UUID_A);

        assertEquals(-1, bridge.getTickCounter(UUID_A), "A counter must be removed");
        assertEquals(3, bridge.getTickCounter(UUID_B), "B counter must be untouched");
    }

    @Test
    void afterDeath_ticksRestartFromZero() {
        // Tick A for 19 ticks, then kill it
        for (int i = 0; i < 19; i++) bridge.handleTick(UUID_A);
        bridge.handleDied(UUID_A);

        // Ticking again must restart the counter fresh — 20 more needed before firing
        for (int i = 0; i < 19; i++) {
            assertFalse(bridge.handleTick(UUID_A), "tick " + (i + 1) + " after respawn must not dispatch");
        }
        assertTrue(bridge.handleTick(UUID_A), "tick 20 after respawn must dispatch");
    }

    // =========================================================================
    // Stale-entry leak — despawning NPCs (no death event)
    // =========================================================================

    @Test
    void staleDespawn_counterPersistsWhenTicksStop() {
        // Simulate an NPC that ticks 5 times then despawns (no death event fired)
        for (int i = 0; i < 5; i++) bridge.handleTick(UUID_A);

        // The entry is still present — leak is bounded (events simply stop arriving)
        assertEquals(5, bridge.getTickCounter(UUID_A), "stale counter must persist after despawn (bounded leak)");
    }

    @Test
    void staleDespawn_doesNotPreventOtherNpcsFromFiring() {
        // A despawns mid-cycle; B continues independently
        for (int i = 0; i < 10; i++) bridge.handleTick(UUID_A);
        // A stops; B now ticks to 20 — should fire normally
        for (int i = 0; i < 19; i++) bridge.handleTick(UUID_B);
        assertTrue(bridge.handleTick(UUID_B), "B must fire despite A having a stale entry");
        // A's stale counter is still 10
        assertEquals(10, bridge.getTickCounter(UUID_A));
    }
}
