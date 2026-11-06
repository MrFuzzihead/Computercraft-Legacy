package dan200.computercraft.shared.pocket.upgrades;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.PeripheralItemFactory;
import dan200.computercraft.shared.pocket.PocketAccess;
import dan200.computercraft.shared.pocket.PocketUpgrades;
import dan200.computercraft.shared.pocket.peripherals.PocketModemPeripheral;

/**
 * The wireless modem, and the base of the ender modem.
 *
 * <p>
 * Its {@link #update(IPocketAccess, IPeripheral)} is the code that used to be the first branch of
 * {@code ItemPocketComputer.onUpdate}: follow the holder and keep the modem light's {@code modemLight} flag in the
 * computer's user data. That flag is what the item renderer reads, so it is part of the computer rather than the
 * upgrade's own NBT.
 * </p>
 */
public class PocketModemUpgrade extends BuiltinPocketUpgrade {

    public PocketModemUpgrade() {
        this(PocketUpgrades.WIRELESS_MODEM, "upgrade.computercraft:wireless_modem.adjective");
    }

    protected PocketModemUpgrade(int id, String adjective) {
        super(id, adjective);
    }

    @Override
    public ItemStack getCraftingItem() {
        // A null block means the mod is not registered yet (or this is a headless test); returning null is the same
        // "not craftable" answer the speaker and chat box give, and it keeps getUpgradeData from throwing.
        if (ComputerCraft.Blocks.peripheral == null) return null;

        return PeripheralItemFactory.create(PeripheralType.WirelessModem, null, 1);
    }

    @Override
    public IPeripheral createPeripheral(IPocketAccess access) {
        return new PocketModemPeripheral();
    }

    @Override
    public void update(IPocketAccess access, IPeripheral peripheral) {
        PocketModemPeripheral modem = expect(peripheral, PocketModemPeripheral.class);
        if (modem == null) return;

        double[] position = holderPosition(access);
        if (position != null) {
            modem.setLocation(access.getWorld(), position[0], position[1], position[2]);
        }

        setModemLight(access, modem.isActive());
    }

    /**
     * Records whether the modem's light is lit, so the client can render it.
     *
     * <p>
     * The flag lives in the computer's user data, which is synchronised to clients, not in the upgrade's own NBT.
     * Only this mod's {@link PocketAccess} can reach the computer, so a foreign {@code IPocketAccess} (a test double,
     * or an access from another implementation) simply skips the light.
     * </p>
     *
     * @param access the pocket computer
     * @param lit    whether the modem is lit
     */
    protected void setModemLight(IPocketAccess access, boolean lit) {
        if (!(access instanceof PocketAccess)) return;

        ServerComputer computer = ((PocketAccess) access).getComputer();
        NBTTagCompound userData = computer.getUserData();
        if (userData.getBoolean("modemLight") != lit) {
            userData.setBoolean("modemLight", lit);
            computer.updateUserData();
        }
    }
}
