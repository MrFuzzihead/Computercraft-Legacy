package dan200.computercraft.shared.media.common;

import net.minecraft.item.Item;
import net.minecraft.item.ItemRecord;
import net.minecraft.item.ItemStack;

import dan200.computercraft.api.media.IMedia;
import dan200.computercraft.api.media.IMediaProvider;
import dan200.computercraft.shared.media.items.RecordMedia;

public class DefaultMediaProvider implements IMediaProvider {

    @Override
    public IMedia getMedia(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof IMedia) {
            return (IMedia) item;
        } else {
            return item instanceof ItemRecord ? new RecordMedia() : null;
        }
    }
}
