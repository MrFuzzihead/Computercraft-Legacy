package dan200.computercraft.shared.peripheral.speaker;

/**
 * Server-side DFPWM encoder state for {@code playAudio}.
 *
 * <p>
 * Mirrors {@code DfpwmState} from CC:Tweaked — same predictor, same
 * strength-adaption formula, same {@code CLIENT_BUFFER} back-pressure model.
 * The only difference from the upstream {@code short}-based variant is that
 * our PCM input is already in the {@code [-128, 127]} byte range.
 * </p>
 */
public class SpeakerAudioState {

    // DFPWM constants — must match DfpwmDecoder on the client exactly.
    public static final int PREC_SHIFT = 10;
    public static final int PREC_CEIL = 1 << PREC_SHIFT; // 1024
    public static final int LPF_STRENGTH = 140;

    /**
     * How many nanoseconds before the client buffer runs out to send the
     * next batch (upstream CLIENT_BUFFER = 500 ms).
     */
    private static final long CLIENT_BUFFER_NS = 500_000_000L;

    // DFPWM encoder state (fixed-point, PREC_SHIFT fractional bits for s).
    private int encFq = 0;
    private int encQ = 0;
    private int encS = 0;

    /** Pending DFPWM bytes waiting to be dispatched via network. */
    byte[] pendingAudio = null;

    /** Volume level accompanying {@link #pendingAudio}. */
    float pendingVolume = 1.0f;

    /**
     * Volume of the most-recently accepted buffer; used as the default
     * for the next {@code playAudio} call when no explicit volume is given.
     */
    float lastVolume = 1.0f;

    /** Estimated nanosecond timestamp at which the client exhausts buffered audio. */
    private long clientEndTime = 0L;

    /**
     * Returns {@code true} if a new buffer can be accepted right now
     * (i.e. no batch is waiting to be dispatched). Use as a cheap O(1)
     * back-pressure probe before doing any expensive sample extraction.
     */
    public boolean canAcceptBuffer() {
        return pendingAudio == null;
    }

    /**
     * Encodes {@code samples} (8-bit signed PCM, −128..127) to DFPWM and
     * stores the result as pending.
     *
     * @return {@code true} if accepted; {@code false} if a previous batch has
     *         not yet been dispatched (back-pressure).
     */
    public boolean pushBuffer(int[] samples, float volume) {
        if (pendingAudio != null) return false;
        this.lastVolume = volume;
        this.pendingVolume = volume;
        this.pendingAudio = encodeTodfpwm(samples);
        return true;
    }

    /**
     * Returns {@code true} when the pending batch should be dispatched now
     * (i.e. the client will exhaust its buffer within the next
     * {@value #CLIENT_BUFFER_NS} ns).
     */
    public boolean shouldSendPending(long now) {
        return pendingAudio != null && now >= clientEndTime - CLIENT_BUFFER_NS;
    }

    /**
     * Consumes the pending bytes and advances the estimated client-end time.
     *
     * @param now {@code System.nanoTime()} at the time of dispatch.
     * @return the DFPWM bytes to be sent in the packet.
     */
    public byte[] pullPending(long now) {
        byte[] data = pendingAudio;
        // Each DFPWM byte encodes 8 samples at 48 000 Hz.
        long audioDurationNs = (long) pendingAudio.length * 8L * 1_000_000_000L / 48_000L;
        clientEndTime = Math.max(clientEndTime, now) + audioDurationNs;
        pendingAudio = null;
        return data;
    }

    /**
     * Returns {@code true} if audio is either pending dispatch or the client
     * is still expected to be playing previously dispatched audio.
     */
    public boolean isActive() {
        return pendingAudio != null || System.nanoTime() < clientEndTime;
    }

    // -------------------------------------------------------------------------
    // DFPWM encoder — mirrors the Lua cc.audio.dfpwm make_encoder() spec.
    // -------------------------------------------------------------------------

    private byte[] encodeTodfpwm(int[] samples) {
        int outputLen = (samples.length + 7) / 8; // ceiling division
        byte[] output = new byte[outputLen];

        int q = encQ, s = encS, fq = encFq;

        for (int i = 0; i < outputLen; i++) {
            int d = 0;
            for (int j = 0; j < 8; j++) {
                int inp = (i * 8 + j < samples.length) ? samples[i * 8 + j] : 0;

                // Comparator: decide the output bit.
                // The -128 tie-break matches the cc.audio.dfpwm Lua spec:
                // emit 1 when level > q, or when both are at the minimum (-128).
                boolean bit = inp > q || (inp == -128 && q == -128);

                // Predictor (charge) update — delta = s >> PREC_SHIFT.
                int t = bit ? Math.min(q + (s >> PREC_SHIFT), 127) : Math.max(q - (s >> PREC_SHIFT), -128);

                // Strength adaption — increase when bit matches charge sign.
                int ds = bit == (q >= 0) ? Math.min(s + PREC_CEIL, 127 << PREC_SHIFT)
                    : Math.max(s - PREC_CEIL, PREC_CEIL);

                // LPF update (tracked for symmetry with the decoder; not used as output).
                fq += ((t - fq) * LPF_STRENGTH) >> 8;

                q = t;
                s = ds;
                d |= bit ? (1 << j) : 0;
            }
            output[i] = (byte) d;
        }

        encQ = q;
        encS = s;
        encFq = fq;
        return output;
    }
}
