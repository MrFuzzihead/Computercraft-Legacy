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

    /** Values to return to Lua immediately, no waiting. */
    private final Object[] results;

    /** Name of the event to wait for, or null when {@link #results} should be returned as-is. */
    private final String eventName;

    /** Correlation ID the event must carry. */
    private final long taskId;

    /** Index of the first value in the event, i.e. how much of the event to strip. */
    private final int valueOffset;

    /**
     * Whether the event is {@code (name, id, success, ...values)} rather than
     * {@code (name, id, ...values)}. When true a false success flag becomes a {@link LuaException}.
     */
    private final boolean checkSuccess;

    private MethodResult(Object[] results, String eventName, long taskId, int valueOffset, boolean checkSuccess) {
        this.results = results;
        this.eventName = eventName;
        this.taskId = taskId;
        this.valueOffset = valueOffset;
        this.checkSuccess = checkSuccess;
    }

    /**
     * Return values to Lua immediately, without waiting.
     *
     * <p>
     * This exists so an API method can return a {@code MethodResult} uniformly, letting it wait in
     * some branches and not others.
     *
     * @param results The values to return.
     * @return A result carrying {@code results}.
     */
    public static MethodResult of(Object... results) {
        return new MethodResult(results, null, 0L, 0, false);
    }

    /**
     * Wait for a task result event of the form {@code (eventName, taskId, success, ...values)}.
     *
     * <p>
     * This is the shape produced by {@link ILuaContext#executeMainThreadTask}. A {@code false}
     * success flag is turned into a {@link LuaException} carrying the event's message, matching how
     * a task failure surfaces today.
     *
     * @param eventName The event to wait for.
     * @param taskId    The correlation ID the event must carry.
     * @return A result that waits for {@code eventName} with {@code taskId}.
     */
    public static MethodResult task(String eventName, long taskId) {
        return new MethodResult(null, eventName, taskId, 3, true);
    }

    /**
     * Wait for an event of the form {@code (eventName, eventId, ...values)}, returning everything
     * from {@code valueOffset} onwards.
     *
     * <p>
     * Unlike {@link #task}, the event carries no success flag; the values are passed through as-is
     * and any error is the event's own business. This is the shape the turtle uses, where the event
     * is {@code ("turtle_response", commandId, ...returnValues)}.
     *
     * @param eventName   The event to wait for.
     * @param eventId     The correlation ID the event must carry.
     * @param valueOffset Index of the first value to return, i.e. how much of the event to strip.
     * @return A result that waits for {@code eventName} with {@code eventId}.
     */
    public static MethodResult event(String eventName, long eventId, int valueOffset) {
        return new MethodResult(null, eventName, eventId, valueOffset, false);
    }

    /** Whether this result should be returned immediately rather than waited for. */
    public boolean isImmediate() {
        return eventName == null;
    }

    /** @return The values to return, when {@link #isImmediate()}. */
    public Object[] getResults() {
        return results;
    }

    /** @return The name of the event to wait for, or null if this result is immediate. */
    public String getEventName() {
        return eventName;
    }

    /** @return The correlation ID the event must carry. */
    public long getTaskId() {
        return taskId;
    }

    /** @return The index of the first value in the event. */
    public int getValueOffset() {
        return valueOffset;
    }

    /** @return Whether a false success flag in the event should raise a {@link LuaException}. */
    public boolean isCheckSuccess() {
        return checkSuccess;
    }

    /**
     * Whether {@code event} satisfies this result's filter.
     *
     * <p>
     * Mismatched events are not an error -- a computer routinely has unrelated events queued -- so
     * the bridge simply suspends again until one matches.
     *
     * @param event The event values, with the event name at index 0.
     * @return Whether the event matches.
     */
    public boolean matches(Object[] event) {
        if (event == null || event.length < 2) {
            return false;
        }
        if (eventName == null) {
            // An immediate result is never waiting for anything.
            return false;
        }
        if (!(event[0] instanceof String) || !eventName.equals(event[0])) {
            return false;
        }
        if (!(event[1] instanceof Number)) {
            return false;
        }
        return ((Number) event[1]).longValue() == taskId;
    }

    @Override
    public String toString() {
        return isImmediate() ? "MethodResult[immediate]" : "MethodResult[await " + eventName + "#" + taskId + "]";
    }
}
