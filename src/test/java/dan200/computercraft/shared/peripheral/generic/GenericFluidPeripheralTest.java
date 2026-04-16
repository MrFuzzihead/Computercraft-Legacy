package dan200.computercraft.shared.peripheral.generic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import org.junit.jupiter.api.Test;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

/**
 * Unit tests for {@link GenericFluidPeripheral}.
 *
 * <h2>Coverage</h2>
 * <ul>
 * <li>Method surface ({@code getType}, {@code getMethodNames} count and index correctness).</li>
 * <li>{@code tanks()} with an empty handler (no fluid stored).</li>
 * <li>{@code pushFluid} / {@code pullFluid} argument validation.</li>
 * <li>{@code pushFluid} / {@code pullFluid} error paths — unknown target and non-fluid-storage
 * target.</li>
 * <li>End-to-end zero-transfer smoke test — proves the main-thread dispatch wiring works when
 * both handlers have no stored fluid.</li>
 * </ul>
 *
 * <h2>Out-of-scope (in-game tests)</h2>
 * <p>
 * Successful fluid transfer (non-zero mB moved) and {@link dan200.computercraft.shared.util.FluidUtil}
 * fluid-filter paths require {@code FluidStack} objects, whose constructor calls
 * {@code FluidRegistry.makeDelegate()}, which triggers {@code FluidRegistry.<clinit>} and crashes
 * outside a running Minecraft server. Those paths are verified by the in-game test script
 * {@code test_fluid_storage.lua}.
 * </p>
 */
class GenericFluidPeripheralTest {

    // =========================================================================
    // Constants
    // =========================================================================

    private static final int METHOD_TANKS = 0;
    private static final int METHOD_PUSH_FLUID = 1;
    private static final int METHOD_PULL_FLUID = 2;

    // =========================================================================
    // Infrastructure — synchronous ILuaContext
    // =========================================================================

    /**
     * Executes {@link ILuaTask} callbacks synchronously so tests do not need a
     * running Minecraft main thread.
     */
    private static final ILuaContext SYNC_CONTEXT = new ILuaContext() {

        @Override
        public Object[] executeMainThreadTask(ILuaTask task) throws LuaException, InterruptedException {
            return task.execute();
        }

        @Override
        public long issueMainThreadTask(ILuaTask task) throws LuaException {
            return 0L;
        }

        @Override
        public Object[] pullEvent(String filter) throws LuaException, InterruptedException {
            return null;
        }

        @Override
        public Object[] pullEventRaw(String filter) throws InterruptedException {
            return null;
        }

        @Override
        public Object[] yield(Object[] args) throws InterruptedException {
            return null;
        }
    };

    // =========================================================================
    // Infrastructure — minimal IComputerAccess stubs
    // =========================================================================

    /** Minimal {@link IComputerAccess} that always returns {@code null} from peripheral lookups. */
    private static final IComputerAccess NULL_COMPUTER = new IComputerAccess() {

        @Override
        public String mount(String desiredLoc, IMount mount) {
            return null;
        }

        @Override
        public String mount(String desiredLoc, IMount mount, String driveName) {
            return null;
        }

        @Override
        public String mountWritable(String desiredLoc, IWritableMount mount) {
            return null;
        }

        @Override
        public String mountWritable(String desiredLoc, IWritableMount mount, String driveName) {
            return null;
        }

        @Override
        public void unmount(String location) {}

        @Override
        public int getID() {
            return 0;
        }

        @Override
        public void queueEvent(String event, Object[] arguments) {}

        @Override
        public String getAttachmentName() {
            return "right";
        }
    };

    /**
     * Creates an {@link IComputerAccess} that returns {@code peripheral} when
     * {@link IComputerAccess#getAvailablePeripheral} is called with {@code name}, and
     * {@code null} for every other name.
     */
    private static IComputerAccess computerWith(final String name, final IPeripheral peripheral) {
        return new IComputerAccess() {

            @Override
            public IPeripheral getAvailablePeripheral(String lookupName) {
                return name.equals(lookupName) ? peripheral : null;
            }

            @Override
            public String mount(String desiredLoc, IMount mount) {
                return null;
            }

            @Override
            public String mount(String desiredLoc, IMount mount, String driveName) {
                return null;
            }

            @Override
            public String mountWritable(String desiredLoc, IWritableMount mount) {
                return null;
            }

            @Override
            public String mountWritable(String desiredLoc, IWritableMount mount, String driveName) {
                return null;
            }

            @Override
            public void unmount(String location) {}

            @Override
            public int getID() {
                return 0;
            }

            @Override
            public void queueEvent(String event, Object[] arguments) {}

            @Override
            public String getAttachmentName() {
                return "right";
            }
        };
    }

