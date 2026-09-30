package dan200.computercraft.testsupport;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;

/**
 * Unwraps the {@link MethodResult} sentinel from a peripheral's return value.
 *
 * <p>
 * A peripheral that must wait returns a single-element array holding a {@code MethodResult} rather
 * than values; the Lua bridge in {@code CobaltMachine} recognises that and either returns the
 * values (immediate) or suspends the coroutine. Unit tests call {@code callMethod} directly and
 * bypass the bridge, so they see the sentinel and must unwrap it themselves.
 */
public final class LuaResults {

    private LuaResults() {}

    /**
     * Unwrap a single-element array holding an immediate {@link MethodResult}.
     *
     * @param results The raw return value of {@code callMethod}.
     * @return The unwrapped values, or {@code results} unchanged if it is not the sentinel.
     * @throws AssertionError If the result is a suspending {@link MethodResult}. A unit test
     *                        driving a peripheral directly has no event loop to resume it, so a suspension cannot be
     *                        completed here.
     */
    public static Object[] unwrap(Object[] results) {
        if (results != null && results.length == 1 && results[0] instanceof MethodResult) {
            MethodResult pending = (MethodResult) results[0];
            if (pending.isImmediate()) {
                return pending.getResults();
            }
            throw new AssertionError("peripheral suspended (" + pending + "); no event loop in a unit test");
        }
        return results;
    }

    /**
     * Drive a suspending {@link MethodResult} to completion, mimicking what the Lua bridge does.
     *
     * <p>
     * {@code callMethod} returns the sentinel rather than values when a peripheral waits, because
     * only the bridge can supply the event that resumes it. A unit test calls {@code callMethod}
     * directly, so it has to play the bridge's part: offer queued events until the callback decides
     * the wait is over.
     *
     * @param results The raw return value of {@code callMethod}.
     * @param events  Events to offer, in order, until one ends the wait.
     * @return The values the call ultimately returned to Lua.
     * @throws LuaException   If the callback raised one.
     * @throws AssertionError If the events run out before the call finishes.
     */
    public static Object[] drive(Object[] results, java.util.List<Object[]> events) throws LuaException {
        if (results == null || results.length != 1 || !(results[0] instanceof MethodResult)) {
            return unwrap(results);
        }

        MethodResult pending = (MethodResult) results[0];
        for (Object[] event : events) {
            if (pending.isImmediate()) {
                break;
            }
            MethodResult next = pending.resumeWith(event);
            if (next != null) {
                pending = next;
            }
        }

        if (!pending.isImmediate()) {
            throw new AssertionError(
                "ran out of events while the call was still suspended; offered " + events.size() + " event(s)");
        }
        return pending.getResults();
    }
}
