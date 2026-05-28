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
}
