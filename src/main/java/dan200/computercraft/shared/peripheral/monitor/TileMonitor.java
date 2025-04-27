package dan200.computercraft.shared.peripheral.monitor;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.shared.common.ClientTerminal;
import dan200.computercraft.shared.common.ITerminal;
import dan200.computercraft.shared.common.ITerminalTile;
import dan200.computercraft.shared.common.ServerTerminal;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.TilePeripheralBase;

public class TileMonitor extends TilePeripheralBase implements ITerminalTile {

    private static final IIcon[] s_icons = new IIcon[48];
    private static final IIcon[] s_advancedIcons = new IIcon[48];
    public static final float RENDER_BORDER = 0.125F;
    public static final float RENDER_MARGIN = 0.03125F;
    public static final float RENDER_PIXEL_SCALE = 0.015625F;
    private static final int MAX_WIDTH = 8;
    private static final int MAX_HEIGHT = 6;
    private ServerTerminal m_serverTerminal;
    private ClientTerminal m_clientTerminal;
    private final Set<IComputerAccess> m_computers = new HashSet<>();
    public int m_renderDisplayList;
    private boolean m_destroyed = false;
    private boolean m_ignoreMe = false;
    private boolean m_changed;
    private int m_textScale = 2;
    private int m_width = 1;
    private int m_height = 1;
    private int m_xIndex = 0;
    private int m_yIndex = 0;
    private int m_dir;
    private boolean m_sizeChangedQueued;

    @SideOnly(Side.CLIENT)
    public static void registerIcons(IIconRegister iconRegister) {
        for (int i = 0; i <= 7; i++) {
            s_icons[i] = iconRegister.registerIcon("computercraft:monitor" + i);
            s_advancedIcons[i] = iconRegister.registerIcon("computercraft:advMonitor" + i);
        }

        for (int i = 15; i <= 47; i++) {
            s_icons[i] = iconRegister.registerIcon("computercraft:monitor" + i);
            s_advancedIcons[i] = iconRegister.registerIcon("computercraft:advMonitor" + i);
        }
    }

    public static IIcon getItemTexture(int side, boolean advanced) {
        IIcon[] icons = advanced ? s_advancedIcons : s_icons;
        if (side == 1 || side == 0) {
            return icons[0];
        } else {
            return side == 3 ? icons[15] : icons[32];
        }
    }

    public TileMonitor() {
        super(s_icons);
        this.m_changed = false;
        this.m_dir = 2;
        this.m_renderDisplayList = -1;
    }

    @Override
    public void destroy() {
        if (!this.m_destroyed) {
            this.m_destroyed = true;
            if (!this.worldObj.isRemote) {
                this.contractNeighbours();
            }
        }
    }

    @Override
    public IIcon getTexture(int side) {
        IIcon[] texArray = this.getLocalTerminal()
            .isColour() ? s_advancedIcons : s_icons;
        int xPos = this.getXIndex();
        int yPos = this.getYIndex();
        int width = this.getWidth();
        int height = this.getHeight();
        int dir = this.getRenderFace();
        int realDir = this.getDir();
        int left;
        int right;
        switch (realDir % 6) {
            case 2:
            default:
                left = 4;
                right = 5;
                break;
            case 3:
                left = 5;
                right = 4;
                break;
            case 4:
                left = 3;
                right = 2;
                break;
            case 5:
                left = 2;
                right = 3;
        }

        if (side == dir) {
            return realDir != 8 && realDir != 9 && realDir != 10 && realDir != 11
                ? texArray[16 + this.getMonitorFaceTexture(xPos, yPos, width, height)]
                : texArray[16 + this.getMonitorFaceTexture(width - 1 - xPos, yPos, width, height)];
        } else if (side != Facing.oppositeSide[dir]) {
            if (side != left && side != right) {
                if (width == 1) {
                    return texArray[0];
                } else if (xPos == 0) {
                    return texArray[1];
                } else {
                    return xPos == width - 1 ? texArray[3] : texArray[2];
                }
            } else if (height == 1) {
                return texArray[4];
            } else if (yPos == 0) {
                return texArray[5];
            } else {
                return yPos == height - 1 ? texArray[7] : texArray[6];
            }
        } else {
            return realDir != 14 && realDir != 15 && realDir != 16 && realDir != 17
                ? texArray[32 + this.getMonitorFaceTexture(width - 1 - xPos, yPos, width, height)]
                : texArray[32 + this.getMonitorFaceTexture(xPos, yPos, width, height)];
        }
    }

