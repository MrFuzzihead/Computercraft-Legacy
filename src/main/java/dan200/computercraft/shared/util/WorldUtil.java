package dan200.computercraft.shared.util;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Facing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class WorldUtil {

    public static boolean isBlockInWorld(World world, ChunkCoordinates coordinates) {
        return coordinates.posY >= 0 && coordinates.posY < world.getHeight();
    }

    public static boolean isLiquidBlock(World world, ChunkCoordinates coordinates) {
        if (isBlockInWorld(world, coordinates)) {
            Block block = world.getBlock(coordinates.posX, coordinates.posY, coordinates.posZ);
            if (block != null) {
                return block.getMaterial()
                    .isLiquid();
            }
        }

        return false;
    }

    public static ChunkCoordinates moveCoords(ChunkCoordinates coordinates, int dir) {
        return new ChunkCoordinates(
            coordinates.posX + Facing.offsetsXForSide[dir],
            coordinates.posY + Facing.offsetsYForSide[dir],
            coordinates.posZ + Facing.offsetsZForSide[dir]);
    }

    public static Entity rayTraceEntities(World world, Vec3 vecStart, Vec3 vecDir, double distance) {
        Vec3 vecEnd = vecStart.addVector(vecDir.xCoord * distance, vecDir.yCoord * distance, vecDir.zCoord * distance);
        MovingObjectPosition result = world
            .rayTraceBlocks(vecStart.addVector(0.0, 0.0, 0.0), vecEnd.addVector(0.0, 0.0, 0.0));
        if (result != null && result.typeOfHit == MovingObjectType.BLOCK) {
            distance = vecStart.distanceTo(result.hitVec);
            vecEnd = vecStart.addVector(vecDir.xCoord * distance, vecDir.yCoord * distance, vecDir.zCoord * distance);
        }

        float xStretch = Math.abs(vecDir.xCoord) > 0.25 ? 0.0F : 1.0F;
        float yStretch = Math.abs(vecDir.yCoord) > 0.25 ? 0.0F : 1.0F;
        float zStretch = Math.abs(vecDir.zCoord) > 0.25 ? 0.0F : 1.0F;
        AxisAlignedBB bigBox = AxisAlignedBB.getBoundingBox(
            Math.min(vecStart.xCoord, vecEnd.xCoord) - 0.375F * xStretch,
            Math.min(vecStart.yCoord, vecEnd.yCoord) - 0.375F * yStretch,
            Math.min(vecStart.zCoord, vecEnd.zCoord) - 0.375F * zStretch,
            Math.max(vecStart.xCoord, vecEnd.xCoord) + 0.375F * xStretch,
            Math.max(vecStart.yCoord, vecEnd.yCoord) + 0.375F * yStretch,
            Math.max(vecStart.zCoord, vecEnd.zCoord) + 0.375F * zStretch);
        Entity closest = null;
        double closestDist = 99.0;
        List list = world.getEntitiesWithinAABBExcludingEntity(null, bigBox);

        for (int i = 0; i < list.size(); i++) {
            Entity entity = (Entity) list.get(i);
            if (entity.canBeCollidedWith()) {
                AxisAlignedBB littleBox = entity.boundingBox;
                if (littleBox.isVecInside(vecStart)) {
                    closest = entity;
                    closestDist = 0.0;
                } else {
                    MovingObjectPosition littleBoxResult = littleBox.calculateIntercept(vecStart, vecEnd);
                    if (littleBoxResult != null) {
                        double dist = vecStart.distanceTo(littleBoxResult.hitVec);
                        if (closest == null || dist <= closestDist) {
                            closest = entity;
                            closestDist = dist;
                        }
                    } else if (littleBox.intersectsWith(bigBox) && closest == null) {
                        closest = entity;
                        closestDist = distance;
                    }
                }
            }
        }

        return closest != null && closestDist <= distance ? closest : null;
    }

    public static void dropItemStack(ItemStack stack, World world, int x, int y, int z) {
        dropItemStack(stack, world, x, y, z, -1);
    }

    public static void dropItemStack(ItemStack stack, World world, int x, int y, int z, int direction) {
        double xDir;
        double yDir;
        double zDir;
        if (direction >= 0) {
            xDir = Facing.offsetsXForSide[direction];
            yDir = Facing.offsetsYForSide[direction];
            zDir = Facing.offsetsZForSide[direction];
        } else {
            xDir = 0.0;
            yDir = 0.0;
            zDir = 0.0;
        }

        double xPos = x + 0.5 + xDir * 0.4;
        double yPos = y + 0.5 + yDir * 0.4;
        double zPos = z + 0.5 + zDir * 0.4;
        dropItemStack(stack, world, xPos, yPos, zPos, xDir, yDir, zDir);
    }

    public static void dropItemStack(ItemStack stack, World world, double xPos, double yPos, double zPos) {
        dropItemStack(stack, world, xPos, yPos, zPos, 0.0, 0.0, 0.0);
    }

    public static void dropItemStack(ItemStack stack, World world, double xPos, double yPos, double zPos, double xDir,
        double yDir, double zDir) {
        EntityItem entityItem = new EntityItem(world, xPos, yPos, zPos, stack.copy());
        entityItem.motionX = xDir * 0.7 + world.rand.nextFloat() * 0.2 - 0.1;
        entityItem.motionY = yDir * 0.7 + world.rand.nextFloat() * 0.2 - 0.1;
        entityItem.motionZ = zDir * 0.7 + world.rand.nextFloat() * 0.2 - 0.1;
        entityItem.delayBeforeCanPickup = 30;
        world.spawnEntityInWorld(entityItem);
    }
}
