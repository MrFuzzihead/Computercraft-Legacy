package dan200.computercraft.shared.media.items;

import net.minecraft.item.ItemRecord;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.media.IMedia;

public class RecordMedia implements IMedia {

    @Override
    public String getLabel(ItemStack stack) {
        return this.getAudioTitle(stack);
    }

    @Override
    public boolean setLabel(ItemStack stack, String label) {
        return false;
    }

    @Override
    public String getAudioTitle(ItemStack stack) {
        return ComputerCraft.getRecordInfo(stack);
    }

    @Override
    public String getAudioRecordName(ItemStack stack) {
        ItemRecord itemRecord = (ItemRecord) stack.getItem();
        return "records." + itemRecord.recordName;
    }

    @Override
    public IMount createDataMount(ItemStack stack, World world) {
        return null;
    }
}
