package dan200.computercraft.shared.peripheral.common;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.diskdrive.TileDiskDrive;
import dan200.computercraft.shared.peripheral.modem.TileWirelessModem;
import dan200.computercraft.shared.peripheral.monitor.TileMonitor;
import dan200.computercraft.shared.peripheral.printer.TilePrinter;
import dan200.computercraft.shared.util.DirectionUtil;

public final class BlockPeripheral extends BlockPeripheralBase {

    public IIcon s_craftyUpgradeIcon;

    public BlockPeripheral() {
        this.setHardness(2.0F);
        this.setBlockName("computercraft:peripheral");
        this.setCreativeTab(ComputerCraft.mainCreativeTab);
    }

    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        if (block != this) return block.getLightValue(world, x, y, z);

        PeripheralType type = getPeripheralType(world, x, y, z);
        if (type == PeripheralType.Monitor) return ComputerCraft.monitorLightLevel;
        if (type == PeripheralType.AdvancedMonitor) return ComputerCraft.advancedMonitorLightLevel;
        return 0;
    }

    @Override
    public int getDefaultMetadata(PeripheralType type, int placedSide) {
        switch (type) {
            case DiskDrive:
            default:
                if (placedSide >= 2) {
                    return placedSide;
                }

                return 2;
            case WirelessModem:
                int dir = Facing.oppositeSide[placedSide];
                if (dir >= 2) {
                    return 4 + dir;
                }

                return dir;
            case Monitor:
                return 10;
            case Printer:
                return 11;
            case AdvancedMonitor:
                return 12;
        }
    }

    @Override
    public PeripheralType getPeripheralType(int metadata) {
        if (metadata >= 2 && metadata <= 5) {
            return PeripheralType.DiskDrive;
        } else if (metadata <= 9) {
            return PeripheralType.WirelessModem;
        } else if (metadata == 10) {
            return PeripheralType.Monitor;
        } else if (metadata == 11) {
            return PeripheralType.Printer;
        } else {
            return metadata == 12 ? PeripheralType.AdvancedMonitor : PeripheralType.DiskDrive;
        }
    }

    @Override
    public TilePeripheralBase createTile(PeripheralType type) {
        switch (type) {
            case DiskDrive:
            default:
                return new TileDiskDrive();
            case WirelessModem:
                return new TileWirelessModem();
            case Monitor:
            case AdvancedMonitor:
                return new TileMonitor();
            case Printer:
                return new TilePrinter();
        }
    }

    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase player, ItemStack stack) {
        switch (this.getPeripheralType(world, x, y, z)) {
            case DiskDrive:
            case Printer:
                int dir = DirectionUtil.fromEntityRot(player);
                this.setDirection(world, x, y, z, dir);
                if (stack.hasDisplayName()) {
                    TileEntity tilex = world.getTileEntity(x, y, z);
                    if (tilex != null && tilex instanceof TilePeripheralBase) {
                        TilePeripheralBase peripheralBase = (TilePeripheralBase) tilex;
                        peripheralBase.setLabel(stack.getDisplayName());
                    }
                }
            case WirelessModem:
            default:
                break;
            case Monitor:
            case AdvancedMonitor:
                TileEntity tile = world.getTileEntity(x, y, z);
                if (tile != null && tile instanceof TileMonitor) {
                    int orientation = DirectionUtil.fromEntityRot(player);
                    if (player.rotationPitch > 66.5F) {
                        orientation += 12;
                    } else if (player.rotationPitch < -66.5F) {
                        orientation += 6;
                    }

                    TileMonitor monitor = (TileMonitor) tile;
                    if (world.isRemote) {
                        monitor.setDir(orientation);
                    } else {
                        monitor.contractNeighbours();
                        monitor.setDir(orientation);
                        monitor.contract();
                        monitor.expand();
                    }
                }
        }
    }

    @Override
    public IIcon getItemTexture(PeripheralType type, int side) {
        switch (type) {
            case DiskDrive:
            default:
                return TileDiskDrive.getItemTexture(side);
            case WirelessModem:
                return TileWirelessModem.getItemTexture(side, false);
            case Monitor:
            case AdvancedMonitor:
                return TileMonitor.getItemTexture(side, type == PeripheralType.AdvancedMonitor);
            case Printer:
                return TilePrinter.getItemTexture(side);
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister iconRegister) {
        super.registerBlockIcons(iconRegister);
        TileDiskDrive.registerIcons(iconRegister);
        TileWirelessModem.registerIcons(iconRegister);
        TileMonitor.registerIcons(iconRegister);
        TilePrinter.registerIcons(iconRegister);
        this.s_craftyUpgradeIcon = iconRegister.registerIcon("computercraft:craftyUpgrade");
    }
}
