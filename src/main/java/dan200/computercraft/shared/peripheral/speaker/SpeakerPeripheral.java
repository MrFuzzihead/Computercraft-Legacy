package dan200.computercraft.shared.peripheral.speaker;

import java.util.HashMap;
import java.util.Map;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;

/**
 * Speaker peripheral — four Lua methods: {@code playNote}, {@code playSound},
 * {@code playAudio}, {@code stop}.
 *
 * <p>
 * All methods are <em>non-blocking</em>: they validate arguments, write to
 * shared fields on {@link TileSpeaker}, and return immediately. Actual
 * Minecraft I/O happens on the server main thread in
 * {@link TileSpeaker#updateEntity()}.
 * </p>
 */
public class SpeakerPeripheral implements IPeripheral {

    // -------------------------------------------------------------------------
    // Instrument map: CC:Tweaked name → 1.7.10 Minecraft sound event
    // -------------------------------------------------------------------------

    private static final Map<String, String> INSTRUMENTS = new HashMap<>();

    static {
        INSTRUMENTS.put("harp", "note.harp");
        INSTRUMENTS.put("basedrum", "note.bd");
        INSTRUMENTS.put("snare", "note.snare");
        INSTRUMENTS.put("hat", "note.hat");
        INSTRUMENTS.put("bass", "note.bass");
        // Post-1.7.10 instruments — accepted by the API but silent in vanilla 1.7.10
        // without a resource pack that provides the sound events.
        INSTRUMENTS.put("flute", "note.flute");
        INSTRUMENTS.put("bell", "note.bell");
        INSTRUMENTS.put("guitar", "note.guitar");
        INSTRUMENTS.put("chime", "note.chime");
        INSTRUMENTS.put("xylophone", "note.xylophone");
        INSTRUMENTS.put("iron_xylophone", "note.iron_xylophone");
        INSTRUMENTS.put("cow_bell", "note.cow_bell");
        INSTRUMENTS.put("didgeridoo", "note.didgeridoo");
        INSTRUMENTS.put("bit", "note.bit");
        INSTRUMENTS.put("banjo", "note.banjo");
        INSTRUMENTS.put("pling", "note.pling");
    }

    // -------------------------------------------------------------------------
    // Method index constants
    // -------------------------------------------------------------------------

    private static final int METHOD_PLAY_NOTE = 0;
    private static final int METHOD_PLAY_SOUND = 1;
    private static final int METHOD_PLAY_AUDIO = 2;
    private static final int METHOD_STOP = 3;

    // -------------------------------------------------------------------------

    private final TileSpeaker m_tile;

    public SpeakerPeripheral(TileSpeaker tile) {
        this.m_tile = tile;
    }

    // -------------------------------------------------------------------------
    // IPeripheral
    // -------------------------------------------------------------------------

    @Override
    public String getType() {
        return "speaker";
    }

