package dan200.computercraft.compat.customnpcs.peripheral;

/**
 * Shared CustomNPCs type-name lookup tables, sourced from the
 * {@code noppes.npcs.scripted.constants} classes.
 *
 * <p>
 * Centralised here so that {@code NpcDetectorPeripheral} and
 * {@code NpcInterfacePeripheral} stay in sync automatically — changing
 * a name in one place updates both.
 * </p>
 */
public final class NpcTypeNames {

    private NpcTypeNames() {}

    /** EnumMovingType ordinals: 0=Standing, 1=Wandering, 2=MovingPath. */
    public static final String[] MOVING_TYPE = { "standing", "wandering", "path" };

    /**
     * scripted.constants.JobType ordinals:
     * 0=none, 1=bard, 2=healer, 3=guard, 4=follower,
     * 5=itemgiver, 6=spawner, 7=conversation, 8=puppet
     */
    public static final String[] JOB_TYPE = { "none", "bard", "healer", "guard", "follower", "itemgiver", "spawner",
        "conversation", "puppet" };

    /**
     * scripted.constants.RoleType ordinals:
     * 0=none, 1=trader, 2=follower, 3=bank, 4=transporter, 5=postman, 6=companion
     */
    public static final String[] ROLE_TYPE = { "none", "trader", "follower", "bank", "transporter", "postman",
        "companion" };

    /**
     * Returns the name for {@code type} in {@code table}, or {@code "unknown"} if
     * the index is out of range.
     */
    public static String nameOf(String[] table, int type) {
        return type >= 0 && type < table.length ? table[type] : "unknown";
    }

    /**
     * Returns the index of {@code name} (case-insensitive) in {@code table},
     * or {@code -1} if not found.
     */
    public static int indexOf(String[] table, String name) {
        for (int i = 0; i < table.length; i++) {
            if (table[i].equalsIgnoreCase(name)) return i;
        }
        return -1;
    }
}
