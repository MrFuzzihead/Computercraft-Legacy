package dan200.computercraft.client.proxy;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.client.gui.FixedWidthFontRenderer;
import dan200.computercraft.client.gui.GuiComputer;
import dan200.computercraft.client.gui.GuiDiskDrive;
import dan200.computercraft.client.gui.GuiPocketComputer;
import dan200.computercraft.client.gui.GuiPrinter;
import dan200.computercraft.client.gui.GuiPrintout;
import dan200.computercraft.client.gui.GuiTurtle;
import dan200.computercraft.client.render.FixedRenderBlocks;
import dan200.computercraft.client.render.TileEntityMonitorRenderer;
import dan200.computercraft.shared.computer.blocks.TileComputer;
import dan200.computercraft.shared.media.inventory.ContainerHeldItem;
import dan200.computercraft.shared.media.items.ItemPrintout;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.BlockCable;
import dan200.computercraft.shared.peripheral.common.BlockPeripheral;
import dan200.computercraft.shared.peripheral.common.ItemCable;
import dan200.computercraft.shared.peripheral.common.ItemPeripheral;
import dan200.computercraft.shared.peripheral.diskdrive.TileDiskDrive;
import dan200.computercraft.shared.peripheral.monitor.TileMonitor;
import dan200.computercraft.shared.peripheral.printer.TilePrinter;
import dan200.computercraft.shared.pocket.items.ItemPocketComputer;
import dan200.computercraft.shared.proxy.ComputerCraftProxyCommon;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;
import dan200.computercraft.shared.turtle.entity.TurtleVisionCamera;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.Pre;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.opengl.GL11;

public class ComputerCraftProxyClient extends ComputerCraftProxyCommon {
   private long m_tickCount;
   private FixedWidthFontRenderer m_fixedWidthFontRenderer;
   private FixedRenderBlocks m_fixedRenderBlocks;

   @Override
   public void init() {
      super.init();
      this.m_tickCount = 0L;
      this.m_fixedRenderBlocks = new FixedRenderBlocks();
      Minecraft mc = FMLClientHandler.instance().getClient();
      this.m_fixedWidthFontRenderer = new FixedWidthFontRenderer(mc.field_71446_o);
      IResourceManager resourceManager = mc.func_110442_L();
      if (resourceManager instanceof IReloadableResourceManager) {
         IReloadableResourceManager reloadableResourceManager = (IReloadableResourceManager)resourceManager;
         reloadableResourceManager.func_110542_a(this.m_fixedWidthFontRenderer);
      }

      ComputerCraft.Blocks.computer.blockRenderID = RenderingRegistry.getNextAvailableRenderId();
      ComputerCraft.Blocks.peripheral.blockRenderID = RenderingRegistry.getNextAvailableRenderId();
      ComputerCraft.Blocks.cable.blockRenderID = RenderingRegistry.getNextAvailableRenderId();
      ComputerCraft.Blocks.commandComputer.blockRenderID = RenderingRegistry.getNextAvailableRenderId();
      ClientRegistry.bindTileEntitySpecialRenderer(TileMonitor.class, new TileEntityMonitorRenderer());
      this.registerForgeHandlers();
   }

   @Override
   public boolean isClient() {
      return true;
   }

   @Override
   public boolean getGlobalCursorBlink() {
      return this.m_tickCount / 8L % 2L == 0L;
   }

   @Override
   public Object getFixedWidthFontRenderer() {
      return this.m_fixedWidthFontRenderer;
   }

   @Override
   public String getRecordInfo(ItemStack recordStack) {
      List info = new ArrayList(1);
      recordStack.getItem().addInformation(recordStack, null, info, false);
      return info.size() > 0 ? info.get(0).toString() : super.getRecordInfo(recordStack);
   }

   @Override
   public void playRecord(String record, String recordInfo, World world, int x, int y, int z) {
      Minecraft mc = FMLClientHandler.instance().getClient();
      world.func_72934_a(record, x, y, z);
      if (record != null) {
         mc.field_71456_v.func_73833_a(recordInfo);
      }
   }

