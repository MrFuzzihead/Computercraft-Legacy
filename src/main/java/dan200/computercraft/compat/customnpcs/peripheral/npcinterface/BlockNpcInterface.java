package dan200.computercraft.compat.customnpcs.peripheral.npcinterface;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.common.BlockGeneric;
import dan200.computercraft.shared.common.TileGeneric;
import dan200.computercraft.shared.util.DirectionUtil;

/**
 * Block class for the NPC Interface peripheral.
 *
 * <p>
 * Mirrors {@link dan200.computercraft.shared.peripheral.chatbox.BlockChatBox}.
 * </p>
 */
public class BlockNpcInterface extends BlockGeneric {

    // 0 = top/bottom, 1 = side, 2 = front
    private static final IIcon[] s_icons = new IIcon[3];

    @SideOnly(Side.CLIENT)
    public static void registerIcons(IIconRegister iconRegister) {
        s_icons[0] = iconRegister.registerIcon("computercraft:npcInterfaceTop");
        s_icons[1] = iconRegister.registerIcon("computercraft:npcInterfaceSide");
        s_icons[2] = iconRegister.registerIcon("computercraft:npcInterfaceFront");
    }

    public static IIcon getNpcInterfaceIcon(int side, int direction) {
        if (side == 0 || side == 1) return s_icons[0]; // top / bottom
        if (side == direction) return s_icons[2]; // front face
        return s_icons[1]; // side faces
    }

    public BlockNpcInterface() {
        super(Material.iron);
        this.setHardness(2.0F);
        this.setBlockName("computercraft:npc_interface");
        this.setCreativeTab(ComputerCraft.mainCreativeTab);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister iconRegister) {
        super.registerBlockIcons(iconRegister);
        registerIcons(iconRegister);
    }

    @Override
    protected int getDefaultMetadata(int damage, int placedSide) {
        return 0;
    }

    @Override
    protected TileGeneric createTile(int metadata) {
        return new TileNpcInterface();
    }

    @Override
    protected IIcon getItemTexture(int damage, int side) {
        if (side == 0 || side == 1) return s_icons[0];
        if (side == 4) return s_icons[2];
        return s_icons[1];
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack stack) {
        int dir = DirectionUtil.fromEntityRot(entity);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileNpcInterface) {
            ((TileNpcInterface) tile).setDirection(dir);
        }
    }
}
