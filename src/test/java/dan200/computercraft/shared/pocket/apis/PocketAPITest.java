package dan200.computercraft.shared.pocket.apis;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;

/**
 * Unit tests for {@link PocketAPI}.
 *
 * <h2>Coverage</h2>
 * <ul>
 * <li>API surface ({@link PocketAPI#getNames()},
 * {@link PocketAPI#getMethodNames()}).</li>
 * <li>Lifecycle no-ops ({@code startup}, {@code advance}, {@code shutdown}).</li>
 * <li>{@code isEquipped()} when no item stack has been set — the fast-path
 * null-stack branch that requires no Minecraft class.</li>
 * <li>The <em>null-player guard</em> at the top of {@code equipBack()} and
 * {@code unequipBack()}: both methods must return {@code false} plus an
 * error string when neither a carrier player nor an item stack is
 * available.</li>
 * </ul>
 *
 * <h2>Out-of-scope for unit tests</h2>
 * <p>
 * The success paths of {@code equipBack()} and {@code unequipBack()} —
 * which search/modify an {@link net.minecraft.entity.player.InventoryPlayer},
 * read/write {@link net.minecraft.nbt.NBTTagCompound} on an
 * {@link net.minecraft.item.ItemStack}, and call
 * {@link dan200.computercraft.shared.computer.core.ServerComputer#setPeripheral}
 * — require Minecraft's item registry and a live {@code ServerComputer}. Those
 * paths are covered by the in-game script {@code testPocketAPI}.
 *
 * <h2>Threading note</h2>
 * <p>
 * {@code equipBack} and {@code unequipBack} use
 * {@link ILuaContext#executeMainThreadTask}. The {@link #SYNC_CONTEXT} stub
 * executes tasks synchronously on the calling thread, which is safe here
 * because the null-player guard fires before any Minecraft or
 * {@code ServerComputer} interaction.
 */
class PocketAPITest {

    /** Method indices — must match the order in {@link PocketAPI#getMethodNames()}. */
    private static final int METHOD_EQUIP_BACK = 0;
    private static final int METHOD_UNEQUIP_BACK = 1;
    private static final int METHOD_IS_EQUIPPED = 2;

