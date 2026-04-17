package dan200.computercraft.shared.peripheral.speaker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.World;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.network.ComputerCraftPacket;

/**
 * A standalone speaker peripheral suitable for use in turtle upgrades and pocket
 * computer upgrades. All audio state (pending notes, pending sound, DFPWM audio
 * stream) is owned by this object rather than by a {@link TileSpeaker}.
 *
 * <p>
 * Concrete subclasses must implement {@link #equals(IPeripheral)} and call
 * {@link #tick(World, double, double, double)} once per server tick from their
 * respective update hooks.
 * </p>
 */
public abstract class PortableSpeakerPeripheral implements IPeripheral {

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
    // State — guarded by {@code this}
    // -------------------------------------------------------------------------

    /** Notes queued this tick; flushed by {@link #tick}. */
    private final List<TileSpeaker.PendingNote> m_pendingNotes = new ArrayList<>();

    /** Sound queued this tick; flushed by {@link #tick}. */
    private TileSpeaker.PendingSound m_pendingSound = null;

    /** DFPWM encoder + back-pressure state for {@code playAudio}. */
    private SpeakerAudioState m_audioState = null;

    /** Set by {@code stop()}; processed once in {@link #tick}. */
    private boolean m_shouldStop = false;

    /** Computers currently attached to this peripheral. */
    private final Set<IComputerAccess> m_computers = new HashSet<>();

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
        synchronized (this) {
            m_computers.add(computer);
        }
    }

    @Override
    public void detach(IComputerAccess computer) {
        synchronized (this) {
            m_computers.remove(computer);
        }
    }

    // -------------------------------------------------------------------------
    // Tick — called each server tick from the owning upgrade / item
    // -------------------------------------------------------------------------

    /**
     * Flushes pending audio state and plays sounds at the given world position.
     * Must be called once per server tick on the <em>server side only</em>.
     *
     * @param world the world the speaker currently occupies
     * @param x     X position (block centre or entity eye position)
     * @param y     Y position
     * @param z     Z position
     */
    public void tick(World world, double x, double y, double z) {
        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);
        int iz = (int) Math.round(z);

        // ---- 1. Handle stop ----
        boolean shouldStop;
        synchronized (this) {
            shouldStop = m_shouldStop;
            if (shouldStop) {
                m_shouldStop = false;
                m_audioState = null;
                m_pendingSound = null;
            }
        }
        if (shouldStop) {
            ComputerCraftPacket stopPacket = new ComputerCraftPacket();
            stopPacket.m_packetType = ComputerCraftPacket.SpeakerStop;
            stopPacket.m_dataInt = new int[] { ix, iy, iz };
            ComputerCraft.sendToAllPlayers(stopPacket);
            return;
        }

        // ---- 2. Flush pending notes ----
        List<TileSpeaker.PendingNote> notesToPlay;
        synchronized (this) {
            if (!m_pendingNotes.isEmpty()) {
                notesToPlay = new ArrayList<>(m_pendingNotes);
                m_pendingNotes.clear();
            } else {
                notesToPlay = null;
            }
        }
        if (notesToPlay != null) {
            for (TileSpeaker.PendingNote note : notesToPlay) {
                world.playSoundEffect(x, y, z, note.soundEvent, note.volume, note.pitch);
            }
        }

        // ---- 3. Flush pending sound ----
        TileSpeaker.PendingSound pendingSound;
        synchronized (this) {
            pendingSound = m_pendingSound;
            m_pendingSound = null;
        }
        if (pendingSound != null) {
            world.playSoundEffect(x, y, z, pendingSound.name, pendingSound.volume, pendingSound.pitch);
        }

        // ---- 4. Flush pending audio ----
        SpeakerAudioState audioState;
        synchronized (this) {
            audioState = m_audioState;
        }
        if (audioState != null) {
            long now = System.nanoTime();
            if (audioState.shouldSendPending(now)) {
                byte[] dfpwm = audioState.pullPending(now);
                float volume = audioState.pendingVolume;

                ComputerCraftPacket audioPacket = new ComputerCraftPacket();
                audioPacket.m_packetType = ComputerCraftPacket.SpeakerAudio;
                audioPacket.m_dataInt = new int[] { ix, iy, iz, Math.round(volume * 1000) };
                audioPacket.m_dataByte = new byte[][] { dfpwm };
                ComputerCraft.sendToAllAround(audioPacket, world, x, y, z, ComputerCraft.speaker_audio_range);

                Set<IComputerAccess> snapshot;
                synchronized (this) {
                    snapshot = new HashSet<>(m_computers);
                }
                for (IComputerAccess computer : snapshot) {
                    computer.queueEvent("speaker_audio_empty", new Object[0]);
                }
            }
        }
    }

    /**
     * Sends a {@link ComputerCraftPacket#SpeakerStop} packet to all players.
     * Should be called when the upgrade is removed or the peripheral destroyed.
     *
     * @param world world the speaker was in
     * @param x     last known X position
     * @param y     last known Y position
     * @param z     last known Z position
     */
    public void destroy(World world, double x, double y, double z) {
        if (world == null || world.isRemote) return;
        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);
        int iz = (int) Math.round(z);
        synchronized (this) {
            m_audioState = null;
            m_pendingSound = null;
            m_pendingNotes.clear();
            m_shouldStop = false;
        }
        ComputerCraftPacket stopPacket = new ComputerCraftPacket();
        stopPacket.m_packetType = ComputerCraftPacket.SpeakerStop;
        stopPacket.m_dataInt = new int[] { ix, iy, iz };
        ComputerCraft.sendToAllPlayers(stopPacket);
    }

    // -------------------------------------------------------------------------
    // Method implementations
    // -------------------------------------------------------------------------

    private Object[] playNote(Object[] args) throws LuaException {
        if (args.length < 1 || args[0] == null) throw new LuaException("Expected string");
        if (!(args[0] instanceof String)) throw new LuaException("Expected string");
        String instrumentName = (String) args[0];
        String soundEvent = INSTRUMENTS.get(instrumentName);
        if (soundEvent == null) throw new LuaException("Invalid instrument '" + instrumentName + "'");

        float volume = 1.0f;
        if (args.length >= 2 && args[1] != null) {
            if (!(args[1] instanceof Number)) throw new LuaException("Expected number");
            double v = ((Number) args[1]).doubleValue();
            if (v < 0.0 || v > 3.0) throw new LuaException("Volume must be between 0 and 3");
            volume = (float) v;
        }

        double pitch = 12.0;
        if (args.length >= 3 && args[2] != null) {
            if (!(args[2] instanceof Number)) throw new LuaException("Expected number");
            pitch = ((Number) args[2]).doubleValue();
            if (pitch < 0.0 || pitch > 24.0) throw new LuaException("Pitch must be between 0 and 24");
        }
        float pitchF = (float) Math.pow(2.0, (pitch - 12.0) / 12.0);

        synchronized (this) {
            if (m_pendingNotes.size() >= ComputerCraft.speaker_max_notes_per_tick) {
                return new Object[] { false };
            }
            m_pendingNotes.add(new TileSpeaker.PendingNote(soundEvent, volume, pitchF));
        }
        return new Object[] { true };
    }

    private Object[] playSound(Object[] args) throws LuaException {
        if (args.length < 1 || args[0] == null) throw new LuaException("Expected string");
        if (!(args[0] instanceof String)) throw new LuaException("Expected string");
        String name = (String) args[0];
        if (name.isEmpty()) throw new LuaException("Sound name must not be empty");
        if (name.length() > 512) throw new LuaException("Sound name too long");

        float volume = 1.0f;
        if (args.length >= 2 && args[1] != null) {
            if (!(args[1] instanceof Number)) throw new LuaException("Expected number");
            double v = ((Number) args[1]).doubleValue();
            if (v < 0.0 || v > 3.0) throw new LuaException("Volume must be between 0 and 3");
            volume = (float) v;
        }

        float pitch = 1.0f;
        if (args.length >= 3 && args[2] != null) {
            if (!(args[2] instanceof Number)) throw new LuaException("Expected number");
            double p = ((Number) args[2]).doubleValue();
            if (p < 0.0) throw new LuaException("Pitch must be non-negative");
            pitch = (float) p;
        }

        synchronized (this) {
            if (m_pendingSound != null) return new Object[] { false };
            if (m_audioState != null && m_audioState.isActive()) return new Object[] { false };
            m_audioState = null;
            m_pendingSound = new TileSpeaker.PendingSound(name, volume, pitch);
        }
        return new Object[] { true };
    }

    private Object[] playAudio(Object[] args) throws LuaException {
        if (args.length < 1 || args[0] == null) throw new LuaException("Expected table");
        if (!(args[0] instanceof Map)) throw new LuaException("Expected table");
        Map<?, ?> audioMap = (Map<?, ?>) args[0];
        int audioLength = audioMap.size();
        if (audioLength == 0) throw new LuaException("Cannot play empty audio");
        if (audioLength > 128 * 1024) throw new LuaException("Audio too large");

        synchronized (this) {
            if (m_audioState != null && !m_audioState.canAcceptBuffer()) {
                return new Object[] { false };
            }
        }

        int[] samples = new int[audioLength];
        for (int i = 0; i < audioLength; i++) {
            Object val = audioMap.get((double) (i + 1));
            if (!(val instanceof Number)) throw new LuaException("Invalid audio data at index " + (i + 1));
            double d = ((Number) val).doubleValue();
            if (d < -128 || d > 127) throw new LuaException("Sample out of range [-128, 127] at index " + (i + 1));
            samples[i] = (int) d;
        }

        synchronized (this) {
            float defaultVol = (m_audioState != null) ? m_audioState.lastVolume : 1.0f;
            float volume = defaultVol;
            if (args.length >= 2 && args[1] != null) {
                if (!(args[1] instanceof Number)) throw new LuaException("Expected number");
                double v = ((Number) args[1]).doubleValue();
                if (v < 0.0 || v > 3.0) throw new LuaException("Volume must be between 0 and 3");
                volume = (float) v;
            }

            if (m_audioState == null) {
                m_audioState = new SpeakerAudioState();
            }
            boolean accepted = m_audioState.pushBuffer(samples, volume);
            return new Object[] { accepted };
        }
    }

    private Object[] stop() {
        synchronized (this) {
            m_shouldStop = true;
        }
        return null;
    }
}
