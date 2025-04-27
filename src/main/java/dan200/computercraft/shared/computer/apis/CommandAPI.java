package dan200.computercraft.shared.computer.apis;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.block.Block;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandManager;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.shared.computer.blocks.TileCommandComputer;
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
        return new String[] { "exec", "execAsync", "list", "getBlockPosition", "getBlockInfo" };
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
                return new Object[] { result > 0, sender.getOutput() };
            } catch (Throwable var6) {
                return new Object[] { false, this.createOutput("Java Exception Thrown: " + var6.toString()) };
            }
        } else {
            return new Object[] { false, this.createOutput("Command blocks disabled by server") };
        }
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
            case 2:
                return context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        int i = 1;
                        Map<Object, Object> result = new HashMap<>();
                        MinecraftServer server = MinecraftServer.getServer();
                        if (server != null) {
                            ICommandManager commandManager = server.getCommandManager();
                            ICommandSender commmandSender = CommandAPI.this.m_computer.getCommandSender();
                            Map commands = commandManager.getCommands();

                            for (Object entryObject : commands.entrySet()) {
                                Entry entry = (Entry) entryObject;
                                String name = (String) entry.getKey();
                                ICommand command = (ICommand) entry.getValue();

                                try {
                                    if (command.canCommandSenderUseCommand(commmandSender)) {
                                        result.put(i++, name);
                                    }
                                } catch (Throwable var13) {}
                            }
                        }

                        return new Object[] { result };
                    }
                });
            case 3:
                return new Object[] { this.m_computer.xCoord, this.m_computer.yCoord, this.m_computer.zCoord };
            case 4:
                if (arguments.length >= 3 && arguments[0] instanceof Number
                    && arguments[1] instanceof Number
                    && arguments[2] instanceof Number) {
                    final int x = ((Number) arguments[0]).intValue();
                    final int y = ((Number) arguments[1]).intValue();
                    final int z = ((Number) arguments[2]).intValue();
                    return context.executeMainThreadTask(new ILuaTask() {

                        @Override
                        public Object[] execute() throws LuaException {
                            World world = CommandAPI.this.m_computer.getWorldObj();
                            ChunkCoordinates position = new ChunkCoordinates(x, y, z);
                            if (WorldUtil.isBlockInWorld(world, position)) {
                                Block block = world.getBlock(position.posX, position.posY, position.posZ);
                                String name = Block.blockRegistry.getNameForObject(block);
                                int metadata = world.getBlockMetadata(position.posX, position.posY, position.posZ);
                                Map<Object, Object> table = new HashMap<>();
                                table.put("name", name);
                                table.put("metadata", metadata);
                                return new Object[] { table };
                            } else {
                                throw new LuaException("Coordinates out of range");
                            }
                        }
                    });
                }

                throw new LuaException("Expected number, number, number");
            default:
                return null;
        }
    }
}