    /**
     * A minimal {@link ILuaContext} that runs {@link ILuaTask} callbacks
     * synchronously so tests do not need a real Minecraft main thread.
     */
    private static final ILuaContext SYNC_CONTEXT = new ILuaContext() {

        @Override
        public Object[] executeMainThreadTask(ILuaTask task) throws LuaException, InterruptedException {
            return task.execute();
        }

        @Override
        public long issueMainThreadTask(ILuaTask task) throws LuaException {
            return 0;
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

    private PocketAPI api;

    @BeforeEach
    void setUp() {
        // null ServerComputer is safe: the null-player guard fires before
        // setPeripheral() is ever reached on the paths tested here.
        api = new PocketAPI(null);
        // m_player, m_stack, and m_inventory remain null (update() not called).
    }

    // =========================================================================
    // API surface
    // =========================================================================

    @Test
    void getNamesPocketExposed() {
        String[] names = api.getNames();

        assertNotNull(names);
        assertEquals(1, names.length);
        assertEquals("pocket", names[0]);
    }

    @Test
    void getMethodNamesHasThreeMethods() {
        String[] methods = api.getMethodNames();

        assertNotNull(methods);
        assertEquals(3, methods.length);
    }

    @Test
    void getMethodNamesContainsEquipBack() {
        assertEquals("equipBack", api.getMethodNames()[METHOD_EQUIP_BACK]);
    }

    @Test
    void getMethodNamesContainsUnequipBack() {
        assertEquals("unequipBack", api.getMethodNames()[METHOD_UNEQUIP_BACK]);
    }

    @Test
    void getMethodNamesContainsIsEquipped() {
        assertEquals("isEquipped", api.getMethodNames()[METHOD_IS_EQUIPPED]);
    }

    // =========================================================================
    // Lifecycle no-ops
    // =========================================================================

    @Test
    void lifecycleMethodsDoNotThrow() {
        assertDoesNotThrow(() -> {
            api.startup();
            api.advance(0.05);
            api.shutdown();
        });
    }

    // =========================================================================
    // isEquipped — null stack
    // =========================================================================

    @Test
    void isEquippedReturnsFalseWhenNoStackSet() throws LuaException, InterruptedException {
        // m_stack is null from setUp() — no ItemStack needed.
        Object[] result = api.callMethod(null, METHOD_IS_EQUIPPED, new Object[0]);

        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals(Boolean.FALSE, result[0]);
    }

    @Test
    void isEquippedReturnsFalseAfterExplicitUpdateWithNullStack() throws LuaException, InterruptedException {
        api.update(null, null, null);

        Object[] result = api.callMethod(null, METHOD_IS_EQUIPPED, new Object[0]);

        assertEquals(Boolean.FALSE, result[0]);
    }

    // =========================================================================
    // equipBack — null-player guard (failure path)
    // =========================================================================

    @Test
    void equipBackReturnsTwoValueResultWhenNoPlayer() throws LuaException, InterruptedException {
        Object[] result = api.callMethod(SYNC_CONTEXT, METHOD_EQUIP_BACK, new Object[0]);

        assertNotNull(result);
        assertEquals(2, result.length, "equipBack() should return [false, errorMessage] when no player is set");
    }

    @Test
    void equipBackFirstValueIsFalseWhenNoPlayer() throws LuaException, InterruptedException {
        Object[] result = api.callMethod(SYNC_CONTEXT, METHOD_EQUIP_BACK, new Object[0]);

        assertEquals(Boolean.FALSE, result[0]);
    }

    @Test
    void equipBackSecondValueIsErrorStringWhenNoPlayer() throws LuaException, InterruptedException {
        Object[] result = api.callMethod(SYNC_CONTEXT, METHOD_EQUIP_BACK, new Object[0]);

        assertInstanceOf(String.class, result[1], "second return value of equipBack() should be an error string");
        assertFalse(((String) result[1]).isEmpty(), "error string from equipBack() must not be empty");
    }

    @Test
    void equipBackReturnsFalseAfterUpdateWithNullPlayer() throws LuaException, InterruptedException {
        api.update(null, null, null);

        Object[] result = api.callMethod(SYNC_CONTEXT, METHOD_EQUIP_BACK, new Object[0]);

        assertEquals(Boolean.FALSE, result[0]);
    }

    // =========================================================================
    // unequipBack — null-player guard (failure path)
    // =========================================================================

    @Test
    void unequipBackReturnsTwoValueResultWhenNoPlayer() throws LuaException, InterruptedException {
        Object[] result = api.callMethod(SYNC_CONTEXT, METHOD_UNEQUIP_BACK, new Object[0]);

        assertNotNull(result);
        assertEquals(2, result.length, "unequipBack() should return [false, errorMessage] when no player is set");
    }

    @Test
    void unequipBackFirstValueIsFalseWhenNoPlayer() throws LuaException, InterruptedException {
        Object[] result = api.callMethod(SYNC_CONTEXT, METHOD_UNEQUIP_BACK, new Object[0]);

        assertEquals(Boolean.FALSE, result[0]);
    }

    @Test
    void unequipBackSecondValueIsErrorStringWhenNoPlayer() throws LuaException, InterruptedException {
        Object[] result = api.callMethod(SYNC_CONTEXT, METHOD_UNEQUIP_BACK, new Object[0]);

        assertInstanceOf(String.class, result[1], "second return value of unequipBack() should be an error string");
        assertFalse(((String) result[1]).isEmpty(), "error string from unequipBack() must not be empty");
    }

    @Test
    void unequipBackReturnsFalseAfterUpdateWithNullPlayer() throws LuaException, InterruptedException {
        api.update(null, null, null);

        Object[] result = api.callMethod(SYNC_CONTEXT, METHOD_UNEQUIP_BACK, new Object[0]);

        assertEquals(Boolean.FALSE, result[0]);
    }
}
