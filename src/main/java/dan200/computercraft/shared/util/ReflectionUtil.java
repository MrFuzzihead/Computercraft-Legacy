package dan200.computercraft.shared.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class ReflectionUtil {

    public static Class getOptionalClass(String name) {
        try {
            return Class.forName(name);
        } catch (Exception var2) {
            return null;
        }
    }

    public static Class getOptionalInnerClass(Class enclosingClass, String name) {
        if (enclosingClass != null) {
            try {
                Class[] declaredClasses = enclosingClass.getDeclaredClasses();
                if (declaredClasses != null) {
                    for (int i = 0; i < declaredClasses.length; i++) {
                        if (declaredClasses[i].getSimpleName()
                            .equals(name)) {
                            return declaredClasses[i];
                        }
                    }
                }
            } catch (Exception var4) {}
        }

        return null;
    }

    public static Method getOptionalMethod(Class clazz, String name, Class[] arguments) {
        if (clazz != null) {
            try {
                return clazz.getDeclaredMethod(name, arguments);
            } catch (Exception var4) {}
        }

        return null;
    }

    public static Constructor getOptionalConstructor(Class clazz, Class[] arguments) {
        if (clazz != null) {
            try {
                return clazz.getConstructor(arguments);
            } catch (Exception var3) {}
        }

        return null;
    }

    public static Field getOptionalField(Class clazz, String name) {
        if (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                if (field != null) {
                    try {
                        field.setAccessible(true);
                    } catch (Exception var4) {}
                }

                return field;
            } catch (Exception var5) {}
        }

        return null;
    }

    public static <T> T safeNew(Constructor constructor, Object[] arguments, Class<T> resultClass) {
        if (constructor != null) {
            try {
                Object result = constructor.newInstance(arguments);
                if (result != null && resultClass.isInstance(result)) {
                    return (T) result;
                }
            } catch (Exception var4) {}
        }

        return null;
    }

    public static boolean safeInstanceOf(Object object, Class clazz) {
        return clazz != null ? clazz.isInstance(object) : false;
    }

    public static void safeInvoke(Method method, Object object, Object[] arguments) {
        if (method != null) {
            try {
                if (object == null || method.getClass()
                    .isInstance(object)) {
                    method.invoke(object, arguments);
                }
            } catch (Exception var4) {}
        }
    }

    public static <T> T safeInvoke(Method method, Object object, Object[] arguments, Class<T> resultClass) {
        if (method != null) {
            try {
                if (object == null && Modifier.isStatic(method.getModifiers()) || method.getDeclaringClass()
                    .isInstance(object)) {
                    Object result = method.invoke(object, arguments);
                    if (result != null && resultClass.isInstance(result)) {
                        return (T) result;
                    }
                }
            } catch (Exception var5) {}
        }

        return null;
    }

    public static <T> T safeGet(Field field, Object object, Class<T> resultClass) {
        if (field != null) {
            try {
                if (object == null && Modifier.isStatic(field.getModifiers()) || field.getDeclaringClass()
                    .isInstance(object)) {
                    Object result = field.get(object);
                    if (result != null && resultClass.isInstance(result)) {
                        return (T) result;
                    }
                }
            } catch (Exception var4) {}
        }

        return null;
    }

    public static <T> T safeSet(Field field, Object object, T value) {
        if (field != null) {
            try {
                if (object == null || field.getClass()
                    .isInstance(object)) {
                    field.set(object, value);
                }
            } catch (Exception var4) {}
        }

        return null;
    }
}
