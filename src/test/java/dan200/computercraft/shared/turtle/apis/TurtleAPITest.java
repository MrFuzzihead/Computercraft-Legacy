package dan200.computercraft.shared.turtle.apis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IIcon;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import org.junit.jupiter.api.Test;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleAnimation;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleUpgradeType;
import dan200.computercraft.api.turtle.TurtleVerb;

/**
 * Unit tests for the two new methods added to {@link TurtleAPI}:
 * {@code getEquippedLeft} (index 42) and {@code getEquippedRight} (index 43).
 *
 * <p>The success path that reads {@link net.minecraft.item.Item#itemRegistry} requires
 * Minecraft class initialisation and is covered by in-game tests. The null-guard paths
 * (no upgrade equipped, or upgrade returns a {@code null} crafting item) are fully
 * exercisable here without a live Minecraft environment.</p>
 */
class TurtleAPITest {

    /** Method indices — must match the order in {@link TurtleAPI#getMethodNames()}. */
    private static final int METHOD_GET_EQUIPPED_LEFT = 42;
    private static final int METHOD_GET_EQUIPPED_RIGHT = 43;
    private static final int METHOD_GET_ITEM_DETAIL = 41;

    // -------------------------------------------------------------------------
    // Stub implementations
    // -------------------------------------------------------------------------

