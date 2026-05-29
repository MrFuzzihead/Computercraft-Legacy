package dan200.computercraft.shared.peripheral.npcdetector;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.LuaException;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntityLivingBase;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.handler.data.IFaction;
import noppes.npcs.api.jobs.IJob;
import noppes.npcs.api.roles.IRole;

/**
 * Unit tests for {@link NpcDetectorPeripheral}.
 *
 * <p>
 * No running Minecraft world is required. CNPC-dependent methods (0–8) are verified to
 * throw the expected {@link LuaException} when CustomNPCs is absent (the game is not
 * running in these tests, so {@code AbstractNpcAPI.IsAvailable()} returns false or
 * throws, both of which {@link NpcDetectorPeripheral#requireCnpc()} translates to the
 * standard error message).
 * </p>
 */
class NpcDetectorPeripheralTest {

    private TileNpcDetector tile;
    private NpcDetectorPeripheral peripheral;

    @BeforeEach
    void setUp() {
        tile = new TileNpcDetector();
        peripheral = NpcDetectorPeripheral.forTile(tile);
        ComputerCraft.npc_detector_max_range = 64;
    }

    // =========================================================================
    // getType / getMethodNames
    // =========================================================================

    @Test
    void getType_returnsNpcDetector() {
        assertEquals("npc_detector", peripheral.getType());
    }

    @Test
    void getMethodNames_tenMethods() {
        assertEquals(
            Arrays.asList(
                "getNpcs",
                "getNpcsByName",
                "getNpcsInFaction",
                "getNearestNpc",
                "countNpcs",
                "getPlayers",
                "getNearestPlayer",
                "countPlayers",
                "getEntities",
                "getMaxRange"),
            Arrays.asList(peripheral.getMethodNames()));
    }

    // =========================================================================
    // getMaxRange (method 9) — works without CustomNPCs
    // =========================================================================

    @Test
    void getMaxRange_returnsConfiguredValue() throws LuaException, InterruptedException {
        ComputerCraft.npc_detector_max_range = 32;
        Object[] result = peripheral.callMethod(null, null, 9, new Object[0]);
        assertEquals(32.0, result[0]);
    }

    @Test
    void getMaxRange_returnsDefaultValue() throws LuaException, InterruptedException {
        Object[] result = peripheral.callMethod(null, null, 9, new Object[0]);
        assertEquals(64.0, result[0]);
    }

    // =========================================================================
    // Methods 0–8 throw when CustomNPCs is not installed
    // =========================================================================

