package dan200.computercraft.shared.pocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.api.pocket.IPocketUpgrade;
import dan200.computercraft.shared.pocket.upgrades.PocketChatBoxUpgrade;
import dan200.computercraft.shared.pocket.upgrades.PocketEnderModemUpgrade;
import dan200.computercraft.shared.pocket.upgrades.PocketModemUpgrade;
import dan200.computercraft.shared.pocket.upgrades.PocketNpcDetectorUpgrade;
import dan200.computercraft.shared.pocket.upgrades.PocketNpcInterfaceUpgrade;
import dan200.computercraft.shared.pocket.upgrades.PocketSpeakerUpgrade;

/**
 * Tests for the pocket upgrade registry.
 *
 * <p>
 * The registry is what replaced ComputerCraft's closed six-id table. Two things about it are load-bearing and are
 * pinned here: the six built-in ids still resolve to the same peripherals they always did (they are on-disk NBT
 * values), and a mod can only claim ids that are not reserved and not already taken — a silent overwrite would
 * reinterpret every saved pocket computer carrying either upgrade.
 * </p>
 */
class PocketUpgradeRegistryTest {

    /** A mod upgrade that attaches nothing; only the registry bookkeeping is under test here. */
    private static final class TestUpgrade implements IPocketUpgrade {

        private final int id;

        private TestUpgrade(int id) {
            this.id = id;
        }

        @Override
        public int getUpgradeID() {
            return this.id;
        }

        @Override
        public String getUnlocalisedAdjective() {
            return "test.adjective";
        }

        @Override
        public ItemStack getCraftingItem() {
            return null;
        }

        @Override
        public IPeripheral createPeripheral(IPocketAccess access) {
            return null;
        }
    }

    private static IPocketAccess access() {
        IPocketAccess access = mock(IPocketAccess.class);
        when(access.getStack()).thenReturn(new ItemStack(net.minecraft.init.Blocks.stone));
        return access;
    }

    @BeforeEach
    @AfterEach
    void clearModUpgrades() {
        // The registry is process-wide: without this a registration here would leak into every later test.
        PocketUpgrades.resetModUpgradesForTesting();
    }

    // =========================================================================
    // The six built-ins
    // =========================================================================

    @Test
    void theSixBuiltInIdsAreRegisteredByDefault() {
        assertEquals(
            PocketModemUpgrade.class,
            PocketUpgrades.getUpgrade(PocketUpgrades.WIRELESS_MODEM)
                .getClass());
        assertEquals(
            PocketEnderModemUpgrade.class,
            PocketUpgrades.getUpgrade(PocketUpgrades.ENDER_MODEM)
                .getClass());
        assertEquals(
            PocketSpeakerUpgrade.class,
            PocketUpgrades.getUpgrade(PocketUpgrades.SPEAKER)
                .getClass());
        assertEquals(
            PocketChatBoxUpgrade.class,
            PocketUpgrades.getUpgrade(PocketUpgrades.CHAT_BOX)
                .getClass());
        assertEquals(
            PocketNpcDetectorUpgrade.class,
            PocketUpgrades.getUpgrade(PocketUpgrades.NPC_DETECTOR)
                .getClass());
        assertEquals(
            PocketNpcInterfaceUpgrade.class,
            PocketUpgrades.getUpgrade(PocketUpgrades.NPC_INTERFACE)
                .getClass());
    }

    @Test
    void theBuiltInIdsStillMatchTheirPeripherals() {
        // The peripherals are unchanged; only who decides to build them moved.
        assertEquals(
            "modem",
            PocketUpgrades.createPeripheral(PocketUpgrades.WIRELESS_MODEM, access())
                .getType());
        assertEquals(
            "modem",
            PocketUpgrades.createPeripheral(PocketUpgrades.ENDER_MODEM, access())
                .getType());
        assertEquals(
            "speaker",
            PocketUpgrades.createPeripheral(PocketUpgrades.SPEAKER, access())
                .getType());
        assertEquals(
            "chatbox",
            PocketUpgrades.createPeripheral(PocketUpgrades.CHAT_BOX, access())
                .getType());
        assertEquals(
            "npc_detector",
            PocketUpgrades.createPeripheral(PocketUpgrades.NPC_DETECTOR, access())
                .getType());
        assertEquals(
            "npc_interface",
            PocketUpgrades.createPeripheral(PocketUpgrades.NPC_INTERFACE, access())
                .getType());
    }

    @Test
    void anUnregisteredIdHasNoUpgradeAndNoPeripheral() {
        assertNull(PocketUpgrades.getUpgrade(42));
        assertFalse(PocketUpgrades.isUpgrade(42));
        assertNull(PocketUpgrades.createPeripheral(42, access()));
    }

    // =========================================================================
    // Registration rules
    // =========================================================================

    @Test
    void aModUpgradeAboveTheReservedRangeIsAccepted() {
        TestUpgrade upgrade = new TestUpgrade(IPocketUpgrade.FIRST_MOD_UPGRADE_ID);

        PocketUpgrades.register(upgrade);

        assertTrue(PocketUpgrades.isUpgrade(IPocketUpgrade.FIRST_MOD_UPGRADE_ID));
        assertSame(upgrade, PocketUpgrades.getUpgrade(IPocketUpgrade.FIRST_MOD_UPGRADE_ID));
        assertTrue(
            PocketUpgrades.getRegisteredUpgrades()
                .contains(upgrade),
            "registered upgrades are what equipBack scans");
    }

    @Test
    void aReservedIdIsRefused() {
        // Ids under the boundary are ComputerCraft's and are stored in saved items: reusing 3 would turn every
        // speaker pocket computer into whatever the mod registered.
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> PocketUpgrades.register(new TestUpgrade(PocketUpgrades.SPEAKER)));

        assertTrue(
            error.getMessage()
                .contains("reserved"),
            error.getMessage());
        assertEquals(
            PocketSpeakerUpgrade.class,
            PocketUpgrades.getUpgrade(PocketUpgrades.SPEAKER)
                .getClass());
    }

    @Test
    void aDuplicateIdIsRefusedAndKeepsTheFirstRegistration() {
        TestUpgrade first = new TestUpgrade(100);
        PocketUpgrades.register(first);

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> PocketUpgrades.register(new TestUpgrade(100)));

        assertTrue(
            error.getMessage()
                .contains("already registered"),
            error.getMessage());
        assertSame(first, PocketUpgrades.getUpgrade(100));
    }

    @Test
    void aNullUpgradeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PocketUpgrades.register(null));
    }

    @Test
    void resettingTheRegistryLeavesTheBuiltInsAlone() {
        PocketUpgrades.register(new TestUpgrade(200));

        PocketUpgrades.resetModUpgradesForTesting();

        assertFalse(PocketUpgrades.isUpgrade(200));
        assertNotNull(PocketUpgrades.getUpgrade(PocketUpgrades.WIRELESS_MODEM));
        assertNotNull(PocketUpgrades.getUpgrade(PocketUpgrades.NPC_INTERFACE));
    }
}
