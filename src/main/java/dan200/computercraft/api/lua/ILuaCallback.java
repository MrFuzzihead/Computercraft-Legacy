package dan200.computercraft.api.lua;

/**
 * A continuation called when a suspended Lua call is resumed.
 *
 * <p>
 * A {@link MethodResult} that carries a callback describes a call that is waiting for events. Each
 * time one arrives, {@link #resume(Object[])} is invoked with it and decides what happens next:
 *
 * <ul>
 * <li>Return {@link MethodResult#of(Object...)} to finish the call and hand those values to
 * Lua.</li>
 * <li>Return the same (or another) suspending {@code MethodResult} to keep waiting. The
 * coroutine yields again and the callback will be invoked on the next event.</li>
 * </ul>
 *
 * <p>
 * Putting the decision here rather than in the peripheral is what makes cleanup possible. A
 * WebSocket receive, for instance, races a timeout: the timer has to be cancelled when a message
 * finally arrives, but a {@code finally} block cannot survive a coroutine suspension. Cancelling
 * from {@link #resume} runs exactly when the wait ends, however it ended.
 *
 * @see MethodResult#pullEvent(ILuaCallback)
 */
public interface ILuaCallback {

    /**
     * Called with each event while the call is suspended.
     *
     * @param event The event, with its name at index 0. Never null, though individual elements may be.
     * @return Values to return to Lua, or a suspending {@code MethodResult} to keep waiting.
     * @throws LuaException To raise a Lua error from here.
     */
    MethodResult resume(Object[] event) throws LuaException;
}
