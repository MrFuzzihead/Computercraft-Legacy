package dan200.computercraft.shared.peripheral.npcdetector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.World;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IEntityLivingBase;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.handler.data.IFaction;
import noppes.npcs.api.jobs.IJob;
import noppes.npcs.api.roles.IRole;

/**
 * The {@code npc_detector} peripheral — a stateless, read-only scanner that locates
 * nearby CustomNPCs entities and players using the CustomNPCs {@link IWorld} API.
 *
 * <p>
 * All scan methods (methods 0–8) run on the Minecraft main thread via
 * {@link ILuaContext#executeMainThreadTask} to safely traverse world entity lists.
 * Method 9 ({@code getMaxRange}) is the sole exception and works even when CustomNPCs
 * is absent.
 * </p>
 *
 * <p>
 * Subclasses supply the scan origin via {@link #getPositionX()}, {@link #getPositionY()},
 * {@link #getPositionZ()} and the world via {@link #getMcWorld()}. Use
 * {@link #forTile(TileNpcDetector)} to create a tile-bound instance.
 * </p>
 */
public abstract class NpcDetectorPeripheral implements IPeripheral {

    // -------------------------------------------------------------------------
    // Type name tables (sourced from CustomNPCs scripted constants classes)
    // -------------------------------------------------------------------------

    /** EnumMovingType ordinals: 0=Standing, 1=Wandering, 2=MovingPath */
    private static final String[] MOVING_TYPE_NAMES = { "standing", "wandering", "path" };

    /**
     * scripted.constants.JobType: 0=none, 1=bard, 2=healer, 3=guard,
     * 4=follower, 5=itemgiver, 6=spawner, 7=conversation, 8=puppet
     */
    private static final String[] JOB_TYPE_NAMES = { "none", "bard", "healer", "guard", "follower", "itemgiver",
        "spawner", "conversation", "puppet" };

    /**
     * scripted.constants.RoleType: 0=none, 1=trader, 2=follower,
     * 3=bank, 4=transporter, 5=postman, 6=companion
     */
    private static final String[] ROLE_TYPE_NAMES = { "none", "trader", "follower", "bank", "transporter", "postman",
        "companion" };

    private static String nameFromTable(String[] table, int type) {
        return type >= 0 && type < table.length ? table[type] : "unknown";
    }

    static final String[] METHOD_NAMES = { "getNpcs", // 0
        "getNpcsByName", // 1
        "getNpcsInFaction", // 2
        "getNearestNpc", // 3
        "countNpcs", // 4
        "getPlayers", // 5
        "getNearestPlayer", // 6
        "countPlayers", // 7
        "getEntities", // 8
        "getMaxRange" // 9
    };

    // -------------------------------------------------------------------------
    // Abstract position / world source — implemented by each variant
    // -------------------------------------------------------------------------

    /** X coordinate of the scan origin (block-centre or entity position). */
    protected abstract double getPositionX();

    /** Y coordinate of the scan origin. */
    protected abstract double getPositionY();

    /** Z coordinate of the scan origin. */
    protected abstract double getPositionZ();

    /**
     * The Minecraft world used to resolve the CustomNPCs {@link IWorld}.
     * May return {@code null} if the world is not yet known (pocket computer before first tick).
     */
    protected abstract World getMcWorld();

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates a tile-bound {@code NpcDetectorPeripheral} that reads position and world
     * directly from {@code tile}. Two instances created for the same tile compare equal.
     */
    static NpcDetectorPeripheral forTile(TileNpcDetector tile) {
        return new TileBoundPeripheral(tile);
    }

    /** Concrete tile-bound implementation. Equality is based on tile object identity. */
    private static final class TileBoundPeripheral extends NpcDetectorPeripheral {

        private final TileNpcDetector m_tile;

        TileBoundPeripheral(TileNpcDetector tile) {
            this.m_tile = tile;
        }

        @Override
        protected double getPositionX() {
            return m_tile.xCoord + 0.5;
        }

        @Override
        protected double getPositionY() {
            return m_tile.yCoord + 0.5;
        }

        @Override
        protected double getPositionZ() {
            return m_tile.zCoord + 0.5;
        }

        @Override
        protected World getMcWorld() {
            return m_tile.getWorldObj();
        }

        @Override
        public boolean equals(IPeripheral other) {
            return other instanceof TileBoundPeripheral && ((TileBoundPeripheral) other).m_tile == m_tile;
        }
    }

    // -------------------------------------------------------------------------
    // IPeripheral
    // -------------------------------------------------------------------------

    @Override
    public String getType() {
        return "npc_detector";
    }