    @Override
    public boolean onActivate(EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (!player.isSneaking() && this.getRenderFace() == side) {
            if (!this.worldObj.isRemote) {
                this.monitorTouched(hitX, hitY, hitZ);
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        nbttagcompound.setInteger("xIndex", this.m_xIndex);
        nbttagcompound.setInteger("yIndex", this.m_yIndex);
        nbttagcompound.setInteger("width", this.m_width);
        nbttagcompound.setInteger("height", this.m_height);
        nbttagcompound.setInteger("dir", this.m_dir);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        this.m_xIndex = nbttagcompound.getInteger("xIndex");
        this.m_yIndex = nbttagcompound.getInteger("yIndex");
        this.m_width = nbttagcompound.getInteger("width");
        this.m_height = nbttagcompound.getInteger("height");
        this.m_dir = nbttagcompound.getInteger("dir");
    }

    @Override
    public void updateEntity() {
        if (!this.worldObj.isRemote) {
            if (this.m_sizeChangedQueued) {
                for (IComputerAccess computer : this.m_computers) {
                    computer.queueEvent("monitor_resize", new Object[] { computer.getAttachmentName() });
                }

                this.m_sizeChangedQueued = false;
            }

            if (this.m_serverTerminal != null) {
                this.m_serverTerminal.update();
                if (this.m_serverTerminal.hasTerminalChanged()) {
                    this.updateBlock();
                }
            }

            if (this.m_clientTerminal != null) {
                this.m_clientTerminal.update();
            }
        }
    }

    public boolean pollChanged() {
        if (this.m_changed) {
            this.m_changed = false;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public IPeripheral getPeripheral(int side) {
        return new MonitorPeripheral(this);
    }

    public void setTextScale(int scale) {
        TileMonitor origin = this.getOrigin();
        if (origin != null) {
            synchronized (origin) {
                if (origin.m_textScale != scale) {
                    origin.m_textScale = scale;
                    origin.rebuildTerminal();
                    origin.updateBlock();
                }
            }
        }
    }

    @Override
    public void writeDescription(NBTTagCompound nbttagcompound) {
        super.writeDescription(nbttagcompound);
        nbttagcompound.setInteger("xIndex", this.m_xIndex);
        nbttagcompound.setInteger("yIndex", this.m_yIndex);
        nbttagcompound.setInteger("width", this.m_width);
        nbttagcompound.setInteger("height", this.m_height);
        nbttagcompound.setInteger("textScale", this.m_textScale);
        nbttagcompound.setInteger("monitorDir", this.m_dir);
        ((ServerTerminal) this.getLocalTerminal()).writeDescription(nbttagcompound);
    }

    @Override
    public final void readDescription(NBTTagCompound nbttagcompound) {
        super.readDescription(nbttagcompound);
        int oldXIndex = this.m_xIndex;
        int oldYIndex = this.m_yIndex;
        int oldWidth = this.m_width;
        int oldHeight = this.m_height;
        int oldTextScale = this.m_textScale;
        int oldDir = this.m_dir;
        this.m_xIndex = nbttagcompound.getInteger("xIndex");
        this.m_yIndex = nbttagcompound.getInteger("yIndex");
        this.m_width = nbttagcompound.getInteger("width");
        this.m_height = nbttagcompound.getInteger("height");
        this.m_textScale = nbttagcompound.getInteger("textScale");
        this.m_dir = nbttagcompound.getInteger("monitorDir");
        ((ClientTerminal) this.getLocalTerminal()).readDescription(nbttagcompound);
        this.m_changed = true;
        if (oldXIndex != this.m_xIndex || oldYIndex != this.m_yIndex
            || oldWidth != this.m_width
            || oldHeight != this.m_height
            || oldTextScale != this.m_textScale
            || oldDir != this.m_dir) {
            this.updateBlock();
        }
    }

    @Override
    public ITerminal getTerminal() {
        TileMonitor origin = this.getOrigin();
        return origin != null ? origin.getLocalTerminal() : null;
    }

    private ITerminal getLocalTerminal() {
        if (!this.worldObj.isRemote) {
            if (this.m_serverTerminal == null) {
                this.m_serverTerminal = new ServerTerminal(this.getPeripheralType() == PeripheralType.AdvancedMonitor);
            }

            return this.m_serverTerminal;
        } else {
            if (this.m_clientTerminal == null) {
                this.m_clientTerminal = new ClientTerminal(this.getPeripheralType() == PeripheralType.AdvancedMonitor);
            }

            return this.m_clientTerminal;
        }
    }

    public float getTextScale() {
        return this.m_textScale * 0.5F;
    }

    private void rebuildTerminal() {
        Terminal oldTerm = this.getTerminal()
            .getTerminal();
        int oldWidth = oldTerm != null ? oldTerm.getWidth() : -1;
        int oldHeight = oldTerm != null ? oldTerm.getHeight() : -1;
        float textScale = this.getTextScale();
        int termWidth = Math.max(Math.round((this.m_width - 0.3125F) / (textScale * 6.0F * 0.015625F)), 1);
        int termHeight = Math.max(Math.round((this.m_height - 0.3125F) / (textScale * 9.0F * 0.015625F)), 1);
        ((ServerTerminal) this.getLocalTerminal()).resize(termWidth, termHeight);
        if (oldWidth != termWidth || oldHeight != termHeight) {
            this.getLocalTerminal()
                .getTerminal()
                .clear();

            for (int y = 0; y < this.m_height; y++) {
                for (int x = 0; x < this.m_width; x++) {
                    TileMonitor monitor = this.getNeighbour(x, y);
                    if (monitor != null) {
                        monitor.queueSizeChangedEvent();
                    }
                }
            }
        }
    }

    private void destroyTerminal() {
        ((ServerTerminal) this.getLocalTerminal()).delete();
    }

    public int getRenderFace() {
        return this.m_dir <= 5 ? this.m_dir : (this.m_dir <= 11 ? 0 : 1);
    }

    public int getDir() {
        return this.m_dir;
    }

    public void setDir(int dir) {
        this.m_dir = dir;
        this.m_changed = true;
        this.markDirty();
    }

    public int getRight() {
        int dir = this.getDir() % 6;
        switch (dir) {
            case 2:
                return 4;
            case 3:
                return 5;
            case 4:
                return 3;
            case 5:
                return 2;
            default:
                return dir;
        }
    }

    private int getDown() {
        int dir = this.getDir();
        if (dir <= 5) {
            return 1;
        } else {
            switch (dir) {
                case 8:
                    return 2;
                case 9:
                    return 3;
                case 10:
                    return 4;
                case 11:
                    return 5;
                case 12:
                case 13:
                default:
                    return dir;
                case 14:
                    return 3;
                case 15:
                    return 2;
                case 16:
                    return 5;
                case 17:
                    return 4;
            }
        }
    }

    public int getWidth() {
        return this.m_width;
    }

    public int getHeight() {
        return this.m_height;
    }

    public int getXIndex() {
        return this.m_xIndex;
    }

    public int getYIndex() {
        return this.m_yIndex;
    }

    private TileMonitor getSimilarMonitorAt(int x, int y, int z) {
        if (y >= 0 && y < this.worldObj.getHeight() && this.worldObj.blockExists(x, y, z)) {
            TileEntity tile = this.worldObj.getTileEntity(x, y, z);
            if (tile != null && tile instanceof TileMonitor) {
                TileMonitor monitor = (TileMonitor) tile;
                if (monitor.getDir() == this.getDir() && monitor.getLocalTerminal()
                    .isColour()
                    == this.getLocalTerminal()
                        .isColour()
                    && !monitor.m_destroyed
                    && !monitor.m_ignoreMe) {
                    return monitor;
                }
            }
        }

        return null;
    }

    private TileMonitor getNeighbour(int x, int y) {
        int right = this.getRight();
        int down = this.getDown();
        int xOffset = -this.m_xIndex + x;
        int yOffset = -this.m_yIndex + y;
        return this.getSimilarMonitorAt(
            this.xCoord + Facing.offsetsXForSide[right] * xOffset + Facing.offsetsXForSide[down] * yOffset,
            this.yCoord + Facing.offsetsYForSide[right] * xOffset + Facing.offsetsYForSide[down] * yOffset,
            this.zCoord + Facing.offsetsZForSide[right] * xOffset + Facing.offsetsZForSide[down] * yOffset);
    }

    private TileMonitor getOrigin() {
        return this.getNeighbour(0, 0);
    }

    private void resize(int width, int height) {
        int right = this.getRight();
        int rightX = Facing.offsetsXForSide[right];
        int rightY = Facing.offsetsYForSide[right];
        int rightZ = Facing.offsetsZForSide[right];
        int down = this.getDown();
        int downX = Facing.offsetsXForSide[down];
        int downY = Facing.offsetsYForSide[down];
        int downZ = Facing.offsetsZForSide[down];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                TileMonitor monitor = this.getSimilarMonitorAt(
                    this.xCoord + rightX * x + downX * y,
                    this.yCoord + rightY * x + downY * y,
                    this.zCoord + rightZ * x + downZ * y);
                if (monitor != null) {
                    monitor.m_xIndex = x;
                    monitor.m_yIndex = y;
                    monitor.m_width = width;
                    monitor.m_height = height;
                    monitor.updateBlock();
                    if (x != 0 || y != 0) {
                        monitor.destroyTerminal();
                    }
                }
            }
        }

        this.rebuildTerminal();
    }

    private boolean mergeLeft() {
        TileMonitor left = this.getNeighbour(-1, 0);
        if (left != null && left.m_yIndex == 0 && left.m_height == this.m_height) {
            int width = left.m_width + this.m_width;
            if (width <= 8) {
                TileMonitor origin = left.getOrigin();
                if (origin != null) {
                    origin.resize(width, this.m_height);
                }

                left.expand();
                return true;
            }
        }

        return false;
    }

    private boolean mergeRight() {
        TileMonitor right = this.getNeighbour(this.m_width, 0);
        if (right != null && right.m_yIndex == 0 && right.m_height == this.m_height) {
            int width = this.m_width + right.m_width;
            if (width <= 8) {
                TileMonitor origin = this.getOrigin();
                if (origin != null) {
                    origin.resize(width, this.m_height);
                }

                this.expand();
                return true;
            }
        }

        return false;
    }

    private boolean mergeUp() {
        TileMonitor above = this.getNeighbour(0, this.m_height);
        if (above != null && above.m_xIndex == 0 && above.m_width == this.m_width) {
            int height = above.m_height + this.m_height;
            if (height <= 6) {
                TileMonitor origin = this.getOrigin();
                if (origin != null) {
                    origin.resize(this.m_width, height);
                }

                this.expand();
                return true;
            }
        }

        return false;
    }

    private boolean mergeDown() {
        TileMonitor below = this.getNeighbour(0, -1);
        if (below != null && below.m_xIndex == 0 && below.m_width == this.m_width) {
            int height = this.m_height + below.m_height;
            if (height <= 6) {
                TileMonitor origin = below.getOrigin();
                if (origin != null) {
                    origin.resize(this.m_width, height);
                }

                below.expand();
                return true;
            }
        }

        return false;
    }

    public void expand() {
        while (this.mergeLeft() || this.mergeRight() || this.mergeUp() || this.mergeDown()) {}
    }

    public void contractNeighbours() {
        this.m_ignoreMe = true;
        if (this.m_xIndex > 0) {
            TileMonitor left = this.getNeighbour(this.m_xIndex - 1, this.m_yIndex);
            if (left != null) {
                left.contract();
            }
        }

        if (this.m_xIndex + 1 < this.m_width) {
            TileMonitor right = this.getNeighbour(this.m_xIndex + 1, this.m_yIndex);
            if (right != null) {
                right.contract();
            }
        }

        if (this.m_yIndex > 0) {
            TileMonitor below = this.getNeighbour(this.m_xIndex, this.m_yIndex - 1);
            if (below != null) {
                below.contract();
            }
        }

        if (this.m_yIndex + 1 < this.m_height) {
            TileMonitor above = this.getNeighbour(this.m_xIndex, this.m_yIndex + 1);
            if (above != null) {
                above.contract();
            }
        }

        this.m_ignoreMe = false;
    }

    public void contract() {
        int height = this.m_height;
        int width = this.m_width;
        TileMonitor origin = this.getOrigin();
        if (origin == null) {
            TileMonitor right = null;
            TileMonitor below = null;
            if (width > 1) {
                right = this.getNeighbour(1, 0);
            }

            if (height > 1) {
                below = this.getNeighbour(0, 1);
            }

            if (right != null) {
                right.resize(width - 1, 1);
            }

            if (below != null) {
                below.resize(width, height - 1);
            }

            if (right != null) {
                right.expand();
            }

            if (below != null) {
                below.expand();
            }
        } else {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    TileMonitor monitor = origin.getNeighbour(x, y);
                    if (monitor == null) {
                        TileMonitor above = null;
                        TileMonitor left = null;
                        TileMonitor rightx = null;
                        TileMonitor belowx = null;
                        if (y > 0) {
                            above = origin;
                            origin.resize(width, y);
                        }

                        if (x > 0) {
                            left = origin.getNeighbour(0, y);
                            left.resize(x, 1);
                        }

                        if (x + 1 < width) {
                            rightx = origin.getNeighbour(x + 1, y);
                            rightx.resize(width - (x + 1), 1);
                        }

                        if (y + 1 < height) {
                            belowx = origin.getNeighbour(0, y + 1);
                            belowx.resize(width, height - (y + 1));
                        }

                        if (above != null) {
                            above.expand();
                        }

                        if (left != null) {
                            left.expand();
                        }

                        if (rightx != null) {
                            rightx.expand();
                        }

                        if (belowx != null) {
                            belowx.expand();
                        }

                        return;
                    }
                }
            }
        }
    }

