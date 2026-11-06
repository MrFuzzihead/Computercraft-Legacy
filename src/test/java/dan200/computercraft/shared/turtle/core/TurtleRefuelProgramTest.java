package dan200.computercraft.shared.turtle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.ValueFactory;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.function.VarArgFunction;

import dan200.computercraft.core.lua.LuaTestMachine;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;

/**
 * Tests the {@code refuel} ROM program against a fake turtle.
 *
 * <p>
 * The program is the only place that decides <em>how many</em> refuel calls to make per slot, and that is where the
 * electric path bit: it used to ask for at most the item count, so a single battery — count one — was charged for one
 * fuel unit per run, whatever was requested. A burnt item is gone after its call; a drained one is not, so the program
 * has to keep working a slot while it still yields fuel.
 * </p>
 *
 * <p>
 * The fake turtle charges one fuel unit per unit of battery charge and 20 fuel per coal, so the numbers asserted below
 * are the program's decisions rather than a fuel source's.
 * </p>
 */
class TurtleRefuelProgramTest {

    private static final String PROGRAM = "src/main/resources/assets/computercraft/lua/rom/programs/turtle/refuel";

    private static final int BATTERY_SLOT = 1;
    private static final int COAL_SLOT = 2;
    private static final int COAL_FUEL = 20;

    /** Records what the program asked the turtle to do. */
    private static final class FakeTurtle {

        private final List<Integer> refuelRequests = new ArrayList<>();

        private int fuel;
        private int fuelLimit = 20000;
        private int batteryUnits = 400;
        private int coal = 10;
        private int selected;

        void select(int slot) {
            this.selected = slot;
        }

        int itemCount(int slot) {
            if (slot == BATTERY_SLOT) return 1;
            if (slot == COAL_SLOT) return this.coal;
            return 0;
        }

        boolean refuel(int request) {
            this.refuelRequests.add(request);

            if (this.selected == BATTERY_SLOT) {
                // Drained, not consumed: the item stays in the slot with less charge.
                int units = Math.min(Math.min(request, this.batteryUnits), this.fuelLimit - this.fuel);
                units = Math.max(units, 0);
                this.batteryUnits -= units;
                this.fuel += units;
                return true;
            }

            if (this.selected == COAL_SLOT) {
                int items = Math.min(Math.min(request, this.coal), (this.fuelLimit - this.fuel) / COAL_FUEL);
                items = Math.max(items, 0);
                this.coal -= items;
                this.fuel += items * COAL_FUEL;
                return true;
            }

            return false;
        }
    }

    // =========================================================================
    // "all"
    // =========================================================================

    @Test
    void allDrainsTheWholeBattery() throws Exception {
        FakeTurtle turtle = new FakeTurtle();
        turtle.coal = 0;

        run(turtle, "all");

        assertEquals(0, turtle.batteryUnits, "every unit of charge must be spent");
        assertEquals(400, turtle.fuel, "a unit of charge is one unit of fuel");
    }

    @Test
    void allKeepsWorkingASlotThatIsNotConsumed() throws Exception {
        FakeTurtle turtle = new FakeTurtle();

        run(turtle, "all");

        assertTrue(
            turtle.refuelRequests.size() > 1,
            "a drained item needs several calls, and one call was the bug: " + turtle.refuelRequests);
    }

    @Test
    void allAlsoConsumesBurnableItems() throws Exception {
        FakeTurtle turtle = new FakeTurtle();

        run(turtle, "all");

        assertEquals(0, turtle.coal, "every coal item must be burnt");
        assertEquals(400 + 10 * COAL_FUEL, turtle.fuel);
    }

    @Test
    void allNeverAsksForMoreThanTheRefuelLimit() throws Exception {
        FakeTurtle turtle = new FakeTurtle();

        run(turtle, "all");

        for (int request : turtle.refuelRequests) {
            assertTrue(request <= 64, "turtle.refuel rejects counts above 64, but the program asked for " + request);
            assertTrue(request > 0, "the program must not issue an empty refuel");
        }
    }

    @Test
    void allStopsWhenTheTankIsFull() throws Exception {
        FakeTurtle turtle = new FakeTurtle();
        turtle.coal = 0;
        turtle.fuelLimit = 150;

        run(turtle, "all");

        assertEquals(150, turtle.fuel, "the tank must stop at its limit");
        assertTrue(turtle.batteryUnits > 0, "the rest of the battery must be left alone");
        assertEquals(250, turtle.batteryUnits);
    }

