package dan200.computercraft.shared.pocket.upgrades;

import net.minecraft.item.ItemStack;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.shared.pocket.PocketUpgrades;
import dan200.computercraft.shared.pocket.peripherals.PocketNpcInterfacePeripheral;

/**
 * The CustomNPCs interface: lets a pocket computer drive NPCs it is linked to.
 *
 * <p>
 * This is the one built-in upgrade whose peripheral is built from the pocket computer's own stack, and the one whose
 * tick has to push the stack back in: the deep links an NPC keeps to this peripheral are held in that stack's NBT, so
 * a stack swapped out from under the peripheral (the item moved between inventories) must be noticed.
 * </p>
 */
public class PocketNpcInterfaceUpgrade extends BuiltinPocketUpgrade {

    public PocketNpcInterfaceUpgrade() {
        super(PocketUpgrades.NPC_INTERFACE, "upgrade.computercraft:npc_interface.adjective");
    }

    @Override
    public ItemStack getCraftingItem() {
        return ComputerCraft.Blocks.npcInterface == null ? null
            : new ItemStack(ComputerCraft.Blocks.npcInterface, 1, 0);
    }

    @Override
    public IPeripheral createPeripheral(IPocketAccess access) {
        return new PocketNpcInterfacePeripheral(access.getStack());
    }

    @Override
    public void update(IPocketAccess access, IPeripheral peripheral) {
        PocketNpcInterfacePeripheral npcInterface = expect(peripheral, PocketNpcInterfacePeripheral.class);
        if (npcInterface == null) return;

        npcInterface.setStack(access.getStack());

        double[] position = holderPosition(access);
        if (position != null) {
            npcInterface.setLocation(position[0], position[1], position[2]);
        }
    }
}
