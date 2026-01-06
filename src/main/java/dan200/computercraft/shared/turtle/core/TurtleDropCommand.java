package dan200.computercraft.shared.turtle.core;

import java.util.Arrays;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Facing;
import net.minecraft.world.World;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleAnimation;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.util.InventoryUtil;
import dan200.computercraft.shared.util.WorldUtil;

public class TurtleDropCommand implements ITurtleCommand {

    private final InteractDirection m_direction;
    private final int m_quantity;
    private final String COMMANDNAME = "drop";

    public TurtleDropCommand(InteractDirection direction, int quantity) {
        this.m_direction = direction;
        this.m_quantity = quantity;
    }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        if (Arrays.asList(ComputerCraft.turtleDisabledActions)
            .contains(COMMANDNAME)) {
            return TurtleCommandResult.failure("Turtle action \"" + COMMANDNAME + "\" is disabled");
        }

        if (this.m_quantity == 0) {
            turtle.playAnimation(TurtleAnimation.Wait);
            return TurtleCommandResult.success();
        } else {
            int direction = this.m_direction.toWorldDir(turtle);
            ItemStack stack = InventoryUtil.takeItems(
                this.m_quantity,
                turtle.getInventory(),
                turtle.getSelectedSlot(),
                1,
                turtle.getSelectedSlot());
            if (stack == null) {
                return TurtleCommandResult.failure("No items to drop");
            } else {
                World world = turtle.getWorld();
                ChunkCoordinates oldPosition = turtle.getPosition();
                ChunkCoordinates newPosition = WorldUtil.moveCoords(oldPosition, direction);
                int side = Facing.oppositeSide[direction];
                IInventory inventory = InventoryUtil
                    .getInventory(world, newPosition.posX, newPosition.posY, newPosition.posZ, side);
                if (inventory != null) {
                    ItemStack remainder = InventoryUtil.storeItems(stack, inventory, side);
                    if (remainder != null) {
                        InventoryUtil.storeItems(
                            remainder,
                            turtle.getInventory(),
                            0,
                            turtle.getInventory()
                                .getSizeInventory(),
                            turtle.getSelectedSlot());
                    }

                    if (remainder != stack) {
                        turtle.playAnimation(TurtleAnimation.Wait);
                        return TurtleCommandResult.success();
                    } else {
                        return TurtleCommandResult.failure("No space for items");
                    }
                } else {
                    WorldUtil
                        .dropItemStack(stack, world, oldPosition.posX, oldPosition.posY, oldPosition.posZ, direction);
                    world.playSoundEffect(
                        newPosition.posX + 0.5,
                        newPosition.posY + 0.5,
                        newPosition.posZ + 0.5,
                        "random.pop",
                        0.2F,
                        ((world.rand.nextFloat() - world.rand.nextFloat()) * 0.7F + 1.0F) * 2.0F);
                    turtle.playAnimation(TurtleAnimation.Wait);
                    return TurtleCommandResult.success();
                }
            }
        }
    }
}
