package dan200.computercraft.shared.pocket.items;

import net.minecraft.item.ItemStack;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.computer.core.ComputerFamily;

public class PocketComputerItemFactory {

    public static ItemStack create(int id, String label, ComputerFamily family, boolean modem) {
        ItemPocketComputer computer = ComputerCraft.Items.pocketComputer;
        switch (family) {
            case Normal:
            case Advanced:
                return computer.create(id, label, family, modem);
            default:
                return null;
        }
    }
}
