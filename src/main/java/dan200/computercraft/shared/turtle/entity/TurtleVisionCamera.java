package dan200.computercraft.shared.turtle.entity;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.core.TurtleBrain;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class TurtleVisionCamera extends EntityLivingBase {
   private ITurtleAccess m_turtle;
   public int thirdPersonView = 0;

   public TurtleVisionCamera(World world, ITurtleAccess turtle) {
      super(world);
      this.field_70129_M = 1.62F;
      this.m_turtle = turtle;
      this.applyPos();
   }

   public ITurtleAccess getTurtle() {
      return this.m_turtle;
   }

   public float getEyeHeight() {
      return 0.0F;
   }

   public void onUpdate() {
      this.m_turtle = ((TurtleBrain)this.m_turtle).getFutureSelf();
      this.applyPos();
   }

   private void applyPos() {
      Vec3 prevPos = this.m_turtle.getVisualPosition(0.0F);
      this.lastTickPosX = this.prevPosX = prevPos.xCoord;
      this.lastTickPosY = this.prevPosY = prevPos.yCoord;
      this.lastTickPosZ = this.prevPosZ = prevPos.zCoord;
      this.prevRotationPitch = 0.0F;
      this.prevRotationYaw = this.m_turtle.getVisualYaw(0.0F);
      this.prevCameraPitch = 0.0F;
      Vec3 pos = this.m_turtle.getVisualPosition(1.0F);
      this.posX = pos.xCoord;
      this.posY = pos.yCoord;
      this.posZ = pos.zCoord;
      this.rotationPitch = 0.0F;
      this.rotationYaw = this.m_turtle.getVisualYaw(1.0F);
      this.cameraPitch = 0.0F;
      float yawDifference = this.rotationYaw - this.prevRotationYaw;
      if (yawDifference > 180.0F) {
         this.prevRotationYaw += 360.0F;
      } else if (yawDifference < -180.0F) {
         this.prevRotationYaw -= 360.0F;
      }
   }

   public ItemStack getHeldItem() {
      return null;
   }

   public ItemStack getEquipmentInSlot(int slot) {
      return null;
   }

   public void setCurrentItemOrArmor(int slot, ItemStack stack) {
   }

   public ItemStack[] getLastActiveItems() {
      return new ItemStack[0];
   }
}
