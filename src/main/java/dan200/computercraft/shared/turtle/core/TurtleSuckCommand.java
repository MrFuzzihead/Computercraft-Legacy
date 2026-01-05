package dan200.computercraft.shared.turtle.core;

import java.util.Arrays;
import java.util.List;

import dan200.computercraft.ComputerCraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Facing;
import net.minecraft.world.World;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleAnimation;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.util.InventoryUtil;
import dan200.computercraft.shared.util.WorldUtil;

public class TurtleSuckCommand implements ITurtleCommand {

    private final InteractDirection m_direction;
    private final int m_quantity;
    private final String COMMANDNAME = "suck";

    public TurtleSuckCommand(InteractDirection direction, int quantity) {
        this.m_direction = direction;
        this.m_quantity = quantity;
    }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        if (Arrays.asList(ComputerCraft.turtleDisabledActions).contains(COMMANDNAME)) {
            return TurtleCommandResult.failure("Turtle action \"" + COMMANDNAME + "\" is disabled");
        }

        if (this.m_quantity == 0) {
            turtle.playAnimation(TurtleAnimation.Wait);
            return TurtleCommandResult.success();
        } else {
            int direction = this.m_direction.toWorldDir(turtle);
            World world = turtle.getWorld();
            ChunkCoordinates oldPosition = turtle.getPosition();
            ChunkCoordinates newPosition = WorldUtil.moveCoords(oldPosition, direction);
            int side = Facing.oppositeSide[direction];
            IInventory inventory = InventoryUtil
                .getInventory(world, newPosition.posX, newPosition.posY, newPosition.posZ, side);
            if (inventory != null) {
                ItemStack stack = InventoryUtil.takeItems(this.m_quantity, inventory, side);
                if (stack != null) {
                    ItemStack remainder = InventoryUtil.storeItems(
                        stack,
                        turtle.getInventory(),
                        0,
                        turtle.getInventory()
                            .getSizeInventory(),
                        turtle.getSelectedSlot());
                    if (remainder != null) {
                        InventoryUtil.storeItems(remainder, inventory, side);
                    }

                    if (remainder != stack) {
                        turtle.playAnimation(TurtleAnimation.Wait);
                        return TurtleCommandResult.success();
                    } else {
                        return TurtleCommandResult.failure("No space for items");
                    }
                } else {
                    return TurtleCommandResult.failure("No items to take");
                }
            } else {
                AxisAlignedBB aabb = Blocks.dirt
                    .getCollisionBoundingBoxFromPool(world, newPosition.posX, newPosition.posY, newPosition.posZ);
                List list = world.getEntitiesWithinAABBExcludingEntity(null, aabb);
                if (list.size() > 0) {
                    boolean foundItems = false;
                    boolean storedItems = false;

                    for (int i = 0; i < list.size(); i++) {
                        Entity entity = (Entity) list.get(i);
                        if (entity != null && entity instanceof EntityItem && !entity.isDead) {
                            foundItems = true;
                            EntityItem entityItem = (EntityItem) entity;
                            ItemStack stack = entityItem.getEntityItem()
                                .copy();
                            ItemStack storeStack;
                            ItemStack leaveStack;
                            if (stack.stackSize > this.m_quantity) {
                                storeStack = stack.splitStack(this.m_quantity);
                                leaveStack = stack;
                            } else {
                                storeStack = stack;
                                leaveStack = null;
                            }

                            ItemStack remainderx = InventoryUtil.storeItems(
                                storeStack,
                                turtle.getInventory(),
                                0,
                                turtle.getInventory()
                                    .getSizeInventory(),
                                turtle.getSelectedSlot());
                            if (remainderx != storeStack) {
                                storedItems = true;
                                if (remainderx == null && leaveStack == null) {
                                    entityItem.setDead();
                                    break;
                                }

                                if (remainderx == null) {
                                    entityItem.setEntityItemStack(leaveStack);
                                } else if (leaveStack == null) {
                                    entityItem.setEntityItemStack(remainderx);
                                } else {
                                    leaveStack.stackSize = leaveStack.stackSize + remainderx.stackSize;
                                    entityItem.setEntityItemStack(leaveStack);
                                }
                                break;
                            }
                        }
                    }

                    if (foundItems) {
                        if (storedItems) {
                            world.playSoundEffect(
                                oldPosition.posX + 0.5,
                                oldPosition.posY + 0.5,
                                oldPosition.posZ + 0.5,
                                "random.pop",
                                0.2F,
                                ((world.rand.nextFloat() - world.rand.nextFloat()) * 0.7F + 1.0F) * 2.0F);
                            turtle.playAnimation(TurtleAnimation.Wait);
                            return TurtleCommandResult.success();
                        }

                        return TurtleCommandResult.failure("No space for items");
                    }
                }

                return TurtleCommandResult.failure("No items to take");
            }
        }
    }
}
