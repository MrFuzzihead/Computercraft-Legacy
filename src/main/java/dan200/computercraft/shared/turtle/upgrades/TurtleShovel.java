package dan200.computercraft.shared.turtle.upgrades;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.world.World;

public class TurtleShovel extends TurtleTool {

    public TurtleShovel(int id, String adjective, Item item) {
        super(id, adjective, item);
    }

    @Override
    protected boolean canBreakBlock(World world, int x, int y, int z) {
        if (!super.canBreakBlock(world, x, y, z)) {
            return false;
        } else {
            Block block = world.getBlock(x, y, z);
            return block.getMaterial() == Material.ground || block.getMaterial() == Material.sand
                || block.getMaterial() == Material.snow
                || block.getMaterial() == Material.clay
                || block.getMaterial() == Material.craftedSnow
                || block.getMaterial() == Material.grass
                || block.getMaterial() == Material.plants
                || block.getMaterial() == Material.cactus
                || block.getMaterial() == Material.gourd
                || block.getMaterial() == Material.leaves
                || block.getMaterial() == Material.vine;
        }
    }
}
