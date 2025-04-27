package dan200.computercraft.shared.computer.items;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.computer.blocks.IComputerTile;
import dan200.computercraft.shared.computer.core.ComputerFamily;

public class ItemComputer extends ItemComputerBase {

    public static int HIGHEST_DAMAGE_VALUE_ID = 16382;

    public ItemComputer(Block block) {
        super(block);
        this.setMaxStackSize(64);
        this.setHasSubtypes(true);
        this.setUnlocalizedName("computercraft:computer");
        this.setCreativeTab(ComputerCraft.mainCreativeTab);
    }

    public ItemStack create(int id, String label, ComputerFamily family) {
        if (family != ComputerFamily.Normal && family != ComputerFamily.Advanced) {
            return null;
        } else {
            int damage = 0;
            if (id >= 0 && id <= HIGHEST_DAMAGE_VALUE_ID) {
                damage = id + 1;
            }

            if (family == ComputerFamily.Advanced) {
                damage += 16384;
            }

            ItemStack result = new ItemStack(this, 1, damage);
            if (id > HIGHEST_DAMAGE_VALUE_ID) {
                NBTTagCompound nbt = new NBTTagCompound();
                nbt.setInteger("computerID", id);
                result.setTagCompound(nbt);
            }

            if (label != null) {
                result.setStackDisplayName(label);
            }

            return result;
        }
    }

    public void getSubItems(Item itemID, CreativeTabs tabs, List list) {
        list.add(ComputerItemFactory.create(-1, null, ComputerFamily.Normal));
        list.add(ComputerItemFactory.create(-1, null, ComputerFamily.Advanced));
    }

    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ, int metadata) {
        if (super.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, metadata)) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (tile != null && tile instanceof IComputerTile) {
                IComputerTile computer = (IComputerTile) tile;
                this.setupComputerAfterPlacement(stack, computer);
            }

            return true;
        } else {
            return false;
        }
    }

    private void setupComputerAfterPlacement(ItemStack stack, IComputerTile computer) {
        int id = this.getComputerID(stack);
        if (id >= 0) {
            computer.setComputerID(id);
        }

        String label = this.getLabel(stack);
        if (label != null) {
            computer.setLabel(label);
        }
    }

    public String getUnlocalizedName(ItemStack stack) {
        switch (this.getFamily(stack)) {
            case Normal:
            default:
                return "tile.computercraft:computer";
            case Advanced:
                return "tile.computercraft:advanced_computer";
            case Command:
                return "tile.computercraft:command_computer";
        }
    }

    @Override
    public int getComputerID(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound()
            .hasKey("computerID")) {
            return stack.getTagCompound()
                .getInteger("computerID");
        } else {
            int damage = stack.getItemDamage() & 16383;
            return damage - 1;
        }
    }

    @Override
    public ComputerFamily getFamily(int damage) {
        return (damage & 16384) != 0 ? ComputerFamily.Advanced : ComputerFamily.Normal;
    }
}
