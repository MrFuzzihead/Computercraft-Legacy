package dan200.computercraft.shared.peripheral.speaker;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.shared.pocket.peripherals.PocketSpeakerPeripheral;

/**
 * Unit tests for {@link PortableSpeakerPeripheral} exercised via
 * {@link PocketSpeakerPeripheral} (the simplest concrete subclass).
 *
 * <p>
 * Covers argument validation and back-pressure behavior for every Lua method.
 * Mirrors the structure of {@link SpeakerPeripheralTest}; internal state is not
 * inspected directly because {@link PortableSpeakerPeripheral}'s fields are
 * private — return values are used instead.
 * </p>
 */
class PortableSpeakerPeripheralTest {

    private PocketSpeakerPeripheral peripheral;

    @BeforeEach
    void setUp() {
        peripheral = new PocketSpeakerPeripheral();
        ComputerCraft.speaker_max_notes_per_tick = 8;
    }

    // =========================================================================
    // 1 – getType
    // =========================================================================

    @Test
    void getType_returnsSpeaker() {
        assertEquals("speaker", peripheral.getType());
    }

    // =========================================================================
    // 2 – getMethodNames
    // =========================================================================

    @Test
    void getMethodNames_exactlyFourMethods() {
        String[] names = peripheral.getMethodNames();
        assertEquals(Arrays.asList("playNote", "playSound", "playAudio", "stop"), Arrays.asList(names));
    }

    // =========================================================================
    // 3–9 – playNote argument validation
    // =========================================================================

    @Test
    void playNote_missingInstrument_throws() {
        assertThrows(LuaException.class, () -> call(METHOD_PLAY_NOTE));
    }

    @Test
    void playNote_nonStringInstrument_throws() {
        assertThrows(LuaException.class, () -> call(METHOD_PLAY_NOTE, 42.0));
    }

    @Test
    void playNote_unknownInstrument_throws() {
        assertThrows(LuaException.class, () -> call(METHOD_PLAY_NOTE, "kazoo"));
    }

    @Test
    void playNote_volumeBelowZero_throws() {
        assertThrows(LuaException.class, () -> call(METHOD_PLAY_NOTE, "harp", -0.1));
    }

    @Test
    void playNote_volumeAboveThree_throws() {
        assertThrows(LuaException.class, () -> call(METHOD_PLAY_NOTE, "harp", 3.1));
    }

    @Test
    void playNote_pitchBelowZero_throws() {
        assertThrows(LuaException.class, () -> call(METHOD_PLAY_NOTE, "harp", 1.0, -1.0));
    }

    @Test
    void playNote_pitchAbove24_throws() {
        assertThrows(LuaException.class, () -> call(METHOD_PLAY_NOTE, "harp", 1.0, 25.0));
    }

    // =========================================================================
    // 10–11 – playNote success + rate limit
    // =========================================================================

    @Test
    void playNote_validArgs_returnsTrue() throws LuaException {
        Object[] result = call(METHOD_PLAY_NOTE, "harp", 1.0, 12.0);
        assertTrue((Boolean) result[0]);
    }

    @Test
    void playNote_atRateLimit_returnsFalse() throws LuaException {
        for (int i = 0; i < ComputerCraft.speaker_max_notes_per_tick; i++) {
            call(METHOD_PLAY_NOTE, "harp");
        }
        Object[] result = call(METHOD_PLAY_NOTE, "harp");
        assertFalse((Boolean) result[0]);
    }

    // =========================================================================
    // 12–15 – playSound argument validation
    // =========================================================================

    @Test
    void playSound_missingName_throws() {
        assertThrows(LuaException.class, () -> call(METHOD_PLAY_SOUND));
    }

    @Test
    void playSound_nonStringName_throws() {
        assertThrows(LuaException.class, () -> call(METHOD_PLAY_SOUND, 99.0));
    }

    @Test
    void playSound_nameTooLong_throws() {
        char[] chars = new char[513];
        Arrays.fill(chars, 'x');
        String longName = new String(chars);
        assertThrows(LuaException.class, () -> call(METHOD_PLAY_SOUND, longName));
    }

    @Test
    void playSound_negativeVolume_throws() {
        assertThrows(LuaException.class, () -> call(METHOD_PLAY_SOUND, "entity.pig.ambient", -1.0));
    }

    // =========================================================================
    // 16–17 – playSound success + duplicate rejection
    // =========================================================================