   @Override
   public Object getDiskDriveGUI(InventoryPlayer inventory, TileDiskDrive drive) {
      return new GuiDiskDrive(inventory, drive);
   }

   @Override
   public Object getComputerGUI(TileComputer computer) {
      return new GuiComputer(computer);
   }

   @Override
   public Object getPrinterGUI(InventoryPlayer inventory, TilePrinter printer) {
      return new GuiPrinter(inventory, printer);
   }

   @Override
   public Object getTurtleGUI(InventoryPlayer inventory, TileTurtle turtle) {
      return new GuiTurtle(turtle.getWorldObj(), inventory, turtle);
   }

   @Override
   public Object getPrintoutGUI(InventoryPlayer inventory) {
      ContainerHeldItem container = new ContainerHeldItem(inventory);
      return container.getStack() != null && container.getStack().getItem() instanceof ItemPrintout ? new GuiPrintout(container) : null;
   }

   @Override
   public Object getPocketComputerGUI(InventoryPlayer inventory) {
      ContainerHeldItem container = new ContainerHeldItem(inventory);
      return container.getStack() != null && container.getStack().getItem() instanceof ItemPocketComputer ? new GuiPocketComputer(container) : null;
   }

   @Override
   public File getWorldDir(World world) {
      return new File(ComputerCraft.getBaseDir(), "saves/" + world.getSaveHandler().getWorldDirectory());
   }

   private void registerForgeHandlers() {
      ComputerCraftProxyClient.ForgeHandlers handlers = new ComputerCraftProxyClient.ForgeHandlers();
      FMLCommonHandler.instance().bus().register(handlers);
      MinecraftForge.EVENT_BUS.register(handlers);
      ComputerCraftProxyClient.ComputerBlockRenderingHandler computerHandler = new ComputerCraftProxyClient.ComputerBlockRenderingHandler(
         ComputerCraft.Blocks.computer.blockRenderID
      );
      RenderingRegistry.registerBlockHandler(computerHandler);
      ComputerCraftProxyClient.ComputerBlockRenderingHandler commmandComputerHandler = new ComputerCraftProxyClient.ComputerBlockRenderingHandler(
         ComputerCraft.Blocks.commandComputer.blockRenderID
      );
      RenderingRegistry.registerBlockHandler(commmandComputerHandler);
      ComputerCraftProxyClient.PeripheralBlockRenderingHandler peripheralHandler = new ComputerCraftProxyClient.PeripheralBlockRenderingHandler();
      RenderingRegistry.registerBlockHandler(peripheralHandler);
      ComputerCraftProxyClient.CableBlockRenderingHandler cableHandler = new ComputerCraftProxyClient.CableBlockRenderingHandler();
      RenderingRegistry.registerBlockHandler(cableHandler);
   }

   private static void renderStandardInvBlock(RenderBlocks renderblocks, Block block, int damage) {
      Tessellator tessellator = Tessellator.field_78398_a;
      tessellator.func_78382_b();
      tessellator.func_78375_b(0.0F, -1.0F, 0.0F);
      renderblocks.func_147768_a(block, 0.0, 0.0, 0.0, block.func_149691_a(0, damage));
      tessellator.func_78381_a();
      tessellator.func_78382_b();
      tessellator.func_78375_b(0.0F, 1.0F, 0.0F);
      renderblocks.func_147806_b(block, 0.0, 0.0, 0.0, block.func_149691_a(1, damage));
      tessellator.func_78381_a();
      tessellator.func_78382_b();
      tessellator.func_78375_b(0.0F, 0.0F, -1.0F);
      renderblocks.func_147761_c(block, 0.0, 0.0, 0.0, block.func_149691_a(2, damage));
      tessellator.func_78381_a();
      tessellator.func_78382_b();
      tessellator.func_78375_b(0.0F, 0.0F, 1.0F);
      renderblocks.func_147734_d(block, 0.0, 0.0, 0.0, block.func_149691_a(3, damage));
      tessellator.func_78381_a();
      tessellator.func_78382_b();
      tessellator.func_78375_b(-1.0F, 0.0F, 0.0F);
      renderblocks.func_147798_e(block, 0.0, 0.0, 0.0, block.func_149691_a(4, damage));
      tessellator.func_78381_a();
      tessellator.func_78382_b();
      tessellator.func_78375_b(1.0F, 0.0F, 0.0F);
      renderblocks.func_147764_f(block, 0.0, 0.0, 0.0, block.func_149691_a(5, damage));
      tessellator.func_78381_a();
      GL11.glTranslatef(0.5F, 0.5F, 0.5F);
   }

