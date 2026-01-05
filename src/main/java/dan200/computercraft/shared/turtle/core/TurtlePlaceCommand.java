package dan200.computercraft.shared.turtle.core;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBoat;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemGlassBottle;
import net.minecraft.item.ItemLilyPad;
import net.minecraft.item.ItemSign;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Facing;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleCommand;
import dan200.computercraft.api.turtle.TurtleAnimation;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.util.DirectionUtil;
import dan200.computercraft.shared.util.IEntityDropConsumer;
import dan200.computercraft.shared.util.InventoryUtil;
import dan200.computercraft.shared.util.WorldUtil;

import java.util.Arrays;

public class TurtlePlaceCommand implements ITurtleCommand {

    private final InteractDirection m_direction;
    private final Object[] m_extraArguments;
    private final String COMMANDNAME = "place";

    public TurtlePlaceCommand(InteractDirection direction, Object[] arguments) {
        this.m_direction = direction;
        this.m_extraArguments = arguments;
    }

    @Override
    public TurtleCommandResult execute(ITurtleAccess turtle) {
        if (Arrays.asList(ComputerCraft.turtleDisabledActions).contains(COMMANDNAME)) {
            return TurtleCommandResult.failure("Turtle action \"" + COMMANDNAME + "\" is disabled");
        }

        ItemStack stack = turtle.getInventory()
            .getStackInSlot(turtle.getSelectedSlot());
        if (stack == null) {
            return TurtleCommandResult.failure("No items to place");
        } else {
            int direction = this.m_direction.toWorldDir(turtle);
            World world = turtle.getWorld();
            ChunkCoordinates coordinates = WorldUtil.moveCoords(turtle.getPosition(), direction);
            Block previousBlock;
            int previousMetadata;
            if (WorldUtil.isBlockInWorld(world, coordinates)) {
                previousBlock = world.getBlock(coordinates.posX, coordinates.posY, coordinates.posZ);
                previousMetadata = world.getBlockMetadata(coordinates.posX, coordinates.posY, coordinates.posZ);
            } else {
                previousBlock = null;
                previousMetadata = -1;
            }

            String[] errorMessage = new String[1];
            ItemStack remainder = deploy(stack, turtle, direction, this.m_extraArguments, errorMessage);
            if (remainder != stack) {
                turtle.getInventory()
                    .setInventorySlotContents(turtle.getSelectedSlot(), remainder);
                turtle.getInventory()
                    .markDirty();
                if (turtle instanceof TurtleBrain && previousBlock != null) {
                    TurtleBrain brain = (TurtleBrain) turtle;
                    brain.saveBlockChange(coordinates, previousBlock, previousMetadata);
                }

                turtle.playAnimation(TurtleAnimation.Wait);
                return TurtleCommandResult.success();
            } else if (errorMessage[0] != null) {
                return TurtleCommandResult.failure(errorMessage[0]);
            } else {
                return stack.getItem() instanceof ItemBlock ? TurtleCommandResult.failure("Cannot place block here")
                    : TurtleCommandResult.failure("Cannot place item here");
            }
        }
    }

    public static ItemStack deploy(ItemStack stack, ITurtleAccess turtle, int direction, Object[] extraArguments,
        String[] o_errorMessage) {
        ChunkCoordinates playerPosition = WorldUtil.moveCoords(turtle.getPosition(), direction);
        TurtlePlayer turtlePlayer = createPlayer(turtle, playerPosition, direction);
        ItemStack remainder = deployOnEntity(stack, turtle, turtlePlayer, direction, extraArguments, o_errorMessage);
        if (remainder != stack) {
            return remainder;
        } else {
            ChunkCoordinates position = turtle.getPosition();
            ChunkCoordinates newPosition = WorldUtil.moveCoords(position, direction);
            remainder = deployOnBlock(
                stack,
                turtle,
                turtlePlayer,
                newPosition,
                Facing.oppositeSide[direction],
                extraArguments,
                true,
                o_errorMessage);
            if (remainder != stack) {
                return remainder;
            } else {
                remainder = deployOnBlock(
                    stack,
                    turtle,
                    turtlePlayer,
                    WorldUtil.moveCoords(newPosition, direction),
                    Facing.oppositeSide[direction],
                    extraArguments,
                    false,
                    o_errorMessage);
                if (remainder != stack) {
                    return remainder;
                } else {
                    if (direction >= 2) {
                        remainder = deployOnBlock(
                            stack,
                            turtle,
                            turtlePlayer,
                            WorldUtil.moveCoords(newPosition, 0),
                            1,
                            extraArguments,
                            false,
                            o_errorMessage);
                        if (remainder != stack) {
                            return remainder;
                        }
                    }

                    remainder = deployOnBlock(
                        stack,
                        turtle,
                        turtlePlayer,
                        position,
                        direction,
                        extraArguments,
                        false,
                        o_errorMessage);
                    return remainder != stack ? remainder : stack;
                }
            }
        }
    }

