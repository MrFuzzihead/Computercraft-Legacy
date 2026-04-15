package dan200.computercraft.shared.peripheral.redstone;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.common.BlockDirectional;
import dan200.computercraft.shared.common.TileGeneric;
import dan200.computercraft.shared.util.DirectionUtil;

/**
 * The Redstone Relay block. Hosts a {@link TileRedstoneRelay} tile entity
 * and exposes the full redstone API surface as a peripheral accessible via
 * wired or wireless modems.
 */
public class BlockRedstoneRelay extends BlockDirectional {

    public BlockRedstoneRelay() {
        super(Material.rock);
        setHardness(2.0F);
        setBlockName("computercraft:redstone_relay");
        setCreativeTab(ComputerCraft.mainCreativeTab);
    }

    // =========================================================================
    // BlockGeneric abstract methods
    // =========================================================================

    @Override
    protected int getDefaultMetadata(int damage, int placedSide) {
        return Math.max(placedSide, 2);
    }

    @Override
    protected TileGeneric createTile(int metadata) {
        return new TileRedstoneRelay();
    }

    @Override
    protected IIcon getItemTexture(int damage, int side) {
        return TileRedstoneRelay.getItemTexture(side);
    }

    // =========================================================================
    // Icon registration
    // =========================================================================

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister iconRegister) {
        super.registerBlockIcons(iconRegister);
        TileRedstoneRelay.registerIcons(iconRegister);
    }

    // =========================================================================
    // Placement — set direction from player rotation
    // =========================================================================

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack itemstack) {
        int dir = DirectionUtil.fromEntityRot(player);
        setDirection(world, x, y, z, DirectionUtil.rotate180(dir));
    }
}
