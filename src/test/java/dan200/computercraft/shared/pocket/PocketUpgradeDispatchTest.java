package dan200.computercraft.shared.pocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.api.pocket.IPocketUpgrade;
import dan200.computercraft.core.computer.ComputerThread;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.computer.core.ServerComputerRegistry;
import dan200.computercraft.shared.pocket.apis.PocketAPI;
import dan200.computercraft.shared.pocket.items.ItemPocketComputer;
import dan200.computercraft.testsupport.LuaResults;

/**
 * Tests for the pocket upgrade seam: the three lifecycle moments a mod's upgrade now gets called at.
 *
 * <p>
 * Before this existed, an addon's only option was to mixin {@code ItemPocketComputer} at all three — the peripheral
 * creation inside {@code createServerComputer}, the per-tick {@code onUpdate} chain of {@code instanceof} checks, and
 * the display-name chain — because the upgrade itself was an {@code int} in ComputerCraft's NBT and ComputerCraft owned
 * every place that {@code int} was read. These tests drive a mod-registered upgrade through each moment and assert the
 * unregistered path still behaves exactly as before.
 * </p>
 */
class PocketUpgradeDispatchTest {

    private static final int MOD_UPGRADE_ID = 71;
    private static final int INSTANCE_ID = 0;

    /** Records what the seam calls, and hands out a peripheral the test can recognise. */
    private static final class RecordingUpgrade implements IPocketUpgrade {

        private final ItemStack craftingItem;
        private final IPeripheral peripheral;
        private final List<IPocketAccess> creates = new ArrayList<>();
        private final List<IPocketAccess> updates = new ArrayList<>();
        private final List<IPeripheral> updatedPeripherals = new ArrayList<>();

        private RecordingUpgrade(ItemStack craftingItem) {
            this.craftingItem = craftingItem;
            this.peripheral = mock(IPeripheral.class);
            when(this.peripheral.getType()).thenReturn("test_peripheral");
            when(this.peripheral.getMethodNames()).thenReturn(new String[0]);
        }

        @Override
        public int getUpgradeID() {
            return MOD_UPGRADE_ID;
        }

        @Override
        public String getUnlocalisedAdjective() {
            return "test.mod_upgrade.adjective";
        }

        @Override
        public ItemStack getCraftingItem() {
            return this.craftingItem;
        }

        @Override
        public NBTTagCompound getUpgradeData(ItemStack stack) {
            // Matched by stack identity rather than by item: headless, ItemStack#getItem() is null for every stack,
            // because Forge fills Item#delegate from the FML registry and no registry is running here. The default
            // rule (item + damage against getCraftingItem()) is therefore exercised in game, not here.
            if (stack != this.craftingItem) return null;

            NBTTagCompound data = new NBTTagCompound();
            data.setString("marker", "carried");
            return data;
        }

        @Override
        public IPeripheral createPeripheral(IPocketAccess access) {
            this.creates.add(access);
            return this.peripheral;
        }

        @Override
        public void update(IPocketAccess access, IPeripheral peripheral) {
            this.updates.add(access);
            this.updatedPeripherals.add(peripheral);
        }
    }

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

    /**
     * Real items, not mocks: {@code ItemStack#setItem} copies {@code item.delegate}, a Forge registry field that only
     * real registered items have, so a mocked {@code Item} yields a stack whose {@code getItem()} is {@code null} and
     * nothing matches it. A block's item is the simplest real one to hand.
     */
    private final Item craftingItem = Item.getItemFromBlock(net.minecraft.init.Blocks.stone);
    private final Item otherItem = Item.getItemFromBlock(net.minecraft.init.Blocks.dirt);
    private final ItemPocketComputer item = new ItemPocketComputer();
    private ServerComputerRegistry realRegistry;
    private ServerComputerRegistry registry;
    private RecordingUpgrade upgrade;

    @BeforeEach
    void installMockRegistry() {
        realRegistry = ComputerCraft.serverComputerRegistry;
        registry = mock(ServerComputerRegistry.class);
        ComputerCraft.serverComputerRegistry = registry;
        when(registry.getUnusedInstanceID()).thenReturn(INSTANCE_ID);

        upgrade = new RecordingUpgrade(new ItemStack(craftingItem));
        PocketUpgrades.register(upgrade);
    }

