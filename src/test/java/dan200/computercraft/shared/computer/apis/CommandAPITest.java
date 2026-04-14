package dan200.computercraft.shared.computer.apis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;

/**
 * Unit tests for {@link CommandAPI}.
 *
 * <h2>Coverage</h2>
 * <ul>
 * <li>API surface ({@link CommandAPI#getNames()},
 * {@link CommandAPI#getMethodNames()}).</li>
 * <li>Lifecycle no-ops ({@code startup}, {@code advance}, {@code shutdown}).</li>
 * <li>Argument-validation error paths for all methods that validate before
 * dispatching to the main thread (no Minecraft server required).</li>
 * <li>{@code getBlockInfos} volume cap ({@code > 4096 → LuaException}).</li>
 * <li>{@code exec} / {@code list} null-server fallback paths via
 * {@link #SYNC_CONTEXT} (executes main-thread tasks synchronously).</li>
 * <li>{@code getBlockInfos} with dimension arg and no server →
 * {@code "No server available"} error.</li>
 * </ul>
 *
 * <h2>Out-of-scope for unit tests</h2>
 * <p>
 * Any path that requires a live {@link net.minecraft.server.MinecraftServer},
 * a real {@link dan200.computercraft.shared.computer.blocks.TileCommandComputer},
 * or a loaded Minecraft world. Those are covered by the in-game manual tests.
 */
class CommandAPITest {

    /** Method indices — must match the order in {@link CommandAPI#getMethodNames()}. */
    private static final int METHOD_EXEC = 0;
    private static final int METHOD_EXEC_ASYNC = 1;
    private static final int METHOD_LIST = 2;
    private static final int METHOD_GET_BLOCK_POSITION = 3;
    private static final int METHOD_GET_BLOCK_INFO = 4;
    private static final int METHOD_GET_BLOCK_INFOS = 5;

    /**
     * A minimal {@link ILuaContext} that runs {@link ILuaTask} callbacks
     * synchronously on the calling thread so tests do not need a real Minecraft
     * main thread.
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

    /** A null TileCommandComputer is safe for all paths that do not touch the Minecraft world. */
    private CommandAPI api;

    @BeforeEach
    void setUp() {
        api = new CommandAPI(null);
    }

    // =========================================================================
    // API surface
    // =========================================================================

    @Test
    void getNamesReturnsCommands() {
        String[] names = api.getNames();

        assertNotNull(names);
        assertEquals(1, names.length);
        assertEquals("commands", names[0]);
    }

    @Test
    void getMethodNamesHasSixMethods() {
        assertEquals(6, api.getMethodNames().length);
    }

    @Test
    void getMethodNamesContainsExec() {
        assertEquals("exec", api.getMethodNames()[METHOD_EXEC]);
    }

    @Test
    void getMethodNamesContainsExecAsync() {
        assertEquals("execAsync", api.getMethodNames()[METHOD_EXEC_ASYNC]);
    }

    @Test
    void getMethodNamesContainsList() {
        assertEquals("list", api.getMethodNames()[METHOD_LIST]);
    }

    @Test
    void getMethodNamesContainsGetBlockPosition() {
        assertEquals("getBlockPosition", api.getMethodNames()[METHOD_GET_BLOCK_POSITION]);
    }

    @Test
    void getMethodNamesContainsGetBlockInfo() {
        assertEquals("getBlockInfo", api.getMethodNames()[METHOD_GET_BLOCK_INFO]);
    }

