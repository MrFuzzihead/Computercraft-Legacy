package dan200.computercraft.core.lua.lib;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;

/** Q8: covers the delayed-task queue {@link DelayedTasks} (scheduling, events, cancellation, list integrity). */
class DelayedTasksTest {

    private IComputerAccess access;

    @BeforeEach
    void setUp() {
        DelayedTasks.reset();
        access = mock(IComputerAccess.class);
    }

    @AfterEach
    void tearDown() {
        DelayedTasks.reset();
    }

    private static ILuaTask task(Object result) throws LuaException {
        ILuaTask mock = mock(ILuaTask.class);
        when(mock.execute()).thenReturn(result == null ? null : new Object[] { result });
        return mock;
    }

    private List<Object[]> events() {
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(access, atLeast(0)).queueEvent(any(String.class), captor.capture());
        return captor.getAllValues();
    }

    private void add(ILuaTask task, int delay, long id) {
        assertTrue(DelayedTasks.addTask(access, task, delay, id));
    }

    @Test
    void zeroDelayTaskExecutesOnFirstUpdateWithSuccessEvent() throws Exception {
        add(task("done"), 0, 1);
        DelayedTasks.update();
        List<Object[]> events = events();
        assertEquals(1, events.size());
        assertArrayEquals(new Object[] { 1L, true, "done" }, events.get(0));
    }

    @Test
    void delayedTaskOnlyExecutesAfterCountingDown() throws Exception {
        ILuaTask inner = task(null);
        add(inner, 2, 7);
        DelayedTasks.update();
        DelayedTasks.update();
        assertEquals(0, events().size());
        verify(inner, never()).execute();
        DelayedTasks.update();
        verify(inner).execute();
        assertArrayEquals(new Object[] { 7L, true }, events().get(0));
        DelayedTasks.update();
        assertEquals(1, events().size());
    }

    @Test
    void luaExceptionQueuesFailureEvent() throws Exception {
        ILuaTask failing = mock(ILuaTask.class);
        when(failing.execute()).thenThrow(new LuaException("boom"));
        add(failing, 0, 3);
        DelayedTasks.update();
        assertArrayEquals(new Object[] { 3L, false, "boom" }, events().get(0));
    }

    @Test
    void javaExceptionQueuesFailureEvent() throws Exception {
        ILuaTask failing = mock(ILuaTask.class);
        when(failing.execute()).thenThrow(new IllegalStateException("kaboom"));
        add(failing, 0, 4);
        DelayedTasks.update();
        Object[] event = events().get(0);
        assertEquals(4L, event[0]);
        assertEquals(false, event[1]);
        assertTrue(((String) event[2]).contains("kaboom"));
    }

    @Test
    void canceledTaskNeverExecutes() throws Exception {
        ILuaTask inner = task(null);
        add(inner, 0, 9);
        DelayedTasks.cancel(9);
        DelayedTasks.update();
        verify(inner, never()).execute();
        assertEquals(0, events().size());
    }

    @Test
    void appendAfterFinishingTailTaskIsStillReachable() throws Exception {
        // A long-delay task stays pending; the short-delay task is currently the tail.
        ILuaTask longTask = task(null);
        ILuaTask tailTask = task(null);
        add(longTask, 5, 1);
        add(tailTask, 0, 2);

        DelayedTasks.update(); // Runs + removes the tail task.
        verify(tailTask).execute();

        // A task added after the tail was removed must still be scheduled.
        ILuaTask added = task(null);
        add(added, 0, 3);
        DelayedTasks.update();
        DelayedTasks.update();
        verify(added).execute();
        List<Object[]> events = events();
        assertArrayEquals(new Object[] { 3L, true }, events.get(events.size() - 1));
    }

    @Test
    void cancelingTailTaskKeepsLaterAppendsReachable() throws Exception {
        ILuaTask longTask = task(null);
        ILuaTask tail = task(null);
        add(longTask, 5, 1);
        add(tail, 0, 2);
        DelayedTasks.cancel(2); // Removes the tail via cancel().

        ILuaTask added = task(null);
        add(added, 0, 3);
        DelayedTasks.update();
        DelayedTasks.update();
        verify(added).execute();
    }

    @Test
    void resetClearsPendingTasks() throws Exception {
        ILuaTask inner = task(null);
        add(inner, 0, 1);
        DelayedTasks.reset();
        DelayedTasks.update();
        verify(inner, never()).execute();
        assertEquals(0, events().size());
    }

    @Test
    void addTaskValidatesArguments() {
        assertThrows(NullPointerException.class, () -> DelayedTasks.addTask(null, mock(ILuaTask.class), 0, 1));
        assertThrows(NullPointerException.class, () -> DelayedTasks.addTask(access, null, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> DelayedTasks.addTask(access, mock(ILuaTask.class), -1, 1));
    }
}
