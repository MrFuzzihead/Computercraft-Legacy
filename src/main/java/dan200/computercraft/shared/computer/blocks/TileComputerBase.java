package dan200.computercraft.shared.computer.blocks;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.common.IDirectionalTile;
import dan200.computercraft.shared.common.ITerminal;
import dan200.computercraft.shared.common.TileGeneric;
import dan200.computercraft.shared.computer.core.ClientComputer;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.core.IComputer;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.util.DirectionUtil;
import dan200.computercraft.shared.util.PeripheralUtil;
import dan200.computercraft.shared.util.RedstoneUtil;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Facing;

public abstract class TileComputerBase extends TileGeneric implements IComputerTile, IDirectionalTile {
   protected int m_instanceID = -1;
   protected int m_computerID = -1;
   protected String m_label = null;
   protected boolean m_on = false;
   protected boolean m_startOn = false;
   protected boolean m_fresh;

   protected TileComputerBase() {
   }

   public BlockComputerBase getBlock() {
      Block block = super.getBlock();
      return block != null && block instanceof BlockComputerBase ? (BlockComputerBase)block : null;
   }

   protected void unload() {
      if (this.m_instanceID >= 0) {
         if (!this.getWorldObj().isRemote) {
            ComputerCraft.serverComputerRegistry.remove(this.m_instanceID);
         }

         this.m_instanceID = -1;
      }
   }

   @Override
   public void destroy() {
      this.unload();

      for (int dir = 0; dir < 6; dir++) {
         RedstoneUtil.propogateRedstoneOutput(this.getWorldObj(), this.xCoord, this.yCoord, this.zCoord, dir);
      }
   }

   public void onChunkUnload() {
      this.unload();
   }

   public void invalidate() {
      this.unload();
      super.invalidate();
   }

   public abstract void openGUI(EntityPlayer var1);

   protected boolean canNameWithTag(EntityPlayer player) {
      return false;
   }

   protected boolean onDefaultComputerInteract(EntityPlayer player) {
      if (!this.getWorldObj().isRemote && this.isUsable(player, false)) {
         this.createServerComputer().turnOn();
         this.openGUI(player);
      }

      return true;
   }

   @Override
   public boolean onActivate(EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
      ItemStack currentItem = player.getHeldItem();
      if (currentItem != null && currentItem.getItem() == Items.name_tag && this.canNameWithTag(player)) {
         if (!this.getWorldObj().isRemote) {
            if (currentItem.hasDisplayName()) {
               this.setLabel(currentItem.getDisplayName());
            } else {
               this.setLabel(null);
            }

            currentItem.stackSize--;
         }

         return true;
      } else {
         return !player.isSneaking() ? this.onDefaultComputerInteract(player) : false;
      }
   }

   @Override
   public boolean getRedstoneConnectivity(int side) {
      int localDir = this.remapLocalSide(DirectionUtil.toLocal(this, side));
      return !this.isRedstoneBlockedOnSide(localDir);
   }

   @Override
   public int getRedstoneOutput(int side) {
      int localDir = this.remapLocalSide(DirectionUtil.toLocal(this, side));
      if (!this.isRedstoneBlockedOnSide(localDir) && !this.getWorldObj().isRemote) {
         ServerComputer computer = this.getServerComputer();
         if (computer != null) {
            return computer.getRedstoneOutput(localDir);
         }
      }

      return 0;
   }

   @Override
   public boolean getBundledRedstoneConnectivity(int side) {
      int localDir = this.remapLocalSide(DirectionUtil.toLocal(this, side));
      return !this.isRedstoneBlockedOnSide(localDir);
   }

   @Override
   public int getBundledRedstoneOutput(int side) {
      int localDir = this.remapLocalSide(DirectionUtil.toLocal(this, side));
      if (!this.isRedstoneBlockedOnSide(localDir) && !this.getWorldObj().isRemote) {
         ServerComputer computer = this.getServerComputer();
         if (computer != null) {
            return computer.getBundledRedstoneOutput(localDir);
         }
      }

      return 0;
   }

   @Override
   public void onNeighbourChange() {
      this.updateInput();
   }

   public void update() {
      if (!this.getWorldObj().isRemote) {
         ServerComputer computer = this.createServerComputer();
         if (computer != null) {
            if (this.m_startOn || (this.m_fresh && this.m_on)) {
               computer.turnOn();
               this.m_startOn = false;
            }

            computer.keepAlive();
            if (computer.hasOutputChanged()) {
               this.updateOutput();
            }
			this.m_fresh = false;
            this.m_computerID = computer.getID();
            this.m_label = computer.getLabel();
            this.m_on = computer.isOn();
         }
      } else {
         ClientComputer computer = this.createClientComputer();
         if (computer != null && computer.hasOutputChanged()) {
            this.updateBlock();
         }
      }
   }

   public void writeToNBT(NBTTagCompound nbttagcompound) {
      super.writeToNBT(nbttagcompound);
      if (this.m_computerID >= 0) {
         nbttagcompound.setInteger("computerID", this.m_computerID);
      }

      if (this.m_label != null) {
         nbttagcompound.setString("label", this.m_label);
      }

      nbttagcompound.setBoolean("on", this.m_on);
   }

   public void readFromNBT(NBTTagCompound nbttagcompound) {
      super.readFromNBT(nbttagcompound);
      int id = -1;
      if (nbttagcompound.hasKey("computerID")) {
         id = nbttagcompound.getInteger("computerID");
      } else if (nbttagcompound.hasKey("userDir")) {
         String userDir = nbttagcompound.getString("userDir");

         try {
            id = Integer.parseInt(userDir);
         } catch (NumberFormatException var5) {
         }
      }

      this.m_computerID = id;
      if (nbttagcompound.hasKey("label")) {
         this.m_label = nbttagcompound.getString("label");
      } else {
         this.m_label = null;
      }

      this.m_startOn = nbttagcompound.getBoolean("on");
      this.m_on = this.m_startOn;
   }

