package dan200.computercraft.shared.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;

public class NBTUtil {

    private static NBTBase toNBTTag(Object object) {
        if (object != null) {
            if (object instanceof Boolean) {
                boolean b = (Boolean) object;
                return new NBTTagByte((byte) (b ? 1 : 0));
            }

            if (object instanceof Number) {
                Double d = ((Number) object).doubleValue();
                return new NBTTagDouble(d);
            }

            if (object instanceof String) {
                String s = object.toString();
                return new NBTTagString(s);
            }

            if (object instanceof Map) {
                Map<Object, Object> m = (Map<Object, Object>) object;
                NBTTagCompound nbt = new NBTTagCompound();
                int i = 0;

                for (Entry<Object, Object> entry : m.entrySet()) {
                    NBTBase key = toNBTTag(entry.getKey());
                    NBTBase value = toNBTTag(entry.getKey());
                    if (key != null && value != null) {
                        nbt.setTag("k" + Integer.toString(i), key);
                        nbt.setTag("v" + Integer.toString(i), value);
                        i++;
                    }
                }

                nbt.setInteger("len", m.size());
                return nbt;
            }
        }

        return null;
    }

    public static NBTTagCompound encodeObjects(Object[] objects) {
        if (objects != null && objects.length > 0) {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setInteger("len", objects.length);

            for (int i = 0; i < objects.length; i++) {
                Object object = objects[i];
                NBTBase tag = toNBTTag(object);
                if (tag != null) {
                    nbt.setTag(Integer.toString(i), tag);
                }
            }

            return nbt;
        } else {
            return null;
        }
    }

    private static Object fromNBTTag(NBTBase tag) {
        if (tag != null) {
            byte typeID = tag.getId();
            switch (typeID) {
                case 1:
                    boolean b = ((NBTTagByte) tag).func_150290_f() > 0;
                    return b;
                case 2:
                case 3:
                case 4:
                case 5:
                case 7:
                case 9:
                default:
                    break;
                case 6:
                    double d = ((NBTTagDouble) tag).func_150286_g();
                    return d;
                case 8:
                    return ((NBTTagString) tag).func_150285_a_();
                case 10:
                    NBTTagCompound c = (NBTTagCompound) tag;
                    int len = c.getInteger("len");
                    Map<Object, Object> map = new HashMap<>(len);

                    for (int i = 0; i < len; i++) {
                        Object key = fromNBTTag(c.getTag("k" + Integer.toString(i)));
                        Object value = fromNBTTag(c.getTag("v" + Integer.toString(i)));
                        if (key != null && value != null) {
                            map.put(key, value);
                        }
                    }

                    return map;
            }
        }

        return null;
    }

    /**
     * Converts an arbitrary {@link NBTTagCompound} (e.g. from a tile entity) into a
     * Lua-compatible {@code Map<Object,Object>}. Numbers become {@code double}; strings
     * are kept as-is; lists and arrays become 1-indexed tables; nested compounds become
     * nested maps. NBT list element types that have no public getter in 1.7.10 MCP are
     * silently omitted.
     */
    public static Map<Object, Object> toObject(NBTTagCompound compound) {
        return (Map<Object, Object>) nbtTagToLua(compound);
    }

    @SuppressWarnings("unchecked")
    private static Object nbtTagToLua(NBTBase tag) {
        if (tag == null) return null;
        switch (tag.getId()) {
            case 1: // byte
                return (double) ((NBTTagByte) tag).func_150290_f();
            case 2: // short
                return (double) ((NBTTagShort) tag).func_150289_e();
            case 3: // int
                return (double) ((NBTTagInt) tag).func_150287_d();
            case 4: // long
                return (double) ((NBTTagLong) tag).func_150291_c();
            case 5: // float
                return (double) ((NBTTagFloat) tag).func_150288_h();
            case 6: // double
                return ((NBTTagDouble) tag).func_150286_g();
            case 8: // string
                return ((NBTTagString) tag).func_150285_a_();
            case 7: { // byte[]
                byte[] bytes = ((NBTTagByteArray) tag).func_150292_c();
                Map<Object, Object> arr = new HashMap<>(bytes.length);
                for (int i = 0; i < bytes.length; i++) arr.put(i + 1, (double) bytes[i]);
                return arr;
            }
            case 11: { // int[]
                int[] ints = ((NBTTagIntArray) tag).func_150302_c();
                Map<Object, Object> arr = new HashMap<>(ints.length);
                for (int i = 0; i < ints.length; i++) arr.put(i + 1, (double) ints[i]);
                return arr;
            }
            case 9: { // list
                NBTTagList list = (NBTTagList) tag;
                int count = list.tagCount();
                int listType = list.func_150303_d(); // element type ID
                Map<Object, Object> arr = new HashMap<>(count);
                for (int i = 0; i < count; i++) {
                    Object val;
                    switch (listType) {
                        case 6:
                            val = list.func_150309_d(i); // double
                            break;
                        case 5:
                            val = (double) list.func_150308_e(i); // float → double
                            break;
                        case 8:
                            val = list.getStringTagAt(i);
                            break;
                        case 10:
                            val = nbtTagToLua(list.getCompoundTagAt(i));
                            break;
                        case 11: {
                            int[] ints = list.func_150306_c(i);
                            Map<Object, Object> inner = new HashMap<>(ints.length);
                            for (int j = 0; j < ints.length; j++) inner.put(j + 1, (double) ints[j]);
                            val = inner;
                            break;
                        }
                        default:
                            val = null;
                    }
                    if (val != null) arr.put(i + 1, val);
                }
                return arr;
            }
            case 10: { // compound
                NBTTagCompound compound = (NBTTagCompound) tag;
                Set<String> keys = compound.func_150296_c();
                Map<Object, Object> map = new HashMap<>(keys.size());
                for (String key : keys) {
                    Object val = nbtTagToLua(compound.getTag(key));
                    if (val != null) map.put(key, val);
                }
                return map;
            }
            default:
                return null;
        }
    }

    public static Object[] decodeObjects(NBTTagCompound tagCompound) {
        int len = tagCompound.getInteger("len");
        if (len > 0) {
            Object[] objects = new Object[len];

            for (int i = 0; i < len; i++) {
                String key = Integer.toString(i);
                if (tagCompound.hasKey(key)) {
                    NBTBase tag = tagCompound.getTag(key);
                    objects[i] = fromNBTTag(tag);
                }
            }

            return objects;
        } else {
            return null;
        }
    }
}
