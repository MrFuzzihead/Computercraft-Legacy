package dan200.computercraft.shared.turtle.blocks;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleUpgradeType;
import dan200.computercraft.shared.computer.blocks.TileComputerBase;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.core.IComputer;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.turtle.apis.TurtleAPI;
import dan200.computercraft.shared.turtle.core.TurtleBrain;
import dan200.computercraft.shared.turtle.items.TurtleItemFactory;
import dan200.computercraft.shared.util.Colour;
import dan200.computercraft.shared.util.InventoryUtil;
import dan200.computercraft.shared.util.RedstoneUtil;
import dan200.computercraft.shared.util.WorldUtil;

public class TileTurtle extends TileComputerBase implements ITurtleTile, IInventory {

    private static IIcon s_icon;
    private static IIcon s_iconAdvanced;
    public static final int INVENTORY_SIZE = 16;
    private ItemStack[] m_inventory = new ItemStack[16];
    private ItemStack[] m_previousInventory = new ItemStack[this.getSizeInventory()];
    private boolean m_inventoryChanged = false;
    private TurtleBrain m_brain = this.createBrain();
    private boolean m_moved = false;

    @SideOnly(Side.CLIENT)
    public static void registerIcons(IIconRegister iconRegister) {
        s_icon = iconRegister.registerIcon("computercraft:turtle");
        s_iconAdvanced = iconRegister.registerIcon("computercraft:turtleAdvanced");
    }

    public static IIcon getItemTexture(boolean advanced) {
        return advanced ? s_iconAdvanced : s_icon;
    }

    public boolean hasMoved() {
        return this.m_moved;
    }

    protected TurtleBrain createBrain() {
        return new TurtleBrain(this);
    }

    protected final ServerComputer createComputer(int instanceID, int id, int termWidth, int termHeight) {
        ServerComputer computer = new ServerComputer(
            this.worldObj,
            id,
            this.m_label,
            instanceID,
            this.getFamily(),
            termWidth,
            termHeight);
        computer.setPosition(this.xCoord, this.yCoord, this.zCoord);
        computer.addAPI(new TurtleAPI(computer.getAPIEnvironment(), this.getAccess()));
        this.m_brain.setupComputer(computer);
        return computer;
    }

    @Override
    protected ServerComputer createComputer(int instanceID, int id) {
        return this.createComputer(instanceID, id, 39, 13);
    }

    @Override
    public void destroy() {
        if (!this.hasMoved()) {
            super.destroy();
            if (!this.worldObj.isRemote) {
                int size = this.getSizeInventory();

                for (int i = 0; i < size; i++) {
                    ItemStack stack = this.getStackInSlot(i);
                    if (stack != null) {
                        WorldUtil.dropItemStack(stack, this.worldObj, this.xCoord, this.yCoord, this.zCoord);
                    }
                }
            }
        } else {
            for (int dir = 0; dir < 6; dir++) {
                RedstoneUtil.propogateRedstoneOutput(this.worldObj, this.xCoord, this.yCoord, this.zCoord, dir);
            }
        }
    }

    @Override
    protected void unload() {
        if (!this.hasMoved()) {
            super.unload();
        }
    }

    @Override
    public void getDroppedItems(List<ItemStack> drops, int fortune, boolean creative, boolean silkTouch) {
        IComputer computer = this.getComputer();
        if (!creative || computer != null && computer.getLabel() != null) {
            drops.add(TurtleItemFactory.create(this));
        }
    }

    @Override
    public ItemStack getPickedItem() {
        return TurtleItemFactory.create(this);
    }

    @Override
    public boolean onActivate(EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        this.requestTileEntityUpdate();
        ItemStack currentItem = player.getCurrentEquippedItem();
        if (currentItem != null) {
            if (currentItem.getItem() == Items.dye) {
                if (!this.worldObj.isRemote) {
                    int dye = currentItem.getItemDamage() & 15;
                    if (this.m_brain.getDyeColour() != dye) {
                        this.m_brain.setDyeColour(dye);
                        if (!player.capabilities.isCreativeMode) {
                            currentItem.stackSize--;
                        }
                    }
                }

                return true;
            }

            if (currentItem.getItem() == Items.water_bucket && this.m_brain.getDyeColour() != -1) {
                if (!this.worldObj.isRemote && this.m_brain.getDyeColour() != -1) {
                    this.m_brain.setDyeColour(-1);
                    if (!player.capabilities.isCreativeMode) {
                        currentItem.func_150996_a(Items.bucket);
                    }
                }

                return true;
            }
        }

        return super.onActivate(player, side, hitX, hitY, hitZ);
    }

