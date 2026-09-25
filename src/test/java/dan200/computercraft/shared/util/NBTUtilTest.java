package dan200.computercraft.shared.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NBTUtil} event-argument encoding/decoding, covering the
 * S3 fixes from {@code docs/CODEBASE_ANALYSIS.md}:
 *
 * <ul>
 * <li>{@code toNBTTag} must encode the map <em>value</em> (previously the key
 * was encoded as the value, silently corrupting table-valued event
 * arguments).</li>
 * <li>The stored {@code len} must count entries actually written, so
 * encode→decode round-trips are lossless even when some entries are
 * unencodable.</li>
 * <li>{@code byte[]} values must survive the round trip (previously silently
 * dropped).</li>
 * <li>{@code decodeObjects} must reject hostile {@code len} values instead of
 * allocating attacker-sized arrays.</li>
 * </ul>
 */
class NBTUtilTest {

    // =========================================================================
    // Basic encode/decode round trips
    // =========================================================================

    @Test
    void roundTripsScalarArguments() {
        Object[] args = new Object[] { true, false, 1.5, -2.0, "hello", null };

        NBTTagCompound nbt = NBTUtil.encodeObjects(args);
        Object[] decoded = NBTUtil.decodeObjects(nbt);

        assertEquals(args.length, decoded.length);
        assertEquals(Boolean.TRUE, decoded[0]);
        assertEquals(Boolean.FALSE, decoded[1]);
        assertEquals(1.5, decoded[2]);
        assertEquals(-2.0, decoded[3]);
        assertEquals("hello", decoded[4]);
        assertNull(decoded[5]);
    }

    @Test
    void encodeObjectsReturnsNullForEmptyOrNull() {
        assertNull(NBTUtil.encodeObjects(null));
        assertNull(NBTUtil.encodeObjects(new Object[0]));
        assertNull(NBTUtil.decodeObjects(new NBTTagCompound()));
    }

    // =========================================================================
    // S3 fix 1: map values (not keys) must be encoded
    // =========================================================================

    @Test
    void mapValuesAreEncodedNotKeys() {
        Map<Object, Object> table = new HashMap<>();
        table.put("a", 1.0);
        table.put("b", "two");

        Object[] args = new Object[] { table };
        NBTTagCompound nbt = NBTUtil.encodeObjects(args);
        Object[] decoded = NBTUtil.decodeObjects(nbt);

        @SuppressWarnings("unchecked")
        Map<Object, Object> decodedTable = (Map<Object, Object>) decoded[0];
        assertEquals(2, decodedTable.size());
        assertEquals(1.0, decodedTable.get("a"));
        assertEquals("two", decodedTable.get("b"));
    }

    @Test
    void nestedMapsRoundTrip() {
        Map<Object, Object> inner = new HashMap<>();
        inner.put(1.0, "x");
        inner.put("k", true);
        Map<Object, Object> outer = new HashMap<>();
        outer.put("inner", inner);
        outer.put("n", 42.0);

        Object[] args = new Object[] { outer };
        Object[] decoded = NBTUtil.decodeObjects(NBTUtil.encodeObjects(args));

        @SuppressWarnings("unchecked")
        Map<Object, Object> decodedOuter = (Map<Object, Object>) decoded[0];
        assertEquals(42.0, decodedOuter.get("n"));
        @SuppressWarnings("unchecked")
        Map<Object, Object> decodedInner = (Map<Object, Object>) decodedOuter.get("inner");
        assertEquals("x", decodedInner.get(1.0));
        assertEquals(Boolean.TRUE, decodedInner.get("k"));
    }

    // =========================================================================
    // S3 fix 2: "len" counts written entries (skipped entries don't desync)
    // =========================================================================

    @Test
    void unencodableEntriesAreSkippedWithoutDesync() {
        // A map whose value is unencodable (a raw Object) must be skipped, and
        // the remaining entries must still decode at the right indices.
        Map<Object, Object> table = new HashMap<>();
        table.put("good", 7.0);
        table.put("bad", new Object()); // unencodable value → skipped

        Object[] args = new Object[] { table };
        Object[] decoded = NBTUtil.decodeObjects(NBTUtil.encodeObjects(args));

        @SuppressWarnings("unchecked")
        Map<Object, Object> decodedTable = (Map<Object, Object>) decoded[0];
        assertEquals(1, decodedTable.size());
        assertEquals(7.0, decodedTable.get("good"));
    }

