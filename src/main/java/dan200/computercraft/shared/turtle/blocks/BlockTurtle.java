package dan200.computercraft.shared.turtle.blocks;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.computer.blocks.BlockComputerBase;
import dan200.computercraft.shared.computer.blocks.TileComputerBase;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.util.DirectionUtil;

public class BlockTurtle extends BlockComputerBase {

    public static BlockTurtle createTurtleBlock() {
        return new BlockTurtle();
    }

    public BlockTurtle() {
        super(Material.iron);
        this.setHardness(2.5F);
        this.setBlockName("computercraft:turtle");
        this.setCreativeTab(ComputerCraft.mainCreativeTab);
    }

    @Override
    protected int getDefaultMetadata(ComputerFamily family, int placedSide) {
        return 0;
    }

    @Override
    public ComputerFamily getFamily(int metadata) {
        return this == ComputerCraft.Blocks.turtleAdvanced ? ComputerFamily.Advanced : ComputerFamily.Normal;
    }

    @Override
    protected TileComputerBase createTile(ComputerFamily family) {
        if (this == ComputerCraft.Blocks.turtleAdvanced) {
            return new TileTurtleAdvanced();
        } else {
            return (TileComputerBase) (this == ComputerCraft.Blocks.turtleExpanded ? new TileTurtleExpanded()
                : new TileTurtle());
        }
    }

    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack itemstack) {
        int dir = DirectionUtil.fromEntityRot(player);
        this.setDirection(world, x, y, z, Facing.oppositeSide[dir]);
    }

    @Override
    protected IIcon getItemTexture(ComputerFamily family, int side) {
        return TileTurtle.getItemTexture(family == ComputerFamily.Advanced);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister iconRegister) {
        super.registerBlockIcons(iconRegister);
        TileTurtle.registerIcons(iconRegister);
    }
}
