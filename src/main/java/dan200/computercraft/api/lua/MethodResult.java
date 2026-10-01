package dan200.computercraft.api.lua;

/**
 * The result of a Lua API method that may need to wait for an event before it can complete.
 *
 * <p>
 * Historically an API method that had to wait -- a turtle movement, a scheduled task, a blocking
 * HTTP request -- called {@link ILuaContext#pullEvent} in a loop, blocking the computer's thread
 * until a matching event arrived. That no longer works, because {@code ComputerThread} executes a
 * computer's tasks serially on a worker pool: a blocked call also blocks the very task that would
 * have delivered the event, so the computer deadlocks.
 *
 * <p>
 * Instead, a method that needs to wait returns a {@code MethodResult} describing <em>what to wait
 * for</em>, and the Lua bridge turns that into a coroutine suspension. The computer's thread is
 * released immediately; the coroutine is resumed from
 * {@link dan200.computercraft.core.lua.lib.cobalt.CobaltMachine#handleEvent} once a matching event
 * arrives, and the method's caller continues from where it left off. The retry loop moves out of
 * the peripheral and into the bridge, where it is written once.
 *
 * <p>
 * This mirrors how CC: Tweaked's {@code MethodResult} works, and is why their
 * {@link ILuaContext} has no {@code pullEvent}/{@code yield} at all: blocking is not something a
 * Lua API method can do here.
 *
 * <h2>Using it</h2>
 *
 * A peripheral signals a suspension by returning a single-element array whose only element is a
 * {@code MethodResult}:
 *
 * <pre>
 * {@code
 * public Object[] callMethod(ILuaContext context, int method, Object[] args) {
 *     long id = issueCommand(args);
 *     return new Object[] { MethodResult.event("turtle_response", id, 2) };
 * }
 * }
 * </pre>
 *
 * The bridge recognises the sentinel, suspends, and on a matching event resumes the coroutine with
 * the event's values, which the method's caller then returns to Lua. The correlation ID is checked
 * for you, so mismatched events are skipped exactly as the old manual loop did.
 */
public final class MethodResult {

    /** Values to return to Lua immediately, or null when this result suspends. */
    private final Object[] results;

    /** Invoked with each event while suspended, or null when {@link #results} should be returned. */
    private final ILuaCallback callback;

    private MethodResult(Object[] results, ILuaCallback callback) {
        this.results = results;
        this.callback = callback;
    }

    /**
     * Return values to Lua immediately, without waiting.
     *
     * @param values The values to return.
     * @return A result carrying {@code values}.
     */
    public static MethodResult of(Object... values) {
        return new MethodResult(values == null || values.length == 0 ? new Object[0] : values, null);
    }

    /**
     * Wait for a task result event of the form {@code (eventName, taskId, success, ...values)}.
     *
     * <p>
     * This is the shape produced by {@link ILuaContext#executeMainThreadTask}. A {@code false}
     * success flag becomes a {@link LuaException} carrying the event's message, matching how a task
     * failure surfaced before the port.
     *
     * <p>
     * Events that do not match are ignored and the call keeps waiting; a computer routinely has
     * unrelated events queued.
     *
     * @param eventName The event to wait for.
     * @param taskId    The correlation ID the event must carry.
     * @return A result that waits for {@code eventName} with {@code taskId}.
     */
    public static MethodResult task(String eventName, long taskId) {
        return task(eventName, taskId, null);
    }

    /**
     * Wait for a task result event, running {@code onFailure} if the task reports failure.
     *
     * <p>
     * This exists because the error is raised here, inside the callback, rather than by the
     * method that queued the task. A caller that needs to clean up something it allocated for the
     * task -- cancelling the task's own queue entry, say -- used to do that in a catch block
     * around the wait, and that no longer runs.
     *
     * @param eventName The event to wait for.
     * @param taskId    The correlation ID the event must carry.
     * @param onFailure Run just before the Lua error is raised. May be null.
     * @return A result that waits for {@code eventName} with {@code taskId}.
     */
    public static MethodResult task(String eventName, long taskId, Runnable onFailure) {
        return pullEvent(event -> {
            if (!matches(event, eventName, taskId)) {
                return null;
            }
            // The success flag lives at index 2, before the values -- test it before slicing.
            if (event.length > 2 && event[2] instanceof Boolean && !(Boolean) event[2]) {
                if (onFailure != null) {
                    onFailure.run();
                }
                String message = event.length > 3 && event[3] instanceof String ? (String) event[3]
                    : "Java Exception Thrown";
                throw new LuaException(message, 0);
            }
            return of(java.util.Arrays.copyOfRange(event, 3, event.length));
        });
    }

    /**
     * Wait for an event of the form {@code (eventName, eventId, ...values)}, returning everything
     * from {@code valueOffset} onwards.
     *
     * <p>
     * Unlike {@link #task}, the event carries no success flag; the values are passed through as-is.
     * This is the shape the turtle uses, where the event is
     * {@code ("turtle_response", commandId, ...returnValues)} -- note the success flag at index 2 is
     * part of the returned values, exactly as it was before the port.
     *
     * @param eventName   The event to wait for.
     * @param eventId     The correlation ID the event must carry.
     * @param valueOffset Index of the first value to return, i.e. how much of the event to strip.
     * @return A result that waits for {@code eventName} with {@code eventId}.
     */
    public static MethodResult event(String eventName, long eventId, int valueOffset) {
        return pullEvent(
            event -> matches(event, eventName, eventId)
                ? of(java.util.Arrays.copyOfRange(event, valueOffset, event.length))
                : null);
    }

    /**
     * Wait for any event, deferring the decision to {@code callback}.
     *
     * <p>
     * The callback is given every event the computer receives while suspended, including unrelated
     * ones. Returning {@code null} from a callback means "not mine, keep waiting", which is how
     * {@link #event} and {@link #task} are implemented.
     *
     * @param callback Invoked with each event until it decides to finish.
     * @return A result that suspends and delegates to {@code callback}.
     */
    public static MethodResult pullEvent(ILuaCallback callback) {
        return new MethodResult(null, callback);
    }

    /**
     * Offer {@code event} to this result's callback.
     *
     * @param event The event, with its name at index 0.
     * @return The callback's answer, or {@code null} to keep waiting.
     * @throws LuaException If the callback raised one.
     */
    public MethodResult resumeWith(Object[] event) throws LuaException {
        return callback == null ? null : callback.resume(event);
    }

    /** Whether this result returns immediately rather than waiting. */
    public boolean isImmediate() {
        return callback == null;
    }

    /** @return The values to return, when {@link #isImmediate()}. */
    public Object[] getResults() {
        return results;
    }

    /** @return The callback that receives events, or null when {@link #isImmediate()}. */
    public ILuaCallback getCallback() {
        return callback;
    }

    /**
     * Whether {@code event} has the given name and correlation ID.
     *
     * <p>
     * Non-matching events are not an error -- a computer routinely has unrelated events queued --
     * so a false result simply means "keep waiting".
     *
     * @param event     The event values, with the event name at index 0.
     * @param eventName The name to require.
     * @param eventId   The correlation ID to require.
     * @return Whether the event matches.
     */
    public static boolean matches(Object[] event, String eventName, long eventId) {
        if (event == null || event.length < 2) {
            return false;
        }
        if (!(event[0] instanceof String) || !eventName.equals(event[0])) {
            return false;
        }
        if (!(event[1] instanceof Number)) {
            return false;
        }
        return ((Number) event[1]).longValue() == eventId;
    }

    @Override
    public String toString() {
        return isImmediate() ? "MethodResult[immediate]" : "MethodResult[suspended]";
    }
}
