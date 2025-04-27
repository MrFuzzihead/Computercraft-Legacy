package dan200.computercraft.shared.peripheral.common;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Facing;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.modem.TileCable;

public class ItemCable extends ItemPeripheralBase {

    public ItemCable(Block block) {
        super(block);
        this.setUnlocalizedName("computercraft:cable");
        this.setCreativeTab(ComputerCraft.mainCreativeTab);
    }

    public ItemStack create(PeripheralType type, String label, int quantity) {
        ItemStack stack;
        switch (type) {
            case Cable:
                stack = new ItemStack(this, quantity, 0);
                break;
            case WiredModem:
                stack = new ItemStack(this, quantity, 1);
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
        list.add(PeripheralItemFactory.create(PeripheralType.WiredModem, null, 1));
        list.add(PeripheralItemFactory.create(PeripheralType.Cable, null, 1));
    }

    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float fx,
        float fy, float fz) {
        if (!this.func_150936_a(world, x, y, z, side, player, stack)) {
            return false;
        } else {
            PeripheralType type = this.getPeripheralType(stack);
            Block existing = world.getBlock(x, y, z);
            if (existing == ComputerCraft.Blocks.cable) {
                PeripheralType existingType = ComputerCraft.Blocks.cable.getPeripheralType(world, x, y, z);
                if (existingType == PeripheralType.WiredModem && type == PeripheralType.Cable) {
                    if (stack.stackSize > 0) {
                        int existingDirection = ComputerCraft.Blocks.cable.getDirection(world, x, y, z);
                        world.setBlockMetadataWithNotify(x, y, z, existingDirection + 6, 3);
                        world.playSoundEffect(
                            x + 0.5,
                            y + 0.5,
                            z + 0.5,
                            ComputerCraft.Blocks.cable.stepSound.getBreakSound(),
                            (ComputerCraft.Blocks.cable.stepSound.getVolume() + 1.0F) / 2.0F,
                            ComputerCraft.Blocks.cable.stepSound.getPitch() * 0.8F);
                        stack.stackSize--;
                        TileEntity tile = world.getTileEntity(x, y, z);
                        if (tile != null && tile instanceof TileCable) {
                            TileCable cable = (TileCable) tile;
                            cable.networkChanged();
                        }

                        return true;
                    }

                    return false;
                }
            }

            if (existing != Blocks.air && (type == PeripheralType.Cable
                || existing.isSideSolid(world, x, y, z, ForgeDirection.getOrientation(side)))) {
                int offsetX = x + Facing.offsetsXForSide[side];
                int offsetY = y + Facing.offsetsYForSide[side];
                int offsetZ = z + Facing.offsetsZForSide[side];
                Block offsetExisting = world.getBlock(offsetX, offsetY, offsetZ);
                if (offsetExisting == ComputerCraft.Blocks.cable) {
                    PeripheralType offsetExistingType = ComputerCraft.Blocks.cable
                        .getPeripheralType(world, offsetX, offsetY, offsetZ);
                    if (offsetExistingType == PeripheralType.Cable && type == PeripheralType.WiredModem) {
                        if (stack.stackSize > 0) {
                            int direction = Facing.oppositeSide[side];
                            world.setBlockMetadataWithNotify(offsetX, offsetY, offsetZ, direction + 6, 3);
                            world.playSoundEffect(
                                offsetX + 0.5,
                                offsetY + 0.5,
                                offsetZ + 0.5,
                                ComputerCraft.Blocks.cable.stepSound.getBreakSound(),
                                (ComputerCraft.Blocks.cable.stepSound.getVolume() + 1.0F) / 2.0F,
                                ComputerCraft.Blocks.cable.stepSound.getPitch() * 0.8F);
                            stack.stackSize--;
                            TileEntity tile = world.getTileEntity(offsetX, offsetY, offsetZ);
                            if (tile != null && tile instanceof TileCable) {
                                TileCable cable = (TileCable) tile;
                                cable.networkChanged();
                            }

                            return true;
                        }

                        return false;
                    }

                    if (offsetExistingType == PeripheralType.WiredModem && type == PeripheralType.Cable) {
                        if (stack.stackSize > 0) {
                            int offsetExistingDirection = ComputerCraft.Blocks.cable
                                .getDirection(world, offsetX, offsetY, offsetZ);
                            world.setBlockMetadataWithNotify(offsetX, offsetY, offsetZ, offsetExistingDirection + 6, 3);
                            world.playSoundEffect(
                                offsetX + 0.5,
                                offsetY + 0.5,
                                offsetZ + 0.5,
                                ComputerCraft.Blocks.cable.stepSound.getBreakSound(),
                                (ComputerCraft.Blocks.cable.stepSound.getVolume() + 1.0F) / 2.0F,
                                ComputerCraft.Blocks.cable.stepSound.getPitch() * 0.8F);
                            stack.stackSize--;
                            TileEntity tile = world.getTileEntity(offsetX, offsetY, offsetZ);
                            if (tile != null && tile instanceof TileCable) {
                                TileCable cable = (TileCable) tile;
                                cable.networkChanged();
                            }

                            return true;
                        }

                        return false;
                    }
                }
            }

            return super.onItemUse(stack, player, world, x, y, z, side, fx, fy, fz);
        }
    }

    @Override
    public PeripheralType getPeripheralType(int damage) {
        switch (damage) {
            case 0:
            default:
                return PeripheralType.Cable;
            case 1:
                return PeripheralType.WiredModem;
        }
    }
}
