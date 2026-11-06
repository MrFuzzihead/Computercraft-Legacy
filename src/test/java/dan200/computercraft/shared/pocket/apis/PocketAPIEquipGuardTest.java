package dan200.computercraft.shared.pocket.apis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.testsupport.LuaResults;

/**
 * Tests for the {@code upgrade} guard at the head of {@link PocketAPI}'s {@code equipBack()}.
 *
 * <h2>What changed</h2>
 * <p>
 * The guard used to be {@code tag.hasKey("upgrade") && PocketUpgrades.isUpgrade(id)}: it only refused when the id
 * belonged to ComputerCraft's own table (1–6). Addons register their own ids into the same key on the same item, so a
 * third-party upgrade was silently overwritten — and the peripheral it had attached was orphaned, since
 * {@code unequipBack()} only gives back ids it knows. Any present id now refuses the equip.
 * </p>
 *
 * <h2>Why these are unit tests</h2>
 * <p>
 * The guard runs before the inventory scan, so it needs no item registry and no live {@code ServerComputer}. The
 * success path beyond it still requires both and remains covered in game.
 * </p>
 */
class PocketAPIEquipGuardTest {

    private static final String ALREADY_EQUIPPED = "Upgrade already equipped";
    private static final String NOTHING_TO_EQUIP = "No upgrade to equip";

    /**
     * Runs {@link ILuaTask} callbacks synchronously so no Minecraft main thread is needed.
     */
    private static final ILuaContext SYNC_CONTEXT = new ILuaContext() {

        @Override
        public MethodResult executeMainThreadTask(ILuaTask task) throws LuaException {
            return MethodResult.of(task.execute());
        }

        @Override
        public long issueMainThreadTask(ILuaTask task) throws LuaException {
            return 0;
        }
    };

    private PocketAPI api;
    private ItemStack stack;
    private InventoryPlayer inventory;

    @BeforeEach
    void setUp() {
        api = new PocketAPI(null);
        stack = new ItemStack((Item) null, 1, 0);
        inventory = mock(InventoryPlayer.class);
        EntityPlayer player = mock(EntityPlayer.class);
        player.inventory = inventory;
        api.update(player, stack, null);
    }

    private Object[] equipBack() throws LuaException, InterruptedException {
        return LuaResults.unwrap(api.callMethod(SYNC_CONTEXT, 0, new Object[0]));
    }

    private void setUpgradeTag(int id) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("upgrade", id);
        stack.setTagCompound(tag);
    }

    // =========================================================================
    // Ids ComputerCraft owns — refused before and after the change
    // =========================================================================

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3, 4, 5, 6 })
    void refusesWhenOneOfOurUpgradesIsInstalled(int id) throws LuaException, InterruptedException {
        setUpgradeTag(id);

        Object[] result = equipBack();

        assertEquals(Boolean.FALSE, result[0]);
        assertEquals(ALREADY_EQUIPPED, result[1]);
    }

    // =========================================================================
    // Ids ComputerCraft does not own — the behaviour change
    // =========================================================================

    @ParameterizedTest
    @ValueSource(ints = { 0, 7, 8, 100, 32766, -1 })
    void refusesWhenAForeignUpgradeIdIsInstalled(int id) throws LuaException, InterruptedException {
        setUpgradeTag(id);

        Object[] result = equipBack();

        assertEquals(Boolean.FALSE, result[0]);
        assertEquals(ALREADY_EQUIPPED, result[1], "a foreign upgrade id must not be treated as free");
    }

    @Test
    void refusesWhenTheUpgradeTagIsNotAnInteger() throws LuaException, InterruptedException {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("upgrade", "someaddon:laser");
        stack.setTagCompound(tag);

        Object[] result = equipBack();

        assertEquals(ALREADY_EQUIPPED, result[1], "presence of the key is what means 'installed'");
    }

    @Test
    void refusalLeavesTheInventoryAndTagAlone() throws LuaException, InterruptedException {
        setUpgradeTag(7);

        equipBack();

        verify(inventory, never()).setInventorySlotContents(anyInt(), any());
        verify(inventory, never()).markDirty();
        assertEquals(
            7,
            stack.getTagCompound()
                .getInteger("upgrade"),
            "the existing upgrade id must survive");
    }

    // =========================================================================
    // Nothing installed — the guard must not fire
    // =========================================================================

    @Test
    void reportsNothingToEquipWhenTheStackHasNoTag() throws LuaException, InterruptedException {
        Object[] result = equipBack();

        assertEquals(Boolean.FALSE, result[0]);
        assertEquals(NOTHING_TO_EQUIP, result[1]);
    }

    @Test
    void reportsNothingToEquipWhenTheTagHasNoUpgradeKey() throws LuaException, InterruptedException {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("computerID", 12);
        stack.setTagCompound(tag);

        Object[] result = equipBack();

        assertEquals(NOTHING_TO_EQUIP, result[1]);
    }

    @Test
    void reportsNothingToEquipWhenNoStackIsHeld() throws LuaException, InterruptedException {
        api.update(mock(EntityPlayer.class), null, null);

        Object[] result = equipBack();

        assertEquals(Boolean.FALSE, result[0]);
        assertEquals("Cannot find player", result[1], "the player/stack guard still runs first");
    }

    // =========================================================================
    // Result shape
    // =========================================================================

    @Test
    void refusalIsAFreshTwoValueArray() throws LuaException, InterruptedException {
        setUpgradeTag(4);

        Object[] first = equipBack();
        Object[] second = equipBack();

        assertNotNull(first);
        assertEquals(2, first.length);
        assertNotSame(first, second, "each call must build its own result array");
        assertFalse((Boolean) first[0]);
    }
}
