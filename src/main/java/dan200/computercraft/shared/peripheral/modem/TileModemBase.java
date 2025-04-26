package dan200.computercraft.shared.peripheral.modem;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.common.BlockGeneric;
import dan200.computercraft.shared.peripheral.common.TilePeripheralBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;

public abstract class TileModemBase extends TilePeripheralBase {
   private final IIcon[] m_icons;
   protected ModemPeripheral m_modem;

   protected TileModemBase(IIcon[] icons) {
      super(icons);
      this.m_icons = icons;
      this.m_modem = this.createPeripheral();
   }

   protected abstract ModemPeripheral createPeripheral();

   @Override
   public synchronized void destroy() {
      if (this.m_modem != null) {
         this.m_modem.destroy();
         this.m_modem = null;
      }
   }

   @Override
   public boolean isSolidOnSide(int side) {
      return false;
   }

   @Override
   public void onNeighbourChange() {
      int dir = this.getDirection();
      if (!this.getWorldObj()
         .isSideSolid(
            this.xCoord + Facing.offsetsXForSide[dir],
            this.yCoord + Facing.offsetsYForSide[dir],
            this.zCoord + Facing.offsetsZForSide[dir],
            ForgeDirection.getOrientation(Facing.oppositeSide[dir])
         )) {
         ((BlockGeneric)this.getBlockType()).dropAllItems(this.getWorldObj(), this.xCoord, this.yCoord, this.zCoord, 0, false, false);
         this.getWorldObj().setBlockToAir(this.xCoord, this.yCoord, this.zCoord);
      }
   }

   @Override
   public AxisAlignedBB getBounds() {
      switch (this.getDirection()) {
         case 0:
         default:
            return AxisAlignedBB.getBoundingBox(0.125, 0.0, 0.125, 0.875, 0.1875, 0.875);
         case 1:
            return AxisAlignedBB.getBoundingBox(0.125, 0.8125, 0.125, 0.875, 1.0, 0.875);
         case 2:
            return AxisAlignedBB.getBoundingBox(0.125, 0.125, 0.0, 0.875, 0.875, 0.1875);
         case 3:
            return AxisAlignedBB.getBoundingBox(0.125, 0.125, 0.8125, 0.875, 0.875, 1.0);
         case 4:
            return AxisAlignedBB.getBoundingBox(0.0, 0.125, 0.125, 0.1875, 0.875, 0.875);
         case 5:
            return AxisAlignedBB.getBoundingBox(0.8125, 0.125, 0.125, 1.0, 0.875, 0.875);
      }
   }

   @Override
   public void func_145845_h() {
      super.func_145845_h();
      if (!this.getWorldObj().isRemote && this.m_modem.pollChanged()) {
         this.updateAnim();
      }
   }

   protected void updateAnim() {
      if (this.m_modem.isActive()) {
         this.setAnim(1);
      } else {
         this.setAnim(0);
      }
   }

   @Override
   public IIcon getTexture(int side) {
      IIcon[] icons = this.m_icons;
      int tex = 2 * this.getAnim();
      int dir = this.getDirection();
      if (dir == 0 || dir == 1 || side == Facing.oppositeSide[dir]) {
         return icons[tex];
      } else {
         return side != 2 && side != 5 ? icons[tex] : icons[tex + 1];
      }
   }

   @Override
   public final void readDescription(NBTTagCompound nbttagcompound) {
      super.readDescription(nbttagcompound);
      this.updateBlock();
   }

   @Override
   public IPeripheral getPeripheral(int side) {
      return side == this.getDirection() ? this.m_modem : null;
   }

   protected boolean isAttached() {
      return this.m_modem != null && this.m_modem.getComputer() != null;
   }
}
