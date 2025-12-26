package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

class LuaJLuaMachineTest {

    @Test
    void testIsFinishedInitially() {
        LuaJLuaMachine machine = new LuaJLuaMachine(null);
        assertTrue(machine.isFinished(), "New machine should be finished (no main routine)");
    }

    @Test
    void testLoadBiosCompletesImmediately() {
        LuaJLuaMachine machine = new LuaJLuaMachine(null);
        String lua = "return function() end";
        machine.loadBios(new ByteArrayInputStream(lua.getBytes(StandardCharsets.UTF_8)));
        // First resume should run the function (which returns immediately) and thus be finished
        machine.handleEvent(null, null);
        assertTrue(machine.isFinished(), "Machine should be finished after executing an immediate-return BIOS");
    }

    @Test
    void testLoadBiosYieldsThenUnload() {
        LuaJLuaMachine machine = new LuaJLuaMachine(null);
        String lua = "return function() coroutine.yield() end";
        machine.loadBios(new ByteArrayInputStream(lua.getBytes(StandardCharsets.UTF_8)));
        // Try to resume once; depending on LuaJ internals this may finish or yield.
        machine.handleEvent(null, null);
        // Unload should always clean up the main routine
        machine.unload();
        assertTrue(machine.isFinished(), "Machine should be finished after unload");
    }

    @Test
    void testCoroutineAbandonSuspendedSucceeds() throws Exception {
        LuaJLuaMachine machine = new LuaJLuaMachine(null);
        // Access private m_globals to call coroutine.* functions directly
        java.lang.reflect.Field gField = LuaJLuaMachine.class.getDeclaredField("m_globals");
        gField.setAccessible(true);
        LuaValue globals = (LuaValue) gField.get(machine);

        LuaValue load = globals.get("loadstring");
        LuaValue assertV = globals.get("assert");
        LuaValue coroutine = globals.get("coroutine");

        // Create a suspended coroutine (yields immediately)
        Varargs chunk = load.call(LuaValue.valueOf("return function() coroutine.yield() end"));
        LuaValue program = chunk.arg1();
        LuaThread thread = coroutine.get("create")
            .call(program)
            .checkthread();

        // Abandon the suspended coroutine
        Varargs res = coroutine.get("abandon")
            .invoke(LuaValue.varargsOf(new LuaValue[] { thread }));
        assertTrue(
            res.arg1()
                .toboolean(),
            "Abandoning a suspended coroutine should succeed");
    }

    @Test
    void testCoroutineAbandonDeadFails() throws Exception {
        LuaJLuaMachine machine = new LuaJLuaMachine(null);
        java.lang.reflect.Field gField = LuaJLuaMachine.class.getDeclaredField("m_globals");
        gField.setAccessible(true);
        LuaValue globals = (LuaValue) gField.get(machine);

        LuaValue load = globals.get("loadstring");
        LuaValue assertV = globals.get("assert");
        LuaValue coroutine = globals.get("coroutine");

        // Create a coroutine that immediately returns (dead)
        Varargs chunk = load.call(LuaValue.valueOf("return function() return 42 end"));
        LuaValue program = chunk.arg1();
        LuaThread thread = coroutine.get("create")
            .call(program)
            .checkthread();

        // Resume to completion
        coroutine.get("resume")
            .invoke(LuaValue.varargsOf(new LuaValue[] { thread }));
        assertEquals("dead", thread.getStatus());

        // Attempt to abandon should fail
        Varargs res = coroutine.get("abandon")
            .invoke(LuaValue.varargsOf(new LuaValue[] { thread }));
        assertFalse(
            res.arg1()
                .toboolean(),
            "Abandoning a dead coroutine should fail");
        String msg = res.arg(2)
            .isnil() ? null
                : res.arg(2)
                    .tojstring();
        assertNotNull(msg);
        assertTrue(msg.contains("cannot abandon"));
    }
}
