package dan200.computercraft.shared.peripheral.diskdrive;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.media.IMedia;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.peripheral.common.TilePeripheralBase;
import dan200.computercraft.shared.util.InventoryUtil;

public class TileDiskDrive extends TilePeripheralBase implements IInventory {

    private static final IIcon[] s_icons = new IIcon[5];
    private static final int BLOCKEVENT_PLAY_RECORD = 0;
    private static final int BLOCKEVENT_STOP_RECORD = 1;
    private final Map<IComputerAccess, TileDiskDrive.MountInfo> m_computers = new HashMap<>();
    private ItemStack m_diskStack = null;
    private IMount m_diskMount = null;
    private boolean m_recordQueued = false;
    private boolean m_recordPlaying = false;
    private boolean m_restartRecord = false;
    private boolean m_ejectQueued;

    @SideOnly(Side.CLIENT)
    public static void registerIcons(IIconRegister iconRegister) {
        s_icons[0] = iconRegister.registerIcon("computercraft:diskDriveTop");
        s_icons[1] = iconRegister.registerIcon("computercraft:diskDriveSide");
        s_icons[2] = iconRegister.registerIcon("computercraft:diskDriveFront");
        s_icons[3] = iconRegister.registerIcon("computercraft:diskDriveFrontRejected");
        s_icons[4] = iconRegister.registerIcon("computercraft:diskDriveFrontAccepted");
    }

    public static IIcon getItemTexture(int side) {
        return getItemTexture(side, s_icons);
    }

    public TileDiskDrive() {
        super(s_icons);
    }

    @Override
    public void destroy() {
        this.ejectContents(true);
        synchronized (this) {
            if (this.m_recordPlaying) {
                this.sendBlockEvent(1);
            }
        }
    }

