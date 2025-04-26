package dan200.computercraft.api.core;

import com.mojang.authlib.GameProfile;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.shared.util.InventoryUtil;
import dan200.computercraft.shared.util.WorldUtil;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Facing;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

public class TurtlePlayer extends FakePlayer {
   private static final GameProfile s_profile = new GameProfile(UUID.fromString("0d0c4ca0-4ff1-11e4-916c-0800200c9a66"), "ComputerCraft");

   public TurtlePlayer(WorldServer world) {
      super(world, s_profile);
   }

   public void loadInventory(ItemStack currentStack) {
      this.inventory.currentItem = 0;
      this.inventory.setInventorySlotContents(0, currentStack);
   }

   public ItemStack unloadInventory(ITurtleAccess turtle) {
      ItemStack results = this.inventory.getStackInSlot(0);
      this.inventory.setInventorySlotContents(0, null);
      ChunkCoordinates dropPosition = turtle.getPosition();
      int dropDirection = Facing.oppositeSide[turtle.getDirection()];

      for (int i = 0; i < this.inventory.getSizeInventory(); i++) {
         ItemStack stack = this.inventory.getStackInSlot(i);
         if (stack != null) {
            ItemStack remainder = InventoryUtil.storeItems(stack, turtle.getInventory(), 0, turtle.getInventory().getSizeInventory(), turtle.getSelectedSlot());
            if (remainder != null) {
               WorldUtil.dropItemStack(
                  remainder, turtle.getWorld(), dropPosition.posX, dropPosition.posY, dropPosition.posZ, dropDirection
               );
            }

            this.inventory.setInventorySlotContents(i, null);
         }
      }

      this.inventory.markDirty();
      return results;
   }

   public float getEyeHeight() {
      return 0.0F;
   }

   public float getDefaultEyeHeight() {
      return 0.0F;
   }

   public void func_146100_a(TileEntity entity) {
   }

   public void dismountEntity(Entity entity) {
   }

   public void func_110145_l(Entity entity) {
   }
}
