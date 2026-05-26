package dan200.computercraft.shared.peripheral.chatbox;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Singleton registry that tracks all live {@link TileChatBox} instances and fans
 * incoming Forge events out to every attached computer as Lua events.
 */
public final class ChatBoxManager {

    private static final Set<TileChatBox> TILES = Collections.synchronizedSet(new HashSet<>());

    private ChatBoxManager() {}

    // -------------------------------------------------------------------------
    // Tile registry
    // -------------------------------------------------------------------------

    public static void register(TileChatBox tile) {
        TILES.add(tile);
    }

    public static void unregister(TileChatBox tile) {
        TILES.remove(tile);
    }

    // -------------------------------------------------------------------------
    // Event fan-out
    // -------------------------------------------------------------------------

    /**
     * Queues a {@code "chat"} event on all attached computers.
     *
     * @param playerName the name of the player who sent the message
     * @param message    the chat message text
     */
    public static void dispatchChat(String playerName, String message) {
        Set<TileChatBox> snapshot;
        synchronized (TILES) {
            snapshot = new HashSet<>(TILES);
        }
        Object[] args = new Object[] { "chat", playerName, message };
        for (TileChatBox tile : snapshot) {
            tile.queueEvent(args);
        }
    }

    /**
     * Queues a {@code "death"} event on all attached computers.
     *
     * @param player     the name of the player who died
     * @param killer     the name of the killer (empty string if none)
     * @param damageType the damage source type string
     */
    public static void dispatchDeath(String player, String killer, String damageType) {
        Set<TileChatBox> snapshot;
        synchronized (TILES) {
            snapshot = new HashSet<>(TILES);
        }
        Object[] args = new Object[] { "death", player, killer, damageType };
        for (TileChatBox tile : snapshot) {
            tile.queueEvent(args);
        }
    }

    /**
     * Queues a {@code "command"} event on all attached computers.
     *
     * @param player    the name of the player who issued the command
     * @param arguments 1-based Lua table mapping index → token (index 1 = command name)
     */
    public static void dispatchCommand(String player, Map<Integer, String> arguments) {
        Set<TileChatBox> snapshot;
        synchronized (TILES) {
            snapshot = new HashSet<>(TILES);
        }
        Object[] args = new Object[] { "command", player, arguments };
        for (TileChatBox tile : snapshot) {
            tile.queueEvent(args);
        }
    }
}
