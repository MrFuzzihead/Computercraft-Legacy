package dan200.computercraft.testsupport;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.apis.IAPIEnvironment;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.IComputerEnvironment;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.terminal.Terminal;

/**
 * An {@link IAPIEnvironment} backed by a real {@link FileSystem}, for tests that need a working
 * {@code fs} API but no running computer.
 *
 * <p>
 * Only {@link #getFileSystem()} carries state; everything else is inert. This was previously a
 * private class inside {@code FSAPITest}; it lives here so the {@code io} tests can use it too.
 * </p>
 */
public class StubApiEnvironment implements IAPIEnvironment {

    private final FileSystem m_fs;
    private final Terminal m_terminal;

    public StubApiEnvironment(FileSystem fs) {
        this(fs, null);
    }

    public StubApiEnvironment(FileSystem fs, Terminal terminal) {
        m_fs = fs;
        m_terminal = terminal;
    }

    @Override
    public FileSystem getFileSystem() {
        return m_fs;
    }

    @Override
    public Computer getComputer() {
        return null;
    }

    @Override
    public int getComputerID() {
        return 1;
    }

    @Override
    public IComputerEnvironment getComputerEnvironment() {
        return null;
    }

    @Override
    public Terminal getTerminal() {
        return m_terminal;
    }

    @Override
    public void shutdown() {}

    @Override
    public void reboot() {}

    @Override
    public void queueEvent(String event, Object[] args) {}

    @Override
    public void setOutput(int side, int output) {}

    @Override
    public int getOutput(int side) {
        return 0;
    }

    @Override
    public int getInput(int side) {
        return 0;
    }

    @Override
    public void setBundledOutput(int side, int output) {}

    @Override
    public int getBundledOutput(int side) {
        return 0;
    }

    @Override
    public int getBundledInput(int side) {
        return 0;
    }

    @Override
    public void setPeripheralChangeListener(IAPIEnvironment.IPeripheralChangeListener listener) {}

    @Override
    public IPeripheral getPeripheral(int side) {
        return null;
    }

    @Override
    public String getLabel() {
        return null;
    }

    @Override
    public void setLabel(String label) {}
}
