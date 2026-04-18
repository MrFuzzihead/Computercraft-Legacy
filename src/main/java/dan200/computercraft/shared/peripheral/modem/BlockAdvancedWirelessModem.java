package dan200.computercraft.shared.peripheral.modem;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.BlockPeripheralBase;
import dan200.computercraft.shared.peripheral.common.TilePeripheralBase;

public class BlockAdvancedWirelessModem extends BlockPeripheralBase {

    public BlockAdvancedWirelessModem() {
        this.setHardness(2.0F);
        this.setBlockName("computercraft:advanced_wireless_modem");
        this.setCreativeTab(ComputerCraft.mainCreativeTab);
    }

    @Override
    public int getDefaultMetadata(PeripheralType type, int placedSide) {
        int dir = Facing.oppositeSide[placedSide];
        if (dir >= 2) {
            return 4 + dir;
        }
        return dir;
    }

    @Override
    public PeripheralType getPeripheralType(int metadata) {
        return PeripheralType.AdvancedWirelessModem;
    }

    @Override
    public TilePeripheralBase createTile(PeripheralType type) {
        return new TileAdvancedWirelessModem();
    }

    @Override
    public IIcon getItemTexture(PeripheralType type, int side) {
        return TileAdvancedWirelessModem.getItemTexture(side, false);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(IIconRegister iconRegister) {
        super.registerBlockIcons(iconRegister);
        TileAdvancedWirelessModem.registerIcons(iconRegister);
    }
}
