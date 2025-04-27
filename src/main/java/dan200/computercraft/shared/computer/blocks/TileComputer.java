package dan200.computercraft.shared.computer.blocks;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.core.IComputer;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.computer.items.ComputerItemFactory;

public class TileComputer extends TileComputerBase {

    private static final IIcon[] s_icons = new IIcon[5];
    private static final IIcon[] s_advancedIcons = new IIcon[5];
    private static final IIcon[] s_commandIcons = new IIcon[5];
    private static final int[] s_remapSide = new int[] { 0, 1, 2, 3, 5, 4 };

    @SideOnly(Side.CLIENT)
    public static void registerIcons(IIconRegister iconRegister) {
        s_icons[0] = iconRegister.registerIcon("computercraft:computerTop");
        s_icons[1] = iconRegister.registerIcon("computercraft:computerSide");
        s_icons[2] = iconRegister.registerIcon("computercraft:computerFront");
        s_icons[3] = iconRegister.registerIcon("computercraft:computerFrontOn");
        s_icons[4] = iconRegister.registerIcon("computercraft:computerFrontBlink");
        s_advancedIcons[0] = iconRegister.registerIcon("computercraft:computerTopAdvanced");
        s_advancedIcons[1] = iconRegister.registerIcon("computercraft:computerSideAdvanced");
        s_advancedIcons[2] = iconRegister.registerIcon("computercraft:computerFrontAdvanced");
        s_advancedIcons[3] = iconRegister.registerIcon("computercraft:computerFrontOnAdvanced");
        s_advancedIcons[4] = iconRegister.registerIcon("computercraft:computerFrontBlinkAdvanced");
        s_commandIcons[0] = iconRegister.registerIcon("computercraft:commandComputerTop");
        s_commandIcons[1] = iconRegister.registerIcon("computercraft:commandComputerSide");
        s_commandIcons[2] = iconRegister.registerIcon("computercraft:commandComputerFront");
        s_commandIcons[3] = iconRegister.registerIcon("computercraft:commandComputerFrontOn");
        s_commandIcons[4] = iconRegister.registerIcon("computercraft:commandComputerFrontBlink");
    }

    public static IIcon getItemTexture(int side, ComputerFamily family) {
        return getTexture(side, family, true, true, 3);
    }

    private static IIcon getTexture(int side, ComputerFamily family, boolean on, boolean cursor, int direction) {
        IIcon[] icons = null;
        switch (family) {
            case Normal:
            default:
                icons = s_icons;
                break;
            case Advanced:
                icons = s_advancedIcons;
                break;
            case Command:
                icons = s_commandIcons;
        }

        if (side == 0 || side == 1) {
            return icons[0];
        } else if (side != direction) {
            return icons[1];
        } else if (on) {
            return cursor ? icons[4] : icons[3];
        } else {
            return icons[2];
        }
    }

    @Override
    protected ServerComputer createComputer(int instanceID, int id) {
        ComputerFamily family = this.getFamily();
        ServerComputer computer = new ServerComputer(this.worldObj, id, this.m_label, instanceID, family, 51, 19);
        computer.setPosition(this.xCoord, this.yCoord, this.zCoord);
        return computer;
    }

    @Override
    public void getDroppedItems(List<ItemStack> drops, int fortune, boolean creative, boolean silkTouch) {
        IComputer computer = this.getComputer();
        if (!creative || computer != null && computer.getLabel() != null) {
            drops.add(ComputerItemFactory.create(this));
        }
    }

    @Override
    public ItemStack getPickedItem() {
        return ComputerItemFactory.create(this);
    }

    @Override
    public void openGUI(EntityPlayer player) {
        ComputerCraft.openComputerGUI(player, this);
    }

    @Override
    public IIcon getTexture(int side) {
        IComputer computer = this.getComputer();
        return getTexture(
            side,
            this.getFamily(),
            computer != null ? computer.isOn() : false,
            computer != null ? computer.isCursorDisplayed() : false,
            this.getDirection());
    }

    @Override
    public final void readDescription(NBTTagCompound nbttagcompound) {
        super.readDescription(nbttagcompound);
        this.updateBlock();
    }

    public boolean isUseableByPlayer(EntityPlayer player) {
        return this.isUsable(player, false);
    }

    @Override
    public int getDirection() {
        int metadata = this.getMetadata();
        return metadata & 7;
    }

    @Override
    public void setDirection(int dir) {
        int metadata = this.getMetadata();
        if (metadata < 8) {
            this.setMetadata(dir);
        } else {
            this.setMetadata(dir + 8);
        }

        this.updateInput();
    }

    @Override
    protected int remapLocalSide(int localSide) {
        return s_remapSide[localSide];
    }
}
