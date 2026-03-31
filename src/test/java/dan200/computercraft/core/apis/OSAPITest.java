package dan200.computercraft.core.apis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.TimeZone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.IComputerEnvironment;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.terminal.Terminal;

/**
 * Unit tests for {@link OSAPI} covering the {@code os.epoch} and {@code os.date}
 * methods added to match the CC:Tweaked API.
 *
 * <p>All tests call {@link OSAPI#callMethod} directly with {@code null} as the
 * {@link dan200.computercraft.api.lua.ILuaContext}; neither {@code epoch} nor
 * {@code date} schedule tasks on the main thread so this is safe.</p>
 *
 * <p>The stub environment is initialised with <b>day&nbsp;=&nbsp;5</b> and
 * <b>time&nbsp;=&nbsp;6.0</b> (06:00 in-game hours) to give deterministic ingame
 * epoch results:
 * <pre>ticks = 5 * 24000 + 6 * 1000 = 126 000 → ms = 6 300 000</pre>
 * </p>
 */
class OSAPITest {

    // Method indices must match the order declared in OSAPI.getMethodNames().
    private static final int METHOD_EPOCH = 15;
    private static final int METHOD_DATE = 16;

    private OSAPI api;

    @BeforeEach
    void setUp() {
        api = new OSAPI(new StubAPIEnvironment(5, 6.0));
        api.startup();
    }

    // =========================================================================
    // os.epoch
    // =========================================================================

    @Test
    void epochUtcIsCloseToSystemTime() throws LuaException {
        long before = System.currentTimeMillis();
        Object[] result = api.callMethod(null, METHOD_EPOCH, new Object[] { "utc" });
        long after = System.currentTimeMillis();

        assertNotNull(result);
        long epoch = ((Number) result[0]).longValue();
        assertTrue(
            epoch >= before && epoch <= after + 5,
            "utc epoch must fall within the measured system-time window");
    }

    @Test
    void epochLocalDiffersFromUtcByRawTimezoneOffset() throws LuaException {
        Object[] utcResult = api.callMethod(null, METHOD_EPOCH, new Object[] { "utc" });
        Object[] localResult = api.callMethod(null, METHOD_EPOCH, new Object[] { "local" });

        long utc = ((Number) utcResult[0]).longValue();
        long local = ((Number) localResult[0]).longValue();

        // Allow a small window for the two currentTimeMillis() calls racing.
        long expectedOffset = TimeZone.getDefault().getRawOffset();
        assertTrue(
            Math.abs((local - utc) - expectedOffset) < 200,
            "local epoch should differ from utc by the raw timezone offset");
    }

    @Test
    void epochIngameUsesGameDayAndTime() throws LuaException {
        // day=5, time=6.0h → ticks = 5*24000 + 6*1000 = 126 000 → ms = 6 300 000
        long expected = (5L * 24000L + (long) (6.0 * 1000.0)) * 50L;
        Object[] result = api.callMethod(null, METHOD_EPOCH, new Object[] { "ingame" });

        assertNotNull(result);
        assertEquals(
            expected,
            ((Number) result[0]).longValue(),
            "ingame epoch must be derived from game day and time-of-day");
    }

    @Test
    void epochDefaultsToIngame() throws LuaException {
        // No argument → same as "ingame"
        long expected = (5L * 24000L + (long) (6.0 * 1000.0)) * 50L;
        Object[] result = api.callMethod(null, METHOD_EPOCH, new Object[] {});

        assertNotNull(result);
        assertEquals(
            expected,
            ((Number) result[0]).longValue(),
            "os.epoch() with no argument must equal the ingame epoch");
    }

    @Test
    void epochUnknownTimezoneThrowsLuaException() {
        assertThrows(
            LuaException.class,
            () -> api.callMethod(null, METHOD_EPOCH, new Object[] { "mars" }),
            "An unknown timezone string must throw LuaException");
    }

    // =========================================================================
    // os.date — *t (table) format
    // =========================================================================

