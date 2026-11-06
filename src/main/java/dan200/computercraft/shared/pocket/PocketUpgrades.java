package dan200.computercraft.shared.pocket;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.api.pocket.IPocketUpgrade;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.pocket.upgrades.PocketChatBoxUpgrade;
import dan200.computercraft.shared.pocket.upgrades.PocketEnderModemUpgrade;
import dan200.computercraft.shared.pocket.upgrades.PocketModemUpgrade;
import dan200.computercraft.shared.pocket.upgrades.PocketNpcDetectorUpgrade;
import dan200.computercraft.shared.pocket.upgrades.PocketNpcInterfaceUpgrade;
import dan200.computercraft.shared.pocket.upgrades.PocketSpeakerUpgrade;

/**
 * The pocket computer upgrade registry.
 *
 * <p>
 * This used to be a closed table: six hard-coded ids, a {@code switch} from id to peripheral, and an
 * {@code isUpgrade} range check that turned every other id into "no upgrade". Addons had no way in — their only option
 * was to mixin {@code ItemPocketComputer} to attach a peripheral behind ComputerCraft's back. It is now the registry
 * behind {@link dan200.computercraft.api.ComputerCraftAPI#registerPocketUpgrade(IPocketUpgrade)}, and the six built-in
 * upgrades are registered into it like anybody else's.
 * </p>
 *
 * <p>
 * The ids are on-disk NBT values: {@code 1} wireless modem, {@code 2} ender modem, {@code 3} speaker, {@code 4} chat
 * box, {@code 5} NPC detector, {@code 6} NPC interface. They must not be renumbered, and
 * {@link IPocketUpgrade#FIRST_MOD_UPGRADE_ID} keeps new registrations clear of them.
 * </p>
 */
public final class PocketUpgrades {

    public static final int WIRELESS_MODEM = 1;
    public static final int ENDER_MODEM = 2;
    public static final int SPEAKER = 3;
    public static final int CHAT_BOX = 4;
    public static final int NPC_DETECTOR = 5;
    public static final int NPC_INTERFACE = 6;

    /** Registration order, so {@code equipBack}'s scan and the error messages are deterministic. */
    private static final Map<Integer, IPocketUpgrade> s_upgrades = new LinkedHashMap<>();

    static {
        registerBuiltin(new PocketModemUpgrade());
        registerBuiltin(new PocketEnderModemUpgrade());
        registerBuiltin(new PocketSpeakerUpgrade());
        registerBuiltin(new PocketChatBoxUpgrade());
        registerBuiltin(new PocketNpcDetectorUpgrade());
        registerBuiltin(new PocketNpcInterfaceUpgrade());
    }

    private PocketUpgrades() {}

    /**
     * Registers a mod's upgrade.
     *
     * <p>
     * Mirrors {@code CCTurtleProxyCommon#registerTurtleUpgrade}: ids below {@link IPocketUpgrade#FIRST_MOD_UPGRADE_ID}
     * are refused outright rather than quietly renamed, because reusing one would reinterpret every saved pocket
     * computer carrying it.
     * </p>
     *
     * @param upgrade the upgrade to add
     * @throws IllegalArgumentException if the upgrade is {@code null}, its id is reserved, or its id is already taken
     */
    public static void register(IPocketUpgrade upgrade) {
        if (upgrade == null) {
            throw new IllegalArgumentException("Cannot register a null pocket upgrade");
        }

        int id = upgrade.getUpgradeID();
        if (id < IPocketUpgrade.FIRST_MOD_UPGRADE_ID) {
            throw new IllegalArgumentException(
                "Error registering '" + upgrade.getUnlocalisedAdjective()
                    + "' Pocket Upgrade. UpgradeID '"
                    + id
                    + "' is reserved by ComputerCraft");
        }

        registerInternal(upgrade);
    }

    private static void registerBuiltin(IPocketUpgrade upgrade) {
        registerInternal(upgrade);
    }

    private static void registerInternal(IPocketUpgrade upgrade) {
        int id = upgrade.getUpgradeID();
        if (s_upgrades.containsKey(id)) {
            throw new IllegalArgumentException(
                "Error registering '" + upgrade.getUnlocalisedAdjective()
                    + "' Pocket Upgrade. UpgradeID '"
                    + id
                    + "' is already registered");
        }

        s_upgrades.put(id, upgrade);
    }

    /**
     * The upgrade registered under an id.
     *
     * @param upgrade the NBT upgrade id
     * @return the upgrade, or {@code null} if no mod registered that id
     */
    public static IPocketUpgrade getUpgrade(int upgrade) {
        return s_upgrades.get(upgrade);
    }

    /**
     * Every registered upgrade, in registration order.
     *
     * @return an unmodifiable view of the registry
     */
    public static Collection<IPocketUpgrade> getRegisteredUpgrades() {
        return java.util.Collections.unmodifiableCollection(s_upgrades.values());
    }

    /**
     * The upgrade id an item installs.
     *
     * <p>
     * Kept for the built-in item mapping: {@code ItemPeripheralBase} knows a stack is a wireless modem, and this says
     * which id that is. It is not the general path —
     * {@link IPocketUpgrade#getUpgradeData(net.minecraft.item.ItemStack)}
     * is, and it also covers mods.
     * </p>
     *
     * @param type the item's peripheral type
     * @return the upgrade id, or {@code -1} if this item is not a built-in pocket upgrade
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
     * Whether an upgrade id is registered.
     *
     * @param upgrade the NBT upgrade id
     * @return whether an upgrade is registered under it
     */
    public static boolean isUpgrade(int upgrade) {
        return s_upgrades.containsKey(upgrade);
    }

    /**
     * Builds the peripheral for an installed upgrade.
     *
     * @param upgrade the NBT upgrade id
     * @param access  the pocket computer
     * @return the peripheral, or {@code null} if the id is unknown or the upgrade attaches nothing
     */
    public static IPeripheral createPeripheral(int upgrade, IPocketAccess access) {
        IPocketUpgrade handler = s_upgrades.get(upgrade);
        return handler == null ? null : handler.createPeripheral(access);
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

    /**
     * Forgets every mod-registered upgrade, leaving the six built-ins.
     *
     * <p>
     * Package-private and only for tests: the registry is process-wide, so a test that registers an upgrade would
     * otherwise leak it into every test that runs afterwards.
     * </p>
     */
    static void resetModUpgradesForTesting() {
        s_upgrades.keySet()
            .removeIf(id -> id >= IPocketUpgrade.FIRST_MOD_UPGRADE_ID);
    }
}
