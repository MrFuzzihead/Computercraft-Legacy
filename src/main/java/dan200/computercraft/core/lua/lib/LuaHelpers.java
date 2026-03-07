package dan200.computercraft.core.lua.lib;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.lua.ILuaMachine;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * Various classes for helping with Lua conversion
 */
public class LuaHelpers {

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

    /**
     * Creates the Lua machine for a computer. Cobalt is always used.
     *
     * @param computer The computer to create the machine for
     * @return A new {@link CobaltMachine}
     */
    public static ILuaMachine createMachine(Computer computer) {
        return new CobaltMachine(computer);
    }
}
