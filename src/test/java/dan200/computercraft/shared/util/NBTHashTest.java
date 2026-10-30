package dan200.computercraft.shared.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link NBTHash}.
 *
 * <p>
 * The properties that matter for programs are pinned here: identical NBT hashes identically,
 * compound key order does not change the hash, and different values or tag types do not collide.
 * The digest is deliberately not compared to CC:Tweaked's, because 1.7.10 cannot serialise NBT
 * canonically (see the class javadoc); that trade-off is documented rather than tested.
 * </p>
 */
class NBTHashTest {

    private static NBTTagCompound compound(String... keysAndValues) {
        NBTTagCompound tag = new NBTTagCompound();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            tag.setString(keysAndValues[i], keysAndValues[i + 1]);
        }
        return tag;
    }

    @Test
    void noTagHasNoHash() {
        assertNull(NBTHash.of(null));
    }

    @Test
    void anEmptyTagStillHashes() {
        String hash = NBTHash.of(new NBTTagCompound());
        assertNotNull(hash, "an empty tag should hash rather than return null");
        assertEquals(32, hash.length(), "MD5 is 32 hex characters");
    }

    @Test
    void hashesAreLowercaseHex() {
        String hash = NBTHash.of(compound("a", "b"));
        assertNotNull(hash);
        assertTrue(hash.matches("[0-9a-f]{32}"), "expected lowercase hex, got: " + hash);
    }

    @Test
    void otheSameContentHashesIdentically() {
        assertEquals(NBTHash.of(compound("a", "1", "b", "2")), NBTHash.of(compound("a", "1", "b", "2")));
    }

    @Test
    void keyOrderDoesNotChangeTheHash() {
        // The whole point of sorting: NBT loaded from disk may order keys differently.
        assertEquals(NBTHash.of(compound("a", "1", "b", "2")), NBTHash.of(compound("b", "2", "a", "1")));
    }

    @Test
    void differentValuesHashDifferently() {
        assertNotEquals(NBTHash.of(compound("a", "1")), NBTHash.of(compound("a", "2")));
    }

    @Test
    void differentKeysHashDifferently() {
        assertNotEquals(NBTHash.of(compound("a", "1")), NBTHash.of(compound("b", "1")));
    }

    @Test
    void tagTypesAreDistinguished() {
        NBTTagCompound asString = new NBTTagCompound();
        asString.setString("a", "1");

        NBTTagCompound asInt = new NBTTagCompound();
        asInt.setInteger("a", 1);

        assertNotEquals(NBTHash.of(asString), NBTHash.of(asInt), "1 as a string and 1 as an int must not collide");
    }

    @Test
    void nestedCompoundsAreSortedToo() {
        NBTTagCompound outer1 = new NBTTagCompound();
        NBTTagCompound inner1 = new NBTTagCompound();
        inner1.setString("x", "1");
        inner1.setString("y", "2");
        outer1.setTag("display", inner1);

        NBTTagCompound outer2 = new NBTTagCompound();
        NBTTagCompound inner2 = new NBTTagCompound();
        inner2.setString("y", "2");
        inner2.setString("x", "1");
        outer2.setTag("display", inner2);

        assertEquals(NBTHash.of(outer1), NBTHash.of(outer2), "inner key order must not matter either");
    }

    @Test
    void listOrderIsPreserved() {
        NBTTagList one = new NBTTagList();
        one.appendTag(new NBTTagString("a"));
        one.appendTag(new NBTTagString("b"));

        NBTTagList two = new NBTTagList();
        two.appendTag(new NBTTagString("b"));
        two.appendTag(new NBTTagString("a"));

        NBTTagCompound left = new NBTTagCompound();
        left.setTag("lore", one);
        NBTTagCompound right = new NBTTagCompound();
        right.setTag("lore", two);

        assertNotEquals(NBTHash.of(left), NBTHash.of(right), "list order is meaningful");
    }

    @Test
    void nestedListsHashStably() {
        NBTTagList inner = new NBTTagList();
        inner.appendTag(new NBTTagString("x"));

        NBTTagList outer = new NBTTagList();
        outer.appendTag(inner);
        outer.appendTag(new NBTTagString("y"));

        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("l", outer);

        assertEquals(NBTHash.of(tag), NBTHash.of(tag), "repeated calls must agree");
        assertNotNull(NBTHash.of(tag));
    }

    @Test
    void hashingDoesNotMutateTheTag() {
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagString("a"));
        list.appendTag(new NBTTagString("b"));

        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("l", list);

        NBTHash.of(tag);

        NBTTagList after = tag.getTagList("l", 8);
        assertEquals(2, after.tagCount(), "walking the list must not consume it");
        assertEquals("a", after.getStringTagAt(0));
        assertEquals("b", after.getStringTagAt(1));
    }
}
