package dan200.computercraft.shared.turtle.upgrades;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IIcon;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleUpgradeType;
import dan200.computercraft.api.turtle.TurtleVerb;
import dan200.computercraft.shared.peripheral.speaker.BlockSpeaker;
import dan200.computercraft.shared.peripheral.speaker.PortableSpeakerPeripheral;

public class TurtleSpeaker implements ITurtleUpgrade {

    private final int m_id;

    public TurtleSpeaker(int id) {
        this.m_id = id;
    }

    @Override
    public int getUpgradeID() {
        return this.m_id;
    }

    @Override
    public String getUnlocalisedAdjective() {
        return "upgrade.computercraft:speaker.adjective";
    }

    @Override
    public TurtleUpgradeType getType() {
        return TurtleUpgradeType.Peripheral;
    }

    @Override
    public ItemStack getCraftingItem() {
        return new ItemStack(ComputerCraft.Blocks.speaker, 1, 0);
    }

    @Override
    public IIcon getIcon(ITurtleAccess turtle, TurtleSide side) {
        // Return the speaker front face icon (side=2, direction=2)
        return BlockSpeaker.getSpeakerIcon(2, 2);
    }

    @Override
    public IPeripheral createPeripheral(ITurtleAccess turtle, TurtleSide side) {
        return new TurtleSpeaker.Peripheral(turtle);
    }

    @Override
    public TurtleCommandResult useTool(ITurtleAccess turtle, TurtleSide side, TurtleVerb verb, int dir) {
        return null;
    }

    @Override
    public void update(ITurtleAccess turtle, TurtleSide side) {
        if (!turtle.getWorld().isRemote) {
            IPeripheral peripheral = turtle.getPeripheral(side);
            if (peripheral instanceof TurtleSpeaker.Peripheral speakerPeripheral) {
                ChunkCoordinates pos = turtle.getPosition();
                speakerPeripheral.tick(turtle.getWorld(), pos.posX + 0.5, pos.posY + 0.5, pos.posZ + 0.5);
            }
        }
    }

    private static class Peripheral extends PortableSpeakerPeripheral {

        private final ITurtleAccess m_turtle;

        Peripheral(ITurtleAccess turtle) {
            this.m_turtle = turtle;
        }

        @Override
        public boolean equals(IPeripheral other) {
            if (other instanceof TurtleSpeaker.Peripheral otherSpeaker) {
                return otherSpeaker.m_turtle == this.m_turtle;
            }
            return false;
        }
    }
}
