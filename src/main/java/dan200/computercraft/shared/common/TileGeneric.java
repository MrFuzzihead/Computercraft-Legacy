package dan200.computercraft.shared.common;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.network.ComputerCraftPacket;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IIcon;

public abstract class TileGeneric extends TileEntity {
   public void requestTileEntityUpdate() {
      if (this.getWorldObj().isRemote) {
         ComputerCraftPacket packet = new ComputerCraftPacket();
         packet.m_packetType = 9;
         packet.m_dataInt = new int[]{this.xCoord, this.yCoord, this.zCoord};
         ComputerCraft.sendToServer(packet);
      }
   }

   public void destroy() {
   }

   public ChunkCoordinates getCoords() {
      return new ChunkCoordinates(this.xCoord, this.yCoord, this.zCoord);
   }

   public BlockGeneric getBlock() {
      Block block = this.getWorldObj().getBlock(this.xCoord, this.yCoord, this.zCoord);
      return block != null && block instanceof BlockGeneric ? (BlockGeneric)block : null;
   }

   protected final int getMetadata() {
      return this.getWorldObj().getBlockMetadata(this.xCoord, this.yCoord, this.zCoord);
   }

   public final void updateBlock() {
      this.getWorldObj().markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
      this.getWorldObj().notifyBlockChange(this.xCoord, this.yCoord, this.zCoord, this);
   }

   protected final void setMetadata(int metadata) {
      this.getWorldObj().setBlockMetadataWithNotify(this.xCoord, this.yCoord, this.zCoord, metadata, 3);
   }

   public void getDroppedItems(List<ItemStack> drops, int fortune, boolean creative, boolean silkTouch) {
   }

   public ItemStack getPickedItem() {
      return null;
   }

   public boolean onActivate(EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
      return false;
   }

   public void onNeighbourChange() {
   }

   public boolean isSolidOnSide(int side) {
      return true;
   }

   public boolean isImmuneToExplosion(Entity exploder) {
      return false;
   }

   public AxisAlignedBB getBounds() {
      return AxisAlignedBB.getBoundingBox(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
   }

   public void getCollisionBounds(List<AxisAlignedBB> bounds) {
      bounds.add(this.getBounds());
   }

   public boolean getRedstoneConnectivity(int side) {
      return false;
   }

   public int getRedstoneOutput(int side) {
      return 0;
   }

   public boolean getBundledRedstoneConnectivity(int side) {
      return false;
   }

   public int getBundledRedstoneOutput(int side) {
      return 0;
   }

   public abstract IIcon getTexture(int var1);

   protected double getInteractRange(EntityPlayer player) {
      return 8.0;
   }

   public boolean isUsable(EntityPlayer player, boolean ignoreRange) {
      if (player == null || !player.isEntityAlive() || this.getWorldObj().getTileEntity(this.xCoord, this.yCoord, this.zCoord) != this) {
         return false;
      } else if (ignoreRange) {
         return true;
      } else {
         double range = this.getInteractRange(player);
         return player.getEntityWorld() == this.getWorldObj()
            && player.getDistanceSq(this.xCoord + 0.5, this.yCoord + 0.5, this.zCoord + 0.5) <= range * range;
      }
   }

   protected void writeDescription(NBTTagCompound nbttagcompound) {
   }

   protected void readDescription(NBTTagCompound nbttagcompound) {
   }

   public final void sendBlockEvent(int eventID) {
      this.sendBlockEvent(eventID, 0);
   }

   public final void sendBlockEvent(int eventID, int eventParameter) {
      this.getWorldObj()
         .addBlockEvent(
            this.xCoord,
            this.yCoord,
            this.zCoord,
            this.getWorldObj().getBlock(this.xCoord, this.yCoord, this.zCoord),
            eventID,
            eventParameter
         );
   }

   public void onBlockEvent(int eventID, int eventParameter) {
   }

   public final Packet getUpdatePacket() {
      NBTTagCompound nbttagcompound = new NBTTagCompound();
      this.writeDescription(nbttagcompound);
      return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 0, nbttagcompound);
   }

   public final void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
      switch (packet.func_148853_f()) {
         case 0:
            NBTTagCompound nbttagcompound = packet.func_148857_g();
            this.readDescription(nbttagcompound);
      }
   }
}
