package dan200.computercraft.shared.pocket.upgrades;

import net.minecraft.item.ItemStack;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.shared.pocket.PocketUpgrades;
import dan200.computercraft.shared.pocket.peripherals.PocketNpcDetectorPeripheral;

/**
 * The CustomNPCs detector: reports nearby NPCs relative to the pocket computer.
 *
 * <p>
 * The upgrade is always registered, like the peripheral it builds always existed; the <em>item</em> that installs it
 * only exists when CustomNPCs is loaded, so {@link #getCraftingItem()} returns {@code null} otherwise and no player can
 * fit one.
 * </p>
 */
public class PocketNpcDetectorUpgrade extends BuiltinPocketUpgrade {

    public PocketNpcDetectorUpgrade() {
        super(PocketUpgrades.NPC_DETECTOR, "upgrade.computercraft:npc_detector.adjective");
    }

    @Override
    public ItemStack getCraftingItem() {
        return ComputerCraft.Blocks.npcDetector == null ? null : new ItemStack(ComputerCraft.Blocks.npcDetector, 1, 0);
    }

    @Override
    public IPeripheral createPeripheral(IPocketAccess access) {
        return new PocketNpcDetectorPeripheral();
    }

    @Override
    public void update(IPocketAccess access, IPeripheral peripheral) {
        PocketNpcDetectorPeripheral detector = expect(peripheral, PocketNpcDetectorPeripheral.class);
        if (detector == null) return;

        double[] position = holderPosition(access);
        if (position != null) {
            detector.setLocation(access.getWorld(), position[0], position[1], position[2]);
        }
    }
}
