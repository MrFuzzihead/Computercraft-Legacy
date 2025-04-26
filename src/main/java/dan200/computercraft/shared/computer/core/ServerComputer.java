package dan200.computercraft.shared.computer.core;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.apis.IAPIEnvironment;
import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.IComputerEnvironment;
import dan200.computercraft.shared.common.ServerTerminal;
import dan200.computercraft.shared.network.ComputerCraftPacket;
import dan200.computercraft.shared.network.INetworkedThing;
import dan200.computercraft.shared.util.NBTUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

public class ServerComputer extends ServerTerminal implements IComputer, IComputerEnvironment, INetworkedThing {
   private final int m_instanceID;
   private World m_world;
   private ChunkCoordinates m_position;
   private final Computer m_computer;
   private NBTTagCompound m_userData;
   private boolean m_changed;
   private boolean m_changedLastFrame;
   private int m_ticksSincePing;

   public ServerComputer(World world, int computerID, String label, int instanceID, ComputerFamily family, int terminalWidth, int terminalHeight) {
      super(family != ComputerFamily.Normal, terminalWidth, terminalHeight);
      this.m_instanceID = instanceID;
      this.m_world = world;
      this.m_position = null;
      this.m_computer = new Computer(this, this.getTerminal(), computerID);
      this.m_computer.setLabel(label);
      this.m_userData = null;
      this.m_changed = false;
      this.m_changedLastFrame = false;
      this.m_ticksSincePing = 0;
   }

   public World getWorld() {
      return this.m_world;
   }

   public void setWorld(World world) {
      this.m_world = world;
   }

   public ChunkCoordinates getPosition() {
      return this.m_position;
   }

   public void setPosition(int x, int y, int z) {
      this.m_position = new ChunkCoordinates(x, y, z);
   }

   public IAPIEnvironment getAPIEnvironment() {
      return this.m_computer.getAPIEnvironment();
   }

   @Override
   public void update() {
      super.update();
      this.m_computer.advance(0.05);
      this.m_changedLastFrame = this.m_changed || this.m_computer.pollChanged();
      this.m_computer.clearChanged();
      this.m_changed = false;
      this.m_ticksSincePing++;
   }

   public void keepAlive() {
      this.m_ticksSincePing = 0;
   }

   public boolean hasTimedOut() {
      return this.m_ticksSincePing > 100;
   }

   public boolean hasOutputChanged() {
      return this.m_changedLastFrame;
   }

   public void unload() {
      this.m_computer.unload();
   }

   public NBTTagCompound getUserData() {
      if (this.m_userData == null) {
         this.m_userData = new NBTTagCompound();
      }

      return this.m_userData;
   }

   public void updateUserData() {
      this.m_changed = true;
   }

   public void broadcastState() {
      ComputerCraftPacket packet = new ComputerCraftPacket();
      packet.m_packetType = 7;
      packet.m_dataInt = new int[]{this.getInstanceID()};
      packet.m_dataNBT = new NBTTagCompound();
      this.writeDescription(packet.m_dataNBT);
      ComputerCraft.sendToAllPlayers(packet);
   }

   public void sendState(EntityPlayer player) {
      ComputerCraftPacket packet = new ComputerCraftPacket();
      packet.m_packetType = 7;
      packet.m_dataInt = new int[]{this.getInstanceID()};
      packet.m_dataNBT = new NBTTagCompound();
      this.writeDescription(packet.m_dataNBT);
      ComputerCraft.sendToPlayer(player, packet);
   }

   public void broadcastDelete() {
      ComputerCraftPacket packet = new ComputerCraftPacket();
      packet.m_packetType = 8;
      packet.m_dataInt = new int[]{this.getInstanceID()};
      ComputerCraft.sendToAllPlayers(packet);
   }

   public IWritableMount getRootMount() {
      return this.m_computer.getRootMount();
   }

   public int assignID() {
      return this.m_computer.assignID();
   }

   public void setID(int id) {
      this.m_computer.setID(id);
   }

