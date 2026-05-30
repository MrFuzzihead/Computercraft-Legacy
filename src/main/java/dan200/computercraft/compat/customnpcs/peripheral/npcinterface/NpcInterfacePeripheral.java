package dan200.computercraft.compat.customnpcs.peripheral.npcinterface;

import java.util.HashMap;
import java.util.Map;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import noppes.npcs.api.AbstractNpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IEntityLivingBase;

/**
 * The {@code npc_interface} peripheral — links to a specific CustomNPC entity by UUID
 * and exposes state-read, control, and event-forwarding Lua methods.
 *
 * <p>
 * Methods 0–4 (link management) work without CustomNPCs installed.
 * Methods 5–24 require CNPC installed and the peripheral to be linked to a loaded NPC.
 * All NPC-access methods run on the Minecraft main thread via
 * {@link ILuaContext#executeMainThreadTask}.
 * </p>
 */
public class NpcInterfacePeripheral implements IPeripheral {

    /** EnumMovingType ordinals — matches NpcDetectorPeripheral.MOVING_TYPE_NAMES. */
    private static final String[] MOVING_TYPE_NAMES = { "standing", "wandering", "path" };

    static final String[] METHOD_NAMES = { // Link management — always available
        "link", // 0
        "linkNearest", // 1
        "unlink", // 2
        "isLinked", // 3
        "getLinkedName", // 4
        // NPC state read — require CNPC + link
        "getName", // 5
        "getTitle", // 6
        "getUUID", // 7
        "isAlive", // 8
        "getHealth", // 9
        "getMaxHealth", // 10
        "getPosition", // 11
        "getMovingType", // 12
        "isAttacking", // 13
        "getTarget", // 14
        "getFaction", // 15
        "getJob", // 16
        "getRole", // 17
        // NPC control — require CNPC + link
        "say", // 18
        "setHome", // 19
        "setMovingType", // 20
        "navigateTo", // 21
        "executeCommand", // 22
        "kill", // 23
        "reset", // 24
        // NPC state write — require CNPC + link
        "setName", // 25
        "setTitle", // 26
        "setHealth", // 27
        "setMaxHealth", // 28
        "setFaction", // 29
        "setJob", // 30
        "setRole", // 31
    };

    private final INpcInterfaceHolder m_holder;

    public NpcInterfacePeripheral(INpcInterfaceHolder holder) {
        this.m_holder = holder;
    }

    @Override
    public String getType() {
        return "npc_interface";
    }

    @Override
    public String[] getMethodNames() {
        return METHOD_NAMES;
    }