    @Override
    protected boolean canNameWithTag(EntityPlayer player) {
        return true;
    }

    @Override
    public void openGUI(EntityPlayer player) {
        ComputerCraft.openTurtleGUI(player, this);
    }

    @Override
    public boolean isSolidOnSide(int side) {
        return false;
    }

    @Override
    public boolean isImmuneToExplosion(Entity exploder) {
        return exploder != null && (exploder instanceof EntityLivingBase || exploder instanceof EntityFireball);
    }

    @Override
    public AxisAlignedBB getBounds() {
        Vec3 offset = this.getRenderOffset(1.0F);
        return AxisAlignedBB.getBoundingBox(
            offset.xCoord + 0.125,
            offset.yCoord + 0.125,
            offset.zCoord + 0.125,
            offset.xCoord + 0.875,
            offset.yCoord + 0.875,
            offset.zCoord + 0.875);
    }

    @Override
    public IIcon getTexture(int side) {
        return getItemTexture(this.getFamily() == ComputerFamily.Advanced);
    }

    @Override
    protected double getInteractRange(EntityPlayer player) {
        return 12.0;
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        this.m_brain.update();
        synchronized (this.m_inventory) {
            if (!this.worldObj.isRemote && this.m_inventoryChanged) {
                IComputer computer = this.getComputer();
                if (computer != null) {
                    computer.queueEvent("turtle_inventory");
                }

                this.m_inventoryChanged = false;

                for (int n = 0; n < this.getSizeInventory(); n++) {
                    this.m_previousInventory[n] = InventoryUtil.copyItem(this.getStackInSlot(n));
                }
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        NBTTagList nbttaglist = nbttagcompound.getTagList("Items", 10);
        this.m_inventory = new ItemStack[16];
        this.m_previousInventory = new ItemStack[this.getSizeInventory()];

        for (int i = 0; i < nbttaglist.tagCount(); i++) {
            NBTTagCompound itemtag = nbttaglist.getCompoundTagAt(i);
            int slot = itemtag.getByte("Slot") & 255;
            if (slot >= 0 && slot < this.getSizeInventory()) {
                this.m_inventory[slot] = ItemStack.loadItemStackFromNBT(itemtag);
                this.m_previousInventory[slot] = InventoryUtil.copyItem(this.m_inventory[slot]);
            }
        }

        this.m_brain.readFromNBT(nbttagcompound);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        NBTTagList nbttaglist = new NBTTagList();

        for (int i = 0; i < 16; i++) {
            if (this.m_inventory[i] != null) {
                NBTTagCompound itemtag = new NBTTagCompound();
                itemtag.setByte("Slot", (byte) i);
                this.m_inventory[i].writeToNBT(itemtag);
                nbttaglist.appendTag(itemtag);
            }
        }

        nbttagcompound.setTag("Items", nbttaglist);
        this.m_brain.writeToNBT(nbttagcompound);
    }

    @Override
    protected boolean isPeripheralBlockedOnSide(int localSide) {
        return this.hasPeripheralUpgradeOnSide(localSide);
    }

    @Override
    protected boolean isRedstoneBlockedOnSide(int localSide) {
        return this.hasPeripheralUpgradeOnSide(localSide);
    }

    @Override
    public int getDirection() {
        return this.m_brain.getDirection();
    }

    @Override
    public void setDirection(int dir) {
        this.m_brain.setDirection(dir);
    }

    @Override
    public ITurtleUpgrade getUpgrade(TurtleSide side) {
        return this.m_brain.getUpgrade(side);
    }

    @Override
    public Colour getColour() {
        int dye = this.m_brain.getDyeColour();
        return dye >= 0 ? Colour.values()[dye] : null;
    }

    @Override
    public ResourceLocation getOverlay() {
        return this.m_brain.getOverlay();
    }

    @Override
    public ResourceLocation getHatOverlay() {
        return this.m_brain.getHatOverlay();
    }

    @Override
    public ITurtleAccess getAccess() {
        return this.m_brain;
    }

    @Override
    public Vec3 getRenderOffset(float f) {
        return this.m_brain.getRenderOffset(f);
    }

    @Override
    public float getRenderYaw(float f) {
        return this.m_brain.getVisualYaw(f);
    }

    @Override
    public float getToolRenderAngle(TurtleSide side, float f) {
        return this.m_brain.getToolRenderAngle(side, f);
    }

    public int getSizeInventory() {
        return 16;
    }

    public ItemStack getStackInSlot(int slot) {
        if (slot >= 0 && slot < 16) {
            synchronized (this.m_inventory) {
                return this.m_inventory[slot];
            }
        } else {
            return null;
        }
    }

    public ItemStack getStackInSlotOnClosing(int slot) {
        synchronized (this.m_inventory) {
            ItemStack result = this.getStackInSlot(slot);
            this.setInventorySlotContents(slot, null);
            return result;
        }
    }

    public ItemStack decrStackSize(int slot, int count) {
        if (count == 0) {
            return null;
        } else {
            synchronized (this.m_inventory) {
                ItemStack stack = this.getStackInSlot(slot);
                if (stack == null) {
                    return null;
                } else if (stack.stackSize <= count) {
                    this.setInventorySlotContents(slot, null);
                    return stack;
                } else {
                    ItemStack part = stack.splitStack(count);
                    this.onInventoryDefinitelyChanged();
                    return part;
                }
            }
        }
    }

    public void setInventorySlotContents(int i, ItemStack stack) {
        if (i >= 0 && i < 16) {
            synchronized (this.m_inventory) {
                if (!InventoryUtil.areItemsEqual(stack, this.m_inventory[i])) {
                    this.m_inventory[i] = stack;
                    this.onInventoryDefinitelyChanged();
                }
            }
        }
    }

    public String getInventoryName() {
        IComputer computer = this.getComputer();
        if (computer != null) {
            String label = computer.getLabel();
            if (label != null && label.length() > 0) {
                return label;
            }
        }

        return "tile.computercraft:turtle.name";
    }

    public int getInventoryStackLimit() {
        return 64;
    }

    public void openInventory() {}

    public void closeInventory() {}

    public boolean hasCustomInventoryName() {
        IComputer computer = this.getComputer();
        if (computer != null) {
            String label = computer.getLabel();
            if (label != null && label.length() > 0) {
                return true;
            }
        }

        return false;
    }

    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return true;
    }

    public void markDirty() {
        super.markDirty();
        synchronized (this.m_inventory) {
            if (!this.m_inventoryChanged) {
                for (int n = 0; n < this.getSizeInventory(); n++) {
                    if (!ItemStack.areItemStacksEqual(this.getStackInSlot(n), this.m_previousInventory[n])) {
                        this.m_inventoryChanged = true;
                        break;
                    }
                }
            }
        }
    }

    public boolean isUseableByPlayer(EntityPlayer player) {
        return this.isUsable(player, false);
    }

    public boolean isUseableByRemote(EntityPlayer player) {
        return this.isUsable(player, true);
    }

    public void onInventoryDefinitelyChanged() {
        super.markDirty();
        this.m_inventoryChanged = true;
    }

    public void onTileEntityChange() {
        super.markDirty();
    }

    @Override
    public void writeDescription(NBTTagCompound nbttagcompound) {
        super.writeDescription(nbttagcompound);
        this.m_brain.writeDescription(nbttagcompound);
    }

    @Override
    public void readDescription(NBTTagCompound nbttagcompound) {
        super.readDescription(nbttagcompound);
        this.m_brain.readDescription(nbttagcompound);
        this.updateBlock();
    }

    private boolean hasPeripheralUpgradeOnSide(int side) {
        ITurtleUpgrade upgrade;
        switch (side) {
            case 4:
                upgrade = this.getUpgrade(TurtleSide.Right);
                break;
            case 5:
                upgrade = this.getUpgrade(TurtleSide.Left);
                break;
            default:
                return false;
        }

        return upgrade != null && upgrade.getType() == TurtleUpgradeType.Peripheral;
    }

    public void transferStateFrom(TileTurtle copy) {
        super.transferStateFrom(copy);
        this.m_inventory = copy.m_inventory;
        this.m_previousInventory = copy.m_previousInventory;
        this.m_inventoryChanged = copy.m_inventoryChanged;
        this.m_brain = copy.m_brain;
        this.m_brain.setOwner(this);
        copy.m_moved = true;
    }
}
