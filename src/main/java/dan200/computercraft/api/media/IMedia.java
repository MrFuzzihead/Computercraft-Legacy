package dan200.computercraft.api.media;

import dan200.computercraft.api.filesystem.IMount;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public interface IMedia {
   String getLabel(ItemStack var1);

   boolean setLabel(ItemStack var1, String var2);

   String getAudioTitle(ItemStack var1);

   String getAudioRecordName(ItemStack var1);

   IMount createDataMount(ItemStack var1, World var2);
}