    public static TurtlePlayer createPlayer(ITurtleAccess turtle, ChunkCoordinates position, int direction) {
        TurtlePlayer turtlePlayer = new TurtlePlayer((WorldServer) turtle.getWorld());
        orientPlayer(turtle, turtlePlayer, position, direction);
        return turtlePlayer;
    }

    public static void orientPlayer(ITurtleAccess turtle, TurtlePlayer turtlePlayer, ChunkCoordinates position,
        int direction) {
        turtlePlayer.posX = position.posX + 0.5;
        turtlePlayer.posY = position.posY + 0.5;
        turtlePlayer.posZ = position.posZ + 0.5;
        if (turtle.getPosition()
            .equals(position)) {
            turtlePlayer.posX = turtlePlayer.posX + 0.48 * Facing.offsetsXForSide[direction];
            turtlePlayer.posY = turtlePlayer.posY + 0.48 * Facing.offsetsYForSide[direction];
            turtlePlayer.posZ = turtlePlayer.posZ + 0.48 * Facing.offsetsZForSide[direction];
        }

        if (direction > 2) {
            turtlePlayer.rotationYaw = DirectionUtil.toYawAngle(direction);
            turtlePlayer.rotationPitch = 0.0F;
        } else {
            turtlePlayer.rotationYaw = DirectionUtil.toYawAngle(turtle.getDirection());
            turtlePlayer.rotationPitch = DirectionUtil.toPitchAngle(direction);
        }

        turtlePlayer.prevPosX = turtlePlayer.posX;
        turtlePlayer.prevPosY = turtlePlayer.posY;
        turtlePlayer.prevPosZ = turtlePlayer.posZ;
        turtlePlayer.prevRotationPitch = turtlePlayer.rotationPitch;
        turtlePlayer.prevRotationYaw = turtlePlayer.rotationYaw;
    }

    private static ItemStack deployOnEntity(ItemStack stack, final ITurtleAccess turtle, TurtlePlayer turtlePlayer,
        int direction, Object[] extraArguments, String[] o_errorMessage) {
        final World world = turtle.getWorld();
        final ChunkCoordinates position = turtle.getPosition();
        Vec3 turtlePos = Vec3.createVectorHelper(turtlePlayer.posX, turtlePlayer.posY, turtlePlayer.posZ);
        Vec3 rayDir = turtlePlayer.getLook(1.0F);
        Vec3 rayStart = turtlePos.addVector(rayDir.xCoord * 0.4, rayDir.yCoord * 0.4, rayDir.zCoord * 0.4);
        Entity hitEntity = WorldUtil.rayTraceEntities(world, rayStart, rayDir, 1.1);
        if (hitEntity == null) {
            return stack;
        } else {
            Item item = stack.getItem();
            ItemStack stackCopy = stack.copy();
            turtlePlayer.loadInventory(stackCopy);
            ComputerCraft.setEntityDropConsumer(hitEntity, new IEntityDropConsumer() {

                @Override
                public void consumeDrop(Entity entity, ItemStack drop) {
                    ItemStack remainder = InventoryUtil.storeItems(
                        drop,
                        turtle.getInventory(),
                        0,
                        turtle.getInventory()
                            .getSizeInventory(),
                        turtle.getSelectedSlot());
                    if (remainder != null) {
                        WorldUtil.dropItemStack(
                            remainder,
                            world,
                            position.posX,
                            position.posY,
                            position.posZ,
                            Facing.oppositeSide[turtle.getDirection()]);
                    }
                }
            });
            boolean placed = false;
            if (hitEntity.interactFirst(turtlePlayer)) {
                placed = true;
            } else if (hitEntity instanceof EntityLivingBase) {
                placed = item.itemInteractionForEntity(stackCopy, turtlePlayer, (EntityLivingBase) hitEntity);
                if (placed) {
                    turtlePlayer.loadInventory(stackCopy);
                }
            }

            ComputerCraft.clearEntityDropConsumer(hitEntity);
            ItemStack remainder = turtlePlayer.unloadInventory(turtle);
            if (!placed && remainder != null && ItemStack.areItemStacksEqual(stack, remainder)) {
                return stack;
            } else {
                return remainder != null && remainder.stackSize > 0 ? remainder : null;
            }
        }
    }

