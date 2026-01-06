package dan200.computercraft.shared.turtle.core;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Facing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.google.common.base.Objects;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.IExtendedTurtleUpgrade;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleAnimation;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleUpgradeType;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.core.IComputer;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;
import dan200.computercraft.shared.util.Colour;
import dan200.computercraft.shared.util.DirectionUtil;
import dan200.computercraft.shared.util.Holiday;
import dan200.computercraft.shared.util.HolidayUtil;

public class TurtleBrain implements ITurtleAccess {

    private static int s_nextInstanceID = 0;
    private static Map<Integer, WeakReference<TurtleBrain>> s_allClientBrains = new HashMap<>();
    private static final int ANIM_DURATION = 8;
    private TileTurtle m_owner;
    private LinkedList<TurtleCommandQueueEntry> m_commandQueue;
    private int m_commandsIssued;
    private Map<TurtleSide, ITurtleUpgrade> m_upgrades;
    private Map<TurtleSide, IPeripheral> m_peripherals;
    private Map<TurtleSide, NBTTagCompound> m_upgradeNBTData;
    private int m_selectedSlot;
    private int m_fuelLevel;
    private Colour m_colour;
    private ResourceLocation m_overlay;
    private ResourceLocation m_hatOverlay;
    private int m_instanceID;
    private int m_direction;
    private TurtleAnimation m_animation;
    private int m_animationProgress;
    private int m_lastAnimationProgress;

    public static int assignInstanceID() {
        return s_nextInstanceID++;
    }

    public static TurtleBrain getClientBrain(int instanceID) {
        if (instanceID >= 0) {
            WeakReference<TurtleBrain> ref = s_allClientBrains.get(instanceID);
            if (ref != null) {
                TurtleBrain brain = ref.get();
                if (brain != null) {
                    return brain;
                }

                s_allClientBrains.remove(instanceID);
            }
        }

        return null;
    }

    public static void setClientBrain(int instanceID, TurtleBrain brain) {
        if (instanceID >= 0 && getClientBrain(instanceID) != brain) {
            s_allClientBrains.put(instanceID, new WeakReference<>(brain));
        }
    }

    public static void cleanupBrains() {
        if (s_allClientBrains.size() > 0) {
            Iterator<Entry<Integer, WeakReference<TurtleBrain>>> it = s_allClientBrains.entrySet()
                .iterator();

            while (it.hasNext()) {
                Entry<Integer, WeakReference<TurtleBrain>> entry = it.next();
                WeakReference<TurtleBrain> ref = entry.getValue();
                if (ref != null) {
                    TurtleBrain brain = ref.get();
                    if (brain == null) {
                        it.remove();
                    }
                }
            }
        }
    }

    public TurtleBrain(TileTurtle turtle) {
        this.m_owner = turtle;
        this.m_commandQueue = new LinkedList<>();
        this.m_commandsIssued = 0;
        this.m_upgrades = new HashMap<>();
        this.m_peripherals = new HashMap<>();
        this.m_upgradeNBTData = new HashMap<>();
        this.m_selectedSlot = 0;
        this.m_fuelLevel = 0;
        this.m_colour = null;
        this.m_overlay = null;
        this.m_hatOverlay = null;
        this.m_instanceID = -1;
        this.m_direction = 2;
        this.m_animation = TurtleAnimation.None;
        this.m_animationProgress = 0;
        this.m_lastAnimationProgress = 0;
    }

    public TurtleBrain getFutureSelf() {
        if (this.getOwner()
            .getWorldObj().isRemote) {
            TurtleBrain futureSelf = getClientBrain(this.m_instanceID);
            if (futureSelf != null) {
                return futureSelf;
            }
        }

        return this;
    }

    public void setOwner(TileTurtle owner) {
        this.m_owner = owner;
    }

    public TileTurtle getOwner() {
        return this.m_owner;
    }

    public ComputerFamily getFamily() {
        return this.m_owner.getFamily();
    }

    public void setupComputer(ServerComputer computer) {
        this.updatePeripherals(computer);
    }

