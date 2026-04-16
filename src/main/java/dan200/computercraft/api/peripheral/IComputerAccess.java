package dan200.computercraft.api.peripheral;

import java.util.Collections;
import java.util.Map;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;

public interface IComputerAccess {

    String mount(String var1, IMount var2);

    String mount(String var1, IMount var2, String var3);

    String mountWritable(String var1, IWritableMount var2);

    String mountWritable(String var1, IWritableMount var2, String var3);

    void unmount(String var1);

    int getID();

    void queueEvent(String var1, Object[] var2);

    String getAttachmentName();

    default Map<String, IPeripheral> getAvailablePeripherals() {
        return Collections.emptyMap();
    }

    /**
     * Returns the {@link IPeripheral} attached to this computer under the given name, or {@code null} if no such
     * peripheral is currently connected.
     *
     * <p>
     * For direct-side connections the name is a side string (e.g. {@code "right"}). For wired-modem
     * connections the name is the cable-assigned peripheral name (e.g. {@code "tile_iron_tank_0"}).
     * </p>
     *
     * <p>
     * The default implementation returns {@code null}; concrete {@link IComputerAccess} implementations
     * override this to scan the set of peripherals visible to the connected computer.
     * </p>
     *
     * @param name the attachment name to look up
     * @return the attached {@link IPeripheral}, or {@code null}
     */
    default IPeripheral getAvailablePeripheral(String name) {
        return null;
    }
}