    @AfterEach
    void restoreRegistry() {
        PocketUpgrades.resetModUpgradesForTesting();
        ComputerCraft.serverComputerRegistry = realRegistry;
        ComputerThread.stop();
    }

    /** A pocket computer stack carrying a computer id and an installed upgrade id. */
    private ItemStack pocketStack(int upgradeId) {
        ItemStack stack = new ItemStack(item);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("computerID", 42);
        tag.setInteger("upgrade", upgradeId);
        stack.setTagCompound(tag);
        return stack;
    }

    // =========================================================================
    // Reading the id back
    // =========================================================================

    @Test
    void aRegisteredModUpgradeIsVisibleInTheStack() {
        ItemStack stack = pocketStack(MOD_UPGRADE_ID);

        assertEquals(MOD_UPGRADE_ID, item.getUpgrade(stack), "a registered id must not be clamped away");
    }

    @Test
    void anUnregisteredIdStillReadsAsNoUpgrade() {
        ItemStack stack = pocketStack(MOD_UPGRADE_ID + 1);

        assertEquals(-1, item.getUpgrade(stack));
    }

    // =========================================================================
    // createServerComputer — the peripheral
    // =========================================================================

    @Test
    void creatingTheComputerAttachesTheModPeripheralToTheBackSide() {
        ItemStack stack = pocketStack(MOD_UPGRADE_ID);
        World world = mock(World.class);

        ServerComputer computer = item.createServerComputer(world, null, stack);

        assertNotNull(computer);
        assertSame(upgrade.peripheral, computer.getPeripheral(2));
        assertEquals(1, upgrade.creates.size(), "the peripheral is built exactly once");
    }

    @Test
    void creatingTheComputerForAnUnregisteredIdAttachesNothing() {
        ItemStack stack = pocketStack(MOD_UPGRADE_ID + 1);

        ServerComputer computer = item.createServerComputer(mock(World.class), null, stack);

        assertNotNull(computer);
        assertNull(computer.getPeripheral(2), "an unknown id is not an upgrade, as before");
        assertTrue(upgrade.creates.isEmpty());
    }

    @Test
    void theAccessHandedToTheUpgradeSeesTheStack() {
        ItemStack stack = pocketStack(MOD_UPGRADE_ID);

        item.createServerComputer(mock(World.class), null, stack);

        assertSame(
            stack,
            upgrade.creates.get(0)
                .getStack());
    }

    // =========================================================================
    // onUpdate — the tick
    // =========================================================================

    @Test
    void tickingDispatchesToTheUpgradeAndNotToAnInstanceofChain() {
        ItemStack stack = pocketStack(MOD_UPGRADE_ID);
        World world = mock(World.class);
        Entity holder = mock(Entity.class);

        item.createServerComputer(world, null, stack);
        ServerComputer computer = computerFor(stack, world);
        when(registry.contains(INSTANCE_ID)).thenReturn(true);
        when(registry.get(INSTANCE_ID)).thenReturn(computer);

        item.onUpdate(stack, world, holder, 0, true);

        assertEquals(1, upgrade.updates.size(), "one update per tick");
        assertSame(
            holder,
            upgrade.updates.get(0)
                .getEntity(),
            "the upgrade is told who is holding the computer");
        assertSame(
            upgrade.peripheral,
            upgrade.updatedPeripherals.get(0),
            "the peripheral built at creation is passed back");
    }

    private ServerComputer computerFor(ItemStack stack, World world) {
        ServerComputer computer = mock(ServerComputer.class);
        when(computer.getWorld()).thenReturn(world);
        when(computer.getPeripheral(2)).thenReturn(upgrade.peripheral);
        when(computer.getInstanceID()).thenReturn(INSTANCE_ID);
        when(computer.getID()).thenReturn(42);
        when(computer.getLabel()).thenReturn(null);
        return computer;
    }

    @Test
    void tickingAnUnregisteredUpgradeDoesNothing() {
        ItemStack stack = pocketStack(MOD_UPGRADE_ID + 1);
        World world = mock(World.class);

        ServerComputer computer = computerFor(stack, world);
        when(registry.contains(INSTANCE_ID)).thenReturn(true);
        when(registry.get(INSTANCE_ID)).thenReturn(computer);

        item.onUpdate(stack, world, mock(Entity.class), 0, true);

        assertTrue(upgrade.updates.isEmpty());
    }

    // =========================================================================
    // pocket.equipBack / unequipBack
    // =========================================================================

