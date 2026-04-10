package dan200.computercraft.core.computer;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;

public interface IComputerEnvironment {

    int getDay();

    double getTimeOfDay();

    boolean isColour();

    long getComputerSpaceLimit();

    int assignNewID();

    IWritableMount createSaveDirMount(String var1, long var2);

    IMount createResourceMount(String var1, String var2);

    /**
     * Returns a string identifying the host environment, e.g.
     * {@code "ComputerCraft 2.1.3 (Minecraft 1.7.10)"}. Used to populate
     * the {@code _HOST} global in the Lua environment.
     *
     * <p>
     * The default implementation returns an empty string so that
     * existing anonymous implementations (e.g. in unit tests) compile
     * without change.
     * </p>
     */
    default String getHostString() {
        return "";
    }
}
