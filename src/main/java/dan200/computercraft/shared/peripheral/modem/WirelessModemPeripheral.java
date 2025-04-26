package dan200.computercraft.shared.peripheral.modem;

import dan200.computercraft.ComputerCraft;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public abstract class WirelessModemPeripheral extends ModemPeripheral {
   protected abstract World getWorld();

   @Override
   protected double getTransmitRange() {
      World world = this.getWorld();
      if (world != null) {
         Vec3 position = this.getPosition();
         double minRange = ComputerCraft.modem_range;
         double maxRange = ComputerCraft.modem_highAltitudeRange;
         if (world.isRaining() && world.isThundering()) {
            minRange = ComputerCraft.modem_rangeDuringStorm;
            maxRange = ComputerCraft.modem_highAltitudeRangeDuringStorm;
         }

         return position.yCoord > 96.0 && maxRange > minRange
            ? minRange + (position.yCoord - 96.0) * ((maxRange - minRange) / (world.getHeight() - 1 - 96.0))
            : minRange;
      } else {
         return 0.0;
      }
   }

   @Override
   protected INetwork getNetwork() {
      World world = this.getWorld();
      return world != null ? WirelessNetwork.get(world) : null;
   }
}