    @Test
    void equipBackInstallsARegisteredModUpgrade() throws LuaException, InterruptedException {
        ItemStack pocket = new ItemStack(item);
        pocket.setTagCompound(new NBTTagCompound());
        ServerComputer computer = mock(ServerComputer.class);
        PocketAPI api = new PocketAPI(computer);

        EntityPlayer player = mock(EntityPlayer.class);
        IInventory inventory = mock(IInventory.class);
        player.inventory = mock(net.minecraft.entity.player.InventoryPlayer.class);
        when(player.inventory.getSizeInventory()).thenReturn(1);
        when(player.inventory.getStackInSlot(0)).thenReturn(upgrade.getCraftingItem());
        api.update(player, pocket, inventory);

        Object[] result = LuaResults.unwrap(api.callMethod(SYNC_CONTEXT, 0, new Object[0]));

        assertEquals(Boolean.TRUE, result[0]);
        assertEquals(
            MOD_UPGRADE_ID,
            pocket.getTagCompound()
                .getInteger("upgrade"));
        assertTrue(
            pocket.getTagCompound()
                .hasKey("upgradeData"),
            "the upgrade's data is stored with it: " + pocket.getTagCompound());
        verify(computer).setPeripheral(eq(2), any(IPeripheral.class));
        verify(inventory).markDirty();
    }

    @Test
    void equipBackDoesNotConsumeAnItemItCannotInstall() throws LuaException, InterruptedException {
        ItemStack pocket = new ItemStack(item);
        pocket.setTagCompound(new NBTTagCompound());
        PocketAPI api = new PocketAPI(mock(ServerComputer.class));

        EntityPlayer player = mock(EntityPlayer.class);
        IInventory inventory = mock(IInventory.class);
        player.inventory = mock(net.minecraft.entity.player.InventoryPlayer.class);
        when(player.inventory.getSizeInventory()).thenReturn(1);
        when(player.inventory.getStackInSlot(0)).thenReturn(new ItemStack(otherItem));
        api.update(player, pocket, inventory);

        Object[] result = LuaResults.unwrap(api.callMethod(SYNC_CONTEXT, 0, new Object[0]));

        assertEquals(Boolean.FALSE, result[0]);
        assertEquals("No upgrade to equip", result[1]);
        verify(player.inventory, never()).setInventorySlotContents(anyInt(), any());
    }

    @Test
    void unequipBackGivesBackTheRegisteredUpgradesOwnItem() throws LuaException, InterruptedException {
        ItemStack pocket = pocketStack(MOD_UPGRADE_ID);
        ServerComputer computer = mock(ServerComputer.class);
        PocketAPI api = new PocketAPI(computer);

        EntityPlayer player = mock(EntityPlayer.class);
        IInventory inventory = mock(IInventory.class);
        player.inventory = mock(net.minecraft.entity.player.InventoryPlayer.class);
        when(player.inventory.addItemStackToInventory(any(ItemStack.class))).thenReturn(true);
        api.update(player, pocket, inventory);

        Object[] result = LuaResults.unwrap(api.callMethod(SYNC_CONTEXT, 1, new Object[0]));

        assertEquals(Boolean.TRUE, result[0]);
        assertFalse(
            pocket.getTagCompound()
                .hasKey("upgrade"),
            "the upgrade flag is cleared");
        assertFalse(
            pocket.getTagCompound()
                .hasKey("upgradeData"),
            "the data that went with it is cleared");
        verify(player.inventory).addItemStackToInventory(any(ItemStack.class));
        verify(computer).setPeripheral(2, null);
    }

    @Test
    void unequipBackRefusesAnUpgradeThatIsNoLongerInstalled() throws LuaException, InterruptedException {
        ItemStack pocket = pocketStack(MOD_UPGRADE_ID + 1);
        PocketAPI api = new PocketAPI(mock(ServerComputer.class));

        EntityPlayer player = mock(EntityPlayer.class);
        player.inventory = mock(net.minecraft.entity.player.InventoryPlayer.class);
        api.update(player, pocket, null);

        Object[] result = LuaResults.unwrap(api.callMethod(SYNC_CONTEXT, 1, new Object[0]));

        assertEquals(Boolean.FALSE, result[0]);
        assertEquals(
            "Nothing equipped",
            result[1],
            "a save from an uninstalled mod must not lose its upgrade silently");
    }
}
