package dan200.computercraft.shared.media.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import dan200.computercraft.shared.util.InventoryUtil;

public class ContainerHeldItem extends Container {

    private final ItemStack m_stack;
    private final int m_slot;

    public ContainerHeldItem(InventoryPlayer player) {
        this.m_slot = player.currentItem;
        this.m_stack = InventoryUtil.copyItem(player.getStackInSlot(this.m_slot));
    }

    public ItemStack getStack() {
        return this.m_stack;
    }

    public boolean canInteractWith(EntityPlayer player) {
        if (player != null && player.isEntityAlive()) {
            ItemStack stack = player.inventory.getStackInSlot(this.m_slot);
            if (stack == this.m_stack
                || stack != null && this.m_stack != null && stack.getItem() == this.m_stack.getItem()) {
                return true;
            }
        }

        return false;
    }
}