    @Override
    public String[] getMethodNames() {
        return new String[] { "playNote", "playSound", "playAudio", "stop" };
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
        throws LuaException {
        switch (method) {
            case METHOD_PLAY_NOTE:
                return playNote(arguments);
            case METHOD_PLAY_SOUND:
                return playSound(arguments);
            case METHOD_PLAY_AUDIO:
                return playAudio(arguments);
            case METHOD_STOP:
                return stop();
            default:
                return null;
        }
    }

    @Override
    public void attach(IComputerAccess computer) {
        m_tile.attachComputer(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        m_tile.detachComputer(computer);
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other || (other instanceof SpeakerPeripheral && ((SpeakerPeripheral) other).m_tile == m_tile);
    }

    // -------------------------------------------------------------------------
    // Method implementations
    // -------------------------------------------------------------------------

    private Object[] playNote(Object[] args) throws LuaException {
        // arg 0: instrument (string, required)
        if (args.length < 1 || args[0] == null) {
            throw new LuaException("Expected string");
        }
        if (!(args[0] instanceof String)) {
            throw new LuaException("Expected string");
        }
        String instrumentName = (String) args[0];
        String soundEvent = INSTRUMENTS.get(instrumentName);
        if (soundEvent == null) {
            throw new LuaException("Invalid instrument '" + instrumentName + "'");
        }

        // arg 1: volume (optional, 0..3, default 1.0)
        float volume = 1.0f;
        if (args.length >= 2 && args[1] != null) {
            if (!(args[1] instanceof Number)) throw new LuaException("Expected number");
            double v = ((Number) args[1]).doubleValue();
            if (v < 0.0 || v > 3.0) throw new LuaException("Volume must be between 0 and 3");
            volume = (float) v;
        }

        // arg 2: pitch (optional, 0..24, default 12.0)
        double pitch = 12.0;
        if (args.length >= 3 && args[2] != null) {
            if (!(args[2] instanceof Number)) throw new LuaException("Expected number");
            pitch = ((Number) args[2]).doubleValue();
            if (pitch < 0.0 || pitch > 24.0) throw new LuaException("Pitch must be between 0 and 24");
        }
        float pitchF = (float) Math.pow(2.0, (pitch - 12.0) / 12.0);

        synchronized (m_tile) {
            if (m_tile.m_pendingNotes.size() >= ComputerCraft.speaker_max_notes_per_tick) {
                return new Object[] { false };
            }
            m_tile.m_pendingNotes.add(new TileSpeaker.PendingNote(soundEvent, volume, pitchF));
        }
        return new Object[] { true };
    }

    private Object[] playSound(Object[] args) throws LuaException {
        // arg 0: name (string, required, 1..512 chars)
        if (args.length < 1 || args[0] == null) {
            throw new LuaException("Expected string");
        }
        if (!(args[0] instanceof String)) {
            throw new LuaException("Expected string");
        }
        String name = (String) args[0];
        if (name.isEmpty()) {
            throw new LuaException("Sound name must not be empty");
        }
        if (name.length() > 512) {
            throw new LuaException("Sound name too long");
        }

        // arg 1: volume (optional, 0..3, default 1.0)
        float volume = 1.0f;
        if (args.length >= 2 && args[1] != null) {
            if (!(args[1] instanceof Number)) throw new LuaException("Expected number");
            double v = ((Number) args[1]).doubleValue();
            if (v < 0.0 || v > 3.0) throw new LuaException("Volume must be between 0 and 3");
            volume = (float) v;
        }

        // arg 2: pitch (optional, >= 0, default 1.0)
        float pitch = 1.0f;
        if (args.length >= 3 && args[2] != null) {
            if (!(args[2] instanceof Number)) throw new LuaException("Expected number");
            double p = ((Number) args[2]).doubleValue();
            if (p < 0.0) throw new LuaException("Pitch must be non-negative");
            pitch = (float) p;
        }

        synchronized (m_tile) {
            // Reject if another sound / active audio stream is in progress.
            if (m_tile.m_pendingSound != null) return new Object[] { false };
            if (m_tile.m_audioState != null && m_tile.m_audioState.isActive()) {
                return new Object[] { false };
            }
            m_tile.m_audioState = null;
            m_tile.m_pendingSound = new TileSpeaker.PendingSound(name, volume, pitch);
        }
        return new Object[] { true };
    }

    private Object[] playAudio(Object[] args) throws LuaException {
        // arg 0: audio table (required)
        if (args.length < 1 || args[0] == null) {
            throw new LuaException("Expected table");
        }
        if (!(args[0] instanceof Map)) {
            throw new LuaException("Expected table");
        }
        Map<?, ?> audioMap = (Map<?, ?>) args[0];
        int audioLength = audioMap.size();
        if (audioLength == 0) {
            throw new LuaException("Cannot play empty audio");
        }
        if (audioLength > 128 * 1024) {
            throw new LuaException("Audio too large");
        }

        // Extract and validate samples (1-based Lua array → 0-based Java array).
        int[] samples = new int[audioLength];
        for (int i = 0; i < audioLength; i++) {
            Object val = audioMap.get((double) (i + 1));
            if (!(val instanceof Number)) {
                throw new LuaException("Invalid audio data at index " + (i + 1));
            }
            double d = ((Number) val).doubleValue();
            if (d < -128 || d > 127) {
                throw new LuaException("Sample out of range [-128, 127] at index " + (i + 1));
            }
            samples[i] = (int) d;
        }

        // arg 1: volume (optional, 0..3, default = previous or 1.0)
        synchronized (m_tile) {
            float defaultVol = (m_tile.m_audioState != null) ? m_tile.m_audioState.lastVolume : 1.0f;
            float volume = defaultVol;
            if (args.length >= 2 && args[1] != null) {
                if (!(args[1] instanceof Number)) throw new LuaException("Expected number");
                double v = ((Number) args[1]).doubleValue();
                if (v < 0.0 || v > 3.0) throw new LuaException("Volume must be between 0 and 3");
                volume = (float) v;
            }

            if (m_tile.m_audioState == null) {
                m_tile.m_audioState = new SpeakerAudioState();
            }
            boolean accepted = m_tile.m_audioState.pushBuffer(samples, volume);
            return new Object[] { accepted };
        }
    }

    private Object[] stop() {
        synchronized (m_tile) {
            m_tile.m_shouldStop = true;
        }
        return null;
    }
}
