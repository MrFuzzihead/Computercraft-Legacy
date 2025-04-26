package dan200.computercraft.shared.peripheral.common;

import dan200.computercraft.shared.common.BlockDirectional;
import dan200.computercraft.shared.common.TileGeneric;
import dan200.computercraft.shared.peripheral.PeripheralType;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public abstract class BlockPeripheralBase extends BlockDirectional {
   public int blockRenderID = -1;

   public BlockPeripheralBase() {
      super(Material.rock);
   }

   protected abstract int getDefaultMetadata(PeripheralType var1, int var2);

   protected abstract PeripheralType getPeripheralType(int var1);

   protected abstract TilePeripheralBase createTile(PeripheralType var1);

   protected abstract IIcon getItemTexture(PeripheralType var1, int var2);

   public final int func_149645_b() {
      return this.blockRenderID;
   }

   public final boolean func_149686_d() {
      return false;
   }

   public final boolean func_149662_c() {
      return false;
   }

   public final boolean func_149707_d(World world, int i, int j, int k, int side) {
      return true;
   }

   @Override
   protected final int getDefaultMetadata(int damage, int placedSide) {
      ItemPeripheralBase item = (ItemPeripheralBase)Item.getItemFromBlock(this);
      return this.getDefaultMetadata(item.getPeripheralType(damage), placedSide);
   }

   @Override
   public final TileGeneric createTile(int metadata) {
      return this.createTile(this.getPeripheralType(metadata));
   }

   public final PeripheralType getPeripheralType(IBlockAccess world, int x, int y, int z) {
      return this.getPeripheralType(world.getBlockMetadata(x, y, z));
   }

   @Override
   public final IIcon getItemTexture(int damage, int side) {
      ItemPeripheralBase item = (ItemPeripheralBase)Item.getItemFromBlock(this);
      return this.getItemTexture(item.getPeripheralType(damage), side);
   }
}