    private static boolean canDeployOnBlock(ItemStack stack, ITurtleAccess turtle, TurtlePlayer player,
        ChunkCoordinates position, int side, boolean allowReplaceable, String[] o_errorMessage) {
        World world = turtle.getWorld();
        if (WorldUtil.isBlockInWorld(world, position) && !world.isAirBlock(position.posX, position.posY, position.posZ)
            && (!(stack.getItem() instanceof ItemBlock) || !WorldUtil.isLiquidBlock(world, position))) {
            Block block = world.getBlock(position.posX, position.posY, position.posZ);
            int metadata = world.getBlockMetadata(position.posX, position.posY, position.posZ);
            boolean replaceable = block.isReplaceable(world, position.posX, position.posY, position.posZ);
            if (allowReplaceable || !replaceable) {
                if (ComputerCraft.turtlesObeyBlockProtection) {
                    boolean editable = true;
                    if (replaceable) {
                        editable = ComputerCraft
                            .isBlockEditable(world, position.posX, position.posY, position.posZ, player);
                    } else {
                        ChunkCoordinates shiftedPos = WorldUtil.moveCoords(position, side);
                        if (WorldUtil.isBlockInWorld(world, shiftedPos)) {
                            editable = ComputerCraft
                                .isBlockEditable(world, shiftedPos.posX, shiftedPos.posY, shiftedPos.posZ, player);
                        }
                    }

                    if (!editable) {
                        if (o_errorMessage != null) {
                            o_errorMessage[0] = "Cannot place in protected area";
                        }

                        return false;
                    }
                }

                if (block.canCollideCheck(metadata, true)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static ItemStack deployOnBlock(ItemStack stack, ITurtleAccess turtle, TurtlePlayer turtlePlayer,
        ChunkCoordinates position, int side, Object[] extraArguments, boolean allowReplace, String[] o_errorMessage) {
        if (!canDeployOnBlock(stack, turtle, turtlePlayer, position, side, allowReplace, o_errorMessage)) {
            return stack;
        } else {
            int playerDir = Facing.oppositeSide[side];
            ChunkCoordinates playerPosition = WorldUtil.moveCoords(position, side);
            orientPlayer(turtle, turtlePlayer, playerPosition, playerDir);
            float hitX = 0.5F + Facing.offsetsXForSide[side] * 0.5F;
            float hitY = 0.5F + Facing.offsetsYForSide[side] * 0.5F;
            float hitZ = 0.5F + Facing.offsetsZForSide[side] * 0.5F;
            if (Math.abs(hitY - 0.5F) < 0.01F) {
                hitY = 0.45F;
            }

            Item item = stack.getItem();
            ItemStack stackCopy = stack.copy();
            turtlePlayer.loadInventory(stackCopy);
            boolean placed = false;
            if (item.onItemUseFirst(
                stackCopy,
                turtlePlayer,
                turtle.getWorld(),
                position.posX,
                position.posY,
                position.posZ,
                side,
                hitX,
                hitY,
                hitZ)
                || item.onItemUse(
                    stackCopy,
                    turtlePlayer,
                    turtle.getWorld(),
                    position.posX,
                    position.posY,
                    position.posZ,
                    side,
                    hitX,
                    hitY,
                    hitZ)) {
                placed = true;
                turtlePlayer.loadInventory(stackCopy);
            } else if (item instanceof ItemBucket || item instanceof ItemBoat
                || item instanceof ItemLilyPad
                || item instanceof ItemGlassBottle) {
                    ItemStack result = item.onItemRightClick(stackCopy, turtle.getWorld(), turtlePlayer);
                    if (!ItemStack.areItemStacksEqual(stack, result)) {
                        placed = true;
                        turtlePlayer.loadInventory(result);
                    }
                }

            if (placed && item instanceof ItemSign
                && extraArguments != null
                && extraArguments.length >= 1
                && extraArguments[0] instanceof String) {
                World world = turtle.getWorld();
                TileEntity tile = world.getTileEntity(position.posX, position.posY, position.posZ);
                if (tile == null) {
                    ChunkCoordinates newPosition = WorldUtil.moveCoords(position, side);
                    tile = world.getTileEntity(newPosition.posX, newPosition.posY, newPosition.posZ);
                }

                if (tile != null && tile instanceof TileEntitySign) {
                    TileEntitySign signTile = (TileEntitySign) tile;
                    String s = (String) extraArguments[0];
                    String[] split = s.split("\n");
                    String[] signText = new String[] { "", "", "", "" };
                    int firstLine = split.length <= 2 ? 1 : 0;

                    for (int i = 0; i < Math.min(split.length, signText.length); i++) {
                        if (split[i].length() > 15) {
                            signText[firstLine + i] = split[i].substring(0, 15);
                        } else {
                            signText[firstLine + i] = split[i];
                        }
                    }

                    signTile.signText = signText;
                    signTile.markDirty();
                    world.markBlockForUpdate(signTile.xCoord, signTile.yCoord, signTile.zCoord);
                }
            }

            ItemStack remainder = turtlePlayer.unloadInventory(turtle);
            if (!placed && remainder != null && ItemStack.areItemStacksEqual(stack, remainder)) {
                return stack;
            } else {
                return remainder != null && remainder.stackSize > 0 ? remainder : null;
            }
        }
    }
}
