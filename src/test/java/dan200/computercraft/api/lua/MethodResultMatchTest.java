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

    @Test
    @DisplayName("a task failure runs the failure hook before raising")
    void taskFailureRunsHookThenThrows() {
        boolean[] ran = new boolean[1];
        MethodResult pending = MethodResult.task("task_complete", 7, () -> ran[0] = true);

        LuaException thrown = org.junit.jupiter.api.Assertions
            .assertThrows(LuaException.class, () -> feed(pending, "task_complete", 7.0, Boolean.FALSE, "it broke"));

        assertTrue(ran[0], "the failure hook must run before the error is raised");
        assertEquals("it broke", thrown.getMessage());
    }

    @Test
    @DisplayName("a task success does not run the failure hook")
    void taskSuccessSkipsHook() throws Exception {
        boolean[] ran = new boolean[1];
        MethodResult pending = MethodResult.task("task_complete", 7, () -> ran[0] = true);

        MethodResult answer = feed(pending, "task_complete", 7.0, Boolean.TRUE, "ok");

        assertNotNull(answer);
        assertTrue(answer.isImmediate());
        assertTrue(!ran[0], "a successful task must not run the failure hook");
    }

    @Test
    @DisplayName("a non-matching event does not run the failure hook")
    void nonMatchingEventSkipsHook() throws Exception {
        boolean[] ran = new boolean[1];
        MethodResult pending = MethodResult.task("task_complete", 7, () -> ran[0] = true);

        assertNull(feed(pending, "key_up", 7.0));
        assertTrue(!ran[0], "an unrelated event must not run the failure hook");
    }

    /**
     * The compatibility guarantee: an implementation written against ComputerCraft 1.7.10's
     * {@code Object[] callMethod} must still work, because {@code callMethodResult} defaults to
     * delegating to it. This is what lets companion mods compile unchanged.
     */
    @Test
    @DisplayName("callMethodResult defaults to delegating to the legacy callMethod")
    void defaultResultDelegatesToLegacyCallMethod() throws Exception {
        boolean[] legacyCalled = new boolean[1];

        ILuaObject legacy = new ILuaObject() {

            @Override
            public String[] getMethodNames() {
                return new String[] { "old" };
            }

            @Override
            public Object[] callMethod(ILuaContext context, int method, Object[] args) {
                legacyCalled[0] = true;
                return new Object[] { "legacy" };
            }
        };

        Object result = legacy.callMethodResult(null, 0, new Object[0]);

        assertTrue(legacyCalled[0], "a legacy implementation's callMethod must be invoked");
        assertTrue(result instanceof Object[], "the legacy return type must pass straight through");
        assertEquals("legacy", ((Object[]) result)[0]);
    }

    @Test
    @DisplayName("an overridden callMethodResult is used instead of callMethod")
    void overriddenResultWins() throws Exception {
        boolean[] legacyCalled = new boolean[1];

        ILuaObject modern = new ILuaObject() {

            @Override
            public String[] getMethodNames() {
                return new String[] { "new" };
            }

            @Override
            public Object[] callMethod(ILuaContext context, int method, Object[] args) {
                legacyCalled[0] = true;
                return new Object[] { "legacy" };
            }

            @Override
            public Object callMethodResult(ILuaContext context, int method, Object[] args) {
                return MethodResult.of("modern");
            }
        };

        Object result = modern.callMethodResult(null, 0, new Object[0]);

        assertTrue(!legacyCalled[0], "an overriding implementation must not fall back to callMethod");
        assertSame(
            MethodResult.of()
                .getClass(),
            result.getClass());
        assertTrue(((MethodResult) result).isImmediate());
    }
}
