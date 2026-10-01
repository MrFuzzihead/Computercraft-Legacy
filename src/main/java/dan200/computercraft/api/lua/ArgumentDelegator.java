package dan200.computercraft.api.lua;

import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

/**
 * Used to delegate arguments
 */
public final class ArgumentDelegator {

    private ArgumentDelegator() {
        throw new RuntimeException("Cannot create ArgumentDelegator");
    }

    /**
     * Delegate arguments to a {@link ILuaObject}, choosing the best way to pass methods.
     *
     * @param object    The object to invoke.
     * @param context   The context of the currently running lua thread.
     * @param method    The method index to call
     * @param arguments An instance of {@link IArguments}, representing the arguments passed into peripheral.call().
     * @return An array of objects, or a {@link MethodResult} to suspend the call.
     * @throws LuaException         If the wrong arguments are supplied to your method.
     * @throws InterruptedException If the computer is terminated.
     * @see ILuaObject#callMethodResult(ILuaContext, int, Object[])
     * @see ILuaObjectWithArguments#callMethod(ILuaContext, int, IArguments)
     * @see IBinaryHandler
     */
    public static Object delegateLuaObject(ILuaObject object, ILuaContext context, int method, IArguments arguments)
        throws LuaException, InterruptedException {
        if (object instanceof ILuaObjectWithArguments) {
            return ((ILuaObjectWithArguments) object).callMethodResult(context, method, arguments);
        } else if (object instanceof IBinaryHandler) {
            return object.callMethodResult(context, method, arguments.asBinary());
        } else {
            return object.callMethodResult(context, method, arguments.asArguments());
        }
    }

    /**
     * Delegate arguments to a {@link IPeripheral}, choosing the best way to pass methods.
     *
     * @param peripheral The peripheral to invoke.
     * @param computer   The interface to the computercraft that is making the call.
     * @param context    The context of the currently running lua thread.
     * @param method     The method index to call
     * @param arguments  An instance of {@link IArguments}, representing the arguments passed into peripheral.call().
     * @return An array of objects, or a {@link MethodResult} to suspend the call.
     * @throws LuaException         If the wrong arguments are supplied to your method.
     * @throws InterruptedException If the computer is terminated.
     * @see IPeripheral#callMethodResult(IComputerAccess, ILuaContext, int, Object[])
     * @see IPeripheralWithArguments#callMethod(IComputerAccess, ILuaContext, int, IArguments)
     * @see IBinaryHandler
     */
    public static Object delegatePeripheral(IPeripheral peripheral, IComputerAccess computer, ILuaContext context,
        int method, IArguments arguments) throws LuaException, InterruptedException {
        if (peripheral instanceof IPeripheralWithArguments) {
            return ((IPeripheralWithArguments) peripheral).callMethodResult(computer, context, method, arguments);
        } else if (peripheral instanceof IBinaryHandler) {
            return peripheral.callMethodResult(computer, context, method, arguments.asBinary());
        } else {
            return peripheral.callMethodResult(computer, context, method, arguments.asArguments());
        }
    }
}
