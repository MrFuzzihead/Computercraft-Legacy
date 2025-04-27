package dan200.computercraft.shared.turtle.core;

import java.lang.reflect.Method;
import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.ReflectionHelper;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.util.WorldUtil;

public class TurtleCompareCommand implements ITurtleCommand {

    private final InteractDirection m_direction;

    public TurtleCompareCommand(InteractDirection direction) {
        this.m_direction = direction;
    }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        int direction = this.m_direction.toWorldDir(turtle);
        ItemStack selectedStack = turtle.getInventory()
            .getStackInSlot(turtle.getSelectedSlot());
        World world = turtle.getWorld();
        ChunkCoordinates oldPosition = turtle.getPosition();
        ChunkCoordinates newPosition = WorldUtil.moveCoords(oldPosition, direction);
        ItemStack lookAtStack = null;
        if (WorldUtil.isBlockInWorld(world, newPosition)
            && !world.isAirBlock(newPosition.posX, newPosition.posY, newPosition.posZ)) {
            Block lookAtBlock = world.getBlock(newPosition.posX, newPosition.posY, newPosition.posZ);
            if (lookAtBlock != null
                && !lookAtBlock.isAir(world, newPosition.posX, newPosition.posY, newPosition.posZ)) {
                int lookAtMetadata = world.getBlockMetadata(newPosition.posX, newPosition.posY, newPosition.posZ);
                if (!lookAtBlock.hasTileEntity(lookAtMetadata)) {
                    try {
                        Method method = ReflectionHelper.findMethod(
                            Block.class,
                            lookAtBlock,
                            new String[] { "func_149644_j", "j", "createStackedBlock" },
                            new Class[] { int.class });
                        if (method != null) {
                            lookAtStack = (ItemStack) method.invoke(lookAtBlock, lookAtMetadata);
                        }
                    } catch (Exception var14) {}
                }

                for (int i = 0; i < 5 && lookAtStack == null; i++) {
                    ArrayList<ItemStack> drops = lookAtBlock
                        .getDrops(world, newPosition.posX, newPosition.posY, newPosition.posZ, lookAtMetadata, 0);
                    if (drops != null && drops.size() > 0) {
                        for (ItemStack drop : drops) {
                            if (drop.getItem() == Item.getItemFromBlock(lookAtBlock)) {
                                lookAtStack = drop;
                                break;
                            }
                        }
                    }
                }

                if (lookAtStack == null) {
                    Item item = Item.getItemFromBlock(lookAtBlock);
                    if (item != null && item.getHasSubtypes()) {
                        lookAtStack = new ItemStack(item, 1, lookAtMetadata);
                    } else {
                        lookAtStack = new ItemStack(item, 1, 0);
                    }
                }
            }
        }

        if (selectedStack == null && lookAtStack == null) {
            return TurtleCommandResult.success();
        } else {
            if (selectedStack != null && lookAtStack != null && selectedStack.getItem() == lookAtStack.getItem()) {
                if (!selectedStack.getHasSubtypes()) {
                    return TurtleCommandResult.success();
                }

                if (selectedStack.getItemDamage() == lookAtStack.getItemDamage()) {
                    return TurtleCommandResult.success();
                }

                if (selectedStack.getUnlocalizedName()
                    .equals(lookAtStack.getUnlocalizedName())) {
                    return TurtleCommandResult.success();
                }
            }

            return TurtleCommandResult.failure();
        }
    }
}
