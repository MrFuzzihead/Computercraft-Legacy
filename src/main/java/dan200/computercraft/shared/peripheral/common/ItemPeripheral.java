package dan200.computercraft.shared.peripheral.common;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.peripheral.PeripheralType;

public class ItemPeripheral extends ItemPeripheralBase {

    public ItemPeripheral(Block block) {
        super(block);
        this.setUnlocalizedName("computercraft:peripheral");
        this.setCreativeTab(ComputerCraft.mainCreativeTab);
    }

    public ItemStack create(PeripheralType type, String label, int quantity) {
        ItemStack stack;
        switch (type) {
            case DiskDrive:
                stack = new ItemStack(this, quantity, 0);
                break;
            case WirelessModem:
                stack = new ItemStack(this, quantity, 1);
                break;
            case Monitor:
                stack = new ItemStack(this, quantity, 2);
                break;
            case Printer:
                stack = new ItemStack(this, quantity, 3);
                break;
            case AdvancedMonitor:
                stack = new ItemStack(this, quantity, 4);
                break;
            default:
                return null;
        }

        if (label != null) {
            stack.setStackDisplayName(label);
        }

        return stack;
    }

    public void getSubItems(Item itemID, CreativeTabs tabs, List list) {
        list.add(PeripheralItemFactory.create(PeripheralType.DiskDrive, null, 1));
        list.add(PeripheralItemFactory.create(PeripheralType.Printer, null, 1));
        list.add(PeripheralItemFactory.create(PeripheralType.Monitor, null, 1));
        list.add(PeripheralItemFactory.create(PeripheralType.AdvancedMonitor, null, 1));
        list.add(PeripheralItemFactory.create(PeripheralType.WirelessModem, null, 1));
    }

    @Override
    public PeripheralType getPeripheralType(int damage) {
        switch (damage) {
            case 0:
            default:
                return PeripheralType.DiskDrive;
            case 1:
                return PeripheralType.WirelessModem;
            case 2:
                return PeripheralType.Monitor;
            case 3:
                return PeripheralType.Printer;
            case 4:
                return PeripheralType.AdvancedMonitor;
        }
    }
}
