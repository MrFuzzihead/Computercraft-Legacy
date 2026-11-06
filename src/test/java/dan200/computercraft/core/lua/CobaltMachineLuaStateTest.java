package dan200.computercraft.core.lua;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaValue;

import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * Tests for {@link CobaltMachine#getLuaState()}, the accessor debugging tools use to inspect the live VM.
 *
 * <p>
 * The state is built in the machine's constructor and never replaced, so the contract has two halves: the state is
 * stable, and it is the state the machine is actually running (proved by looking up a standard library the machine
 * installed into its globals).
 * </p>
 */
class CobaltMachineLuaStateTest {

    @Test
    void exposesTheLiveState() {
        CobaltMachine machine = LuaTestMachine.create();

        assertNotNull(machine.getLuaState());
    }

    @Test
    void theStateIsStableForTheMachinesLifetime() {
        CobaltMachine machine = LuaTestMachine.create();

        assertSame(machine.getLuaState(), machine.getLuaState());
    }

    @Test
    void theStateIsTheOneTheMachineRunsOn() {
        CobaltMachine machine = LuaTestMachine.create();

        LuaTable globals = machine.getLuaState()
            .globals();
        LuaValue string = globals.rawget("string");
        LuaValue coroutine = globals.rawget("coroutine");

        assertInstanceOf(LuaTable.class, string, "the string library must be installed in the exposed state's globals");
        assertInstanceOf(
            LuaTable.class,
            coroutine,
            "the coroutine library must be installed in the exposed state's globals");
    }
}
