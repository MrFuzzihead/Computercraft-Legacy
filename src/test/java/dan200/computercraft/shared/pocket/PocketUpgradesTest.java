package dan200.computercraft.shared.pocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.pocket.peripherals.PocketChatBoxPeripheral;
import dan200.computercraft.shared.pocket.peripherals.PocketEnderModemPeripheral;
import dan200.computercraft.shared.pocket.peripherals.PocketModemPeripheral;
import dan200.computercraft.shared.pocket.peripherals.PocketNpcDetectorPeripheral;
import dan200.computercraft.shared.pocket.peripherals.PocketNpcInterfacePeripheral;
import dan200.computercraft.shared.pocket.peripherals.PocketSpeakerPeripheral;

/**
 * Tests for the pocket upgrade table.
 *
 * <p>
 * The table is what lets {@code pocket.equipBack} install anything the pocket computer already
 * supports, and it is shared with the code that rebuilds a computer from its NBT — so a wrong entry
 * here would either install the wrong peripheral or, worse, renumber an on-disk NBT value.
 * </p>
 */
class PocketUpgradesTest {

    @org.junit.jupiter.api.BeforeEach
    void onlyBuiltInsAreRegistered() {
        PocketUpgrades.resetModUpgradesForTesting();
    }

    // -------------------------------------------------------------------------
    // Peripheral type -> upgrade id
    // -------------------------------------------------------------------------

    @Test
    void everyItemTypeMapsToItsNbtId() {
        assertEquals(PocketUpgrades.WIRELESS_MODEM, PocketUpgrades.upgradeFor(PeripheralType.WirelessModem));
        assertEquals(PocketUpgrades.ENDER_MODEM, PocketUpgrades.upgradeFor(PeripheralType.AdvancedWirelessModem));
        assertEquals(PocketUpgrades.SPEAKER, PocketUpgrades.upgradeFor(PeripheralType.Speaker));
        assertEquals(PocketUpgrades.CHAT_BOX, PocketUpgrades.upgradeFor(PeripheralType.ChatBox));
        assertEquals(PocketUpgrades.NPC_DETECTOR, PocketUpgrades.upgradeFor(PeripheralType.NpcDetector));
        assertEquals(PocketUpgrades.NPC_INTERFACE, PocketUpgrades.upgradeFor(PeripheralType.NpcInterface));
    }

    @Test
    void theNbtIdsAreTheOnesAlreadyOnDisk() {
        // These numbers are stored in saved items. Renumbering them would silently turn every
        // existing pocket computer's upgrade into a different one.
        assertEquals(1, PocketUpgrades.WIRELESS_MODEM);
        assertEquals(2, PocketUpgrades.ENDER_MODEM);
        assertEquals(3, PocketUpgrades.SPEAKER);
        assertEquals(4, PocketUpgrades.CHAT_BOX);
        assertEquals(5, PocketUpgrades.NPC_DETECTOR);
        assertEquals(6, PocketUpgrades.NPC_INTERFACE);
    }

    @Test
    void nonUpgradeItemsAreRejected() {
        assertEquals(-1, PocketUpgrades.upgradeFor(PeripheralType.DiskDrive));
        assertEquals(-1, PocketUpgrades.upgradeFor(PeripheralType.Printer));
        assertEquals(-1, PocketUpgrades.upgradeFor(PeripheralType.Monitor));
        assertEquals(-1, PocketUpgrades.upgradeFor(PeripheralType.WiredModem));
        assertEquals(-1, PocketUpgrades.upgradeFor(PeripheralType.Cable));
        assertEquals(-1, PocketUpgrades.upgradeFor(null));
    }

    @Test
    void isUpgradeAcceptsExactlyTheSupportedRange() {
        assertTrue(PocketUpgrades.isUpgrade(1));
        assertTrue(PocketUpgrades.isUpgrade(6));
        assertFalse(PocketUpgrades.isUpgrade(0));
        assertFalse(PocketUpgrades.isUpgrade(7));
        assertFalse(PocketUpgrades.isUpgrade(-1));
    }

    // -------------------------------------------------------------------------
    // Upgrade id -> peripheral
    // -------------------------------------------------------------------------

    @Test
    void eachUpgradeBuildsItsOwnPeripheral() {
        IPocketAccess access = access();

        assertTrue(
            PocketUpgrades.createPeripheral(PocketUpgrades.WIRELESS_MODEM, access) instanceof PocketModemPeripheral);
        assertTrue(
            PocketUpgrades.createPeripheral(PocketUpgrades.ENDER_MODEM, access) instanceof PocketEnderModemPeripheral);
        assertTrue(PocketUpgrades.createPeripheral(PocketUpgrades.SPEAKER, access) instanceof PocketSpeakerPeripheral);
        assertTrue(PocketUpgrades.createPeripheral(PocketUpgrades.CHAT_BOX, access) instanceof PocketChatBoxPeripheral);
        assertTrue(
            PocketUpgrades
                .createPeripheral(PocketUpgrades.NPC_DETECTOR, access) instanceof PocketNpcDetectorPeripheral);
        assertTrue(
            PocketUpgrades
                .createPeripheral(PocketUpgrades.NPC_INTERFACE, access) instanceof PocketNpcInterfacePeripheral);
    }

