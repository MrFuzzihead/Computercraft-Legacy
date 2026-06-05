package dan200.computercraft.client.audio;

import java.util.HashMap;
import java.util.Map;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import net.minecraft.util.ChunkCoordinates;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.shared.peripheral.speaker.SpeakerAudioState;

/**
 * Client-side speaker audio manager.
 *
 * <p>
 * Decodes DFPWM-encoded audio packets and plays them via
 * {@code javax.sound.sampled} at 48 kHz / 8-bit signed mono. Each unique
 * speaker position keeps its own {@link DfpwmDecoder} so that state is never
 * shared between two different speakers.
 * </p>
 *
 * <p>
 * Volume is applied by linearly scaling PCM sample values; this does not
 * respect Minecraft's master-volume slider (OpenAL upgrade deferred).
 * </p>
 */
@SideOnly(Side.CLIENT)
public class SpeakerManager {

    public static final SpeakerManager INSTANCE = new SpeakerManager();

    // Use 16-bit signed PCM for better playback quality in the Java audio stack.
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(48_000, 16, 1, true, false);
    /**
     * Size of the SourceDataLine internal buffer in bytes (approx seconds * sample_rate).
     * Use a larger buffer so non-blocking writes don't drop most decoded PCM.
     */
    private static final int LINE_BUFFER_BYTES = 48_000 * 2 * 2; // ~2 seconds of 16-bit mono audio

    /** Per-speaker DFPWM decoder — keyed by world position. */
    private final Map<ChunkCoordinates, DfpwmDecoder> m_decoders = new HashMap<>();

    /** Per-speaker output line — keyed by world position. */
    private final Map<ChunkCoordinates, SourceDataLine> m_lines = new HashMap<>();

    private SpeakerManager() {}

    // -------------------------------------------------------------------------
    // Public API (called from ComputerCraftProxyClient on the game thread)
    // -------------------------------------------------------------------------

    /**
     * Decodes {@code dfpwm} bytes and writes them to the audio line for the
     * speaker at {@code (x, y, z)}.
     *
     * @param dfpwm  DFPWM-encoded bytes produced by the server-side
     *               {@link dan200.computercraft.shared.peripheral.speaker.SpeakerAudioState}.
     * @param volume linear volume scalar in {@code [0, 3]}.
     */
    public void playAudio(int x, int y, int z, byte[] dfpwm, float volume) {
        playAudio(x, y, z, dfpwm, volume, 0); // 0 = DFPWM format (default)
    }

    /**
     * Decodes and plays audio for the speaker at (x, y, z).
     * Format: 0 = DFPWM (compressed), 1 = raw signed-8 PCM (lossless).
     *
     * @param audioData encoded audio bytes (DFPWM or raw PCM depending on format).
     * @param volume    linear volume scalar in {@code [0, 3]}.
     * @param format    0 for DFPWM, 1 for raw signed-8 PCM.
     */
    public void playAudio(int x, int y, int z, byte[] audioData, float volume, int format) {
        ChunkCoordinates key = new ChunkCoordinates(x, y, z);

        byte[] pcm;
        if (format == 1) {
            // Raw signed-8 PCM: use directly (already in the correct range).
            pcm = audioData;
        } else {
            // DFPWM (format == 0): decode using stateful decoder.
            DfpwmDecoder decoder = m_decoders.computeIfAbsent(key, k -> new DfpwmDecoder());
            pcm = decoder.decode(audioData);
        }

        // Convert decoded signed-8 PCM -> signed-16 PCM (little-endian bytes)
        // and apply volume on the wider range for better fidelity.
        byte[] out = new byte[pcm.length * 2];
        for (int i = 0; i < pcm.length; i++) {
            int s8 = pcm[i]; // signed 8-bit
            int s16 = s8 << 8; // expand to signed 16-bit
            if (Math.abs(volume - 1.0f) > 1e-4f) {
                s16 = Math.round(s16 * volume);
                if (s16 > 32767) s16 = 32767;
                if (s16 < -32768) s16 = -32768;
            }
            // little endian
            out[i * 2] = (byte) (s16 & 0xFF);
            out[i * 2 + 1] = (byte) ((s16 >> 8) & 0xFF);
        }

        // Optional debug: compute simple stats about the decoded PCM so we can
        // compare in-game audio to what the server/Lua received. Only log when
        // the global debug flag is enabled to avoid spamming release logs.
        if (ComputerCraft.debug) {
            int mn = 32767, mx = -32768, nz = 0;
            long sa = 0;
            int limit = Math.min(pcm.length, 32);
            StringBuilder first = new StringBuilder();
            for (int i = 0; i < pcm.length; i++) {
                int v = pcm[i] << 8;
                if (v < mn) mn = v;
                if (v > mx) mx = v;
                if (v != 0) nz++;
                sa += Math.abs(v);
                if (i < limit) {
                    if (i > 0) first.append(',');
                    first.append(v);
                }
            }
            double avgabs = pcm.length > 0 ? ((double) sa) / pcm.length : 0.0;
            ComputerCraft.logger.info(
                String.format(
                    "Speaker: decoded pcm len=%d fmt=%d min=%d max=%d nonzero=%d avgabs=%.2f",
                    pcm.length,
                    format,
                    mn,
                    mx,
                    nz,
                    avgabs));
        }

        SourceDataLine line = getOrOpenLine(key);
        if (line == null) return;

        // Non-blocking write: if the internal buffer is full, drop the excess.
        int available = line.available();
        if (available > 0) {
            // Ensure we write an even number of bytes (whole samples).
            int toWrite = Math.min(out.length, available);
            toWrite = (toWrite / 2) * 2;
            if (toWrite > 0) line.write(out, 0, toWrite);
            if (ComputerCraft.debug && toWrite < out.length) {
                ComputerCraft.logger
                    .info(String.format("Speaker: dropped bytes=%d out_of=%d", out.length - toWrite, out.length));
            }
        }
    }

