package dan200.computercraft.shared.pocket;

import net.minecraft.item.ItemStack;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.pocket.peripherals.PocketChatBoxPeripheral;
import dan200.computercraft.shared.pocket.peripherals.PocketEnderModemPeripheral;
import dan200.computercraft.shared.pocket.peripherals.PocketModemPeripheral;
import dan200.computercraft.shared.pocket.peripherals.PocketNpcDetectorPeripheral;
import dan200.computercraft.shared.pocket.peripherals.PocketNpcInterfacePeripheral;
import dan200.computercraft.shared.pocket.peripherals.PocketSpeakerPeripheral;

/**
 * The pocket computer upgrade table: which NBT {@code upgrade} id means what, which item installs it,
 * and which peripheral it attaches.
 *
 * <p>
 * Before this existed the mapping lived in two places — the chain in
 * {@code ItemPocketComputer} that builds a computer from its NBT, and a lone wireless-modem case in
 * {@code PocketAPI.equipBack}. Keeping one table means {@code pocket.equipBack} can install anything
 * the pocket computer already supports, and the two paths cannot drift.
 * </p>
 *
 * <p>
 * The ids are on-disk NBT values: {@code 1} wireless modem, {@code 2} ender modem, {@code 3}
 * speaker, {@code 4} chat box, {@code 5} NPC detector, {@code 6} NPC interface. They must not be
 * renumbered.
 * </p>
 */
public final class PocketUpgrades {

    public static final int WIRELESS_MODEM = 1;
    public static final int ENDER_MODEM = 2;
    public static final int SPEAKER = 3;
    public static final int CHAT_BOX = 4;
    public static final int NPC_DETECTOR = 5;
    public static final int NPC_INTERFACE = 6;

    private PocketUpgrades() {}

    /**
     * The upgrade id an item installs.
     *
     * @param type the item's peripheral type
     * @return the upgrade id, or {@code -1} if this item is not a pocket upgrade
     */
    public static int upgradeFor(PeripheralType type) {
        if (type == null) return -1;

        switch (type) {
            case WirelessModem:
                return WIRELESS_MODEM;
            case AdvancedWirelessModem:
                return ENDER_MODEM;
            case Speaker:
                return SPEAKER;
            case ChatBox:
                return CHAT_BOX;
            case NpcDetector:
                return NPC_DETECTOR;
            case NpcInterface:
                return NPC_INTERFACE;
            default:
                return -1;
        }
    }

    /**
     * Whether an upgrade id is one this mod understands.
     *
     * @param upgrade the NBT upgrade id
     * @return whether it names a supported upgrade
     */
    public static boolean isUpgrade(int upgrade) {
        return upgrade >= WIRELESS_MODEM && upgrade <= NPC_INTERFACE;
    }

    /**
     * Builds the peripheral for an installed upgrade.
     *
     * <p>
     * The NPC interface reads its settings from the pocket computer's stack, so the stack is passed
     * in even though most upgrades ignore it.
     * </p>
     *
     * @param upgrade the NBT upgrade id
     * @param stack   the pocket computer's item stack
     * @return the peripheral, or {@code null} if the id is not a supported upgrade
     */
    public static IPeripheral createPeripheral(int upgrade, ItemStack stack) {
        switch (upgrade) {
            case WIRELESS_MODEM:
                return new PocketModemPeripheral();
            case ENDER_MODEM:
                return new PocketEnderModemPeripheral();
            case SPEAKER:
                return new PocketSpeakerPeripheral();
            case CHAT_BOX:
                return new PocketChatBoxPeripheral();
            case NPC_DETECTOR:
                return new PocketNpcDetectorPeripheral();
            case NPC_INTERFACE:
                return new PocketNpcInterfacePeripheral(stack);
            default:
                return null;
        }
    }

    /**
     * The human-readable name of an upgrade, for chat messages and item tooltips.
     *
     * @param upgrade the NBT upgrade id
     * @return the name, or {@code "unknown"} for an unrecognised id
     */
    public static String nameOf(int upgrade) {
        switch (upgrade) {
            case WIRELESS_MODEM:
                return "wireless modem";
            case ENDER_MODEM:
                return "ender modem";
            case SPEAKER:
                return "speaker";
            case CHAT_BOX:
                return "chat box";
            case NPC_DETECTOR:
                return "NPC detector";
            case NPC_INTERFACE:
                return "NPC interface";
            default:
                return "unknown";
        }
    }
}