    @Test
    @SuppressWarnings("unchecked")
    void dateStar_tReturnsCorrectFieldsForEpoch() throws LuaException {
        // Unix epoch 0 = 1970-01-01 00:00:00 UTC (Thursday)
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!*t", 0.0 });

        assertNotNull(result);
        Map<Object, Object> t = (Map<Object, Object>) result[0];

        assertEquals(1970.0, ((Number) t.get("year")).doubleValue(), "year");
        assertEquals(1.0, ((Number) t.get("month")).doubleValue(), "month");
        assertEquals(1.0, ((Number) t.get("day")).doubleValue(), "day");
        assertEquals(0.0, ((Number) t.get("hour")).doubleValue(), "hour");
        assertEquals(0.0, ((Number) t.get("min")).doubleValue(), "min");
        assertEquals(0.0, ((Number) t.get("sec")).doubleValue(), "sec");
        // 1970-01-01 was a Thursday; Java Calendar.THURSDAY = 5
        assertEquals(5.0, ((Number) t.get("wday")).doubleValue(), "wday (Thursday = 5)");
        assertEquals(1.0, ((Number) t.get("yday")).doubleValue(), "yday");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dateStar_tIsdstIsFalseForUtc() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!*t", 0.0 });
        Map<Object, Object> t = (Map<Object, Object>) result[0];

        assertNotNull(t.get("isdst"), "isdst must be present");
        assertFalse((Boolean) t.get("isdst"), "UTC never observes DST");
    }

    // =========================================================================
    // os.date — format string tokens
    // =========================================================================

