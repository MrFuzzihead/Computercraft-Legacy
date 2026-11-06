package dan200.computercraft.api.turtle;

import java.util.OptionalInt;

import net.minecraft.item.ItemStack;

import dan200.computercraft.api.ComputerCraftAPI;

/**
 * A source of turtle fuel other than the furnace burn time of the item in the turtle's selected slot.
 *
 * <p>
 * Handlers are called by {@code turtle.refuel()} in registration order, and the first one to return a
 * present {@link OptionalInt} wins. Use this to treat a battery, a capacitor or any other item that
 * carries charge as fuel, or to accept an item Forge's fuel registry does not know about.
 * </p>
 *
 * <p>
 * A handler must not modify the turtle's fuel level itself: return the fuel gained and the command
 * adds it. It is responsible for consuming its own items, because only it knows whether the item is
 * used up (a coal is) or merely discharged (a battery is).
 * </p>
 *
 * <p>
 * The stack is never {@code null} and always resides in the given slot (1.7.10 has no empty
 * {@code ItemStack} sentinel: the command reports "No items to combust" before handlers run).
 * </p>
 *
 * @see ComputerCraftAPI#registerRefuelHandler(TurtleRefuelHandler)
 */
public interface TurtleRefuelHandler {

    /**
     * Attempt to refuel a turtle with an item.
     *
     * @param turtle the turtle being refuelled
     * @param stack  the stack to refuel with; never {@code null}
     * @param slot   the inventory slot the stack occupies, for handlers that consume from the inventory
     * @param limit  the maximum number of refuel operations to perform. This usually corresponds to the number of
     *               items allowed to be consumed, and {@code turtle.refuel()} with no argument passes 64.
     *               <p>
     *               This value may be zero: that is the {@code turtle.refuel(0)} probe. A handler must still report
     *               whether it recognises the item (returning {@code OptionalInt.of(0)} if so), but must
     *               <em>not</em> modify the stack or the inventory in that case.
     * @return the fuel gained, or an empty {@link OptionalInt} if this handler does not accept the item. Returning a
     *         present zero means "handled, but nothing was gained" — for example when the tank is already full.
     */
    OptionalInt refuel(ITurtleAccess turtle, ItemStack stack, int slot, int limit);
}
