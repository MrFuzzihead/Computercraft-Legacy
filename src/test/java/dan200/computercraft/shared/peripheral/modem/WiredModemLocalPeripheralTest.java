package dan200.computercraft.shared.peripheral.modem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import dan200.computercraft.api.peripheral.IPeripheral;

class WiredModemLocalPeripheralTest {

    private static IPeripheral peripheral(String type) {
        IPeripheral peripheral = mock(IPeripheral.class);
        when(peripheral.getType()).thenReturn(type);
        return peripheral;
    }

    @Test
    void firstAttachAllocatesAnIdAndBuildsTheName() {
        WiredModemLocalPeripheral local = new WiredModemLocalPeripheral();
        IPeripheral inventory = peripheral("inventory");

        assertTrue(local.attach(inventory, type -> 7));
        assertEquals("inventory", local.getType());
        assertEquals(7, local.getId());
        assertEquals("inventory_7", local.getConnectedName());
        assertTrue(local.hasPeripheral());
    }

    @Test
    void reattachingTheSameTypeKeepsTheId() {
        WiredModemLocalPeripheral local = new WiredModemLocalPeripheral();
        assertTrue(local.attach(peripheral("inventory"), type -> 7));

        local.detach();
        assertFalse(local.hasPeripheral());
        assertNull(local.getConnectedName());

        assertTrue(local.attach(peripheral("inventory"), type -> 99));
        assertEquals("inventory_7", local.getConnectedName(), "the ID must survive a detach/re-attach");
    }

    @Test
    void changingTheTypeReallocatesTheId() {
        WiredModemLocalPeripheral local = new WiredModemLocalPeripheral();
        assertTrue(local.attach(peripheral("inventory"), type -> 7));
        assertEquals("inventory_7", local.getConnectedName());

        assertTrue(local.attach(peripheral("monitor"), type -> 42));
        assertEquals("monitor_42", local.getConnectedName());
    }

    @Test
    void wiredModemPeripheralsAreIgnored() {
        WiredModemLocalPeripheral local = new WiredModemLocalPeripheral();
        IPeripheral modem = new TileCable.Peripheral(null);

        assertFalse(local.attach(modem, type -> 1));
        assertFalse(local.hasPeripheral());
        assertNull(local.getConnectedName());
    }

    @Test
    void extendMapUsesTheConnectedName() {
        WiredModemLocalPeripheral local = new WiredModemLocalPeripheral();
        IPeripheral inventory = peripheral("inventory");
        local.attach(inventory, type -> 7);

        Map<String, IPeripheral> map = new HashMap<>();
        local.extendMap(map);

        assertEquals(1, map.size());
        assertTrue(map.containsKey("inventory_7"));
        assertEquals(inventory, map.get("inventory_7"));
    }

    @Test
    void nbtRoundTripsIdAndType() {
        WiredModemLocalPeripheral original = new WiredModemLocalPeripheral();
        original.attach(peripheral("inventory"), type -> 7);

        NBTTagCompound tag = new NBTTagCompound();
        original.write(tag, "3");

        WiredModemLocalPeripheral restored = new WiredModemLocalPeripheral();
        restored.read(tag, "3");

        assertEquals(7, restored.getId());
        assertEquals("inventory", restored.getType());
        assertFalse(restored.hasPeripheral(), "loading only restores the name, not the world peripheral");
    }

    @Test
    void anIdWithoutATypeOnlyGainsTheType() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("PeripheralId", 11);
        WiredModemLocalPeripheral local = new WiredModemLocalPeripheral();
        local.read(tag, "");

        assertTrue(local.attach(peripheral("chest"), type -> 99));
        assertEquals("chest_11", local.getConnectedName());
    }
}
