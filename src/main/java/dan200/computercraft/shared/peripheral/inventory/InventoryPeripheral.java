package dan200.computercraft.shared.peripheral.inventory;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralTargeted;
import dan200.computercraft.shared.util.InventoryUtil;

/**
 * Generic peripheral that wraps any {@link IInventory} tile entity and exposes
 * the six CC:Tweaked {@code inventory} methods:
 * {@code size}, {@code list}, {@code getItemDetail}, {@code getItemLimit},
 * {@code pushItems}, and {@code pullItems}.
 *
 * <p>
 * All six methods dispatch to the server/main thread via
 * {@link ILuaContext#executeMainThreadTask} so inventory reads and writes are
 * always safe to perform.
 * </p>
 */
public class InventoryPeripheral implements IPeripheralTargeted {

    private static final String[] METHOD_NAMES = new String[] { "size", "list", "getItemDetail", "getItemLimit",
        "pushItems", "pullItems", };

    private static final int METHOD_SIZE = 0;
    private static final int METHOD_LIST = 1;
    private static final int METHOD_GET_ITEM_DETAIL = 2;
    private static final int METHOD_GET_ITEM_LIMIT = 3;
    private static final int METHOD_PUSH_ITEMS = 4;
    private static final int METHOD_PULL_ITEMS = 5;

    private final TileEntity m_tile;
    private final World m_world;
    private final int m_x;
    private final int m_y;
    private final int m_z;

    public InventoryPeripheral(TileEntity tile) {
        this.m_tile = tile;
        this.m_world = tile.getWorldObj();
        this.m_x = tile.xCoord;
        this.m_y = tile.yCoord;
        this.m_z = tile.zCoord;
    }

    @Override
    public String getType() {
        return "inventory";
    }

    @Override
    public Object getTarget() {
        return m_tile;
    }

