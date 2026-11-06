package dan200.computercraft.shared.pocket.upgrades;

import net.minecraft.item.ItemStack;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.PeripheralItemFactory;
import dan200.computercraft.shared.pocket.PocketUpgrades;
import dan200.computercraft.shared.pocket.peripherals.PocketEnderModemPeripheral;

/**
 * The ender modem: a wireless modem that works across dimensions.
 *
 * <p>
 * It shares {@link PocketModemUpgrade}'s tick behaviour (the old {@code instanceof PocketModemPeripheral} branch caught
 * both), and differs only in the item that installs it and the peripheral class.
 * </p>
 */
public class PocketEnderModemUpgrade extends PocketModemUpgrade {

    public PocketEnderModemUpgrade() {
        super(PocketUpgrades.ENDER_MODEM, "upgrade.computercraft:advanced_wireless_modem.adjective");
    }

    @Override
    public ItemStack getCraftingItem() {
        if (ComputerCraft.Blocks.advancedWirelessModem == null) return null;

        return PeripheralItemFactory.create(PeripheralType.AdvancedWirelessModem, null, 1);
    }

    @Override
    public IPeripheral createPeripheral(IPocketAccess access) {
        return new PocketEnderModemPeripheral();
    }
}
