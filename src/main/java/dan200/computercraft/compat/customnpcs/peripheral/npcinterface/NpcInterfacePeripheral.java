package dan200.computercraft.compat.customnpcs.peripheral.npcinterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.compat.customnpcs.peripheral.NpcTypeNames;
import noppes.npcs.api.AbstractNpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IEntityLivingBase;

/**
 * The {@code npc_interface} peripheral — links to one or more CustomNPC entities
 * by UUID and exposes state-read, control, and event-forwarding Lua methods.
 *
 * <h3>Link management (no CNPC required except where noted)</h3>
 *
 * <pre>
 *   link(uuid)               – replace all links with a single NPC by UUID
 *   linkByName(name[,r])     – replace all links with the first NPC named {@code name} within radius r          [requires CNPC]
 *   linkAll(name[,r])        – replace all links with every NPC named {@code name} within radius r; returns count  [requires CNPC]
 *   linkNearest([r])         – replace all links with the nearest NPC within radius r                             [requires CNPC]
 *   unlink([uuid])           – remove a specific linked UUID, or clear all if no arg
 *   isLinked([uuid])         – true if any NPC is linked (or if the specific UUID is linked)
 *   getLinkedNpcs()          – {uuid→name} table of every currently linked NPC
 *   getLinkedUUID()          – the first persisted linked UUID string, or nil; works without CNPC
 *   scanNpcs([r])            – scan without linking; returns [{name,uuid,distance}] sorted by distance           [requires CNPC]
 * </pre>
 *
 * <h3>State-read (require CNPC + link)</h3>
 * All take an optional trailing {@code uuid} string. If omitted, the peripheral must be
 * linked to exactly one NPC; otherwise a UUID is required to disambiguate.
 *
 * <h3>Control (require CNPC + link)</h3>
 * All take an optional trailing {@code uuid} string. If omitted, the command is
 * broadcast to <em>all</em> currently loaded linked NPCs.
 */
public class NpcInterfacePeripheral implements IPeripheral {

    // Type-name tables are defined in NpcTypeNames — shared with NpcDetectorPeripheral.

