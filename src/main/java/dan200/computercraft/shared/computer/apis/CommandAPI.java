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
        return new String[] { "exec", "execAsync", "list", "getBlockPosition", "getBlockInfo", "getBlockInfos" };
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

        Map<Object, Object> stateTable = new HashMap<>();
        stateTable.put("metadata", metadata);

        Map<Object, Object> table = new HashMap<>();
        table.put("name", name);
        table.put("state", stateTable);

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
                    return context.executeMainThreadTask(new ILuaTask() {

                        @Override
                        public Object[] execute() throws LuaException {
                            return CommandAPI.this.doCommand(command);
                        }
                    });
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
                return context.executeMainThreadTask(new ILuaTask() {

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
                });
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
                    final int infoDim4;
                    final boolean infoHasDim4;
                    if (arguments.length >= 4 && arguments[3] instanceof Number) {
                        infoDim4 = ((Number) arguments[3]).intValue();
                        infoHasDim4 = true;
                    } else {
                        infoDim4 = 0;
                        infoHasDim4 = false;
                    }
                    return context.executeMainThreadTask(new ILuaTask() {

                        @Override
                        public Object[] execute() throws LuaException {
                            World world;
                            if (infoHasDim4) {
                                MinecraftServer server = MinecraftServer.getServer();
                                if (server == null) throw new LuaException("No server available");
                                world = server.worldServerForDimension(infoDim4);
                                if (world == null) throw new LuaException("Unknown dimension");
                            } else {
                                world = CommandAPI.this.m_computer.getWorldObj();
                            }
                            ChunkCoordinates position = new ChunkCoordinates(x, y, z);
                            if (!WorldUtil.isBlockInWorld(world, position)) {
                                throw new LuaException("Coordinates out of range");
                            }
                            return new Object[] { CommandAPI.this.buildBlockInfo(world, x, y, z) };
                        }
                    });
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
                final int infoDim5;
                final boolean infoHasDim5;
                if (arguments.length >= 7 && arguments[6] instanceof Number) {
                    infoDim5 = ((Number) arguments[6]).intValue();
                    infoHasDim5 = true;
                } else {
                    infoDim5 = 0;
                    infoHasDim5 = false;
                }
                return context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        World world;
                        if (infoHasDim5) {
                            MinecraftServer server = MinecraftServer.getServer();
                            if (server == null) throw new LuaException("No server available");
                            world = server.worldServerForDimension(infoDim5);
                            if (world == null) throw new LuaException("Unknown dimension");
                        } else {
                            world = CommandAPI.this.m_computer.getWorldObj();
                        }
                        Map<Object, Object> result = new HashMap<>();
                        int idx = 1;
                        for (int bx = bMinX; bx <= bMaxX; bx++) {
                            for (int by = bMinY; by <= bMaxY; by++) {
                                for (int bz = bMinZ; bz <= bMaxZ; bz++) {
                                    result.put(idx++, CommandAPI.this.buildBlockInfo(world, bx, by, bz));
                                }
                            }
                        }
                        return new Object[] { result };
                    }
                });
            }
            default:
                return null;
        }
    }
}
