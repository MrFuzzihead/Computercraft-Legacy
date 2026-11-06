package dan200.computercraft.shared.peripheral.modem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the read-only surface {@link TileCable} exposes to addons: the icon table halves and the two pieces of
 * peripheral state the network and the renderer need.
 *
 * <p>
 * The icon array is private and only populated by {@code registerIcons} on the client, so the tests install stand-in
 * icons into it reflectively. That is the point of the getters: callers outside the package cannot see the array, and
 * the face/side split is the only thing {@code getModemFaceTexture} did not already publish.
 * </p>
 */
class TileCableExposureTest {

    /** Stand-in icons, installed into {@code TileCable.s_modemIcons} before each test. */
    private static final IIcon[] ICONS = new IIcon[8];

    /**
     * {@code TileEntity.writeToNBT} stamps a registry name, so the round-trip test needs one. Nothing else in the tests
     * registers tile entities, and a duplicate registration (if another test got here first) is harmless.
     */
    @BeforeAll
    static void registerTileEntityName() {
        try {
            TileEntity.addMapping(TileCable.class, "computercraft_test:wired_modem");
        } catch (IllegalArgumentException alreadyRegistered) {}
    }

    @BeforeEach
    void installIcons() throws Exception {
        Field field = TileCable.class.getDeclaredField("s_modemIcons");
        field.setAccessible(true);
        IIcon[] icons = (IIcon[]) field.get(null);
        for (int i = 0; i < icons.length; i++) {
            if (ICONS[i] == null) {
                ICONS[i] = mock(IIcon.class, "modemIcon" + i);
            }
            icons[i] = ICONS[i];
        }
    }

    // =========================================================================
    // Icon table halves
    // =========================================================================

    @Test
    void faceTextureUsesEvenIndices() {
        for (int anim = 0; anim < 4; anim++) {
            assertEquals(ICONS[anim * 2], TileCable.getModemFaceTexture(anim), "face icon for anim " + anim);
        }
    }

    @Test
    void sideTextureUsesOddIndices() {
        for (int anim = 0; anim < 4; anim++) {
            assertEquals(ICONS[anim * 2 + 1], TileCable.getModemSideTexture(anim), "side icon for anim " + anim);
        }
    }

    @Test
    void faceAndSideTexturesAreDisjoint() {
        for (int anim = 0; anim < 4; anim++) {
            assertNotSame(
                TileCable.getModemFaceTexture(anim),
                TileCable.getModemSideTexture(anim),
                "anim " + anim + " must have distinct face and side icons");
        }
    }

    @Test
    void animationStateIsClampedInBothGetters() {
        assertEquals(ICONS[0], TileCable.getModemFaceTexture(-3));
        assertEquals(ICONS[1], TileCable.getModemSideTexture(-3));
        assertEquals(ICONS[6], TileCable.getModemFaceTexture(99));
        assertEquals(ICONS[7], TileCable.getModemSideTexture(99));
    }

    // =========================================================================
    // Peripheral state
    // =========================================================================

    @Test
    void defaultsMatchAFreshlyPlacedModem() {
        TileCable tile = new TileCable();

        assertEquals(-1, tile.getAttachedPeripheralID(), "a modem that has never been attached has no id");
        assertFalse(tile.isPeripheralAccessAllowed(), "a freshly placed modem does not expose its peripheral");
    }

    @Test
    void gettersReflectPersistedState() {
        TileCable tile = new TileCable();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("peripheralAccess", true);
        tag.setInteger("peripheralID", 4321);

        tile.readFromNBT(tag);

        assertTrue(tile.isPeripheralAccessAllowed());
        assertEquals(4321, tile.getAttachedPeripheralID());
    }

    @Test
    void gettersSurviveASaveLoadRoundTrip() {
        TileCable saved = new TileCable();
        NBTTagCompound fromNbt = new NBTTagCompound();
        fromNbt.setBoolean("peripheralAccess", true);
        fromNbt.setInteger("peripheralID", 17);
        saved.readFromNBT(fromNbt);

        NBTTagCompound written = new NBTTagCompound();
        saved.writeToNBT(written);

        TileCable loaded = new TileCable();
        loaded.readFromNBT(written);

        assertTrue(loaded.isPeripheralAccessAllowed());
        assertEquals(17, loaded.getAttachedPeripheralID());
        assertEquals(17, written.getInteger("peripheralID"));
        assertTrue(written.getBoolean("peripheralAccess"));
    }
}
