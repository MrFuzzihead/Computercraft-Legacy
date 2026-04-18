package dan200.computercraft.shared.pocket.items;

import net.minecraft.item.ItemStack;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.computer.core.ComputerFamily;

public class PocketComputerItemFactory {

    public static ItemStack create(int id, String label, ComputerFamily family, boolean modem) {
        ItemPocketComputer computer = ComputerCraft.Items.pocketComputer;
        return switch (family) {
            case Normal, Advanced -> computer.create(id, label, family, modem);
            default -> null;
        };
    }

    public static ItemStack createWithEnderModem(int id, String label, ComputerFamily family) {
        ItemPocketComputer computer = ComputerCraft.Items.pocketComputer;
        return switch (family) {
            case Normal, Advanced -> computer.createWithEnderModem(id, label, family);
            default -> null;
        };
    }

    public static ItemStack createWithSpeaker(int id, String label, ComputerFamily family) {
        ItemPocketComputer computer = ComputerCraft.Items.pocketComputer;
        return switch (family) {
            case Normal, Advanced -> computer.createWithSpeaker(id, label, family);
            default -> null;
        };
    }
}
