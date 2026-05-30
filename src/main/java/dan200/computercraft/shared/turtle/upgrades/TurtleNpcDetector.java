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
import dan200.computercraft.compat.customnpcs.peripheral.npcdetector.BlockNpcDetector;
import dan200.computercraft.compat.customnpcs.peripheral.npcdetector.NpcDetectorPeripheral;

/**
 * Turtle upgrade that embeds an NPC Detector into the turtle's tool slot.
 * All 10 {@code npc_detector} methods are available; the scan origin is the
 * turtle's current block position.
 *
 * <p>
 * Only registered when CustomNPC+ (mod ID {@code customnpcs}) is loaded.
 * </p>
 */
public class TurtleNpcDetector implements ITurtleUpgrade {

    private final int m_id;

    public TurtleNpcDetector(int id) {
        this.m_id = id;
    }

    @Override
    public int getUpgradeID() {
        return m_id;
    }

    @Override
    public String getUnlocalisedAdjective() {
        return "upgrade.computercraft:npc_detector.adjective";
    }

    @Override
    public TurtleUpgradeType getType() {
        return TurtleUpgradeType.Peripheral;
    }

    @Override
    public ItemStack getCraftingItem() {
        return new ItemStack(ComputerCraft.Blocks.npcDetector, 1, 0);
    }

    @Override
    public IIcon getIcon(ITurtleAccess turtle, TurtleSide side) {
        return BlockNpcDetector.getNpcDetectorIcon(2, 2);
    }

    @Override
    public IPeripheral createPeripheral(ITurtleAccess turtle, TurtleSide side) {
        return new Peripheral(turtle);
    }

    @Override
    public TurtleCommandResult useTool(ITurtleAccess turtle, TurtleSide side, TurtleVerb verb, int dir) {
        return null;
    }

    @Override
    public void update(ITurtleAccess turtle, TurtleSide side) {}

    // -------------------------------------------------------------------------
    // Inner peripheral — position provided by the turtle
    // -------------------------------------------------------------------------

    private static class Peripheral extends NpcDetectorPeripheral {

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
        protected World getMcWorld() {
            return m_turtle.getWorld();
        }

        @Override
        public boolean equals(IPeripheral other) {
            return other instanceof Peripheral && ((Peripheral) other).m_turtle == m_turtle;
        }
    }
}
