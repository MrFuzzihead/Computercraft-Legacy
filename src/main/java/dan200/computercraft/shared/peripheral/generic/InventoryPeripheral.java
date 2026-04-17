package dan200.computercraft.shared.peripheral.generic;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

/**
 * A peripheral that wraps any {@link IInventory} tile entity, exposing its
 * contents to Lua scripts in a manner consistent with CC:Tweaked's
 * {@code inventory} peripheral.
 */
public class InventoryPeripheral implements IPeripheral {

    private static final String[] METHOD_NAMES = new String[] {
        "size",
        "list",
        "getItemDetail",
        "pushItems",
        "pullItems",
    };

    private final IInventory m_inventory;
    private final String m_type;

    public InventoryPeripheral(IInventory inventory) {
        this.m_inventory = inventory;
        // Derive the type from the block's registry name when the inventory is a TileEntity.
        String type = "inventory";
        if (inventory instanceof TileEntity) {
            TileEntity te = (TileEntity) inventory;
            Block block = te.getBlockType();
            if (block != null) {
                String name = Block.blockRegistry.getNameForObject(block);
                if (name != null && !name.isEmpty()) {
                    type = name;
                }
            }
        }
        this.m_type = type;
    }

    @Override
    public String getType() {
        return this.m_type;
    }

