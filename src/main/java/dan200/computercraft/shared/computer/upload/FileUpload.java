package dan200.computercraft.shared.computer.upload;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * A file on its way to a computer: a name, its bytes, and the SHA-256 digest the client computed for
 * them.
 *
 * <p>
 * Ported from CC: Tweaked's {@code dan200.computercraft.shared.computer.upload.FileUpload}
 * (MPL-2.0; see NOTICE). Upstream keeps the bytes in a {@code ByteBuffer} because its slices share
 * one; here the pieces are copied into a plain {@code byte[]} on arrival, which is simpler to
 * bounds-check against a hostile packet.
 * </p>
 */
public class FileUpload {

    /** The length of the SHA-256 digest carried alongside each file. */
    public static final int CHECKSUM_LENGTH = 32;

    private final String name;
    private final byte[] bytes;
    private final byte[] checksum;

    public FileUpload(String name, byte[] bytes, byte[] checksum) {
        this.name = name;
        this.bytes = bytes;
        this.checksum = checksum;
    }

    public String getName() {
        return this.name;
    }

    public byte[] getBytes() {
        return this.bytes;
    }

    public int getLength() {
        return this.bytes.length;
    }

    public byte[] getChecksum() {
        return this.checksum;
    }

    /**
     * Whether the received bytes still hash to the digest the client sent. This is an integrity
     * check, not a security one, so it does not need to be constant-time.
     */
    public boolean checksumMatches() {
        byte[] digest = getDigest(this.bytes);
        return digest != null && Arrays.equals(this.checksum, digest);
    }

    public static byte[] getDigest(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
