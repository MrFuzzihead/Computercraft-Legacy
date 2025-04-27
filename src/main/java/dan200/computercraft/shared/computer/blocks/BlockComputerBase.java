package dan200.computercraft.shared.computer.blocks;

import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import dan200.computercraft.shared.common.BlockDirectional;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.items.ItemComputerBase;

public abstract class BlockComputerBase extends BlockDirectional {

    public int blockRenderID = -1;

    public BlockComputerBase(Material material) {
        super(material);
    }

    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        this.updateInput(world, x, y, z);
    }

    @Override
    public void setDirection(World world, int x, int y, int z, int dir) {
        super.setDirection(world, x, y, z, dir);
        this.updateInput(world, x, y, z);
    }

    protected abstract int getDefaultMetadata(ComputerFamily var1, int var2);

    protected abstract ComputerFamily getFamily(int var1);

    protected abstract TileComputerBase createTile(ComputerFamily var1);

    protected abstract IIcon getItemTexture(ComputerFamily var1, int var2);

    public final int getRenderType() {
        return this.blockRenderID;
    }

    public final boolean renderAsNormalBlock() {
        return false;
    }

    public final boolean isOpaqueCube() {
        return false;
    }

    @Override
    protected final int getDefaultMetadata(int damage, int placedSide) {
        ItemComputerBase item = (ItemComputerBase) Item.getItemFromBlock(this);
        return this.getDefaultMetadata(item.getFamily(damage), placedSide);
    }

    public final TileComputerBase createTile(int metadata) {
        return this.createTile(this.getFamily(metadata));
    }

    public final ComputerFamily getFamily(IBlockAccess world, int x, int y, int z) {
        return this.getFamily(world.getBlockMetadata(x, y, z));
    }

    @Override
    public final IIcon getItemTexture(int damage, int side) {
        ItemComputerBase item = (ItemComputerBase) Item.getItemFromBlock(this);
        return this.getItemTexture(item.getFamily(damage), side);
    }

    protected void updateInput(IBlockAccess world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile != null && tile instanceof TileComputerBase) {
            TileComputerBase computer = (TileComputerBase) tile;
            computer.updateInput();
        }
    }
}