    @Override
    public void attach(IComputerAccess computer) {
        m_holder.attachComputer(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        m_holder.detachComputer(computer);
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof NpcInterfacePeripheral && ((NpcInterfacePeripheral) other).m_holder == m_holder;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] args)
        throws LuaException, InterruptedException {

        switch (method) {
            // ---- Link management (no CNPC required) ----
            case 0:
                return methodLink(context, args);
            case 1:
                return methodLinkNearest(context, args);
            case 2: {
                m_holder.setLink(null, null);
                return null;
            }
            case 3:
                return new Object[] { m_holder.getLinkedUUID() != null };
            case 4:
                return new Object[] { m_holder.getLinkedName() };

            // setMovingType validates its string arg before the CNPC gate so callers
            // receive a descriptive error even when CustomNPCs is absent.
            case 20: {
                String typeName = requireString(args, 0, "type");
                int mt = parseMovingType(typeName);
                requireCnpc();
                return mainThread(context, npc -> {
                    npc.setMovingType(mt);
                    return null;
                });
            }

            // ---- NPC state / control (CNPC required) ----
            default: {
                requireCnpc();
                switch (method) {
                    case 5:
                        return mainThread(context, npc -> new Object[] { npc.getName() });
                    case 6:
                        return mainThread(context, npc -> new Object[] { npc.getTitle() });
                    case 7:
                        return mainThread(context, npc -> new Object[] { npc.getUniqueID() });
                    case 8:
                        return mainThread(context, npc -> new Object[] { npc.isAlive() });
                    case 9:
                        return mainThread(context, npc -> new Object[] { (double) npc.getHealth() });
                    case 10:
                        return mainThread(context, npc -> new Object[] { (double) npc.getMaxHealth() });
                    case 11:
                        return mainThread(context, npc -> {
                            Map<String, Double> pos = new HashMap<>();
                            pos.put("x", npc.getX());
                            pos.put("y", npc.getY());
                            pos.put("z", npc.getZ());
                            return new Object[] { pos };
                        });
                    case 12:
                        return mainThread(context, npc -> new Object[] { (double) npc.getMovingType() });
                    case 13:
                        return mainThread(context, npc -> new Object[] { npc.isAttacking() });
                    case 14:
                        return mainThread(context, npc -> {
                            IEntityLivingBase<?> target = npc.getAttackTarget();
                            return new Object[] { target != null ? target.getTypeName() : null };
                        });
                    case 15:
                        return mainThread(context, npc -> {
                            var f = npc.getFaction();
                            return new Object[] { f != null ? f.getName() : "" };
                        });
                    case 16:
                        return mainThread(context, npc -> {
                            var j = npc.getJob();
                            return new Object[] { j != null ? (double) j.getType() : -1.0 };
                        });
                    case 17:
                        return mainThread(context, npc -> {
                            var r = npc.getRole();
                            return new Object[] { r != null ? (double) r.getType() : -1.0 };
                        });
                    case 18: { // say(message)
                        String msg = requireString(args, 0, "message");
                        return mainThread(context, npc -> {
                            npc.say(msg);
                            return null;
                        });
                    }
                    case 19: { // setHome(x, y, z)
                        int hx = requireInt(args, 0, "x");
                        int hy = requireInt(args, 1, "y");
                        int hz = requireInt(args, 2, "z");
                        return mainThread(context, npc -> {
                            npc.setHome(hx, hy, hz);
                            return null;
                        });
                    }
                    case 21: { // navigateTo(x, y, z [, speed])
                        double nx = requireDouble(args, 0, "x");
                        double ny = requireDouble(args, 1, "y");
                        double nz = requireDouble(args, 2, "z");
                        double speed = (args.length > 3 && args[3] instanceof Number) ? ((Number) args[3]).doubleValue()
                            : 0.7;
                        return mainThread(context, npc -> {
                            npc.navigateTo(nx, ny, nz, speed);
                            return null;
                        });
                    }
                    case 22: { // executeCommand(cmd)
                        String cmd = requireString(args, 0, "command");
                        return mainThread(context, npc -> {
                            npc.executeCommand(cmd);
                            return null;
                        });
                    }
                    case 23:
                        return mainThread(context, npc -> {
                            npc.kill();
                            return null;
                        });
                    case 24:
                        return mainThread(context, npc -> {
                            npc.reset();
                            return null;
                        });
                    case 25: { // setName(name)
                        String name = requireString(args, 0, "name");
                        return mainThread(context, npc -> {
                            npc.setName(name);
                            return null;
                        });
                    }
                    case 26: { // setTitle(title)
                        String title = requireString(args, 0, "title");
                        return mainThread(context, npc -> {
                            npc.setTitle(title);
                            return null;
                        });
                    }
                    case 27: { // setHealth(health)
                        float health = (float) requireDouble(args, 0, "health");
                        return mainThread(context, npc -> {
                            npc.setHealth(health);
                            return null;
                        });
                    }
                    case 28: { // setMaxHealth(maxHealth)
                        double maxHealth = requireDouble(args, 0, "maxHealth");
                        return mainThread(context, npc -> {
                            npc.setMaxHealth(maxHealth);
                            return null;
                        });
                    }
                    case 29: { // setFaction(factionId)
                        int factionId = requireInt(args, 0, "factionId");
                        return mainThread(context, npc -> {
                            npc.setFaction(factionId);
                            return null;
                        });
                    }
                    case 30: { // setJob(jobType)
                        int jobType = requireInt(args, 0, "jobType");
                        return mainThread(context, npc -> {
                            npc.setJob(jobType);
                            return null;
                        });
                    }
                    case 31: { // setRole(roleType)
                        int roleType = requireInt(args, 0, "roleType");
                        return mainThread(context, npc -> {
                            npc.setRole(roleType);
                            return null;
                        });
                    }
                    default:
                        return null;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @FunctionalInterface
    private interface NpcAction {

        Object[] run(ICustomNpc<?> npc) throws LuaException;
    }

    private Object[] mainThread(ILuaContext ctx, NpcAction action) throws LuaException, InterruptedException {
        return ctx.executeMainThreadTask(() -> {
            ICustomNpc<?> npc = m_holder.resolveNpc();
            if (npc == null) {
                throw new LuaException("NPC not found — is it loaded and linked?");
            }
            return action.run(npc);
        });
    }

    private Object[] methodLink(ILuaContext context, Object[] args) throws LuaException, InterruptedException {
        requireCnpc();
        String name = requireString(args, 0, "npcName");
        double radius = (args.length > 1 && args[1] instanceof Number) ? ((Number) args[1]).doubleValue() : 16.0;
        final double rSq = radius * radius;
        return context.executeMainThreadTask(() -> {
            AbstractNpcAPI api = AbstractNpcAPI.Instance();
            if (api == null) return new Object[] { false };
            for (IEntity<?> entity : api.getLoadedEntities()) {
                if (!(entity instanceof ICustomNpc)) continue;
                ICustomNpc<?> npc = (ICustomNpc<?>) entity;
                if (!name.equals(npc.getName())) continue;
                double dx = npc.getX() - m_holder.getPositionX();
                double dy = npc.getY() - m_holder.getPositionY();
                double dz = npc.getZ() - m_holder.getPositionZ();
                if (dx * dx + dy * dy + dz * dz <= rSq) {
                    m_holder.setLink(npc.getUniqueID(), npc.getName());
                    return new Object[] { true };
                }
            }
            return new Object[] { false };
        });
    }

    private Object[] methodLinkNearest(ILuaContext context, Object[] args) throws LuaException, InterruptedException {
        requireCnpc();
        double radius = (args.length > 0 && args[0] instanceof Number) ? ((Number) args[0]).doubleValue() : 16.0;
        final double rSq = radius * radius;
        return context.executeMainThreadTask(() -> {
            AbstractNpcAPI api = AbstractNpcAPI.Instance();
            if (api == null) return new Object[] { false };
            ICustomNpc<?> best = null;
            double bestDist = Double.MAX_VALUE;
            for (IEntity<?> entity : api.getLoadedEntities()) {
                if (!(entity instanceof ICustomNpc)) continue;
                ICustomNpc<?> npc = (ICustomNpc<?>) entity;
                double dx = npc.getX() - m_holder.getPositionX();
                double dy = npc.getY() - m_holder.getPositionY();
                double dz = npc.getZ() - m_holder.getPositionZ();
                double dist = dx * dx + dy * dy + dz * dz;
                if (dist <= rSq && dist < bestDist) {
                    best = npc;
                    bestDist = dist;
                }
            }
            if (best == null) return new Object[] { false };
            m_holder.setLink(best.getUniqueID(), best.getName());
            return new Object[] { true };
        });
    }

    private static int parseMovingType(String name) throws LuaException {
        for (int i = 0; i < MOVING_TYPE_NAMES.length; i++) {
            if (MOVING_TYPE_NAMES[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        throw new LuaException(
            "Invalid moving type '" + name + "'. Expected one of: " + String.join(", ", MOVING_TYPE_NAMES));
    }

    private static void requireCnpc() throws LuaException {
        try {
            if (!AbstractNpcAPI.IsAvailable()) {
                throw new LuaException("CustomNPCs is not installed");
            }
        } catch (LuaException e) {
            throw e;
        } catch (Throwable t) {
            throw new LuaException("CustomNPCs is not installed");
        }
    }

    static String requireString(Object[] args, int i, String name) throws LuaException {
        if (args.length <= i || !(args[i] instanceof String)) {
            throw new LuaException("Expected string for " + name);
        }
        return (String) args[i];
    }

    static int requireInt(Object[] args, int i, String name) throws LuaException {
        if (args.length <= i || !(args[i] instanceof Number)) {
            throw new LuaException("Expected number for " + name);
        }
        return ((Number) args[i]).intValue();
    }

    static double requireDouble(Object[] args, int i, String name) throws LuaException {
        if (args.length <= i || !(args[i] instanceof Number)) {
            throw new LuaException("Expected number for " + name);
        }
        return ((Number) args[i]).doubleValue();
    }
}
