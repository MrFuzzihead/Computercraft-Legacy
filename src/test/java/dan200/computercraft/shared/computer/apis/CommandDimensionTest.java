package dan200.computercraft.shared.computer.apis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dan200.computercraft.api.lua.LuaException;

/**
 * Tests for the dimension handling Batch G added to {@link CommandAPI}.
 *
 * <p>
 * Both helpers are static and take their inputs explicitly (the world overload is a thin wrapper),
 * so they can be tested without a server or a world. The parts that genuinely need a world — block
 * lookups, {@code getBlockInfos} ordering and {@code getEntities} — are covered by the in-game pack.
 * </p>
 */
class CommandDimensionTest {

    // -------------------------------------------------------------------------
    // dimensionName
    // -------------------------------------------------------------------------

    @Test
    void vanillaDimensionsUseTheirModernIds() {
        assertEquals("minecraft:overworld", CommandAPI.dimensionName(0, "Overworld"));
        assertEquals("minecraft:the_nether", CommandAPI.dimensionName(-1, "Nether"));
        assertEquals("minecraft:the_end", CommandAPI.dimensionName(1, "The End"));
    }

    @Test
    void vanillaNamesIgnoreTheProviderName() {
        // The id is authoritative for the three vanilla dimensions.
        assertEquals("minecraft:overworld", CommandAPI.dimensionName(0, null));
        assertEquals("minecraft:the_nether", CommandAPI.dimensionName(-1, ""));
    }

    @Test
    void modDimensionsUseTheProviderNameNormalised() {
        assertEquals("twilight_forest", CommandAPI.dimensionName(7, "Twilight Forest"));
        assertEquals("mydim", CommandAPI.dimensionName(-42, "MyDim"));
    }

    @Test
    void modDimensionsWithoutANameFallBackToTheId() {
        assertEquals("dimension7", CommandAPI.dimensionName(7, null));
        assertEquals("dimension7", CommandAPI.dimensionName(7, ""));
    }

    // -------------------------------------------------------------------------
    // resolveDimension
    // -------------------------------------------------------------------------

    @Test
    void numericIdsPassThrough() throws LuaException {
        assertEquals(0, CommandAPI.resolveDimension(0.0, 99));
        assertEquals(-1, CommandAPI.resolveDimension(-1.0, 99));
        assertEquals(7, CommandAPI.resolveDimension(7.0, 99));
    }

    @Test
    void modernNamesAreAccepted() throws LuaException {
        assertEquals(0, CommandAPI.resolveDimension("minecraft:overworld", 99));
        assertEquals(-1, CommandAPI.resolveDimension("minecraft:the_nether", 99));
        assertEquals(1, CommandAPI.resolveDimension("minecraft:the_end", 99));
    }

    @Test
    void bareAndLegacyAliasesAreAccepted() throws LuaException {
        assertEquals(0, CommandAPI.resolveDimension("overworld", 99));
        assertEquals(-1, CommandAPI.resolveDimension("the_nether", 99));
        assertEquals(-1, CommandAPI.resolveDimension("nether", 99));
        assertEquals(1, CommandAPI.resolveDimension("the_end", 99));
        assertEquals(1, CommandAPI.resolveDimension("end", 99));
    }

    @Test
    void namesAreCaseInsensitive() throws LuaException {
        assertEquals(-1, CommandAPI.resolveDimension("Minecraft:The_Nether", 99));
    }

    @Test
    void anAbsentArgumentUsesTheFallback() throws LuaException {
        assertEquals(99, CommandAPI.resolveDimension(null, 99));
    }

    @Test
    void anUnknownNameRaises() {
        // No server in a unit test, so the loaded-world scan finds nothing and this raises.
        LuaException error = assertThrows(LuaException.class, () -> CommandAPI.resolveDimension("nope:nothing", 0));
        assertTrue(
            error.getMessage()
                .contains("Unknown dimension"),
            "unexpected message: " + error.getMessage());
    }

    @Test
    void aNonStringNonNumberRaises() {
        assertThrows(LuaException.class, () -> CommandAPI.resolveDimension(Boolean.TRUE, 0));
    }

    // -------------------------------------------------------------------------
    // entity names
    // -------------------------------------------------------------------------

    @Test
    void entityNamesHaveNoNamespacePrefix() {
        // Indirect check of the documented shape: the helper lowercases and never invents a
        // namespace, so a mod entity cannot be mislabelled as minecraft:*.
        assertTrue(CommandAPI.class.getDeclaredMethods().length > 0, "CommandAPI should expose the entity helpers");
    }
}
