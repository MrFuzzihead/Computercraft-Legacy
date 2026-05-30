package dan200.computercraft.compat.customnpcs.peripheral.traderrole;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.api.lua.LuaException;

/**
 * Unit tests for {@link TileTraderRole}.
 *
 * <p>
 * Validates link state, NBT round-trip, and the error conditions of
 * {@link TileTraderRole#resolveTrader()} without a running Minecraft world.
 * </p>
 */
class TileTraderRoleTest {

    private TileTraderRole tile;

    @BeforeEach
    void setUp() {
        tile = new TileTraderRole();
    }

    // =========================================================================
    // Link state
    // =========================================================================

    @Test
    void initialState_isUnlinked() {
        assertNull(tile.getLinkedUUID());
        assertNull(tile.getLinkedName());
    }

    @Test
    void setLink_storesUUIDAndName() {
        tile.setLink("abc-123", "Merchant");
        assertEquals("abc-123", tile.getLinkedUUID());
        assertEquals("Merchant", tile.getLinkedName());
    }

    @Test
    void setLink_null_clearsLink() {
        tile.setLink("abc-123", "Merchant");
        tile.setLink(null, null);
        assertNull(tile.getLinkedUUID());
        assertNull(tile.getLinkedName());
    }

    // =========================================================================
    // resolveTrader() — error paths (no MC world needed)
    // =========================================================================

    @Test
    void resolveTrader_whenNotLinked_throwsNotLinked() {
        LuaException ex = assertThrows(LuaException.class, () -> tile.resolveTrader());
        assertEquals("Not linked to any NPC", ex.getMessage());
    }

    @Test
    void resolveTrader_whenLinkedButCnpcAbsent_throwsCnpcError() {
        tile.setLink("abc-123", "Merchant");
        // AbstractNpcAPI.IsAvailable() returns false in a pure unit-test environment
        LuaException ex = assertThrows(LuaException.class, () -> tile.resolveTrader());
        // Either "CustomNPCs is not installed" or "NPC not found" depending on classpath
        assertNotNull(ex.getMessage());
    }

    // =========================================================================
    // NBT round-trip
    //
    // We bypass TileEntity.writeToNBT/readFromNBT (which requires a Minecraft
    // world) by writing only into a fresh NBTTagCompound and reading back via
    // the same tile, testing the link-field paths directly.
    // =========================================================================

    @Test
    void nbtRoundTrip_linkPreserved() {
        tile.setLink("uuid-xyz", "Bob the Trader");
        tile.setDirection(3);

        NBTTagCompound nbt = new NBTTagCompound();
        // Write only the fields our override adds (avoid TileEntity super call)
        nbt.setInteger("dir", tile.getDirection());
        nbt.setString("npcUUID", tile.getLinkedUUID());
        nbt.setString("npcName", tile.getLinkedName());

        TileTraderRole loaded = new TileTraderRole();
        // Simulate readFromNBT field reads without calling MC TileEntity super
        loaded.setLink(
            nbt.hasKey("npcUUID") ? nbt.getString("npcUUID") : null,
            nbt.hasKey("npcName") ? nbt.getString("npcName") : null);
        loaded.setDirection(nbt.hasKey("dir") ? nbt.getInteger("dir") : 2);

        assertEquals("uuid-xyz", loaded.getLinkedUUID());
        assertEquals("Bob the Trader", loaded.getLinkedName());
        assertEquals(3, loaded.getDirection());
    }

    @Test
    void nbtRoundTrip_nullLinkPreserved() {
        NBTTagCompound nbt = new NBTTagCompound();
        // No link set — npcUUID/npcName keys absent
        nbt.setInteger("dir", tile.getDirection());

        TileTraderRole loaded = new TileTraderRole();
        loaded.setLink(
            nbt.hasKey("npcUUID") ? nbt.getString("npcUUID") : null,
            nbt.hasKey("npcName") ? nbt.getString("npcName") : null);
        loaded.setDirection(nbt.hasKey("dir") ? nbt.getInteger("dir") : 2);

        assertNull(loaded.getLinkedUUID());
        assertNull(loaded.getLinkedName());
    }

    // =========================================================================
    // Direction
    // =========================================================================

    @Test
    void setDirection_clampsBadValue() {
        tile.setDirection(99);
        assertEquals(2, tile.getDirection());
    }

    @Test
    void setDirection_acceptsValid() {
        tile.setDirection(5);
        assertEquals(5, tile.getDirection());
    }
}
