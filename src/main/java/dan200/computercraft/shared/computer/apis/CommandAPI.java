package dan200.computercraft.shared.computer.apis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.block.Block;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandManager;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerSelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.shared.computer.blocks.TileCommandComputer;
import dan200.computercraft.shared.util.BlockStateBridge;
import dan200.computercraft.shared.util.NBTUtil;
import dan200.computercraft.shared.util.WorldUtil;

public class CommandAPI implements ILuaAPI {

    private TileCommandComputer m_computer;

    public CommandAPI(TileCommandComputer computer) {
        this.m_computer = computer;
    }

    @Override
    public String[] getNames() {
        return new String[] { "commands" };
    }

    @Override
    public void startup() {}

    @Override
    public void advance(double dt) {}

    @Override
    public void shutdown() {}

    @Override
    public String[] getMethodNames() {
        return new String[] { "exec", "execAsync", "list", "getBlockPosition", "getBlockInfo", "getBlockInfos",
            "getDimension", "getEntities" };
    }

    private Map<Object, Object> createOutput(String output) {
        Map<Object, Object> result = new HashMap<>(1);
        result.put(1, output);
        return result;
    }

    private Object[] doCommand(String command) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && server.isCommandBlockEnabled()) {
            ICommandManager commandManager = server.getCommandManager();

            try {
                TileCommandComputer.CommandSender sender = this.m_computer.getCommandSender();
                sender.clearOutput();
                int result = commandManager.executeCommand(sender, command);
                return new Object[] { result > 0, sender.getOutput(), result };
            } catch (Throwable var6) {
                return new Object[] { false, this.createOutput("Java Exception Thrown: " + var6.toString()), 0 };
            }
        } else {
            return new Object[] { false, this.createOutput("Command blocks disabled by server"), 0 };
        }
    }

    private static boolean matchesPrefix(String name, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Builds a Lua block-info table for the block at (x, y, z) in {@code world}.
     *
     * <p>
     * Returns a table with:
     * <ul>
     * <li>{@code name} — the block's registry name (e.g. {@code "minecraft:stone"})</li>
     * <li>{@code state} — a table containing {@code metadata} (the raw 1.7.10 block metadata)</li>
     * <li>{@code nbt} — (optional) tile-entity NBT data if a tile entity is present</li>
     * </ul>
     *
     * <p>
     * Blocks at y-coordinates outside the world's height range are treated as air
     * with metadata 0. No exception is thrown for out-of-bounds coordinates here;
     * the caller is responsible for pre-validating if a hard error is desired.
     */
    /**
     * The canonical dimension name for a world. 1.7.10 has no dimension registry, so the three
     * vanilla dimensions are mapped to their modern ids and mod dimensions fall back to the
     * provider's own name.
     *
     * @param world the world
     * @return a name such as {@code minecraft:the_nether}
     */
    static String dimensionName(World world) {
        return dimensionName(world.provider.dimensionId, world.provider.getDimensionName());
    }

    /**
     * The pure half of {@link #dimensionName(World)}: an id plus the provider's own name.
     *
     * @param id           the dimension id
     * @param providerName the provider's name, used only for non-vanilla dimensions
     * @return the canonical name
     */
    static String dimensionName(int id, String providerName) {
        switch (id) {
            case 0:
                return "minecraft:overworld";
            case -1:
                return "minecraft:the_nether";
            case 1:
                return "minecraft:the_end";
            default: {
                if (providerName == null || providerName.isEmpty()) return "dimension" + id;
                return providerName.toLowerCase()
                    .replace(' ', '_');
            }
        }
    }

    /**
     * Resolves a dimension argument, accepting either an integer id (for compatibility) or a name.
     *
     * <p>
     * Accepted names are the modern ids ({@code minecraft:overworld}, {@code minecraft:the_nether},
     * {@code minecraft:the_end}), their bare forms ({@code overworld}, {@code the_nether},
     * {@code the_end}, {@code nether}, {@code end}), and any loaded provider's
     * {@link #dimensionName(World)}.
     * </p>
     *
     * @param arg      the argument, a Number or String
     * @param fallback the dimension to use when {@code arg} is absent or not a dimension
     * @return the resolved dimension id
     * @throws LuaException if a name was given that matches no loaded world
     */
    static int resolveDimension(Object arg, int fallback) throws LuaException {
        if (arg == null) return fallback;

        if (arg instanceof Number) return ((Number) arg).intValue();
        if (!(arg instanceof String)) throw new LuaException("Invalid dimension name");

        String name = ((String) arg).toLowerCase();
        if (name.equals("minecraft:overworld") || name.equals("overworld")) return 0;
        if (name.equals("minecraft:the_nether") || name.equals("the_nether") || name.equals("nether")) return -1;
        if (name.equals("minecraft:the_end") || name.equals("the_end") || name.equals("end")) return 1;

        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            for (World world : server.worldServers) {
                if (world != null && dimensionName(world).equals(name)) return world.provider.dimensionId;
            }
        }

        throw new LuaException("Unknown dimension");
    }

    /**
     * The world this computer is in, or {@code null} when there is no computer/server.
     *
     * <p>
     * Every caller must tolerate {@code null}: a command computer is created before the server
     * exists in some test and load paths, and dereferencing it eagerly turned a "no server"
     * error into an NPE.
     * </p>
     *
     * @return the world, or {@code null}
     */
    private World currentWorld() {
        return this.m_computer != null ? this.m_computer.getWorldObj() : null;
    }

    /**
     * Resolves a dimension argument to a world.
     *
     * @param arg      the argument, a Number or String
     * @param fallback the world to use when {@code arg} is absent
     * @return the world
     * @throws LuaException if no such dimension is loaded
     */
    private static World worldFor(Object arg, World fallback) throws LuaException {
        if (arg == null) {
            // The computer itself supplies the dimension; without one (no server) there is
            // nothing to look up.
            if (fallback == null) throw new LuaException("No server available");
            return fallback;
        }

        int id = resolveDimension(arg, fallback != null ? fallback.provider.dimensionId : 0);
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) throw new LuaException("No server available");

        World world = server.worldServerForDimension(id);
        if (world == null) throw new LuaException("Unknown dimension");
        return world;
    }

    private Map<Object, Object> buildBlockInfo(World world, int x, int y, int z) {
        final String name;
        final int metadata;
        if (y < 0 || y >= world.getHeight()) {
            name = "minecraft:air";
            metadata = 0;
        } else {
            Block block = world.getBlock(x, y, z);
            String rawName = Block.blockRegistry.getNameForObject(block);
            name = rawName != null ? rawName : "minecraft:air";
            metadata = world.getBlockMetadata(x, y, z);
        }

        Map<Object, Object> table = new HashMap<>();
        table.put("name", name);
        // Named states need GTNHLib; without it this degrades to { metadata = n }.
        table.put(
            "state",
            y >= 0 && y < world.getHeight()
                ? BlockStateBridge.getState(world, x, y, z, world.getBlock(x, y, z), metadata)
                : BlockStateBridge.metadataFallback(metadata));

        if (y >= 0 && y < world.getHeight()) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te != null) {
                NBTTagCompound nbt = new NBTTagCompound();
                te.writeToNBT(nbt);
                table.put("nbt", NBTUtil.toObject(nbt));
            }
        }

        return table;
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        switch (method) {
            case 0:
                if (arguments.length >= 1 && arguments[0] instanceof String) {
                    final String command = (String) arguments[0];
                    return new Object[] { context.executeMainThreadTask(new ILuaTask() {

                        @Override
                        public Object[] execute() throws LuaException {
                            return CommandAPI.this.doCommand(command);
                        }
                    }) };
                }

                throw new LuaException("Expected string");
            case 1:
                if (arguments.length >= 1 && arguments[0] instanceof String) {
                    final String command = (String) arguments[0];
                    long taskID = context.issueMainThreadTask(new ILuaTask() {

                        @Override
                        public Object[] execute() throws LuaException {
                            return CommandAPI.this.doCommand(command);
                        }
                    });
                    return new Object[] { taskID };
                }

                throw new LuaException("Expected string");
            case 2: {
                final List<String> listPrefixes = new ArrayList<>();
                for (Object arg : arguments) {
                    if (arg instanceof String) listPrefixes.add((String) arg);
                }
                return new Object[] { context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        int i = 1;
                        Map<Object, Object> result = new HashMap<>();
                        MinecraftServer server = MinecraftServer.getServer();
                        if (server != null) {
                            ICommandManager commandManager = server.getCommandManager();
                            ICommandSender commandSender = CommandAPI.this.m_computer.getCommandSender();
                            Map commands = commandManager.getCommands();

                            for (Object entryObject : commands.entrySet()) {
                                Entry entry = (Entry) entryObject;
                                String name = (String) entry.getKey();
                                ICommand command = (ICommand) entry.getValue();

                                try {
                                    if (command.canCommandSenderUseCommand(commandSender)) {
                                        if (listPrefixes.isEmpty() || CommandAPI.matchesPrefix(name, listPrefixes)) {
                                            result.put(i++, name);
                                        }
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }

                        return new Object[] { result };
                    }
                }) };
            }
            case 3:
                return new Object[] { this.m_computer.xCoord, this.m_computer.yCoord, this.m_computer.zCoord };
            case 4:
                if (arguments.length >= 3 && arguments[0] instanceof Number
                    && arguments[1] instanceof Number
                    && arguments[2] instanceof Number) {
                    final int x = ((Number) arguments[0]).intValue();
                    final int y = ((Number) arguments[1]).intValue();
                    final int z = ((Number) arguments[2]).intValue();
                    // The fourth argument is a dimension: an id (for compatibility) or a name.
                    final Object infoDim4 = arguments.length >= 4 ? arguments[3] : null;
                    return new Object[] { context.executeMainThreadTask(new ILuaTask() {

                        @Override
                        public Object[] execute() throws LuaException {
                            World world = CommandAPI.worldFor(infoDim4, CommandAPI.this.currentWorld());
                            ChunkCoordinates position = new ChunkCoordinates(x, y, z);
                            if (!WorldUtil.isBlockInWorld(world, position)) {
                                throw new LuaException("Coordinates out of range");
                            }
                            return new Object[] { CommandAPI.this.buildBlockInfo(world, x, y, z) };
                        }
                    }) };
                }

                throw new LuaException("Expected number, number, number");
            case 5: {
                if (arguments.length < 6) {
                    throw new LuaException("Expected number, number, number, number, number, number");
                }
                for (int argIdx = 0; argIdx < 6; argIdx++) {
                    if (!(arguments[argIdx] instanceof Number)) {
                        throw new LuaException("Expected number, number, number, number, number, number");
                    }
                }
                final int bMinX = Math.min(((Number) arguments[0]).intValue(), ((Number) arguments[3]).intValue());
                final int bMinY = Math.min(((Number) arguments[1]).intValue(), ((Number) arguments[4]).intValue());
                final int bMinZ = Math.min(((Number) arguments[2]).intValue(), ((Number) arguments[5]).intValue());
                final int bMaxX = Math.max(((Number) arguments[0]).intValue(), ((Number) arguments[3]).intValue());
                final int bMaxY = Math.max(((Number) arguments[1]).intValue(), ((Number) arguments[4]).intValue());
                final int bMaxZ = Math.max(((Number) arguments[2]).intValue(), ((Number) arguments[5]).intValue());
                final long volume = (long) (bMaxX - bMinX + 1) * (bMaxY - bMinY + 1) * (bMaxZ - bMinZ + 1);
                if (volume > 4096L) {
                    throw new LuaException("Too many blocks");
                }
                final Object infoDim5 = arguments.length >= 7 ? arguments[6] : null;
                return new Object[] { context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        World world = CommandAPI.worldFor(infoDim5, CommandAPI.this.currentWorld());
                        Map<Object, Object> result = new HashMap<>();
                        int idx = 1;
                        // CC:Tweaked order: ascending y, then z, then x. A program can index the
                        // result with x + z*width + y*width*depth + 1.
                        for (int by = bMinY; by <= bMaxY; by++) {
                            for (int bz = bMinZ; bz <= bMaxZ; bz++) {
                                for (int bx = bMinX; bx <= bMaxX; bx++) {
                                    result.put(idx++, CommandAPI.this.buildBlockInfo(world, bx, by, bz));
                                }
                            }
                        }
                        return new Object[] { result };
                    }
                }) };
            }
            case 6: {
                // Modern name for the computer's own dimension.
                World world = this.currentWorld();
                if (world == null) throw new LuaException("No server available");
                return new Object[] { dimensionName(world) };
            }
            case 7: {
                if (arguments.length < 1 || !(arguments[0] instanceof String)) {
                    throw new LuaException("Expected string");
                }
                final String selector = (String) arguments[0];
                return new Object[] { context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        return new Object[] { CommandAPI.this.getEntities(selector) };
                    }
                }) };
            }
            default:
                return null;
        }
    }

    /**
     * Resolves an entity selector to a list of entity detail tables.
     *
     * <p>
     * 1.7.10 has no {@code @e} selector and no entity tags/NBT/scores to filter on, so this is a
     * documented subset of the modern behaviour:
     * </p>
     * <ul>
     * <li>{@code @p}, {@code @r}, {@code @a} and player names, with the filters 1.7.10's
     * {@code PlayerSelector} supports ({@code x},{@code y},{@code z},{@code r},{@code rm},
     * {@code m},{@code c},{@code l},{@code team}), delegated to Forge rather than
     * reimplemented;</li>
     * <li>{@code @e} with {@code type=}, {@code distance=}/{@code r=}, {@code x=}/{@code y=}/
     * {@code z=} and {@code limit=}, implemented as a world scan.</li>
     * </ul>
     * <p>
     * Not supported, because 1.7.10 has no equivalent: {@code tag=}, {@code nbt=},
     * {@code scores=}, {@code predicate=} and {@code @s}.
     * </p>
     *
     * @param selector the selector or player name
     * @return one table per entity, each with name/displayName (and health/maxHealth when living)
     * @throws LuaException if the selector cannot be parsed
     */
    private List<Map<Object, Object>> getEntities(String selector) throws LuaException {
        try {
            if (selector.startsWith("@e")) {
                return scanEntities(selector);
            }

            EntityPlayerMP[] players = PlayerSelector.matchPlayers(this.m_computer.getCommandSender(), selector);
            List<Map<Object, Object>> results = new ArrayList<>();
            if (players != null) {
                for (EntityPlayerMP player : players) {
                    results.add(entityDetail(player));
                }
            }
            return results;
        } catch (LuaException e) {
            throw e;
        } catch (Throwable t) {
            // PlayerSelector reports parse errors with its own exception types.
            throw new LuaException("Invalid entity selector: " + t.getMessage());
        }
    }

    /**
     * A small {@code @e[...]} scanner: type, distance/r, x/y/z and limit.
     *
     * @param selector the selector, starting with {@code @e}
     * @return the matching entities
     * @throws LuaException if a filter is malformed or unsupported
     */
    @SuppressWarnings("unchecked")
    private List<Map<Object, Object>> scanEntities(String selector) throws LuaException {
        // Parse before touching the world, so a bad filter raises the right error even when
        // there is no computer to look through.
        double originX = 0.5;
        double originY = 0.5;
        double originZ = 0.5;
        String type = null;
        double maxDistance = Double.MAX_VALUE;
        double minDistance = 0.0;
        int limit = Integer.MAX_VALUE;

        int open = selector.indexOf('[');
        if (open >= 0) {
            if (!selector.endsWith("]")) throw new LuaException("Invalid entity selector: missing ]");
            String body = selector.substring(open + 1, selector.length() - 1)
                .trim();
            if (!body.isEmpty()) {
                for (String part : body.split(",")) {
                    int eq = part.indexOf('=');
                    if (eq < 0) throw new LuaException("Invalid entity selector: " + part.trim());
                    String key = part.substring(0, eq)
                        .trim();
                    String value = part.substring(eq + 1)
                        .trim();

                    if (key.equals("type")) {
                        type = value;
                    } else if (key.equals("limit")) {
                        limit = parseInt(value);
                    } else if (key.equals("distance") || key.equals("r")) {
                        maxDistance = parseDistance(value, true);
                    } else if (key.equals("rm") || key.equals("distance_min")) {
                        minDistance = parseDistance(value, false);
                    } else if (key.equals("x")) {
                        originX = parseDouble(value);
                    } else if (key.equals("y")) {
                        originY = parseDouble(value);
                    } else if (key.equals("z")) {
                        originZ = parseDouble(value);
                    } else {
                        // tag=, nbt=, scores= and friends have no 1.7.10 equivalent.
                        throw new LuaException("Unsupported selector filter: " + key);
                    }
                }
            }
        }

        World world = this.currentWorld();
        if (world == null) throw new LuaException("No server available");

        // The default origin is the computer; explicit x=/y=/z= override it.
        if (this.m_computer != null) {
            originX = this.m_computer.xCoord + 0.5;
            originY = this.m_computer.yCoord + 0.5;
            originZ = this.m_computer.zCoord + 0.5;
        }

        List<Map<Object, Object>> results = new ArrayList<>();
        for (Object entry : world.loadedEntityList) {
            if (!(entry instanceof Entity)) continue;
            Entity entity = (Entity) entry;
            if (entity instanceof EntityPlayer) continue; // players use the @a/@p/@r path

            if (type != null) {
                String entityName = entityTypeName(entity);
                if (!entityName.equalsIgnoreCase(type) && !entityName.endsWith(":" + type.toLowerCase())) continue;
            }

            double dx = entity.posX - originX;
            double dy = entity.posY - originY;
            double dz = entity.posZ - originZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > maxDistance || distance < minDistance) continue;

            results.add(entityDetail(entity));
            if (results.size() >= limit) break;
        }
        return results;
    }

    private static int parseInt(String value) throws LuaException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new LuaException("Invalid number: " + value);
        }
    }

    private static double parseDouble(String value) throws LuaException {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new LuaException("Invalid number: " + value);
        }
    }

    /** Parses {@code 10} or the {@code ..10} / {@code 1..10} range syntax, taking the bound. */
    private static double parseDistance(String value, boolean upper) throws LuaException {
        if (value.startsWith("..")) return parseDouble(value.substring(2));
        int range = value.indexOf("..");
        if (range >= 0) {
            return upper ? parseDouble(value.substring(range + 2)) : parseDouble(value.substring(0, range));
        }
        return parseDouble(value);
    }

    /**
     * The name a program sees for an entity. 1.7.10 has no entity registry id, so this uses
     * {@code EntityList.getEntityString} lowercased; it is not prefixed with a namespace because
     * mod entities would get the wrong one.
     *
     * @param entity the entity
     * @return the name, or {@code unknown} when 1.7.10 has no mapping
     */
    static String entityTypeName(Entity entity) {
        String name = EntityList.getEntityString(entity);
        return name != null ? name.toLowerCase() : "unknown";
    }

    /** The detail table for one entity, matching CC:Tweaked's shape. */
    private static Map<Object, Object> entityDetail(Entity entity) {
        Map<Object, Object> detail = new HashMap<>();
        detail.put("name", entityTypeName(entity));
        detail.put("displayName", entity.getCommandSenderName());
        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            detail.put("health", (double) living.getHealth());
            detail.put("maxHealth", (double) living.getMaxHealth());
        }
        return detail;
    }
}