   private class CableBlockRenderingHandler implements ISimpleBlockRenderingHandler {
      public CableBlockRenderingHandler() {
      }

      public boolean shouldRender3DInInventory(int modelID) {
         return true;
      }

      public int getRenderId() {
         return ComputerCraft.Blocks.cable.blockRenderID;
      }

      public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelID, RenderBlocks renderblocks) {
         if (modelID != this.getRenderId()) {
            return false;
         } else {
            BlockCable cable = (BlockCable)block;
            PeripheralType type = cable.getPeripheralType(world, x, y, z);
            if (type == PeripheralType.Cable || type == PeripheralType.WiredModemWithCable) {
               ComputerCraftProxyClient.this.m_fixedRenderBlocks.setWorld(world);
               ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147782_a(0.375, 0.375, 0.375, 0.625, 0.625, 0.625);
               ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147784_q(block, x, y, z);
               int modemDir;
               if (type == PeripheralType.WiredModemWithCable) {
                  modemDir = cable.getDirection(world, x, y, z);
               } else {
                  modemDir = -1;
               }

               if (BlockCable.isCable(world, x, y - 1, z) || modemDir == 0) {
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147782_a(0.375, 0.0, 0.375, 0.625, 0.375, 0.625);
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147784_q(block, x, y, z);
               }

               if (BlockCable.isCable(world, x, y + 1, z) || modemDir == 1) {
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147782_a(0.375, 0.625, 0.375, 0.625, 1.0, 0.625);
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147784_q(block, x, y, z);
               }

               if (BlockCable.isCable(world, x, y, z - 1) || modemDir == 2) {
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147782_a(0.375, 0.375, 0.0, 0.625, 0.625, 0.375);
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147784_q(block, x, y, z);
               }

               if (BlockCable.isCable(world, x, y, z + 1) || modemDir == 3) {
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147782_a(0.375, 0.375, 0.625, 0.625, 0.625, 1.0);
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147784_q(block, x, y, z);
               }

               if (BlockCable.isCable(world, x - 1, y, z) || modemDir == 4) {
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147782_a(0.0, 0.375, 0.375, 0.375, 0.625, 0.625);
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147784_q(block, x, y, z);
               }

               if (BlockCable.isCable(world, x + 1, y, z) || modemDir == 5) {
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147782_a(0.625, 0.375, 0.375, 1.0, 0.625, 0.625);
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147784_q(block, x, y, z);
               }

               block.getCollisionBoundingBoxFromPool(world, x, y, z);
            }

            if (type == PeripheralType.WiredModem || type == PeripheralType.WiredModemWithCable) {
               BlockCable.renderAsModem = true;
               block.getCollisionBoundingBoxFromPool(world, x, y, z);
               ComputerCraftProxyClient.this.m_fixedRenderBlocks.setWorld(world);
               ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147775_a(block);
               ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147784_q(block, x, y, z);
               BlockCable.renderAsModem = false;
               block.getCollisionBoundingBoxFromPool(world, x, y, z);
            }

            return true;
         }
      }