    static final String[] METHOD_NAMES = {
        // ---- Link management ----
        "link", // 0 link(uuid)
        "linkByName", // 1 linkByName(name [, radius])
        "linkAll", // 2 linkAll(name [, radius]) → returns count
        "linkNearest", // 3 linkNearest([radius])
        "unlink", // 4 unlink([uuid])
        "isLinked", // 5 isLinked([uuid])
        "getLinkedNpcs", // 6 getLinkedNpcs() → {uuid→name}
        "getLinkedUUID", // 7 getLinkedUUID() → string|nil (no CNPC required)
        "scanNpcs", // 8 scanNpcs([radius]) → [{name,uuid,distance}]
        // ---- State read ----
        "getName", // 9 getName([uuid])
        "getTitle", // 10 getTitle([uuid])
        "getUUID", // 11 getUUID([uuid])
        "isAlive", // 12 isAlive([uuid])
        "getHealth", // 13 getHealth([uuid])
        "getMaxHealth", // 14 getMaxHealth([uuid])
        "getPosition", // 15 getPosition([uuid])
        "getMovingType", // 16 getMovingType([uuid])
        "isAttacking", // 17 isAttacking([uuid])
        "getTarget", // 18 getTarget([uuid])
        "getFaction", // 19 getFaction([uuid])
        "getJob", // 20 getJob([uuid])
        "getRole", // 21 getRole([uuid])
        // ---- Control ----
        "say", // 22 say(message [, uuid])
        "setHome", // 23 setHome(x, y, z [, uuid])
        "setMovingType", // 24 setMovingType(type [, uuid])
        "navigateTo", // 25 navigateTo(x, y, z [, speed [, uuid]])
        "executeCommand", // 26 executeCommand(command [, uuid])
        "kill", // 27 kill([uuid])
        "reset", // 28 reset([uuid])
        // ---- State write ----
        "setName", // 29 setName(name [, uuid])
        "setTitle", // 30 setTitle(title [, uuid])
        "setHealth", // 31 setHealth(health [, uuid])
        "setMaxHealth", // 32 setMaxHealth(maxHealth [, uuid])
        "setFaction", // 33 setFaction(factionId [, uuid])
        "setJob", // 34 setJob(jobType [, uuid])
        "setRole", // 35 setRole(roleType [, uuid])
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

            // ---- 0: link(uuid) ----
            // Replaces all links with a single NPC by UUID. Attempts to resolve the
            // display name from loaded entities (best-effort). Always succeeds.
            case 0: {
                String uuid = requireString(args, 0, "uuid");
                return context.executeMainThreadTask(() -> {
                    String name = null;
                    try {
                        if (AbstractNpcAPI.IsAvailable()) {
                            AbstractNpcAPI api = AbstractNpcAPI.Instance();
                            if (api != null) {
                                for (IEntity<?> e : api.getLoadedEntities()) {
                                    if (e instanceof ICustomNpc && uuid.equals(e.getUniqueID())) {
                                        name = ((ICustomNpc<?>) e).getName();
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                    m_holder.setLink(uuid, name);
                    return new Object[] { true };
                });
            }

            // ---- 1: linkByName(name [, radius]) ----
            case 1: {
                requireCnpc();
                return methodLinkByName(context, args);
            }

            // ---- 2: linkAll(name [, radius]) ----
            case 2: {
                requireCnpc();
                return methodLinkAll(context, args);
            }

            // ---- 3: linkNearest([radius]) ----
            case 3: {
                requireCnpc();
                return methodLinkNearest(context, args);
            }

            // ---- 4: unlink([uuid]) ----
            case 4: {
                if (args.length > 0 && args[0] instanceof String) {
                    m_holder.removeLink((String) args[0]);
                } else {
                    m_holder.clearLinks();
                }
                return null;
            }

            // ---- 5: isLinked([uuid]) ----
            case 5: {
                Map<String, String> linked = m_holder.getLinkedNpcs();
                if (args.length > 0 && args[0] instanceof String) {
                    return new Object[] { linked.containsKey(args[0]) };
                }
                return new Object[] { !linked.isEmpty() };
            }

            // ---- 6: getLinkedNpcs() ----
            case 6: {
                return new Object[] { m_holder.getLinkedNpcs() };
            }

            // ---- 7: getLinkedUUID() ----
            // Returns the first persisted linked UUID string, or nil if not linked.
            // Does not require CustomNPCs — reads stored state only.
            case 7: {
                return new Object[] { m_holder.getLinkedUUID() };
            }

            // ---- 8: scanNpcs([radius]) ----
            case 8: {
                requireCnpc();
                return methodScanNpcs(context, args);
            }

            // setMovingType (24) validates the type string before the CNPC gate so callers
            // receive a descriptive error even when CustomNPCs is absent.
            case 24: {
                String typeName = requireString(args, 0, "type");
                int mt = parseMovingType(typeName);
                requireCnpc();
                String tuuid = optionalUUID(args, 1);
                return mainThreadAll(context, tuuid, npc -> {
                    npc.setMovingType(mt);
                    return null;
                });
            }

            // ---- State read (9–21): optional [uuid] as first arg ----
            // ---- Control / state-write (22–35): optional [uuid] as last string arg ----
            default: {
                requireCnpc();
                switch (method) {

                    // State read
                    case 9:
                        return mainThreadSingle(context, args, 0, npc -> new Object[] { npc.getName() });
                    case 10:
                        return mainThreadSingle(context, args, 0, npc -> new Object[] { npc.getTitle() });
                    case 11:
                        return mainThreadSingle(context, args, 0, npc -> new Object[] { npc.getUniqueID() });
                    case 12:
                        return mainThreadSingle(context, args, 0, npc -> new Object[] { npc.isAlive() });
                    case 13:
                        return mainThreadSingle(context, args, 0, npc -> new Object[] { (double) npc.getHealth() });
                    case 14:
                        return mainThreadSingle(context, args, 0, npc -> new Object[] { (double) npc.getMaxHealth() });
                    case 15:
                        return mainThreadSingle(context, args, 0, npc -> {
                            Map<String, Double> pos = new HashMap<>();
                            pos.put("x", npc.getX());
                            pos.put("y", npc.getY());
                            pos.put("z", npc.getZ());
                            return new Object[] { pos };
                        });
                    case 16:
                        return mainThreadSingle(context, args, 0, npc -> {
                            int mt = npc.getMovingType();
                            String typeName = (mt >= 0 && mt < NpcTypeNames.MOVING_TYPE.length)
                                ? NpcTypeNames.MOVING_TYPE[mt]
                                : String.valueOf(mt);
                            return new Object[] { typeName };
                        });
                    case 17:
                        return mainThreadSingle(context, args, 0, npc -> new Object[] { npc.isAttacking() });
                    case 18:
                        return mainThreadSingle(context, args, 0, npc -> {
                            IEntityLivingBase<?> target = npc.getAttackTarget();
                            return new Object[] { target != null ? target.getTypeName() : null };
                        });
                    case 19:
                        return mainThreadSingle(context, args, 0, npc -> {
                            var f = npc.getFaction();
                            Map<String, Object> factionTable = new LinkedHashMap<>();
                            factionTable.put("name", f != null ? f.getName() : "");
                            factionTable.put("id", f != null ? (double) f.getId() : -1.0);
                            return new Object[] { factionTable };
                        });
                    case 20:
                        return mainThreadSingle(context, args, 0, npc -> {
                            var j = npc.getJob();
                            return new Object[] {
                                j != null ? NpcTypeNames.nameOf(NpcTypeNames.JOB_TYPE, j.getType()) : "none" };
                        });
                    case 21:
                        return mainThreadSingle(context, args, 0, npc -> {
                            var r = npc.getRole();
                            return new Object[] {
                                r != null ? NpcTypeNames.nameOf(NpcTypeNames.ROLE_TYPE, r.getType()) : "none" };
                        });

                    // Control — broadcast to all linked NPCs unless uuid arg given
                    case 22: { // say(message [, uuid])
                        String msg = requireString(args, 0, "message");
                        return mainThreadAll(context, optionalUUID(args, 1), npc -> {
                            npc.say(msg);
                            return null;
                        });
                    }
                    case 23: { // setHome(x, y, z [, uuid])
                        int hx = requireInt(args, 0, "x");
                        int hy = requireInt(args, 1, "y");
                        int hz = requireInt(args, 2, "z");
                        return mainThreadAll(context, optionalUUID(args, 3), npc -> {
                            npc.setHome(hx, hy, hz);
                            return null;
                        });
                    }
                    // case 24 handled above
                    case 25: { // navigateTo(x, y, z [, speed [, uuid]])
                        double nx = requireDouble(args, 0, "x");
                        double ny = requireDouble(args, 1, "y");
                        double nz = requireDouble(args, 2, "z");
                        // args[3] = speed (number) or uuid (string); args[4] = uuid if speed given
                        double speed;
                        String tuuid;
                        if (args.length > 3 && args[3] instanceof String) {
                            speed = 0.7;
                            tuuid = (String) args[3];
                        } else {
                            speed = (args.length > 3 && args[3] instanceof Number) ? ((Number) args[3]).doubleValue()
                                : 0.7;
                            tuuid = optionalUUID(args, 4);
                        }
                        final double fSpeed = speed;
                        return mainThreadAll(context, tuuid, npc -> {
                            npc.navigateTo(nx, ny, nz, fSpeed);
                            return null;
                        });
                    }
                    case 26: { // executeCommand(command [, uuid])
                        String cmd = requireString(args, 0, "command");
                        return mainThreadAll(context, optionalUUID(args, 1), npc -> {
                            npc.executeCommand(cmd);
                            return null;
                        });
                    }
                    case 27: { // kill([uuid])
                        return mainThreadAll(context, optionalUUID(args, 0), npc -> {
                            npc.kill();
                            return null;
                        });
                    }
                    case 28: { // reset([uuid])
                        return mainThreadAll(context, optionalUUID(args, 0), npc -> {
                            npc.reset();
                            return null;
                        });
                    }

                    // State write
                    case 29: { // setName(name [, uuid])
                        String name = requireString(args, 0, "name");
                        return mainThreadAll(context, optionalUUID(args, 1), npc -> {
                            npc.setName(name);
                            return null;
                        });
                    }
                    case 30: { // setTitle(title [, uuid])
                        String title = requireString(args, 0, "title");
                        return mainThreadAll(context, optionalUUID(args, 1), npc -> {
                            npc.setTitle(title);
                            return null;
                        });
                    }
                    case 31: { // setHealth(health [, uuid])
                        float health = (float) requireDouble(args, 0, "health");
                        return mainThreadAll(context, optionalUUID(args, 1), npc -> {
                            npc.setHealth(health);
                            return null;
                        });
                    }
                    case 32: { // setMaxHealth(maxHealth [, uuid])
                        double maxHealth = requireDouble(args, 0, "maxHealth");
                        return mainThreadAll(context, optionalUUID(args, 1), npc -> {
                            npc.setMaxHealth(maxHealth);
                            return null;
                        });
                    }
                    case 33: { // setFaction(factionId [, uuid])
                        int factionId = requireInt(args, 0, "factionId");
                        return mainThreadAll(context, optionalUUID(args, 1), npc -> {
                            npc.setFaction(factionId);
                            return null;
                        });
                    }
                    case 34: { // setJob(jobType [, uuid]) — jobType is a string name or integer ordinal
                        int jobType;
                        if (args.length > 0 && args[0] instanceof String) {
                            jobType = parseJobType((String) args[0]);
                        } else {
                            jobType = requireInt(args, 0, "jobType");
                        }
                        return mainThreadAll(context, optionalUUID(args, 1), npc -> {
                            npc.setJob(jobType);
                            return null;
                        });
                    }
                    case 35: { // setRole(roleType [, uuid]) — roleType is a string name or integer ordinal
                        int roleType;
                        if (args.length > 0 && args[0] instanceof String) {
                            roleType = parseRoleType((String) args[0]);
                        } else {
                            roleType = requireInt(args, 0, "roleType");
                        }
                        return mainThreadAll(context, optionalUUID(args, 1), npc -> {
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

    // =========================================================================
    // Private helpers
    // =========================================================================

    @FunctionalInterface
    private interface NpcAction {

        Object[] run(ICustomNpc<?> npc) throws LuaException;
    }

    /**
     * Executes {@code action} on a single target NPC on the main thread.
     *
     * <p>
     * If {@code args[uuidArgIdx]} is a string it is used as the target UUID;
     * otherwise the peripheral must be linked to exactly one NPC.
     * </p>
     */
    private Object[] mainThreadSingle(ILuaContext ctx, Object[] args, int uuidArgIdx, NpcAction action)
        throws LuaException, InterruptedException {
        final String targetUUID = (args.length > uuidArgIdx && args[uuidArgIdx] instanceof String)
            ? (String) args[uuidArgIdx]
            : null;
        return ctx.executeMainThreadTask(() -> {
            ICustomNpc<?> npc;
            if (targetUUID != null) {
                npc = resolveByUUID(targetUUID);
                if (npc == null) {
                    throw new LuaException("NPC '" + targetUUID + "' not found — is it loaded?");
                }
            } else {
                Map<String, String> linked = m_holder.getLinkedNpcs();
                if (linked.isEmpty()) throw new LuaException("Not linked to any NPC");
                if (linked.size() > 1) {
                    throw new LuaException("Multiple NPCs linked; specify a UUID");
                }
                npc = m_holder.resolveNpc();
                if (npc == null) throw new LuaException("NPC not found — is it currently loaded?");
            }
            return action.run(npc);
        });
    }

    /**
     * Executes {@code action} on all linked NPCs (or just {@code targetUUID} if
     * non-null) on the main thread. Silently skips NPCs that are not currently
     * loaded when broadcasting; throws if the explicit target is not found.
     */
    private Object[] mainThreadAll(ILuaContext ctx, String targetUUID, NpcAction action)
        throws LuaException, InterruptedException {
        return ctx.executeMainThreadTask(() -> {
            if (targetUUID != null) {
                ICustomNpc<?> npc = resolveByUUID(targetUUID);
                if (npc == null) {
                    throw new LuaException("NPC '" + targetUUID + "' not found — is it loaded?");
                }
                action.run(npc);
            } else {
                Map<String, String> linked = m_holder.getLinkedNpcs();
                if (linked.isEmpty()) throw new LuaException("Not linked to any NPC");
                for (ICustomNpc<?> npc : m_holder.resolveNpcs()) {
                    action.run(npc);
                }
            }
            return null;
        });
    }

    /** Resolves a loaded NPC entity by UUID. Must be called on the main thread. */
    private ICustomNpc<?> resolveByUUID(String uuid) {
        try {
            if (!AbstractNpcAPI.IsAvailable()) return null;
            AbstractNpcAPI api = AbstractNpcAPI.Instance();
            if (api == null) return null;
            for (IEntity<?> entity : api.getLoadedEntities()) {
                if (entity instanceof ICustomNpc && uuid.equals(entity.getUniqueID())) {
                    return (ICustomNpc<?>) entity;
                }
            }
        } catch (Throwable t) {
            // CNPC absent or incompatible
        }
        return null;
    }

    // ---- Link helpers -------------------------------------------------------

    /**
     * Finds the first NPC by name within radius and sets it as the sole link.
     * Returns {@code {true, uuid}} on success, {@code {false}} on failure.
     */
    private Object[] methodLinkByName(ILuaContext context, Object[] args) throws LuaException, InterruptedException {
        String name = requireString(args, 0, "npcName");
        double radius = optionalRadius(args, 1);
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
                    return new Object[] { true, npc.getUniqueID() };
                }
            }
            return new Object[] { false };
        });
    }

    /**
     * Links ALL NPCs named {@code name} within radius. Replaces the current link
     * set. Returns {@code {count}} — the number of NPCs now linked.
     */
    private Object[] methodLinkAll(ILuaContext context, Object[] args) throws LuaException, InterruptedException {
        String name = requireString(args, 0, "npcName");
        double radius = optionalRadius(args, 1);
        final double rSq = radius * radius;
        return context.executeMainThreadTask(() -> {
            AbstractNpcAPI api = AbstractNpcAPI.Instance();
            if (api == null) return new Object[] { 0 };
            m_holder.clearLinks();
            int count = 0;
            for (IEntity<?> entity : api.getLoadedEntities()) {
                if (!(entity instanceof ICustomNpc)) continue;
                ICustomNpc<?> npc = (ICustomNpc<?>) entity;
                if (!name.equals(npc.getName())) continue;
                double dx = npc.getX() - m_holder.getPositionX();
                double dy = npc.getY() - m_holder.getPositionY();
                double dz = npc.getZ() - m_holder.getPositionZ();
                if (dx * dx + dy * dy + dz * dz <= rSq) {
                    m_holder.addLink(npc.getUniqueID(), npc.getName());
                    count++;
                }
            }
            return new Object[] { count };
        });
    }

    /**
     * Links the nearest NPC within radius.
     * Returns {@code {true, uuid}} on success, {@code {false}} on failure.
     */
    private Object[] methodLinkNearest(ILuaContext context, Object[] args) throws LuaException, InterruptedException {
        double radius = optionalRadius(args, 0);
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
            return new Object[] { true, best.getUniqueID() };
        });
    }

    /**
     * Scans for all NPCs within radius without changing the current link state.
     * Returns a Lua array of tables {@code {name, uuid, distance}}, sorted nearest-first.
     */
    private Object[] methodScanNpcs(ILuaContext context, Object[] args) throws LuaException, InterruptedException {
        double radius = optionalRadius(args, 0);
        final double rSq = radius * radius;
        return context.executeMainThreadTask(() -> {
            AbstractNpcAPI api = AbstractNpcAPI.Instance();
            if (api == null) return new Object[] { new Object[0] };
            List<Map<String, Object>> entries = new ArrayList<>();
            for (IEntity<?> entity : api.getLoadedEntities()) {
                if (!(entity instanceof ICustomNpc)) continue;
                ICustomNpc<?> npc = (ICustomNpc<?>) entity;
                double dx = npc.getX() - m_holder.getPositionX();
                double dy = npc.getY() - m_holder.getPositionY();
                double dz = npc.getZ() - m_holder.getPositionZ();
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq <= rSq) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("name", npc.getName());
                    entry.put("uuid", npc.getUniqueID());
                    entry.put("distance", Math.sqrt(distSq));
                    entries.add(entry);
                }
            }
            entries.sort((a, b) -> Double.compare((Double) a.get("distance"), (Double) b.get("distance")));
            return new Object[] { entries.toArray() };
        });
    }

    // ---- Argument utilities -------------------------------------------------

    private static int parseMovingType(String name) throws LuaException {
        int idx = NpcTypeNames.indexOf(NpcTypeNames.MOVING_TYPE, name);
        if (idx >= 0) return idx;
        throw new LuaException(
            "Invalid moving type '" + name + "'. Expected one of: " + String.join(", ", NpcTypeNames.MOVING_TYPE));
    }

    private static int parseJobType(String name) throws LuaException {
        int idx = NpcTypeNames.indexOf(NpcTypeNames.JOB_TYPE, name);
        if (idx >= 0) return idx;
        throw new LuaException(
            "Invalid job type '" + name + "'. Expected one of: " + String.join(", ", NpcTypeNames.JOB_TYPE));
    }

    private static int parseRoleType(String name) throws LuaException {
        int idx = NpcTypeNames.indexOf(NpcTypeNames.ROLE_TYPE, name);
        if (idx >= 0) return idx;
        throw new LuaException(
            "Invalid role type '" + name + "'. Expected one of: " + String.join(", ", NpcTypeNames.ROLE_TYPE));
    }

    private static void requireCnpc() throws LuaException {
        try {
            if (!AbstractNpcAPI.IsAvailable()) throw new LuaException("CustomNPCs is not installed");
        } catch (LuaException e) {
            throw e;
        } catch (Throwable t) {
            throw new LuaException("CustomNPCs is not installed");
        }
    }

    /** Returns {@code args[i]} as a String if present and a String, otherwise {@code null}. */
    private static String optionalUUID(Object[] args, int i) {
        return (i < args.length && args[i] instanceof String) ? (String) args[i] : null;
    }

    private static double optionalRadius(Object[] args, int i) {
        if (i < args.length && args[i] instanceof Number) {
            double max = dan200.computercraft.ComputerCraft.npc_detector_max_range;
            return Math.max(0, Math.min(((Number) args[i]).doubleValue(), max));
        }
        return 16.0;
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
