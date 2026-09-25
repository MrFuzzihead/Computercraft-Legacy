package dan200.computercraft.core.apis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.filesystem.FileSystemException;

class PeripheralMountTest {

    private PeripheralAPI api;
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
        api = new PeripheralAPI(environment);
        api.startup();
        IPeripheral peripheral = mock(IPeripheral.class);
        when(peripheral.getType()).thenReturn("drive");
        when(peripheral.getMethodNames()).thenReturn(new String[0]);
        // Install a wrapper directly, avoiding the global async attach queue.
        Class<?> wrapper = Class.forName(PeripheralAPI.class.getName() + "$PeripheralWrapper");
        Constructor<?> constructor = wrapper
            .getDeclaredConstructor(PeripheralAPI.class, IPeripheral.class, String.class);
        constructor.setAccessible(true);
        access = (IComputerAccess) constructor.newInstance(api, peripheral, "left");
        Field attached = wrapper.getDeclaredField("m_attached");
        attached.setAccessible(true);
        attached.set(access, true);
        Field peripherals = PeripheralAPI.class.getDeclaredField("m_peripherals");
        peripherals.setAccessible(true);
        ((Object[]) peripherals.get(api))[4] = access;

        oldLevel = logger.getLevel();
        appender = mock(Appender.class);
        when(appender.getName()).thenReturn("peripheral-mount-test");
        when(appender.isStarted()).thenReturn(true);
        doAnswer(invocation -> {
            LogEvent event = invocation.getArgument(0);
            messages.add(
                event.getMessage()
                    .getFormattedMessage());
            exceptions.add(event.getThrown());
            assertEquals(Level.WARN, event.getLevel());
            return null;
        }).when(appender)
            .append(any(LogEvent.class));
        logger.addAppender(appender);
        logger.setLevel(Level.ALL);
    }

    @AfterEach
    void tearDown() {
        try {
            if (api != null) api.shutdown();
        } finally {
            if (appender != null) {
                logger.removeAppender(appender);
                logger.setLevel(oldLevel);
            }
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

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void failedMountReturnsNullLogsCauseAndDoesNotClaimOwnership(boolean writable) throws Exception {
        FileSystemException failure = failure();
        if (writable) {
            doThrow(failure).when(fileSystem)
                .mountWritable("drive", "disk", mount);
        } else {
            doThrow(failure).when(fileSystem)
                .mount("drive", "disk", mount);
        }
        assertNull(mount(writable));
        assertEquals(1, messages.size());
        assertTrue(
            messages.get(0)
                .contains("left"));
        assertTrue(
            messages.get(0)
                .contains("disk"));
        assertSame(failure, exceptions.get(0));
        assertThrows(RuntimeException.class, () -> access.unmount("disk"));
        api.shutdown();
        verify(fileSystem, never()).unmount(anyString());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void locationCheckFailureIsLoggedAndDoesNotAttemptMount(boolean writable) throws Exception {
        FileSystemException failure = failure();
        when(fileSystem.exists("disk")).thenThrow(failure);
        assertNull(mount(writable));
        assertEquals(1, messages.size());
        assertTrue(
            messages.get(0)
                .contains("disk"));
        assertSame(failure, exceptions.get(0));
        verify(fileSystem, never()).mount(anyString(), anyString(), any(IMount.class));
        verify(fileSystem, never()).mountWritable(anyString(), anyString(), any(IWritableMount.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void occupiedLocationRemainsAQuietNullResult(boolean writable) throws Exception {
        when(fileSystem.exists("disk")).thenReturn(true);
        assertNull(mount(writable));
        assertTrue(messages.isEmpty());
        verify(fileSystem, never()).mount(anyString(), anyString(), any(IMount.class));
        verify(fileSystem, never()).mountWritable(anyString(), anyString(), any(IWritableMount.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void successfulMountIsOwnedAndUnmountedOnDetach(boolean writable) throws Exception {
        assertEquals("disk", mount(writable));
        if (writable) verify(fileSystem).mountWritable("drive", "disk", mount);
        else verify(fileSystem).mount("drive", "disk", mount);
        assertTrue(messages.isEmpty());
        api.shutdown();
        verify(fileSystem).unmount("disk");
    }
}
