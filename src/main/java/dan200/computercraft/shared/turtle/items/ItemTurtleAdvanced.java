package dan200.computercraft.shared.turtle.items;

import net.minecraft.block.Block;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.computer.core.ComputerFamily;

public class ItemTurtleAdvanced extends ItemTurtleNormal {

    public ItemTurtleAdvanced(Block block) {
        super(block);
        this.setUnlocalizedName("computercraft:advanced_turtle");
        this.setCreativeTab(ComputerCraft.mainCreativeTab);
    }

    @Override
    public ComputerFamily getFamily(int damage) {
        return ComputerFamily.Advanced;
    }
}
