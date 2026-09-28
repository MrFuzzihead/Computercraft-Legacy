package dan200.computercraft.shared.turtle.core;

import java.util.ArrayList;
import java.util.Arrays;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.util.WorldUtil;

public class TurtleCompareCommand implements ITurtleCommand {

    private final InteractDirection m_direction;
    private final String COMMANDNAME = "compare";

    public TurtleCompareCommand(InteractDirection direction) {
        this.m_direction = direction;
    }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        if (Arrays.asList(ComputerCraft.turtleDisabledActions)
            .contains(COMMANDNAME)) {
            return TurtleCommandResult.failure("Turtle action \"" + COMMANDNAME + "\" is disabled");
        }

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
                    lookAtStack = createStackedStack(Item.getItemFromBlock(lookAtBlock), lookAtMetadata);
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
                    lookAtStack = createStackedStack(Item.getItemFromBlock(lookAtBlock), lookAtMetadata);
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

    /**
     * Builds the single-item stack a block contributes to a comparison, mirroring
     * {@code Block#createStackedBlock(int)} — which is protected in vanilla and so cannot be
     * called from here. Only items with subtypes carry metadata; for everything else the damage
     * value is pinned to 0.
     *
     * <p>
     * A null {@code item} means the block has no item form at all. That is not hypothetical:
     * {@code Item.registerItems()} skips an explicit exclusion set when it gives blocks their
     * item forms, so vanilla blocks such as redstone wire, repeaters, comparators, signs, beds,
     * doors, skulls, tripwire, brewing stands, cauldrons, flower pots, cake and crops have no
     * entry at their block id, and {@code Item#getItemFromBlock} returns null for them. Mods can
     * do the same by passing a null item class to {@code GameRegistry#registerBlock}. Passing
     * that null into the {@code ItemStack} constructor throws, which used to escape this command
     * entirely (no try/catch anywhere between here and the world tick). Such a block can never
     * match a held item, so reporting "no stack" lets the comparison fail normally instead.
     * </p>
     *
     * @param item     the block's item form, or null if it has none
     * @param metadata the block's metadata
     * @return the comparison stack, or null when the block has no item form
     */
    static ItemStack createStackedStack(Item item, int metadata) {
        if (item == null) {
            return null;
        }

        return new ItemStack(item, 1, item.getHasSubtypes() ? metadata : 0);
    }
}
