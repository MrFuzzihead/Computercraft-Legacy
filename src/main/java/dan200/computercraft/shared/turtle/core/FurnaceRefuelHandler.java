package dan200.computercraft.shared.turtle.core;

import java.util.OptionalInt;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleRefuelHandler;
import dan200.computercraft.shared.util.InventoryUtil;

/**
 * Burns ordinary furnace fuel, which is what ComputerCraft has always accepted: an item's furnace burn time, at five
 * fuel per second of burn.
 *
 * <p>
 * This is the built-in handler, registered at mod init. Anything more exotic — a battery, a capacitor — is a
 * {@link TurtleRefuelHandler} registered by another mod.
 * </p>
 */
public class FurnaceRefuelHandler implements TurtleRefuelHandler {

    @Override
    public OptionalInt refuel(ITurtleAccess turtle, ItemStack stack, int slot, int limit) {
        int fuelPerItem = this.getFuelPerItem(stack);
        if (fuelPerItem <= 0) {
            return OptionalInt.empty();
        }

        if (limit == 0) {
            // The refuel(0) probe: this item is fuel, but nothing may be consumed.
            return OptionalInt.of(0);
        }

        IInventory inventory = turtle.getInventory();
        int fuelSpaceLeft = turtle.getFuelLimit() - turtle.getFuelLevel();
        if (fuelSpaceLeft <= 0) {
            // Tank full. Better than the old behaviour, which consumed the stack and let addFuel() clamp the gain.
            return OptionalInt.of(0);
        }

        // Only take as many items as will fit in the tank, so a nearly-full turtle does not burn the rest for nothing.
        int fuelItemLimit = (int) Math.ceil(fuelSpaceLeft / (double) fuelPerItem);
        ItemStack consumed = InventoryUtil.takeItems(Math.min(limit, fuelItemLimit), inventory, slot, 1, slot);
        if (consumed == null) {
            // Only reachable if a handler ahead of this one already drained the slot.
            return OptionalInt.empty();
        }

        ItemStack replacement = consumed.getItem()
            .getContainerItem(consumed);
        if (replacement != null) {
            InventoryUtil.storeItems(replacement, inventory, 0, inventory.getSizeInventory(), slot);
        }

        return OptionalInt.of(fuelPerItem * consumed.stackSize);
    }

    /**
     * The fuel an item is worth, in ComputerCraft fuel units.
     *
     * @param stack the item to test
     * @return the fuel per item, or zero if this is not furnace fuel
     */
    protected int getFuelPerItem(ItemStack stack) {
        return TileEntityFurnace.getItemBurnTime(stack) * 5 / 100;
    }
}
