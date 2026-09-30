package dan200.computercraft.api.lua;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MethodResult#matches}, which decides whether a queued event is the one a
 * suspended peripheral call is waiting for.
 *
 * <p>
 * This exists because the corresponding test surface was missing entirely: every other test stubs
 * {@link ILuaContext} and so never executes the bridge's inbound path. A single off-by-one there
 * (Cobalt's {@code Varargs#arg(int)} is 1-based) silently made every event fail to match, so
 * turtles hung forever with no error anywhere.
 */
class MethodResultMatchTest {

    @Test
    @DisplayName("an event with the right name and correlation id matches")
    void matchesExactEvent() {
        MethodResult pending = MethodResult.event("turtle_response", 1, 2);
        assertTrue(pending.matches(new Object[] { "turtle_response", 1.0, Boolean.TRUE }));
    }

    @Test
    @DisplayName("the correlation id may arrive as any Number width")
    void matchesNumericIdWidths() {
        MethodResult pending = MethodResult.event("turtle_response", 1, 2);
        assertTrue(pending.matches(new Object[] { "turtle_response", Integer.valueOf(1), Boolean.TRUE }));
        assertTrue(pending.matches(new Object[] { "turtle_response", Long.valueOf(1L), Boolean.TRUE }));
        assertTrue(pending.matches(new Object[] { "turtle_response", Double.valueOf(1.0), Boolean.TRUE }));
    }

    @Test
    @DisplayName("a different correlation id does not match")
    void rejectsDifferentId() {
        MethodResult pending = MethodResult.event("turtle_response", 1, 2);
        assertFalse(pending.matches(new Object[] { "turtle_response", 2.0, Boolean.TRUE }));
    }

    @Test
    @DisplayName("a different event name does not match")
    void rejectsDifferentName() {
        MethodResult pending = MethodResult.event("turtle_response", 1, 2);
        assertFalse(pending.matches(new Object[] { "key_up", 1.0 }));
    }

    @Test
    @DisplayName("short, null and non-string first elements do not match")
    void rejectsMalformedEvents() {
        MethodResult pending = MethodResult.event("turtle_response", 1, 2);
        assertFalse(pending.matches(null));
        assertFalse(pending.matches(new Object[0]));
        assertFalse(pending.matches(new Object[] { "turtle_response" }));
        // The symptom of the 1-based Varargs#arg(int) bug: a leading null where the name
        // belonged shifted every field by one.
        assertFalse(pending.matches(new Object[] { null, "turtle_response", 1.0, Boolean.TRUE }));
        assertFalse(pending.matches(new Object[] { "turtle_response" }));
    }

    @Test
    @DisplayName("a non-numeric correlation id does not match")
    void rejectsNonNumericId() {
        MethodResult pending = MethodResult.event("turtle_response", 1, 2);
        assertFalse(pending.matches(new Object[] { "turtle_response", "one" }));
    }

    @Test
    @DisplayName("task results match on name and id, ignoring the success flag")
    void taskResultMatches() {
        MethodResult pending = MethodResult.task("task_complete", 7);
        assertTrue(pending.matches(new Object[] { "task_complete", 7.0, Boolean.TRUE, "ok" }));
        assertTrue(pending.matches(new Object[] { "task_complete", 7.0, Boolean.FALSE, "boom" }));
        assertFalse(pending.matches(new Object[] { "task_complete", 8.0, Boolean.TRUE }));
    }

    @Test
    @DisplayName("an immediate result never matches")
    void immediateNeverMatches() {
        MethodResult immediate = MethodResult.of("a", "b");
        assertTrue(immediate.isImmediate());
        assertFalse(immediate.matches(new Object[] { "anything", 1.0 }));
    }
}
