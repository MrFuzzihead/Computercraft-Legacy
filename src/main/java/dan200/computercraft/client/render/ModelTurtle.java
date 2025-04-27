package dan200.computercraft.client.render;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;

import org.lwjgl.opengl.GL11;

public class ModelTurtle extends ModelBase {

    private ModelRenderer[] m_parts = new ModelRenderer[2];
    private ModelRenderer[] m_hatParts;

    public ModelTurtle() {
        this.m_parts[0] = new ModelRenderer(this, 0, 0);
        this.m_parts[0].addBox(2.0F, 2.0F, 3.0F, 12, 12, 11);
        this.m_parts[0].setRotationPoint(0.0F, 0.0F, 0.0F);
        this.m_parts[1] = new ModelRenderer(this, 35, 0);
        this.m_parts[1].addBox(3.0F, 6.0F, 1.0F, 10, 7, 2);
        this.m_parts[1].setRotationPoint(0.0F, 0.0F, 0.0F);
        this.m_hatParts = new ModelRenderer[1];
        this.m_hatParts[0] = new ModelRenderer(this, 0, 0);
        this.m_hatParts[0].addBox(2.0F, 2.0F, 3.0F, 12, 12, 11, 0.5F);
        this.m_hatParts[0].setRotationPoint(0.0F, 0.0F, 0.0F);
    }

    public void render(float f) {
        for (int i = 0; i < this.m_parts.length; i++) {
            this.m_parts[i].render(f);
        }
    }

    public void renderHat(float f) {
        GL11.glDisable(2884);

        for (int i = 0; i < this.m_hatParts.length; i++) {
            this.m_hatParts[i].render(f);
        }

        GL11.glEnable(2884);
    }
}