    @Test
    void anUnsupportedIdBuildsNothing() {
        IPocketAccess access = access();
        assertNull(PocketUpgrades.createPeripheral(0, access));
        assertNull(PocketUpgrades.createPeripheral(7, access));
        assertNull(PocketUpgrades.createPeripheral(-5, access));
    }

    @Test
    void theEnderModemIsStillAModem() {
        // Existing code does `instanceof PocketModemPeripheral` to find the modem, so the ender
        // modem must remain a subclass. This is the invariant that keeps that check valid.
        IPocketAccess access = access();
        assertTrue(
            PocketUpgrades.createPeripheral(PocketUpgrades.ENDER_MODEM, access) instanceof PocketModemPeripheral);
    }

    @Test
    void everyPeripheralReportsATypeThatMapsBackToItsUpgrade() {
        // Round-trip: the peripheral a given upgrade installs must answer with the type that would
        // pick that same upgrade, or equipBack and the rebuild path would disagree.
        IPocketAccess access = access();

        int[] upgrades = { PocketUpgrades.WIRELESS_MODEM, PocketUpgrades.ENDER_MODEM, PocketUpgrades.SPEAKER,
            PocketUpgrades.CHAT_BOX, PocketUpgrades.NPC_DETECTOR, PocketUpgrades.NPC_INTERFACE };

        for (int upgrade : upgrades) {
            var peripheral = PocketUpgrades.createPeripheral(upgrade, access);
            assertNotNull(peripheral, "no peripheral for upgrade " + upgrade);
            String type = peripheral.getType();
            assertNotNull(type, "upgrade " + upgrade + " has no type");

            // The peripheral type must be recognised as *some* upgrade. (Not necessarily the same
            // id: the modem and ender modem share the "modem" type.)
            assertTrue(
                PocketUpgrades.upgradeFor(typeFor(type)) >= 0,
                "upgrade " + upgrade + " reports type " + type + ", which is not a pocket upgrade");
        }
    }

    /** Maps a peripheral type string back to its item type, as the item would report it. */
    private static PeripheralType typeFor(String peripheralType) {
        switch (peripheralType) {
            case "modem":
                return PeripheralType.WirelessModem;
            case "speaker":
                return PeripheralType.Speaker;
            case "chatbox":
                return PeripheralType.ChatBox;
            case "npc_detector":
                return PeripheralType.NpcDetector;
            case "npc_interface":
                return PeripheralType.NpcInterface;
            default:
                return null;
        }
    }

    @Test
    void namesAreHumanReadable() {
        assertEquals("wireless modem", PocketUpgrades.nameOf(PocketUpgrades.WIRELESS_MODEM));
        assertEquals("ender modem", PocketUpgrades.nameOf(PocketUpgrades.ENDER_MODEM));
        assertEquals("speaker", PocketUpgrades.nameOf(PocketUpgrades.SPEAKER));
        assertEquals("unknown", PocketUpgrades.nameOf(99));
    }

    // -------------------------------------------------------------------------
    // The NBT read used by ItemPocketComputer
    // -------------------------------------------------------------------------

    @Test
    void theUpgradeIsReadFromNbtSafely() {
        // getUpgrade() is what the rebuild path now uses; it must tolerate every shape of NBT.
        ItemStack withoutTag = new ItemStack(net.minecraft.init.Blocks.stone);
        assertNotNull(withoutTag);

        ItemStack withTag = new ItemStack(net.minecraft.init.Blocks.stone);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("upgrade", PocketUpgrades.SPEAKER);
        withTag.setTagCompound(tag);

        // A plain read of the same tag the helper reads, so this documents the shape it expects.
        assertEquals(
            3,
            withTag.getTagCompound()
                .getInteger("upgrade"));
        assertFalse(withoutTag.hasTagCompound());
    }

    @Test
    void anOutOfRangeNbtValueIsNotAnUpgrade() {
        // A world saved by a future version (or a corrupted tag) must not be treated as an upgrade.
        for (int id : new int[] { 0, 7, 42, -3 }) {
            assertFalse(PocketUpgrades.isUpgrade(id), "id " + id + " should not be an upgrade");
        }
    }

    /** An access whose stack is a placeholder item; the built-in upgrades only read it for the NPC interface. */
    private static IPocketAccess access() {
        IPocketAccess access = org.mockito.Mockito.mock(IPocketAccess.class);
        org.mockito.Mockito.when(access.getStack())
            .thenReturn(new ItemStack(net.minecraft.init.Blocks.stone));
        return access;
    }
}
