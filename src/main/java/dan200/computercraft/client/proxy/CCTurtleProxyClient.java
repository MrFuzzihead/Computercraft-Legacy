package dan200.computercraft.client.proxy;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import net.minecraftforge.client.IItemRenderer.ItemRendererHelper;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.client.render.TileEntityTurtleRenderer;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.proxy.CCTurtleProxyCommon;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;
import dan200.computercraft.shared.turtle.core.TurtleBrain;
import dan200.computercraft.shared.util.Colour;

public class CCTurtleProxyClient extends CCTurtleProxyCommon {

    private static TileEntityTurtleRenderer m_turtleRenderer;
    private static final ResourceLocation turtleModelTexture = new ResourceLocation(
        "computercraft",
        "textures/models/turtle.png");
    private static final ResourceLocation turtleModelTextureAdvanced = new ResourceLocation(
        "computercraft",
        "textures/models/turtleAdvanced.png");
    private static final ResourceLocation turtleModelTexturePainted = new ResourceLocation(
        "computercraft",
        "textures/models/turtlePainted.png");
    private static final ResourceLocation turtleModelTexturePaintedOverlay = new ResourceLocation(
        "computercraft",
        "textures/models/turtlePaintedOverlay.png");
    private static final ResourceLocation turtleModelTextureBeginners = new ResourceLocation(
        "computercraftedu",
        "textures/models/turtleBeginners.png");
    private static final ResourceLocation turtleModelTextureBeginnersOverlay = new ResourceLocation(
        "computercraftedu",
        "textures/models/turtleBeginnersOverlay.png");

    public CCTurtleProxyClient() {
        m_turtleRenderer = null;
    }

    @Override
    public void init() {
        super.init();
        ComputerCraft.Blocks.turtle.blockRenderID = RenderingRegistry.getNextAvailableRenderId();
        m_turtleRenderer = new TileEntityTurtleRenderer();
        ClientRegistry.bindTileEntitySpecialRenderer(TileTurtle.class, m_turtleRenderer);
        this.registerForgeHandlers();
    }

    @Override
    public void getTurtleModelTextures(List<ResourceLocation> list, ComputerFamily family, Colour colour) {
        if (family == ComputerFamily.Beginners) {
            list.add(turtleModelTextureBeginners);
            list.add(turtleModelTextureBeginnersOverlay);
        } else if (colour != null) {
            list.add(turtleModelTexturePainted);
            list.add(turtleModelTexturePaintedOverlay);
        } else if (family == ComputerFamily.Advanced) {
            list.add(turtleModelTextureAdvanced);
        } else {
            list.add(turtleModelTexture);
        }
    }

    public static CCTurtleProxyClient.TurtleRenderer getTurtleRenderer() {
        return new CCTurtleProxyClient.TurtleRenderer();
    }

    private void registerForgeHandlers() {
        CCTurtleProxyClient.ForgeHandlers handlers = new CCTurtleProxyClient.ForgeHandlers();
        FMLCommonHandler.instance()
            .bus()
            .register(handlers);
        MinecraftForge.EVENT_BUS.register(handlers);
        CCTurtleProxyClient.TurtleRenderer renderer = getTurtleRenderer();
        MinecraftForgeClient.registerItemRenderer(Item.getItemFromBlock(ComputerCraft.Blocks.turtle), renderer);
        MinecraftForgeClient.registerItemRenderer(Item.getItemFromBlock(ComputerCraft.Blocks.turtleExpanded), renderer);
        MinecraftForgeClient.registerItemRenderer(Item.getItemFromBlock(ComputerCraft.Blocks.turtleAdvanced), renderer);
        RenderingRegistry.registerBlockHandler(renderer);
    }

    public class ForgeHandlers {

        @SubscribeEvent
        public void onTick(ClientTickEvent event) {
            if (event.phase == Phase.END) {
                TurtleBrain.cleanupBrains();
            }
        }
    }

    public static class TurtleRenderer implements IItemRenderer, ISimpleBlockRenderingHandler {

        public boolean handleRenderType(ItemStack item, ItemRenderType type) {
            switch (type) {
                case ENTITY:
                case EQUIPPED:
                case EQUIPPED_FIRST_PERSON:
                case INVENTORY:
                    return true;
                case FIRST_PERSON_MAP:
                default:
                    return false;
            }
        }

        public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
            switch (helper) {
                case ENTITY_ROTATION:
                case ENTITY_BOBBING:
                case EQUIPPED_BLOCK:
                case BLOCK_3D:
                case INVENTORY_BLOCK:
                    return true;
                default:
                    return false;
            }
        }

        public void renderItem(ItemRenderType type, ItemStack item, Object[] data) {
            switch (type) {
                case ENTITY:
                case INVENTORY:
                    this.renderTurtle(item);
                    break;
                case EQUIPPED:
                case EQUIPPED_FIRST_PERSON:
                    GL11.glPushMatrix();
                    GL11.glTranslatef(0.5F, 0.5F, 0.5F);
                    GL11.glRotatef(180.0F, 0.0F, 1.0F, 0.0F);
                    this.renderTurtle(item);
                    GL11.glPopMatrix();
            }
        }

        public boolean shouldRender3DInInventory(int modelID) {
            return true;
        }

        public int getRenderId() {
            return ComputerCraft.Blocks.turtle.blockRenderID;
        }

        public boolean renderWorldBlock(IBlockAccess iblockaccess, int i, int j, int k, Block block, int modelID,
            RenderBlocks renderblocks) {
            return modelID == this.getRenderId();
        }

        public void renderInventoryBlock(Block block, int metadata, int modelID, RenderBlocks renderblocks) {}

        private void renderTurtle(ItemStack turtle) {
            GL11.glPushMatrix();
            GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
            GL11.glScalef(1.15F, 1.15F, 1.15F);
            CCTurtleProxyClient.m_turtleRenderer.renderInventoryTurtle(turtle);
            GL11.glPopMatrix();
        }
    }
}
