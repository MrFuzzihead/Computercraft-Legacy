package dan200.computercraft.server.proxy;

import java.io.File;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.computer.blocks.TileComputer;
import dan200.computercraft.shared.peripheral.diskdrive.TileDiskDrive;
import dan200.computercraft.shared.peripheral.printer.TilePrinter;
import dan200.computercraft.shared.proxy.ComputerCraftProxyCommon;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;

public class ComputerCraftProxyServer extends ComputerCraftProxyCommon {

    @Override
    public void init() {
        super.init();
    }

    @Override
    public Object getTurtleGUI(InventoryPlayer inventory, TileTurtle turtle) {
        return null;
    }

    @Override
    public boolean isClient() {
        return false;
    }

    @Override
    public boolean getGlobalCursorBlink() {
        return false;
    }

    @Override
    public Object getFixedWidthFontRenderer() {
        return null;
    }

    @Override
    public void playRecord(String record, String recordInfo, World world, int x, int y, int z) {}

    @Override
    public Object getDiskDriveGUI(InventoryPlayer inventory, TileDiskDrive drive) {
        return null;
    }

    @Override
    public Object getComputerGUI(TileComputer computer) {
        return null;
    }

    @Override
    public Object getPrinterGUI(InventoryPlayer inventory, TilePrinter printer) {
        return null;
    }

    @Override
    public Object getPrintoutGUI(InventoryPlayer inventory) {
        return null;
    }

    @Override
    public Object getPocketComputerGUI(InventoryPlayer inventory) {
        return null;
    }

    @Override
    public File getWorldDir(World world) {
        return new File(
            ComputerCraft.getBaseDir(),
            DimensionManager.getWorld(0)
                .getSaveHandler()
                .getWorldDirectoryName());
    }

    private void registerForgeHandlers() {}
}
