package dan200.computercraft.core.lua;

import static org.mockito.Mockito.mock;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.IComputerEnvironment;
import dan200.computercraft.core.lua.lib.cobalt.CobaltMachine;
import dan200.computercraft.core.terminal.Terminal;

/**
 * Factory for {@link CobaltMachine} instances for ROM/Lua-level tests.
 *
 * <p>
 * {@code CobaltMachine}'s constructor populates the {@code _HOST} global from
 * {@code computer.getAPIEnvironment().getComputerEnvironment().getHostString()}, so it cannot be
 * handed a {@code null} computer. These tests only need a machine to host the Lua state — they
 * register their own APIs and mounts — so the {@link Computer} supplied here is backed by an
 * inert environment and is never turned on.
 * </p>
 *
 * <p>
 * Note that {@link Computer}'s constructor calls {@code ComputerThread.start()}; the shared worker
 * pool's threads are daemons, so it is not a JVM-lifetime requirement. Tests that assert on the
 * pool itself are responsible for stopping it.
 * </p>
 */
final class LuaTestMachine {

    private LuaTestMachine() {}

    /**
     * Creates a machine whose environment does not identify itself, so {@code _HOST} is the
     * interface default empty string.
     *
     * @return a new machine, not turned on
     */
    static CobaltMachine create() {
        return create("");
    }

    /**
     * Creates a machine whose environment reports the given host identity.
     *
     * @param hostString value to expose as the {@code _HOST} global
     * @return a new machine, not turned on
     */
    static CobaltMachine create(String hostString) {
        return new CobaltMachine(newComputer(hostString));
    }

    private static Computer newComputer(String hostString) {
        return new Computer(new IComputerEnvironment() {

            @Override
            public int getDay() {
                return 1;
            }

            @Override
            public double getTimeOfDay() {
                return 0.0;
            }

            @Override
            public boolean isColour() {
                return true;
            }

            @Override
            public long getComputerSpaceLimit() {
                return 1024 * 1024;
            }

            @Override
            public int assignNewID() {
                return 1;
            }

            @Override
            public IWritableMount createSaveDirMount(String subPath, long capacity) {
                return mock(IWritableMount.class);
            }

            @Override
            public IMount createResourceMount(String domain, String subPath) {
                return mock(IMount.class);
            }

            @Override
            public String getHostString() {
                return hostString;
            }
        }, new Terminal(51, 19), 1);
    }
}
