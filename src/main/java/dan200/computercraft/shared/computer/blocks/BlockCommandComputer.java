package dan200.computercraft.shared.computer.blocks;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.util.DirectionUtil;

public class BlockCommandComputer extends BlockComputerBase {

    public static BlockCommandComputer createComputerBlock() {
        return new BlockCommandComputer();
    }

    public BlockCommandComputer() {
        super(Material.iron);
        this.setBlockUnbreakable();
        this.setResistance(6000000.0F);
        this.setBlockName("computercraft:command_computer");
        this.setCreativeTab(ComputerCraft.mainCreativeTab);
    }

    @Override
    protected int getDefaultMetadata(ComputerFamily family, int placedSide) {
        if (placedSide < 2) {
            placedSide = 2;
        }

        return placedSide;
    }

    @Override
    public ComputerFamily getFamily(int metadata) {
        return ComputerFamily.Command;
    }

    protected TileComputer createTile(ComputerFamily family) {
        return new TileCommandComputer();
    }

    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack itemstack) {
        int dir = DirectionUtil.fromEntityRot(player);
        this.setDirection(world, x, y, z, dir);
    }

    @Override
    protected IIcon getItemTexture(ComputerFamily family, int side) {
        return TileCommandComputer.getItemTexture(side, family);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister iconRegister) {
        super.registerBlockIcons(iconRegister);
        TileCommandComputer.registerIcons(iconRegister);
    }
}
