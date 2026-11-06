package dan200.computercraft.testsupport;

import java.lang.reflect.Field;
import java.util.List;

import dan200.computercraft.impl.TurtleRefuelHandlers;

/**
 * Empties the turtle refuel handler registry between tests.
 *
 * <p>
 * The registry is append-only on purpose — nothing in the mod unregisters a handler, and exposing a {@code reset()} on
 * it would let an addon silently drop ComputerCraft's own furnace handler. Tests still need isolation, so they reach
 * the list reflectively from here instead. A rename of the field or the class breaks this loudly at test time, which is
 * the right place for that to surface.
 * </p>
 */
public final class RefuelHandlers {

    private RefuelHandlers() {}

    /** Removes every registered refuel handler. */
    public static void reset() {
        try {
            Field field = TurtleRefuelHandlers.class.getDeclaredField("handlers");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Object> handlers = (List<Object>) field.get(null);
            handlers.clear();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("TurtleRefuelHandlers no longer exposes its handler list", e);
        }
    }
}
