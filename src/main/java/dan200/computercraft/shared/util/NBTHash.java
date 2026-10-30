package dan200.computercraft.shared.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;

import dan200.computercraft.ComputerCraft;

/**
 * A stable hash of an item's NBT, for {@code turtle.getItemDetail} / {@code inventory.getItemDetail}.
 *
 * <h2>Why this is not byte-identical to CC:Tweaked</h2>
 * <p>
 * Upstream hashes the NBT wire format, which it can write with a canonical writer that sorts
 * compound keys. 1.7.10 has no such writer: {@code NBTBase.write(DataOutput)} is package-private
 * and {@code NBTTagList} exposes no generic element accessor, so the tag cannot be serialised from
 * outside {@code net.minecraft.nbt}. Rather than reflect into Minecraft's internals, this class
 * hashes a canonical <em>text</em> encoding of the tag:
 * </p>
 * <ul>
 * <li>compound keys are sorted, so key order does not affect the hash;</li>
 * <li>every value carries its tag type, so {@code 1b} and {@code 1} cannot collide;</li>
 * <li>list order is preserved, and lists are walked recursively via {@code copy()} +
 * {@code removeTag(0)} because there is no index accessor.</li>
 * </ul>
 * <p>
 * The contract that matters for programs is preserved: identical NBT hashes identically, and
 * different NBT does not. The digest value itself differs from a 1.20 server's, which only matters
 * if a program compares hashes across versions.
 * </p>
 */
public final class NBTHash {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private NBTHash() {}

    /**
     * The hash of the given tag, or {@code null} when there is no tag or hashing is unavailable.
     *
     * @param tag the tag to hash, may be {@code null}
     * @return a lowercase hex MD5 digest, or {@code null}
     */
    public static String of(NBTTagCompound tag) {
        if (tag == null) return null;

        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(encode(tag).getBytes(StandardCharsets.UTF_8));

            char[] hex = new char[hash.length * 2];
            for (int i = 0; i < hash.length; i++) {
                int b = hash[i] & 0xFF;
                hex[i * 2] = HEX[b >>> 4];
                hex[i * 2 + 1] = HEX[b & 0xF];
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException e) {
            ComputerCraft.logger.error("Cannot hash NBT", e);
            return null;
        }
    }

    /** Canonical, type-tagged text encoding of a tag. */
    private static String encode(NBTBase tag) {
        int id = tag.getId();
        switch (id) {
            case 1: // byte e.g. "1b"
            case 2: // short e.g. "1s"
            case 3: // int e.g. "1"
            case 4: // long e.g. "1L"
            case 5: // float e.g. "1.0f"
            case 6: // double e.g. "1.0d"
                // Minecraft's own toString() is already type-tagged and deterministic.
                return tag.toString();

            case 7: { // byte array
                byte[] bytes = ((NBTTagByteArray) tag).func_150292_c();
                StringBuilder out = new StringBuilder("[B");
                for (byte b : bytes) {
                    out.append(',')
                        .append(b);
                }
                return out.append(']')
                    .toString();
            }

            case 8: { // string
                String value = tag.toString();
                // toString() already quotes and escapes; prefix so it cannot be confused with a
                // scalar.
                return "S" + value;
            }

            case 9: { // list, walked in order
                NBTTagList list = (NBTTagList) tag;
                StringBuilder out = new StringBuilder("[L");
                out.append(list.func_150303_d())
                    .append(':');
                if (list.tagCount() > 0) {
                    // No generic accessor exists, so copy and pop. `copy()` is a deep copy, so the
                    // original tag is untouched.
                    NBTTagList remaining = (NBTTagList) list.copy();
                    boolean first = true;
                    while (remaining.tagCount() > 0) {
                        if (!first) out.append(',');
                        first = false;
                        out.append(encode(remaining.removeTag(0)));
                    }
                }
                return out.append(']')
                    .toString();
            }

            case 10: { // compound, keys sorted
                NBTTagCompound compound = (NBTTagCompound) tag;
                @SuppressWarnings("unchecked")
                Set<String> keys = compound.func_150296_c();
                List<String> sorted = new ArrayList<String>(keys);
                Collections.sort(sorted);

                StringBuilder out = new StringBuilder("{");
                boolean first = true;
                for (String key : sorted) {
                    if (!first) out.append(',');
                    first = false;
                    out.append(key)
                        .append('=')
                        .append(encode(compound.getTag(key)));
                }
                return out.append('}')
                    .toString();
            }

            case 11: { // int array
                int[] values = ((NBTTagIntArray) tag).func_150302_c();
                StringBuilder out = new StringBuilder("[I");
                for (int value : values) {
                    out.append(',')
                        .append(value);
                }
                return out.append(']')
                    .toString();
            }

            default:
                return "?" + id;
        }
    }
}
