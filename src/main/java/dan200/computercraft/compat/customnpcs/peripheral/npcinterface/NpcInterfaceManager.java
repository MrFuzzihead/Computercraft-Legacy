package dan200.computercraft.compat.customnpcs.peripheral.npcinterface;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry that routes CustomNPCs events to {@link INpcInterfaceHolder} instances
 * linked to the same NPC UUID.
 *
 * <p>
 * Thread-safe: {@link NpcInterfaceBridge} may call dispatch methods from Forge's event
 * thread while tiles register/unregister from the main server thread.
 * </p>
 */
public final class NpcInterfaceManager {

    /** UUID string → set of holders linked to that NPC. */
    private static final Map<String, Set<INpcInterfaceHolder>> BY_UUID = new ConcurrentHashMap<>();

    private NpcInterfaceManager() {}

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    public static void register(String npcUUID, INpcInterfaceHolder holder) {
        BY_UUID.computeIfAbsent(npcUUID, k -> Collections.synchronizedSet(new HashSet<>()))
            .add(holder);
    }

    public static void unregister(String npcUUID, INpcInterfaceHolder holder) {
        // compute() holds the CHM bin-lock for the duration of the lambda, making
        // the remove-then-check-empty atomic with respect to concurrent register() calls.
        // Returning null from the lambda removes the key from the map.
        BY_UUID.compute(npcUUID, (k, set) -> {
            if (set == null) return null;
            set.remove(holder);
            return set.isEmpty() ? null : set;
        });
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if at least one {@link INpcInterfaceHolder} is currently
     * registered for {@code npcUUID}.
     *
     * <p>
     * Used by {@link NpcInterfaceBridge} to skip tick-counter bookkeeping for NPCs
     * that have no linked interface, keeping {@code m_tickCounters} bounded.
     * </p>
     */
    public static boolean hasListeners(String npcUUID) {
        return BY_UUID.containsKey(npcUUID);
    }

    // -------------------------------------------------------------------------
    // Dispatch
    // -------------------------------------------------------------------------

    private static void dispatch(String npcUUID, String event, Object... params) {
        Set<INpcInterfaceHolder> set = BY_UUID.get(npcUUID);
        if (set == null) return;
        Set<INpcInterfaceHolder> snapshot;
        synchronized (set) {
            snapshot = new HashSet<>(set);
        }
        for (INpcInterfaceHolder holder : snapshot) {
            holder.queueNpcEvent(event, params);
        }
    }

    public static void dispatchInteract(String uuid, String playerName) {
        dispatch(uuid, "npc_interact", playerName);
    }

    public static void dispatchDialog(String uuid, String playerName, int dialogId, int optionId) {
        dispatch(uuid, "npc_dialog", playerName, dialogId, optionId);
    }

    public static void dispatchDialogClosed(String uuid, String playerName, int dialogId, int optionId) {
        dispatch(uuid, "npc_dialog_closed", playerName, dialogId, optionId);
    }

    public static void dispatchDamaged(String uuid, String sourceName, double damage, String damageType) {
        dispatch(uuid, "npc_damaged", sourceName, damage, damageType);
    }

    public static void dispatchDied(String uuid, String killerName, String damageType) {
        dispatch(uuid, "npc_died", killerName, damageType);
    }

    public static void dispatchTarget(String uuid, String targetName) {
        dispatch(uuid, "npc_target", targetName);
    }

    public static void dispatchTargetLost(String uuid) {
        dispatch(uuid, "npc_target_lost");
    }

    public static void dispatchTick(String uuid) {
        dispatch(uuid, "npc_tick");
    }
}
