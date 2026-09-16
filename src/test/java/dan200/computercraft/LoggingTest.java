package dan200.computercraft;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.shared.network.PacketHandler;
import dan200.computercraft.shared.util.IDAssigner;

/** Q1: diagnostics reach Log4j with context and their original exception, without changing fallbacks. */
class LoggingTest {

    @TempDir
    Path temp;

    private final List<String> messages = new ArrayList<>();
    private final List<Throwable> exceptions = new ArrayList<>();
    private final List<Level> levels = new ArrayList<>();
    private Logger logger;
    private Appender appender;
    private Level oldLevel;
    private boolean oldAdditive;

    @BeforeEach
    void captureLogs() {
        attach((Logger) ComputerCraft.logger);
    }

    private void attach(Logger target) {
        logger = target;
        oldLevel = logger.getLevel();
        oldAdditive = logger.isAdditive();
        appender = mock(Appender.class);
        when(appender.getName()).thenReturn("q1-test-appender");
        when(appender.isStarted()).thenReturn(true);
        doAnswer(invocation -> {
            LogEvent event = invocation.getArgument(0);
            // Copy values while appending: Log4j may reuse its event objects.
            messages.add(event.getMessage().getFormattedMessage());
            exceptions.add(event.getThrown());
            levels.add(event.getLevel());
            return null;
        }).when(appender).append(any(LogEvent.class));
        logger.addAppender(appender);
        logger.setLevel(Level.ALL);
        logger.setAdditive(false);
    }

    @AfterEach
    void restoreLogger() {
        logger.removeAppender(appender);
        logger.setLevel(oldLevel);
        logger.setAdditive(oldAdditive);
    }

    @Test
    void invalidIdLogsPathAndExceptionAndStillReturnsZero() throws Exception {
        Path lastId = temp.resolve("lastid.txt");
        Files.write(lastId, "invalid".getBytes(StandardCharsets.UTF_8));

        assertEquals(0, IDAssigner.getNextIDFromFile(lastId.toFile()));
        assertError("invalid contents", lastId, NumberFormatException.class);
        assertEquals("invalid", new String(Files.readAllBytes(lastId), StandardCharsets.UTF_8));
    }

    @Test
    void unreadableIdLogsPathAndExceptionAndStillReturnsZero() throws Exception {
        // A directory cannot be opened as a file on either Windows or Unix.
        Path lastId = Files.createDirectory(temp.resolve("lastid.txt"));

        assertEquals(0, IDAssigner.getNextIDFromFile(lastId.toFile()));
        assertError("failed to read", lastId, IOException.class);
    }

    @Test
    void unwritableIdLogsOneErrorAndRetainsFallback() throws Exception {
        Path parentFile = temp.resolve("not-a-directory");
        Files.write(parentFile, new byte[] { 1 });
        Path lastId = parentFile.resolve("lastid.txt");

        assertEquals(0, IDAssigner.getNextIDFromFile(lastId.toFile()));
        assertError("failed to write", lastId, IOException.class);
    }

    @Test
    void successfulAllocationRemainsSequentialAndQuiet() throws Exception {
        Path directory = Files.createDirectory(temp.resolve("computers"));
        Files.createDirectory(directory.resolve("7"));
        Files.createDirectory(directory.resolve("not-an-id"));
        assertEquals(8, IDAssigner.getNextIDFromDirectory(directory.toFile()));
        assertEquals(9, IDAssigner.getNextIDFromDirectory(directory.toFile()));
        assertEquals(0, IDAssigner.getNextIDFromFile(temp.resolve("standalone-id").toFile()));
        assertEquals(1, IDAssigner.getNextIDFromFile(temp.resolve("standalone-id").toFile()));
        assertTrue(messages.isEmpty());
    }

    @Test
    void packetHandlerLogsDirectionAndExceptionWithoutRethrowing() {
        PacketHandler handler = new PacketHandler();
        // Null event deterministically enters each broad exception handler without a Minecraft proxy.
        assertDoesNotThrow(() -> handler.onClientPacket(null));
        assertDoesNotThrow(() -> handler.onServerPacket(null));
        assertEquals(2, messages.size());
        assertTrue(messages.get(0).contains("client-bound packet"));
        assertTrue(messages.get(1).contains("server-bound packet"));
        for (int i = 0; i < 2; i++) {
            assertEquals(Level.ERROR, levels.get(i));
            assertTrue(exceptions.get(i) instanceof NullPointerException);
        }
    }

    @Test
    void missingApiMethodLogsWarningAndPreservesNullFallback() throws Exception {
        restoreLogger();
        attach((Logger) LogManager.getLogger("ComputerCraftAPI"));
        Field ccClass = ComputerCraftAPI.class.getDeclaredField("computerCraft");
        ccClass.setAccessible(true);
        Object original = ccClass.get(null);
        Method findMethod = ComputerCraftAPI.class.getDeclaredMethod("findCCMethod", String.class, Class[].class);
        findMethod.setAccessible(true);
        try {
            ccClass.set(null, String.class);
            assertNull(findMethod.invoke(null, "missingComputerCraftMethod", new Class<?>[0]));
            assertEquals(1, messages.size());
            assertTrue(messages.get(0).contains("missingComputerCraftMethod"));
            assertEquals(Level.WARN, levels.get(0));
            assertTrue(exceptions.get(0) instanceof NoSuchMethodException);
        } finally {
            ccClass.set(null, original);
        }
    }

    @Test
    void productionSourcesDoNotWriteDiagnosticsDirectlyToConsole() throws Exception {
        Path sources = Paths.get("src/main/java");
        assertTrue(Files.isDirectory(sources), "source guard must not silently scan an absent directory");
        List<Path> offenders = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(sources)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                if (source.matches("(?s).*\\.printStackTrace\\s*\\(.*")
                    || source.matches("(?s).*System\\.(out|err)\\.print(ln|f)?\\s*\\(.*")) {
                    offenders.add(path);
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Use Log4j for diagnostics: " + offenders);
    }

    private void assertError(String operation, Path path, Class<? extends Throwable> exceptionType) {
        assertEquals(1, messages.size(), "one contextual log entry, not separate message and stack trace");
        assertTrue(messages.get(0).contains(operation));
        assertTrue(messages.get(0).contains(path.toString()));
        assertEquals(Level.ERROR, levels.get(0));
        assertTrue(exceptionType.isInstance(exceptions.get(0)), "the original exception must be attached");
    }
}
