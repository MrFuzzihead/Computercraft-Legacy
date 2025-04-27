package dan200.computercraft.api.turtle;

import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IPeripheral;

public interface ITurtleAccess {

    World getWorld();

    ChunkCoordinates getPosition();

    boolean teleportTo(World var1, int var2, int var3, int var4);

    Vec3 getVisualPosition(float var1);

    float getVisualYaw(float var1);

    int getDirection();

    void setDirection(int var1);

    int getSelectedSlot();

    void setSelectedSlot(int var1);

    void setDyeColour(int var1);

    int getDyeColour();

    IInventory getInventory();

    boolean isFuelNeeded();

    int getFuelLevel();

    void setFuelLevel(int var1);

    int getFuelLimit();

    boolean consumeFuel(int var1);

    void addFuel(int var1);

    Object[] executeCommand(ILuaContext var1, ITurtleCommand var2) throws LuaException, InterruptedException;

    void playAnimation(TurtleAnimation var1);

    ITurtleUpgrade getUpgrade(TurtleSide var1);

    void setUpgrade(TurtleSide var1, ITurtleUpgrade var2);

    IPeripheral getPeripheral(TurtleSide var1);

    NBTTagCompound getUpgradeNBTData(TurtleSide var1);

    void updateUpgradeNBTData(TurtleSide var1);
}
