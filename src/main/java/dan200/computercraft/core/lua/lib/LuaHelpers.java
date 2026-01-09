package dan200.computercraft.core.lua.lib;

import java.lang.reflect.Field;

import org.luaj.vm2.LuaTable;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.lua.ILuaMachine;
import dan200.computercraft.core.lua.LuaJLuaMachine;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * Various classes for helping with Lua conversion
 */
public class LuaHelpers {
    /**
     * Simple method which creates varargs and delegates to the delegator. (I know how stupid that sounds).
     *
     * This exists so I don't have to grow the stack size.
     *
     * @see org.squiddev.cctweaks.lua.asm.binary.BinaryMachine#patchWrappedObject(ClassVisitor)
     */
    /*
     * public static Object[] delegateLuaObject(ILuaObject object, ILuaContext context, int method, Varargs arguments)
     * throws LuaException, InterruptedException {
     * return ArgumentDelegator.delegateLuaObject(object, context, method, new LuaJArguments(arguments));
     * }
     */

    /**
     * Wraps an exception, defaulting to another string on an empty message
     *
     * @param e   The exception to wrap
     * @param def The default message
     * @return The created exception
     */
    public static LuaException rewriteException(Throwable e, String def) {
        String message = e.getMessage();
        return new LuaException((message == null || message.isEmpty()) ? def : message);
    }

    /**
     * Wraps an exception, including its type
     *
     * @param e The exception to wrap
     * @return The created exception
     */
    public static LuaException rewriteWholeException(Throwable e) {
        return e instanceof LuaException ? (LuaException) e : new LuaException(e.toString());
    }

    public static ILuaMachine createMachine(Computer computer) {
        if (ComputerCraft.cobalt) {
            return new CobaltMachine(computer);
        } else {
            LuaJLuaMachine machine = new LuaJLuaMachine(computer);
            LuaTable env = null;
            try {
                env = (LuaTable) getGlobals.get(machine);
            } catch (IllegalAccessException e) {
                ComputerCraft.logger.error("Cannot get LuaJLuaMachine.m_globals", e);
            }

            if (env != null) {
                // if (ComputerCraft.bigInteger) BigIntegerValue.setup(env);
                // if (ComputerCraft.bitop) BitOpLib.setup(env);
            }

            return machine;
        }
    }

    private static Field getGlobals = null;

    static {
        try {
            getGlobals = LuaJLuaMachine.class.getDeclaredField("m_globals");
            getGlobals.setAccessible(true);
        } catch (NoSuchFieldException e) {
            ComputerCraft.logger.error("Cannot load LuaJLuaMachine.m_globals", e);
        }
    }
}
