package dan200.computercraft.api.lua;

/**
 * The context a Lua API method is executing in.
 *
 * <p>
 * <b>Breaking change (Cobalt 0.9.9 port).</b> This interface previously offered
 * {@link #pullEvent(String)}, {@link #pullEventRaw(String)} and {@link #yield(Object[])}, which
 * blocked the calling thread until an event arrived. Those methods are gone, and cannot be
 * reinstated: {@code ComputerThread} runs a computer's tasks serially on a worker pool, so a call
 * that blocked would also block the task that delivers the event it is waiting for, deadlocking the
 * computer.
 *
 * <p>
 * The replacement is {@link MethodResult}. An API method that needs to wait returns one describing
 * what to wait for; the Lua bridge turns that into a coroutine suspension, releases the computer's
 * thread, and resumes the call when a matching event arrives. Peripheral code never blocks and never
 * runs a retry loop of its own.
 *
 * <p>
 * Companion mods that only <em>read</em> the context are unaffected. Those that call
 * {@code pullEvent}/{@code yield} will not compile, which is deliberate: a compile error points at
 * the line to fix, where a runtime failure would surface later as a stuck computer.
 */
public interface ILuaContext {

    /**
     * Run a task on the computer's main thread and wait for its result.
     *
     * <p>
     * This no longer blocks. The task is queued and a {@link MethodResult} is returned immediately;
     * the surrounding Lua call is suspended by the bridge and resumed with the task's return values
     * once the {@code task_complete} event arrives. A task that fails raises a {@link LuaException}
     * in the Lua program, as before.
     *
     * @param task The task to run.
     * @return A result that waits for the task to complete. Never {@code null}.
     * @throws LuaException If the task could not be queued at all.
     * @see #issueMainThreadTask(ILuaTask)
     */
    MethodResult executeMainThreadTask(ILuaTask task) throws LuaException;

    /**
     * Queue a task on the computer's main thread without waiting for it.
     *
     * <p>
     * Use this for fire-and-forget work. To wait for the result, use
     * {@link #executeMainThreadTask(ILuaTask)}.
     *
     * @param task The task to run.
     * @return The task's unique ID, which will be carried by its completion event.
     * @throws LuaException If the task limit has been exceeded.
     */
    long issueMainThreadTask(ILuaTask task) throws LuaException;
}
