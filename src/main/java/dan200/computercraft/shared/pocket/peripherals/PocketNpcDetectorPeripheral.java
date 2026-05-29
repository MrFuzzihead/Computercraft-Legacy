package dan200.computercraft.shared.pocket.peripherals;

import net.minecraft.world.World;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.peripheral.npcdetector.NpcDetectorPeripheral;

/**
 * NPC Detector peripheral for pocket computers.
 *
 * <p>
 * The owning {@link dan200.computercraft.shared.pocket.items.ItemPocketComputer} calls
 * {@link #setLocation} each tick to supply the current world and position.
 * Scan methods block until the Minecraft main thread can execute, which is the same
 * constraint as the block and turtle variants.
 * </p>
 *
 * <p>
 * Only created when CustomNPC+ (mod ID {@code customnpcs}) is loaded.
 * </p>
 */
public class PocketNpcDetectorPeripheral extends NpcDetectorPeripheral {

    private World m_world = null;
    private double m_x = 0.0;
    private double m_y = 0.0;
    private double m_z = 0.0;

    /**
     * Updates the scan origin. Called every tick by
     * {@link dan200.computercraft.shared.pocket.items.ItemPocketComputer#onUpdate}.
     */
    public void setLocation(World world, double x, double y, double z) {
        m_world = world;
        m_x = x;
        m_y = y;
        m_z = z;
    }

    @Override
    protected double getPositionX() {
        return m_x;
    }

    @Override
    protected double getPositionY() {
        return m_y;
    }

    @Override
    protected double getPositionZ() {
        return m_z;
    }

    @Override
    protected World getMcWorld() {
        return m_world;
    }

    @Override
    public boolean equals(IPeripheral other) {
        // Only one PocketNpcDetectorPeripheral exists per pocket computer instance.
        return other instanceof PocketNpcDetectorPeripheral;
    }
}
