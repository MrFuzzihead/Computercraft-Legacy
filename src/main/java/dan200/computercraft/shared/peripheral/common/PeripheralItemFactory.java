package dan200.computercraft.shared.peripheral.common;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.peripheral.PeripheralType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class PeripheralItemFactory {
   public static ItemStack create(IPeripheralTile tile) {
      return create(tile.getPeripheralType(), tile.getLabel(), 1);
   }

   public static ItemStack create(PeripheralType type, String label, int quantity) {
      ItemPeripheral peripheral = (ItemPeripheral)Item.getItemFromBlock(ComputerCraft.Blocks.peripheral);
      ItemCable cable = (ItemCable)Item.getItemFromBlock(ComputerCraft.Blocks.cable);
      switch (type) {
         case DiskDrive:
         case Printer:
         case Monitor:
         case AdvancedMonitor:
         case WirelessModem:
            return peripheral.create(type, label, quantity);
         case WiredModem:
         case Cable:
            return cable.create(type, label, quantity);
         default:
            return null;
      }
   }
}
