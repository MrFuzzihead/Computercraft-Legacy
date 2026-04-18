package dan200.computercraft.shared.pocket.peripherals;

import net.minecraft.world.World;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.peripheral.modem.AdvancedWirelessModemPeripheral;
import dan200.computercraft.shared.peripheral.modem.INetwork;

/**
 * Ender Modem peripheral for pocket computers.
 * Extends {@link PocketModemPeripheral} so that the existing
 * {@code instanceof PocketModemPeripheral} checks in {@code ItemPocketComputer}
 * continue to work (position updates, modem-light NBT, etc.).
 */
public class PocketEnderModemPeripheral extends PocketModemPeripheral {

    @Override
    protected double getTransmitRange() {
        return Double.MAX_VALUE;
    }

    @Override
    protected INetwork getNetwork() {
        World world = this.getWorld();
        return AdvancedWirelessModemPeripheral.createEnderNetwork(world);
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof PocketEnderModemPeripheral;
    }
}
