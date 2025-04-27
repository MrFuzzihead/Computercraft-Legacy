package dan200.computercraft.shared.peripheral.commandblock;

import net.minecraft.tileentity.TileEntityCommandBlock;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

public class CommandBlockPeripheral implements IPeripheral {

    private final TileEntityCommandBlock m_commandBlock;

    public CommandBlockPeripheral(TileEntityCommandBlock commandBlock) {
        this.m_commandBlock = commandBlock;
    }

    @Override
    public String getType() {
        return "command";
    }

    @Override
    public String[] getMethodNames() {
        return new String[] { "getCommand", "setCommand", "runCommand" };
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException, InterruptedException {
        switch (method) {
            case 0:
                return context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        return new Object[] { CommandBlockPeripheral.this.m_commandBlock.func_145993_a()
                            .func_145753_i() };
                    }
                });
            case 1:
                if (arguments.length >= 1 && arguments[0] instanceof String) {
                    final String command = (String) arguments[0];
                    context.issueMainThreadTask(new ILuaTask() {

                        @Override
                        public Object[] execute() throws LuaException {
                            CommandBlockPeripheral.this.m_commandBlock.func_145993_a()
                                .func_145752_a(command);
                            CommandBlockPeripheral.this.m_commandBlock.getWorldObj()
                                .markBlockForUpdate(
                                    CommandBlockPeripheral.this.m_commandBlock.xCoord,
                                    CommandBlockPeripheral.this.m_commandBlock.yCoord,
                                    CommandBlockPeripheral.this.m_commandBlock.zCoord);
                            return null;
                        }
                    });
                    return null;
                }

                throw new LuaException("Expected string");
            case 2:
                return context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        CommandBlockPeripheral.this.m_commandBlock.func_145993_a()
                            .func_145755_a(CommandBlockPeripheral.this.m_commandBlock.getWorldObj());
                        int result = CommandBlockPeripheral.this.m_commandBlock.func_145993_a()
                            .func_145760_g();
                        return result > 0 ? new Object[] { true } : new Object[] { false, "Command failed" };
                    }
                });
            default:
                return null;
        }
    }

    @Override
    public void attach(IComputerAccess computer) {}

    @Override
    public void detach(IComputerAccess computer) {}

    @Override
    public boolean equals(IPeripheral other) {
        return other != null && other.getClass() == this.getClass();
    }
}
