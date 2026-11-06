package dan200.computercraft.shared.pocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.api.pocket.IPocketUpgrade;

/**
 * Tests that {@link ComputerCraftAPI#registerPocketUpgrade} actually reaches the mod.
 *
 * <p>
 * This API is reflective: the call looks up a method on {@code dan200.computercraft.ComputerCraft} by name and
 * parameter types, and silently does nothing when the lookup fails. A renamed target would compile, ship, and quietly
 * stop accepting addon upgrades — with the failure showing up only as "my upgrade never installs" in game. The first
 * test closes that hole; the second drives a registration through the API and looks for it in the registry.
 * </p>
 */
class PocketUpgradeApiTest {

    private static final int ID = 91;

    @AfterEach
    void clearModUpgrades() {
        PocketUpgrades.resetModUpgradesForTesting();
    }

    private static IPocketUpgrade upgrade() {
        return new IPocketUpgrade() {

            @Override
            public int getUpgradeID() {
                return ID;
            }

            @Override
            public String getUnlocalisedAdjective() {
                return "test.api_upgrade.adjective";
            }

            @Override
            public ItemStack getCraftingItem() {
                return null;
            }

            @Override
            public NBTTagCompound getUpgradeData(ItemStack stack) {
                return null;
            }

            @Override
            public IPeripheral createPeripheral(IPocketAccess access) {
                return mock(IPeripheral.class);
            }
        };
    }

    @Test
    void theLookupTargetExists() throws Exception {
        Method target = ComputerCraft.class.getMethod("registerPocketUpgrade", IPocketUpgrade.class);

        assertTrue(Modifier.isStatic(target.getModifiers()), "the API reflects into a static method");
        assertEquals(void.class, target.getReturnType());
        assertTrue(Modifier.isPublic(target.getModifiers()));
    }

    @Test
    void theApiForwardsIntoTheRegistry() {
        assertTrue(ComputerCraftAPI.isInstalled(), "the API must find ComputerCraft on this classpath");
        IPocketUpgrade registered = upgrade();

        ComputerCraftAPI.registerPocketUpgrade(registered);

        assertSame(registered, PocketUpgrades.getUpgrade(ID), "a registration through the API must reach the registry");
    }

    @Test
    void theApiDoesNothingForNull() {
        assertTrue(ComputerCraftAPI.isInstalled());

        ComputerCraftAPI.registerPocketUpgrade(null);

        assertNull(PocketUpgrades.getUpgrade(ID));
    }
}
