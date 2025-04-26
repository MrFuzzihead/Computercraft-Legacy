package dan200.computercraft.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

public class FixedRenderBlocks extends RenderBlocks {
   public void func_147761_c(Block par1Block, double par2, double par4, double par6, IIcon par8IIcon) {
      Tessellator tessellator = Tessellator.field_78398_a;
      if (this.func_147744_b()) {
         par8IIcon = this.field_147840_d;
      }

      double d3 = par8IIcon.func_94214_a(this.field_147859_h * 16.0);
      double d4 = par8IIcon.func_94214_a(this.field_147861_i * 16.0);
      double d5 = par8IIcon.func_94207_b(16.0 - this.field_147857_k * 16.0);
      double d6 = par8IIcon.func_94207_b(16.0 - this.field_147855_j * 16.0);
      if (this.field_147842_e) {
         double d7 = d3;
         d3 = d4;
         d4 = d7;
      }

      if (this.field_147859_h < 0.0 || this.field_147861_i > 1.0) {
         d3 = par8IIcon.func_94209_e();
         d4 = par8IIcon.func_94212_f();
      }

      if (this.field_147855_j < 0.0 || this.field_147857_k > 1.0) {
         d5 = par8IIcon.func_94206_g();
         d6 = par8IIcon.func_94210_h();
      }

      double d7 = d4;
      double d8 = d3;
      double d9 = d5;
      double d10 = d6;
      if (this.field_147875_q == 2) {
         d3 = par8IIcon.func_94214_a(16.0 - this.field_147859_h * 16.0);
         d5 = par8IIcon.func_94207_b(16.0 - this.field_147855_j * 16.0);
         d4 = par8IIcon.func_94214_a(16.0 - this.field_147861_i * 16.0);
         d6 = par8IIcon.func_94207_b(16.0 - this.field_147857_k * 16.0);
         d9 = d5;
         d10 = d6;
         d7 = d3;
         d8 = d4;
         d5 = d6;
         d6 = d5;
      } else if (this.field_147875_q == 1) {
         d3 = par8IIcon.func_94214_a(this.field_147857_k * 16.0);
         d5 = par8IIcon.func_94207_b(this.field_147861_i * 16.0);
         d4 = par8IIcon.func_94214_a(this.field_147855_j * 16.0);
         d6 = par8IIcon.func_94207_b(this.field_147859_h * 16.0);
         d7 = d4;
         d8 = d3;
         d3 = d4;
         d4 = d3;
         d9 = d6;
         d10 = d5;
      } else if (this.field_147875_q == 3) {
         d3 = par8IIcon.func_94214_a(16.0 - this.field_147859_h * 16.0);
         d4 = par8IIcon.func_94214_a(16.0 - this.field_147861_i * 16.0);
         d5 = par8IIcon.func_94207_b(this.field_147857_k * 16.0);
         d6 = par8IIcon.func_94207_b(this.field_147855_j * 16.0);
         d7 = d4;
         d8 = d3;
         d9 = d5;
         d10 = d6;
      }

      double d11 = par2 + this.field_147859_h;
      double d12 = par2 + this.field_147861_i;
      double d13 = par4 + this.field_147855_j;
      double d14 = par4 + this.field_147857_k;
      double d15 = par6 + this.field_147851_l;
      if (this.field_147863_w) {
         tessellator.func_78386_a(this.field_147872_ap, this.field_147846_at, this.field_147854_ax);
         tessellator.func_78380_c(this.field_147864_al);
         tessellator.addVertexWithUV(d11, d14, d15, d7, d9);
         tessellator.func_78386_a(this.field_147852_aq, this.field_147860_au, this.field_147841_ay);
         tessellator.func_78380_c(this.field_147874_am);
         tessellator.addVertexWithUV(d12, d14, d15, d3, d5);
         tessellator.func_78386_a(this.field_147850_ar, this.field_147858_av, this.field_147839_az);
         tessellator.func_78380_c(this.field_147876_an);
         tessellator.addVertexWithUV(d12, d13, d15, d8, d10);
         tessellator.func_78386_a(this.field_147848_as, this.field_147856_aw, this.field_147833_aA);
         tessellator.func_78380_c(this.field_147870_ao);
         tessellator.addVertexWithUV(d11, d13, d15, d4, d6);
      } else {
         tessellator.addVertexWithUV(d11, d14, d15, d7, d9);
         tessellator.addVertexWithUV(d12, d14, d15, d3, d5);
         tessellator.addVertexWithUV(d12, d13, d15, d8, d10);
         tessellator.addVertexWithUV(d11, d13, d15, d4, d6);
      }
   }

