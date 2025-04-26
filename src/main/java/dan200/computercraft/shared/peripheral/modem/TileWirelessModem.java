package dan200.computercraft.shared.peripheral.modem;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class TileWirelessModem extends TileModemBase {
   private static final IIcon[] s_icons = new IIcon[4];

   @SideOnly(Side.CLIENT)
   public static void registerIcons(IIconRegister iconRegister) {
      s_icons[0] = iconRegister.registerIcon("computercraft:wirelessModemFace");
      s_icons[1] = iconRegister.registerIcon("computercraft:wirelessModemSide");
      s_icons[2] = iconRegister.registerIcon("computercraft:wirelessModemFaceOn");
      s_icons[3] = iconRegister.registerIcon("computercraft:wirelessModemSideOn");
   }

   public static IIcon getItemTexture(int side, boolean on) {
      return side != 2 && side != 5 ? s_icons[on ? 2 : 0] : s_icons[on ? 3 : 1];
   }

   public TileWirelessModem() {
      super(s_icons);
   }

   @Override
   public int getDirection() {
      int metadata = this.getMetadata();
      return metadata < 2 ? metadata : metadata - 4;
   }

   @Override
   public void setDirection(int dir) {
      if (dir < 2) {
         this.setMetadata(dir);
      } else {
         this.setMetadata(dir + 4);
      }
   }

   @Override
   protected ModemPeripheral createPeripheral() {
      return new TileWirelessModem.Peripheral(this);
   }

   private static class Peripheral extends WirelessModemPeripheral {
      private TileModemBase m_entity;

      public Peripheral(TileModemBase entity) {
         this.m_entity = entity;
      }

      @Override
      protected World getWorld() {
         return this.m_entity.getWorldObj();
      }

      @Override
      protected Vec3 getPosition() {
         int direction = this.m_entity.getDirection();
         int x = this.m_entity.xCoord + Facing.offsetsXForSide[direction];
         int y = this.m_entity.yCoord + Facing.offsetsYForSide[direction];
         int z = this.m_entity.zCoord + Facing.offsetsZForSide[direction];
         return Vec3.createVectorHelper(x, y, z);
      }

      @Override
      public boolean equals(IPeripheral other) {
         if (other instanceof TileWirelessModem.Peripheral) {
            TileWirelessModem.Peripheral otherModem = (TileWirelessModem.Peripheral)other;
            return otherModem.m_entity == this.m_entity;
         } else {
            return false;
         }
      }
   }
}
