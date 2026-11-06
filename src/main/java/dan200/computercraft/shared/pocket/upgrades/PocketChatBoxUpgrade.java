package dan200.computercraft.shared.pocket.upgrades;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.shared.pocket.PocketUpgrades;
import dan200.computercraft.shared.pocket.peripherals.PocketChatBoxPeripheral;

/**
 * The chat box: relays chat to and from the pocket computer.
 *
 * <p>
 * Unlike the other positional upgrades the chat box tracks a dimension id rather than a {@code World}, because its
 * messages are addressed across dimensions; that is why the old {@code onUpdate} branch read
 * {@code world.provider.dimensionId} instead of passing the world through.
 * </p>
 */
public class PocketChatBoxUpgrade extends BuiltinPocketUpgrade {

    public PocketChatBoxUpgrade() {
        super(PocketUpgrades.CHAT_BOX, "upgrade.computercraft:chatbox.adjective");
    }

    @Override
    public ItemStack getCraftingItem() {
        return ComputerCraft.Blocks.chatBox == null ? null : new ItemStack(ComputerCraft.Blocks.chatBox, 1, 0);
    }

    @Override
    public IPeripheral createPeripheral(IPocketAccess access) {
        return new PocketChatBoxPeripheral();
    }

    @Override
    public void update(IPocketAccess access, IPeripheral peripheral) {
        PocketChatBoxPeripheral chatBox = expect(peripheral, PocketChatBoxPeripheral.class);
        if (chatBox == null) return;

        double[] position = holderPosition(access);
        if (position == null) return;

        World world = access.getWorld();
        chatBox.setLocation(world == null ? 0 : world.provider.dimensionId, position[0], position[1], position[2]);
    }
}
