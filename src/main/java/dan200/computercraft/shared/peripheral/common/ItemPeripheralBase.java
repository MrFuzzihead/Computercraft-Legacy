package dan200.computercraft.shared.peripheral.common;

import dan200.computercraft.shared.peripheral.PeripheralType;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public abstract class ItemPeripheralBase extends ItemBlock implements IPeripheralItem {
   protected ItemPeripheralBase(Block block) {
      super(block);
      this.setMaxStackSize(64);
      this.setHasSubtypes(true);
   }

   public abstract PeripheralType getPeripheralType(int var1);

   public final int getMetadata(int damage) {
      return damage;
   }

   public boolean func_150936_a(World world, int x, int y, int z, int side, EntityPlayer player, ItemStack stack) {
      PeripheralType type = this.getPeripheralType(stack);
      switch (type) {
         case WirelessModem:
         case WiredModem:
            return world.isSideSolid(x, y, z, ForgeDirection.getOrientation(side));
         case Cable:
            return true;
         default:
            return super.func_150936_a(world, x, y, z, side, player, stack);
      }
   }

   public String getUnlocalizedName(ItemStack stack) {
      PeripheralType type = this.getPeripheralType(stack);
      switch (type) {
         case WirelessModem:
            return "tile.computercraft:wireless_modem";
         case WiredModem:
         case WiredModemWithCable:
            return "tile.computercraft:wired_modem";
         case Cable:
            return "tile.computercraft:cable";
         case DiskDrive:
         default:
            return "tile.computercraft:drive";
         case Printer:
            return "tile.computercraft:printer";
         case Monitor:
            return "tile.computercraft:monitor";
         case AdvancedMonitor:
            return "tile.computercraft:advanced_monitor";
      }
   }

   @Override
   public final PeripheralType getPeripheralType(ItemStack stack) {
      return this.getPeripheralType(stack.getItemDamage());
   }
}