    @Test
    void unencodableKeyIsSkippedWithoutDesync() {
        Map<Object, Object> table = new HashMap<>();
        table.put(new Object(), 1.0); // unencodable key → skipped
        table.put("ok", 2.0);

        Object[] args = new Object[] { table };
        Object[] decoded = NBTUtil.decodeObjects(NBTUtil.encodeObjects(args));

        @SuppressWarnings("unchecked")
        Map<Object, Object> decodedTable = (Map<Object, Object>) decoded[0];
        assertEquals(1, decodedTable.size());
        assertEquals(2.0, decodedTable.get("ok"));
    }

    // =========================================================================
    // S3 fix 3: byte[] support
    // =========================================================================

    @Test
    void byteArraysRoundTrip() {
        byte[] data = new byte[] { 1, -2, 3, 0, 127, -128 };
        Object[] args = new Object[] { data };

        Object[] decoded = NBTUtil.decodeObjects(NBTUtil.encodeObjects(args));

        assertArrayEquals(data, (byte[]) decoded[0]);
    }

    @Test
    void byteArraysInsideMapsRoundTrip() {
        Map<Object, Object> table = new HashMap<>();
        table.put("audio", new byte[] { 5, 6, 7 });

        Object[] args = new Object[] { table };
        Object[] decoded = NBTUtil.decodeObjects(NBTUtil.encodeObjects(args));

        @SuppressWarnings("unchecked")
        Map<Object, Object> decodedTable = (Map<Object, Object>) decoded[0];
        assertArrayEquals(new byte[] { 5, 6, 7 }, (byte[]) decodedTable.get("audio"));
    }

    // =========================================================================
    // S3 fix 4: hostile "len" values must not allocate
    // =========================================================================

    @Test
    void decodeObjectsRejectsHugeLength() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("len", Integer.MAX_VALUE); // no entries present

        assertNull(NBTUtil.decodeObjects(nbt));
    }

    @Test
    void decodeObjectsRejectsNegativeLength() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("len", -100);

        assertNull(NBTUtil.decodeObjects(nbt));
    }

    @Test
    void decodeObjectsRejectsLengthAboveCap() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("len", 257); // just above MAX_DECODED_OBJECTS

        assertNull(NBTUtil.decodeObjects(nbt));
    }

    @Test
    void decodeObjectsAcceptsLengthAtCap() {
        // len == MAX_DECODED_OBJECTS (256) must still be accepted; entries
        // beyond what is present simply decode to null.
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("len", 256);
        nbt.setTag("0", new net.minecraft.nbt.NBTTagDouble(1.0));

        Object[] decoded = NBTUtil.decodeObjects(nbt);
        assertEquals(256, decoded.length);
        assertEquals(1.0, decoded[0]);
        assertNull(decoded[1]);
    }

    @Test
    void hostileMapLengthIsRejected() {
        // A crafted compound that claims a huge map "len" but contains no
        // entries must decode to null rather than looping/allocation.
        NBTTagCompound hostile = new NBTTagCompound();
        hostile.setInteger("len", Integer.MAX_VALUE);

        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("len", 1);
        nbt.setTag("0", hostile);

        Object[] decoded = NBTUtil.decodeObjects(nbt);
        assertEquals(1, decoded.length);
        assertNull(decoded[0]);
    }

    // =========================================================================
    // toObject (tile-entity NBT → Lua table) sanity
    // =========================================================================

    @Test
    void toObjectConvertsCompoundToLuaTable() {
        NBTTagCompound compound = new NBTTagCompound();
        compound.setInteger("x", 10);
        compound.setString("name", "test");
        compound.setBoolean("on", true);

        Map<Object, Object> lua = NBTUtil.toObject(compound);
        assertEquals(10.0, lua.get("x"));
        assertEquals("test", lua.get("name"));
        // toObject converts all NBT numerics (incl. boolean-backed bytes) to
        // double, per its documented contract — unlike the event path which
        // preserves booleans.
        assertEquals(1.0, lua.get("on"));
    }

    @Test
    void toObjectHandlesByteArraysAndLists() {
        NBTTagCompound compound = new NBTTagCompound();
        compound.setByteArray("data", new byte[] { 1, 2, 3 });

        Map<Object, Object> lua = NBTUtil.toObject(compound);
        @SuppressWarnings("unchecked")
        Map<Object, Object> arr = (Map<Object, Object>) lua.get("data");
        assertEquals(3, arr.size());
        assertEquals(1.0, arr.get(1));
        assertEquals(3.0, arr.get(3));
    }

    @Test
    void toObjectOfEmptyCompoundIsEmptyMap() {
        Map<Object, Object> lua = NBTUtil.toObject(new NBTTagCompound());
        assertTrue(lua.isEmpty());
    }
}