    @Test
    void getMethodNamesContainsGetBlockInfos() {
        assertEquals("getBlockInfos", api.getMethodNames()[METHOD_GET_BLOCK_INFOS]);
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
    // exec — argument validation
    // =========================================================================

    @Test
    void execWithNoArgThrowsLuaException() {
        assertThrows(LuaException.class, () -> api.callMethod(SYNC_CONTEXT, METHOD_EXEC, new Object[0]));
    }

    @Test
    void execWithNumberArgThrowsLuaException() {
        assertThrows(LuaException.class, () -> api.callMethod(SYNC_CONTEXT, METHOD_EXEC, new Object[] { 42.0 }));
    }

    // =========================================================================
    // exec — null server fallback (failure path)
    // =========================================================================

    @Test
    void execNullServerReturnsFalse() throws LuaException, InterruptedException {
        // MinecraftServer.getServer() returns null outside a running server.
        Object[] result = api.callMethod(SYNC_CONTEXT, METHOD_EXEC, new Object[] { "say hi" });

        assertNotNull(result);
        assertEquals(Boolean.FALSE, result[0]);
    }

    @Test
    void execNullServerReturnsOutputTable() throws LuaException, InterruptedException {
        Object[] result = api.callMethod(SYNC_CONTEXT, METHOD_EXEC, new Object[] { "say hi" });

        assertNotNull(result);
        assertInstanceOf(Map.class, result[1]);
    }

    @Test
    void execNullServerReturnsThreeValues() throws LuaException, InterruptedException {
        Object[] result = api.callMethod(SYNC_CONTEXT, METHOD_EXEC, new Object[] { "say hi" });

        assertNotNull(result);
        assertEquals(3, result.length, "exec() must return (boolean, table, number)");
    }

    @Test
    void execNullServerReturnsZeroAffectedCount() throws LuaException, InterruptedException {
        Object[] result = api.callMethod(SYNC_CONTEXT, METHOD_EXEC, new Object[] { "say hi" });

        assertNotNull(result);
        assertEquals(0, ((Number) result[2]).intValue());
    }

    // =========================================================================
    // execAsync — argument validation
    // =========================================================================

    @Test
    void execAsyncWithNoArgThrowsLuaException() {
        assertThrows(LuaException.class, () -> api.callMethod(SYNC_CONTEXT, METHOD_EXEC_ASYNC, new Object[0]));
    }

    @Test
    void execAsyncWithNumberArgThrowsLuaException() {
        assertThrows(LuaException.class, () -> api.callMethod(SYNC_CONTEXT, METHOD_EXEC_ASYNC, new Object[] { 99.0 }));
    }

    // =========================================================================
    // list — null server returns empty table
    // =========================================================================

    @Test
    void listNullServerReturnsEmptyTable() throws LuaException, InterruptedException {
        Object[] result = api.callMethod(SYNC_CONTEXT, METHOD_LIST, new Object[0]);

        assertNotNull(result);
        assertEquals(1, result.length);
        Map<?, ?> table = assertInstanceOf(Map.class, result[0]);
        assertTrue(table.isEmpty(), "list() should return an empty table when no server is running");
    }

    @Test
    void listWithPrefixNullServerReturnsEmptyTable() throws LuaException, InterruptedException {
        Object[] result = api.callMethod(SYNC_CONTEXT, METHOD_LIST, new Object[] { "say" });

        assertNotNull(result);
        Map<?, ?> table = assertInstanceOf(Map.class, result[0]);
        assertTrue(table.isEmpty());
    }

    // =========================================================================
    // getBlockInfo — argument validation
    // =========================================================================

    @Test
    void getBlockInfoWithNoArgsThrowsLuaException() {
        assertThrows(LuaException.class, () -> api.callMethod(SYNC_CONTEXT, METHOD_GET_BLOCK_INFO, new Object[0]));
    }

    @Test
    void getBlockInfoWithTwoArgsThrowsLuaException() {
        assertThrows(
            LuaException.class,
            () -> api.callMethod(SYNC_CONTEXT, METHOD_GET_BLOCK_INFO, new Object[] { 0.0, 64.0 }));
    }

    @Test
    void getBlockInfoWithStringArgThrowsLuaException() {
        assertThrows(
            LuaException.class,
            () -> api.callMethod(SYNC_CONTEXT, METHOD_GET_BLOCK_INFO, new Object[] { "x", 64.0, 0.0 }));
    }

    // =========================================================================
    // getBlockInfos — argument validation
    // =========================================================================

    @Test
    void getBlockInfosWithNoArgsThrowsLuaException() {
        assertThrows(LuaException.class, () -> api.callMethod(SYNC_CONTEXT, METHOD_GET_BLOCK_INFOS, new Object[0]));
    }

    @Test
    void getBlockInfosWithFiveArgsThrowsLuaException() {
        assertThrows(
            LuaException.class,
            () -> api.callMethod(SYNC_CONTEXT, METHOD_GET_BLOCK_INFOS, new Object[] { 0.0, 0.0, 0.0, 1.0, 1.0 }));
    }

    @Test
    void getBlockInfosWithStringArgThrowsLuaException() {
        assertThrows(
            LuaException.class,
            () -> api.callMethod(SYNC_CONTEXT, METHOD_GET_BLOCK_INFOS, new Object[] { "x", 0.0, 0.0, 1.0, 1.0, 1.0 }));
    }

    // =========================================================================
    // getBlockInfos — volume cap
    // =========================================================================

    @Test
    void getBlockInfosWithExactly4096BlocksDoesNotHitVolumeCap() {
        // 16 × 16 × 16 = 4096 — must NOT throw "Too many blocks".
        // Pass dimension=0 so the task hits "No server available" rather than NPE on null computer.
        LuaException ex = assertThrows(
            LuaException.class,
            () -> api.callMethod(
                SYNC_CONTEXT,
                METHOD_GET_BLOCK_INFOS,
                new Object[] { 0.0, 0.0, 0.0, 15.0, 15.0, 15.0, 0.0 }));
        assertNotEquals("Too many blocks", ex.getMessage());
    }

    @Test
    void getBlockInfosExceedingVolumeThrowsTooManyBlocks() {
        // 17 × 16 × 16 = 4352 > 4096
        LuaException ex = assertThrows(
            LuaException.class,
            () -> api
                .callMethod(SYNC_CONTEXT, METHOD_GET_BLOCK_INFOS, new Object[] { 0.0, 0.0, 0.0, 16.0, 15.0, 15.0 }));
        assertEquals("Too many blocks", ex.getMessage());
    }

    @Test
    void getBlockInfosExceedingVolumeByOneThrowsTooManyBlocks() {
        // 1 × 4097 × 1 = 4097 > 4096
        LuaException ex = assertThrows(
            LuaException.class,
            () -> api
                .callMethod(SYNC_CONTEXT, METHOD_GET_BLOCK_INFOS, new Object[] { 0.0, 0.0, 0.0, 0.0, 4096.0, 0.0 }));
        assertEquals("Too many blocks", ex.getMessage());
    }

    @Test
    void getBlockInfosWithReversedCoordsNormalizesCorrectly() {
        // Max before min (5,5,5 → 0,0,0) → volume = 1, well under cap.
        // Pass dimension=0 so the task hits "No server available" rather than NPE on null computer.
        LuaException ex = assertThrows(
            LuaException.class,
            () -> api
                .callMethod(SYNC_CONTEXT, METHOD_GET_BLOCK_INFOS, new Object[] { 5.0, 5.0, 5.0, 0.0, 0.0, 0.0, 0.0 }));
        assertNotEquals("Too many blocks", ex.getMessage());
    }

    // =========================================================================
    // getBlockInfos — dimension arg + no server
    // =========================================================================

    @Test
    void getBlockInfosWithDimensionArgAndNoServerThrowsLuaException() throws LuaException, InterruptedException {
        // Volume = 1, but hasDim = true → server lookup → NPE guard throws LuaException
        LuaException ex = assertThrows(
            LuaException.class,
            () -> api.callMethod(
                SYNC_CONTEXT,
                METHOD_GET_BLOCK_INFOS,
                new Object[] { 0.0, 64.0, 0.0, 0.0, 64.0, 0.0, 0.0 }));
        assertEquals("No server available", ex.getMessage());
    }
}
