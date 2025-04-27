package dan200.computercraft.shared.turtle.blocks;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.shared.common.IDirectionalTile;
import dan200.computercraft.shared.computer.blocks.IComputerTile;
import dan200.computercraft.shared.util.Colour;

public interface ITurtleTile extends IComputerTile, IDirectionalTile {

    Colour getColour();

    ResourceLocation getOverlay();

    ResourceLocation getHatOverlay();

    ITurtleUpgrade getUpgrade(TurtleSide var1);

    ITurtleAccess getAccess();

    Vec3 getRenderOffset(float var1);

    float getRenderYaw(float var1);

    float getToolRenderAngle(TurtleSide var1, float var2);
}
