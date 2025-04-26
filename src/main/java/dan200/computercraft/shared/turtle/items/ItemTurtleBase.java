package dan200.computercraft.shared.turtle.items;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.items.ItemComputerBase;
import dan200.computercraft.shared.turtle.blocks.ITurtleTile;
import dan200.computercraft.api.core.TurtleBrain;
import dan200.computercraft.shared.util.Colour;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

public abstract class ItemTurtleBase extends ItemComputerBase implements ITurtleItem {
   protected ItemTurtleBase(Block block) {
      super(block);
      this.setMaxStackSize(64);
      this.setHasSubtypes(true);
   }

   public abstract ItemStack create(
      int var1, String var2, Colour var3, ITurtleUpgrade var4, ITurtleUpgrade var5, int var6, ResourceLocation var7, ResourceLocation var8
   );

   public void getSubItems(Item itemID, CreativeTabs tabs, List list) {
      List<ItemStack> all = new ArrayList<>();
      ComputerCraft.addAllUpgradedTurtles(all);

      for (ItemStack stack : all) {
         if (stack.getItem() == this) {
            list.add(stack);
         }
      }
   }

   public boolean placeBlockAt(
      ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int metadata
   ) {
      if (super.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, metadata)) {
         TileEntity tile = world.getTileEntity(x, y, z);
         if (tile != null && tile instanceof ITurtleTile) {
            ITurtleTile turtle = (ITurtleTile)tile;
            this.setupTurtleAfterPlacement(stack, turtle);
         }

         return true;
      } else {
         return false;
      }
   }

   public void setupTurtleAfterPlacement(ItemStack stack, ITurtleTile turtle) {
      int id = this.getComputerID(stack);
      if (id >= 0) {
         turtle.setComputerID(id);
      }

      String label = this.getLabel(stack);
      if (label != null) {
         turtle.setLabel(label);
      }

      for (TurtleSide side : TurtleSide.values()) {
         turtle.getAccess().setUpgrade(side, this.getUpgrade(stack, side));
      }

      int fuelLevel = this.getFuelLevel(stack);
      turtle.getAccess().setFuelLevel(fuelLevel);
      Colour colour = this.getColour(stack);
      if (colour != null) {
         turtle.getAccess().setDyeColour(colour.ordinal());
      }

      ResourceLocation overlay = this.getOverlay(stack);
      ResourceLocation hatOverlay = this.getHatOverlay(stack);
      if (overlay != null || hatOverlay != null) {
         ((TurtleBrain)turtle.getAccess()).setOverlay(overlay, hatOverlay);
      }
   }

   public String getUnlocalizedName(ItemStack stack) {
      ComputerFamily family = this.getFamily(stack);
      switch (family) {
         case Normal:
         default:
            return "tile.computercraft:turtle";
         case Advanced:
            return "tile.computercraft:advanced_turtle";
         case Beginners:
            return "tile.computercraftedu:beginner_turtle";
      }
   }

   public String getItemStackDisplayName(ItemStack stack) {
      String baseString = this.getUnlocalizedName(stack);
      ITurtleUpgrade left = this.getUpgrade(stack, TurtleSide.Left);
      ITurtleUpgrade right = this.getUpgrade(stack, TurtleSide.Right);
      if (left != null && right != null) {
         return StatCollector.translateToLocalFormatted(
            baseString + ".upgraded_twice.name",
            new Object[]{StatCollector.translateToLocal(right.getUnlocalisedAdjective()), StatCollector.translateToLocal(left.getUnlocalisedAdjective())}
         );
      } else if (left != null) {
         return StatCollector.translateToLocalFormatted(baseString + ".upgraded.name", new Object[]{StatCollector.translateToLocal(left.getUnlocalisedAdjective())});
      } else {
         return right != null
            ? StatCollector.translateToLocalFormatted(baseString + ".upgraded.name", new Object[]{StatCollector.translateToLocal(right.getUnlocalisedAdjective())})
            : StatCollector.translateToLocal(baseString + ".name");
      }
   }

   @Override
   public abstract ITurtleUpgrade getUpgrade(ItemStack var1, TurtleSide var2);

   @Override
   public abstract Colour getColour(ItemStack var1);

   @Override
   public abstract ResourceLocation getOverlay(ItemStack var1);

   @Override
   public abstract int getFuelLevel(ItemStack var1);
}
