package dan200.computercraft.shared.turtle.upgrades;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleVerb;
import dan200.computercraft.shared.turtle.core.TurtlePlaceCommand;

public class TurtleHoe extends TurtleTool {

    public TurtleHoe(int id, String adjective, Item item) {
        super(id, adjective, item);
    }

    @Override
    protected boolean canBreakBlock(World world, int x, int y, int z) {
        if (!super.canBreakBlock(world, x, y, z)) {
            return false;
        } else {
            Block block = world.getBlock(x, y, z);
            return block.getMaterial() == Material.plants || block.getMaterial() == Material.cactus
                || block.getMaterial() == Material.gourd
                || block.getMaterial() == Material.leaves
                || block.getMaterial() == Material.vine;
        }
    }

    @Override
    public TurtleCommandResult useTool(ITurtleAccess turtle, TurtleSide side, TurtleVerb verb, int direction) {
        if (verb == TurtleVerb.Dig) {
            ItemStack hoe = this.m_item.copy();
            ItemStack remainder = TurtlePlaceCommand.deploy(hoe, turtle, direction, null, null);
            if (remainder != hoe) {
                return TurtleCommandResult.success();
            }
        }

        return super.useTool(turtle, side, verb, direction);
    }
}
