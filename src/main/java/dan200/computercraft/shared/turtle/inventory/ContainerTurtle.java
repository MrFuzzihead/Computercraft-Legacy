package dan200.computercraft.shared.turtle.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;
import dan200.computercraft.shared.turtle.core.TurtleBrain;

public class ContainerTurtle extends Container {

    private static final int PROGRESS_ID_SELECTED_SLOT = 0;
    public final int m_playerInvStartY;
    public final int m_turtleInvStartX;
    protected ITurtleAccess m_turtle;
    private int m_selectedSlot;

    protected ContainerTurtle(IInventory playerInventory, ITurtleAccess turtle, int playerInvStartY,
        int turtleInvStartX) {
        this.m_playerInvStartY = playerInvStartY;
        this.m_turtleInvStartX = turtleInvStartX;
        this.m_turtle = turtle;
        if (!this.m_turtle.getWorld().isRemote) {
            this.m_selectedSlot = this.m_turtle.getSelectedSlot();
        } else {
            this.m_selectedSlot = 0;
        }

        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                this.addSlotToContainer(
                    new Slot(
                        this.m_turtle.getInventory(),
                        x + y * 4,
                        turtleInvStartX + 1 + x * 18,
                        playerInvStartY + 1 + y * 18));
            }
        }

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                this.addSlotToContainer(
                    new Slot(playerInventory, x + y * 9 + 9, 8 + x * 18, playerInvStartY + 1 + y * 18));
            }
        }

        for (int x = 0; x < 9; x++) {
            this.addSlotToContainer(new Slot(playerInventory, x, 8 + x * 18, playerInvStartY + 54 + 5));
        }
    }

    public ContainerTurtle(IInventory playerInventory, ITurtleAccess turtle) {
        this(playerInventory, turtle, 134, 175);
    }

    public int getSelectedSlot() {
        return this.m_selectedSlot;
    }

    private void sendStateToPlayer(ICrafting icrafting) {
        int selectedSlot = this.m_turtle.getSelectedSlot();
        icrafting.sendProgressBarUpdate(this, 0, selectedSlot);
    }

    public void addCraftingToCrafters(ICrafting crafting) {
        super.addCraftingToCrafters(crafting);
        this.sendStateToPlayer(crafting);
    }

    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int selectedSlot = this.m_turtle.getSelectedSlot();

        for (int i = 0; i < this.crafters.size(); i++) {
            ICrafting icrafting = (ICrafting) this.crafters.get(i);
            if (this.m_selectedSlot != selectedSlot) {
                icrafting.sendProgressBarUpdate(this, 0, selectedSlot);
            }
        }

        this.m_selectedSlot = selectedSlot;
    }

    public void updateProgressBar(int id, int value) {
        super.updateProgressBar(id, value);
        switch (id) {
            case 0:
                this.m_selectedSlot = value;
        }
    }

    public boolean canInteractWith(EntityPlayer player) {
        TileTurtle turtle = ((TurtleBrain) this.m_turtle).getOwner();
        return turtle != null ? turtle.isUseableByPlayer(player) : false;
    }

    protected ItemStack tryItemMerge(EntityPlayer player, int slotNum, int firstSlot, int lastSlot, boolean reverse) {
        Slot slot = (Slot) this.inventorySlots.get(slotNum);
        ItemStack originalStack = null;
        if (slot != null && slot.getHasStack()) {
            ItemStack clickedStack = slot.getStack();
            originalStack = clickedStack.copy();
            if (!this.mergeItemStack(clickedStack, firstSlot, lastSlot, reverse)) {
                return null;
            }

            if (clickedStack.stackSize == 0) {
                slot.putStack(null);
            } else {
                slot.onSlotChanged();
            }

            if (clickedStack.stackSize == originalStack.stackSize) {
                return null;
            }

            slot.onPickupFromSlot(player, clickedStack);
        }

        return originalStack;
    }

    public ItemStack transferStackInSlot(EntityPlayer player, int slotNum) {
        if (slotNum >= 0 && slotNum < 16) {
            return this.tryItemMerge(player, slotNum, 16, 52, true);
        } else {
            return slotNum >= 16 ? this.tryItemMerge(player, slotNum, 0, 16, false) : null;
        }
    }
}
