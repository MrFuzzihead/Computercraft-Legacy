package dan200.computercraft.shared.common;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public abstract class BlockGeneric extends Block implements ITileEntityProvider {
   private IIcon m_transparentIcon;
   private static final int[] s_canConnectRedstoneSideToSide = new int[]{1, 2, 5, 3, 4};

   protected BlockGeneric(Material material) {
      super(material);
      this.isBlockContainer = true;
   }

   protected abstract int getDefaultMetadata(int var1, int var2);

   protected abstract TileGeneric createTile(int var1);

   protected abstract IIcon getItemTexture(int var1, int var2);

   public final void dropBlockAsItemWithChance(World world, int i, int j, int k, int l, float chance, int fortune) {
   }

   public final ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune) {
      ArrayList<ItemStack> drops = new ArrayList<>(1);
      TileEntity tile = world.getTileEntity(x, y, z);
      if (tile != null && tile instanceof TileGeneric) {
         TileGeneric generic = (TileGeneric)tile;
         generic.getDroppedItems(drops, fortune, false, false);
      }

      return drops;
   }

   public final int func_149660_a(World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int damage) {
      return this.getDefaultMetadata(damage, side);
   }

   public final boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
      if (!world.isRemote) {
         int fortune = EnchantmentHelper.getFortuneModifier(player);
         boolean creative = player.capabilities.isCreativeMode;
         boolean silkTouch = EnchantmentHelper.getSilkTouchModifier(player);
         this.dropAllItems(world, x, y, z, fortune, creative, silkTouch);
      }

      return super.removedByPlayer(world, player, x, y, z, willHarvest);
   }

   public final void dropAllItems(World world, int x, int y, int z, int fortune, boolean creative, boolean silkTouch) {
      List<ItemStack> drops = new ArrayList<>(1);
      TileEntity tile = world.getTileEntity(x, y, z);
      if (tile != null && tile instanceof TileGeneric) {
         TileGeneric generic = (TileGeneric)tile;
         generic.getDroppedItems(drops, fortune, creative, silkTouch);
      }

      if (drops.size() > 0) {
         for (ItemStack item : drops) {
            this.dropItem(world, x, y, z, item);
         }
      }
   }

   public final void dropItem(World world, int x, int y, int z, ItemStack item) {
      this.dropBlockAsItem(world, x, y, z, item);
   }

   public final void breakBlock(World world, int x, int y, int z, Block newBlock, int newMeta) {
      TileEntity tile = world.getTileEntity(x, y, z);
      if (tile != null && tile instanceof TileGeneric) {
         TileGeneric generic = (TileGeneric)tile;
         generic.destroy();
      }

      super.breakBlock(world, x, y, z, newBlock, newMeta);
      world.removeTileEntity(x, y, z);
   }

   public final ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z, EntityPlayer player) {
      return this.getPickBlock(target, world, x, y, z);
   }

   public final ItemStack getPickBlock(MovingObjectPosition target, World world, int x, int y, int z) {
      TileEntity tile = world.getTileEntity(x, y, z);
      if (tile != null && tile instanceof TileGeneric) {
         TileGeneric generic = (TileGeneric)tile;
         return generic.getPickedItem();
      } else {
         return null;
      }
   }

   protected final ItemStack func_149644_j(int metadata) {
      return null;
   }

   public final boolean func_149727_a(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
      TileEntity tile = world.getTileEntity(x, y, z);
      if (tile != null && tile instanceof TileGeneric) {
         TileGeneric generic = (TileGeneric)tile;
         return generic.onActivate(player, side, hitX, hitY, hitZ);
      } else {
         return false;
      }
   }

   public final void neighborChanged(World world, int x, int y, int z, Block neighbour) {
      TileEntity tile = world.getTileEntity(x, y, z);
      if (tile != null && tile instanceof TileGeneric) {
         TileGeneric generic = (TileGeneric)tile;
         generic.onNeighbourChange();
      }
   }

   public final boolean isSideSolid(IBlockAccess world, int x, int y, int z, ForgeDirection side) {
      TileEntity tile = world.getTileEntity(x, y, z);
      if (tile != null && tile instanceof TileGeneric) {
         TileGeneric generic = (TileGeneric)tile;
         return generic.isSolidOnSide(side.ordinal());
      } else {
         return false;
      }
   }

   public final boolean canBeReplacedByLeaves(IBlockAccess world, int x, int y, int z) {
      return false;
   }

   public float getExplosionResistance(Entity entity, World world, int x, int y, int z, double explosionX, double explosionY, double explosionZ) {
      TileEntity tile = world.getTileEntity(x, y, z);
      if (tile != null && tile instanceof TileGeneric) {
         TileGeneric generic = (TileGeneric)tile;
         if (generic.isImmuneToExplosion(entity)) {
            return 2000.0F;
         }
      }

      return super.getExplosionResistance(entity);
   }

   private void setBlockBounds(AxisAlignedBB bounds) {
      this.setBlockBounds(
         (float)bounds.minX,
         (float)bounds.minY,
         (float)bounds.minZ,
         (float)bounds.maxX,
         (float)bounds.maxY,
         (float)bounds.maxZ
      );
   }

   public final void getCollisionBoundingBoxFromPool(IBlockAccess world, int x, int y, int z) {
      TileEntity tile = world.getTileEntity(x, y, z);
      if (tile != null && tile instanceof TileGeneric) {
         TileGeneric generic = (TileGeneric)tile;
         this.setBlockBounds(generic.getBounds());
      }
   }

   public final AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
      this.getCollisionBoundingBoxFromPool(world, x, y, z);
      return super.getSelectedBoundingBoxFromPool(world, x, y, z);
   }

   public final void func_149743_a(World world, int x, int y, int z, AxisAlignedBB bigBox, List list, Entity entity) {
      TileEntity tile = world.getTileEntity(x, y, z);
      if (tile != null && tile instanceof TileGeneric) {
         TileGeneric generic = (TileGeneric)tile;
         List<AxisAlignedBB> collision = new ArrayList<>(1);
         generic.getCollisionBounds(collision);
         if (collision.size() > 0) {
            for (AxisAlignedBB localBounds : collision) {
               this.setBlockBounds(localBounds);
               AxisAlignedBB bounds = super.getSelectedBoundingBoxFromPool(world, x, y, z);
               if (bounds != null && bigBox.intersectsWith(bounds)) {
                  list.add(bounds);
               }
            }
         }
      }
   }

   public final boolean canProvidePower() {
      return true;
   }

   public final boolean canConnectRedstone(IBlockAccess world, int x, int y, int z, int weirdSide) {
      int side = s_canConnectRedstoneSideToSide[weirdSide + 1];
      TileEntity tile = world.getTileEntity(x, y, z);
      if (tile != null && tile instanceof TileGeneric) {
         TileGeneric generic = (TileGeneric)tile;
         return generic.getRedstoneConnectivity(side);
      } else {
         return false;
      }
   }

   public final int isProvidingStrongPower(IBlockAccess world, int x, int y, int z, int invertedSide) {
      TileEntity tile = world.getTileEntity(x, y, z);
      if (tile != null && tile instanceof TileGeneric) {
         TileGeneric generic = (TileGeneric)tile;
         return generic.getRedstoneOutput(Facing.oppositeSide[invertedSide]);
      } else {
         return 0;
      }
   }

   public final int isProvidingWeakPower(IBlockAccess world, int x, int y, int z, int invertedSide) {
      return this.isProvidingStrongPower(world, x, y, z, invertedSide);
   }

   public boolean getBundledRedstoneConnectivity(World world, int x, int y, int z, int side) {
      TileEntity tile = world.getTileEntity(x, y, z);
      if (tile != null && tile instanceof TileGeneric) {
         TileGeneric generic = (TileGeneric)tile;
         return generic.getBundledRedstoneConnectivity(side);
      } else {
         return false;
      }
   }

   public int getBundledRedstoneOutput(World world, int x, int y, int z, int side) {
      TileEntity tile = world.getTileEntity(x, y, z);
      if (tile != null && tile instanceof TileGeneric) {
         TileGeneric generic = (TileGeneric)tile;
         return generic.getBundledRedstoneOutput(side);
      } else {
         return 0;
      }
   }

   public final IIcon func_149673_e(IBlockAccess world, int x, int y, int z, int side) {
      TileEntity tile = world.getTileEntity(x, y, z);
      if (tile != null && tile instanceof TileGeneric) {
         TileGeneric generic = (TileGeneric)tile;
         return generic.getTexture(side);
      } else {
         return this.m_transparentIcon;
      }
   }

   public final IIcon func_149691_a(int side, int damage) {
      return this.getItemTexture(damage, side);
   }

   @SideOnly(Side.CLIENT)
   public void func_149651_a(IIconRegister iconRegister) {
      this.m_transparentIcon = iconRegister.registerIcon("computercraft:transparent");
   }

   public boolean func_149696_a(World world, int x, int y, int z, int eventID, int eventParameter) {
      if (world.isRemote) {
         TileEntity tile = world.getTileEntity(x, y, z);
         if (tile != null && tile instanceof TileGeneric) {
            TileGeneric generic = (TileGeneric)tile;
            generic.onBlockEvent(eventID, eventParameter);
         }
      }

      return true;
   }

   public final TileEntity createTileEntity(World world, int metadata) {
      return this.createTile(metadata);
   }

   public final TileEntity createNewTileEntity(World world, int metadata) {
      return this.createTile(metadata);
   }
}
