package dan200.computercraft.shared.peripheral.chatbox;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

/**
 * Chat Box peripheral — two Lua methods: {@code say} and {@code tell}.
 *
 * <p>
 * Both methods are <em>blocking</em>: they execute on the server main thread via
 * {@link ILuaContext#executeMainThreadTask(ILuaTask)} to safely access the player list.
 * </p>
 */
public class ChatBoxPeripheral implements IPeripheral {

    private static final int METHOD_SAY = 0;
    private static final int METHOD_TELL = 1;

    private static final String DEFAULT_LABEL = "#";
    private static final int MAX_TEXT_LENGTH = 256;
    private static final int MAX_LABEL_LENGTH = 32;

    private final TileChatBox m_tile;

    public ChatBoxPeripheral(TileChatBox tile) {
        this.m_tile = tile;
    }

    // -------------------------------------------------------------------------
    // IPeripheral
    // -------------------------------------------------------------------------

    @Override
    public String getType() {
        return "chatbox";
    }

    @Override
    public String[] getMethodNames() {
        return new String[] { "say", "tell" };
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        switch (method) {
            case METHOD_SAY:
                return say(context, arguments);
            case METHOD_TELL:
                return tell(context, arguments);
            default:
                return null;
        }
    }

    @Override
    public void attach(IComputerAccess computer) {
        m_tile.attachComputer(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        m_tile.detachComputer(computer);
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other || (other instanceof ChatBoxPeripheral && ((ChatBoxPeripheral) other).m_tile == m_tile);
    }

    // -------------------------------------------------------------------------
    // Method implementations
    // -------------------------------------------------------------------------

    /**
     * say( string text [, number range [, string label]] )
     *
     * <p>
     * Broadcasts a labelled message to all players within range.
     * Range -1 (or the configured default of -1) means infinite.
     * </p>
     */
    private Object[] say(ILuaContext context, Object[] args) throws LuaException, InterruptedException {
        // arg 0: text (required)
        final String text = requireText(args, 0);
        // arg 1: range (optional)
        final double range = parseRange(args, 1);
        // arg 2: label (optional)
        final String label = parseLabel(args, 2);

        context.executeMainThreadTask(new ILuaTask() {

            @Override
            public Object[] execute() throws LuaException {
                String formatted = "[" + label + "] " + text;
                ChatComponentText component = new ChatComponentText(formatted);
                for (Object playerObj : MinecraftServer.getServer()
                    .getConfigurationManager().playerEntityList) {
                    if (!(playerObj instanceof EntityPlayerMP)) continue;
                    EntityPlayerMP player = (EntityPlayerMP) playerObj;
                    if (isInRange(player, range)) {
                        player.addChatMessage(component);
                    }
                }
                return null;
            }
        });
        return null;
    }

    /**
     * tell( string playerName, string text [, number range [, string label]] )
     *
     * <p>
     * Sends a labelled message to a specific player (still range-checked unless range is -1).
     * </p>
     */
    private Object[] tell(ILuaContext context, Object[] args) throws LuaException, InterruptedException {
        // arg 0: playerName (required)
        if (args.length < 1 || !(args[0] instanceof String)) {
            throw new LuaException("Expected string for playerName");
        }
        final String playerName = (String) args[0];
        // arg 1: text (required)
        final String text = requireText(args, 1);
        // arg 2: range (optional)
        final double range = parseRange(args, 2);
        // arg 3: label (optional)
        final String label = parseLabel(args, 3);

        context.executeMainThreadTask(new ILuaTask() {

            @Override
            public Object[] execute() throws LuaException {
                EntityPlayerMP player = MinecraftServer.getServer()
                    .getConfigurationManager()
                    .func_152612_a(playerName); // getPlayerByUsername
                if (player == null) {
                    throw new LuaException("Player not found");
                }
                if (!isInRange(player, range)) {
                    // Player is out of range — silently succeed (same as say).
                    return null;
                }
                String formatted = "[" + label + "] " + text;
                player.addChatMessage(new ChatComponentText(formatted));
                return null;
            }
        });
        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts and validates a non-empty text string from {@code args[index]}.
     */
    static String requireText(Object[] args, int index) throws LuaException {
        if (args.length <= index || !(args[index] instanceof String)) {
            throw new LuaException("Expected string");
        }
        String text = (String) args[index];
        if (text.isEmpty()) {
            throw new LuaException("Text must not be empty");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new LuaException("Text too long (max " + MAX_TEXT_LENGTH + " characters)");
        }
        return text;
    }

    /**
     * Parses an optional range argument.
     */
    static double parseRange(Object[] args, int index) throws LuaException {
        double range = ComputerCraft.chatbox_max_range; // default from config
        if (args.length > index && args[index] != null) {
            if (!(args[index] instanceof Number)) {
                throw new LuaException("Expected number for range");
            }
            range = ((Number) args[index]).doubleValue();
        }
        // Cap to config maximum (if config max is not infinite)
        if (ComputerCraft.chatbox_max_range >= 0 && range < 0) {
            // caller requested infinite but config does not allow it
            range = ComputerCraft.chatbox_max_range;
        } else if (ComputerCraft.chatbox_max_range >= 0 && range > ComputerCraft.chatbox_max_range) {
            range = ComputerCraft.chatbox_max_range;
        }
        return range;
    }

    /**
     * Parses an optional label argument. Returns {@link #DEFAULT_LABEL} if absent.
     */
    static String parseLabel(Object[] args, int index) throws LuaException {
        if (args.length > index && args[index] != null) {
            if (!(args[index] instanceof String)) {
                throw new LuaException("Expected string for label");
            }
            String label = (String) args[index];
            if (label.length() > MAX_LABEL_LENGTH) {
                throw new LuaException("Label too long (max " + MAX_LABEL_LENGTH + " characters)");
            }
            return label;
        }
        return DEFAULT_LABEL;
    }

    /**
     * Returns {@code true} if the player is within the specified range of the chat box tile,
     * or if range is negative (infinite).
     */
    private boolean isInRange(EntityPlayerMP player, double range) {
        if (range < 0) return true;
        if (m_tile.getWorldObj() == null) return false;
        if (player.worldObj.provider.dimensionId != m_tile.getWorldObj().provider.dimensionId) {
            return false;
        }
        double dx = player.posX - (m_tile.xCoord + 0.5);
        double dy = player.posY - (m_tile.yCoord + 0.5);
        double dz = player.posZ - (m_tile.zCoord + 0.5);
        return (dx * dx + dy * dy + dz * dz) <= (range * range);
    }
}