   public void func_147764_f(Block par1Block, double par2, double par4, double par6, IIcon par8IIcon) {
      Tessellator tessellator = Tessellator.field_78398_a;
      if (this.func_147744_b()) {
         par8IIcon = this.field_147840_d;
      }

      double d3 = par8IIcon.func_94214_a(this.field_147851_l * 16.0);
      double d4 = par8IIcon.func_94214_a(this.field_147853_m * 16.0);
      double d5 = par8IIcon.func_94207_b(16.0 - this.field_147857_k * 16.0);
      double d6 = par8IIcon.func_94207_b(16.0 - this.field_147855_j * 16.0);
      if (this.field_147842_e) {
         double d7 = d3;
         d3 = d4;
         d4 = d7;
      }

      if (this.field_147851_l < 0.0 || this.field_147853_m > 1.0) {
         d3 = par8IIcon.func_94209_e();
         d4 = par8IIcon.func_94212_f();
      }

      if (this.field_147855_j < 0.0 || this.field_147857_k > 1.0) {
         d5 = par8IIcon.func_94206_g();
         d6 = par8IIcon.func_94210_h();
      }

      double d7 = d4;
      double d8 = d3;
      double d9 = d5;
      double d10 = d6;
      if (this.field_147871_s == 2) {
         d3 = par8IIcon.func_94214_a(16.0 - this.field_147851_l * 16.0);
         d5 = par8IIcon.func_94207_b(16.0 - this.field_147855_j * 16.0);
         d4 = par8IIcon.func_94214_a(16.0 - this.field_147853_m * 16.0);
         d6 = par8IIcon.func_94207_b(16.0 - this.field_147857_k * 16.0);
         d9 = d5;
         d10 = d6;
         d7 = d3;
         d8 = d4;
         d5 = d6;
         d6 = d5;
      } else if (this.field_147871_s == 1) {
         d3 = par8IIcon.func_94214_a(this.field_147857_k * 16.0);
         d5 = par8IIcon.func_94207_b(this.field_147853_m * 16.0);
         d4 = par8IIcon.func_94214_a(this.field_147855_j * 16.0);
         d6 = par8IIcon.func_94207_b(this.field_147851_l * 16.0);
         d7 = d4;
         d8 = d3;
         d3 = d4;
         d4 = d3;
         d9 = d6;
         d10 = d5;
      } else if (this.field_147871_s == 3) {
         d3 = par8IIcon.func_94214_a(16.0 - this.field_147851_l * 16.0);
         d4 = par8IIcon.func_94214_a(16.0 - this.field_147853_m * 16.0);
         d5 = par8IIcon.func_94207_b(this.field_147857_k * 16.0);
         d6 = par8IIcon.func_94207_b(this.field_147855_j * 16.0);
         d7 = d4;
         d8 = d3;
         d9 = d5;
         d10 = d6;
      }

      double d11 = par2 + this.field_147861_i;
      double d12 = par4 + this.field_147855_j;
      double d13 = par4 + this.field_147857_k;
      double d14 = par6 + this.field_147851_l;
      double d15 = par6 + this.field_147853_m;
      if (this.field_147863_w) {
         tessellator.func_78386_a(this.field_147872_ap, this.field_147846_at, this.field_147854_ax);
         tessellator.func_78380_c(this.field_147864_al);
         tessellator.addVertexWithUV(d11, d12, d15, d8, d10);
         tessellator.func_78386_a(this.field_147852_aq, this.field_147860_au, this.field_147841_ay);
         tessellator.func_78380_c(this.field_147874_am);
         tessellator.addVertexWithUV(d11, d12, d14, d4, d6);
         tessellator.func_78386_a(this.field_147850_ar, this.field_147858_av, this.field_147839_az);
         tessellator.func_78380_c(this.field_147876_an);
         tessellator.addVertexWithUV(d11, d13, d14, d7, d9);
         tessellator.func_78386_a(this.field_147848_as, this.field_147856_aw, this.field_147833_aA);
         tessellator.func_78380_c(this.field_147870_ao);
         tessellator.addVertexWithUV(d11, d13, d15, d3, d5);
      } else {
         tessellator.addVertexWithUV(d11, d12, d15, d8, d10);
         tessellator.addVertexWithUV(d11, d12, d14, d4, d6);
         tessellator.addVertexWithUV(d11, d13, d14, d7, d9);
         tessellator.addVertexWithUV(d11, d13, d15, d3, d5);
      }
   }

   public void setWorld(IBlockAccess world) {
      this.field_147845_a = world;
   }
}
