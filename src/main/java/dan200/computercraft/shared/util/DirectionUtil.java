package dan200.computercraft.shared.util;

import dan200.computercraft.shared.common.IDirectionalTile;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;

public class DirectionUtil {
   private static int[] s_rotateRight = new int[]{0, 1, 5, 4, 2, 3};
   private static int[] s_rotateLeft = new int[]{0, 1, 4, 5, 3, 2};
   private static int[] s_rotate180 = new int[]{0, 1, 3, 2, 5, 4};

   public static int rotateRight(int dir) {
      return s_rotateRight[dir];
   }

   public static int rotateLeft(int dir) {
      return s_rotateLeft[dir];
   }

   public static int rotate180(int dir) {
      return s_rotate180[dir];
   }

   public static int toLocal(IDirectionalTile directional, int dir) {
      int front = directional.getDirection();
      if (front < 2) {
         return dir;
      } else {
         int back = rotate180(front);
         int left = rotateLeft(front);
         int right = rotateRight(front);
         if (dir == front) {
            return 3;
         } else if (dir == back) {
            return 2;
         } else if (dir == left) {
            return 5;
         } else {
            return dir == right ? 4 : dir;
         }
      }
   }

   public static int fromEntityRot(EntityLivingBase player) {
      int rot = MathHelper.floor_float(player.rotationYaw / 90.0F + 0.5F) & 3;
      switch (rot) {
         case 0:
            return 2;
         case 1:
            return 5;
         case 2:
            return 3;
         case 3:
            return 4;
         default:
            return 2;
      }
   }

   public static float toYawAngle(int dir) {
      switch (dir) {
         case 2:
            return 180.0F;
         case 3:
            return 0.0F;
         case 4:
            return 90.0F;
         case 5:
            return 270.0F;
         default:
            return 0.0F;
      }
   }

   public static float toPitchAngle(int dir) {
      switch (dir) {
         case 0:
            return 90.0F;
         case 1:
            return -90.0F;
         default:
            return 0.0F;
      }
   }
}
