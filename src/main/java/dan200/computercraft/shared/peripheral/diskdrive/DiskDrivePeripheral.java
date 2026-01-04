package dan200.computercraft.shared.peripheral.diskdrive;

import dan200.computercraft.api.peripheral.IPeripheralTargeted;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.media.IMedia;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.media.items.ItemDiskLegacy;

public class DiskDrivePeripheral implements IPeripheralTargeted {

    private final TileDiskDrive m_diskDrive;

    public DiskDrivePeripheral(TileDiskDrive diskDrive) {
        this.m_diskDrive = diskDrive;
    }

    @Override
    public String getType() {
        return "drive";
    }

    @Override
    public Object getTarget() {
        return m_diskDrive;
    }

    @Override
    public String[] getMethodNames() {
        return new String[] { "isDiskPresent", "getDiskLabel", "setDiskLabel", "hasData", "getMountPath", "hasAudio",
            "getAudioTitle", "playAudio", "stopAudio", "ejectDisk", "getDiskID" };
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException {
        switch (method) {
            case 0:
                return new Object[] { this.m_diskDrive.getDiskStack() != null };
            case 1:
                IMedia media = this.m_diskDrive.getDiskMedia();
                if (media != null) {
                    return new Object[] { media.getLabel(this.m_diskDrive.getDiskStack()) };
                }

                return null;
            case 2:
                String label = null;
                if (arguments.length > 0) {
                    if (arguments[0] != null && !(arguments[0] instanceof String)) {
                        throw new LuaException("Expected string");
                    }

                    label = (String) arguments[0];
                }

                IMedia media2 = this.m_diskDrive.getDiskMedia();
                if (media2 != null) {
                    ItemStack disk = this.m_diskDrive.getDiskStack();
                    if (!media2.setLabel(disk, label)) {
                        throw new LuaException("Disk label cannot be changed");
                    }

                    this.m_diskDrive.setDiskStack(disk);
                }

                return null;
            case 3:
                return new Object[] { this.m_diskDrive.getDiskMountPath(computer) != null };
            case 4:
                return new Object[] { this.m_diskDrive.getDiskMountPath(computer) };
            case 5:
                IMedia media5 = this.m_diskDrive.getDiskMedia();
                if (media5 != null) {
                    return new Object[] { media5.getAudioRecordName(this.m_diskDrive.getDiskStack()) != null };
                }

                return new Object[] { false };
            case 6:
                IMedia media6 = this.m_diskDrive.getDiskMedia();
                if (media6 != null) {
                    return new Object[] { media6.getAudioTitle(this.m_diskDrive.getDiskStack()) };
                }

                return new Object[] { false };
            case 7:
                this.m_diskDrive.playDiskAudio();
                return null;
            case 8:
                this.m_diskDrive.stopDiskAudio();
                return null;
            case 9:
                this.m_diskDrive.ejectDisk();
                return null;
            case 10:
                ItemStack disk = this.m_diskDrive.getDiskStack();
                if (disk != null) {
                    Item item = disk.getItem();
                    if (item instanceof ItemDiskLegacy) {
                        return new Object[] { ((ItemDiskLegacy) item).getDiskID(disk) };
                    }
                }

                return null;
            default:
                return null;
        }
    }

    @Override
    public void attach(IComputerAccess computer) {
        this.m_diskDrive.mount(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        this.m_diskDrive.unmount(computer);
    }

    @Override
    public boolean equals(IPeripheral other) {
        if (other instanceof DiskDrivePeripheral) {
            DiskDrivePeripheral otherDiskDrive = (DiskDrivePeripheral) other;
            if (otherDiskDrive.m_diskDrive == this.m_diskDrive) {
                return true;
            }
        }

        return false;
    }
}
