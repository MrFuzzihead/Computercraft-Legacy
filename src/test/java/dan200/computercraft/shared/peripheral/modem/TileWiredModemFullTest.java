package dan200.computercraft.shared.peripheral.modem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import org.junit.jupiter.api.Test;

import dan200.computercraft.shared.peripheral.PeripheralType;

class TileWiredModemFullTest {

    @Test
    void exposesSixDistinctModemsSharingOneState() {
        TileWiredModemFull tile = new TileWiredModemFull();

        ModemPeripheral[] modems = new ModemPeripheral[6];
        for (int side = 0; side < 6; side++) {
            modems[side] = (ModemPeripheral) tile.getPeripheral(side);
            assertEquals(true, tile.getPeripheral(side) != null, "side " + side + " must expose a modem");
        }

        assertNotSame(modems[0], modems[1]);
        ModemState state = modems[0].getModemState();
        for (ModemPeripheral modem : modems) {
            assertSame(state, modem.getModemState(), "all six faces must share one modem state");
        }
    }

    @Test
    void hasNoPeripheralOnOutOfRangeSides() {
        TileWiredModemFull tile = new TileWiredModemFull();
        assertNull(tile.getPeripheral(-1));
        assertNull(tile.getPeripheral(6));
    }

    @Test
    void reportsTheWiredModemType() {
        assertEquals(PeripheralType.WiredModemWithCable, new TileWiredModemFull().getPeripheralType());
    }

    @Test
    void isAFullCube() {
        AxisAlignedBB bounds = new TileWiredModemFull().getBounds();
        assertEquals(0.0, bounds.minX);
        assertEquals(0.0, bounds.minY);
        assertEquals(0.0, bounds.minZ);
        assertEquals(1.0, bounds.maxX);
        assertEquals(1.0, bounds.maxY);
        assertEquals(1.0, bounds.maxZ);
    }

    @Test
    void theModemBitTracksTheSharedState() throws Exception {
        TileWiredModemFull tile = new TileWiredModemFull();
        tile.updateAnim();
        assertEquals(0, tile.getAnim());

        tile.getModemState()
            .open(3);
        tile.updateAnim();
        assertEquals(1, tile.getAnim(), "an open channel on any face lights the modem");

        tile.getModemState()
            .closeAll();
        tile.updateAnim();
        assertEquals(0, tile.getAnim());
    }

    @Test
    void nbtRoundTripsTheEnabledFlagAndPerSideNames() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("PeripheralEnabled", true);
        tag.setInteger("PeripheralId0", 7);
        tag.setString("PeripheralType0", "inventory");
        tag.setInteger("PeripheralId5", 11);
        tag.setString("PeripheralType5", "monitor");

        TileWiredModemFull tile = new TileWiredModemFull();
        tile.readFromNBT(tag);

        assertTrue(tile.isPeripheralEnabled());
        assertEquals(
            7,
            tile.getLocalPeripheral(0)
                .getId());
        assertEquals(
            "inventory",
            tile.getLocalPeripheral(0)
                .getType());
        assertEquals(
            11,
            tile.getLocalPeripheral(5)
                .getId());
        assertEquals(
            "monitor",
            tile.getLocalPeripheral(5)
                .getType());
    }

    @Test
    void aLoadedEnabledHubRemembersItWasEnabled() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("PeripheralEnabled", true);

        TileWiredModemFull tile = new TileWiredModemFull();
        tile.readFromNBT(tag);

        assertTrue(tile.isPeripheralEnabled());
    }

    @Test
    void tickingWithNoPeripheralsIsSafe() {
        TileWiredModemFull tile = new TileWiredModemFull();
        World world = mock(World.class);
        world.isRemote = false;
        when(world.getHeight()).thenReturn(0);
        tile.setWorldObj(world);
        tile.xCoord = 0;
        tile.yCoord = 0;
        tile.zCoord = 0;

        tile.updateEntity();
        tile.updateEntity();
    }

    @Test
    void rightClickWithNoPeripheralsIsSafe() {
        TileWiredModemFull tile = new TileWiredModemFull();
        World world = mock(World.class);
        world.isRemote = false;
        when(world.getHeight()).thenReturn(0);
        tile.setWorldObj(world);

        EntityPlayer player = mock(EntityPlayer.class);
        when(player.isSneaking()).thenReturn(false);
        player.capabilities = new PlayerCapabilities();
        player.capabilities.allowEdit = true;

        assertTrue(tile.onActivate(player, 0, 0.0F, 0.0F, 0.0F));
    }
}