    /**
     * Stops playback for the speaker at {@code (x, y, z)} and releases its
     * audio resources.
     */
    public void stop(int x, int y, int z) {
        ChunkCoordinates key = new ChunkCoordinates(x, y, z);
        m_decoders.remove(key);

        SourceDataLine line = m_lines.remove(key);
        if (line != null) {
            line.stop();
            line.flush();
            line.close();
        }
    }

    /**
     * Stops playback for all speakers and releases every tracked audio line.
     *
     * <p>
     * Must be called on client disconnect and world unload, because those
     * transitions do not deliver per-speaker {@code SpeakerStop} packets.
     * </p>
     */
    public void stopAll() {
        for (SourceDataLine line : m_lines.values()) {
            if (line != null) {
                line.stop();
                line.flush();
                line.close();
            }
        }
        m_lines.clear();
        m_decoders.clear();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private SourceDataLine getOrOpenLine(ChunkCoordinates key) {
        SourceDataLine line = m_lines.get(key);
        if (line != null && line.isOpen()) return line;

        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
            line = (SourceDataLine) AudioSystem.getLine(info);
            // Open the line with a larger internal buffer to reduce dropped writes when
            // the game thread performs non-blocking writes of decoded PCM.
            line.open(AUDIO_FORMAT, LINE_BUFFER_BYTES);
            line.start();
            m_lines.put(key, line);
            return line;
        } catch (LineUnavailableException e) {
            ComputerCraft.logger.warn("Speaker: could not open audio line at " + key + ": " + e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // DFPWM decoder — mirrors cc.audio.dfpwm make_decoder() exactly.
    // -------------------------------------------------------------------------

    /**
     * Stateful DFPWM decoder. Uses the same constants as
     * {@link SpeakerAudioState} (encoder) so that audio round-trips cleanly.
     */
    private static final class DfpwmDecoder {

        private static final int PREC_SHIFT = SpeakerAudioState.PREC_SHIFT;
        private static final int PREC_CEIL = SpeakerAudioState.PREC_CEIL;
        private static final int LPF_STRENGTH = SpeakerAudioState.LPF_STRENGTH;

        private int q = 0;
        private int s = 0;
        private int fq = 0;

        /** Decodes {@code input} DFPWM bytes to 8-bit signed PCM. */
        byte[] decode(byte[] input) {
            byte[] output = new byte[input.length * 8];

            int q = this.q, s = this.s, fq = this.fq;

            for (int i = 0; i < input.length; i++) {
                for (int j = 0; j < 8; j++) {
                    boolean bit = (input[i] & (1 << j)) != 0;

                    // Predictor update — same as encoder.
                    int t = bit ? Math.min(q + (s >> PREC_SHIFT), 127) : Math.max(q - (s >> PREC_SHIFT), -128);

                    // Strength adaption — same as encoder.
                    int ds = bit == (q >= 0) ? Math.min(s + PREC_CEIL, 127 << PREC_SHIFT)
                        : Math.max(s - PREC_CEIL, PREC_CEIL);

                    // LPF: output is the filtered charge, not the raw predictor.
                    fq += ((t - fq) * LPF_STRENGTH) >> 8;

                    q = t;
                    s = ds;
                    output[i * 8 + j] = (byte) fq;
                }
            }

            this.q = q;
            this.s = s;
            this.fq = fq;
            return output;
        }
    }
}