    public void update() {
        World world = this.getWorld();
        if (!world.isRemote) {
            this.updateCommands();
        }

        this.updateAnimation();
        if (!this.m_upgrades.isEmpty()) {
            for (Entry<TurtleSide, ITurtleUpgrade> entry : this.m_upgrades.entrySet()) {
                entry.getValue()
                    .update(this, entry.getKey());
            }
        }
    }

    public void readFromNBT(NBTTagCompound nbttagcompound) {
        this.m_direction = nbttagcompound.getInteger("dir");
        this.m_selectedSlot = nbttagcompound.getInteger("selectedSlot");
        if (nbttagcompound.hasKey("fuelLevel")) {
            this.m_fuelLevel = nbttagcompound.getInteger("fuelLevel");
        } else {
            this.m_fuelLevel = 0;
        }

        if (nbttagcompound.hasKey("colourIndex")) {
            this.m_colour = Colour.values()[nbttagcompound.getInteger("colourIndex")];
        } else {
            this.m_colour = null;
        }

        if (nbttagcompound.hasKey("overlay_mod")) {
            String overlay_mod = nbttagcompound.getString("overlay_mod");
            if (nbttagcompound.hasKey("overlay_path")) {
                String overlay_path = nbttagcompound.getString("overlay_path");
                this.m_overlay = new ResourceLocation(overlay_mod, overlay_path);
            } else {
                this.m_overlay = null;
            }

            if (nbttagcompound.hasKey("overlay_hatPath")) {
                String overlay_hatPath = nbttagcompound.getString("overlay_hatPath");
                this.m_hatOverlay = new ResourceLocation(overlay_mod, overlay_hatPath);
            } else {
                this.m_hatOverlay = null;
            }
        } else {
            this.m_overlay = null;
            this.m_hatOverlay = null;
        }

        int leftUpgradeID = 0;
        int rightUpgradeID = 0;
        if (nbttagcompound.hasKey("subType")) {
            int subType = nbttagcompound.getInteger("subType");
            if ((subType & 1) > 0) {
                leftUpgradeID = 5;
            }

            if ((subType & 2) > 0) {
                rightUpgradeID = 1;
            }
        } else {
            if (nbttagcompound.hasKey("leftUpgrade")) {
                leftUpgradeID = nbttagcompound.getInteger("leftUpgrade");
            }

            if (nbttagcompound.hasKey("rightUpgrade")) {
                rightUpgradeID = nbttagcompound.getInteger("rightUpgrade");
            }
        }

        this.setUpgrade(TurtleSide.Left, ComputerCraft.getTurtleUpgrade(leftUpgradeID));
        this.setUpgrade(TurtleSide.Right, ComputerCraft.getTurtleUpgrade(rightUpgradeID));
        this.m_upgradeNBTData.clear();
        if (nbttagcompound.hasKey("leftUpgradeNBT")) {
            this.m_upgradeNBTData.put(
                TurtleSide.Left,
                (NBTTagCompound) nbttagcompound.getCompoundTag("leftUpgradeNBT")
                    .copy());
        }

        if (nbttagcompound.hasKey("rightUpgradeNBT")) {
            this.m_upgradeNBTData.put(
                TurtleSide.Right,
                (NBTTagCompound) nbttagcompound.getCompoundTag("rightUpgradeNBT")
                    .copy());
        }
    }

