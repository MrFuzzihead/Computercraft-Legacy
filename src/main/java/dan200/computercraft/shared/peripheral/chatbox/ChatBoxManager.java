package dan200.computercraft.shared.peripheral.chatbox;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    public static void dispatchChat(String playerName, String message) {
        Set<IChatBoxReceiver> snapshot;
        synchronized (RECEIVERS) {
            snapshot = new HashSet<>(RECEIVERS);
        }
        for (IChatBoxReceiver r : snapshot) {
            r.onChatEvent(playerName, message);
        }
    }

    public static void dispatchDeath(String player, String killer, String damageType) {
        Set<IChatBoxReceiver> snapshot;
        synchronized (RECEIVERS) {
            snapshot = new HashSet<>(RECEIVERS);
        }
        for (IChatBoxReceiver r : snapshot) {
            r.onDeathEvent(player, killer, damageType);
        }
    }

    public static void dispatchCommand(String player, Map<Integer, String> arguments) {
        Set<IChatBoxReceiver> snapshot;
        synchronized (RECEIVERS) {
            snapshot = new HashSet<>(RECEIVERS);
        }
        for (IChatBoxReceiver r : snapshot) {
            r.onCommandEvent(player, arguments);
        }
    }

    // -------------------------------------------------------------------------
    // CustomNPCs integration dispatchers
    // -------------------------------------------------------------------------

    public static void dispatchNpcInteract(String playerName, String npcName) {
        Set<IChatBoxReceiver> snapshot;
        synchronized (RECEIVERS) {
            snapshot = new HashSet<>(RECEIVERS);
        }
        for (IChatBoxReceiver r : snapshot) {
            r.onNpcInteractEvent(playerName, npcName);
        }
    }

    public static void dispatchNpcDialog(String playerName, String npcName, int dialogId, int optionId) {
        Set<IChatBoxReceiver> snapshot;
        synchronized (RECEIVERS) {
            snapshot = new HashSet<>(RECEIVERS);
        }
        for (IChatBoxReceiver r : snapshot) {
            r.onNpcDialogEvent(playerName, npcName, dialogId, optionId);
        }
    }

    public static void dispatchNpcDialogClosed(String playerName, String npcName, int dialogId, int optionId) {
        Set<IChatBoxReceiver> snapshot;
        synchronized (RECEIVERS) {
            snapshot = new HashSet<>(RECEIVERS);
        }
        for (IChatBoxReceiver r : snapshot) {
            r.onNpcDialogClosedEvent(playerName, npcName, dialogId, optionId);
        }
    }

    public static void dispatchNpcDied(String npcName, String killerName, String damageType) {
        Set<IChatBoxReceiver> snapshot;
        synchronized (RECEIVERS) {
            snapshot = new HashSet<>(RECEIVERS);
        }
        for (IChatBoxReceiver r : snapshot) {
            r.onNpcDiedEvent(npcName, killerName, damageType);
        }
    }

    public static void dispatchNpcSpawned(String npcName) {
        Set<IChatBoxReceiver> snapshot;
        synchronized (RECEIVERS) {
            snapshot = new HashSet<>(RECEIVERS);
        }
        for (IChatBoxReceiver r : snapshot) {
            r.onNpcSpawnedEvent(npcName);
        }
    }

    public static void dispatchNpcDamaged(String npcName, String attackerName, float damage, String damageType) {
        Set<IChatBoxReceiver> snapshot;
        synchronized (RECEIVERS) {
            snapshot = new HashSet<>(RECEIVERS);
        }
        for (IChatBoxReceiver r : snapshot) {
            r.onNpcDamagedEvent(npcName, attackerName, damage, damageType);
        }
    }

    public static void dispatchNpcKilledEntity(String npcName, String entityName, String entityType) {
        Set<IChatBoxReceiver> snapshot;
        synchronized (RECEIVERS) {
            snapshot = new HashSet<>(RECEIVERS);
        }
        for (IChatBoxReceiver r : snapshot) {
            r.onNpcKilledEntityEvent(npcName, entityName, entityType);
        }
    }
}