    // =========================================================================
    // Infrastructure — mock IFluidHandler
    // =========================================================================

    /**
     * Minimal {@link IFluidHandler} that is always empty (all {@code drain} calls return
     * {@code null}, all {@code fill} calls return 0). Safe to use without Forge/Minecraft
     * initialisation because it never constructs a {@link FluidStack} object.
     *
     * <p>
     * The {@link #getTankInfo} implementation creates a {@link FluidTankInfo} with a
     * {@code null} fluid and a fixed capacity — the {@code FluidTankInfo(FluidStack, int)}
     * constructor accepts {@code null} and does not reference {@code FluidRegistry}.
     * </p>
     */
    private static final class EmptyTank implements IFluidHandler {

        private final int capacity;

        EmptyTank(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
            return 0;
        }

        @Override
        public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
            return null;
        }

        @Override
        public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
            return null;
        }

        @Override
        public boolean canFill(ForgeDirection from, Fluid fluid) {
            return false;
        }

        @Override
        public boolean canDrain(ForgeDirection from, Fluid fluid) {
            return false;
        }

        @Override
        public FluidTankInfo[] getTankInfo(ForgeDirection from) {
            // FluidTankInfo(null, capacity) does not require FluidRegistry.
            return new FluidTankInfo[] { new FluidTankInfo(null, capacity) };
        }
    }

    // =========================================================================
    // Method surface
    // =========================================================================

    @Test
    void getTypeReturnsFluidStorage() {
        GenericFluidPeripheral p = new GenericFluidPeripheral(new EmptyTank(1000));
        assertEquals("fluid_storage", p.getType());
    }

    @Test
    void getMethodNamesHasThreeMethods() {
        GenericFluidPeripheral p = new GenericFluidPeripheral(new EmptyTank(1000));
        assertEquals(3, p.getMethodNames().length);
    }

    @Test
    void methodNamesAreInCorrectOrder() {
        String[] names = new GenericFluidPeripheral(new EmptyTank(1000)).getMethodNames();
        assertEquals("tanks", names[METHOD_TANKS]);
        assertEquals("pushFluid", names[METHOD_PUSH_FLUID]);
        assertEquals("pullFluid", names[METHOD_PULL_FLUID]);
    }

    // =========================================================================
    // tanks()
    // =========================================================================

    @Test
    @SuppressWarnings("unchecked")
    void tanksEmptyHandlerReturnsCapacityOnly() throws LuaException, InterruptedException {
        GenericFluidPeripheral p = new GenericFluidPeripheral(new EmptyTank(1000));

        Object[] result = p.callMethod(NULL_COMPUTER, SYNC_CONTEXT, METHOD_TANKS, new Object[0]);

        assertNotNull(result);
        Map<Object, Object> table = (Map<Object, Object>) result[0];
        assertEquals(1, table.size(), "Expected exactly one tank entry");
        Map<Object, Object> tank1 = (Map<Object, Object>) table.get(1);
        assertEquals(1000.0, tank1.get("capacity"));
        assertNull(tank1.get("name"), "Empty tank must not have 'name'");
        assertNull(tank1.get("amount"), "Empty tank must not have 'amount'");
    }

    @Test
    @SuppressWarnings("unchecked")
    void tanksNullTankInfoArrayReturnsEmptyTable() throws LuaException, InterruptedException {
        // Handler that returns null from getTankInfo — peripheral must not throw.
        IFluidHandler nullTankHandler = new IFluidHandler() {

            @Override
            public FluidTankInfo[] getTankInfo(ForgeDirection from) {
                return null;
            }

            @Override
            public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
                return 0;
            }

            @Override
            public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
                return null;
            }

            @Override
            public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
                return null;
            }

            @Override
            public boolean canFill(ForgeDirection from, Fluid fluid) {
                return false;
            }

            @Override
            public boolean canDrain(ForgeDirection from, Fluid fluid) {
                return false;
            }
        };
        GenericFluidPeripheral p = new GenericFluidPeripheral(nullTankHandler, ForgeDirection.UNKNOWN);

        Object[] result = p.callMethod(NULL_COMPUTER, SYNC_CONTEXT, METHOD_TANKS, new Object[0]);

        assertNotNull(result);
        Map<Object, Object> table = (Map<Object, Object>) result[0];
        assertTrue(table.isEmpty(), "Null getTankInfo must return an empty table");
    }

    // =========================================================================
    // pushFluid — argument validation
    // =========================================================================

    @Test
    void pushFluidMissingNameArgThrowsLuaException() {
        GenericFluidPeripheral p = new GenericFluidPeripheral(new EmptyTank(1000));
        assertThrows(
            LuaException.class,
            () -> p.callMethod(NULL_COMPUTER, SYNC_CONTEXT, METHOD_PUSH_FLUID, new Object[0]));
    }

    @Test
    void pushFluidNonStringNameArgThrowsLuaException() {
        GenericFluidPeripheral p = new GenericFluidPeripheral(new EmptyTank(1000));
        assertThrows(
            LuaException.class,
            () -> p.callMethod(NULL_COMPUTER, SYNC_CONTEXT, METHOD_PUSH_FLUID, new Object[] { 42.0 }));
    }

    @Test
    void pushFluidZeroLimitThrowsLuaException() {
        GenericFluidPeripheral p = new GenericFluidPeripheral(new EmptyTank(1000));
        assertThrows(
            LuaException.class,
            () -> p.callMethod(NULL_COMPUTER, SYNC_CONTEXT, METHOD_PUSH_FLUID, new Object[] { "dest", 0.0 }));
    }

    @Test
    void pushFluidNegativeLimitThrowsLuaException() {
        GenericFluidPeripheral p = new GenericFluidPeripheral(new EmptyTank(1000));
        assertThrows(
            LuaException.class,
            () -> p.callMethod(NULL_COMPUTER, SYNC_CONTEXT, METHOD_PUSH_FLUID, new Object[] { "dest", -100.0 }));
    }

    // =========================================================================
    // pushFluid — error paths (unknown target, non-fluid-storage target)
    // =========================================================================

    @Test
    void pushFluidUnknownTargetThrowsDoesNotExist() {
        GenericFluidPeripheral p = new GenericFluidPeripheral(new EmptyTank(1000));
        LuaException ex = assertThrows(
            LuaException.class,
            () -> p.callMethod(NULL_COMPUTER, SYNC_CONTEXT, METHOD_PUSH_FLUID, new Object[] { "nonexistent" }));
        assertTrue(
            ex.getMessage()
                .contains("nonexistent"));
        assertTrue(
            ex.getMessage()
                .contains("does not exist"));
    }

    @Test
    void pushFluidNonFluidStorageTargetThrowsNotFluidStorage() {
        GenericFluidPeripheral p = new GenericFluidPeripheral(new EmptyTank(1000));
        IPeripheral notAFluidStorage = stubPeripheral("drive");
        IComputerAccess computer = computerWith("drive_0", notAFluidStorage);

        LuaException ex = assertThrows(
            LuaException.class,
            () -> p.callMethod(computer, SYNC_CONTEXT, METHOD_PUSH_FLUID, new Object[] { "drive_0" }));
        assertTrue(
            ex.getMessage()
                .contains("drive_0"));
        assertTrue(
            ex.getMessage()
                .contains("is not a fluid storage"));
    }

    // =========================================================================
    // pullFluid — argument validation
    // =========================================================================

    @Test
    void pullFluidMissingNameArgThrowsLuaException() {
        GenericFluidPeripheral p = new GenericFluidPeripheral(new EmptyTank(1000));
        assertThrows(
            LuaException.class,
            () -> p.callMethod(NULL_COMPUTER, SYNC_CONTEXT, METHOD_PULL_FLUID, new Object[0]));
    }

    @Test
    void pullFluidNonStringNameArgThrowsLuaException() {
        GenericFluidPeripheral p = new GenericFluidPeripheral(new EmptyTank(1000));
        assertThrows(
            LuaException.class,
            () -> p.callMethod(NULL_COMPUTER, SYNC_CONTEXT, METHOD_PULL_FLUID, new Object[] { 42.0 }));
    }

    // =========================================================================
    // pullFluid — error paths (unknown source, non-fluid-storage source)
    // =========================================================================

    @Test
    void pullFluidUnknownSourceThrowsDoesNotExist() {
        GenericFluidPeripheral p = new GenericFluidPeripheral(new EmptyTank(1000));
        LuaException ex = assertThrows(
            LuaException.class,
            () -> p.callMethod(NULL_COMPUTER, SYNC_CONTEXT, METHOD_PULL_FLUID, new Object[] { "nonexistent" }));
        assertTrue(
            ex.getMessage()
                .contains("nonexistent"));
        assertTrue(
            ex.getMessage()
                .contains("does not exist"));
    }

    @Test
    void pullFluidNonFluidStorageSourceThrowsNotFluidStorage() {
        GenericFluidPeripheral p = new GenericFluidPeripheral(new EmptyTank(1000));
        IPeripheral notAFluidStorage = stubPeripheral("monitor");
        IComputerAccess computer = computerWith("monitor_0", notAFluidStorage);

        LuaException ex = assertThrows(
            LuaException.class,
            () -> p.callMethod(computer, SYNC_CONTEXT, METHOD_PULL_FLUID, new Object[] { "monitor_0" }));
        assertTrue(
            ex.getMessage()
                .contains("monitor_0"));
        assertTrue(
            ex.getMessage()
                .contains("is not a fluid storage"));
    }

    // =========================================================================
    // End-to-end smoke tests — empty tanks, proves main-thread dispatch wiring
    // =========================================================================

    @Test
    void pushFluidEmptySourceReturnsZero() throws LuaException, InterruptedException {
        // Both source and destination are empty. FluidUtil.moveFluid() drains nothing and
        // returns 0 immediately without constructing any FluidStack.
        GenericFluidPeripheral source = new GenericFluidPeripheral(new EmptyTank(1000));
        GenericFluidPeripheral dest = new GenericFluidPeripheral(new EmptyTank(1000));
        IComputerAccess computer = computerWith("dest", dest);

        Object[] result = source.callMethod(computer, SYNC_CONTEXT, METHOD_PUSH_FLUID, new Object[] { "dest" });

        assertNotNull(result);
        assertEquals(0.0, result[0]);
    }

    @Test
    void pullFluidEmptySourceReturnsZero() throws LuaException, InterruptedException {
        GenericFluidPeripheral source = new GenericFluidPeripheral(new EmptyTank(1000));
        GenericFluidPeripheral dest = new GenericFluidPeripheral(new EmptyTank(1000));
        IComputerAccess computer = computerWith("source", source);

        Object[] result = dest.callMethod(computer, SYNC_CONTEXT, METHOD_PULL_FLUID, new Object[] { "source" });

        assertNotNull(result);
        assertEquals(0.0, result[0]);
    }

    @Test
    void pushFluidEmptySourceWithLimitReturnsZero() throws LuaException, InterruptedException {
        GenericFluidPeripheral source = new GenericFluidPeripheral(new EmptyTank(1000));
        GenericFluidPeripheral dest = new GenericFluidPeripheral(new EmptyTank(1000));
        IComputerAccess computer = computerWith("dest", dest);

        Object[] result = source.callMethod(computer, SYNC_CONTEXT, METHOD_PUSH_FLUID, new Object[] { "dest", 500.0 });

        assertNotNull(result);
        assertEquals(0.0, result[0]);
    }

    // =========================================================================
    // equals()
    // =========================================================================

    @Test
    void equalsReturnsTrueForSameHandler() {
        EmptyTank handler = new EmptyTank(1000);
        GenericFluidPeripheral a = new GenericFluidPeripheral(handler, ForgeDirection.UNKNOWN);
        GenericFluidPeripheral b = new GenericFluidPeripheral(handler, ForgeDirection.UNKNOWN);
        assertTrue(a.equals(b));
    }

    @Test
    void equalsReturnsFalseForDifferentHandler() {
        GenericFluidPeripheral a = new GenericFluidPeripheral(new EmptyTank(1000));
        GenericFluidPeripheral b = new GenericFluidPeripheral(new EmptyTank(1000));
        assertFalse(a.equals(b));
    }

    @Test
    void equalsReturnsFalseForNonFluidStoragePeripheral() {
        GenericFluidPeripheral a = new GenericFluidPeripheral(new EmptyTank(1000));
        assertFalse(a.equals(stubPeripheral("drive")));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Creates a minimal no-op {@link IPeripheral} with the given type string. */
    private static IPeripheral stubPeripheral(final String type) {
        return new IPeripheral() {

            @Override
            public String getType() {
                return type;
            }

            @Override
            public String[] getMethodNames() {
                return new String[0];
            }

            @Override
            public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments) {
                return null;
            }

            @Override
            public void attach(IComputerAccess computer) {}

            @Override
            public void detach(IComputerAccess computer) {}

            @Override
            public boolean equals(IPeripheral other) {
                return false;
            }
        };
    }
}
