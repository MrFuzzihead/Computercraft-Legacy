package dan200.computercraft.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

public class FixedRenderBlocks extends RenderBlocks {

    public void renderFaceZNeg(Block par1Block, double par2, double par4, double par6, IIcon par8IIcon) {
        Tessellator tessellator = Tessellator.instance;
        if (this.hasOverrideBlockTexture()) {
            par8IIcon = this.overrideBlockTexture;
        }

        double d3 = par8IIcon.getInterpolatedU(this.renderMinX * 16.0);
        double d4 = par8IIcon.getInterpolatedU(this.renderMaxX * 16.0);
        double d5 = par8IIcon.getInterpolatedV(16.0 - this.renderMaxY * 16.0);
        double d6 = par8IIcon.getInterpolatedV(16.0 - this.renderMinY * 16.0);
        if (this.flipTexture) {
            double d7 = d3;
            d3 = d4;
            d4 = d7;
        }

        if (this.renderMinX < 0.0 || this.renderMaxX > 1.0) {
            d3 = par8IIcon.getMinU();
            d4 = par8IIcon.getMaxU();
        }

        if (this.renderMinY < 0.0 || this.renderMaxY > 1.0) {
            d5 = par8IIcon.getMinV();
            d6 = par8IIcon.getMaxV();
        }

        double d7 = d4;
        double d8 = d3;
        double d9 = d5;
        double d10 = d6;
        if (this.uvRotateEast == 2) {
            d3 = par8IIcon.getInterpolatedU(16.0 - this.renderMinX * 16.0);
            d5 = par8IIcon.getInterpolatedV(16.0 - this.renderMinY * 16.0);
            d4 = par8IIcon.getInterpolatedU(16.0 - this.renderMaxX * 16.0);
            d6 = par8IIcon.getInterpolatedV(16.0 - this.renderMaxY * 16.0);
            d9 = d5;
            d10 = d6;
            d7 = d3;
            d8 = d4;
            d5 = d6;
            d6 = d5;
        } else if (this.uvRotateEast == 1) {
            d3 = par8IIcon.getInterpolatedU(this.renderMaxY * 16.0);
            d5 = par8IIcon.getInterpolatedV(this.renderMaxX * 16.0);
            d4 = par8IIcon.getInterpolatedU(this.renderMinY * 16.0);
            d6 = par8IIcon.getInterpolatedV(this.renderMinX * 16.0);
            d7 = d4;
            d8 = d3;
            d3 = d4;
            d4 = d3;
            d9 = d6;
            d10 = d5;
        } else if (this.uvRotateEast == 3) {
            d3 = par8IIcon.getInterpolatedU(16.0 - this.renderMinX * 16.0);
            d4 = par8IIcon.getInterpolatedU(16.0 - this.renderMaxX * 16.0);
            d5 = par8IIcon.getInterpolatedV(this.renderMaxY * 16.0);
            d6 = par8IIcon.getInterpolatedV(this.renderMinY * 16.0);
            d7 = d4;
            d8 = d3;
            d9 = d5;
            d10 = d6;
        }

        double d11 = par2 + this.renderMinX;
        double d12 = par2 + this.renderMaxX;
        double d13 = par4 + this.renderMinY;
        double d14 = par4 + this.renderMaxY;
        double d15 = par6 + this.renderMinZ;
        if (this.enableAO) {
            tessellator.setColorOpaque_F(this.colorRedTopLeft, this.colorGreenTopLeft, this.colorBlueTopLeft);
            tessellator.setBrightness(this.brightnessTopLeft);
            tessellator.addVertexWithUV(d11, d14, d15, d7, d9);
            tessellator.setColorOpaque_F(this.colorRedBottomLeft, this.colorGreenBottomLeft, this.colorBlueBottomLeft);
            tessellator.setBrightness(this.brightnessBottomLeft);
            tessellator.addVertexWithUV(d12, d14, d15, d3, d5);
            tessellator
                .setColorOpaque_F(this.colorRedBottomRight, this.colorGreenBottomRight, this.colorBlueBottomRight);
            tessellator.setBrightness(this.brightnessBottomRight);
            tessellator.addVertexWithUV(d12, d13, d15, d8, d10);
            tessellator.setColorOpaque_F(this.colorRedTopRight, this.colorGreenTopRight, this.colorBlueTopRight);
            tessellator.setBrightness(this.brightnessTopRight);
            tessellator.addVertexWithUV(d11, d13, d15, d4, d6);
        } else {
            tessellator.addVertexWithUV(d11, d14, d15, d7, d9);
            tessellator.addVertexWithUV(d12, d14, d15, d3, d5);
            tessellator.addVertexWithUV(d12, d13, d15, d8, d10);
            tessellator.addVertexWithUV(d11, d13, d15, d4, d6);
        }
    }

