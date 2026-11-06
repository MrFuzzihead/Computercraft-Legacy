package dan200.computercraft.shared.peripheral.modem;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.common.BlockGeneric;
import dan200.computercraft.shared.common.TileGeneric;

/**
 * The full-cube wired modem.
 *
 * <p>
 * Unlike the wall-mounted wired modem on {@link dan200.computercraft.shared.peripheral.common.BlockCable}, this block
 * fills a whole block space and exposes a modem on every face. It is also a peripheral hub: right-clicking it attaches
 * every adjacent peripheral on all six sides to the wired network.
 * </p>
 */
public class BlockWiredModemFull extends BlockGeneric {

    public BlockWiredModemFull() {
        super(Material.rock);
        this.setHardness(1.5F);
        this.setBlockName("computercraft:wired_modem_full");
        this.setCreativeTab(ComputerCraft.mainCreativeTab);
    }

    @Override
    public boolean isOpaqueCube() {
        return true;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return true;
    }

    @Override
    protected int getDefaultMetadata(int damage, int placedSide) {
        return 0;
    }

    @Override
    protected TileGeneric createTile(int metadata) {
        return new TileWiredModemFull();
    }

    @Override
    protected IIcon getItemTexture(int damage, int side) {
        return TileCable.getModemFaceTexture(0);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister iconRegister) {
        super.registerBlockIcons(iconRegister);
        TileCable.registerIcons(iconRegister);
    }
}
