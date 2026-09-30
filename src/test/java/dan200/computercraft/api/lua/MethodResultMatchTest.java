package dan200.computercraft.api.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MethodResult}'s event factories and for the callback protocol they are
 * built on.
 *
 * <p>
 * The factories are sugar over {@link ILuaCallback}: a matching event produces values, and
 * anything else produces {@code null}, meaning "keep waiting". These tests pin that contract
 * directly, because in game a mismatch is silent -- the call simply parks forever.
 */
class MethodResultMatchTest {

    /** One call through a suspending result, feeding it {@code event}. */
    private static MethodResult feed(MethodResult pending, Object... event) throws LuaException {
        return pending.resumeWith(event);
    }

    @Test
    @DisplayName("a matching event produces the values from the given offset")
    void eventTakesValuesFromOffset() throws Exception {
        MethodResult pending = MethodResult.event("turtle_response", 1, 3);
        MethodResult answer = feed(pending, "turtle_response", 1.0, Boolean.TRUE, "payload");

        assertNotNull(answer, "a matching event should finish the call");
        assertTrue(answer.isImmediate());
        assertEquals("payload", answer.getResults()[0]);
    }

    @Test
    @DisplayName("the correlation id may arrive as any Number width")
    void correlationIdMatchesAcrossWidths() throws Exception {
        for (Object id : new Object[] { Integer.valueOf(1), Long.valueOf(1L), Double.valueOf(1.0) }) {
            MethodResult answer = feed(MethodResult.event("turtle_response", 1, 2), "turtle_response", id, true);
            assertNotNull(
                answer,
                "id " + id
                    + " ("
                    + id.getClass()
                        .getSimpleName()
                    + ") should have matched");
        }
    }

    @Test
    @DisplayName("a different name or correlation id keeps waiting")
    void nonMatchingEventsKeepWaiting() throws Exception {
        MethodResult pending = MethodResult.event("turtle_response", 1, 2);
        assertNull(feed(pending, "key_up", 1.0), "wrong name should keep waiting");
        assertNull(feed(pending, "turtle_response", 2.0), "wrong id should keep waiting");
        assertNull(feed(pending, "turtle_response"), "short event should keep waiting");
        assertNull(feed(pending), "null event should keep waiting");
        assertNull(
            feed(pending, null, "turtle_response", 1.0),
            "a leading null means the event was shifted, so it should keep waiting");
        assertNull(feed(pending, "turtle_response", "one"), "non-numeric id should keep waiting");
    }

    @Test
    @DisplayName("a task success yields the values after the success flag")
    void taskSuccessYieldsValues() throws Exception {
        MethodResult answer = feed(MethodResult.task("task_complete", 7), "task_complete", 7.0, Boolean.TRUE, "ok");

        assertNotNull(answer);
        assertTrue(answer.isImmediate());
        assertEquals(1, answer.getResults().length);
        assertEquals("ok", answer.getResults()[0]);
    }

    @Test
    @DisplayName("a task failure raises a Lua error carrying its message")
    void taskFailureRaisesLuaError() {
        MethodResult pending = MethodResult.task("task_complete", 7);
        LuaException thrown = org.junit.jupiter.api.Assertions
            .assertThrows(LuaException.class, () -> feed(pending, "task_complete", 7.0, Boolean.FALSE, "it broke"));

        assertEquals("it broke", thrown.getMessage());
    }

    @Test
    @DisplayName("an immediate result returns its values and never waits")
    void immediateReturnsValues() throws Exception {
        MethodResult immediate = MethodResult.of("a", "b");

        assertTrue(immediate.isImmediate());
        assertEquals(2, immediate.getResults().length);
        assertNull(immediate.getCallback());
        assertNull(
            immediate.resumeWith(new Object[] { "anything", 1.0 }),
            "an immediate result has no callback, so offering it an event does nothing");
    }

    @Test
    @DisplayName("pullEvent hands every event to the callback, and null means keep waiting")
    void pullEventDelegatesEveryEvent() throws Exception {
        MethodResult stop = MethodResult.of("stopped");
        MethodResult waiting = MethodResult.pullEvent(event -> "stop".equals(event[0]) ? stop : null);

        // Returning null is how a callback says "not mine": the bridge keeps the call suspended
        // and offers it the next event.
        assertNull(waiting.resumeWith(new Object[] { "other" }), "an unmatched event keeps waiting");
        assertSame(stop, waiting.resumeWith(new Object[] { "stop" }), "a matching event finishes the call");
    }

    @Test
    @DisplayName("of() with no values still returns immediately")
    void immediateWithNoValues() {
        MethodResult empty = MethodResult.of();
        assertTrue(empty.isImmediate());
        assertEquals(0, empty.getResults().length);
    }
}
