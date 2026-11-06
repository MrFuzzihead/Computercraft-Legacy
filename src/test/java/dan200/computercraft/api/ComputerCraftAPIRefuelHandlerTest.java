package dan200.computercraft.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.OptionalInt;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleRefuelHandler;
import dan200.computercraft.impl.TurtleRefuelHandlers;
import dan200.computercraft.testsupport.RefuelHandlers;

/**
 * Tests that {@link ComputerCraftAPI#registerRefuelHandler} actually reaches the mod.
 *
 * <p>
 * This API is reflective: every call looks up a method on {@code dan200.computercraft.ComputerCraft} by name and
 * parameter types, and silently does nothing when the lookup fails. A renamed target method would therefore compile,
 * ship, and quietly stop accepting addon handlers. The first test closes that hole; the second pins the signature the
 * lookup depends on, so a failure says which one broke.
 * </p>
 */
class ComputerCraftAPIRefuelHandlerTest {

    @AfterEach
    void clearHandlers() {
        RefuelHandlers.reset();
    }

    @Test
    void theLookupTargetExists() throws Exception {
        Method target = ComputerCraft.class.getMethod("registerRefuelHandler", TurtleRefuelHandler.class);

        assertTrue(Modifier.isStatic(target.getModifiers()), "the API reflects into a static method");
        assertEquals(void.class, target.getReturnType());
        assertTrue(Modifier.isPublic(target.getModifiers()));
    }

    @Test
    void theApiForwardsIntoTheRegistry() {
        assertTrue(ComputerCraftAPI.isInstalled(), "the API must find ComputerCraft on this classpath");
        ComputerCraftAPI.registerRefuelHandler((turtle, stack, slot, limit) -> OptionalInt.of(25));

        ITurtleAccess turtle = mock(ITurtleAccess.class);
        OptionalInt fuel = TurtleRefuelHandlers.refuel(turtle, new ItemStack((Item) null, 1, 0), 0, 1);

        assertTrue(fuel.isPresent(), "a handler registered through the API must be consulted");
        assertEquals(25, fuel.getAsInt());
    }

    @Test
    void refusesToForwardNull() {
        assertTrue(ComputerCraftAPI.isInstalled());

        ComputerCraftAPI.registerRefuelHandler(null);

        ITurtleAccess turtle = mock(ITurtleAccess.class);
        assertTrue(
            TurtleRefuelHandlers.refuel(turtle, new ItemStack((Item) null, 1, 0), 0, 1)
                .isEmpty());
    }
}
