package dan200.computercraft.shared.computer.blocks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.util.DirectionUtil;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

public class BlockComputer extends BlockComputerBase {
   public static BlockComputer createComputerBlock() {
      return new BlockComputer();
   }

   public BlockComputer() {
      super(Material.rock);
      this.setHardness(2.0F);
      this.setBlockName("computercraft:computer");
      this.setCreativeTab(ComputerCraft.mainCreativeTab);
   }

   @Override
   protected int getDefaultMetadata(ComputerFamily family, int placedSide) {
      if (placedSide < 2) {
         placedSide = 2;
      }

      switch (family) {
         case Normal:
         default:
            return placedSide;
         case Advanced:
            return placedSide + 8;
      }
   }

   @Override
   public ComputerFamily getFamily(int metadata) {
      int advancedBit = (metadata & 8) >> 3;
      switch (advancedBit) {
         case 0:
         default:
            return ComputerFamily.Normal;
         case 1:
            return ComputerFamily.Advanced;
      }
   }

   protected TileComputer createTile(ComputerFamily family) {
      return new TileComputer();
   }

   public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack itemstack) {
      int dir = DirectionUtil.fromEntityRot(player);
      this.setDirection(world, x, y, z, dir);
   }

   @Override
   protected IIcon getItemTexture(ComputerFamily family, int side) {
      return TileComputer.getItemTexture(side, family);
   }

   @SideOnly(Side.CLIENT)
   @Override
   public void func_149651_a(IIconRegister iconRegister) {
      super.func_149651_a(iconRegister);
      TileComputer.registerIcons(iconRegister);
   }
}
