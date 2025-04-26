package dan200.computercraft.api.permissions;

import net.minecraft.world.World;

public interface ITurtlePermissionProvider {
   boolean isBlockEnterable(World var1, int var2, int var3, int var4);

   boolean isBlockEditable(World var1, int var2, int var3, int var4);
}
