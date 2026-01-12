package dan200.computercraft.core.lua.lib;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Converter for binary values
 */
public class BinaryConverter {

    public static Object toString(Object value) {
        return toString(value, null);
    }

    private static Object toString(Object value, Map<Object, Object> tables) {
        if (value instanceof byte[]) {
            return new String((byte[]) value);
        } else if (value instanceof Map) {
            if (tables == null) {
                tables = new IdentityHashMap<Object, Object>();
            } else {
                Object object = tables.get(value);
                if (object != null) return object;
            }

            Map<Object, Object> newMap = new HashMap<Object, Object>();
            tables.put(value, newMap);

            Map<?, ?> map = (Map) value;

            for (Object key : map.keySet()) {
                newMap.put(toString(key, tables), toString(map.get(key), tables));
            }

            return newMap;
        } else {
            return value;
        }
    }

    /**
     * Convert the arguments to use strings instead of byte arrays
     *
     * @param items The arguments to convert. This will be modified in place
     */
    public static void toStrings(Object[] items) {
        for (int i = 0; i < items.length; i++) {
            items[i] = toString(items[i], null);
        }
    }

    public static String decodeString(byte[] bytes) {
        return decodeString(bytes, 0, bytes.length);
    }

    public static String decodeString(byte[] bytes, int offset, int length) {
        char[] chars = new char[length];

        for (int i = 0; i < chars.length; ++i) {
            chars[i] = (char) (bytes[offset + i] & 255);
        }

        return new String(chars);
    }

    public static byte[] toBytes(String string) {
        byte[] chars = new byte[string.length()];

        for (int i = 0; i < chars.length; ++i) {
            char c = string.charAt(i);
            chars[i] = c < 256 ? (byte) c : 63;
        }

        return chars;
    }
}
