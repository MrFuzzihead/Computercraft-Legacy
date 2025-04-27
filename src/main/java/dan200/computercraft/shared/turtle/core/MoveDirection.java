package dan200.computercraft.shared.turtle.core;

import net.minecraft.util.Facing;

import dan200.computercraft.api.turtle.ITurtleAccess;

public enum MoveDirection {

    Forward,
    Back,
    Up,
    Down;

    public int toWorldDir(ITurtleAccess turtle) {
        switch (this) {
            case Forward:
            default:
                return turtle.getDirection();
            case Back:
                return Facing.oppositeSide[turtle.getDirection()];
            case Up:
                return 1;
            case Down:
                return 0;
        }
    }
}
