package dan200.computercraft.shared.proxy;

import java.io.File;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import dan200.computercraft.shared.computer.blocks.TileComputer;
import dan200.computercraft.shared.network.ComputerCraftPacket;
import dan200.computercraft.shared.peripheral.diskdrive.TileDiskDrive;
import dan200.computercraft.shared.peripheral.printer.TilePrinter;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;

public interface IComputerCraftProxy {

    void preInit();

    void init();

    boolean isClient();

    boolean getGlobalCursorBlink();

    Object getFixedWidthFontRenderer();

    String getRecordInfo(ItemStack var1);

    void playRecord(String var1, String var2, World var3, int var4, int var5, int var6);

    Object getDiskDriveGUI(InventoryPlayer var1, TileDiskDrive var2);

    Object getComputerGUI(TileComputer var1);

    Object getPrinterGUI(InventoryPlayer var1, TilePrinter var2);

    Object getTurtleGUI(InventoryPlayer var1, TileTurtle var2);

    Object getPrintoutGUI(InventoryPlayer var1);

    Object getPocketComputerGUI(InventoryPlayer var1);

    File getWorldDir(World var1);

    void handlePacket(ComputerCraftPacket var1, EntityPlayer var2);
}
