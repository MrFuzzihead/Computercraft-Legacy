package dan200.computercraft.shared.peripheral.generic;

import java.util.List;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IMultiTypePeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralTargeted;

/**
 * Aggregating peripheral that merges an ordered list of {@link IPeripheral} modules into a
 * single peripheral surface. Method indices are partitioned by module in the order they were
 * supplied; dispatch is delegated to the owning module with the index adjusted to that
 * module's local zero-based range.
 *
 * <p>
 * The peripheral type is taken from the first module. The {@link IPeripheralTargeted#getTarget()}
 * value is supplied explicitly at construction time (typically the underlying
 * {@link net.minecraft.tileentity.TileEntity}) so that other peripherals can resolve
 * the target object via the standard {@code IPeripheralTargeted} contract (e.g. for
 * cross-peripheral item transfer).
 * </p>
 *
 * <p>
 * To add a new capability module (e.g. {@code EnergyStoragePeripheral}), simply append it
 * to the module list passed to this constructor — no changes to this class are required.
 * </p>
 */
public class GenericCombinedPeripheral implements IMultiTypePeripheral, IPeripheralTargeted {

    private final List<IPeripheral> m_modules;
    /** Inclusive start index (in the merged array) of each module's methods. */
    private final int[] m_moduleStart;
    private final String[] m_methodNames;
    private final String[] m_types;
    private final Object m_target;

    /**
     * @param modules ordered list of peripheral modules to merge; must not be empty
     * @param target  the object returned by {@link #getTarget()} — typically the tile entity
     */
    public GenericCombinedPeripheral(List<IPeripheral> modules, Object target) {
        if (modules.isEmpty()) {
            throw new IllegalArgumentException("GenericCombinedPeripheral requires at least one module");
        }
        m_modules = modules;
        m_target = target;

        // Collect one type string per module.
        m_types = new String[modules.size()];
        for (int i = 0; i < modules.size(); i++) {
            m_types[i] = modules.get(i)
                .getType();
        }

        // Pre-compute the merged method name array and per-module start offsets.
        int total = 0;
        for (IPeripheral module : modules) {
            total += module.getMethodNames().length;
        }

        m_methodNames = new String[total];
        m_moduleStart = new int[modules.size()];
        int offset = 0;
        for (int i = 0; i < modules.size(); i++) {
            m_moduleStart[i] = offset;
            String[] names = modules.get(i)
                .getMethodNames();
            System.arraycopy(names, 0, m_methodNames, offset, names.length);
            offset += names.length;
        }
    }

    @Override
    public String getType() {
        // Use the first module's type as the primary type.
        return m_types[0];
    }

    @Override
    public String[] getTypes() {
        return m_types;
    }

    @Override
    public Object getTarget() {
        return m_target;
    }

    @Override
    public String[] getMethodNames() {
        return m_methodNames;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] args)
        throws LuaException, InterruptedException {
        // Find the owning module by scanning start offsets in reverse so the last
        // module whose start is <= method wins.
        int moduleIndex = 0;
        for (int i = m_moduleStart.length - 1; i >= 0; i--) {
            if (method >= m_moduleStart[i]) {
                moduleIndex = i;
                break;
            }
        }
        int localMethod = method - m_moduleStart[moduleIndex];
        return m_modules.get(moduleIndex)
            .callMethod(computer, context, localMethod, args);
    }

    @Override
    public void attach(IComputerAccess computer) {
        for (IPeripheral module : m_modules) {
            module.attach(computer);
        }
    }

    @Override
    public void detach(IComputerAccess computer) {
        for (IPeripheral module : m_modules) {
            module.detach(computer);
        }
    }

    @Override
    public boolean equals(IPeripheral other) {
        if (other instanceof GenericCombinedPeripheral) {
            return m_target == ((GenericCombinedPeripheral) other).m_target;
        }
        return false;
    }
}