    @Test
    void allDoesNotBurnItemsTheTankCannotHold() throws Exception {
        FakeTurtle turtle = new FakeTurtle();
        turtle.fuelLimit = 500;

        run(turtle, "all");

        // The battery fills 400 of the 500, leaving room for five coal at 20 fuel each.
        assertEquals(500, turtle.fuel);
        assertEquals(5, turtle.coal, "only the coal that fits may be burnt");
    }

    // =========================================================================
    // Explicit counts
    // =========================================================================

    @Test
    void oneMeansOneFuelUnitFromABattery() throws Exception {
        FakeTurtle turtle = new FakeTurtle();

        run(turtle);

        assertEquals(1, turtle.fuel);
        assertEquals(399, turtle.batteryUnits);
        assertEquals(1, turtle.refuelRequests.size(), "a count of one must not loop");
    }

    @Test
    void anExplicitCountStopsAtThatManyFuelUnits() throws Exception {
        FakeTurtle turtle = new FakeTurtle();

        run(turtle, "100");

        assertEquals(100, turtle.fuel, "a battery is charged for the count it was given");
        assertEquals(300, turtle.batteryUnits);
    }

    @Test
    void anExplicitCountAboveSixtyFourIsSplitIntoSeveralCalls() throws Exception {
        // turtle.refuel itself rejects counts above 64, so the program has to split the request.
        FakeTurtle turtle = new FakeTurtle();

        run(turtle, "100");

        for (int request : turtle.refuelRequests) {
            assertTrue(request <= 64, "the program asked for " + request);
        }
        assertTrue(turtle.refuelRequests.size() >= 2, "a limit above 64 needs more than one call");
    }

    @Test
    void anExplicitCountConsumesAtMostThatManyItems() throws Exception {
        FakeTurtle turtle = new FakeTurtle();
        turtle.batteryUnits = 0;

        run(turtle, "3");

        assertEquals(7, turtle.coal, "three coal should be burnt");
        assertEquals(3 * COAL_FUEL, turtle.fuel);
    }

    // =========================================================================
    // Harness
    // =========================================================================

    /** Runs the ROM program with the given arguments against {@code fake}. */
    private static void run(FakeTurtle fake, String... args) throws Exception {
        CobaltMachine machine = LuaTestMachine.create();
        Field field = CobaltMachine.class.getDeclaredField("globals");
        field.setAccessible(true);
        LuaTable globals = (LuaTable) field.get(machine);

        LuaTable turtle = new LuaTable();
        turtle.rawset("getFuelLevel", function((state, values) -> ValueFactory.valueOf(fake.fuel)));
        turtle.rawset("getFuelLimit", function((state, values) -> ValueFactory.valueOf(fake.fuelLimit)));
        turtle.rawset("select", function((state, values) -> {
            fake.select(
                values.arg(1)
                    .checkInteger());
            return Constants.NONE;
        }));
        turtle.rawset(
            "getItemCount",
            function(
                (state, values) -> ValueFactory.valueOf(
                    fake.itemCount(
                        values.arg(1)
                            .checkInteger()))));
        turtle.rawset(
            "refuel",
            function(
                (state, values) -> fake.refuel(
                    values.arg(1)
                        .checkInteger()) ? Constants.TRUE : Constants.NIL));

        globals.rawset("turtle", turtle);
        // CobaltMachine removes Lua's print; the program calls it, and the output is not what is under test.
        globals.rawset("print", function((state, values) -> Constants.NONE));

        String program = new String(Files.readAllBytes(Path.of(PROGRAM)), StandardCharsets.UTF_8);
        StringBuilder source = new StringBuilder("(function(...)\n").append(program)
            .append("\nend)(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) source.append(", ");
            source.append('"')
                .append(args[i])
                .append('"');
        }
        source.append(')');

        machine.loadBios(
            new ByteArrayInputStream(
                source.toString()
                    .getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < 50 && !machine.isFinished(); i++) {
            machine.handleEvent(null, null);
        }
        assertTrue(machine.isFinished(), "the refuel program must run to completion");
        machine.unload();
    }

    /** A Lua function backed by a Java lambda. */
    private interface Body {

        Varargs invoke(LuaState state, Varargs values) throws LuaError;
    }

    private static VarArgFunction function(Body body) {
        return new VarArgFunction() {

            @Override
            public Varargs invoke(LuaState state, Varargs values) throws LuaError {
                return body.invoke(state, values);
            }
        };
    }
}