    @Override
    public String[] getMethodNames() {
        return METHOD_NAMES;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] args)
        throws LuaException, InterruptedException {
        switch (method) {
            case 0: // size()
                return new Object[] { this.m_inventory.getSizeInventory() };

            case 1: { // list()
                Map<Object, Object> result = new HashMap<>();
                for (int i = 0; i < this.m_inventory.getSizeInventory(); i++) {
                    ItemStack stack = this.m_inventory.getStackInSlot(i);
                    if (stack != null) {
                        Map<Object, Object> item = new HashMap<>();
                        item.put("name", Block.blockRegistry.getNameForObject(Block.getBlockFromItem(stack.getItem())) != null
                            ? Block.blockRegistry.getNameForObject(Block.getBlockFromItem(stack.getItem()))
                            : net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()));
                        item.put("count", stack.stackSize);
                        item.put("damage", stack.getItemDamage());
                        result.put(i + 1, item);
                    }
                }
                return new Object[] { result };
            }

            case 2: { // getItemDetail(slot)
                int slot = this.getSlot(args, 0) - 1; // 1-indexed to 0-indexed
                ItemStack stack = this.m_inventory.getStackInSlot(slot);
                if (stack == null) return new Object[] { null };
                Map<Object, Object> item = new HashMap<>();
                String itemName = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
                item.put("name", itemName != null ? itemName : "unknown");
                item.put("count", stack.stackSize);
                item.put("damage", stack.getItemDamage());
                if (stack.hasDisplayName()) {
                    item.put("displayName", stack.getDisplayName());
                }
                return new Object[] { item };
            }

            case 3: { // pushItems(toName, fromSlot[, limit[, toSlot]])
                String toName = this.getString(args, 0);
                int fromSlot = this.getSlot(args, 1) - 1;
                int limit = args.length >= 3 && args[2] instanceof Number ? ((Number) args[2]).intValue() : 64;
                int toSlot = args.length >= 4 && args[3] instanceof Number ? ((Number) args[3]).intValue() - 1 : -1;

                IPeripheral toPeripheral = computer.getAvailablePeripheral(toName);
                if (toPeripheral == null) throw new LuaException("Target '" + toName + "' does not exist");
                IInventory toInv = this.getInventoryFromPeripheral(toPeripheral);
                if (toInv == null) throw new LuaException("Target '" + toName + "' is not an inventory");

                return new Object[] { this.moveItems(this.m_inventory, fromSlot, toInv, toSlot, limit) };
            }

            case 4: { // pullItems(fromName, fromSlot[, limit[, toSlot]])
                String fromName = this.getString(args, 0);
                int fromSlot = this.getSlot(args, 1) - 1;
                int limit = args.length >= 3 && args[2] instanceof Number ? ((Number) args[2]).intValue() : 64;
                int toSlot = args.length >= 4 && args[3] instanceof Number ? ((Number) args[3]).intValue() - 1 : -1;

                IPeripheral fromPeripheral = computer.getAvailablePeripheral(fromName);
                if (fromPeripheral == null) throw new LuaException("Source '" + fromName + "' does not exist");
                IInventory fromInv = this.getInventoryFromPeripheral(fromPeripheral);
                if (fromInv == null) throw new LuaException("Source '" + fromName + "' is not an inventory");

                return new Object[] { this.moveItems(fromInv, fromSlot, this.m_inventory, toSlot, limit) };
            }

            default:
                throw new LuaException("Unknown method");
        }
    }

    private int moveItems(IInventory source, int sourceSlot, IInventory dest, int destSlot, int limit) {
        ItemStack sourceStack = source.getStackInSlot(sourceSlot);
        if (sourceStack == null || limit <= 0) return 0;

        int toMove = Math.min(limit, sourceStack.stackSize);
        int moved = 0;

        int destSize = dest.getSizeInventory();
        int startSlot = destSlot >= 0 ? destSlot : 0;
        int endSlot = destSlot >= 0 ? destSlot + 1 : destSize;

        for (int i = startSlot; i < endSlot && toMove > 0; i++) {
            ItemStack destStack = dest.getStackInSlot(i);
            if (destStack == null) {
                int amount = Math.min(toMove, Math.min(sourceStack.getMaxStackSize(), dest.getInventoryStackLimit()));
                ItemStack newStack = sourceStack.copy();
                newStack.stackSize = amount;
                dest.setInventorySlotContents(i, newStack);
                sourceStack.stackSize -= amount;
                if (sourceStack.stackSize <= 0) {
                    source.setInventorySlotContents(sourceSlot, null);
                }
                toMove -= amount;
                moved += amount;
            } else if (destStack.isItemEqual(sourceStack) && destStack.stackSize < destStack.getMaxStackSize()) {
                int space = Math.min(destStack.getMaxStackSize() - destStack.stackSize, dest.getInventoryStackLimit());
                int amount = Math.min(toMove, space);
                destStack.stackSize += amount;
                sourceStack.stackSize -= amount;
                if (sourceStack.stackSize <= 0) {
                    source.setInventorySlotContents(sourceSlot, null);
                }
                toMove -= amount;
                moved += amount;
            }
        }

        dest.markDirty();
        source.markDirty();
        return moved;
    }

    private IInventory getInventoryFromPeripheral(IPeripheral peripheral) {
        if (peripheral instanceof dan200.computercraft.api.peripheral.IPeripheralTargeted) {
            Object target = ((dan200.computercraft.api.peripheral.IPeripheralTargeted) peripheral).getTarget();
            if (target instanceof IInventory) return (IInventory) target;
        }
        if (peripheral instanceof InventoryPeripheral) {
            return ((InventoryPeripheral) peripheral).m_inventory;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Argument helpers
    // -------------------------------------------------------------------------

    private String getString(Object[] args, int index) throws LuaException {
        if (args.length <= index || !(args[index] instanceof String)) {
            throw new LuaException("Expected string at argument " + (index + 1));
        }
        return (String) args[index];
    }

    private int getSlot(Object[] args, int index) throws LuaException {
        if (args.length <= index || !(args[index] instanceof Number)) {
            throw new LuaException("Expected number at argument " + (index + 1));
        }
        int slot = ((Number) args[index]).intValue();
        if (slot < 1 || slot > this.m_inventory.getSizeInventory()) {
            throw new LuaException(
                "Slot out of range (expected 1-" + this.m_inventory.getSizeInventory() + ", got " + slot + ")");
        }
        return slot;
    }

    // -------------------------------------------------------------------------
    // IPeripheral lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void attach(IComputerAccess computer) {}

    @Override
    public void detach(IComputerAccess computer) {}

    @Override
    public boolean equals(IPeripheral other) {
        if (other instanceof InventoryPeripheral) {
            return ((InventoryPeripheral) other).m_inventory == this.m_inventory;
        }
        return false;
    }
}

