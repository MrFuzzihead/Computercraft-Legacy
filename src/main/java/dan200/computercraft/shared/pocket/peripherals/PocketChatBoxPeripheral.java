package dan200.computercraft.shared.pocket.peripherals;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.peripheral.chatbox.PortableChatBoxPeripheral;

/**
 * Chat-box peripheral for pocket computers. The owning
 * {@link dan200.computercraft.shared.pocket.items.ItemPocketComputer} calls
 * {@link #setLocation} each tick to supply the current dimension and position.
 */
public class PocketChatBoxPeripheral extends PortableChatBoxPeripheral {

    private int m_dimensionId = Integer.MIN_VALUE;
    private double m_x = 0.0;
    private double m_y = 0.0;
    private double m_z = 0.0;

    public void setLocation(int dimensionId, double x, double y, double z) {
        m_dimensionId = dimensionId;
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
    protected int getDimensionId() {
        return m_dimensionId;
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof PocketChatBoxPeripheral;
    }
}