    public void writeToNBT(NBTTagCompound nbttagcompound) {
        nbttagcompound.setInteger("dir", this.m_direction);
        nbttagcompound.setInteger("selectedSlot", this.m_selectedSlot);
        nbttagcompound.setInteger("fuelLevel", this.m_fuelLevel);
        nbttagcompound.setInteger("leftUpgrade", ComputerCraft.getTurtleUpgradeID(this.getUpgrade(TurtleSide.Left)));
        nbttagcompound.setInteger("rightUpgrade", ComputerCraft.getTurtleUpgradeID(this.getUpgrade(TurtleSide.Right)));
        if (this.m_colour != null) {
            nbttagcompound.setInteger("colourIndex", this.m_colour.ordinal());
        }

        if (this.m_overlay != null && this.m_hatOverlay != null) {
            nbttagcompound.setString("overlay_mod", this.m_overlay.getResourceDomain());
            nbttagcompound.setString("overlay_path", this.m_overlay.getResourcePath());
            nbttagcompound.setString("overlay_hatPath", this.m_hatOverlay.getResourcePath());
        } else if (this.m_overlay != null) {
            nbttagcompound.setString("overlay_mod", this.m_overlay.getResourceDomain());
            nbttagcompound.setString("overlay_path", this.m_overlay.getResourcePath());
        } else if (this.m_hatOverlay != null) {
            nbttagcompound.setString("overlay_mod", this.m_hatOverlay.getResourceDomain());
            nbttagcompound.setString("overlay_hatPath", this.m_hatOverlay.getResourcePath());
        }

        if (this.m_upgradeNBTData.containsKey(TurtleSide.Left)) {
            nbttagcompound.setTag(
                "leftUpgradeNBT",
                (NBTTagCompound) this.getUpgradeNBTData(TurtleSide.Left)
                    .copy());
        }

        if (this.m_upgradeNBTData.containsKey(TurtleSide.Right)) {
            nbttagcompound.setTag(
                "rightUpgradeNBT",
                (NBTTagCompound) this.getUpgradeNBTData(TurtleSide.Right)
                    .copy());
        }
    }

    public void writeDescription(NBTTagCompound nbttagcompound) {
        nbttagcompound.setInteger("leftUpgrade", ComputerCraft.getTurtleUpgradeID(this.getUpgrade(TurtleSide.Left)));
        nbttagcompound.setInteger("rightUpgrade", ComputerCraft.getTurtleUpgradeID(this.getUpgrade(TurtleSide.Right)));
        if (this.m_upgradeNBTData.containsKey(TurtleSide.Left)) {
            nbttagcompound.setTag(
                "leftUpgradeNBT",
                (NBTTagCompound) this.getUpgradeNBTData(TurtleSide.Left)
                    .copy());
        }

        if (this.m_upgradeNBTData.containsKey(TurtleSide.Right)) {
            nbttagcompound.setTag(
                "rightUpgradeNBT",
                (NBTTagCompound) this.getUpgradeNBTData(TurtleSide.Right)
                    .copy());
        }

        if (this.m_colour != null) {
            nbttagcompound.setInteger("colourIndex", this.m_colour.ordinal());
        }

        if (this.m_overlay != null && this.m_hatOverlay != null) {
            nbttagcompound.setString("overlay_mod", this.m_overlay.getResourceDomain());
            nbttagcompound.setString("overlay_path", this.m_overlay.getResourcePath());
            nbttagcompound.setString("overlay_hatPath", this.m_hatOverlay.getResourcePath());
        } else if (this.m_overlay != null) {
            nbttagcompound.setString("overlay_mod", this.m_overlay.getResourceDomain());
            nbttagcompound.setString("overlay_path", this.m_overlay.getResourcePath());
        } else if (this.m_hatOverlay != null) {
            nbttagcompound.setString("overlay_mod", this.m_hatOverlay.getResourceDomain());
            nbttagcompound.setString("overlay_hatPath", this.m_hatOverlay.getResourcePath());
        }

        if (this.m_instanceID < 0) {
            this.m_instanceID = assignInstanceID();
        }

        nbttagcompound.setInteger("brainInstanceID", this.m_instanceID);
        nbttagcompound.setInteger("animation", this.m_animation.ordinal());
        nbttagcompound.setInteger("direction", this.m_direction);
        nbttagcompound.setInteger("fuelLevel", this.m_fuelLevel);
    }