    @Override
    public String[] getMethodNames() {
        return METHOD_NAMES;
    }

    @Override
    public void attach(IComputerAccess computer) {}

    @Override
    public void detach(IComputerAccess computer) {}

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] args)
        throws LuaException, InterruptedException {

        // Method 9 (getMaxRange) works without CustomNPCs
        if (method == 9) {
            return new Object[] { (double) ComputerCraft.npc_detector_max_range };
        }

        requireCnpc();

        switch (method) {
            case 0: { // getNpcs([radius])
                double radius = parseRadius(args, 0);
                return context.executeMainThreadTask(() -> new Object[] { scanNpcs(radius, null, null) });
            }
            case 1: { // getNpcsByName(name [, radius])
                String name = requireString(args, 0, "name");
                double radius = parseRadius(args, 1);
                return context.executeMainThreadTask(() -> new Object[] { scanNpcs(radius, name, null) });
            }
            case 2: { // getNpcsInFaction(factionName [, radius])
                String faction = requireString(args, 0, "factionName");
                double radius = parseRadius(args, 1);
                return context.executeMainThreadTask(() -> new Object[] { scanNpcs(radius, null, faction) });
            }
            case 3: { // getNearestNpc([radius])
                double radius = parseRadius(args, 0);
                return context.executeMainThreadTask(() -> {
                    Map<Object, Object> all = scanNpcs(radius, null, null);
                    return all.isEmpty() ? new Object[] { null } : new Object[] { all.get(1) };
                });
            }
            case 4: { // countNpcs([radius])
                double radius = parseRadius(args, 0);
                return context
                    .executeMainThreadTask(() -> new Object[] { (double) scanNpcs(radius, null, null).size() });
            }
            case 5: { // getPlayers([radius])
                double radius = parseRadius(args, 0);
                return context.executeMainThreadTask(() -> new Object[] { scanPlayers(radius) });
            }
            case 6: { // getNearestPlayer([radius])
                double radius = parseRadius(args, 0);
                return context.executeMainThreadTask(() -> {
                    Map<Object, Object> all = scanPlayers(radius);
                    return all.isEmpty() ? new Object[] { null } : new Object[] { all.get(1) };
                });
            }
            case 7: { // countPlayers([radius])
                double radius = parseRadius(args, 0);
                return context.executeMainThreadTask(() -> new Object[] { (double) scanPlayers(radius).size() });
            }
            case 8: { // getEntities([radius])
                double radius = parseRadius(args, 0);
                return context.executeMainThreadTask(() -> new Object[] { scanAll(radius) });
            }
            default:
                return null;
        }
    }

    // -------------------------------------------------------------------------
    // Scan helpers (all called on the main thread)
    // -------------------------------------------------------------------------

    private Map<Object, Object> scanNpcs(double radius, String nameFilter, String factionFilter) throws LuaException {
        IWorld world = resolveWorld();
        IEntity<?>[] entities = world.getEntitiesNear(getPositionX(), getPositionY(), getPositionZ(), radius);

        List<Map<String, Object>> result = new ArrayList<>();
        for (IEntity<?> e : entities) {
            if (!(e instanceof ICustomNpc)) continue;
            ICustomNpc<?> npc = (ICustomNpc<?>) e;
            if (nameFilter != null && !nameFilter.equals(npc.getName())) continue;
            if (factionFilter != null) {
                IFaction f = npc.getFaction();
                if (f == null || !factionFilter.equals(f.getName())) continue;
            }
            result.add(buildNpcTable(npc));
        }
        result.sort(Comparator.comparingDouble(t -> (Double) t.get("distance")));
        return toIndexedTable(result);
    }

    private Map<Object, Object> scanPlayers(double radius) throws LuaException {
        IWorld world = resolveWorld();
        IEntity<?>[] entities = world.getEntitiesNear(getPositionX(), getPositionY(), getPositionZ(), radius);

        List<Map<String, Object>> result = new ArrayList<>();
        for (IEntity<?> e : entities) {
            if (!(e instanceof IPlayer)) continue;
            result.add(buildPlayerTable((IPlayer<?>) e));
        }
        result.sort(Comparator.comparingDouble(t -> (Double) t.get("distance")));
        return toIndexedTable(result);
    }

    private Map<Object, Object> scanAll(double radius) throws LuaException {
        IWorld world = resolveWorld();
        IEntity<?>[] entities = world.getEntitiesNear(getPositionX(), getPositionY(), getPositionZ(), radius);

        List<Map<String, Object>> result = new ArrayList<>();
        for (IEntity<?> e : entities) {
            if (e instanceof ICustomNpc) {
                Map<String, Object> t = buildNpcTable((ICustomNpc<?>) e);
                t.put("type", "npc");
                result.add(t);
            } else if (e instanceof IPlayer) {
                Map<String, Object> t = buildPlayerTable((IPlayer<?>) e);
                t.put("type", "player");
                result.add(t);
            }
        }
        result.sort(Comparator.comparingDouble(t -> (Double) t.get("distance")));
        return toIndexedTable(result);
    }

    /** Converts a list of string-keyed maps to a 1-based integer-keyed Lua array table. */
    private static Map<Object, Object> toIndexedTable(List<Map<String, Object>> list) {
        Map<Object, Object> table = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            table.put(i + 1, list.get(i));
        }
        return table;
    }

    // -------------------------------------------------------------------------
    // Table builders (package-private for unit testing)
    // -------------------------------------------------------------------------

    Map<String, Object> buildNpcTable(ICustomNpc<?> npc) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", npc.getName());
        t.put("title", npc.getTitle());
        t.put("uuid", npc.getUniqueID());
        t.put("x", npc.getX());
        t.put("y", npc.getY());
        t.put("z", npc.getZ());
        t.put("distance", distanceTo(npc));
        t.put("health", (double) npc.getHealth());
        t.put("maxHealth", (double) npc.getMaxHealth());
        t.put("isAlive", npc.isAlive());
        t.put("isAttacking", npc.isAttacking());
        IEntityLivingBase<?> target = npc.getAttackTarget();
        t.put("target", target != null ? target.getTypeName() : null);
        t.put("movingType", nameFromTable(MOVING_TYPE_NAMES, npc.getMovingType()));
        IFaction faction = npc.getFaction();
        t.put("factionName", faction != null ? faction.getName() : "");
        t.put("factionId", faction != null ? (double) faction.getId() : -1.0);
        IJob job = npc.getJob();
        t.put("jobType", job != null ? nameFromTable(JOB_TYPE_NAMES, job.getType()) : "none");
        IRole role = npc.getRole();
        t.put("roleType", role != null ? nameFromTable(ROLE_TYPE_NAMES, role.getType()) : "none");
        return t;
    }

    Map<String, Object> buildPlayerTable(IPlayer<?> player) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", player.getName());
        t.put("uuid", player.getUniqueID());
        t.put("x", player.getX());
        t.put("y", player.getY());
        t.put("z", player.getZ());
        t.put("distance", distanceTo(player));
        t.put("health", (double) player.getHealth());
        t.put("maxHealth", (double) player.getMaxHealth());
        t.put("gameMode", (double) player.getMode());
        return t;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private IWorld resolveWorld() throws LuaException {
        try {
            noppes.npcs.api.AbstractNpcAPI api = noppes.npcs.api.AbstractNpcAPI.Instance();
            if (api == null) throw new LuaException("CustomNPCs API unavailable");
            World mcWorld = getMcWorld();
            if (mcWorld == null) throw new LuaException("World not available");
            IWorld world = api.getIWorld(mcWorld);
            if (world == null) throw new LuaException("World not available");
            return world;
        } catch (LuaException e) {
            throw e;
        } catch (Throwable t) {
            throw new LuaException("CustomNPCs API unavailable");
        }
    }

    private double distanceTo(IEntity<?> e) {
        double dx = e.getX() - getPositionX();
        double dy = e.getY() - getPositionY();
        double dz = e.getZ() - getPositionZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Parses an optional radius argument at {@code args[index]}, capped to
     * {@link ComputerCraft#npc_detector_max_range}. Defaults to the cap if absent.
     */
    static double parseRadius(Object[] args, int index) throws LuaException {
        double max = ComputerCraft.npc_detector_max_range;
        if (args.length <= index || args[index] == null) return max;
        if (!(args[index] instanceof Number)) {
            throw new LuaException("Expected number for radius");
        }
        double r = ((Number) args[index]).doubleValue();
        return Math.min(r, max);
    }

    private static void requireCnpc() throws LuaException {
        try {
            if (!noppes.npcs.api.AbstractNpcAPI.IsAvailable()) {
                throw new LuaException("CustomNPCs is not installed");
            }
        } catch (LuaException e) {
            throw e;
        } catch (Throwable t) {
            throw new LuaException("CustomNPCs is not installed");
        }
    }

    static String requireString(Object[] args, int index, String name) throws LuaException {
        if (args.length <= index || !(args[index] instanceof String)) {
            throw new LuaException("Expected string for " + name);
        }
        return (String) args[index];
    }
}