   protected boolean isPeripheralBlockedOnSide(int localSide) {
      return false;
   }

   protected boolean isRedstoneBlockedOnSide(int localSide) {
      return false;
   }

   protected int remapLocalSide(int localSide) {
      return localSide;
   }

   public void updateInput() {
       if (this.getWorldObj() == null || this.getWorldObj().isRemote) {
           return;
       }
         ServerComputer computer = this.getServerComputer();
         if (computer != null) {
            for (int dir = 0; dir < 6; dir++) {
               int offsetX = this.xCoord + Facing.offsetsXForSide[dir];
               int offsetY = this.yCoord + Facing.offsetsYForSide[dir];
               int offsetZ = this.zCoord + Facing.offsetsZForSide[dir];
               int offsetSide = Facing.oppositeSide[dir];
               int localDir = this.remapLocalSide(DirectionUtil.toLocal(this, dir));
               if (!this.isRedstoneBlockedOnSide(localDir)) {
                  computer.setRedstoneInput(localDir, RedstoneUtil.getRedstoneOutput(this.getWorldObj(), offsetX, offsetY, offsetZ, offsetSide));
                  computer.setBundledRedstoneInput(localDir, RedstoneUtil.getBundledRedstoneOutput(this.getWorldObj(), offsetX, offsetY, offsetZ, offsetSide));
               }

               if (!this.isPeripheralBlockedOnSide(localDir)) {
                  computer.setPeripheral(localDir, PeripheralUtil.getPeripheral(this.getWorldObj(), offsetX, offsetY, offsetZ, offsetSide));
               }
            }
         }
   }

   public void updateOutput() {
      this.updateBlock();

      for (int dir = 0; dir < 6; dir++) {
         RedstoneUtil.propogateRedstoneOutput(this.getWorldObj(), this.xCoord, this.yCoord, this.zCoord, dir);
      }
   }

   protected abstract ServerComputer createComputer(int var1, int var2);

   @Override
   public ITerminal getTerminal() {
      return this.getComputer();
   }

   @Override
   public void setComputerID(int id) {
      if (!this.getWorldObj().isRemote && this.m_computerID != id) {
         this.m_computerID = id;
         ServerComputer computer = this.getServerComputer();
         if (computer != null) {
            computer.setID(this.m_computerID);
         }

         this.markDirty();
      }
   }

   @Override
   public void setLabel(String label) {
      if (!this.getWorldObj().isRemote) {
         this.createServerComputer().setLabel(label);
      }
   }

   @Override
   public IComputer createComputer() {
      return (IComputer)(this.getWorldObj().isRemote ? this.createClientComputer() : this.createServerComputer());
   }

   @Override
   public IComputer getComputer() {
      return (IComputer)(this.getWorldObj().isRemote ? this.getClientComputer() : this.getServerComputer());
   }

   @Override
   public ComputerFamily getFamily() {
      return this.getBlock().getFamily(this.getWorldObj(), this.xCoord, this.yCoord, this.zCoord);
   }

   public ServerComputer createServerComputer() {
      if (!this.getWorldObj().isRemote) {
         boolean changed = false;
         if (this.m_instanceID < 0) {
            this.m_instanceID = ComputerCraft.serverComputerRegistry.getUnusedInstanceID();
            changed = true;
         }

         if (!ComputerCraft.serverComputerRegistry.contains(this.m_instanceID)) {
            ServerComputer computer = this.createComputer(this.m_instanceID, this.m_computerID);
            ComputerCraft.serverComputerRegistry.add(this.m_instanceID, computer);
            this.m_fresh = true;
			changed = true;
         }

         if (changed) {
            this.updateBlock();
            this.updateInput();
         }

         return ComputerCraft.serverComputerRegistry.get(this.m_instanceID);
      } else {
         return null;
      }
   }

   public ServerComputer getServerComputer() {
      return !this.getWorldObj().isRemote ? ComputerCraft.serverComputerRegistry.get(this.m_instanceID) : null;
   }

   public ClientComputer createClientComputer() {
      if (this.getWorldObj().isRemote && this.m_instanceID >= 0) {
         if (!ComputerCraft.clientComputerRegistry.contains(this.m_instanceID)) {
            ComputerCraft.clientComputerRegistry.add(this.m_instanceID, new ClientComputer(this.m_instanceID));
         }

         return ComputerCraft.clientComputerRegistry.get(this.m_instanceID);
      } else {
         return null;
      }
   }

   public ClientComputer getClientComputer() {
      return this.getWorldObj().isRemote ? ComputerCraft.clientComputerRegistry.get(this.m_instanceID) : null;
   }

   @Override
   public void writeDescription(NBTTagCompound nbttagcompound) {
      super.writeDescription(nbttagcompound);
      nbttagcompound.setInteger("instanceID", this.createServerComputer().getInstanceID());
   }

   @Override
   public void readDescription(NBTTagCompound nbttagcompound) {
      super.readDescription(nbttagcompound);
      this.m_instanceID = nbttagcompound.getInteger("instanceID");
   }

   protected void transferStateFrom(TileComputerBase copy) {
      if (copy.m_computerID != this.m_computerID || copy.m_instanceID != this.m_instanceID) {
         this.unload();
         this.m_instanceID = copy.m_instanceID;
         this.m_computerID = copy.m_computerID;
         this.m_label = copy.m_label;
         this.m_on = copy.m_on;
         this.m_startOn = copy.m_startOn;
         this.updateBlock();
      }

      copy.m_instanceID = -1;
   }
}