    @Override
    public String[] getMethodNames() {
        return METHOD_NAMES;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] args)
        throws LuaException, InterruptedException {
        switch (method) {
            case METHOD_SIZE: {
                return context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        IInventory inv = getInventory();
                        return new Object[] { inv.getSizeInventory() };
                    }
                });
            }
            case METHOD_LIST: {
                return context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        IInventory inv = getInventory();
                        Map<Object, Object> result = new HashMap<>();
                        for (int i = 0; i < inv.getSizeInventory(); i++) {
                            ItemStack stack = inv.getStackInSlot(i);
                            if (stack != null) {
                                result.put(i + 1, makeBasicDetail(stack));
                            }
                        }
                        return new Object[] { result };
                    }
                });
            }
            case METHOD_GET_ITEM_DETAIL: {
                if (args.length < 1 || !(args[0] instanceof Number)) {
                    throw new LuaException("Expected number");
                }
                final int slot = ((Number) args[0]).intValue();
                return context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        IInventory inv = getInventory();
                        validateSlot(inv, slot);
                        ItemStack stack = inv.getStackInSlot(slot - 1);
                        if (stack == null) {
                            return new Object[] { null };
                        }
                        return new Object[] { makeFullDetail(stack) };
                    }
                });
            }
            case METHOD_GET_ITEM_LIMIT: {
                if (args.length < 1 || !(args[0] instanceof Number)) {
                    throw new LuaException("Expected number");
                }
                final int slot = ((Number) args[0]).intValue();
                return context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        IInventory inv = getInventory();
                        validateSlot(inv, slot);
                        return new Object[] { inv.getInventoryStackLimit() };
                    }
                });
            }
            case METHOD_PUSH_ITEMS: {
                if (args.length < 2 || !(args[0] instanceof String) || !(args[1] instanceof Number)) {
                    throw new LuaException("Expected string, number");
                }
                final String toName = (String) args[0];
                final int fromSlot = ((Number) args[1]).intValue();
                final int limit = (args.length >= 3 && args[2] instanceof Number) ? ((Number) args[2]).intValue()
                    : Integer.MAX_VALUE;
                final int toSlot = (args.length >= 4 && args[3] instanceof Number) ? ((Number) args[3]).intValue() : -1;
                return context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        IInventory from = getInventory();
                        validateSlot(from, fromSlot);
                        IInventory to = resolveInventoryPeripheral(computer, toName);
                        int effectiveLimit = limit == Integer.MAX_VALUE ? getSourceStackSize(from, fromSlot - 1)
                            : limit;
                        int moved = moveItems(from, fromSlot - 1, to, toSlot <= 0 ? -1 : toSlot - 1, effectiveLimit);
                        return new Object[] { moved };
                    }
                });
            }
            case METHOD_PULL_ITEMS: {
                if (args.length < 2 || !(args[0] instanceof String) || !(args[1] instanceof Number)) {
                    throw new LuaException("Expected string, number");
                }
                final String fromName = (String) args[0];
                final int fromSlot = ((Number) args[1]).intValue();
                final int limit = (args.length >= 3 && args[2] instanceof Number) ? ((Number) args[2]).intValue()
                    : Integer.MAX_VALUE;
                final int toSlot = (args.length >= 4 && args[3] instanceof Number) ? ((Number) args[3]).intValue() : -1;
                return context.executeMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() throws LuaException {
                        IInventory to = getInventory();
                        if (toSlot > 0) {
                            validateSlot(to, toSlot);
                        }
                        IInventory from = resolveInventoryPeripheral(computer, fromName);
                        validateSlot(from, fromSlot);
                        int effectiveLimit = limit == Integer.MAX_VALUE ? getSourceStackSize(from, fromSlot - 1)
                            : limit;
                        int moved = moveItems(from, fromSlot - 1, to, toSlot <= 0 ? -1 : toSlot - 1, effectiveLimit);
                        return new Object[] { moved };
                    }
                });
            }
            default:
                return null;
        }
    }

    @Override
    public void attach(IComputerAccess computer) {}

    @Override
    public void detach(IComputerAccess computer) {}

    @Override
    public boolean equals(IPeripheral other) {
        if (other instanceof InventoryPeripheral) {
            InventoryPeripheral otherInv = (InventoryPeripheral) other;
            return otherInv.m_tile == this.m_tile;
        }
        return false;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Resolves the live (possibly merged double-chest) {@link IInventory} at
     * this peripheral's world position. When no world is available (e.g. in
     * unit tests), the stored tile entity is returned directly.
     */
    private IInventory getInventory() {
        if (m_world == null) {
            return (IInventory) m_tile;
        }
        return InventoryUtil.getInventory(m_world, m_x, m_y, m_z, -1);
    }

    /**
     * Validates that {@code slot} is in the 1-indexed range
     * {@code [1, inv.getSizeInventory()]}.
     */
    private static void validateSlot(IInventory inv, int slot) throws LuaException {
        if (slot < 1 || slot > inv.getSizeInventory()) {
            throw new LuaException("Slot out of range");
        }
    }

    /**
     * Resolves a named peripheral from the computer's modem network and verifies
     * that it targets an {@link IInventory}.
     */
    private static IInventory resolveInventoryPeripheral(IComputerAccess computer, String name) throws LuaException {
        Map<String, IPeripheral> peripherals = computer.getAvailablePeripherals();
        IPeripheral periph = peripherals.get(name);
        if (periph == null) {
            throw new LuaException("Target '" + name + "' does not exist");
        }
        if (!(periph instanceof IPeripheralTargeted)) {
            throw new LuaException("Target '" + name + "' is not an inventory");
        }
        Object target = ((IPeripheralTargeted) periph).getTarget();
        if (!(target instanceof IInventory)) {
            throw new LuaException("Target '" + name + "' is not an inventory");
        }
        return (IInventory) target;
    }

    /** Returns the stack size of the item in {@code fromSlot} (0-indexed), or 0 if empty. */
    private static int getSourceStackSize(IInventory inv, int fromSlot) {
        ItemStack stack = inv.getStackInSlot(fromSlot);
        return stack != null ? stack.stackSize : 0;
    }

    /**
     * Moves up to {@code limit} items from {@code from[fromSlot]} to
     * {@code to[toSlot]} (or any available slot if {@code toSlot < 0}).
     * Both slot indices are 0-based.
     *
     * @return the number of items actually moved
     */
    private static int moveItems(IInventory from, int fromSlot, IInventory to, int toSlot, int limit) {
        ItemStack source = from.getStackInSlot(fromSlot);
        if (source == null || source.stackSize == 0 || limit <= 0) {
            return 0;
        }

        int toMove = Math.min(limit, source.stackSize);
        ItemStack moving = source.copy();
        moving.stackSize = toMove;

        int moved;
        if (toSlot >= 0) {
            // Place into a specific destination slot.
            ItemStack dest = to.getStackInSlot(toSlot);
            if (dest == null) {
                int space = to.getInventoryStackLimit();
                int actual = Math.min(toMove, space);
                ItemStack placed = moving.copy();
                placed.stackSize = actual;
                to.setInventorySlotContents(toSlot, placed);
                to.markDirty();
                moved = actual;
            } else if (InventoryUtil.areItemsStackable(dest, moving)) {
                int space = Math.min(dest.getMaxStackSize(), to.getInventoryStackLimit()) - dest.stackSize;
                int actual = Math.min(toMove, space);
                if (actual <= 0) {
                    return 0;
                }
                dest.stackSize += actual;
                to.setInventorySlotContents(toSlot, dest);
                to.markDirty();
                moved = actual;
            } else {
                return 0;
            }
        } else {
            // Store into any available slot.
            ItemStack remainder = InventoryUtil.storeItems(moving, to, 0, to.getSizeInventory(), 0);
            moved = toMove - (remainder != null ? remainder.stackSize : 0);
        }

        // Deduct from source.
        if (moved > 0) {
            source.stackSize -= moved;
            if (source.stackSize <= 0) {
                from.setInventorySlotContents(fromSlot, null);
            } else {
                from.setInventorySlotContents(fromSlot, source);
            }
            from.markDirty();
        }
        return moved;
    }

    /**
     * Returns a basic item detail map ({@code name}, {@code count}, {@code damage})
     * suitable for use in the {@code list()} result table.
     */
    private static Map<Object, Object> makeBasicDetail(ItemStack stack) {
        Map<Object, Object> detail = new HashMap<>();
        detail.put("name", Item.itemRegistry.getNameForObject(stack.getItem()));
        detail.put("count", stack.stackSize);
        detail.put("damage", stack.getItemDamage());
        return detail;
    }

    /**
     * Returns a full item detail map ({@code name}, {@code count}, {@code damage},
     * {@code maxCount}, {@code displayName}) suitable for use in
     * {@code getItemDetail()}.
     */
    private static Map<Object, Object> makeFullDetail(ItemStack stack) {
        Map<Object, Object> detail = makeBasicDetail(stack);
        detail.put("maxCount", stack.getMaxStackSize());
        detail.put("displayName", stack.getDisplayName());
        return detail;
    }
}