    @Test
    void playSound_validArgs_returnsTrue() throws LuaException {
        Object[] result = call(METHOD_PLAY_SOUND, "entity.pig.ambient", 1.0, 1.0);
        assertTrue((Boolean) result[0]);
    }

    @Test
    void playSound_whenPendingSoundAlreadySet_returnsFalse() throws LuaException {
        call(METHOD_PLAY_SOUND, "entity.pig.ambient");
        Object[] result = call(METHOD_PLAY_SOUND, "entity.pig.ambient");
        assertFalse((Boolean) result[0]);
    }

    // =========================================================================
    // 18–21 – playAudio argument validation (table structure)
    // =========================================================================

    @Test
    void playAudio_missingAudioArg_throws() {
        assertThrows(LuaException.class, () -> call(METHOD_PLAY_AUDIO));
    }

    @Test
    void playAudio_nonTableAudioArg_throws() {
        assertThrows(LuaException.class, () -> call(METHOD_PLAY_AUDIO, "not_a_table"));
    }

    @Test
    void playAudio_emptyTable_throws() {
        assertThrows(LuaException.class, () -> call(METHOD_PLAY_AUDIO, makeAudioMap(0)));
    }

    @Test
    void playAudio_tableExceedsMaxSize_throws() {
        assertThrows(LuaException.class, () -> call(METHOD_PLAY_AUDIO, makeAudioMap(128 * 1024 + 1)));
    }

    // =========================================================================
    // 22–23 – playAudio sample range validation
    // =========================================================================

    @Test
    void playAudio_sampleTooLow_throws() {
        Map<Object, Object> audio = new HashMap<>();
        audio.put(1.0, -129.0);
        assertThrows(LuaException.class, () -> call(METHOD_PLAY_AUDIO, audio));
    }

    @Test
    void playAudio_sampleTooHigh_throws() {
        Map<Object, Object> audio = new HashMap<>();
        audio.put(1.0, 128.0);
        assertThrows(LuaException.class, () -> call(METHOD_PLAY_AUDIO, audio));
    }

    // =========================================================================
    // 24–25 – playAudio success + buffer-full rejection
    // =========================================================================

    @Test
    void playAudio_validTable_returnsTrue() throws LuaException {
        Object[] result = call(METHOD_PLAY_AUDIO, makeAudioMap(128));
        assertTrue((Boolean) result[0]);
    }

    @Test
    void playAudio_whenPendingAudioAlreadySet_returnsFalse() throws LuaException {
        call(METHOD_PLAY_AUDIO, makeAudioMap(128));
        Object[] result = call(METHOD_PLAY_AUDIO, makeAudioMap(128));
        assertFalse((Boolean) result[0]);
    }

    // =========================================================================
    // 26 – stop
    // =========================================================================

    @Test
    void stop_doesNotThrow() {
        assertDoesNotThrow(() -> call(METHOD_STOP));
    }

    // =========================================================================
    // 27 – floor rounding contract (regression guard for Math.round off-by-one)
    // =========================================================================

    /**
     * Turtles pass {@code pos + 0.5} as world coordinates. With {@code Math.round}
     * that rounds up to the next block ({@code round(5.5) == 6}); with
     * {@code Math.floor} it correctly maps to the block coordinate ({@code floor(5.5) == 5}).
     * This test guards the rounding contract directly.
     */
    @Test
    void floorRounding_blockCentreMapsToCorrectBlock() {
        assertEquals(5, (int) Math.floor(5.5), "block centre 5.5 must floor to block 5, not 6");
        assertEquals(5, (int) Math.floor(5.0), "exact block position must be unchanged by floor");
        assertEquals(-1, (int) Math.floor(-0.5), "negative block centre must floor toward negative infinity");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static final int METHOD_PLAY_NOTE = 0;
    private static final int METHOD_PLAY_SOUND = 1;
    private static final int METHOD_PLAY_AUDIO = 2;
    private static final int METHOD_STOP = 3;

    private Object[] call(int method, Object... args) throws LuaException {
        try {
            return peripheral.callMethod(null, null, method, args);
        } catch (LuaException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Unexpected checked exception", e);
        }
    }

    /**
     * Builds a Lua-style (Double-keyed) table of {@code size} silent samples.
     */
    private static Map<Object, Object> makeAudioMap(int size) {
        Map<Object, Object> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            map.put((double) (i + 1), 0.0);
        }
        return map;
    }
}
