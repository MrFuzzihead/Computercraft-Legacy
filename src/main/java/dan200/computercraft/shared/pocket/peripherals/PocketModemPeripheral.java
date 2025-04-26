package dan200.computercraft.shared.pocket.peripherals;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.peripheral.modem.WirelessModemPeripheral;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class PocketModemPeripheral extends WirelessModemPeripheral {
   private World m_world = null;
   private Vec3 m_position = Vec3.createVectorHelper(0.0, 0.0, 0.0);

   public void setLocation(World world, double x, double y, double z) {
      this.m_position.xCoord = x;
      this.m_position.yCoord = y;
      this.m_position.zCoord = z;
      if (this.m_world != world) {
         this.m_world = world;
         this.switchNetwork();
      }
   }

   @Override
   protected World getWorld() {
      return this.m_world;
   }

   @Override
   protected Vec3 getPosition() {
      return this.m_world != null ? this.m_position : null;
   }

   @Override
   public boolean equals(IPeripheral other) {
      return other instanceof PocketModemPeripheral;
   }
}
