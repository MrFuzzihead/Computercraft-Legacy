package dan200.computercraft.shared.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagString;

public class NBTUtil {
   private static NBTBase toNBTTag(Object object) {
      if (object != null) {
         if (object instanceof Boolean) {
            boolean b = (Boolean)object;
            return new NBTTagByte((byte)(b ? 1 : 0));
         }

         if (object instanceof Number) {
            Double d = ((Number)object).doubleValue();
            return new NBTTagDouble(d);
         }

         if (object instanceof String) {
            String s = object.toString();
            return new NBTTagString(s);
         }

         if (object instanceof Map) {
            Map<Object, Object> m = (Map<Object, Object>)object;
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
               boolean b = ((NBTTagByte)tag).func_150290_f() > 0;
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
               double d = ((NBTTagDouble)tag).func_150286_g();
               return d;
            case 8:
               return ((NBTTagString)tag).func_150285_a_();
            case 10:
               NBTTagCompound c = (NBTTagCompound)tag;
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