    public void readDescription(NBTTagCompound nbttagcompound) {
        this.setUpgrade(TurtleSide.Left, ComputerCraft.getTurtleUpgrade(nbttagcompound.getInteger("leftUpgrade")));
        this.setUpgrade(TurtleSide.Right, ComputerCraft.getTurtleUpgrade(nbttagcompound.getInteger("rightUpgrade")));
        this.m_upgradeNBTData.clear();
        if (nbttagcompound.hasKey("leftUpgradeNBT")) {
            this.m_upgradeNBTData.put(
                TurtleSide.Left,
                (NBTTagCompound) nbttagcompound.getCompoundTag("leftUpgradeNBT")
                    .copy());
        }

        if (nbttagcompound.hasKey("rightUpgradeNBT")) {
            this.m_upgradeNBTData.put(
                TurtleSide.Right,
                (NBTTagCompound) nbttagcompound.getCompoundTag("rightUpgradeNBT")
                    .copy());
        }

        if (nbttagcompound.hasKey("colourIndex")) {
            this.m_colour = Colour.values()[nbttagcompound.getInteger("colourIndex")];
        } else {
            this.m_colour = null;
        }

        if (nbttagcompound.hasKey("overlay_mod")) {
            String overlay_mod = nbttagcompound.getString("overlay_mod");
            if (nbttagcompound.hasKey("overlay_path")) {
                String overlay_path = nbttagcompound.getString("overlay_path");
                this.m_overlay = new ResourceLocation(overlay_mod, overlay_path);
            } else {
                this.m_overlay = null;
            }

            if (nbttagcompound.hasKey("overlay_hatPath")) {
                String overlay_hatPath = nbttagcompound.getString("overlay_hatPath");
                this.m_hatOverlay = new ResourceLocation(overlay_mod, overlay_hatPath);
            } else {
                this.m_hatOverlay = null;
            }
        } else {
            this.m_overlay = null;
            this.m_hatOverlay = null;
        }

        this.m_instanceID = nbttagcompound.getInteger("brainInstanceID");
        setClientBrain(this.m_instanceID, this);
        TurtleAnimation anim = TurtleAnimation.values()[nbttagcompound.getInteger("animation")];
        if (anim != this.m_animation && anim != TurtleAnimation.Wait
            && anim != TurtleAnimation.ShortWait
            && anim != TurtleAnimation.None) {
            this.m_animation = TurtleAnimation.values()[nbttagcompound.getInteger("animation")];
            this.m_animationProgress = 0;
            this.m_lastAnimationProgress = 0;
        }

        this.m_direction = nbttagcompound.getInteger("direction");
        this.m_fuelLevel = nbttagcompound.getInteger("fuelLevel");
    }

    @Override
    public World getWorld() {
        return this.m_owner.getWorldObj();
    }

    @Override
    public ChunkCoordinates getPosition() {
        return new ChunkCoordinates(this.m_owner.xCoord, this.m_owner.yCoord, this.m_owner.zCoord);
    }

