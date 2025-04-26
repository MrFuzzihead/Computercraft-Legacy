package dan200.computercraft.client.render;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleUpgradeType;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.core.IComputer;
import dan200.computercraft.shared.turtle.blocks.ITurtleTile;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;
import dan200.computercraft.shared.turtle.entity.TurtleVisionCamera;
import dan200.computercraft.shared.turtle.items.ITurtleItem;
import dan200.computercraft.shared.util.Colour;
import dan200.computercraft.shared.util.Holiday;
import dan200.computercraft.shared.util.HolidayUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import org.lwjgl.opengl.GL11;

public class TileEntityTurtleRenderer extends TileEntitySpecialRenderer {
   private static final ResourceLocation elfOverlay = new ResourceLocation("computercraft", "textures/models/elfHat.png");
   private ModelTurtle model;
   private List s_turtleTextures = new ArrayList(3);

   public TileEntityTurtleRenderer() {
      this.model = new ModelTurtle();
   }

   public void render(TileEntity tileentity, double x, double y, double z, float f) {
      if (tileentity instanceof TileTurtle && !tileentity.isInvalid() && ((TileTurtle)tileentity).getBlock() != null) {
         ITurtleTile turtle = (ITurtleTile)tileentity;
         Minecraft mc = Minecraft.getMinecraft();
         if (mc.renderViewEntity instanceof TurtleVisionCamera) {
            TurtleVisionCamera camera = (TurtleVisionCamera)mc.renderViewEntity;
            if (camera.getTurtle() == turtle.getAccess()) {
               return;
            }
         }

         GL11.glPushMatrix();
         GL11.glTranslated(x, y, z);
         Vec3 offset = turtle.getRenderOffset(f);
         GL11.glTranslated(offset.xCoord, offset.yCoord, offset.zCoord);
         IComputer computer = turtle.getComputer();
         String label = computer != null ? computer.getLabel() : null;
         if (label != null) {
            this.renderLabel(turtle.getAccess().getPosition(), label);
         }

         GL11.glTranslatef(0.5F, 0.5F, 0.5F);
         GL11.glRotatef(turtle.getRenderYaw(f), 0.0F, -1.0F, 0.0F);
         GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
         this.renderTurtle(
            turtle,
            turtle.getFamily(),
            turtle.getColour(),
            turtle.getUpgrade(TurtleSide.Left),
            turtle.getUpgrade(TurtleSide.Right),
            turtle.getToolRenderAngle(TurtleSide.Left, f),
            turtle.getToolRenderAngle(TurtleSide.Right, f),
            turtle.getOverlay(),
            turtle.getHatOverlay()
         );
         GL11.glPopMatrix();
      }
   }

   public void renderInventoryTurtle(ItemStack stack) {
      if (stack.getItem() instanceof ITurtleItem) {
         ITurtleItem item = (ITurtleItem)stack.getItem();
         this.renderTurtle(
            null,
            item.getFamily(stack),
            item.getColour(stack),
            item.getUpgrade(stack, TurtleSide.Left),
            item.getUpgrade(stack, TurtleSide.Right),
            0.0F,
            0.0F,
            item.getOverlay(stack),
            item.getHatOverlay(stack)
         );
      }
   }

   private void renderTurtle(
      ITurtleTile turtle,
      ComputerFamily family,
      Colour colour,
      ITurtleUpgrade leftUpgrade,
      ITurtleUpgrade rightUpgrade,
      float leftToolAngle,
      float rightToolAngle,
      ResourceLocation overlay,
      ResourceLocation hatOverlay
   ) {
      GL11.glPushMatrix();
      GL11.glEnable(3042);
      GL11.glBlendFunc(770, 771);
      Minecraft mc = Minecraft.getMinecraft();
      this.s_turtleTextures.clear();
      ComputerCraft.getTurtleModelTextures(this.s_turtleTextures, family, colour);

      for (int i = 0; i < this.s_turtleTextures.size(); i++) {
         if (i == 0 && colour != null) {
            GL11.glColor4f(colour.getR(), colour.getG(), colour.getB(), 1.0F);
         } else {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
         }

         mc.func_110434_K().bindTexture((ResourceLocation)this.s_turtleTextures.get(i));
         this.model.render(0.0625F);
      }

      if (overlay == null && hatOverlay == null) {
         if (HolidayUtil.getCurrentHoliday() == Holiday.Christmas && family != ComputerFamily.Beginners) {
            mc.func_110434_K().bindTexture(elfOverlay);
            this.model.render(0.0625F);
         }
      } else {
         if (overlay != null) {
            mc.func_110434_K().bindTexture(overlay);
            this.model.render(0.0625F);
         }

         if (hatOverlay != null) {
            mc.func_110434_K().bindTexture(hatOverlay);
            this.model.renderHat(0.0625F);
         }
      }

      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      this.renderUpgrade(turtle, leftUpgrade, TurtleSide.Left, leftToolAngle);
      this.renderUpgrade(turtle, rightUpgrade, TurtleSide.Right, rightToolAngle);
      GL11.glPopMatrix();
   }