    public void monitorTouched(float xPos, float yPos, float zPos) {
        int side = this.getDir();
        TileMonitor.XYPair pair = this.convertToXY(xPos, yPos, zPos, side);
        pair = new TileMonitor.XYPair(pair.x + this.m_xIndex, pair.y + this.m_height - this.m_yIndex - 1.0F);
        if (!(pair.x > this.m_width - 0.125F) && !(pair.y > this.m_height - 0.125F)
            && !(pair.x < 0.125F)
            && !(pair.y < 0.125F)) {
            Terminal originTerminal = this.getTerminal()
                .getTerminal();
            if (originTerminal != null) {
                if (this.getTerminal()
                    .isColour()) {
                    float xCharWidth = (this.m_width - 0.3125F) / originTerminal.getWidth();
                    float yCharHeight = (this.m_height - 0.3125F) / originTerminal.getHeight();
                    int xCharPos = (int) Math.min(
                        (float) originTerminal.getWidth(),
                        Math.max((pair.x - 0.125F - 0.03125F) / xCharWidth + 1.0F, 1.0F));
                    int yCharPos = (int) Math.min(
                        (float) originTerminal.getHeight(),
                        Math.max((pair.y - 0.125F - 0.03125F) / yCharHeight + 1.0F, 1.0F));

                    for (int y = 0; y < this.m_height; y++) {
                        for (int x = 0; x < this.m_width; x++) {
                            TileMonitor monitor = this.getNeighbour(x, y);
                            if (monitor != null) {
                                monitor.queueTouchEvent(xCharPos, yCharPos);
                            }
                        }
                    }
                }
            }
        }
    }

