package dan200.computercraft.shared.turtle.upgrades;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleUpgradeType;
import dan200.computercraft.api.turtle.TurtleVerb;
import dan200.computercraft.api.core.TurtleBrain;
import dan200.computercraft.api.core.TurtlePlaceCommand;
import dan200.computercraft.api.core.TurtlePlayer;
import dan200.computercraft.shared.util.IEntityDropConsumer;
import dan200.computercraft.shared.util.InventoryUtil;
import dan200.computercraft.shared.util.WorldUtil;
import java.util.ArrayList;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;

public class TurtleTool implements ITurtleUpgrade {
   private int m_id;
   private String m_adjective;
   protected ItemStack m_item;

   public TurtleTool(int id, String adjective, Item item) {
      this.m_id = id;
      this.m_adjective = adjective;
      this.m_item = new ItemStack(item, 1, 0);
   }

   @Override
   public int getUpgradeID() {
      return this.m_id;
   }

   @Override
   public String getUnlocalisedAdjective() {
      return this.m_adjective;
   }

   @Override
   public TurtleUpgradeType getType() {
      return TurtleUpgradeType.Tool;
   }

   @Override
   public ItemStack getCraftingItem() {
      return this.m_item.copy();
   }

   @Override
   public IIcon getIcon(ITurtleAccess turtle, TurtleSide side) {
      return this.m_item.getItem().func_77650_f(this.m_item);
   }

   @Override
   public IPeripheral createPeripheral(ITurtleAccess turtle, TurtleSide side) {
      return null;
   }

   @Override
   public void update(ITurtleAccess turtle, TurtleSide side) {
   }

   @Override
   public TurtleCommandResult useTool(ITurtleAccess turtle, TurtleSide side, TurtleVerb verb, int direction) {
      switch (verb) {
         case Attack:
            return this.attack(turtle, direction);
         case Dig:
            return this.dig(turtle, direction);
         default:
            return TurtleCommandResult.failure("Unsupported action");
      }
   }

   protected boolean canBreakBlock(World world, int x, int y, int z) {
      Block block = world.getBlock(x, y, z);
      return !block.isAir(world, x, y, z) && block != Blocks.bedrock && !(block.getBlockHardness(world, x, y, z) <= -1.0F);
   }

   protected boolean canHarvestBlock(World world, int x, int y, int z) {
      Block block = world.getBlock(x, y, z);
      int meta = world.getBlockMetadata(x, y, z);
      TurtlePlayer turtlePlayer = new TurtlePlayer((WorldServer)world);
      turtlePlayer.loadInventory(this.m_item.copy());
      return ForgeHooks.canHarvestBlock(block, turtlePlayer, meta);
   }

   protected float getDamageMultiplier() {
      return 3.0F;
   }

   private TurtleCommandResult attack(final ITurtleAccess turtle, int direction) {
      final World world = turtle.getWorld();
      final ChunkCoordinates position = turtle.getPosition();
      TurtlePlayer turtlePlayer = TurtlePlaceCommand.createPlayer(turtle, position, direction);
      Vec3 turtlePos = Vec3.createVectorHelper(turtlePlayer.posX, turtlePlayer.posY, turtlePlayer.posZ);
      Vec3 rayDir = turtlePlayer.getLook(1.0F);
      Vec3 rayStart = turtlePos.addVector(rayDir.xCoord * 0.4, rayDir.yCoord * 0.4, rayDir.zCoord * 0.4);
      Entity hitEntity = WorldUtil.rayTraceEntities(world, rayStart, rayDir, 1.1);
      if (hitEntity != null) {
         ItemStack stackCopy = this.m_item.copy();
         turtlePlayer.loadInventory(stackCopy);
         ComputerCraft.setEntityDropConsumer(
            hitEntity,
            new IEntityDropConsumer() {
               @Override
               public void consumeDrop(Entity entity, ItemStack drop) {
                  ItemStack remainder = InventoryUtil.storeItems(
                     drop, turtle.getInventory(), 0, turtle.getInventory().getSizeInventory(), turtle.getSelectedSlot()
                  );
                  if (remainder != null) {
                     WorldUtil.dropItemStack(
                        remainder, world, position.posX, position.posY, position.posZ, Facing.oppositeSide[turtle.getDirection()]
                     );
                  }
               }
            }
         );
         boolean attacked = false;
         if (hitEntity.func_70075_an() && !hitEntity.hitByEntity(turtlePlayer)) {
            float damage = (float)turtlePlayer.getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue();
            damage *= this.getDamageMultiplier();
            if (damage > 0.0F && hitEntity.attackEntityFrom(DamageSource.causePlayerDamage(turtlePlayer), damage)) {
               attacked = true;
            }
         }

         ComputerCraft.clearEntityDropConsumer(hitEntity);
         if (attacked) {
            turtlePlayer.unloadInventory(turtle);
            return TurtleCommandResult.success();
         }
      }

      return TurtleCommandResult.failure("Nothing to attack here");
   }