    @Override
    public boolean onActivate(EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) {
            if (!this.worldObj.isRemote) {
                ItemStack disk = player.getCurrentEquippedItem();
                if (disk != null && this.getStackInSlot(0) == null && ComputerCraft.getMedia(disk) != null) {
                    this.setInventorySlotContents(0, disk);
                    player.destroyCurrentEquippedItem();
                    return true;
                }
            }

            return false;
        } else {
            if (!this.worldObj.isRemote) {
                ComputerCraft.openDiskDriveGUI(player, this);
            }

            return true;
        }
    }

    @Override
    public int getDirection() {
        int metadata = this.getMetadata();
        return metadata >= 2 && metadata < 6 ? metadata : 2;
    }

    @Override
    public void setDirection(int dir) {
        if (dir < 2 || dir >= 6) {
            dir = 2;
        }

        this.setMetadata(dir);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        if (nbttagcompound.hasKey("item")) {
            NBTTagCompound item = nbttagcompound.getCompoundTag("item");
            this.m_diskStack = ItemStack.loadItemStackFromNBT(item);
            this.m_diskMount = null;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        if (this.m_diskStack != null) {
            NBTTagCompound item = new NBTTagCompound();
            this.m_diskStack.writeToNBT(item);
            nbttagcompound.setTag("item", item);
        }
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        synchronized (this) {
            if (this.m_ejectQueued) {
                this.ejectContents(false);
                this.m_ejectQueued = false;
            }
        }

        synchronized (this) {
            if (this.m_recordPlaying != this.m_recordQueued || this.m_restartRecord) {
                this.m_restartRecord = false;
                if (this.m_recordQueued) {
                    IMedia contents = this.getDiskMedia();
                    String record = contents != null ? contents.getAudioRecordName(this.m_diskStack) : null;
                    if (record != null) {
                        this.m_recordPlaying = true;
                        this.sendBlockEvent(0);
                    } else {
                        this.m_recordQueued = false;
                    }
                } else {
                    this.sendBlockEvent(1);
                    this.m_recordPlaying = false;
                }
            }
        }
    }

    public int getSizeInventory() {
        return 1;
    }

    public ItemStack getStackInSlot(int i) {
        return this.m_diskStack;
    }

    public ItemStack getStackInSlotOnClosing(int i) {
        ItemStack result = this.m_diskStack;
        this.m_diskStack = null;
        this.m_diskMount = null;
        return result;
    }

    public ItemStack decrStackSize(int i, int j) {
        if (this.m_diskStack == null) {
            return null;
        } else if (this.m_diskStack.stackSize <= j) {
            ItemStack disk = this.m_diskStack;
            this.setInventorySlotContents(0, null);
            return disk;
        } else {
            ItemStack part = this.m_diskStack.splitStack(j);
            if (this.m_diskStack.stackSize == 0) {
                this.setInventorySlotContents(0, null);
            } else {
                this.setInventorySlotContents(0, this.m_diskStack);
            }

            return part;
        }
    }

    public void setInventorySlotContents(int i, ItemStack itemStack) {
        if (this.worldObj.isRemote) {
            this.m_diskStack = itemStack;
            this.m_diskMount = null;
            this.markDirty();
        } else {
            synchronized (this) {
                if (InventoryUtil.areItemsStackable(itemStack, this.m_diskStack)) {
                    this.m_diskStack = itemStack;
                } else {
                    if (this.m_diskStack != null) {
                        for (IComputerAccess computer : this.m_computers.keySet()) {
                            this.unmountDisk(computer);
                        }
                    }

                    if (this.m_recordPlaying) {
                        this.sendBlockEvent(1);
                        this.m_recordPlaying = false;
                        this.m_recordQueued = false;
                    }

                    this.m_diskStack = itemStack;
                    this.m_diskMount = null;
                    this.markDirty();
                    this.updateAnim();
                    if (this.m_diskStack != null) {
                        for (IComputerAccess computer : this.m_computers.keySet()) {
                            this.mountDisk(computer);
                        }
                    }
                }
            }
        }
    }

    public String getInventoryName() {
        return this.hasCustomInventoryName() ? this.getLabel() : "tile.computercraft:drive.name";
    }

    public int getInventoryStackLimit() {
        return 64;
    }

    public void openInventory() {}

    public void closeInventory() {}

    public boolean hasCustomInventoryName() {
        return this.getLabel() != null;
    }

    public boolean isItemValidForSlot(int i, ItemStack itemstack) {
        return true;
    }

    public boolean isUseableByPlayer(EntityPlayer player) {
        return this.isUsable(player, false);
    }

    @Override
    public IPeripheral getPeripheral(int side) {
        return new DiskDrivePeripheral(this);
    }

    public ItemStack getDiskStack() {
        synchronized (this) {
            return this.getStackInSlot(0);
        }
    }

    public void setDiskStack(ItemStack stack) {
        synchronized (this) {
            this.setInventorySlotContents(0, stack);
        }
    }

    public IMedia getDiskMedia() {
        return ComputerCraft.getMedia(this.getDiskStack());
    }

    public String getDiskMountPath(IComputerAccess computer) {
        synchronized (this) {
            if (this.m_computers.containsKey(computer)) {
                TileDiskDrive.MountInfo info = this.m_computers.get(computer);
                return info.mountPath;
            } else {
                return null;
            }
        }
    }

    public void mount(IComputerAccess computer) {
        synchronized (this) {
            this.m_computers.put(computer, new TileDiskDrive.MountInfo());
            this.mountDisk(computer);
        }
    }

    public void unmount(IComputerAccess computer) {
        synchronized (this) {
            this.unmountDisk(computer);
            this.m_computers.remove(computer);
        }
    }

    public void playDiskAudio() {
        synchronized (this) {
            IMedia media = this.getDiskMedia();
            if (media != null && media.getAudioTitle(this.m_diskStack) != null) {
                this.m_recordQueued = true;
                this.m_restartRecord = this.m_recordPlaying;
            }
        }
    }

    public void stopDiskAudio() {
        synchronized (this) {
            this.m_recordQueued = false;
            this.m_restartRecord = false;
        }
    }

    public void ejectDisk() {
        synchronized (this) {
            if (!this.m_ejectQueued) {
                this.m_ejectQueued = true;
            }
        }
    }

    private synchronized void mountDisk(IComputerAccess computer) {
        if (this.m_diskStack != null) {
            TileDiskDrive.MountInfo info = this.m_computers.get(computer);
            IMedia contents = this.getDiskMedia();
            if (contents != null) {
                if (this.m_diskMount == null) {
                    this.m_diskMount = contents.createDataMount(this.m_diskStack, this.worldObj);
                }

                if (this.m_diskMount != null) {
                    if (this.m_diskMount instanceof IWritableMount) {
                        for (int n = 1; info.mountPath == null; n++) {
                            info.mountPath = computer
                                .mountWritable(n == 1 ? "disk" : "disk" + n, (IWritableMount) this.m_diskMount);
                        }
                    } else {
                        for (int n = 1; info.mountPath == null; n++) {
                            info.mountPath = computer.mount(n == 1 ? "disk" : "disk" + n, this.m_diskMount);
                        }
                    }
                } else {
                    info.mountPath = null;
                }
            }

            computer.queueEvent("disk", new Object[] { computer.getAttachmentName() });
        }
    }

    private synchronized void unmountDisk(IComputerAccess computer) {
        if (this.m_diskStack != null) {
            TileDiskDrive.MountInfo info = this.m_computers.get(computer);

            assert info != null;

            if (info.mountPath != null) {
                computer.unmount(info.mountPath);
                info.mountPath = null;
            }

            computer.queueEvent("disk_eject", new Object[] { computer.getAttachmentName() });
        }
    }

    private synchronized void updateAnim() {
        if (this.m_diskStack != null) {
            IMedia contents = this.getDiskMedia();
            if (contents != null) {
                this.setAnim(2);
            } else {
                this.setAnim(1);
            }
        } else {
            this.setAnim(0);
        }
    }

    private synchronized void ejectContents(boolean destroyed) {
        if (!this.worldObj.isRemote) {
            if (this.m_diskStack != null) {
                ItemStack disks = this.m_diskStack;
                this.setInventorySlotContents(0, null);
                int xOff = 0;
                int zOff = 0;
                if (!destroyed) {
                    int dir = this.getDirection();
                    switch (dir) {
                        case 2:
                            zOff = -1;
                            break;
                        case 3:
                            zOff = 1;
                            break;
                        case 4:
                            xOff = -1;
                            break;
                        case 5:
                            xOff = 1;
                    }
                }

                double x = this.xCoord + 0.5 + xOff * 0.5;
                double y = this.yCoord + 0.75;
                double z = this.zCoord + 0.5 + zOff * 0.5;
                EntityItem entityitem = new EntityItem(this.worldObj, x, y, z, disks);
                entityitem.motionX = xOff * 0.15;
                entityitem.motionY = 0.0;
                entityitem.motionZ = zOff * 0.15;
                this.worldObj.spawnEntityInWorld(entityitem);
                if (!destroyed) {
                    this.worldObj.playAuxSFX(1000, this.xCoord, this.yCoord, this.zCoord, 0);
                }
            }
        }
    }

    @Override
    public final void readDescription(NBTTagCompound nbttagcompound) {
        super.readDescription(nbttagcompound);
        if (nbttagcompound.hasKey("item")) {
            this.m_diskStack = ItemStack.loadItemStackFromNBT(nbttagcompound.getCompoundTag("item"));
        } else {
            this.m_diskStack = null;
        }

        this.updateBlock();
    }

    @Override
    public void writeDescription(NBTTagCompound nbttagcompound) {
        super.writeDescription(nbttagcompound);
        if (this.m_diskStack != null) {
            NBTTagCompound item = new NBTTagCompound();
            this.m_diskStack.writeToNBT(item);
            nbttagcompound.setTag("item", item);
        }
    }

    @Override
    public void onBlockEvent(int eventID, int eventParameter) {
        super.onBlockEvent(eventID, eventParameter);
        switch (eventID) {
            case 0:
                this.playRecord();
                break;
            case 1:
                this.stopRecord();
        }
    }

    private void playRecord() {
        IMedia contents = this.getDiskMedia();
        String record = contents != null ? contents.getAudioRecordName(this.m_diskStack) : null;
        if (record != null) {
            ComputerCraft.playRecord(
                record,
                contents.getAudioTitle(this.m_diskStack),
                this.worldObj,
                this.xCoord,
                this.yCoord,
                this.zCoord);
        } else {
            ComputerCraft.playRecord(null, null, this.worldObj, this.xCoord, this.yCoord, this.zCoord);
        }
    }

    private void stopRecord() {
        ComputerCraft.playRecord(null, null, this.worldObj, this.xCoord, this.yCoord, this.zCoord);
    }

    private static class MountInfo {

        public String mountPath;

        private MountInfo() {}
    }
}
