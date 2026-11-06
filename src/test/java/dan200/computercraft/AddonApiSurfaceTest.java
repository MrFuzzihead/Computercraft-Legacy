package dan200.computercraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.LuaState;

import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.lua.ILuaMachine;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;
import dan200.computercraft.shared.computer.blocks.TileComputerBase;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.peripheral.modem.TileCable;
import dan200.computercraft.shared.pocket.items.ItemPocketComputer;
import dan200.computercraft.shared.proxy.CCTurtleProxyCommon;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;

/**
 * Pins the public surface addons are expected to depend on instead of using mixins, accessors and invokers.
 *
 * <p>
 * Every member listed here is either a visibility widening or an accessor over state ComputerCraft already keeps.
 * None of them adds a call site inside ComputerCraft, so the surface is cheap to guarantee — but it is only worth
 * anything if it stays public. Reflection is used deliberately: a lambda or a call site in another test would break
 * on a rename, whereas these assertions fail the moment someone narrows a member back.
 * </p>
 *
 * <p>
 * The addons this serves are {@code CCTweaks-Legacy}, {@code PeripheralsPlusPlus} and the {@code OpenPeripheral}
 * forks; see {@code docs/CC_API_EXPOSURE_PLAN.md}.
 * </p>
 */
class AddonApiSurfaceTest {

    /** Asserts the method is declared public and non-abstract, and returns {@code returnType}. */
    private static Method assertPublicReturning(Class<?> owner, String name, Class<?> returnType, Class<?>... params) {
        Method method;
        try {
            method = owner.getDeclaredMethod(name, params);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(owner.getSimpleName() + " must declare " + name, e);
        }

        assertTrue(
            Modifier.isPublic(method.getModifiers()),
            owner.getSimpleName() + "#" + name + " must be public, was " + Modifier.toString(method.getModifiers()));
        assertTrue(
            !Modifier.isAbstract(method.getModifiers()),
            owner.getSimpleName() + "#" + name + " must be concrete");
        assertEquals(returnType, method.getReturnType(), owner.getSimpleName() + "#" + name + " return type");
        return method;
    }

    // =========================================================================
    // TileCable — modem state and the icon table
    // =========================================================================

    @Test
    void tileCableExposesAttachedPeripheralState() {
        Method id = assertPublicReturning(TileCable.class, "getAttachedPeripheralID", int.class);
        assertTrue(!Modifier.isStatic(id.getModifiers()), "the id belongs to one modem, not the class");

        Method allowed = assertPublicReturning(TileCable.class, "isPeripheralAccessAllowed", boolean.class);
        assertTrue(!Modifier.isStatic(allowed.getModifiers()), "access is per modem, not per class");
    }

    @Test
    void tileCableExposesBothHalvesOfTheIconTable() {
        Method side = assertPublicReturning(TileCable.class, "getModemSideTexture", IIcon.class, int.class);
        assertTrue(Modifier.isStatic(side.getModifiers()), "icons are registered once per class");

        assertPublicReturning(TileCable.class, "getModemFaceTexture", IIcon.class, int.class);
        assertPublicReturning(TileCable.class, "getCableItemTexture", IIcon.class, int.class);
    }

    // =========================================================================
    // Turtles and computers
    // =========================================================================

    @Test
    void tileTurtleExposesTheMovedFlagBothWays() {
        assertPublicReturning(TileTurtle.class, "hasMoved", boolean.class);
        assertPublicReturning(TileTurtle.class, "setMoved", void.class, boolean.class);
    }

    @Test
    void computerTilesExposeStateTransfer() {
        assertPublicReturning(TileComputerBase.class, "transferStateFrom", void.class, TileComputerBase.class);
    }

    @Test
    void computerTilesExposeTheirServerComputer() {
        assertPublicReturning(TileComputerBase.class, "getServerComputer", ServerComputer.class);
        assertPublicReturning(TileComputerBase.class, "createServerComputer", ServerComputer.class);
    }

    @Test
    void pocketComputersExposeServerComputerCreation() {
        assertPublicReturning(
            ItemPocketComputer.class,
            "createServerComputer",
            ServerComputer.class,
            World.class,
            IInventory.class,
            ItemStack.class);
    }

    // =========================================================================
    // Lua machine chain — the debugger's path
    // =========================================================================

    @Test
    void theLuaMachineChainIsReachable() {
        assertPublicReturning(ServerComputer.class, "getComputer", Computer.class);
        assertPublicReturning(Computer.class, "getLuaMachine", ILuaMachine.class);
        assertPublicReturning(CobaltMachine.class, "getLuaState", LuaState.class);
    }

    // =========================================================================
    // Turtle upgrade registry
    // =========================================================================

    @Test
    void theTurtleUpgradeTableIsReadable() {
        assertPublicReturning(CCTurtleProxyCommon.class, "getTurtleUpgrades", Map.class);
        assertPublicReturning(CCTurtleProxyCommon.class, "getTurtleUpgrade", ITurtleUpgrade.class, int.class);
        assertPublicReturning(CCTurtleProxyCommon.class, "getTurtleUpgrade", ITurtleUpgrade.class, ItemStack.class);
    }
}
