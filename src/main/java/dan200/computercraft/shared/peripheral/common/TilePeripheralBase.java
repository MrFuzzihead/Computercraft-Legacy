package dan200.computercraft.shared.peripheral.common;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.common.IDirectionalTile;
import dan200.computercraft.shared.common.TileGeneric;
import dan200.computercraft.shared.peripheral.PeripheralType;

public abstract class TilePeripheralBase extends TileGeneric implements IPeripheralTile, IDirectionalTile {

    private final IIcon[] m_iconList;
    private int m_dir;
    private int m_anim;
    private boolean m_changed;
    private String m_label;

    protected static IIcon getItemTexture(int side, IIcon[] icons) {
        return getTexture(side, icons, 0, 3);
    }

    private static IIcon getTexture(int side, IIcon[] icons, int anim, int direction) {
        if (side == 0 || side == 1) {
            return icons[0];
        } else if (side == direction) {
            return anim >= 0 && anim < icons.length - 2 ? icons[2 + anim] : icons[2];
        } else {
            return icons[1];
        }
    }

    public TilePeripheralBase(IIcon[] iconList) {
        this.m_iconList = iconList;
        this.m_dir = 2;
        this.m_anim = 0;
        this.m_changed = false;
        this.m_label = null;
    }

    public BlockPeripheralBase getBlock() {
        return (BlockPeripheralBase) super.getBlock();
    }

    @Override
    public void getDroppedItems(List<ItemStack> drops, int fortune, boolean creative, boolean silkTouch) {
        if (!creative) {
            drops.add(PeripheralItemFactory.create(this));
        }
    }

    @Override
    public ItemStack getPickedItem() {
        return PeripheralItemFactory.create(this);
    }

    @Override
    public final PeripheralType getPeripheralType() {
        return this.getBlock()
            .getPeripheralType(this.getMetadata());
    }

    @Override
    public IPeripheral getPeripheral(int side) {
        return null;
    }

    @Override
    public String getLabel() {
        return this.m_label != null && this.m_label.length() > 0 ? this.m_label : null;
    }

    public void setLabel(String label) {
        this.m_label = label;
    }

    @Override
    public int getDirection() {
        return this.m_dir;
    }

    @Override
    public void setDirection(int dir) {
        if (dir != this.m_dir) {
            this.m_dir = dir;
            this.m_changed = true;
        }
    }

    @Override
    public IIcon getTexture(int side) {
        return getTexture(side, this.m_iconList, this.getAnim(), this.getDirection());
    }

    public synchronized int getAnim() {
        return this.m_anim;
    }

    public synchronized void setAnim(int anim) {
        if (anim != this.m_anim) {
            this.m_anim = anim;
            this.m_changed = true;
        }
    }

    public synchronized void updateEntity() {
        if (this.m_changed) {
            this.updateBlock();
            this.m_changed = false;
        }
    }

    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        if (nbttagcompound.hasKey("dir")) {
            this.m_dir = nbttagcompound.getInteger("dir");
        }

        if (nbttagcompound.hasKey("anim")) {
            this.m_anim = nbttagcompound.getInteger("anim");
        }

        if (nbttagcompound.hasKey("label")) {
            this.m_label = nbttagcompound.getString("label");
        }
    }

    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        nbttagcompound.setInteger("dir", this.m_dir);
        nbttagcompound.setInteger("anim", this.m_anim);
        if (this.m_label != null) {
            nbttagcompound.setString("label", this.m_label);
        }
    }

    @Override
    public void readDescription(NBTTagCompound nbttagcompound) {
        super.readDescription(nbttagcompound);
        this.m_dir = nbttagcompound.getInteger("dir");
        this.m_anim = nbttagcompound.getInteger("anim");
        if (nbttagcompound.hasKey("label")) {
            this.m_label = nbttagcompound.getString("label");
        } else {
            this.m_label = null;
        }
    }

    @Override
    public void writeDescription(NBTTagCompound nbttagcompound) {
        super.writeDescription(nbttagcompound);
        nbttagcompound.setInteger("dir", this.m_dir);
        nbttagcompound.setInteger("anim", this.m_anim);
        if (this.m_label != null) {
            nbttagcompound.setString("label", this.m_label);
        }
    }
}
