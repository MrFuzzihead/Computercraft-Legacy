package dan200.computercraft.shared.peripheral.modem;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.BlockPeripheralBase;
import dan200.computercraft.shared.peripheral.common.TilePeripheralBase;

/**
 * A full-cube variant of the Wired Modem. Unlike the face-attached
 * {@link dan200.computercraft.shared.peripheral.common.BlockCable WiredModem},
 * this block fills an entire block space and can therefore be placed adjacent to
 * non-full-block tile entities (e.g. chests) to expose them on a wired network.
 *
 * <p>All six faces display the {@code wiredModemFace*} textures. The block
 * participates in cable networks exactly like a {@link TileCable WiredModemWithCable}
 * node. A shapeless recipe converts it to/from the regular Wired Modem.</p>
 */
public class BlockWiredModemFull extends BlockPeripheralBase {

    public BlockWiredModemFull() {
        this.setHardness(1.5F);
        this.setBlockName("computercraft:wired_modem_full");
        this.setCreativeTab(ComputerCraft.mainCreativeTab);
        // Use the standard cube renderer (0) instead of the custom peripheral renderer (-1).
        this.blockRenderID = 0;
    }

    // -------------------------------------------------------------------------
    // Full-cube overrides (BlockPeripheralBase declares these non-final so we
    // can override them here)
    // -------------------------------------------------------------------------

    @Override
    public boolean isOpaqueCube() {
        return true;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return true;
    }

    // -------------------------------------------------------------------------
    // BlockPeripheralBase abstract methods
    // -------------------------------------------------------------------------

    @Override
    protected int getDefaultMetadata(PeripheralType type, int placedSide) {
        // Full block; no orientation metadata needed.
        return 0;
    }

    @Override
    public PeripheralType getPeripheralType(int metadata) {
        // Behaves as WiredModemWithCable so TileCable's network/peripheral logic works.
        return PeripheralType.WiredModemWithCable;
    }

    @Override
    protected TilePeripheralBase createTile(PeripheralType type) {
        return new TileWiredModemFull();
    }

    @Override
    protected IIcon getItemTexture(PeripheralType type, int side) {
        return TileCable.getModemFaceIcon(0);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister iconRegister) {
        super.registerBlockIcons(iconRegister);
        TileCable.registerIcons(iconRegister);
    }
}

