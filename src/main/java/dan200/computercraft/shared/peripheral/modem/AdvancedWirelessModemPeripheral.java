package dan200.computercraft.shared.peripheral.modem;

public abstract class AdvancedWirelessModemPeripheral extends WirelessModemPeripheral {

    @Override
    protected double getTransmitRange() {
        // No distance limit for the Ender Modem
        return Double.MAX_VALUE;
    }

    @Override
    protected INetwork getNetwork() {
        return createEnderNetwork(this.getWorld());
    }

    /**
     * Creates the composite {@link INetwork} for an Ender Modem hosted in the
     * given world. Returns {@code null} when {@code world} is {@code null}.
     * <p>
     * Extracted as a public static factory so that other ender-modem peripherals
     * (e.g. {@code PocketEnderModemPeripheral}) can reuse the same network logic
     * without needing access to the private {@link AdvancedWirelessNetwork} class.
     */
    public static INetwork createEnderNetwork(net.minecraft.world.World world) {
        if (world == null) {
            return null;
        }
        return new AdvancedWirelessNetwork(WirelessNetwork.getGlobal(), WirelessNetwork.get(world));
    }

    /**
     * A composite {@link INetwork} used exclusively by the Ender Modem.
     *
     * <ul>
     * <li>Receivers are registered on the <em>global</em> network only, so that
     * an Ender Modem receives each message exactly once.</li>
     * <li>Transmissions are sent to <em>both</em> the global network (reaches all
     * Ender Modems regardless of dimension) <em>and</em> the per-world network
     * (reaches normal Wireless Modems that are in range). The per-world→global
     * forward that {@link WirelessNetwork} normally performs is suppressed during
     * the per-world transmit to prevent Ender Modems from receiving duplicates.</li>
     * </ul>
     */
    private static final class AdvancedWirelessNetwork implements INetwork {

        private final WirelessNetwork m_globalNetwork;
        private final WirelessNetwork m_worldNetwork;

        AdvancedWirelessNetwork(WirelessNetwork global, WirelessNetwork world) {
            this.m_globalNetwork = global;
            this.m_worldNetwork = world;
        }

        @Override
        public void addReceiver(IReceiver receiver) {
            // Register only on the global network; per-world networks forward to
            // global anyway, so a single registration avoids duplicate receives.
            this.m_globalNetwork.addReceiver(receiver);
        }

        @Override
        public void removeReceiver(IReceiver receiver) {
            this.m_globalNetwork.removeReceiver(receiver);
        }

        @Override
        public void transmit(int channel, int replyChannel, Object payload, double range, double xPos, double yPos,
            double zPos, Object senderObject) {
            // 1. Transmit directly to the global network → reaches all Ender Modems.
            this.m_globalNetwork.transmit(channel, replyChannel, payload, range, xPos, yPos, zPos, senderObject);
            // 2. Transmit to the per-world network → reaches normal Wireless Modems in
            // range. The per-world→global forward is suppressed so Ender Modems do
            // not receive the same message a second time via that path.
            WirelessNetwork.suppressGlobalForward(
                () -> this.m_worldNetwork
                    .transmit(channel, replyChannel, payload, range, xPos, yPos, zPos, senderObject));
        }

        @Override
        public boolean isWireless() {
            return true;
        }
    }
}
