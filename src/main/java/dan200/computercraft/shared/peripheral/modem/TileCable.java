package dan200.computercraft.shared.peripheral.modem;

import com.google.common.base.Objects;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.common.BlockGeneric;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.BlockCable;
import dan200.computercraft.shared.peripheral.common.PeripheralItemFactory;
import dan200.computercraft.shared.util.IDAssigner;
import dan200.computercraft.shared.util.PeripheralUtil;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileCable extends TileModemBase implements INetwork {
   private static final IIcon[] s_modemIcons = new IIcon[8];
   private static final IIcon[] s_cableIcons = new IIcon[2];
   private static int s_nextUniqueSearchID = 1;
   private Map<Integer, Set<IReceiver>> m_receivers = new HashMap<>();
   private Queue<TileCable.Packet> m_transmitQueue = new LinkedList<>();
   private boolean m_peripheralAccessAllowed = false;
   private int m_attachedPeripheralID = -1;
   private Map<String, IPeripheral> m_peripheralsByName = new HashMap<>();
   private Map<String, TileCable.RemotePeripheralWrapper> m_peripheralWrappersByName = new HashMap<>();
   private boolean m_peripheralsKnown = false;
   private boolean m_destroyed = false;
   private int m_lastSearchID = 0;

   @SideOnly(Side.CLIENT)
   public static void registerIcons(IIconRegister iconRegister) {
      s_modemIcons[0] = iconRegister.registerIcon("computercraft:wiredModemFace");
      s_modemIcons[1] = iconRegister.registerIcon("computercraft:wiredModemSide");
      s_modemIcons[2] = iconRegister.registerIcon("computercraft:wiredModemFaceOn");
      s_modemIcons[3] = iconRegister.registerIcon("computercraft:wiredModemSideOn");
      s_modemIcons[4] = iconRegister.registerIcon("computercraft:wiredModemFacePeripheral");
      s_modemIcons[5] = iconRegister.registerIcon("computercraft:wiredModemSidePeripheral");
      s_modemIcons[6] = iconRegister.registerIcon("computercraft:wiredModemFacePeripheralOn");
      s_modemIcons[7] = iconRegister.registerIcon("computercraft:wiredModemSidePeripheralOn");
      s_cableIcons[0] = iconRegister.registerIcon("computercraft:cableSide");
      s_cableIcons[1] = iconRegister.registerIcon("computercraft:cableCore");
   }

   public static IIcon getModemItemTexture(int side, boolean on) {
      return side != 2 && side != 5 ? s_modemIcons[on ? 2 : 0] : s_modemIcons[on ? 3 : 1];
   }

   public static IIcon getCableItemTexture(int side) {
      return side != 2 && side != 3 ? s_cableIcons[0] : s_cableIcons[1];
   }

   public TileCable() {
      super(s_modemIcons);
   }

   @Override
   public void destroy() {
      if (!this.m_destroyed) {
         this.m_destroyed = true;
         this.networkChanged();
      }

      super.destroy();
   }

   @Override
   public int getDirection() {
      int metadata = this.getMetadata();
      if (metadata < 6) {
         return metadata;
      } else {
         return metadata < 12 ? metadata - 6 : 2;
      }
   }

   @Override
   public void setDirection(int dir) {
      int metadata = this.getMetadata();
      if (metadata < 6) {
         this.setMetadata(dir);
      } else if (metadata < 12) {
         this.setMetadata(dir + 6);
      }
   }

   @Override
   public void getDroppedItems(List<ItemStack> drops, int fortune, boolean creative, boolean silkTouch) {
      if (!creative) {
         PeripheralType type = this.getPeripheralType();
         switch (type) {
            case Cable:
            case WiredModem:
               drops.add(PeripheralItemFactory.create(type, this.getLabel(), 1));
               break;
            case WiredModemWithCable:
               drops.add(PeripheralItemFactory.create(PeripheralType.WiredModem, this.getLabel(), 1));
               drops.add(PeripheralItemFactory.create(PeripheralType.Cable, null, 1));
         }
      }
   }

   @Override
   public ItemStack getPickedItem() {
      return this.getPeripheralType() == PeripheralType.WiredModemWithCable
         ? PeripheralItemFactory.create(PeripheralType.WiredModem, this.getLabel(), 1)
         : super.getPickedItem();
   }

   @Override
   public void onNeighbourChange() {
      int dir = this.getDirection();
      if (!this.getWorldObj()
         .isSideSolid(
            this.xCoord + Facing.offsetsXForSide[dir],
            this.yCoord + Facing.offsetsYForSide[dir],
            this.zCoord + Facing.offsetsZForSide[dir],
            ForgeDirection.getOrientation(Facing.oppositeSide[dir])
         )) {
         switch (this.getPeripheralType()) {
            case WiredModem:
               ((BlockGeneric)this.getBlockType())
                  .dropAllItems(this.getWorldObj(), this.xCoord, this.yCoord, this.zCoord, 0, false, false);
               this.getWorldObj().setBlockToAir(this.xCoord, this.yCoord, this.zCoord);
               break;
            case WiredModemWithCable:
               ((BlockGeneric)this.getBlockType())
                  .dropItem(
                     this.getWorldObj(),
                     this.xCoord,
                     this.yCoord,
                     this.zCoord,
                     PeripheralItemFactory.create(PeripheralType.WiredModem, this.getLabel(), 1)
                  );
               this.setLabel(null);
               this.getWorldObj().setBlockMetadataWithNotify(this.xCoord, this.yCoord, this.zCoord, 13, 3);
         }
      }
   }

   public AxisAlignedBB getModemBounds() {
      return super.getBounds();
   }

   public AxisAlignedBB getCableBounds() {
      double xMin = 0.375;
      double yMin = 0.375;
      double zMin = 0.375;
      double xMax = 0.625;
      double yMax = 0.625;
      double zMax = 0.625;
      if (BlockCable.isCable(this.getWorldObj(), this.xCoord - 1, this.yCoord, this.zCoord)) {
         xMin = 0.0;
      }

      if (BlockCable.isCable(this.getWorldObj(), this.xCoord + 1, this.yCoord, this.zCoord)) {
         xMax = 1.0;
      }

      if (BlockCable.isCable(this.getWorldObj(), this.xCoord, this.yCoord - 1, this.zCoord)) {
         yMin = 0.0;
      }

      if (BlockCable.isCable(this.getWorldObj(), this.xCoord, this.yCoord + 1, this.zCoord)) {
         yMax = 1.0;
      }

      if (BlockCable.isCable(this.getWorldObj(), this.xCoord, this.yCoord, this.zCoord - 1)) {
         zMin = 0.0;
      }

      if (BlockCable.isCable(this.getWorldObj(), this.xCoord, this.yCoord, this.zCoord + 1)) {
         zMax = 1.0;
      }

      return AxisAlignedBB.getBoundingBox(xMin, yMin, zMin, xMax, yMax, zMax);
   }

   @Override
   public AxisAlignedBB getBounds() {
      PeripheralType type = this.getPeripheralType();
      if (BlockCable.renderAsModem) {
         type = PeripheralType.WiredModem;
      }

      switch (type) {
         case Cable:
            return this.getCableBounds();
         case WiredModem:
         default:
            return this.getModemBounds();
         case WiredModemWithCable:
            AxisAlignedBB modem = this.getModemBounds();
            AxisAlignedBB cable = this.getCableBounds();
            return AxisAlignedBB.getBoundingBox(
               Math.min(modem.minX, cable.minX),
               Math.min(modem.minY, cable.minY),
               Math.min(modem.minZ, cable.minZ),
               Math.max(modem.maxX, cable.maxX),
               Math.max(modem.maxY, cable.maxY),
               Math.max(modem.maxZ, cable.maxZ)
            );
      }
   }

   @Override
   public void getCollisionBounds(List<AxisAlignedBB> bounds) {
      PeripheralType type = this.getPeripheralType();
      if (type == PeripheralType.WiredModem || type == PeripheralType.WiredModemWithCable) {
         bounds.add(this.getModemBounds());
      }

      if (type == PeripheralType.Cable || type == PeripheralType.WiredModemWithCable) {
         bounds.add(this.getCableBounds());
      }
   }

   @Override
   public boolean onActivate(EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
      if (this.getPeripheralType() == PeripheralType.WiredModemWithCable && !player.isSneaking()) {
         if (this.getWorldObj().isRemote) {
            return true;
         }

         String oldPeriphName = this.getConnectedPeripheralName();
         this.togglePeripheralAccess();
         String periphName = this.getConnectedPeripheralName();
         if (!Objects.equal(periphName, oldPeriphName)) {
            if (oldPeriphName != null) {
               player.addChatMessage(new ChatComponentTranslation("gui.computercraft:wired_modem.peripheral_disconnected", new Object[]{oldPeriphName}));
            }

            if (periphName != null) {
               player.addChatMessage(new ChatComponentTranslation("gui.computercraft:wired_modem.peripheral_connected", new Object[]{periphName}));
            }

            return true;
         }
      }

      return false;
   }

   @Override
   public void readFromNBT(NBTTagCompound nbttagcompound) {
      super.readFromNBT(nbttagcompound);
      this.m_peripheralAccessAllowed = nbttagcompound.getBoolean("peripheralAccess");
      this.m_attachedPeripheralID = nbttagcompound.getInteger("peripheralID");
   }

   @Override
   public void writeToNBT(NBTTagCompound nbttagcompound) {
      super.writeToNBT(nbttagcompound);
      nbttagcompound.setBoolean("peripheralAccess", this.m_peripheralAccessAllowed);
      nbttagcompound.setInteger("peripheralID", this.m_attachedPeripheralID);
   }

   @Override
   protected ModemPeripheral createPeripheral() {
      return new TileCable.Peripheral(this);
   }

   @Override
   protected void updateAnim() {
      int anim = 0;
      if (this.m_modem.isActive()) {
         anim++;
      }

      if (this.m_peripheralAccessAllowed) {
         anim += 2;
      }

      this.setAnim(anim);
   }

   @Override
   public IIcon getTexture(int side) {
      PeripheralType type = this.getPeripheralType();
      if (BlockCable.renderAsModem) {
         type = PeripheralType.WiredModem;
      }

      switch (type) {
         case Cable:
         case WiredModemWithCable:
            int dir = -1;
            if (type == PeripheralType.WiredModemWithCable) {
               dir = this.getDirection();
               dir -= dir % 2;
            }

            if (BlockCable.isCable(this.getWorldObj(), this.xCoord - 1, this.yCoord, this.zCoord)
               || BlockCable.isCable(this.getWorldObj(), this.xCoord + 1, this.yCoord, this.zCoord)) {
               if (dir != -1 && dir != 4) {
                  dir = -2;
               } else {
                  dir = 4;
               }
            }

            if (BlockCable.isCable(this.getWorldObj(), this.xCoord, this.yCoord - 1, this.zCoord)
               || BlockCable.isCable(this.getWorldObj(), this.xCoord, this.yCoord + 1, this.zCoord)) {
               if (dir != -1 && dir != 0) {
                  dir = -2;
               } else {
                  dir = 0;
               }
            }

            if (BlockCable.isCable(this.getWorldObj(), this.xCoord, this.yCoord, this.zCoord - 1)
               || BlockCable.isCable(this.getWorldObj(), this.xCoord, this.yCoord, this.zCoord + 1)) {
               if (dir != -1 && dir != 2) {
                  dir = -2;
               } else {
                  dir = 2;
               }
            }

            if (dir == -1) {
               dir = 2;
            }

            if (dir < 0 || side != dir && side != Facing.oppositeSide[dir]) {
               return s_cableIcons[0];
            }

            return s_cableIcons[1];
         case WiredModem:
         default:
            return super.getTexture(side);
      }
   }

   @Override
   public IPeripheral getPeripheral(int side) {
      return this.getPeripheralType() != PeripheralType.Cable ? super.getPeripheral(side) : null;
   }

   @Override
   public void func_145845_h() {
      super.func_145845_h();
      if (!this.getWorldObj().isRemote) {
         synchronized (this.m_peripheralsByName) {
            if (!this.m_peripheralsKnown) {
               this.findPeripherals();
               this.m_peripheralsKnown = true;
            }
         }

         synchronized (this.m_transmitQueue) {
            while (this.m_transmitQueue.peek() != null) {
               TileCable.Packet p = this.m_transmitQueue.remove();
               if (p != null) {
                  this.dispatchPacket(p);
               }
            }
         }
      }
   }

   @Override
   public void addReceiver(IReceiver receiver) {
      synchronized (this.m_receivers) {
         int channel = receiver.getChannel();
         Set<IReceiver> receivers = this.m_receivers.get(channel);
         if (receivers == null) {
            receivers = new HashSet<>();
            this.m_receivers.put(channel, receivers);
         }

         receivers.add(receiver);
      }
   }

   @Override
   public void removeReceiver(IReceiver receiver) {
      synchronized (this.m_receivers) {
         int channel = receiver.getChannel();
         Set<IReceiver> receivers = this.m_receivers.get(channel);
         if (receivers != null) {
            receivers.remove(receiver);
         }
      }
   }

   @Override
   public void transmit(int channel, int replyChannel, Object payload, double range, double xPos, double yPos, double zPos, Object senderObject) {
      TileCable.Packet p = new TileCable.Packet();
      p.channel = channel;
      p.replyChannel = replyChannel;
      p.payload = payload;
      p.senderObject = senderObject;
      synchronized (this.m_transmitQueue) {
         this.m_transmitQueue.offer(p);
      }
   }

   @Override
   public boolean isWireless() {
      return false;
   }

   private void attachPeripheral(String periphName, IPeripheral peripheral) {
      if (!this.m_peripheralWrappersByName.containsKey(periphName)) {
         TileCable.RemotePeripheralWrapper wrapper = new TileCable.RemotePeripheralWrapper(peripheral, this.m_modem.getComputer(), periphName);
         this.m_peripheralWrappersByName.put(periphName, wrapper);
         wrapper.attach();
      }
   }

   private void detachPeripheral(String periphName) {
      if (this.m_peripheralWrappersByName.containsKey(periphName)) {
         TileCable.RemotePeripheralWrapper wrapper = this.m_peripheralWrappersByName.get(periphName);
         this.m_peripheralWrappersByName.remove(periphName);
         wrapper.detach();
      }
   }

   private String getTypeRemote(String remoteName) {
      synchronized (this.m_peripheralsByName) {
         TileCable.RemotePeripheralWrapper wrapper = this.m_peripheralWrappersByName.get(remoteName);
         return wrapper != null ? wrapper.getType() : null;
      }
   }

   private String[] getMethodNamesRemote(String remoteName) {
      synchronized (this.m_peripheralsByName) {
         TileCable.RemotePeripheralWrapper wrapper = this.m_peripheralWrappersByName.get(remoteName);
         return wrapper != null ? wrapper.getMethodNames() : null;
      }
   }

   private Object[] callMethodRemote(String remoteName, ILuaContext context, String method, Object[] arguments) throws LuaException, InterruptedException {
      TileCable.RemotePeripheralWrapper wrapper;
      synchronized (this.m_peripheralsByName) {
         wrapper = this.m_peripheralWrappersByName.get(remoteName);
      }

      if (wrapper != null) {
         return wrapper.callMethod(context, method, arguments);
      } else {
         throw new LuaException("No peripheral: " + remoteName);
      }
   }

   public void networkChanged() {
      if (!this.getWorldObj().isRemote) {
         if (!this.m_destroyed) {
            this.searchNetwork(new TileCable.ICableVisitor() {
               @Override
               public void visit(TileCable modem, int distance) {
                  synchronized (modem.m_peripheralsByName) {
                     modem.m_peripheralsKnown = false;
                  }
               }
            });
         } else {
            for (int dir = 0; dir < 6; dir++) {
               int x = this.xCoord + Facing.offsetsXForSide[dir];
               int y = this.yCoord + Facing.offsetsYForSide[dir];
               int z = this.zCoord + Facing.offsetsZForSide[dir];
               if (y >= 0 && y < this.getWorldObj().getHeight() && BlockCable.isCable(this.getWorldObj(), x, y, z)) {
                  TileEntity tile = this.getWorldObj().getTileEntity(x, y, z);
                  if (tile != null && tile instanceof TileCable) {
                     TileCable modem = (TileCable)tile;
                     modem.networkChanged();
                  }
               }
            }
         }
      }
   }

   private void dispatchPacket(final TileCable.Packet packet) {
      this.searchNetwork(new TileCable.ICableVisitor() {
         @Override
         public void visit(TileCable modem, int distance) {
            modem.receivePacket(packet, distance);
         }
      });
   }

   private void receivePacket(TileCable.Packet packet, int distanceTravelled) {
      synchronized (this.m_receivers) {
         Set<IReceiver> receivers = this.m_receivers.get(packet.channel);
         if (receivers != null) {
            for (IReceiver receiver : receivers) {
               receiver.receive(packet.replyChannel, packet.payload, distanceTravelled, packet.senderObject);
            }
         }
      }
   }

   private void findPeripherals() {
      final TileCable origin = this;
      synchronized (this.m_peripheralsByName) {
         final Map<String, IPeripheral> newPeripheralsByName = new HashMap<>();
         if (this.getPeripheralType() == PeripheralType.WiredModemWithCable) {
            this.searchNetwork(new TileCable.ICableVisitor() {
               @Override
               public void visit(TileCable modem, int distance) {
                  if (modem != origin) {
                     IPeripheral peripheral = modem.getConnectedPeripheral();
                     String periphName = modem.getConnectedPeripheralName();
                     if (peripheral != null && periphName != null) {
                        newPeripheralsByName.put(periphName, peripheral);
                     }
                  }
               }
            });
         }

         Iterator<String> it = this.m_peripheralsByName.keySet().iterator();

         while (it.hasNext()) {
            String periphName = it.next();
            if (!newPeripheralsByName.containsKey(periphName)) {
               this.detachPeripheral(periphName);
               it.remove();
            }
         }

         for (String periphName : newPeripheralsByName.keySet()) {
            if (!this.m_peripheralsByName.containsKey(periphName)) {
               IPeripheral peripheral = newPeripheralsByName.get(periphName);
               if (peripheral != null) {
                  this.m_peripheralsByName.put(periphName, peripheral);
                  if (this.isAttached()) {
                     this.attachPeripheral(periphName, peripheral);
                  }
               }
            }
         }
      }
   }

   public void togglePeripheralAccess() {
      if (!this.m_peripheralAccessAllowed) {
         this.m_peripheralAccessAllowed = true;
         if (this.getConnectedPeripheral() == null) {
            this.m_peripheralAccessAllowed = false;
            return;
         }
      } else {
         this.m_peripheralAccessAllowed = false;
      }

      this.updateAnim();
      this.networkChanged();
   }

   public String getConnectedPeripheralName() {
      IPeripheral periph = this.getConnectedPeripheral();
      if (periph != null) {
         String type = periph.getType();
         if (this.m_attachedPeripheralID < 0) {
            this.m_attachedPeripheralID = IDAssigner.getNextIDFromFile(
               new File(ComputerCraft.getWorldDir(this.getWorldObj()), "computer/lastid_" + type + ".txt")
            );
         }

         return type + "_" + this.m_attachedPeripheralID;
      } else {
         return null;
      }
   }

   private IPeripheral getConnectedPeripheral() {
      if (this.m_peripheralAccessAllowed && this.getPeripheralType() == PeripheralType.WiredModemWithCable) {
         int dir = this.getDirection();
         int x = this.xCoord + Facing.offsetsXForSide[dir];
         int y = this.yCoord + Facing.offsetsYForSide[dir];
         int z = this.zCoord + Facing.offsetsZForSide[dir];
         return PeripheralUtil.getPeripheral(this.getWorldObj(), x, y, z, Facing.oppositeSide[dir]);
      } else {
         return null;
      }
   }

   private static void enqueue(Queue<TileCable.SearchLoc> queue, World world, int x, int y, int z, int distanceTravelled) {
      if (y >= 0 && y < world.getHeight() && BlockCable.isCable(world, x, y, z)) {
         TileCable.SearchLoc loc = new TileCable.SearchLoc();
         loc.world = world;
         loc.x = x;
         loc.y = y;
         loc.z = z;
         loc.distanceTravelled = distanceTravelled;
         queue.offer(loc);
      }
   }

   private static void visitBlock(Queue<TileCable.SearchLoc> queue, TileCable.SearchLoc location, int searchID, TileCable.ICableVisitor visitor) {
      if (location.distanceTravelled < 256) {
         TileEntity tile = location.world.getTileEntity(location.x, location.y, location.z);
         if (tile != null && tile instanceof TileCable) {
            TileCable modem = (TileCable)tile;
            if (!modem.m_destroyed && modem.m_lastSearchID != searchID) {
               modem.m_lastSearchID = searchID;
               visitor.visit(modem, location.distanceTravelled + 1);
               enqueue(queue, location.world, location.x, location.y + 1, location.z, location.distanceTravelled + 1);
               enqueue(queue, location.world, location.x, location.y - 1, location.z, location.distanceTravelled + 1);
               enqueue(queue, location.world, location.x, location.y, location.z + 1, location.distanceTravelled + 1);
               enqueue(queue, location.world, location.x, location.y, location.z - 1, location.distanceTravelled + 1);
               enqueue(queue, location.world, location.x + 1, location.y, location.z, location.distanceTravelled + 1);
               enqueue(queue, location.world, location.x - 1, location.y, location.z, location.distanceTravelled + 1);
            }
         }
      }
   }

   private void searchNetwork(TileCable.ICableVisitor visitor) {
      int searchID = ++s_nextUniqueSearchID;
      Queue<TileCable.SearchLoc> queue = new LinkedList<>();
      enqueue(queue, this.getWorldObj(), this.xCoord, this.yCoord, this.zCoord, 1);

      for (int visited = 0; queue.peek() != null; visited++) {
         TileCable.SearchLoc loc = queue.remove();
         visitBlock(queue, loc, searchID, visitor);
      }
   }

   private interface ICableVisitor {
      void visit(TileCable var1, int var2);
   }

   private class Packet {
      public int channel;
      public int replyChannel;
      public Object payload;
      public Object senderObject;

      private Packet() {
      }
   }

   private static class Peripheral extends ModemPeripheral {
      private TileCable m_entity;

      public Peripheral(TileCable entity) {
         this.m_entity = entity;
      }

      @Override
      protected double getTransmitRange() {
         return 256.0;
      }

      @Override
      protected INetwork getNetwork() {
         return this.m_entity;
      }

      @Override
      protected Vec3 getPosition() {
         int direction = this.m_entity.getDirection();
         int x = this.m_entity.xCoord + Facing.offsetsXForSide[direction];
         int y = this.m_entity.yCoord + Facing.offsetsYForSide[direction];
         int z = this.m_entity.zCoord + Facing.offsetsZForSide[direction];
         return Vec3.createVectorHelper(x + 0.5, y + 0.5, z + 0.5);
      }

      @Override
      public String[] getMethodNames() {
         String[] methods = super.getMethodNames();
         String[] newMethods = new String[methods.length + 5];
         System.arraycopy(methods, 0, newMethods, 0, methods.length);
         newMethods[methods.length] = "getNamesRemote";
         newMethods[methods.length + 1] = "isPresentRemote";
         newMethods[methods.length + 2] = "getTypeRemote";
         newMethods[methods.length + 3] = "getMethodsRemote";
         newMethods[methods.length + 4] = "callRemote";
         return newMethods;
      }

      private String parseString(Object[] arguments, int index) throws LuaException {
         if (arguments.length >= index + 1 && arguments[index] instanceof String) {
            return (String)arguments[index];
         } else {
            throw new LuaException("Expected string");
         }
      }

      @Override
      public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments) throws LuaException, InterruptedException {
         String[] methods = super.getMethodNames();
         switch (method - methods.length) {
            case 0:
               synchronized (this.m_entity.m_peripheralsByName) {
                  int idx = 1;
                  Map<Object, Object> table = new HashMap<>();

                  for (String name : this.m_entity.m_peripheralWrappersByName.keySet()) {
                     table.put(idx++, name);
                  }

                  return new Object[]{table};
               }
            case 1:
               String type = this.m_entity.getTypeRemote(this.parseString(arguments, 0));
               return new Object[]{type != null};
            case 2:
               String type2 = this.m_entity.getTypeRemote(this.parseString(arguments, 0));
               if (type2 != null) {
                  return new Object[]{type2};
               }

               return null;
            case 3:
               String[] methodNames = this.m_entity.getMethodNamesRemote(this.parseString(arguments, 0));
               if (methodNames == null) {
                  return null;
               }

               Map<Object, Object> table = new HashMap<>();

               for (int i = 0; i < methodNames.length; i++) {
                  table.put(i + 1, methodNames[i]);
               }

               return new Object[]{table};
            case 4:
               String remoteName = this.parseString(arguments, 0);
               String methodName = this.parseString(arguments, 1);
               Object[] methodArgs = new Object[arguments.length - 2];
               System.arraycopy(arguments, 2, methodArgs, 0, arguments.length - 2);
               return this.m_entity.callMethodRemote(remoteName, context, methodName, methodArgs);
            default:
               return super.callMethod(computer, context, method, arguments);
         }
      }

      @Override
      public void attach(IComputerAccess computer) {
         super.attach(computer);
         synchronized (this.m_entity.m_peripheralsByName) {
            for (String periphName : this.m_entity.m_peripheralsByName.keySet()) {
               IPeripheral peripheral = this.m_entity.m_peripheralsByName.get(periphName);
               if (peripheral != null) {
                  this.m_entity.attachPeripheral(periphName, peripheral);
               }
            }
         }
      }

      @Override
      public synchronized void detach(IComputerAccess computer) {
         synchronized (this.m_entity.m_peripheralsByName) {
            for (String periphName : this.m_entity.m_peripheralsByName.keySet()) {
               this.m_entity.detachPeripheral(periphName);
            }
         }

         super.detach(computer);
      }

      @Override
      public boolean equals(IPeripheral other) {
         if (other instanceof TileCable.Peripheral) {
            TileCable.Peripheral otherModem = (TileCable.Peripheral)other;
            return otherModem.m_entity == this.m_entity;
         } else {
            return false;
         }
      }
   }

   private static class RemotePeripheralWrapper implements IComputerAccess {
      private IPeripheral m_peripheral;
      private IComputerAccess m_computer;
      private String m_name;
      private String m_type;
      private String[] m_methods;
      private Map<String, Integer> m_methodMap;

      public RemotePeripheralWrapper(IPeripheral peripheral, IComputerAccess computer, String name) {
         this.m_peripheral = peripheral;
         this.m_computer = computer;
         this.m_name = name;
         this.m_type = peripheral.getType();
         this.m_methods = peripheral.getMethodNames();

         assert this.m_type != null;

         assert this.m_methods != null;

         this.m_methodMap = new HashMap<>();

         for (int i = 0; i < this.m_methods.length; i++) {
            if (this.m_methods[i] != null) {
               this.m_methodMap.put(this.m_methods[i], i);
            }
         }
      }

      public void attach() {
         this.m_peripheral.attach(this);
         this.m_computer.queueEvent("peripheral", new Object[]{this.getAttachmentName()});
      }

      public void detach() {
         this.m_peripheral.detach(this);
         this.m_computer.queueEvent("peripheral_detach", new Object[]{this.getAttachmentName()});
      }

      public String getType() {
         return this.m_type;
      }

      public String[] getMethodNames() {
         return this.m_methods;
      }

      public Object[] callMethod(ILuaContext context, String methodName, Object[] arguments) throws LuaException, InterruptedException {
         if (this.m_methodMap.containsKey(methodName)) {
            int method = this.m_methodMap.get(methodName);
            return this.m_peripheral.callMethod(this, context, method, arguments);
         } else {
            throw new LuaException("No such method " + methodName);
         }
      }

      @Override
      public String mount(String desiredLocation, IMount mount) {
         return this.m_computer.mount(desiredLocation, mount, this.m_name);
      }

      @Override
      public String mount(String desiredLocation, IMount mount, String driveName) {
         return this.m_computer.mount(desiredLocation, mount, driveName);
      }

      @Override
      public String mountWritable(String desiredLocation, IWritableMount mount) {
         return this.m_computer.mountWritable(desiredLocation, mount, this.m_name);
      }

      @Override
      public String mountWritable(String desiredLocation, IWritableMount mount, String driveName) {
         return this.m_computer.mountWritable(desiredLocation, mount, driveName);
      }

      @Override
      public void unmount(String location) {
         this.m_computer.unmount(location);
      }

      @Override
      public int getID() {
         return this.m_computer.getID();
      }

      @Override
      public void queueEvent(String event, Object[] arguments) {
         this.m_computer.queueEvent(event, arguments);
      }

      @Override
      public String getAttachmentName() {
         return this.m_name;
      }
   }

   private static class SearchLoc {
      public World world;
      public int x;
      public int y;
      public int z;
      public int distanceTravelled;

      private SearchLoc() {
      }
   }
}
