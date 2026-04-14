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

    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(48_000, 8, 1, true, false);

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
        ChunkCoordinates key = new ChunkCoordinates(x, y, z);

        DfpwmDecoder decoder = m_decoders.computeIfAbsent(key, k -> new DfpwmDecoder());
        byte[] pcm = decoder.decode(dfpwm);

        // Apply volume (clamp to byte range).
        if (Math.abs(volume - 1.0f) > 1e-4f) {
            for (int i = 0; i < pcm.length; i++) {
                int scaled = Math.round(pcm[i] * volume);
                pcm[i] = (byte) Math.max(-128, Math.min(127, scaled));
            }
        }

        SourceDataLine line = getOrOpenLine(key);
        if (line == null) return;

        // Non-blocking write: if the internal buffer is full, drop the excess.
        int available = line.available();
        if (available > 0) {
            int toWrite = Math.min(pcm.length, available);
            line.write(pcm, 0, toWrite);
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

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private SourceDataLine getOrOpenLine(ChunkCoordinates key) {
        SourceDataLine line = m_lines.get(key);
        if (line != null && line.isOpen()) return line;

        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(AUDIO_FORMAT);
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