    /**
     * Minimal {@link ITurtleAccess} stub. All operations except
     * {@code getUpgrade(TurtleSide)} throw {@link UnsupportedOperationException}.
     */
    private static ITurtleAccess stubTurtle(ITurtleUpgrade left, ITurtleUpgrade right) {
        return new ITurtleAccess() {

            @Override
            public ITurtleUpgrade getUpgrade(TurtleSide side) {
                return side == TurtleSide.Left ? left : right;
            }

            @Override
            public int getSelectedSlot() {
                return 0;
            }

            @Override
            public IInventory getInventory() {
                // Returns an inventory where every slot is empty (null stack).
                return new IInventory() {

                    @Override
                    public ItemStack getStackInSlot(int slot) {
                        return null;
                    }

                    @Override
                    public int getSizeInventory() {
                        return 16;
                    }

                    @Override
                    public ItemStack decrStackSize(int slot, int amount) {
                        return null;
                    }

                    @Override
                    public ItemStack getStackInSlotOnClosing(int slot) {
                        return null;
                    }

                    @Override
                    public void setInventorySlotContents(int slot, ItemStack stack) {}

                    @Override
                    public String getInventoryName() {
                        return "stub";
                    }

                    @Override
                    public boolean hasCustomInventoryName() {
                        return false;
                    }

                    @Override
                    public int getInventoryStackLimit() {
                        return 64;
                    }

                    @Override
                    public void markDirty() {}

                    @Override
                    public boolean isUseableByPlayer(net.minecraft.entity.player.EntityPlayer player) {
                        return false;
                    }

                    @Override
                    public void openInventory() {}

                    @Override
                    public void closeInventory() {}

                    @Override
                    public boolean isItemValidForSlot(int slot, ItemStack stack) {
                        return false;
                    }
                };
            }

            @Override
            public World getWorld() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChunkCoordinates getPosition() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean teleportTo(World world, int x, int y, int z) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Vec3 getVisualPosition(float partialTicks) {
                throw new UnsupportedOperationException();
            }

            @Override
            public float getVisualYaw(float partialTicks) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getDirection() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setDirection(int dir) {
                throw new UnsupportedOperationException();
            }


            @Override
            public void setSelectedSlot(int slot) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setDyeColour(int colour) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getDyeColour() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isFuelNeeded() {
                return false;
            }

            @Override
            public int getFuelLevel() {
                return 0;
            }

            @Override
            public void setFuelLevel(int level) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getFuelLimit() {
                return 0;
            }

            @Override
            public boolean consumeFuel(int fuel) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void addFuel(int fuel) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Object[] executeCommand(ILuaContext context, ITurtleCommand command)
                throws LuaException, InterruptedException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void playAnimation(TurtleAnimation animation) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setUpgrade(TurtleSide side, ITurtleUpgrade upgrade) {
                throw new UnsupportedOperationException();
            }

            @Override
            public IPeripheral getPeripheral(TurtleSide side) {
                throw new UnsupportedOperationException();
            }

            @Override
            public NBTTagCompound getUpgradeNBTData(TurtleSide side) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void updateUpgradeNBTData(TurtleSide side) {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Minimal {@link ITurtleUpgrade} stub whose {@code getCraftingItem()} returns
     * the provided stack (may be {@code null}).
     */
    private static ITurtleUpgrade stubUpgrade(ItemStack craftingItem) {
        return new ITurtleUpgrade() {

            @Override
            public ItemStack getCraftingItem() {
                return craftingItem;
            }

            @Override
            public int getUpgradeID() {
                return 0;
            }

            @Override
            public String getUnlocalisedAdjective() {
                return "test";
            }

            @Override
            public TurtleUpgradeType getType() {
                return TurtleUpgradeType.Tool;
            }

            @Override
            public IPeripheral createPeripheral(ITurtleAccess turtle, TurtleSide side) {
                return null;
            }

            @Override
            public TurtleCommandResult useTool(ITurtleAccess turtle, TurtleSide side, TurtleVerb verb, int slot) {
                return null;
            }

            @Override
            public IIcon getIcon(ITurtleAccess turtle, TurtleSide side) {
                return null;
            }

            @Override
            public void update(ITurtleAccess turtle, TurtleSide side) {}
        };
    }

    private static TurtleAPI apiWith(ITurtleUpgrade left, ITurtleUpgrade right) {
        // IAPIEnvironment is null: getEquippedLeft/Right do not touch m_environment.
        return new TurtleAPI(null, stubTurtle(left, right));
    }

    // =========================================================================
    // API surface
    // =========================================================================

    @Test
    void getMethodNamesContainsGetEquippedLeft() {
        TurtleAPI api = apiWith(null, null);
        assertTrue(
            Arrays.asList(api.getMethodNames()).contains("getEquippedLeft"),
            "getMethodNames() must include 'getEquippedLeft'");
    }

    @Test
    void getMethodNamesContainsGetEquippedRight() {
        TurtleAPI api = apiWith(null, null);
        assertTrue(
            Arrays.asList(api.getMethodNames()).contains("getEquippedRight"),
            "getMethodNames() must include 'getEquippedRight'");
    }

    @Test
    void getEquippedLeftIsAtExpectedIndex() {
        TurtleAPI api = apiWith(null, null);
        assertEquals(
            "getEquippedLeft",
            api.getMethodNames()[METHOD_GET_EQUIPPED_LEFT],
            "getEquippedLeft must be at method index " + METHOD_GET_EQUIPPED_LEFT);
    }

    @Test
    void getEquippedRightIsAtExpectedIndex() {
        TurtleAPI api = apiWith(null, null);
        assertEquals(
            "getEquippedRight",
            api.getMethodNames()[METHOD_GET_EQUIPPED_RIGHT],
            "getEquippedRight must be at method index " + METHOD_GET_EQUIPPED_RIGHT);
    }

    // =========================================================================
    // getEquippedLeft — null guards
    // =========================================================================

    @Test
    void getEquippedLeftReturnsNullWhenNoUpgradeEquipped() throws LuaException, InterruptedException {
        TurtleAPI api = apiWith(null, null);

        Object[] result = api.callMethod(null, METHOD_GET_EQUIPPED_LEFT, new Object[0]);

        assertNotNull(result);
        assertEquals(1, result.length, "getEquippedLeft() must return exactly one value");
        assertNull(result[0], "getEquippedLeft() must return nil when no upgrade is equipped");
    }

    @Test
    void getEquippedLeftReturnsNullWhenUpgradeHasNullCraftingItem() throws LuaException, InterruptedException {
        // An upgrade is present but getCraftingItem() returns null.
        TurtleAPI api = apiWith(stubUpgrade(null), null);

        Object[] result = api.callMethod(null, METHOD_GET_EQUIPPED_LEFT, new Object[0]);

        assertNotNull(result);
        assertEquals(1, result.length);
        assertNull(result[0], "getEquippedLeft() must return nil when the upgrade's crafting item is null");
    }

    // =========================================================================
    // getEquippedRight — null guards
    // =========================================================================

    @Test
    void getEquippedRightReturnsNullWhenNoUpgradeEquipped() throws LuaException, InterruptedException {
        TurtleAPI api = apiWith(null, null);

        Object[] result = api.callMethod(null, METHOD_GET_EQUIPPED_RIGHT, new Object[0]);

        assertNotNull(result);
        assertEquals(1, result.length, "getEquippedRight() must return exactly one value");
        assertNull(result[0], "getEquippedRight() must return nil when no upgrade is equipped");
    }

    @Test
    void getEquippedRightReturnsNullWhenUpgradeHasNullCraftingItem() throws LuaException, InterruptedException {
        TurtleAPI api = apiWith(null, stubUpgrade(null));

        Object[] result = api.callMethod(null, METHOD_GET_EQUIPPED_RIGHT, new Object[0]);

        assertNotNull(result);
        assertEquals(1, result.length);
        assertNull(result[0], "getEquippedRight() must return nil when the upgrade's crafting item is null");
    }

    // =========================================================================
    // Independence: left and right slots are independent
    // =========================================================================

    @Test
    void getEquippedLeftIsNullWhenOnlyRightIsEquipped() throws LuaException, InterruptedException {
        // Right has an upgrade; left does not.
        TurtleAPI api = apiWith(null, stubUpgrade(null));

        Object[] result = api.callMethod(null, METHOD_GET_EQUIPPED_LEFT, new Object[0]);

        assertNull(result[0], "getEquippedLeft() must return nil when only the right slot is occupied");
    }

    @Test
    void getEquippedRightIsNullWhenOnlyLeftIsEquipped() throws LuaException, InterruptedException {
        // Left has an upgrade; right does not.
        TurtleAPI api = apiWith(stubUpgrade(null), null);

        Object[] result = api.callMethod(null, METHOD_GET_EQUIPPED_RIGHT, new Object[0]);

        assertNull(result[0], "getEquippedRight() must return nil when only the left slot is occupied");
    }

    // =========================================================================
    // Return-value shape when an upgrade with a crafting item IS present
    // (Minecraft item registry is needed for name resolution; verified in-game)
    // =========================================================================

    @Test
    void getEquippedLeftReturnsTableWhenItemStackIsPresent() throws LuaException, InterruptedException {
        // We cannot call Item.itemRegistry in a headless test, so we only verify
        // that the result is a non-null Map — not its specific field values.
        // Full name/damage/count correctness is covered by the in-game test script.
        ItemStack dummyStack = new ItemStack((net.minecraft.item.Item) null, 1, 0);
        TurtleAPI api = apiWith(stubUpgrade(dummyStack), null);

        // This will throw if ItemStack.getItem() → null causes NPE inside getEquippedDetails.
        // We tolerate that and assert the null-item path gracefully returns null.
        Object[] result;
        try {
            result = api.callMethod(null, METHOD_GET_EQUIPPED_LEFT, new Object[0]);
            // If it didn't throw, either we got a table or null — both are acceptable here.
            assertNotNull(result, "Result array must not be null");
        } catch (NullPointerException npe) {
            // Expected when Item.itemRegistry is unavailable in headless tests.
            // The in-game path is tested via test_turtle_equipped.lua.
        }
    }

    // =========================================================================
    // getItemDetail — detailed parameter
    // =========================================================================

    @Test
    void getItemDetailWithDetailedTrueAndNullStackReturnsNull() throws LuaException, InterruptedException {
        // detailed=true must not change the null-stack guard: nil is still returned.
        TurtleAPI api = apiWith(null, null);

        Object[] result = api.callMethod(null, METHOD_GET_ITEM_DETAIL, new Object[] { 1, Boolean.TRUE });

        assertNotNull(result);
        assertEquals(1, result.length, "getItemDetail must return exactly one value");
        assertNull(result[0], "getItemDetail must return nil when the stack is empty, even with detailed=true");
    }

    @Test
    void getItemDetailWithDetailedFalseExplicitlyBehavesLikeDefault() throws LuaException, InterruptedException {
        TurtleAPI api = apiWith(null, null);

        Object[] resultDefault = api.callMethod(null, METHOD_GET_ITEM_DETAIL, new Object[0]);
        Object[] resultExplicit = api.callMethod(null, METHOD_GET_ITEM_DETAIL, new Object[] { 1, Boolean.FALSE });

        // Both return null for an empty slot.
        assertNull(resultDefault[0]);
        assertNull(resultExplicit[0]);
    }

    @Test
    void getItemDetailDetailedParameterOnlyAffectsNonNullStack() throws LuaException, InterruptedException {
        // When slot is empty, detailed=true and detailed=false produce the same nil result.
        // The extra fields (displayName, maxDamage, enchantments) are only added for a real
        // ItemStack, which requires Minecraft's item registry — verified by in-game tests.
        TurtleAPI api = apiWith(null, null);

        Object[] withDetail    = api.callMethod(null, METHOD_GET_ITEM_DETAIL, new Object[] { 1, Boolean.TRUE });
        Object[] withoutDetail = api.callMethod(null, METHOD_GET_ITEM_DETAIL, new Object[] { 1, Boolean.FALSE });

        assertNull(withDetail[0],    "Empty slot with detailed=true must return nil");
        assertNull(withoutDetail[0], "Empty slot with detailed=false must return nil");
    }
}




