package dan200.computercraft.shared.computer.core;

import java.util.Iterator;

import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

public class ServerComputerRegistry extends ComputerRegistry<ServerComputer> {

    public void update() {
        Iterator<ServerComputer> it = this.getComputers()
            .iterator();

        while (it.hasNext()) {
            ServerComputer computer = it.next();
            // Keep the computer alive as long as its chunk is still loaded.
            // This allows computers to continue running when the player is outside
            // the 64-block TileEntity tick range but the chunk remains loaded
            // (e.g. via a chunk loader, or normal server view-distance).
            if (isChunkLoaded(computer)) {
                computer.keepAlive();
            }
            if (computer.hasTimedOut()) {
                computer.unload();
                computer.broadcastDelete();
                it.remove();
            } else {
                computer.update();
                if (computer.hasTerminalChanged() || computer.hasOutputChanged()) {
                    computer.broadcastState();
                }
            }
        }
    }

    /**
     * Returns {@code true} when the chunk containing {@code computer} is
     * currently loaded in its world. Returns {@code false} for pocket computers
     * (no fixed position) and for any computer whose world reference is absent.
     */
    private static boolean isChunkLoaded(ServerComputer computer) {
        World world = computer.getWorld();
        ChunkCoordinates pos = computer.getPosition();
        return world != null && pos != null && world.blockExists(pos.posX, pos.posY, pos.posZ);
    }

    public void add(int instanceID, ServerComputer computer) {
        super.add(instanceID, computer);
        computer.broadcastState();
    }

    @Override
    public void remove(int instanceID) {
        ServerComputer computer = this.get(instanceID);
        if (computer != null) {
            computer.unload();
            computer.broadcastDelete();
        }

        super.remove(instanceID);
    }

    @Override
    public void reset() {
        for (ServerComputer computer : this.getComputers()) {
            computer.unload();
        }

        super.reset();
    }
}
