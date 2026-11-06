package dan200.computercraft.shared.pocket.upgrades;

import net.minecraft.item.ItemStack;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.shared.pocket.PocketUpgrades;
import dan200.computercraft.shared.pocket.peripherals.PocketSpeakerPeripheral;

/**
 * The speaker: plays notes and sounds at the pocket computer's position.
 *
 * <p>
 * {@link #update(IPocketAccess, IPeripheral)} is the second branch of the old {@code onUpdate} chain — follow the
 * holder, then tick the speaker so queued sounds play from where the player is standing.
 * </p>
 */
public class PocketSpeakerUpgrade extends BuiltinPocketUpgrade {

    public PocketSpeakerUpgrade() {
        super(PocketUpgrades.SPEAKER, "upgrade.computercraft:speaker.adjective");
    }

    @Override
    public ItemStack getCraftingItem() {
        return ComputerCraft.Blocks.speaker == null ? null : new ItemStack(ComputerCraft.Blocks.speaker, 1, 0);
    }

    @Override
    public IPeripheral createPeripheral(IPocketAccess access) {
        return new PocketSpeakerPeripheral();
    }

    @Override
    public void update(IPocketAccess access, IPeripheral peripheral) {
        PocketSpeakerPeripheral speaker = expect(peripheral, PocketSpeakerPeripheral.class);
        if (speaker == null) return;

        double[] position = holderPosition(access);
        if (position != null) {
            speaker.setLocation(access.getWorld(), position[0], position[1], position[2]);
        }

        speaker.tick();
    }
}