    @Test
    void dateFormatYearMonthDay() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!%Y-%m-%d", 0.0 });
        assertEquals("1970-01-01", result[0]);
    }

    @Test
    void dateFormatTwoDigitYear() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!%y", 0.0 });
        assertEquals("70", result[0]);
    }

    @Test
    void dateFormatHourMinSec() throws LuaException {
        // 1h 2m 3s after epoch
        double secs = 1 * 3600.0 + 2 * 60.0 + 3.0;
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!%H:%M:%S", secs });
        assertEquals("01:02:03", result[0]);
    }

    @Test
    void dateFormatTimeComponent_X() throws LuaException {
        double secs = 14 * 3600.0 + 30 * 60.0 + 59.0;
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!%X", secs });
        assertEquals("14:30:59", result[0]);
    }

    @Test
    void dateFormatDateComponent_x() throws LuaException {
        // 1970-01-01 → month=01, day=01, year%100=70
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!%x", 0.0 });
        assertEquals("01/01/70", result[0]);
    }

    @Test
    void dateFormatFullWeekdayName() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!%A", 0.0 });
        assertEquals("Thursday", result[0]);
    }

    @Test
    void dateFormatAbbreviatedWeekdayName() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!%a", 0.0 });
        assertEquals("Thu", result[0]);
    }

    @Test
    void dateFormatFullMonthName() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!%B", 0.0 });
        assertEquals("January", result[0]);
    }

    @Test
    void dateFormatAbbreviatedMonthName_b() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!%b", 0.0 });
        assertEquals("Jan", result[0]);
    }

    @Test
    void dateFormatAbbreviatedMonthName_h() throws LuaException {
        // %h is an alias for %b
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!%h", 0.0 });
        assertEquals("Jan", result[0]);
    }

    @Test
    void dateFormatAmPm() throws LuaException {
        assertEquals("AM", api.callMethod(null, METHOD_DATE, new Object[] { "!%p", 0.0 })[0], "midnight → AM");
        assertEquals(
            "PM",
            api.callMethod(null, METHOD_DATE, new Object[] { "!%p", 12 * 3600.0 })[0],
            "noon → PM");
    }

    @Test
    void dateFormatDayOfYear() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!%j", 0.0 });
        assertEquals("001", result[0]);
    }

    @Test
    void dateFormatWdayThursdayIsFour() throws LuaException {
        // %w: 0=Sunday → Thursday = 4
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!%w", 0.0 });
        assertEquals("4", result[0]);
    }

    @Test
    void dateFormatWdaySundayIsZero() throws LuaException {
        // 1970-01-04 was Sunday (3 days after the Thursday epoch)
        double sundayEpoch = 3 * 86400.0;
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!%w", sundayEpoch });
        assertEquals("0", result[0]);
    }

    @Test
    void dateFormatUdayThursdayIsFour() throws LuaException {
        // %u: 1=Monday … 7=Sunday → Thursday = 4
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!%u", 0.0 });
        assertEquals("4", result[0]);
    }

    @Test
    void dateFormatUdaySundayIsSeven() throws LuaException {
        // %u: Sunday = 7
        double sundayEpoch = 3 * 86400.0;
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!%u", sundayEpoch });
        assertEquals("7", result[0]);
    }

    @Test
    void dateFormatLiteralPercent() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!%%", 0.0 });
        assertEquals("%", result[0]);
    }

    @Test
    void dateFormatNewlineToken() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!%n", 0.0 });
        assertEquals("\n", result[0]);
    }

    @Test
    void dateFormatTabToken() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!%t", 0.0 });
        assertEquals("\t", result[0]);
    }

    @Test
    void dateFormatLiteralTextPassesThrough() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "!hello world", 0.0 });
        assertEquals("hello world", result[0]);
    }

    @Test
    void dateNoArgsReturnsNonNullString() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] {});
        assertNotNull(result);
        assertTrue(result[0] instanceof String, "os.date() with no args must return a string");
    }

    @Test
    void dateCurrentYearIsPlausible() throws LuaException {
        Object[] result = api.callMethod(null, METHOD_DATE, new Object[] { "%Y" });
        assertNotNull(result);
        String year = (String) result[0];
        assertTrue(year.matches("\\d{4}"), "Year must be 4 digits, got: " + year);
        int y = Integer.parseInt(year);
        assertTrue(y >= 2020 && y < 2200, "Year must be in a plausible range, got: " + y);
    }

    // =========================================================================
    // Stubs
    // =========================================================================

    /** Minimal {@link IComputerEnvironment} returning a fixed in-game day and time. */
    private static class StubComputerEnvironment implements IComputerEnvironment {

        private final int m_day;
        private final double m_timeOfDay;

        StubComputerEnvironment(int day, double timeOfDay) {
            this.m_day = day;
            this.m_timeOfDay = timeOfDay;
        }

        @Override
        public int getDay() {
            return m_day;
        }

        @Override
        public double getTimeOfDay() {
            return m_timeOfDay;
        }

        @Override
        public boolean isColour() {
            return true;
        }

        @Override
        public long getComputerSpaceLimit() {
            return 1_000_000L;
        }

        @Override
        public int assignNewID() {
            return 1;
        }

        @Override
        public IWritableMount createSaveDirMount(String path, long capacity) {
            return null;
        }

        @Override
        public IMount createResourceMount(String domain, String path) {
            return null;
        }
    }

    /**
     * Minimal {@link IAPIEnvironment} that wires the stub computer environment into
     * {@link OSAPI}. Methods not exercised by {@code epoch} or {@code date} are
     * no-ops or return zero/null.
     */
    private static class StubAPIEnvironment implements IAPIEnvironment {

        private final IComputerEnvironment m_env;

        StubAPIEnvironment(int day, double timeOfDay) {
            this.m_env = new StubComputerEnvironment(day, timeOfDay);
        }

        @Override
        public IComputerEnvironment getComputerEnvironment() {
            return m_env;
        }

        @Override
        public int getComputerID() {
            return 1;
        }

        @Override
        public String getLabel() {
            return null;
        }

        @Override
        public void setLabel(String label) {}

        @Override
        public void shutdown() {}

        @Override
        public void reboot() {}

        @Override
        public void queueEvent(String event, Object[] args) {}

        @Override
        public Computer getComputer() {
            return null;
        }

        @Override
        public Terminal getTerminal() {
            return null;
        }

        @Override
        public FileSystem getFileSystem() {
            return null;
        }

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
        public void setPeripheralChangeListener(IPeripheralChangeListener listener) {}

        @Override
        public IPeripheral getPeripheral(int side) {
            return null;
        }
    }
}