   private TurtleCommandResult dig(ITurtleAccess turtle, int direction) {
      World world = turtle.getWorld();
      ChunkCoordinates position = turtle.getPosition();
      ChunkCoordinates newPosition = WorldUtil.moveCoords(position, direction);
      if (WorldUtil.isBlockInWorld(world, newPosition)
         && !world.isAirBlock(newPosition.posX, newPosition.posY, newPosition.posZ)
         && !WorldUtil.isLiquidBlock(world, newPosition)) {
         if (ComputerCraft.turtlesObeyBlockProtection) {
            TurtlePlayer turtlePlayer = TurtlePlaceCommand.createPlayer(turtle, position, direction);
            if (!ComputerCraft.isBlockEditable(world, newPosition.posX, newPosition.posY, newPosition.posZ, turtlePlayer)) {
               return TurtleCommandResult.failure("Cannot break protected block");
            }
         }

         if (!this.canBreakBlock(world, newPosition.posX, newPosition.posY, newPosition.posZ)) {
            return TurtleCommandResult.failure("Unbreakable block detected");
         } else {
            if (this.canHarvestBlock(world, newPosition.posX, newPosition.posY, newPosition.posZ)) {
               ArrayList<ItemStack> items = this.getBlockDropped(world, newPosition.posX, newPosition.posY, newPosition.posZ);
               if (items != null && items.size() > 0) {
                  for (ItemStack stack : items) {
                     ItemStack remainder = InventoryUtil.storeItems(
                        stack, turtle.getInventory(), 0, turtle.getInventory().getSizeInventory(), turtle.getSelectedSlot()
                     );
                     if (remainder != null) {
                        WorldUtil.dropItemStack(remainder, world, position.posX, position.posY, position.posZ, direction);
                     }
                  }
               }
            }

            Block previousBlock = world.getBlock(newPosition.posX, newPosition.posY, newPosition.posZ);
            int previousMetadata = world.getBlockMetadata(newPosition.posX, newPosition.posY, newPosition.posZ);
            if (previousBlock != null) {
               world.playSoundEffect(
                  newPosition.posX + 0.5,
                  newPosition.posY + 0.5,
                  newPosition.posZ + 0.5,
                  previousBlock.field_149762_H.func_150495_a(),
                  (previousBlock.field_149762_H.func_150497_c() + 1.0F) / 2.0F,
                  previousBlock.field_149762_H.func_150494_d() * 0.8F
               );
            }

            world.setBlockToAir(newPosition.posX, newPosition.posY, newPosition.posZ);
            if (turtle instanceof TurtleBrain && previousBlock != null) {
               TurtleBrain brain = (TurtleBrain)turtle;
               brain.saveBlockChange(newPosition, previousBlock, previousMetadata);
            }

            return TurtleCommandResult.success();
         }
      } else {
         return TurtleCommandResult.failure("Nothing to dig here");
      }
   }

   private ArrayList<ItemStack> getBlockDropped(World world, int x, int y, int z) {
      Block block = world.getBlock(x, y, z);
      int metadata = world.getBlockMetadata(x, y, z);
      return block.getDrops(world, x, y, z, metadata, 0);
   }
}
