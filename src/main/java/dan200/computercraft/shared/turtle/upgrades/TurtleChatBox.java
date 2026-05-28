package dan200.computercraft.shared.turtle.upgrades;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleUpgradeType;
import dan200.computercraft.api.turtle.TurtleVerb;
import dan200.computercraft.shared.peripheral.chatbox.BlockChatBox;
import dan200.computercraft.shared.peripheral.chatbox.PortableChatBoxPeripheral;

public class TurtleChatBox implements ITurtleUpgrade {

    private final int m_id;

    public TurtleChatBox(int id) {
        this.m_id = id;
    }

    @Override
    public int getUpgradeID() {
        return this.m_id;
    }

    @Override
    public String getUnlocalisedAdjective() {
        return "upgrade.computercraft:chatbox.adjective";
    }

    @Override
    public TurtleUpgradeType getType() {
        return TurtleUpgradeType.Peripheral;
    }

    @Override
    public ItemStack getCraftingItem() {
        return new ItemStack(ComputerCraft.Blocks.chatBox, 1, 0);
    }

    @Override
    public IIcon getIcon(ITurtleAccess turtle, TurtleSide side) {
        return BlockChatBox.getChatBoxIcon(2, 2);
    }

    @Override
    public IPeripheral createPeripheral(ITurtleAccess turtle, TurtleSide side) {
        return new TurtleChatBox.Peripheral(turtle);
    }

    @Override
    public TurtleCommandResult useTool(ITurtleAccess turtle, TurtleSide side, TurtleVerb verb, int dir) {
        return null;
    }

    @Override
    public void update(ITurtleAccess turtle, TurtleSide side) {
        // No per-tick audio flushing needed for chatbox — events arrive asynchronously.
    }

    // -------------------------------------------------------------------------
    // Inner peripheral
    // -------------------------------------------------------------------------

    private static class Peripheral extends PortableChatBoxPeripheral {

        private final ITurtleAccess m_turtle;

        Peripheral(ITurtleAccess turtle) {
            this.m_turtle = turtle;
        }

        @Override
        protected double getPositionX() {
            ChunkCoordinates pos = m_turtle.getPosition();
            return pos.posX + 0.5;
        }

        @Override
        protected double getPositionY() {
            ChunkCoordinates pos = m_turtle.getPosition();
            return pos.posY + 0.5;
        }

        @Override
        protected double getPositionZ() {
            ChunkCoordinates pos = m_turtle.getPosition();
            return pos.posZ + 0.5;
        }

        @Override
        protected int getDimensionId() {
            World world = m_turtle.getWorld();
            return world != null ? world.provider.dimensionId : Integer.MIN_VALUE;
        }

        @Override
        public boolean equals(IPeripheral other) {
            return other instanceof TurtleChatBox.Peripheral
                && ((TurtleChatBox.Peripheral) other).m_turtle == this.m_turtle;
        }
    }
}