    private void queueTouchEvent(int xCharPos, int yCharPos) {
        for (IComputerAccess computer : this.m_computers) {
            computer.queueEvent("monitor_touch", new Object[] { computer.getAttachmentName(), xCharPos, yCharPos });
        }
    }

    private void queueSizeChangedEvent() {
        this.m_sizeChangedQueued = true;
    }

    private TileMonitor.XYPair convertToXY(float xPos, float yPos, float zPos, int side) {
        switch (side) {
            case 2:
                return new TileMonitor.XYPair(1.0F - xPos, 1.0F - yPos);
            case 3:
                return new TileMonitor.XYPair(xPos, 1.0F - yPos);
            case 4:
                return new TileMonitor.XYPair(zPos, 1.0F - yPos);
            case 5:
                return new TileMonitor.XYPair(1.0F - zPos, 1.0F - yPos);
            case 6:
            case 7:
            case 12:
            case 13:
            default:
                return new TileMonitor.XYPair(xPos, zPos);
            case 8:
                return new TileMonitor.XYPair(1.0F - xPos, zPos);
            case 9:
                return new TileMonitor.XYPair(xPos, 1.0F - zPos);
            case 10:
                return new TileMonitor.XYPair(zPos, xPos);
            case 11:
                return new TileMonitor.XYPair(1.0F - zPos, 1.0F - xPos);
            case 14:
                return new TileMonitor.XYPair(1.0F - xPos, 1.0F - zPos);
            case 15:
                return new TileMonitor.XYPair(xPos, zPos);
            case 16:
                return new TileMonitor.XYPair(zPos, 1.0F - xPos);
            case 17:
                return new TileMonitor.XYPair(1.0F - zPos, xPos);
        }
    }