    @Override
    public boolean teleportTo(World world, int x, int y, int z) {
        if (!world.isRemote && !this.getWorld().isRemote) {
            World oldWorld = this.getWorld();
            int oldX = this.m_owner.xCoord;
            int oldY = this.m_owner.yCoord;
            int oldZ = this.m_owner.zCoord;
            Block oldBlock = this.m_owner.getBlock();
            if (oldWorld == world && oldX == x && oldY == y && oldZ == z) {
                return true;
            } else {
                if (world.blockExists(x, y, z) && world.setBlock(x, y, z, oldBlock, 0, 3)) {
                    Block block = world.getBlock(x, y, z);
                    if (block == oldBlock) {
                        TileEntity newTile = world.getTileEntity(x, y, z);
                        if (newTile != null && newTile instanceof TileTurtle) {
                            TileTurtle newTurtle = (TileTurtle) newTile;
                            newTurtle.transferStateFrom(this.m_owner);
                            newTurtle.createServerComputer()
                                .setWorld(world);
                            newTurtle.createServerComputer()
                                .setPosition(x, y, z);
                            oldWorld.setBlockToAir(oldX, oldY, oldZ);
                            newTurtle.updateBlock();
                            return true;
                        }
                    }

                    world.setBlockToAir(x, y, z);
                }

                return false;
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public Vec3 getVisualPosition(float f) {
        Vec3 offset = this.getRenderOffset(f);
        return Vec3.createVectorHelper(
            this.m_owner.xCoord + 0.5 + offset.xCoord,
            this.m_owner.yCoord + 0.5 + offset.yCoord,
            this.m_owner.zCoord + 0.5 + offset.zCoord);
    }

    @Override
    public float getVisualYaw(float f) {
        float forward = DirectionUtil.toYawAngle(this.getDirection());
        float yaw = forward;
        switch (this.m_animation) {
            case TurnLeft:
                yaw = forward + 90.0F * (1.0F - this.getAnimationFraction(f));
                if (yaw >= 360.0F) {
                    yaw -= 360.0F;
                }
                break;
            case TurnRight:
                yaw = forward + -90.0F * (1.0F - this.getAnimationFraction(f));
                if (yaw < 0.0F) {
                    yaw += 360.0F;
                }
        }

        return yaw;
    }

    @Override
    public int getDirection() {
        return this.m_direction;
    }

    @Override
    public void setDirection(int dir) {
        if (dir >= 0 && dir < 6) {
            this.m_direction = Math.max(dir, 2);
            this.m_owner.updateOutput();
            this.m_owner.updateInput();
            this.m_owner.onTileEntityChange();
        }
    }

    @Override
    public int getSelectedSlot() {
        return this.m_selectedSlot;
    }

    @Override
    public void setSelectedSlot(int slot) {
        if (this.getWorld().isRemote) {
            throw new UnsupportedOperationException();
        } else {
            if (slot >= 0 && slot < this.m_owner.getSizeInventory()) {
                this.m_selectedSlot = slot;
                this.m_owner.onTileEntityChange();
            }
        }
    }

    @Override
    public IInventory getInventory() {
        return this.m_owner;
    }

    @Override
    public boolean isFuelNeeded() {
        return ComputerCraft.turtlesNeedFuel;
    }

    @Override
    public int getFuelLevel() {
        return Math.min(this.m_fuelLevel, this.getFuelLimit());
    }

    @Override
    public void setFuelLevel(int level) {
        this.m_fuelLevel = Math.min(level, this.getFuelLimit());
        this.m_owner.onTileEntityChange();
    }

    @Override
    public int getFuelLimit() {
        return this.m_owner.getFamily() == ComputerFamily.Advanced ? ComputerCraft.advancedTurtleFuelLimit
            : ComputerCraft.turtleFuelLimit;
    }

    @Override
    public boolean consumeFuel(int fuel) {
        if (this.getWorld().isRemote) {
            throw new UnsupportedOperationException();
        } else if (!this.isFuelNeeded()) {
            return true;
        } else {
            int consumption = Math.max(fuel, 0);
            if (this.getFuelLevel() >= consumption) {
                this.setFuelLevel(this.getFuelLevel() - consumption);
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public void addFuel(int fuel) {
        if (this.getWorld().isRemote) {
            throw new UnsupportedOperationException();
        } else {
            int addition = Math.max(fuel, 0);
            this.setFuelLevel(this.getFuelLevel() + addition);
        }
    }

    private int issueCommand(ITurtleCommand command) {
        this.m_commandQueue.offer(new TurtleCommandQueueEntry(++this.m_commandsIssued, command));
        return this.m_commandsIssued;
    }

    @Override
    public Object[] executeCommand(ILuaContext context, ITurtleCommand command)
        throws LuaException, InterruptedException {
        if (this.getWorld().isRemote) {
            throw new UnsupportedOperationException();
        } else {
            int commandID = this.issueCommand(command);

            Object[] response;
            do {
                response = context.pullEvent("turtle_response");
            } while (response.length < 3 || !(response[1] instanceof Number)
                || !(response[2] instanceof Boolean)
                || ((Number) response[1]).intValue() != commandID);

            Object[] returnValues = new Object[response.length - 2];

            for (int i = 0; i < returnValues.length; i++) {
                returnValues[i] = response[i + 2];
            }

            return returnValues;
        }
    }

    @Override
    public void playAnimation(TurtleAnimation animation) {
        if (this.getWorld().isRemote) {
            throw new UnsupportedOperationException();
        } else {
            this.m_animation = animation;
            if (this.m_animation == TurtleAnimation.ShortWait) {
                this.m_animationProgress = 4;
                this.m_lastAnimationProgress = 4;
            } else {
                this.m_animationProgress = 0;
                this.m_lastAnimationProgress = 0;
            }

            this.m_owner.updateBlock();
        }
    }

    @Override
    public int getDyeColour() {
        return this.m_colour != null ? this.m_colour.ordinal() : -1;
    }

    public ResourceLocation getOverlay() {
        return this.m_overlay;
    }

    public ResourceLocation getHatOverlay() {
        return this.m_hatOverlay;
    }

    public void setOverlay(ResourceLocation overlay, ResourceLocation hatOverlay) {
        if (!Objects.equal(this.m_overlay, overlay) || !Objects.equal(this.m_hatOverlay, overlay)) {
            this.m_overlay = overlay;
            this.m_hatOverlay = hatOverlay;
            this.m_owner.updateBlock();
        }
    }

    @Override
    public void setDyeColour(int dyeColour) {
        Colour newColour = null;
        if (dyeColour >= 0 && dyeColour < 16) {
            newColour = Colour.values()[dyeColour];
        }

        if (this.m_colour != newColour) {
            this.m_colour = newColour;
            this.m_owner.updateBlock();
        }
    }

    @Override
    public ITurtleUpgrade getUpgrade(TurtleSide side) {
        return this.m_upgrades.containsKey(side) ? this.m_upgrades.get(side) : null;
    }

    @Override
    public void setUpgrade(TurtleSide side, ITurtleUpgrade upgrade) {
        ITurtleUpgrade oldUpgrade = m_upgrades.get(side);
        if (oldUpgrade == upgrade) {
            return;
        } else if (oldUpgrade != null) {
            m_upgrades.remove(side);
        }

        if (m_upgradeNBTData.containsKey(side)) {
            m_upgradeNBTData.remove(side);
        }

        if (upgrade != null) m_upgrades.put(side, upgrade);

        if (m_owner.getWorldObj() != null) {
            updatePeripherals(m_owner.createServerComputer());
            m_owner.updateBlock();

            if (!m_owner.getWorldObj().isRemote) {
                TurtleSide otherSide = side == TurtleSide.Left ? TurtleSide.Right : TurtleSide.Left;
                ITurtleUpgrade other = getUpgrade(otherSide);
                if (other != null && other instanceof IExtendedTurtleUpgrade) {
                    ((IExtendedTurtleUpgrade) other).upgradeChanged(this, otherSide, oldUpgrade, upgrade);
                }
            }
        }
    }

    @Override
    public IPeripheral getPeripheral(TurtleSide side) {
        return this.m_peripherals.containsKey(side) ? this.m_peripherals.get(side) : null;
    }

    @Override
    public NBTTagCompound getUpgradeNBTData(TurtleSide side) {
        if (!this.m_upgradeNBTData.containsKey(side)) {
            this.m_upgradeNBTData.put(side, new NBTTagCompound());
        }

        return this.m_upgradeNBTData.get(side);
    }

    @Override
    public void updateUpgradeNBTData(TurtleSide side) {
        this.m_owner.updateBlock();
    }

    public boolean saveBlockChange(ChunkCoordinates coordinates, Block previousBlock, int previousMetadata) {
        return false;
    }

    public Vec3 getRenderOffset(float f) {
        switch (this.m_animation) {
            case MoveForward:
            case MoveBack:
            case MoveUp:
            case MoveDown:
                int dir;
                switch (this.m_animation) {
                    case MoveForward:
                    default:
                        dir = this.getDirection();
                        break;
                    case MoveBack:
                        dir = Facing.oppositeSide[this.getDirection()];
                        break;
                    case MoveUp:
                        dir = 1;
                        break;
                    case MoveDown:
                        dir = 0;
                }

                double distance = -1.0 + this.getAnimationFraction(f);
                return Vec3.createVectorHelper(
                    distance * Facing.offsetsXForSide[dir],
                    distance * Facing.offsetsYForSide[dir],
                    distance * Facing.offsetsZForSide[dir]);
            default:
                return Vec3.createVectorHelper(0.0, 0.0, 0.0);
        }
    }

    public float getToolRenderAngle(TurtleSide side, float f) {
        return (side != TurtleSide.Left || this.m_animation != TurtleAnimation.SwingLeftTool)
            && (side != TurtleSide.Right || this.m_animation != TurtleAnimation.SwingRightTool) ? 0.0F
                : 45.0F * (float) Math.sin(this.getAnimationFraction(f) * Math.PI);
    }

    private int toDirection(TurtleSide side) {
        switch (side) {
            case Left:
                return 5;
            case Right:
            default:
                return 4;
        }
    }

    public void updatePeripherals(ServerComputer serverComputer) {
        if (serverComputer != null) {
            for (TurtleSide side : TurtleSide.values()) {
                ITurtleUpgrade upgrade = this.getUpgrade(side);
                IPeripheral peripheral = null;
                if (upgrade != null
                    && (upgrade.getType() == TurtleUpgradeType.Peripheral || (upgrade instanceof IExtendedTurtleUpgrade
                        && ((IExtendedTurtleUpgrade) upgrade).alsoPeripheral()))) {
                    peripheral = upgrade.createPeripheral(this, side);
                }

                int dir = this.toDirection(side);
                if (peripheral != null) {
                    if (!this.m_peripherals.containsKey(side)) {
                        serverComputer.setPeripheral(dir, peripheral);
                        serverComputer.setRedstoneInput(dir, 0);
                        serverComputer.setBundledRedstoneInput(dir, 0);
                        this.m_peripherals.put(side, peripheral);
                    } else if (!this.m_peripherals.get(side)
                        .equals(peripheral)) {
                            serverComputer.setPeripheral(dir, peripheral);
                            serverComputer.setRedstoneInput(dir, 0);
                            serverComputer.setBundledRedstoneInput(dir, 0);
                            this.m_peripherals.remove(side);
                            this.m_peripherals.put(side, peripheral);
                        }
                } else if (this.m_peripherals.containsKey(side)) {
                    serverComputer.setPeripheral(dir, null);
                    this.m_peripherals.remove(side);
                }
            }
        }
    }

    private void updateCommands() {
        if (this.m_animation == TurtleAnimation.None) {
            TurtleCommandQueueEntry nextCommand = null;
            if (this.m_commandQueue.peek() != null) {
                nextCommand = this.m_commandQueue.remove();
            }

            if (nextCommand != null) {
                TurtleCommandResult result = nextCommand.command.execute(this);
                int callbackID = nextCommand.callbackID;
                if (callbackID >= 0) {
                    if (result != null && result.isSuccess()) {
                        IComputer computer = this.m_owner.getComputer();
                        if (computer != null) {
                            Object[] results = result.getResults();
                            if (results != null) {
                                Object[] arguments = new Object[results.length + 2];
                                arguments[0] = callbackID;
                                arguments[1] = true;

                                for (int i = 0; i < results.length; i++) {
                                    arguments[2 + i] = results[i];
                                }

                                computer.queueEvent("turtle_response", arguments);
                            } else {
                                computer.queueEvent("turtle_response", new Object[] { callbackID, true });
                            }
                        }
                    } else {
                        IComputer computer = this.m_owner.getComputer();
                        if (computer != null) {
                            computer.queueEvent(
                                "turtle_response",
                                new Object[] { callbackID, false, result != null ? result.getErrorMessage() : null });
                        }
                    }
                }
            }
        }
    }

    private void updateAnimation() {
        if (this.m_animation != TurtleAnimation.None) {
            World world = this.getWorld();
            if (ComputerCraft.turtlesCanPush
                && (this.m_animation == TurtleAnimation.MoveForward || this.m_animation == TurtleAnimation.MoveBack
                    || this.m_animation == TurtleAnimation.MoveUp
                    || this.m_animation == TurtleAnimation.MoveDown)) {
                ChunkCoordinates pos = this.getPosition();
                int moveDir;
                switch (this.m_animation) {
                    case MoveForward:
                    default:
                        moveDir = this.m_direction;
                        break;
                    case MoveBack:
                        moveDir = Facing.oppositeSide[this.m_direction];
                        break;
                    case MoveUp:
                        moveDir = 1;
                        break;
                    case MoveDown:
                        moveDir = 0;
                }

                AxisAlignedBB aabb = AxisAlignedBB
                    .getBoundingBox(pos.posX, pos.posY, pos.posZ, pos.posX + 1.0, pos.posY + 1.0, pos.posZ + 1.0);
                float pushFrac = 1.0F - (this.m_animationProgress + 1) / 8.0F;
                float push = Math.max(pushFrac + 0.0125F, 0.0F);
                if (Facing.offsetsXForSide[moveDir] < 0) {
                    aabb.minX = aabb.minX + Facing.offsetsXForSide[moveDir] * push;
                } else {
                    aabb.maxX = aabb.maxX - Facing.offsetsXForSide[moveDir] * push;
                }

                if (Facing.offsetsYForSide[moveDir] < 0) {
                    aabb.minY = aabb.minY + Facing.offsetsYForSide[moveDir] * push;
                } else {
                    aabb.maxY = aabb.maxY - Facing.offsetsYForSide[moveDir] * push;
                }

                if (Facing.offsetsZForSide[moveDir] < 0) {
                    aabb.minZ = aabb.minZ + Facing.offsetsZForSide[moveDir] * push;
                } else {
                    aabb.maxZ = aabb.maxZ - Facing.offsetsZForSide[moveDir] * push;
                }

                List list = world.getEntitiesWithinAABBExcludingEntity((Entity) null, aabb);
                if (!list.isEmpty()) {
                    double pushStep = 0.125;
                    double pushStepX = Facing.offsetsXForSide[moveDir] * pushStep;
                    double pushStepY = Facing.offsetsYForSide[moveDir] * pushStep;
                    double pushStepZ = Facing.offsetsZForSide[moveDir] * pushStep;

                    for (int i = 0; i < list.size(); i++) {
                        Entity entity = (Entity) list.get(i);
                        entity.moveEntity(pushStepX, pushStepY, pushStepZ);
                    }
                }
            }

            if (world.isRemote && this.m_animation == TurtleAnimation.MoveForward && this.m_animationProgress == 4) {
                Holiday currentHoliday = HolidayUtil.getCurrentHoliday();
                if (currentHoliday == Holiday.Valentines) {
                    Vec3 position = this.getVisualPosition(1.0F);
                    if (position != null && world != null) {
                        double x = position.xCoord + world.rand.nextGaussian() * 0.1;
                        double y = position.yCoord + 0.5 + world.rand.nextGaussian() * 0.1;
                        double z = position.zCoord + world.rand.nextGaussian() * 0.1;
                        world.spawnParticle(
                            "heart",
                            x,
                            y,
                            z,
                            world.rand.nextGaussian() * 0.02,
                            world.rand.nextGaussian() * 0.02,
                            world.rand.nextGaussian() * 0.02);
                    }
                }
            }

            this.m_lastAnimationProgress = this.m_animationProgress;
            if (++this.m_animationProgress >= 8) {
                this.m_animation = TurtleAnimation.None;
                this.m_animationProgress = 0;
                this.m_lastAnimationProgress = 0;
            }
        }
    }

    private float getAnimationFraction(float f) {
        float next = this.m_animationProgress / 8.0F;
        float previous = this.m_lastAnimationProgress / 8.0F;
        return previous + (next - previous) * f;
    }
}
