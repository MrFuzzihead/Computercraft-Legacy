package dan200.computercraft.impl;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.item.ItemStack;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleRefuelHandler;

/**
 * The registry of {@link TurtleRefuelHandler}s consulted by {@code turtle.refuel()}.
 *
 * <p>
 * ComputerCraft registers a furnace-burn-time handler of its own; everything else here comes from
 * other mods. Registration is append-only and first-registered wins, so a handler may shadow the
 * built-in one for a particular item.
 * </p>
 */
public final class TurtleRefuelHandlers {

    private static final List<TurtleRefuelHandler> handlers = new CopyOnWriteArrayList<>();

    private TurtleRefuelHandlers() {}

    /**
     * Registers a refuel handler.
     *
     * @param handler the handler to add
     * @throws NullPointerException if {@code handler} is {@code null}
     */
    public static synchronized void register(TurtleRefuelHandler handler) {
        Objects.requireNonNull(handler, "handler cannot be null");
        handlers.add(handler);
    }

    /**
     * Offers an item to every registered handler in turn.
     *
     * @param turtle the turtle being refuelled
     * @param stack  the stack to refuel with
     * @param slot   the slot the stack occupies
     * @param limit  the maximum number of refuel operations; may be zero, for the {@code refuel(0)} probe
     * @return the fuel gained by the first handler that accepts the item, or an empty {@link OptionalInt} if none did
     * @throws IllegalStateException if a handler returns a negative amount, or consumes anything while {@code limit} is
     *                               zero — both are handler bugs that would otherwise corrupt a turtle's inventory
     */
    public static OptionalInt refuel(ITurtleAccess turtle, ItemStack stack, int slot, int limit) {
        for (TurtleRefuelHandler handler : handlers) {
            OptionalInt fuel = handler.refuel(turtle, stack, slot, limit);
            if (fuel.isPresent()) {
                int refuelled = fuel.getAsInt();
                if (refuelled < 0) {
                    throw new IllegalStateException(handler + " returned a negative fuel amount");
                }

                if (limit == 0 && refuelled != 0) {
                    throw new IllegalStateException(handler + " refuelled despite a limit of 0");
                }

                return fuel;
            }
        }

        return OptionalInt.empty();
    }
}