      public void renderInventoryBlock(Block block, int damage, int modelID, RenderBlocks renderblocks) {
         if (modelID == this.getRenderId()) {
            ItemCable cable = (ItemCable)Item.getItemFromBlock(block);
            PeripheralType type = cable.getPeripheralType(damage);
            switch (type) {
               case Cable:
                  GL11.glPushMatrix();
                  GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
                  block.setBlockBounds(0.375F, 0.375F, 0.125F, 0.625F, 0.625F, 0.875F);
                  renderblocks.func_147775_a(block);
                  ComputerCraftProxyClient.renderStandardInvBlock(renderblocks, block, damage);
                  GL11.glPopMatrix();
                  break;
               case WiredModem:
                  GL11.glPushMatrix();
                  GL11.glScalef(1.333F, 1.333F, 1.333F);
                  GL11.glTranslatef(-0.5F, -0.5F, -0.09375F);
                  block.setBlockBounds(0.125F, 0.125F, 0.0F, 0.875F, 0.875F, 0.1875F);
                  renderblocks.func_147775_a(block);
                  ComputerCraftProxyClient.renderStandardInvBlock(renderblocks, block, damage);
                  GL11.glPopMatrix();
            }
         }
      }
   }

   private class ComputerBlockRenderingHandler implements ISimpleBlockRenderingHandler {
      private int m_renderID;

      public ComputerBlockRenderingHandler(int renderID) {
         this.m_renderID = renderID;
      }

      public boolean shouldRender3DInInventory(int modelID) {
         return true;
      }

      public int getRenderId() {
         return this.m_renderID;
      }

      public boolean renderWorldBlock(IBlockAccess iblockaccess, int i, int j, int k, Block block, int modelID, RenderBlocks renderblocks) {
         if (modelID == this.m_renderID) {
            block.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
            ComputerCraftProxyClient.this.m_fixedRenderBlocks.setWorld(iblockaccess);
            ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147775_a(block);
            ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147784_q(block, i, j, k);
            return true;
         } else {
            return false;
         }
      }

      public void renderInventoryBlock(Block block, int metadata, int modelID, RenderBlocks renderblocks) {
         if (modelID == this.m_renderID) {
            GL11.glPushMatrix();
            GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
            renderblocks.func_147775_a(block);
            block.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
            ComputerCraftProxyClient.renderStandardInvBlock(renderblocks, block, metadata);
            GL11.glPopMatrix();
         }
      }
   }

   public class ForgeHandlers {
      @SubscribeEvent
      public void onRenderHand(RenderHandEvent event) {
         Minecraft mc = Minecraft.getMinecraft();
         if (mc.renderViewEntity instanceof TurtleVisionCamera) {
            event.setCanceled(true);
         }
      }

      @SubscribeEvent
      public void onPreRenderGameOverlay(Pre event) {
         Minecraft mc = Minecraft.getMinecraft();
         if (mc.renderViewEntity instanceof TurtleVisionCamera) {
            switch (event.type) {
               case HELMET:
               case PORTAL:
               case BOSSHEALTH:
               case ARMOR:
               case HEALTH:
               case FOOD:
               case AIR:
               case HOTBAR:
               case EXPERIENCE:
               case HEALTHMOUNT:
               case JUMPBAR:
                  event.setCanceled(true);
            }
         }
      }

      @SubscribeEvent
      public void onTick(ClientTickEvent event) {
         if (event.phase == Phase.START) {
            ComputerCraftProxyClient.this.m_tickCount++;
         }
      }
   }

   private class PeripheralBlockRenderingHandler implements ISimpleBlockRenderingHandler {
      public PeripheralBlockRenderingHandler() {
      }

      public boolean shouldRender3DInInventory(int modelID) {
         return true;
      }

      public int getRenderId() {
         return ComputerCraft.Blocks.peripheral.blockRenderID;
      }

      public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelID, RenderBlocks renderblocks) {
         if (modelID == this.getRenderId()) {
            BlockPeripheral peripheral = (BlockPeripheral)block;
            PeripheralType type = peripheral.getPeripheralType(world, x, y, z);
            switch (type) {
               case DiskDrive:
               case Printer:
               case WirelessModem:
                  peripheral.getCollisionBoundingBoxFromPool(world, x, y, z);
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.setWorld(world);
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147775_a(block);
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147784_q(block, x, y, z);
                  return true;
               case Monitor:
               case AdvancedMonitor:
                  int dir = 2;
                  TileEntity tile = world.getTileEntity(x, y, z);
                  if (tile != null && tile instanceof TileMonitor) {
                     TileMonitor monitor = (TileMonitor)tile;
                     dir = monitor.getDir();
                  }

                  switch (dir) {
                     case 2:
                     case 6:
                     case 7:
                     case 12:
                     case 13:
                     default:
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147867_u = 3;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147865_v = 3;
                        break;
                     case 3:
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147867_u = 0;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147865_v = 0;
                        break;
                     case 4:
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147867_u = 1;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147865_v = 2;
                        break;
                     case 5:
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147867_u = 2;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147865_v = 1;
                        break;
                     case 8:
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147869_t = 2;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147871_s = 1;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147873_r = 3;
                        break;
                     case 9:
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147869_t = 1;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147871_s = 2;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147875_q = 3;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147867_u = 3;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147865_v = 3;
                        break;
                     case 10:
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147871_s = 3;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147875_q = 1;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147873_r = 2;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147867_u = 2;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147865_v = 1;
                        break;
                     case 11:
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147869_t = 3;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147875_q = 2;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147873_r = 1;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147867_u = 1;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147865_v = 2;
                        break;
                     case 14:
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147869_t = 1;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147871_s = 2;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147875_q = 0;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147873_r = 3;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147867_u = 3;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147865_v = 3;
                        break;
                     case 15:
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147869_t = 2;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147871_s = 1;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147875_q = 3;
                        break;
                     case 16:
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147871_s = 3;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147875_q = 2;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147873_r = 1;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147867_u = 1;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147865_v = 2;
                        break;
                     case 17:
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147869_t = 3;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147875_q = 1;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147873_r = 2;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147867_u = 2;
                        ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147865_v = 1;
                  }

                  peripheral.getCollisionBoundingBoxFromPool(world, x, y, z);
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.setWorld(world);
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147775_a(block);
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.func_147784_q(block, x, y, z);
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147869_t = 0;
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147871_s = 0;
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147875_q = 0;
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147873_r = 0;
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147867_u = 0;
                  ComputerCraftProxyClient.this.m_fixedRenderBlocks.field_147865_v = 0;
                  return true;
            }
         }

         return false;
      }

      public void renderInventoryBlock(Block block, int damage, int modelID, RenderBlocks renderblocks) {
         if (modelID == this.getRenderId()) {
            ItemPeripheral peripheral = (ItemPeripheral)Item.getItemFromBlock(block);
            PeripheralType type = peripheral.getPeripheralType(damage);
            switch (type) {
               case DiskDrive:
               case Printer:
               case Monitor:
               case AdvancedMonitor:
                  GL11.glPushMatrix();
                  GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
                  block.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
                  renderblocks.func_147775_a(block);
                  ComputerCraftProxyClient.renderStandardInvBlock(renderblocks, block, damage);
                  GL11.glPopMatrix();
                  break;
               case WirelessModem:
                  GL11.glPushMatrix();
                  GL11.glScalef(1.333F, 1.333F, 1.333F);
                  GL11.glTranslatef(-0.5F, -0.5F, -0.09375F);
                  block.setBlockBounds(0.125F, 0.125F, 0.0F, 0.875F, 0.875F, 0.1875F);
                  renderblocks.func_147775_a(block);
                  ComputerCraftProxyClient.renderStandardInvBlock(renderblocks, block, damage);
                  GL11.glPopMatrix();
            }
         }
      }
   }
}
