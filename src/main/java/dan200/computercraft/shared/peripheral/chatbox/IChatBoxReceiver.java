package dan200.computercraft.shared.peripheral.chatbox;

import java.util.Map;

/**
 * Implemented by anything that can receive chat-box events (tile block,
 * turtle upgrade, pocket computer upgrade). {@link ChatBoxManager} dispatches
 * to all registered receivers.
 */
public interface IChatBoxReceiver {

    void onChatEvent(String playerName, String message);

    void onDeathEvent(String player, String killer, String damageType);

    void onCommandEvent(String player, Map<Integer, String> arguments);

    // -------------------------------------------------------------------------
    // CustomNPCs integration — default no-ops so existing implementors are safe
    // -------------------------------------------------------------------------

    default void onNpcInteractEvent(String playerName, String npcName) {}

    default void onNpcDialogEvent(String playerName, String npcName, int dialogId, int optionId) {}

    default void onNpcDialogClosedEvent(String playerName, String npcName, int dialogId, int optionId) {}

    default void onNpcDiedEvent(String npcName, String killerName, String damageType) {}

    default void onNpcSpawnedEvent(String npcName) {}

    default void onNpcDamagedEvent(String npcName, String attackerName, float damage, String damageType) {}

    default void onNpcKilledEntityEvent(String npcName, String entityName, String entityType) {}
}
