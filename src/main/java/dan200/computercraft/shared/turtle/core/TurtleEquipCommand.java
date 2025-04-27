package dan200.computercraft.shared.turtle.core;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleAnimation;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.shared.proxy.CCTurtleProxyCommon;
import dan200.computercraft.shared.util.InventoryUtil;
import dan200.computercraft.shared.util.WorldUtil;

public class TurtleEquipCommand implements ITurtleCommand {

    private final TurtleSide m_side;

    public TurtleEquipCommand(TurtleSide side) {
        this.m_side = side;
    }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        IInventory inventory = turtle.getInventory();
        ItemStack selectedStack = inventory.getStackInSlot(turtle.getSelectedSlot());
        ITurtleUpgrade newUpgrade;
        ItemStack newUpgradeStack;
        if (selectedStack != null) {
            newUpgradeStack = selectedStack.copy();
            newUpgrade = ComputerCraft.getTurtleUpgrade(newUpgradeStack);
            if (newUpgrade == null
                || !CCTurtleProxyCommon.isUpgradeSuitableForFamily(((TurtleBrain) turtle).getFamily(), newUpgrade)) {
                return TurtleCommandResult.failure("Not a valid upgrade");
            }
        } else {
            newUpgradeStack = null;
            newUpgrade = null;
        }

        ITurtleUpgrade oldUpgrade = turtle.getUpgrade(this.m_side);
        ItemStack oldUpgradeStack;
        if (oldUpgrade != null) {
            ItemStack craftingItem = oldUpgrade.getCraftingItem();
            oldUpgradeStack = craftingItem != null ? craftingItem.copy() : null;
        } else {
            oldUpgradeStack = null;
        }

        if (newUpgradeStack != null) {
            InventoryUtil.takeItems(1, inventory, turtle.getSelectedSlot(), 1, turtle.getSelectedSlot());
            inventory.markDirty();
        }

        if (oldUpgradeStack != null) {
            ItemStack remainder = InventoryUtil
                .storeItems(oldUpgradeStack, inventory, 0, inventory.getSizeInventory(), turtle.getSelectedSlot());
            if (remainder != null) {
                ChunkCoordinates position = turtle.getPosition();
                WorldUtil.dropItemStack(
                    remainder,
                    turtle.getWorld(),
                    position.posX,
                    position.posY,
                    position.posZ,
                    turtle.getDirection());
            }

            inventory.markDirty();
        }

        turtle.setUpgrade(this.m_side, newUpgrade);
        if (newUpgrade != null || oldUpgrade != null) {
            turtle.playAnimation(TurtleAnimation.Wait);
        }

        return TurtleCommandResult.success();
    }
}
