package dan200.computercraft.compat.customnpcs.peripheral.npcinterface;

import java.util.concurrent.ConcurrentHashMap;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dan200.computercraft.compat.customnpcs.chatbox.CustomNpcChatBoxBridge;
import noppes.npcs.scripted.event.NpcEvent;

/**
 * Forge event listener that forwards CustomNPCs NPC events to
 * {@link NpcInterfaceManager} for routing to linked {@link TileNpcInterface} tiles.
 *
 * <p>
 * <b>Important:</b> handlers must declare concrete {@code NpcEvent.*} parameter types,
 * not the {@code INpcEvent.*} interfaces. FML's EventBus walks the concrete class
 * hierarchy only and will never invoke a handler declared on an interface.
 * </p>
 *
 * <p>
 * Registered alongside {@link CustomNpcChatBoxBridge} in {@code ComputerCraft#registerCustomNpcCompat}.
 * </p>
 */
public class NpcInterfaceBridge {

    /**
     * Per-NPC tick counter used to throttle {@code npc_tick} events to every 20 ticks.
     *
     * <p>
     * Values are stored modulo 20 (range [0, 19]) to prevent integer overflow.
     * Entries are removed in {@link #onDied} to reclaim memory when an NPC dies.
     * NPCs that despawn without dying leave a stale entry, but since
     * {@link NpcEvent.UpdateEvent} stops firing for them the leak is bounded.
     * </p>
     */
    private final ConcurrentHashMap<String, Integer> m_tickCounters = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onInteract(NpcEvent.InteractEvent e) {
        NpcInterfaceManager.dispatchInteract(
            e.getNpc()
                .getUniqueID(),
            e.getPlayer()
                .getName());
    }

    @SubscribeEvent
    public void onDialog(NpcEvent.DialogEvent e) {
        NpcInterfaceManager.dispatchDialog(
            e.getNpc()
                .getUniqueID(),
            e.getPlayer()
                .getName(),
            e.getDialogId(),
            e.getOptionId());
    }

    @SubscribeEvent
    public void onDialogClosed(NpcEvent.DialogClosedEvent e) {
        NpcInterfaceManager.dispatchDialogClosed(
            e.getNpc()
                .getUniqueID(),
            e.getPlayer()
                .getName(),
            e.getDialogId(),
            e.getOptionId());
    }

    @SubscribeEvent
    public void onDamaged(NpcEvent.DamagedEvent e) {
        String srcName = CustomNpcChatBoxBridge.entityName(e.getSource());
        String srcType = e.getSource() != null ? CustomNpcChatBoxBridge.resolveKillerType(e.getSource(), e.getType())
            : "";
        NpcInterfaceManager.dispatchDamaged(
            e.getNpc()
                .getUniqueID(),
            srcName,
            e.getDamage(),
            srcType);
    }

    @SubscribeEvent
    public void onDied(NpcEvent.DiedEvent e) {
        String uuid = e.getNpc()
            .getUniqueID();
        String killerName = CustomNpcChatBoxBridge.entityName(e.getSource());
        String killerType = e.getSource() != null ? CustomNpcChatBoxBridge.resolveKillerType(e.getSource(), e.getType())
            : "";
        handleDied(uuid);
        NpcInterfaceManager.dispatchDied(uuid, killerName, killerType);
    }

    @SubscribeEvent
    public void onTarget(NpcEvent.TargetEvent e) {
        String name = e.getTarget() != null ? e.getTarget()
            .getTypeName() : "";
        NpcInterfaceManager.dispatchTarget(
            e.getNpc()
                .getUniqueID(),
            name);
    }

    @SubscribeEvent
    public void onTargetLost(NpcEvent.TargetLostEvent e) {
        NpcInterfaceManager.dispatchTargetLost(
            e.getNpc()
                .getUniqueID());
    }

    @SubscribeEvent
    public void onTick(NpcEvent.UpdateEvent e) {
        handleTick(
            e.getNpc()
                .getUniqueID());
    }

    // -------------------------------------------------------------------------
    // Package-private helpers — exposed for unit testing without CustomNPCs
    // -------------------------------------------------------------------------

    /**
     * Core tick-throttle logic.
     * Increments the per-NPC counter and dispatches {@code npc_tick} via
     * {@link NpcInterfaceManager} exactly once every 20 calls (when the counter
     * wraps to 0 modulo 20).
     *
     * @param uuid NPC unique-ID string
     * @return {@code true} if the tick event was dispatched this call
     */
    boolean handleTick(String uuid) {
        int count = m_tickCounters.merge(uuid, 1, (a, b) -> (a + b) % 20);
        if (count == 0) {
            NpcInterfaceManager.dispatchTick(uuid);
            return true;
        }
        return false;
    }

    /**
     * Removes the tick-counter entry for {@code uuid}.
     * Called by {@link #onDied} to reclaim memory when an NPC dies.
     *
     * @param uuid NPC unique-ID string
     */
    void handleDied(String uuid) {
        m_tickCounters.remove(uuid);
    }

    /**
     * Returns the current raw tick-counter value for {@code uuid}, or {@code -1}
     * if no entry exists (NPC has never ticked, or was removed via
     * {@link #handleDied}).
     *
     * @param uuid NPC unique-ID string
     * @return counter in [0, 19], or {@code -1} if absent
     */
    int getTickCounter(String uuid) {
        Integer v = m_tickCounters.get(uuid);
        return v != null ? v : -1;
    }
}
