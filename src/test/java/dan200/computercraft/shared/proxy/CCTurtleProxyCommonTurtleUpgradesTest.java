package dan200.computercraft.shared.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.Test;

import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.util.Colour;

/**
 * Tests for {@link CCTurtleProxyCommon#getTurtleUpgrades()}, the read-only view recipe browsers use to list every
 * registered turtle upgrade.
 *
 * <p>
 * Upgrades are registered with ids at or above 64: those skip the crafting-recipe half of registration, which needs a
 * live item registry. The view's contract is threefold — it reflects later registrations, it agrees with
 * {@code getTurtleUpgrade(int)}, and it cannot be mutated through.
 * </p>
 */
class CCTurtleProxyCommonTurtleUpgradesTest {

    /** Concrete proxy: the abstract member is client-only rendering. */
    private static final class TestProxy extends CCTurtleProxyCommon {

        @Override
        public void getTurtleModelTextures(List<ResourceLocation> list, ComputerFamily family, Colour colour) {}
    }

    private static ITurtleUpgrade upgrade(int id) {
        ITurtleUpgrade upgrade = mock(ITurtleUpgrade.class);
        when(upgrade.getUpgradeID()).thenReturn(id);
        when(upgrade.getUnlocalisedAdjective()).thenReturn("test" + id);
        return upgrade;
    }

    @Test
    void startsEmpty() {
        assertTrue(
            new TestProxy().getTurtleUpgrades()
                .isEmpty());
    }

    @Test
    void reflectsLaterRegistrations() {
        CCTurtleProxyCommon proxy = new TestProxy();
        Map<Integer, ITurtleUpgrade> view = proxy.getTurtleUpgrades();
        ITurtleUpgrade registered = upgrade(100);

        proxy.registerTurtleUpgrade(registered);

        assertTrue(view.containsKey(100), "the view must be live, not a snapshot");
        assertSame(registered, view.get(100));
    }

    @Test
    void agreesWithTheSingleLookup() {
        CCTurtleProxyCommon proxy = new TestProxy();
        ITurtleUpgrade registered = upgrade(101);
        proxy.registerTurtleUpgrade(registered);

        assertSame(registered, proxy.getTurtleUpgrade(101));
        assertSame(
            proxy.getTurtleUpgrade(101),
            proxy.getTurtleUpgrades()
                .get(101));
        assertEquals(
            1,
            proxy.getTurtleUpgrades()
                .size());
    }

    @Test
    void cannotBeMutatedThrough() {
        CCTurtleProxyCommon proxy = new TestProxy();
        proxy.registerTurtleUpgrade(upgrade(102));
        Map<Integer, ITurtleUpgrade> view = proxy.getTurtleUpgrades();

        assertThrows(UnsupportedOperationException.class, () -> view.put(103, upgrade(103)));
        assertThrows(UnsupportedOperationException.class, () -> view.remove(102));
        assertThrows(UnsupportedOperationException.class, view::clear);
        assertEquals(
            1,
            proxy.getTurtleUpgrades()
                .size(),
            "a rejected mutation must not change the registry");
    }
}
