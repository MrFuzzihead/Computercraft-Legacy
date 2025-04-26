package dan200.computercraft.shared.proxy;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.core.computer.MainThread;
import dan200.computercraft.shared.common.DefaultBundledRedstoneProvider;
import dan200.computercraft.shared.common.TileGeneric;
import dan200.computercraft.shared.computer.blocks.BlockCommandComputer;
import dan200.computercraft.shared.computer.blocks.BlockComputer;
import dan200.computercraft.shared.computer.blocks.TileCommandComputer;
import dan200.computercraft.shared.computer.blocks.TileComputer;
import dan200.computercraft.shared.computer.core.ClientComputer;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.computer.inventory.ContainerComputer;
import dan200.computercraft.shared.computer.items.ComputerItemFactory;
import dan200.computercraft.shared.computer.items.ItemCommandComputer;
import dan200.computercraft.shared.computer.items.ItemComputer;
import dan200.computercraft.shared.media.common.DefaultMediaProvider;
import dan200.computercraft.shared.media.inventory.ContainerHeldItem;
import dan200.computercraft.shared.media.items.ItemDiskExpanded;
import dan200.computercraft.shared.media.items.ItemDiskLegacy;
import dan200.computercraft.shared.media.items.ItemPrintout;
import dan200.computercraft.shared.media.items.ItemTreasureDisk;
import dan200.computercraft.shared.media.recipes.DiskRecipe;
import dan200.computercraft.shared.media.recipes.PrintoutRecipe;
import dan200.computercraft.shared.network.ComputerCraftPacket;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.commandblock.CommandBlockPeripheralProvider;
import dan200.computercraft.shared.peripheral.common.BlockCable;
import dan200.computercraft.shared.peripheral.common.BlockPeripheral;
import dan200.computercraft.shared.peripheral.common.DefaultPeripheralProvider;
import dan200.computercraft.shared.peripheral.common.ItemCable;
import dan200.computercraft.shared.peripheral.common.ItemPeripheral;
import dan200.computercraft.shared.peripheral.common.PeripheralItemFactory;
import dan200.computercraft.shared.peripheral.diskdrive.ContainerDiskDrive;
import dan200.computercraft.shared.peripheral.diskdrive.TileDiskDrive;
import dan200.computercraft.shared.peripheral.modem.TileCable;
import dan200.computercraft.shared.peripheral.modem.TileWirelessModem;
import dan200.computercraft.shared.peripheral.monitor.TileMonitor;
import dan200.computercraft.shared.peripheral.printer.ContainerPrinter;
import dan200.computercraft.shared.peripheral.printer.TilePrinter;
import dan200.computercraft.shared.pocket.items.ItemPocketComputer;
import dan200.computercraft.shared.pocket.items.PocketComputerItemFactory;
import dan200.computercraft.shared.pocket.recipes.PocketComputerUpgradeRecipe;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;
import dan200.computercraft.shared.turtle.inventory.ContainerTurtle;
import dan200.computercraft.shared.util.Colour;
import dan200.computercraft.shared.util.CreativeTabMain;
import dan200.computercraft.shared.util.ImpostorRecipe;
import dan200.computercraft.shared.util.ImpostorShapelessRecipe;
import java.io.File;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemRecord;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent.Load;
import net.minecraftforge.event.world.WorldEvent.Unload;
import net.minecraftforge.oredict.RecipeSorter;
import net.minecraftforge.oredict.RecipeSorter.Category;

public abstract class ComputerCraftProxyCommon implements IComputerCraftProxy {
   @Override
   public void preInit() {
      this.registerItems();
   }

   @Override
   public void init() {
      this.registerTileEntities();
      this.registerForgeHandlers();
   }

   @Override
   public abstract boolean isClient();

   @Override
   public abstract boolean getGlobalCursorBlink();

   @Override
   public abstract Object getFixedWidthFontRenderer();

   @Override
   public String getRecordInfo(ItemStack recordStack) {
      Item item = recordStack.getItem();
      if (item instanceof ItemRecord) {
         ItemRecord record = (ItemRecord)item;
         return Item.itemRegistry.getNameForObject(record).startsWith("minecraft:") ? "C418 - " + record.recordName : record.recordName;
      } else {
         return null;
      }
   }