    public void renderFaceXPos(Block par1Block, double par2, double par4, double par6, IIcon par8IIcon) {
        Tessellator tessellator = Tessellator.instance;
        if (this.hasOverrideBlockTexture()) {
            par8IIcon = this.overrideBlockTexture;
        }

        double d3 = par8IIcon.getInterpolatedU(this.renderMinZ * 16.0);
        double d4 = par8IIcon.getInterpolatedU(this.renderMaxZ * 16.0);
        double d5 = par8IIcon.getInterpolatedV(16.0 - this.renderMaxY * 16.0);
        double d6 = par8IIcon.getInterpolatedV(16.0 - this.renderMinY * 16.0);
        if (this.flipTexture) {
            double d7 = d3;
            d3 = d4;
            d4 = d7;
        }

        if (this.renderMinZ < 0.0 || this.renderMaxZ > 1.0) {
            d3 = par8IIcon.getMinU();
            d4 = par8IIcon.getMaxU();
        }

        if (this.renderMinY < 0.0 || this.renderMaxY > 1.0) {
            d5 = par8IIcon.getMinV();
            d6 = par8IIcon.getMaxV();
        }

        double d7 = d4;
        double d8 = d3;
        double d9 = d5;
        double d10 = d6;
        if (this.uvRotateSouth == 2) {
            d3 = par8IIcon.getInterpolatedU(16.0 - this.renderMinZ * 16.0);
            d5 = par8IIcon.getInterpolatedV(16.0 - this.renderMinY * 16.0);
            d4 = par8IIcon.getInterpolatedU(16.0 - this.renderMaxZ * 16.0);
            d6 = par8IIcon.getInterpolatedV(16.0 - this.renderMaxY * 16.0);
            d9 = d5;
            d10 = d6;
            d7 = d3;
            d8 = d4;
            d5 = d6;
            d6 = d5;
        } else if (this.uvRotateSouth == 1) {
            d3 = par8IIcon.getInterpolatedU(this.renderMaxY * 16.0);
            d5 = par8IIcon.getInterpolatedV(this.renderMaxZ * 16.0);
            d4 = par8IIcon.getInterpolatedU(this.renderMinY * 16.0);
            d6 = par8IIcon.getInterpolatedV(this.renderMinZ * 16.0);
            d7 = d4;
            d8 = d3;
            d3 = d4;
            d4 = d3;
            d9 = d6;
            d10 = d5;
        } else if (this.uvRotateSouth == 3) {
            d3 = par8IIcon.getInterpolatedU(16.0 - this.renderMinZ * 16.0);
            d4 = par8IIcon.getInterpolatedU(16.0 - this.renderMaxZ * 16.0);
            d5 = par8IIcon.getInterpolatedV(this.renderMaxY * 16.0);
            d6 = par8IIcon.getInterpolatedV(this.renderMinY * 16.0);
            d7 = d4;
            d8 = d3;
            d9 = d5;
            d10 = d6;
        }

        double d11 = par2 + this.renderMaxX;
        double d12 = par4 + this.renderMinY;
        double d13 = par4 + this.renderMaxY;
        double d14 = par6 + this.renderMinZ;
        double d15 = par6 + this.renderMaxZ;
        if (this.enableAO) {
            tessellator.setColorOpaque_F(this.colorRedTopLeft, this.colorGreenTopLeft, this.colorBlueTopLeft);
            tessellator.setBrightness(this.brightnessTopLeft);
            tessellator.addVertexWithUV(d11, d12, d15, d8, d10);
            tessellator.setColorOpaque_F(this.colorRedBottomLeft, this.colorGreenBottomLeft, this.colorBlueBottomLeft);
            tessellator.setBrightness(this.brightnessBottomLeft);
            tessellator.addVertexWithUV(d11, d12, d14, d4, d6);
            tessellator
                .setColorOpaque_F(this.colorRedBottomRight, this.colorGreenBottomRight, this.colorBlueBottomRight);
            tessellator.setBrightness(this.brightnessBottomRight);
            tessellator.addVertexWithUV(d11, d13, d14, d7, d9);
            tessellator.setColorOpaque_F(this.colorRedTopRight, this.colorGreenTopRight, this.colorBlueTopRight);
            tessellator.setBrightness(this.brightnessTopRight);
            tessellator.addVertexWithUV(d11, d13, d15, d3, d5);
        } else {
            tessellator.addVertexWithUV(d11, d12, d15, d8, d10);
            tessellator.addVertexWithUV(d11, d12, d14, d4, d6);
            tessellator.addVertexWithUV(d11, d13, d14, d7, d9);
            tessellator.addVertexWithUV(d11, d13, d15, d3, d5);
        }
    }

    public void setWorld(IBlockAccess world) {
        this.blockAccess = world;
    }
}
