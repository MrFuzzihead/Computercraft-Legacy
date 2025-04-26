package dan200.computercraft.shared.computer.core;

import com.google.common.base.Objects;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.common.ClientTerminal;
import dan200.computercraft.shared.network.ComputerCraftPacket;
import dan200.computercraft.shared.network.INetworkedThing;
import dan200.computercraft.shared.util.NBTUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

public class ClientComputer extends ClientTerminal implements IComputer, INetworkedThing {
   private final int m_instanceID;
   private int m_computerID;
   private String m_label;
   private boolean m_on;
   private boolean m_blinking;
   private boolean m_changed;
   private NBTTagCompound m_userData;
   private boolean m_changedLastFrame;

   public ClientComputer(int instanceID) {
      super(false);
      this.m_instanceID = instanceID;
      this.m_computerID = -1;
      this.m_label = null;
      this.m_on = false;
      this.m_blinking = false;
      this.m_changed = true;
      this.m_userData = null;
      this.m_changedLastFrame = false;
   }

   @Override
   public void update() {
      super.update();
      this.m_changedLastFrame = this.m_changed;
      this.m_changed = false;
   }

   public boolean hasOutputChanged() {
      return this.m_changedLastFrame;
   }

   public NBTTagCompound getUserData() {
      return this.m_userData;
   }

   public void requestState() {
      ComputerCraftPacket packet = new ComputerCraftPacket();
      packet.m_packetType = 5;
      packet.m_dataInt = new int[]{this.getInstanceID()};
      ComputerCraft.sendToServer(packet);
   }

   @Override
   public int getInstanceID() {
      return this.m_instanceID;
   }

   @Override
   public int getID() {
      return this.m_computerID;
   }

   @Override
   public String getLabel() {
      return this.m_label;
   }

   @Override
   public boolean isOn() {
      return this.m_on;
   }

   @Override
   public boolean isCursorDisplayed() {
      return this.m_on && this.m_blinking;
   }

   @Override
   public void turnOn() {
      ComputerCraftPacket packet = new ComputerCraftPacket();
      packet.m_packetType = 1;
      packet.m_dataInt = new int[]{this.m_instanceID};
      ComputerCraft.sendToServer(packet);
   }

   @Override
   public void shutdown() {
      ComputerCraftPacket packet = new ComputerCraftPacket();
      packet.m_packetType = 3;
      packet.m_dataInt = new int[]{this.m_instanceID};
      ComputerCraft.sendToServer(packet);
   }

   @Override
   public void reboot() {
      ComputerCraftPacket packet = new ComputerCraftPacket();
      packet.m_packetType = 2;
      packet.m_dataInt = new int[]{this.m_instanceID};
      ComputerCraft.sendToServer(packet);
   }

   @Override
   public void queueEvent(String event) {
      this.queueEvent(event, null);
   }

   @Override
   public void queueEvent(String event, Object[] arguments) {
      ComputerCraftPacket packet = new ComputerCraftPacket();
      packet.m_packetType = 4;
      packet.m_dataInt = new int[]{this.m_instanceID};
      packet.m_dataString = new String[]{event};
      if (arguments != null) {
         packet.m_dataNBT = NBTUtil.encodeObjects(arguments);
      }

      ComputerCraft.sendToServer(packet);
   }

   @Override
   public void readDescription(NBTTagCompound nbttagcompound) {
      super.readDescription(nbttagcompound);
      int oldID = this.m_computerID;
      String oldLabel = this.m_label;
      boolean oldOn = this.m_on;
      boolean oldBlinking = this.m_blinking;
      NBTTagCompound oldUserData = this.m_userData;
      this.m_computerID = nbttagcompound.getInteger("id");
      this.m_label = nbttagcompound.hasKey("label") ? nbttagcompound.getString("label") : null;
      this.m_on = nbttagcompound.getBoolean("on");
      this.m_blinking = nbttagcompound.getBoolean("blinking");
      if (nbttagcompound.hasKey("userData")) {
         this.m_userData = (NBTTagCompound)nbttagcompound.getCompoundTag("userData").copy();
      } else {
         this.m_userData = null;
      }

      if (this.m_computerID != oldID
         || this.m_on != oldOn
         || this.m_blinking != oldBlinking
         || !Objects.equal(this.m_label, oldLabel)
         || !Objects.equal(this.m_userData, oldUserData)) {
         this.m_changed = true;
      }
   }

   @Override
   public void handlePacket(ComputerCraftPacket packet, EntityPlayer sender) {
      switch (packet.m_packetType) {
         case 7:
            this.readDescription(packet.m_dataNBT);
      }
   }
}