   @Override
   public abstract void playRecord(String var1, String var2, World var3, int var4, int var5, int var6);

   @Override
   public abstract Object getDiskDriveGUI(InventoryPlayer var1, TileDiskDrive var2);

   @Override
   public abstract Object getComputerGUI(TileComputer var1);

   @Override
   public abstract Object getPrinterGUI(InventoryPlayer var1, TilePrinter var2);

   @Override
   public abstract Object getTurtleGUI(InventoryPlayer var1, TileTurtle var2);

   @Override
   public abstract Object getPrintoutGUI(InventoryPlayer var1);

   @Override
   public abstract Object getPocketComputerGUI(InventoryPlayer var1);

   @Override
   public abstract File getWorldDir(World var1);

   @Override
   public void handlePacket(ComputerCraftPacket packet, EntityPlayer player) {
      switch (packet.m_packetType) {
         case 1:
         case 2:
         case 3:
         case 4:
         case 5:
         case 6:
            int instance = packet.m_dataInt[0];
            ServerComputer computer = ComputerCraft.serverComputerRegistry.get(instance);
            if (computer != null) {
               computer.handlePacket(packet, player);
            }
            break;
         case 7:
            int instanceIDx = packet.m_dataInt[0];
            if (!ComputerCraft.clientComputerRegistry.contains(instanceIDx)) {
               ComputerCraft.clientComputerRegistry.add(instanceIDx, new ClientComputer(instanceIDx));
            }

            ComputerCraft.clientComputerRegistry.get(instanceIDx).handlePacket(packet, player);
            break;
         case 8:
            int instanceID = packet.m_dataInt[0];
            if (ComputerCraft.clientComputerRegistry.contains(instanceID)) {
               ComputerCraft.clientComputerRegistry.remove(instanceID);
            }
            break;
         case 9:
            int x = packet.m_dataInt[0];
            int y = packet.m_dataInt[1];
            int z = packet.m_dataInt[2];
            World world = player.getEntityWorld();
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity != null && tileEntity instanceof TileGeneric) {
               TileGeneric generic = (TileGeneric)tileEntity;
               Packet description = generic.getUpdatePacket();
               if (description != null) {
                  ((EntityPlayerMP)player).field_71135_a.func_147359_a(description);
               }
            }
      }
   }

   private void registerItems() {
      ComputerCraft.mainCreativeTab = new CreativeTabMain(CreativeTabs.getNextID());
      ComputerCraft.Blocks.computer = BlockComputer.createComputerBlock();
      GameRegistry.registerBlock(ComputerCraft.Blocks.computer, ItemComputer.class, "CC-Computer");
      ComputerCraft.Blocks.peripheral = new BlockPeripheral();
      GameRegistry.registerBlock(ComputerCraft.Blocks.peripheral, ItemPeripheral.class, "CC-Peripheral");
      ComputerCraft.Blocks.cable = new BlockCable();
      GameRegistry.registerBlock(ComputerCraft.Blocks.cable, ItemCable.class, "CC-Cable");
      ComputerCraft.Blocks.commandComputer = BlockCommandComputer.createComputerBlock();
      GameRegistry.registerBlock(ComputerCraft.Blocks.commandComputer, ItemCommandComputer.class, "command_computer");
      ComputerCraft.Items.disk = new ItemDiskLegacy();
      GameRegistry.registerItem(ComputerCraft.Items.disk, "disk");
      ComputerCraft.Items.diskExpanded = new ItemDiskExpanded();
      GameRegistry.registerItem(ComputerCraft.Items.diskExpanded, "diskExpanded");
      ComputerCraft.Items.treasureDisk = new ItemTreasureDisk();
      GameRegistry.registerItem(ComputerCraft.Items.treasureDisk, "treasureDisk");
      ComputerCraft.Items.printout = new ItemPrintout();
      GameRegistry.registerItem(ComputerCraft.Items.printout, "printout");
      ComputerCraft.Items.pocketComputer = new ItemPocketComputer();
      GameRegistry.registerItem(ComputerCraft.Items.pocketComputer, "pocketComputer");
      RecipeSorter.register("computercraft:impostor", ImpostorRecipe.class, Category.SHAPED, "after:minecraft:shapeless");
      RecipeSorter.register("computercraft:impostor_shapeless", ImpostorShapelessRecipe.class, Category.SHAPELESS, "after:minecraft:shapeless");
      RecipeSorter.register("computercraft:disk", DiskRecipe.class, Category.SHAPELESS, "after:minecraft:shapeless");
      RecipeSorter.register("computercraft:printout", PrintoutRecipe.class, Category.SHAPELESS, "after:minecraft:shapeless");
      RecipeSorter.register("computercraft:pocket_computer_upgrade", PocketComputerUpgradeRecipe.class, Category.SHAPELESS, "after:minecraft:shapeless");
      ItemStack computer = ComputerItemFactory.create(-1, null, ComputerFamily.Normal);
      GameRegistry.addRecipe(computer, new Object[]{"XXX", "XYX", "XZX", 'X', Blocks.field_150348_b, 'Y', Items.redstone, 'Z', Blocks.field_150410_aZ});
      ItemStack advancedComputer = ComputerItemFactory.create(-1, null, ComputerFamily.Advanced);
      GameRegistry.addRecipe(
         advancedComputer, new Object[]{"XXX", "XYX", "XZX", 'X', Items.field_151043_k, 'Y', Items.redstone, 'Z', Blocks.field_150410_aZ}
      );
      ItemStack diskDrive = PeripheralItemFactory.create(PeripheralType.DiskDrive, null, 1);
      GameRegistry.addRecipe(diskDrive, new Object[]{"XXX", "XYX", "XYX", 'X', Blocks.field_150348_b, 'Y', Items.redstone});
      ItemStack wirelessModem = PeripheralItemFactory.create(PeripheralType.WirelessModem, null, 1);
      GameRegistry.addRecipe(wirelessModem, new Object[]{"XXX", "XYX", "XXX", 'X', Blocks.field_150348_b, 'Y', Items.field_151079_bi});
      ItemStack monitor = PeripheralItemFactory.create(PeripheralType.Monitor, null, 1);
      GameRegistry.addRecipe(monitor, new Object[]{"XXX", "XYX", "XXX", 'X', Blocks.field_150348_b, 'Y', Blocks.field_150410_aZ});
      ItemStack printer = PeripheralItemFactory.create(PeripheralType.Printer, null, 1);
      GameRegistry.addRecipe(
         printer, new Object[]{"XXX", "XYX", "XZX", 'X', Blocks.field_150348_b, 'Y', Items.redstone, 'Z', new ItemStack(Items.dye, 1, 0)}
      );
      ItemStack advancedMonitors = PeripheralItemFactory.create(PeripheralType.AdvancedMonitor, null, 4);
      GameRegistry.addRecipe(advancedMonitors, new Object[]{"XXX", "XYX", "XXX", 'X', Items.field_151043_k, 'Y', Blocks.field_150410_aZ});
      ItemStack cable = PeripheralItemFactory.create(PeripheralType.Cable, null, 6);
      GameRegistry.addRecipe(cable, new Object[]{" X ", "XYX", " X ", 'X', Blocks.field_150348_b, 'Y', Items.redstone});
      ItemStack wiredModem = PeripheralItemFactory.create(PeripheralType.WiredModem, null, 1);
      GameRegistry.addRecipe(wiredModem, new Object[]{"XXX", "XYX", "XXX", 'X', Blocks.field_150348_b, 'Y', Items.redstone});
      ItemStack commandComputer = ComputerItemFactory.create(-1, null, ComputerFamily.Command);
      GameRegistry.addRecipe(
         commandComputer, new Object[]{"XXX", "XYX", "XZX", 'X', Blocks.field_150348_b, 'Y', Blocks.field_150483_bI, 'Z', Blocks.field_150410_aZ}
      );
      GameRegistry.addRecipe(new DiskRecipe());
      ItemStack paper = new ItemStack(Items.paper, 1);
      ItemStack redstone = new ItemStack(Items.redstone, 1);
      ItemStack basicDisk = ItemDiskLegacy.createFromIDAndColour(-1, null, Colour.Blue.getHex());
      GameRegistry.addRecipe(new ImpostorShapelessRecipe(basicDisk, new Object[]{redstone, paper}));

      for (int colour = 0; colour < 16; colour++) {
         ItemStack disk = ItemDiskLegacy.createFromIDAndColour(-1, null, Colour.values()[colour].getHex());
         ItemStack dye = new ItemStack(Items.dye, 1, colour);

         for (int otherColour = 0; otherColour < 16; otherColour++) {
            if (colour != otherColour) {
               ItemStack otherDisk = ItemDiskLegacy.createFromIDAndColour(-1, null, Colour.values()[colour].getHex());
               GameRegistry.addRecipe(new ImpostorShapelessRecipe(disk, new Object[]{otherDisk, dye}));
            }
         }

         GameRegistry.addRecipe(new ImpostorShapelessRecipe(disk, new Object[]{redstone, paper, dye}));
      }

      GameRegistry.addRecipe(new PrintoutRecipe());
      ItemStack singlePrintout = ItemPrintout.createSingleFromTitleAndText(null, null, null);
      ItemStack multiplePrintout = ItemPrintout.createMultipleFromTitleAndText(null, null, null);
      ItemStack bookPrintout = ItemPrintout.createBookFromTitleAndText(null, null, null);
      ItemStack string = new ItemStack(Items.string, 1, 0);
      GameRegistry.addRecipe(new ImpostorShapelessRecipe(multiplePrintout, new Object[]{singlePrintout, singlePrintout, string}));
      ItemStack leather = new ItemStack(Items.leather, 1, 0);
      GameRegistry.addRecipe(new ImpostorShapelessRecipe(bookPrintout, new Object[]{leather, singlePrintout, string}));
      ItemStack pocketComputer = PocketComputerItemFactory.create(-1, null, ComputerFamily.Normal, false);
      GameRegistry.addRecipe(
         pocketComputer, new Object[]{"XXX", "XYX", "XZX", 'X', Blocks.field_150348_b, 'Y', Items.field_151153_ao, 'Z', Blocks.field_150410_aZ}
      );
      ItemStack advancedPocketComputer = PocketComputerItemFactory.create(-1, null, ComputerFamily.Advanced, false);
      GameRegistry.addRecipe(
         advancedPocketComputer, new Object[]{"XXX", "XYX", "XZX", 'X', Items.field_151043_k, 'Y', Items.field_151153_ao, 'Z', Blocks.field_150410_aZ}
      );
      ItemStack wirelessPocketComputer = PocketComputerItemFactory.create(-1, null, ComputerFamily.Normal, true);
      GameRegistry.addRecipe(new PocketComputerUpgradeRecipe());
      ItemStack advancedWirelessPocketComputer = PocketComputerItemFactory.create(-1, null, ComputerFamily.Advanced, true);
      GameRegistry.addRecipe(new ImpostorRecipe(1, 2, new ItemStack[]{wirelessModem, pocketComputer}, wirelessPocketComputer));
      GameRegistry.addRecipe(new ImpostorRecipe(1, 2, new ItemStack[]{wirelessModem, advancedPocketComputer}, advancedWirelessPocketComputer));
      NBTTagCompound tag = new NBTTagCompound();
      tag.setString("SkullOwner", "dan200");
      ItemStack danHead = new ItemStack(Items.field_151144_bL, 1, 3);
      danHead.setTagCompound(tag);
      GameRegistry.addShapelessRecipe(danHead, new Object[]{computer, new ItemStack(Items.field_151144_bL, 1, 1)});
      tag = new NBTTagCompound();
      tag.setString("SkullOwner", "Cloudhunter");
      ItemStack cloudyHead = new ItemStack(Items.field_151144_bL, 1, 3);
      cloudyHead.setTagCompound(tag);
      GameRegistry.addShapelessRecipe(cloudyHead, new Object[]{monitor, new ItemStack(Items.field_151144_bL, 1, 1)});
   }

   private void registerTileEntities() {
      GameRegistry.registerTileEntity(TileComputer.class, "computer");
      GameRegistry.registerTileEntity(TileDiskDrive.class, "diskdrive");
      GameRegistry.registerTileEntity(TileWirelessModem.class, "wirelessmodem");
      GameRegistry.registerTileEntity(TileMonitor.class, "monitor");
      GameRegistry.registerTileEntity(TilePrinter.class, "ccprinter");
      GameRegistry.registerTileEntity(TileCable.class, "wiredmodem");
      GameRegistry.registerTileEntity(TileCommandComputer.class, "command_computer");
      ComputerCraftAPI.registerPeripheralProvider(new DefaultPeripheralProvider());
      if (ComputerCraft.enableCommandBlock) {
         ComputerCraftAPI.registerPeripheralProvider(new CommandBlockPeripheralProvider());
      }

      ComputerCraftAPI.registerBundledRedstoneProvider(new DefaultBundledRedstoneProvider());
      ComputerCraftAPI.registerMediaProvider(new DefaultMediaProvider());
   }

   private void registerForgeHandlers() {
      ComputerCraftProxyCommon.ForgeHandlers handlers = new ComputerCraftProxyCommon.ForgeHandlers();
      FMLCommonHandler.instance().bus().register(handlers);
      MinecraftForge.EVENT_BUS.register(handlers);
      NetworkRegistry.INSTANCE.registerGuiHandler(ComputerCraft.instance, handlers);
   }

   public class ForgeHandlers implements IGuiHandler {
      private ForgeHandlers() {
      }

      public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
         TileEntity tile = world.getTileEntity(x, y, z);
         switch (id) {
            case 100:
               if (tile != null && tile instanceof TileDiskDrive) {
                  TileDiskDrive drive = (TileDiskDrive)tile;
                  return new ContainerDiskDrive(player.inventory, drive);
               }
               break;
            case 101:
               if (tile != null && tile instanceof TileComputer) {
                  TileComputer computer = (TileComputer)tile;
                  return new ContainerComputer(computer);
               }
               break;
            case 102:
               if (tile != null && tile instanceof TilePrinter) {
                  TilePrinter printer = (TilePrinter)tile;
                  return new ContainerPrinter(player.inventory, printer);
               }
               break;
            case 103:
               if (tile != null && tile instanceof TileTurtle) {
                  TileTurtle turtle = (TileTurtle)tile;
                  return new ContainerTurtle(player.inventory, turtle.getAccess());
               }
            case 104:
            default:
               break;
            case 105:
               return new ContainerHeldItem(player.inventory);
            case 106:
               return new ContainerHeldItem(player.inventory);
         }

         return null;
      }

      public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
         TileEntity tile = world.getTileEntity(x, y, z);
         switch (id) {
            case 100:
               if (tile != null && tile instanceof TileDiskDrive) {
                  TileDiskDrive drive = (TileDiskDrive)tile;
                  return ComputerCraftProxyCommon.this.getDiskDriveGUI(player.inventory, drive);
               }
               break;
            case 101:
               if (tile != null && tile instanceof TileComputer) {
                  TileComputer computer = (TileComputer)tile;
                  return ComputerCraftProxyCommon.this.getComputerGUI(computer);
               }
               break;
            case 102:
               if (tile != null && tile instanceof TilePrinter) {
                  TilePrinter printer = (TilePrinter)tile;
                  return ComputerCraftProxyCommon.this.getPrinterGUI(player.inventory, printer);
               }
               break;
            case 103:
               if (tile != null && tile instanceof TileTurtle) {
                  TileTurtle turtle = (TileTurtle)tile;
                  return ComputerCraftProxyCommon.this.getTurtleGUI(player.inventory, turtle);
               }
            case 104:
            default:
               break;
            case 105:
               return ComputerCraftProxyCommon.this.getPrintoutGUI(player.inventory);
            case 106:
               return ComputerCraftProxyCommon.this.getPocketComputerGUI(player.inventory);
         }

         return null;
      }

      @SubscribeEvent
      public void onConnectionOpened(ClientConnectedToServerEvent event) {
         ComputerCraft.clientComputerRegistry.reset();
      }

      @SubscribeEvent
      public void onConnectionClosed(ClientDisconnectionFromServerEvent event) {
         ComputerCraft.clientComputerRegistry.reset();
      }

      @SubscribeEvent
      public void onClientTick(ClientTickEvent event) {
         if (event.phase == Phase.START) {
            ComputerCraft.clientComputerRegistry.update();
         }
      }

      @SubscribeEvent
      public void onServerTick(ServerTickEvent event) {
         if (event.phase == Phase.START) {
            MainThread.executePendingTasks();
            ComputerCraft.serverComputerRegistry.update();
         }
      }

      @SubscribeEvent
      public void onWorldLoad(Load event) {
      }

      @SubscribeEvent
      public void onWorldUnload(Unload event) {
      }
   }
}
