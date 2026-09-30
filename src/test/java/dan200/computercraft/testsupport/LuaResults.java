package dan200.computercraft.testsupport;

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
}
