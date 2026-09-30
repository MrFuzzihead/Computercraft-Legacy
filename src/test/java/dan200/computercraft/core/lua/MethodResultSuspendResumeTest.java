package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.LuaTable;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * End-to-end tests for the {@link MethodResult} suspend/resume round trip, driving a real
 * {@link CobaltMachine} and a real {@code LuaState}.
 *
 * <p>
 * <b>Why this exists.</b> Every other test in the suite stubs {@link ILuaContext}, so the bridge's
 * inbound path -- event conversion, correlation matching, and resumption -- was executed by nothing.
 * Five bugs shipped into that path during the port, each of which hung a turtle silently, with no
 * error anywhere:
 *
 * <ol>
 * <li>{@code Varargs.arg(int)} is 1-based; a 0-based loop shifted every event.</li>
 * <li>Cobalt's {@code isString()} is {@code type == TSTRING || type == TNUMBER}, so every numeric
 * correlation id became the String {@code "1"} and never compared equal.</li>
 * <li>Resuming needs {@code LuaThread.run}, not {@code LuaThread#resume}; resume throws
 * "cannot resume from a suspended thread".</li>
 * <li>{@code pendingSuspend} was cleared in {@code handleEvent} and again in {@code resume}, so
 * every resumed call returned an empty result.</li>
 * <li>The suspend check sat inside the Lua {@code eventFilter} guard, discarding the very event a
 * parked peripheral was waiting for.</li>
 * </ol>
 *
 * <p>
 * All five were found by reading source and were diagnosed wrongly at least once. These tests
 * assert on the observable behaviour, so a regression fails here rather than in a game world.
 */
class MethodResultSuspendResumeTest {

    /** An API method that records what it was called with, then asks to wait. */
    private static final class WaitingAPI implements ILuaAPI {

        final List<Object[]> received = new ArrayList<>();

        /** The correlation id the fake peripheral will answer on. */
        private final long id;

        /** When set, the peripheral answers with {@code (id, false, message)}. */
        private final String failureMessage;

        WaitingAPI(long id) {
            this(id, null);
        }

        WaitingAPI(long id, String failureMessage) {
            this.id = id;
            this.failureMessage = failureMessage;
        }

        @Override
        public String[] getNames() {
            return new String[] { "test" };
        }

        @Override
        public String[] getMethodNames() {
            return new String[] { "waitFor" };
        }

        @Override
        public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
            received.add(args);
            return new Object[] { failureMessage == null ? MethodResult.event("test_response", id, 2)
                : MethodResult.event("test_failure", id, 2) };
        }

        @Override
        public void startup() {}

        @Override
        public void advance(double dt) {}

        @Override
        public void shutdown() {}
    }

    /** A peripheral that returns a value directly, with no waiting involved. */
    private static final class PlainAPI implements ILuaAPI {

        @Override
        public String[] getNames() {
            return new String[] { "plain" };
        }

        @Override
        public String[] getMethodNames() {
            return new String[] { "answer" };
        }

        @Override
        public Object[] callMethod(ILuaContext context, int method, Object[] args) {
            return new Object[] { 42.0 };
        }

        @Override
        public void startup() {}

        @Override
        public void advance(double dt) {}

        @Override
        public void shutdown() {}
    }

    /** Drives a machine through a BIOS that calls {@code test.waitFor(...)} and records the result. */
    private static LuaTable run(CobaltMachine machine, String bios) throws Exception {
        Field globalsField = CobaltMachine.class.getDeclaredField("globals");
        globalsField.setAccessible(true);
        LuaTable globals = (LuaTable) globalsField.get(machine);
        assertNotNull(globals, "CobaltMachine should expose its globals table");

        machine.loadBios(new ByteArrayInputStream(bios.getBytes(StandardCharsets.UTF_8)));
        return globals;
    }

    @Test
    @DisplayName("a waiting peripheral suspends, then resumes with the event's values")
    void suspendThenResume() throws Exception {
        WaitingAPI api = new WaitingAPI(42);
        CobaltMachine machine = LuaTestMachine.create();
        machine.addAPI(api);

        LuaTable globals = run(
            machine,
            // As the turtle convention does, values start at index 2 of the event, so the first
            // return is the success flag and the rest are the command's results.
            "local ok, b = test.waitFor('hello', 7)\n" + "got = tostring(b) .. '/' .. tostring(ok)\n");

        // First event: the BIOS runs until the peripheral asks to wait, and stops there.
        machine.handleEvent(null, new Object[0]);
        assertFalse(machine.isFinished(), "the machine should still be running after suspending");

        // The matching event resumes it and the BIOS runs to completion.
        machine.handleEvent("test_response", new Object[] { 42.0, Boolean.TRUE, "payload" });
        machine.handleEvent(null, new Object[0]);

        assertEquals(1, api.received.size(), "the peripheral should have been called once");
        assertEquals(
            "payload/true",
            globals.rawget("got")
                .toString(),
            "the resumed call must return its values");
        machine.unload();
    }

    @Test
    @DisplayName("a peripheral receives the arguments Lua actually passed")
    void peripheralReceivesArguments() throws Exception {
        WaitingAPI api = new WaitingAPI(1);
        CobaltMachine machine = LuaTestMachine.create();
        machine.addAPI(api);

        LuaTable globals = run(machine, "test.waitFor('hello', 7, true)\n");

        machine.handleEvent(null, new Object[0]);
        machine.handleEvent("test_response", new Object[] { 1.0, Boolean.TRUE, "ok" });

        assertEquals(1, api.received.size(), "the peripheral should have been called once");
        Object[] args = api.received.get(0);
        assertEquals(
            3,
            args.length,
            "the peripheral should see all three arguments, got " + java.util.Arrays.toString(args));
        assertEquals("hello", args[0]);
        assertEquals(7.0, ((Number) args[1]).doubleValue(), "a numeric argument must stay a number, not a String");
        assertEquals(Boolean.TRUE, args[2]);
        machine.unload();
    }

    @Test
    @DisplayName("unrelated events are dropped while a peripheral waits, and it stays parked")
    void unrelatedEventsAreDropped() throws Exception {
        WaitingAPI api = new WaitingAPI(42);
        CobaltMachine machine = LuaTestMachine.create();
        machine.addAPI(api);

        LuaTable globals = run(machine, "local ok, a = test.waitFor()\ngot = tostring(a)\n");

        machine.handleEvent(null, new Object[0]);

        // A different event name, and an event with the right name but the wrong correlation id.
        machine.handleEvent("key_up", new Object[] { 28.0 });
        machine.handleEvent("test_response", new Object[] { 99.0, Boolean.TRUE, "wrong" });

        assertTrue(
            globals.rawget("got")
                .isNil(),
            "the call must still be parked, so nothing past the wait should have run");

        // The real event finally arrives and the call completes.
        machine.handleEvent("test_response", new Object[] { 42.0, Boolean.TRUE, "payload" });
        assertEquals(
            "payload",
            globals.rawget("got")
                .toString(),
            "the parked call should resume on its own event");
        machine.unload();
    }

    @Test
    @DisplayName("a numeric correlation id matches regardless of its Java width")
    void correlationIdMatchesAcrossWidths() throws Exception {
        for (Object id : new Object[] { 42, 42L, 42.0 }) {
            WaitingAPI api = new WaitingAPI(42);
            CobaltMachine machine = LuaTestMachine.create();
            machine.addAPI(api);

            LuaTable globals = run(machine, "local ok, a = test.waitFor()\ngot = tostring(a)\n");

            machine.handleEvent(null, new Object[0]);
            machine.handleEvent("test_response", new Object[] { id, Boolean.TRUE, "matched" });

            assertEquals(
                "matched",
                globals.rawget("got")
                    .toString(),
                "correlation id " + id
                    + " ("
                    + id.getClass()
                        .getSimpleName()
                    + ") should have matched");
            machine.unload();
        }
    }

    @Test
    @DisplayName("an immediate MethodResult is returned without suspending")
    void immediateResultDoesNotSuspend() throws Exception {
        CobaltMachine machine = LuaTestMachine.create();
        machine.addAPI(new ILuaAPI() {

            @Override
            public String[] getNames() {
                return new String[] { "now" };
            }

            @Override
            public String[] getMethodNames() {
                return new String[] { "value" };
            }

            @Override
            public Object[] callMethod(ILuaContext context, int method, Object[] args) {
                return new Object[] { MethodResult.of("immediate") };
            }

            @Override
            public void startup() {}

            @Override
            public void advance(double dt) {}

            @Override
            public void shutdown() {}
        });

        LuaTable globals = run(machine, "got = now.value()\n");

        // No event is ever queued: an immediate result must not park the call.
        for (int i = 0; i < 10 && !machine.isFinished(); i++) {
            machine.handleEvent(null, new Object[0]);
        }

        assertEquals(
            "immediate",
            globals.rawget("got")
                .toString());
        machine.unload();
    }

    @Test
    @DisplayName("a plain peripheral returning values is unaffected by the sentinel")
    void plainPeripheralStillWorks() throws Exception {
        CobaltMachine machine = LuaTestMachine.create();
        machine.addAPI(new PlainAPI());

        LuaTable globals = run(machine, "got = plain.answer()\n");
        for (int i = 0; i < 10 && !machine.isFinished(); i++) {
            machine.handleEvent(null, new Object[0]);
        }

        // Cobalt renders an integral double Lua-style, so 42.0 prints as "42".
        assertEquals(
            "42",
            globals.rawget("got")
                .toString());
        machine.unload();
    }
}
