package dan200.computercraft.shared.peripheral.common;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.modem.TileCable;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

public final class BlockCable extends BlockPeripheralBase {
   public static boolean renderAsModem = false;

   public static boolean isCable(IBlockAccess world, int x, int y, int z) {
      Block block = world.getBlock(x, y, z);
      if (block == ComputerCraft.Blocks.cable) {
         switch (ComputerCraft.Blocks.cable.getPeripheralType(world, x, y, z)) {
            case Cable:
            case WiredModemWithCable:
               return true;
         }
      }

      return false;
   }

   public BlockCable() {
      this.setHardness(1.5F);
      this.setBlockName("computercraft:cable");
      this.setCreativeTab(ComputerCraft.mainCreativeTab);
   }

   public boolean func_149646_a(IBlockAccess world, int x, int y, int z, int side) {
      return true;
   }

   @Override
   public int getDefaultMetadata(PeripheralType type, int placedSide) {
      switch (type) {
         case Cable:
            return 13;
         case WiredModemWithCable:
            int dir = Facing.oppositeSide[placedSide];
            return dir + 6;
         case WiredModem:
         default:
            return Facing.oppositeSide[placedSide];
      }
   }

   @Override
   public PeripheralType getPeripheralType(int metadata) {
      if (metadata < 6) {
         return PeripheralType.WiredModem;
      } else if (metadata < 12) {
         return PeripheralType.WiredModemWithCable;
      } else {
         return metadata == 13 ? PeripheralType.Cable : PeripheralType.WiredModem;
      }
   }

   @Override
   public TilePeripheralBase createTile(PeripheralType type) {
      return new TileCable();
   }

   @Override
   public IIcon getItemTexture(PeripheralType type, int side) {
      switch (type) {
         case Cable:
            return TileCable.getCableItemTexture(side);
         case WiredModem:
         default:
            return TileCable.getModemItemTexture(side, false);
      }
   }

   @SideOnly(Side.CLIENT)
   @Override
   public void func_149651_a(IIconRegister iconRegister) {
      super.func_149651_a(iconRegister);
      TileCable.registerIcons(iconRegister);
   }
}
