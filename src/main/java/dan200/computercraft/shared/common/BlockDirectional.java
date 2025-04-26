package dan200.computercraft.shared.common;

import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public abstract class BlockDirectional extends BlockGeneric {
   protected BlockDirectional(Material material) {
      super(material);
   }

   public int getDirection(IBlockAccess world, int x, int y, int z) {
      TileEntity tile = world.getTileEntity(x, y, z);
      if (tile != null && tile instanceof IDirectionalTile) {
         IDirectionalTile directional = (IDirectionalTile)tile;
         return directional.getDirection();
      } else {
         return 2;
      }
   }

   public void setDirection(World world, int x, int y, int z, int dir) {
      TileEntity tile = world.getTileEntity(x, y, z);
      if (tile != null && tile instanceof IDirectionalTile) {
         IDirectionalTile directional = (IDirectionalTile)tile;
         directional.setDirection(dir);
      }
   }
}