   @Override
   public int getInstanceID() {
      return this.m_instanceID;
   }

   @Override
   public int getID() {
      return this.m_computer.getID();
   }

   @Override
   public String getLabel() {
      return this.m_computer.getLabel();
   }

   @Override
   public boolean isOn() {
      return this.m_computer.isOn();
   }

   @Override
   public boolean isCursorDisplayed() {
      return this.m_computer.isOn() && this.m_computer.isBlinking();
   }

   @Override
   public void turnOn() {
      this.m_computer.turnOn();
   }

   @Override
   public void shutdown() {
      this.m_computer.shutdown();
   }

   @Override
   public void reboot() {
      this.m_computer.reboot();
   }

   @Override
   public void queueEvent(String event) {
      this.queueEvent(event, null);
   }

   @Override
   public void queueEvent(String event, Object[] arguments) {
      this.m_computer.queueEvent(event, arguments);
   }

   public int getRedstoneOutput(int side) {
      return this.m_computer.getRedstoneOutput(side);
   }

   public void setRedstoneInput(int side, int level) {
      this.m_computer.setRedstoneInput(side, level);
   }

   public int getBundledRedstoneOutput(int side) {
      return this.m_computer.getBundledRedstoneOutput(side);
   }

   public void setBundledRedstoneInput(int side, int combination) {
      this.m_computer.setBundledRedstoneInput(side, combination);
   }

   public void addAPI(ILuaAPI api) {
      this.m_computer.addAPI(api);
   }

   public void setPeripheral(int side, IPeripheral peripheral) {
      this.m_computer.setPeripheral(side, peripheral);
   }

   public IPeripheral getPeripheral(int side) {
      return this.m_computer.getPeripheral(side);
   }

   public void setLabel(String label) {
      this.m_computer.setLabel(label);
   }

   @Override
   public double getTimeOfDay() {
      return (this.m_world.getWorldTime() + 6000L) % 24000L / 1000.0;
   }

   @Override
   public int getDay() {
      return (int)((this.m_world.getWorldTime() + 6000L) / 24000L) + 1;
   }

   @Override
   public IWritableMount createSaveDirMount(String subPath, long capacity) {
      return ComputerCraftAPI.createSaveDirMount(this.m_world, subPath, capacity);
   }

   @Override
   public IMount createResourceMount(String domain, String subPath) {
      return ComputerCraftAPI.createResourceMount(ComputerCraft.class, domain, subPath);
   }

   @Override
   public long getComputerSpaceLimit() {
      return ComputerCraft.computerSpaceLimit;
   }

   @Override
   public int assignNewID() {
      return ComputerCraft.createUniqueNumberedSaveDir(this.m_world, "computer");
   }

   @Override
   public void writeDescription(NBTTagCompound nbttagcompound) {
      super.writeDescription(nbttagcompound);
      nbttagcompound.setInteger("id", this.m_computer.getID());
      String label = this.m_computer.getLabel();
      if (label != null) {
         nbttagcompound.setString("label", label);
      }

      nbttagcompound.setBoolean("on", this.m_computer.isOn());
      nbttagcompound.setBoolean("blinking", this.m_computer.isBlinking());
      if (this.m_userData != null) {
         nbttagcompound.setTag("userData", this.m_userData.copy());
      }
   }

   @Override
   public void handlePacket(ComputerCraftPacket packet, EntityPlayer sender) {
      switch (packet.m_packetType) {
         case 1:
            this.turnOn();
            break;
         case 2:
            this.reboot();
            break;
         case 3:
            this.shutdown();
            break;
         case 4:
            String event = packet.m_dataString[0];
            Object[] arguments = null;
            if (packet.m_dataNBT != null) {
               arguments = NBTUtil.decodeObjects(packet.m_dataNBT);
            }

            this.queueEvent(event, arguments);
            break;
         case 5:
            this.sendState(sender);
            break;
         case 6:
            String label = packet.m_dataString != null && packet.m_dataString.length >= 1 ? packet.m_dataString[0] : null;
            this.setLabel(label);
      }
   }
}
