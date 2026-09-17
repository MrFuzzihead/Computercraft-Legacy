package dan200.computercraft.core.lua.lib;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.core.apis.IAPIEnvironment;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.filesystem.FileSystemException;

class LuaEnvironmentMountTest {

    private FileSystem fileSystem;
    private IComputerAccess access;
    private IWritableMount mount;
    private final Logger logger = (Logger) ComputerCraft.logger;
    private Appender appender;
    private Level oldLevel;
    private final List<String> messages = new ArrayList<>();
    private final List<Throwable> exceptions = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        fileSystem = mock(FileSystem.class);
        mount = mock(IWritableMount.class);
        IAPIEnvironment environment = mock(IAPIEnvironment.class);
        when(environment.getFileSystem()).thenReturn(fileSystem);
        when(environment.getComputerID()).thenReturn(42);
        Computer computer = mock(Computer.class);
        when(computer.getAPIEnvironment()).thenReturn(environment);
        // Avoid registering a factory in the global LuaEnvironment singleton.
        Class<?> wrapper = Class.forName(LuaEnvironment.class.getName() + "$ComputerAccess");
        Constructor<?> constructor = wrapper.getDeclaredConstructor(Computer.class);
        constructor.setAccessible(true);
        access = (IComputerAccess) constructor.newInstance(computer);

        oldLevel = logger.getLevel();
        appender = mock(Appender.class);
        when(appender.getName()).thenReturn("lua-environment-mount-test");
        when(appender.isStarted()).thenReturn(true);
        doAnswer(invocation -> {
            LogEvent event = invocation.getArgument(0);
            messages.add(event.getMessage().getFormattedMessage());
            exceptions.add(event.getThrown());
            assertEquals(Level.WARN, event.getLevel());
            return null;
        }).when(appender).append(any(LogEvent.class));
        logger.addAppender(appender);
        logger.setLevel(Level.ALL);
    }

    @AfterEach
    void tearDown() {
        if (appender != null) {
            logger.removeAppender(appender);
            logger.setLevel(oldLevel);
        }
    }

    private static FileSystemException failure() throws Exception {
        Constructor<FileSystemException> constructor = FileSystemException.class.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        return constructor.newInstance("Test mount failure");
    }

    private String mount(boolean writable) {
        return writable ? access.mountWritable("disk", mount, "drive") : access.mount("disk", mount, "drive");
    }

    private void assertDiagnostic(Throwable failure) {
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("42"));
        assertTrue(messages.get(0).contains("disk"));
        assertSame(failure, exceptions.get(0));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void failedMountDoesNotClaimOwnershipAndCanBeRetried(boolean writable) throws Exception {
        FileSystemException failure = failure();
        if (writable) doThrow(failure).doNothing().when(fileSystem).mountWritable("drive", "disk", mount);
        else doThrow(failure).doNothing().when(fileSystem).mount("drive", "disk", mount);

        assertNull(mount(writable));
        assertDiagnostic(failure);
        assertThrows(RuntimeException.class, () -> access.unmount("disk"));
        verify(fileSystem, never()).unmount(anyString());
        assertEquals("disk", mount(writable));
        access.unmount("disk");
        verify(fileSystem).unmount("disk");
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void lookupFailureIsLoggedWithoutAttemptingMount(boolean writable) throws Exception {
        FileSystemException failure = failure();
        when(fileSystem.exists("disk")).thenThrow(failure);
        assertNull(mount(writable));
        assertDiagnostic(failure);
        verify(fileSystem, never()).mount(anyString(), anyString(), any(IMount.class));
        verify(fileSystem, never()).mountWritable(anyString(), anyString(), any(IWritableMount.class));
        assertThrows(RuntimeException.class, () -> access.unmount("disk"));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void occupiedLocationIsQuietAndNotOwned(boolean writable) throws Exception {
        when(fileSystem.exists("disk")).thenReturn(true);
        assertNull(mount(writable));
        assertTrue(messages.isEmpty());
        verify(fileSystem, never()).mount(anyString(), anyString(), any(IMount.class));
        verify(fileSystem, never()).mountWritable(anyString(), anyString(), any(IWritableMount.class));
        assertThrows(RuntimeException.class, () -> access.unmount("disk"));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void shortOverloadMountsSuccessfullyAndReleasesOwnership(boolean writable) throws Exception {
        assertEquals("disk", writable ? access.mountWritable("disk", mount) : access.mount("disk", mount));
        if (writable) verify(fileSystem).mountWritable("disk", "disk", mount);
        else verify(fileSystem).mount("disk", "disk", mount);
        assertTrue(messages.isEmpty());
        access.unmount("disk");
        assertThrows(RuntimeException.class, () -> access.unmount("disk"));
        verify(fileSystem, times(1)).unmount("disk");
    }
}