    @Test
    void getNpcs_throwsWhenCnpcAbsent() {
        LuaException ex = assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, 0, new Object[0]));
        assertEquals("CustomNPCs is not installed", ex.getMessage());
    }

    @Test
    void getNpcsByName_throwsWhenCnpcAbsent() {
        LuaException ex = assertThrows(
            LuaException.class,
            () -> peripheral.callMethod(null, null, 1, new Object[] { "Guard" }));
        assertEquals("CustomNPCs is not installed", ex.getMessage());
    }

    @Test
    void getNpcsInFaction_throwsWhenCnpcAbsent() {
        LuaException ex = assertThrows(
            LuaException.class,
            () -> peripheral.callMethod(null, null, 2, new Object[] { "Bandits" }));
        assertEquals("CustomNPCs is not installed", ex.getMessage());
    }

    @Test
    void getNearestNpc_throwsWhenCnpcAbsent() {
        LuaException ex = assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, 3, new Object[0]));
        assertEquals("CustomNPCs is not installed", ex.getMessage());
    }

    @Test
    void countNpcs_throwsWhenCnpcAbsent() {
        LuaException ex = assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, 4, new Object[0]));
        assertEquals("CustomNPCs is not installed", ex.getMessage());
    }

    @Test
    void getPlayers_throwsWhenCnpcAbsent() {
        LuaException ex = assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, 5, new Object[0]));
        assertEquals("CustomNPCs is not installed", ex.getMessage());
    }

    @Test
    void getNearestPlayer_throwsWhenCnpcAbsent() {
        LuaException ex = assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, 6, new Object[0]));
        assertEquals("CustomNPCs is not installed", ex.getMessage());
    }

    @Test
    void countPlayers_throwsWhenCnpcAbsent() {
        LuaException ex = assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, 7, new Object[0]));
        assertEquals("CustomNPCs is not installed", ex.getMessage());
    }

    @Test
    void getEntities_throwsWhenCnpcAbsent() {
        LuaException ex = assertThrows(LuaException.class, () -> peripheral.callMethod(null, null, 8, new Object[0]));
        assertEquals("CustomNPCs is not installed", ex.getMessage());
    }

    // =========================================================================
    // parseRadius
    // =========================================================================

    @Test
    void parseRadius_aboveCap_clampedToMax() throws LuaException {
        double r = NpcDetectorPeripheral.parseRadius(new Object[] { 999.0 }, 0);
        assertEquals(64.0, r);
    }

    @Test
    void parseRadius_belowCap_usedAsIs() throws LuaException {
        double r = NpcDetectorPeripheral.parseRadius(new Object[] { 16.0 }, 0);
        assertEquals(16.0, r);
    }

    @Test
    void parseRadius_absent_defaultsToMax() throws LuaException {
        double r = NpcDetectorPeripheral.parseRadius(new Object[0], 0);
        assertEquals(64.0, r);
    }

    @Test
    void parseRadius_null_defaultsToMax() throws LuaException {
        double r = NpcDetectorPeripheral.parseRadius(new Object[] { null }, 0);
        assertEquals(64.0, r);
    }

    @Test
    void parseRadius_nonNumber_throwsLuaException() {
        assertThrows(LuaException.class, () -> NpcDetectorPeripheral.parseRadius(new Object[] { "big" }, 0));
    }

    // =========================================================================
    // requireString
    // =========================================================================

    @Test
    void requireString_present_returnsValue() throws LuaException {
        assertEquals("Guard", NpcDetectorPeripheral.requireString(new Object[] { "Guard" }, 0, "name"));
    }

    @Test
    void requireString_absent_throwsLuaException() {
        assertThrows(LuaException.class, () -> NpcDetectorPeripheral.requireString(new Object[0], 0, "name"));
    }

    @Test
    void requireString_wrongType_throwsLuaException() {
        assertThrows(LuaException.class, () -> NpcDetectorPeripheral.requireString(new Object[] { 42.0 }, 0, "name"));
    }

    // =========================================================================
    // buildNpcTable — all 17 keys, correct Java types
    // =========================================================================

    @Test
    @SuppressWarnings("unchecked")
    void buildNpcTable_allKeysPresent() {
        ICustomNpc<?> npc = mock(ICustomNpc.class);
        IEntityLivingBase<?> target = mock(IEntityLivingBase.class);
        IFaction faction = mock(IFaction.class);
        IJob job = mock(IJob.class);
        IRole role = mock(IRole.class);

        when(npc.getName()).thenReturn("Guard");
        when(npc.getTitle()).thenReturn("Captain");
        when(npc.getUniqueID()).thenReturn("abc-123");
        when(npc.getX()).thenReturn(100.0);
        when(npc.getY()).thenReturn(64.0);
        when(npc.getZ()).thenReturn(200.0);
        when(npc.getHealth()).thenReturn(Float.valueOf(20.0f));
        when(npc.getMaxHealth()).thenReturn(20.0);
        when(npc.isAlive()).thenReturn(true);
        when(npc.isAttacking()).thenReturn(true);
        when(npc.getAttackTarget()).thenReturn(target);
        when(target.getTypeName()).thenReturn("Steve");
        when(npc.getMovingType()).thenReturn(1);
        when(npc.getFaction()).thenReturn(faction);
        when(faction.getName()).thenReturn("Guards");
        when(faction.getId()).thenReturn(5);
        when(npc.getJob()).thenReturn(job);
        when(job.getType()).thenReturn(2);
        when(npc.getRole()).thenReturn(role);
        when(role.getType()).thenReturn(3);

        // Place tile at (10, 64, 10) — distance = sqrt((100.5-10.5)^2 + (64.5-64.5)^2 + (200.5-10.5)^2)
        tile.xCoord = 10;
        tile.yCoord = 64;
        tile.zCoord = 10;

        Map<String, Object> table = peripheral.buildNpcTable(npc);

        assertEquals(17, table.size());
        assertEquals("Guard", table.get("name"));
        assertEquals("Captain", table.get("title"));
        assertEquals("abc-123", table.get("uuid"));
        assertEquals(100.0, table.get("x"));
        assertEquals(64.0, table.get("y"));
        assertEquals(200.0, table.get("z"));
        assertInstanceOf(Double.class, table.get("distance"));
        assertEquals(20.0, table.get("health"));
        assertEquals(20.0, table.get("maxHealth"));
        assertEquals(Boolean.TRUE, table.get("isAlive"));
        assertEquals(Boolean.TRUE, table.get("isAttacking"));
        assertEquals("Steve", table.get("target"));
        assertEquals("wandering", table.get("movingType"));
        assertEquals("Guards", table.get("factionName"));
        assertEquals(5.0, table.get("factionId"));
        assertEquals("healer", table.get("jobType"));
        assertEquals("bank", table.get("roleType"));
    }

    @Test
    void buildNpcTable_nullFactionJobRole_usesDefaults() {
        ICustomNpc<?> npc = mock(ICustomNpc.class);
        when(npc.getName()).thenReturn("Wanderer");
        when(npc.getTitle()).thenReturn("");
        when(npc.getUniqueID()).thenReturn("xyz");
        when(npc.getX()).thenReturn(0.0);
        when(npc.getY()).thenReturn(0.0);
        when(npc.getZ()).thenReturn(0.0);
        when(npc.getHealth()).thenReturn(Float.valueOf(10.0f));
        when(npc.getMaxHealth()).thenReturn(20.0);
        when(npc.isAlive()).thenReturn(true);
        when(npc.isAttacking()).thenReturn(false);
        when(npc.getAttackTarget()).thenReturn(null);
        when(npc.getMovingType()).thenReturn(0);
        when(npc.getFaction()).thenReturn(null);
        when(npc.getJob()).thenReturn(null);
        when(npc.getRole()).thenReturn(null);

        Map<String, Object> table = peripheral.buildNpcTable(npc);

        assertNull(table.get("target"));
        assertEquals("", table.get("factionName"));
        assertEquals(-1.0, table.get("factionId"));
        assertEquals("none", table.get("jobType"));
        assertEquals("none", table.get("roleType"));
    }

    // =========================================================================
    // buildPlayerTable — all 9 keys, correct Java types
    // =========================================================================

    @Test
    void buildPlayerTable_allKeysPresent() {
        IPlayer<?> player = mock(IPlayer.class);
        when(player.getName()).thenReturn("Steve");
        when(player.getUniqueID()).thenReturn("player-uuid");
        when(player.getX()).thenReturn(50.0);
        when(player.getY()).thenReturn(64.0);
        when(player.getZ()).thenReturn(50.0);
        when(player.getHealth()).thenReturn(Float.valueOf(18.0f));
        when(player.getMaxHealth()).thenReturn(20.0);
        when(player.getMode()).thenReturn(0);

        tile.xCoord = 50;
        tile.yCoord = 64;
        tile.zCoord = 50;

        Map<String, Object> table = peripheral.buildPlayerTable(player);

        assertEquals(9, table.size());
        assertEquals("Steve", table.get("name"));
        assertEquals("player-uuid", table.get("uuid"));
        assertEquals(50.0, table.get("x"));
        assertEquals(64.0, table.get("y"));
        assertEquals(50.0, table.get("z"));
        assertInstanceOf(Double.class, table.get("distance"));
        assertEquals(18.0, table.get("health"));
        assertEquals(20.0, table.get("maxHealth"));
        assertEquals(0.0, table.get("gameMode"));
    }

    // =========================================================================
    // equals
    // =========================================================================

    @Test
    void equals_sametile_returnsTrue() {
        NpcDetectorPeripheral other = NpcDetectorPeripheral.forTile(tile);
        assertTrue(peripheral.equals(other));
    }

    @Test
    void equals_differentTile_returnsFalse() {
        TileNpcDetector other = new TileNpcDetector();
        assertFalse(peripheral.equals(NpcDetectorPeripheral.forTile(other)));
    }
}