   private void renderUpgrade(ITurtleTile turtle, ITurtleUpgrade upgrade, TurtleSide side, float toolAngle) {
      if (upgrade != null) {
         if (upgrade.getType() == TurtleUpgradeType.Tool) {
            this.renderTool(turtle, upgrade, side, toolAngle);
         } else {
            this.renderPeripheral(turtle, upgrade, side);
         }
      }
   }

   private void renderPeripheral(ITurtleTile turtle, ITurtleUpgrade upgrade, TurtleSide side) {
      GL11.glPushMatrix();
      GL11.glTranslatef(0.0F, 0.0F, 0.03125F);
      if (side == TurtleSide.Right) {
         GL11.glTranslatef(0.5F, 0.0F, 0.5F);
         GL11.glRotatef(180.0F, 0.0F, 1.0F, 0.0F);
         GL11.glTranslatef(-0.5F, 0.0F, -0.5F);
      }

      GL11.glTranslatef(0.875F, 0.0F, 0.0F);
      GL11.glTranslatef(0.0F, 0.0F, 0.5F);
      GL11.glRotatef(270.0F, 0.0F, 1.0F, 0.0F);
      GL11.glTranslatef(-0.5F, 0.0F, 0.0F);
      GL11.glRotatef(270.0F, 1.0F, 0.0F, 0.0F);
      GL11.glTranslatef(0.5F, 0.0F, 0.5F);
      GL11.glScalef(0.666F, 0.666F, 0.666F);
      Tessellator tesselator = Tessellator.field_78398_a;
      Minecraft mc = Minecraft.getMinecraft();
      mc.func_110434_K().bindTexture(mc.func_110434_K().func_130087_a(0));
      ITurtleAccess access = turtle != null ? turtle.getAccess() : null;
      IIcon icon = upgrade.getIcon(access, side);
      float startX = icon.func_94209_e();
      float endX = icon.func_94212_f();
      float startY = icon.func_94206_g();
      float endY = icon.func_94210_h();
      float spanX = endX - startX;
      float spanY = endY - startY;
      float borderStep = 0.125F;
      float depthStep = 0.1875F;
      float borderStepTex = 0.125F * spanX;
      float depthStepTex = 0.1875F * spanY;

      for (int i = 0; i < 4; i++) {
         tesselator.func_78382_b();
         tesselator.func_78375_b(0.0F, 0.0F, 1.0F);
         tesselator.addVertexWithUV(-0.5F + borderStep, depthStep, 0.5F - borderStep, endX - borderStepTex, endY - depthStepTex);
         tesselator.addVertexWithUV(-0.5F + borderStep, 0.0, 0.5F - borderStep, endX - borderStepTex, endY);
         tesselator.addVertexWithUV(0.5F - borderStep, 0.0, 0.5F - borderStep, startX + borderStepTex, endY);
         tesselator.addVertexWithUV(0.5F - borderStep, depthStep, 0.5F - borderStep, startX + borderStepTex, endY - depthStepTex);
         tesselator.func_78381_a();
         GL11.glRotatef(90.0F, 0.0F, 1.0F, 0.0F);
      }

      tesselator.func_78382_b();
      tesselator.func_78375_b(0.0F, 1.0F, 0.0F);
      tesselator.addVertexWithUV(-0.5F + borderStep, depthStep, -0.5F + borderStep, endX - borderStepTex, endY - borderStepTex);
      tesselator.addVertexWithUV(-0.5F + borderStep, depthStep, 0.5F - borderStep, endX - borderStepTex, startY + borderStepTex);
      tesselator.addVertexWithUV(0.5F - borderStep, depthStep, 0.5F - borderStep, startX + borderStepTex, startY + borderStepTex);
      tesselator.addVertexWithUV(0.5F - borderStep, depthStep, -0.5F + borderStep, startX + borderStepTex, endY - borderStepTex);
      tesselator.func_78381_a();
      GL11.glPopMatrix();
   }

