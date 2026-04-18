package dan200.computercraft.shared.pocket.peripherals;

import net.minecraft.world.World;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.peripheral.speaker.PortableSpeakerPeripheral;

/**
 * Speaker peripheral for pocket computers. The owning
 * {@link dan200.computercraft.shared.pocket.items.ItemPocketComputer}
 * calls {@link #setLocation} each tick to supply the current world and position,
 * and then this peripheral's {@link #tick} is invoked to flush pending audio.
 */
public class PocketSpeakerPeripheral extends PortableSpeakerPeripheral {

    private World m_world = null;
    private double m_x = 0.0;
    private double m_y = 0.0;
    private double m_z = 0.0;

    /**
     * Updates the world and position of this speaker (called every tick from
     * {@link dan200.computercraft.shared.pocket.items.ItemPocketComputer#onUpdate}).
     */
    public void setLocation(World world, double x, double y, double z) {
        m_world = world;
        m_x = x;
        m_y = y;
        m_z = z;
    }

    /**
     * Flushes pending audio using the last set location. No-op if location has
     * not been set yet.
     */
    public void tick() {
        if (m_world != null && !m_world.isRemote) {
            tick(m_world, m_x, m_y, m_z);
        }
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof PocketSpeakerPeripheral;
    }
}
