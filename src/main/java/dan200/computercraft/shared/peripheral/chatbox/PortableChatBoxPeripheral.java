package dan200.computercraft.shared.peripheral.chatbox;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

/**
 * Portable chatbox peripheral shared by the turtle upgrade and pocket computer
 * upgrade. Maintains its own computer set and self-registers with
 * {@link ChatBoxManager} when the first computer attaches.
 *
 * <p>
 * Subclasses supply the current position via
 * {@link #getPositionX()}, {@link #getPositionY()}, {@link #getPositionZ()} and
 * the dimension via {@link #getDimensionId()}. The pocket variant updates these
 * each tick; the turtle variant resolves them at call time.
 * </p>
 */
public abstract class PortableChatBoxPeripheral implements IPeripheral, IChatBoxReceiver {

    private static final int METHOD_SAY = 0;
    private static final int METHOD_TELL = 1;

    private final Set<IComputerAccess> m_computers = new HashSet<>();

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
    public synchronized void attach(IComputerAccess computer) {
        if (m_computers.isEmpty()) {
            ChatBoxManager.register(this);
        }
        m_computers.add(computer);
    }

    @Override
    public synchronized void detach(IComputerAccess computer) {
        m_computers.remove(computer);
        if (m_computers.isEmpty()) {
            ChatBoxManager.unregister(this);
        }
    }

    // -------------------------------------------------------------------------
    // IChatBoxReceiver
    // -------------------------------------------------------------------------

    @Override
    public void onChatEvent(String playerName, String message) {
        queueEvent("chat", playerName, message);
    }

    @Override
    public void onDeathEvent(String player, String killer, String damageType) {
        queueEvent("death", player, killer, damageType);
    }

    @Override
    public void onCommandEvent(String player, Map<Integer, String> arguments) {
        queueEvent("command", player, arguments);
    }

    @Override
    public void onNpcInteractEvent(String playerName, String npcName) {
        queueEvent("cnpc_interact", playerName, npcName);
    }

    @Override
    public void onNpcDialogEvent(String playerName, String npcName, int dialogId, int optionId) {
        queueEvent("cnpc_dialog", playerName, npcName, dialogId, optionId);
    }

    @Override
    public void onNpcDialogClosedEvent(String playerName, String npcName, int dialogId, int optionId) {
        queueEvent("cnpc_dialog_closed", playerName, npcName, dialogId, optionId);
    }

    @Override
    public void onNpcDiedEvent(String npcName, String killerName, String damageType) {
        queueEvent("cnpc_died", npcName, killerName, damageType);
    }

    @Override
    public void onNpcSpawnedEvent(String npcName) {
        queueEvent("cnpc_spawned", npcName);
    }

    @Override
    public void onNpcDamagedEvent(String npcName, String attackerName, float damage, String damageType) {
        queueEvent("cnpc_damaged", npcName, attackerName, damage, damageType);
    }

    @Override
    public void onNpcKilledEntityEvent(String npcName, String entityName, String entityType) {
        queueEvent("cnpc_killed_entity", npcName, entityName, entityType);
    }

    // -------------------------------------------------------------------------
    // Position (supplied by subclass)
    // -------------------------------------------------------------------------

    /** World X coordinate of the carrier (turtle block or entity). */
    protected abstract double getPositionX();

    /** World Y coordinate of the carrier. */
    protected abstract double getPositionY();

    /** World Z coordinate of the carrier. */
    protected abstract double getPositionZ();

    /**
     * Dimension ID of the carrier. Return {@link Integer#MIN_VALUE} when
     * unavailable — causes all range checks to fail (no messages sent).
     */
    protected abstract int getDimensionId();

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void queueEvent(String eventName, Object... params) {
        Set<IComputerAccess> snapshot;
        synchronized (this) {
            snapshot = new HashSet<>(m_computers);
        }
        for (IComputerAccess computer : snapshot) {
            computer.queueEvent(eventName, params);
        }
    }

    private Object[] say(ILuaContext context, Object[] args) throws LuaException, InterruptedException {
        final String text = ChatBoxPeripheral.requireText(args, 0);
        final double range = ChatBoxPeripheral.parseRange(args, 1);
        final String label = ChatBoxPeripheral.parseLabel(args, 2);

        context.executeMainThreadTask(new ILuaTask() {

            @Override
            public Object[] execute() throws LuaException {
                String formatted = "[" + label + "] " + text;
                ChatComponentText component = new ChatComponentText(formatted);
                for (Object obj : MinecraftServer.getServer()
                    .getConfigurationManager().playerEntityList) {
                    if (!(obj instanceof EntityPlayerMP)) continue;
                    EntityPlayerMP player = (EntityPlayerMP) obj;
                    if (isInRange(player, range)) {
                        player.addChatMessage(component);
                    }
                }
                return null;
            }
        });
        return null;
    }

    private Object[] tell(ILuaContext context, Object[] args) throws LuaException, InterruptedException {
        if (args.length < 1 || !(args[0] instanceof String)) {
            throw new LuaException("Expected string for playerName");
        }
        final String playerName = (String) args[0];
        final String text = ChatBoxPeripheral.requireText(args, 1);
        final double range = ChatBoxPeripheral.parseRange(args, 2);
        final String label = ChatBoxPeripheral.parseLabel(args, 3);

        context.executeMainThreadTask(new ILuaTask() {

            @Override
            public Object[] execute() throws LuaException {
                EntityPlayerMP player = MinecraftServer.getServer()
                    .getConfigurationManager()
                    .func_152612_a(playerName);
                if (player == null) {
                    throw new LuaException("Player not found");
                }
                if (!isInRange(player, range)) {
                    return null;
                }
                String formatted = "[" + label + "] " + text;
                player.addChatMessage(new ChatComponentText(formatted));
                return null;
            }
        });
        return null;
    }

    private boolean isInRange(EntityPlayerMP player, double range) {
        int dim = getDimensionId();
        if (dim == Integer.MIN_VALUE) return false;
        if (range < 0) return true;
        if (player.worldObj.provider.dimensionId != dim) return false;
        double dx = player.posX - getPositionX();
        double dy = player.posY - getPositionY();
        double dz = player.posZ - getPositionZ();
        return (dx * dx + dy * dy + dz * dz) <= (range * range);
    }
}