    public void addComputer(IComputerAccess computer) {
        synchronized (this) {
            if (this.m_computers.size() == 0) {
                TileMonitor origin = this.getOrigin();
                if (origin != null) {
                    origin.rebuildTerminal();
                }
            }

            if (!this.m_computers.contains(computer)) {
                this.m_computers.add(computer);
            }
        }
    }

    public void removeComputer(IComputerAccess computer) {
        synchronized (this) {
            if (this.m_computers.contains(computer)) {
                this.m_computers.remove(computer);
            }
        }
    }

    public AxisAlignedBB getRenderBoundingBox() {
        if (this.getXIndex() == 0 && this.getYIndex() == 0) {
            TileMonitor monitor = this.getNeighbour(this.m_width - 1, this.m_height - 1);
            if (monitor != null) {
                int minX = Math.min(this.xCoord, monitor.xCoord);
                int minY = Math.min(this.yCoord, monitor.yCoord);
                int minZ = Math.min(this.zCoord, monitor.zCoord);
                int maxX = (minX == monitor.xCoord ? this.xCoord : monitor.xCoord) + 1;
                int maxY = (minY == monitor.yCoord ? this.yCoord : monitor.yCoord) + 1;
                int maxZ = (minZ == monitor.zCoord ? this.zCoord : monitor.zCoord) + 1;
                return AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
            } else {
                return ComputerCraft.Blocks.peripheral
                    .getCollisionBoundingBoxFromPool(this.worldObj, this.xCoord, this.yCoord, this.zCoord);
            }
        } else {
            return ComputerCraft.Blocks.peripheral
                .getCollisionBoundingBoxFromPool(this.worldObj, this.xCoord, this.yCoord, this.zCoord);
        }
    }

    private int getMonitorFaceTexture(int xPos, int yPos, int width, int height) {
        if (width == 1 && height == 1) {
            return 0;
        } else if (height == 1) {
            if (xPos == 0) {
                return 1;
            } else {
                return xPos == width - 1 ? 3 : 2;
            }
        } else if (width == 1) {
            if (yPos == 0) {
                return 6;
            } else {
                return yPos == height - 1 ? 4 : 5;
            }
        } else if (yPos == 0) {
            if (xPos == 0) {
                return 7;
            } else {
                return xPos == width - 1 ? 9 : 8;
            }
        } else if (yPos == height - 1) {
            if (xPos == 0) {
                return 13;
            } else {
                return xPos == width - 1 ? 15 : 14;
            }
        } else if (xPos == 0) {
            return 10;
        } else {
            return xPos == width - 1 ? 12 : 11;
        }
    }

    public static class XYPair {

        public final float x;
        public final float y;

        private XYPair(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
