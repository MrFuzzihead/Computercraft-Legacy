package dan200.computercraft.shared.peripheral.chatbox;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Singleton registry that tracks all live {@link IChatBoxReceiver} instances
 * (tile blocks, turtle upgrades, pocket computer upgrades) and fans incoming
 * Forge events out to every attached computer as Lua events.
 */
public final class ChatBoxManager {

    private static final Set<IChatBoxReceiver> RECEIVERS = Collections.synchronizedSet(new HashSet<>());

    private ChatBoxManager() {}

    public static void register(IChatBoxReceiver receiver) {
        RECEIVERS.add(receiver);
    }

    public static void unregister(IChatBoxReceiver receiver) {
        RECEIVERS.remove(receiver);
    }

    /**
     * Snapshots the receiver set and invokes {@code action} on every receiver.
     * Taking a snapshot before iteration means receivers can safely
     * register/unregister during dispatch without causing
     * {@link java.util.ConcurrentModificationException}.
     */
    private static void dispatch(Consumer<IChatBoxReceiver> action) {
        Set<IChatBoxReceiver> snapshot;
        synchronized (RECEIVERS) {
            snapshot = new HashSet<>(RECEIVERS);
        }
        for (IChatBoxReceiver r : snapshot) {
            action.accept(r);
        }
    }

    public static void dispatchChat(String playerName, String message) {
        dispatch(r -> r.onChatEvent(playerName, message));
    }

    public static void dispatchDeath(String player, String killer, String damageType) {
        dispatch(r -> r.onDeathEvent(player, killer, damageType));
    }

    public static void dispatchCommand(String player, Map<Integer, String> arguments) {
        dispatch(r -> r.onCommandEvent(player, arguments));
    }

    // -------------------------------------------------------------------------
    // CustomNPCs integration dispatchers
    // -------------------------------------------------------------------------

    public static void dispatchNpcInteract(String playerName, String npcName) {
        dispatch(r -> r.onNpcInteractEvent(playerName, npcName));
    }

    public static void dispatchNpcDialog(String playerName, String npcName, int dialogId, int optionId) {
        dispatch(r -> r.onNpcDialogEvent(playerName, npcName, dialogId, optionId));
    }

    public static void dispatchNpcDialogClosed(String playerName, String npcName, int dialogId, int optionId) {
        dispatch(r -> r.onNpcDialogClosedEvent(playerName, npcName, dialogId, optionId));
    }

    public static void dispatchNpcDied(String npcName, String killerName, String damageType) {
        dispatch(r -> r.onNpcDiedEvent(npcName, killerName, damageType));
    }

    public static void dispatchNpcSpawned(String npcName) {
        dispatch(r -> r.onNpcSpawnedEvent(npcName));
    }

    public static void dispatchNpcDamaged(String npcName, String attackerName, float damage, String damageType) {
        dispatch(r -> r.onNpcDamagedEvent(npcName, attackerName, damage, damageType));
    }

    public static void dispatchNpcKilledEntity(String npcName, String entityName, String entityType) {
        dispatch(r -> r.onNpcKilledEntityEvent(npcName, entityName, entityType));
    }
}
