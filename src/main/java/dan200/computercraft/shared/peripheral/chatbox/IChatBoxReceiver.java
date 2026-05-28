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
}