   private void renderTool(ITurtleTile turtle, ITurtleUpgrade upgrade, TurtleSide side, float angle) {
      GL11.glPushMatrix();
      GL11.glTranslatef(0.0F, 0.5F, 0.5F);
      GL11.glRotatef(angle + 90.0F, 1.0F, 0.0F, 0.0F);
      GL11.glTranslatef(0.0F, -0.5F, -0.5F);
      if (side == TurtleSide.Left) {
         GL11.glTranslatef(0.875F, 0.0F, 0.0F);
      } else {
         GL11.glTranslatef(0.0625F, 0.0F, 0.0F);
      }

      GL11.glTranslatef(0.0F, 0.0F, 0.5F);
      GL11.glRotatef(270.0F, 0.0F, 1.0F, 0.0F);
      GL11.glTranslatef(-0.5F, 0.0F, 0.0F);
      Tessellator tesselator = Tessellator.field_78398_a;
      Minecraft mc = Minecraft.getMinecraft();
      mc.func_110434_K().bindTexture(mc.func_110434_K().func_130087_a(1));
      ITurtleAccess access = turtle != null ? turtle.getAccess() : null;
      IIcon upgradeIcon = upgrade.getIcon(access, side);
      if (upgradeIcon != null) {
         renderItemIn2D(tesselator, upgradeIcon);
      }

      GL11.glPopMatrix();
   }

   private static void renderItemIn2D(Tessellator tesselator, IIcon icon) {
      ItemRenderer.func_78439_a(
         tesselator, icon.func_94212_f(), icon.func_94206_g(), icon.func_94209_e(), icon.func_94210_h(), icon.func_94211_a(), icon.func_94216_b(), 0.0625F
      );
      GL11.glEnable(32826);
   }

   private void renderLabel(ChunkCoordinates position, String label) {
      RenderManager renderManager = RenderManager.field_78727_a;
      MovingObjectPosition mop = Minecraft.getMinecraft().field_71476_x;
      if (mop != null
         && mop.typeOfHit == MovingObjectType.BLOCK
         && mop.field_72311_b == position.posX
         && mop.field_72312_c == position.posY
         && mop.field_72309_d == position.posZ) {
         FontRenderer fontrenderer = renderManager.func_78716_a();
         float scale = 0.02666667F;
         GL11.glPushMatrix();
         GL11.glTranslated(0.5, 1.25, 0.5);
         GL11.glNormal3f(0.0F, 1.0F, 0.0F);
         GL11.glRotatef(-renderManager.field_78735_i, 0.0F, 1.0F, 0.0F);
         GL11.glRotatef(renderManager.field_78732_j, 1.0F, 0.0F, 0.0F);
         GL11.glScalef(-scale, -scale, scale);
         GL11.glDisable(2896);
         GL11.glDepthMask(false);
         GL11.glDisable(2929);
         GL11.glEnable(3042);
         GL11.glBlendFunc(770, 771);
         Tessellator tessellator = Tessellator.field_78398_a;
         int yOffset = 0;
         int xOffset = fontrenderer.getStringWidth(label) / 2;
         GL11.glDisable(3553);
         tessellator.func_78382_b();
         tessellator.func_78369_a(0.0F, 0.0F, 0.0F, 0.25F);
         tessellator.func_78377_a(-xOffset - 1, -1 + yOffset, 0.0);
         tessellator.func_78377_a(-xOffset - 1, 8 + yOffset, 0.0);
         tessellator.func_78377_a(xOffset + 1, 8 + yOffset, 0.0);
         tessellator.func_78377_a(xOffset + 1, -1 + yOffset, 0.0);
         tessellator.func_78381_a();
         GL11.glEnable(3553);
         fontrenderer.drawString(label, -fontrenderer.getStringWidth(label) / 2, yOffset, 553648127);
         GL11.glEnable(2929);
         GL11.glDepthMask(true);
         fontrenderer.drawString(label, -fontrenderer.getStringWidth(label) / 2, yOffset, -1);
         GL11.glEnable(2896);
         GL11.glDisable(3042);
         GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
         GL11.glPopMatrix();
      }
   }
}
