package dan200.computercraft.client.upload;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import dan200.computercraft.ComputerCraft;

/**
 * Picks up files dropped onto the game window, using SDL3 while lwjgl3ify is installed.
 *
 * <p>
 * CC: Tweaked receives drops from Minecraft's own drag-and-drop hook
 * ({@code Screen#onFilesDropped}). Forge 1.7.10 has no such hook and LWJGL2 has no drop callback at
 * all, but lwjgl3ify 3.x runs the client on SDL3, which does: {@code SDL_AddEventWatch} reports
 * {@code SDL_EVENT_DROP_FILE} with the dropped path. So this is the legacy equivalent of the modern
 * hook - not an approximation of it - and it degrades to nothing when lwjgl3ify is absent.
 * </p>
 *
 * <p>
 * Everything here is reflective, deliberately. Compiling against {@code org.lwjgl:lwjgl-sdl} would
 * make the mod's build depend on a repository serving a 3.4.x LWJGL that only lwjgl3ify ships, and a
 * missing class at runtime would be a hard failure rather than a missing feature. Instead the whole
 * watcher is only reached after {@code Class.forName("org.lwjgl.sdl.SDLEvents")} succeeds, and any
 * failure inside {@link #install()} disables drops with one log line.
 * </p>
 */
public final class SdlFileDropWatcher {

    private static final Queue<String> drops = new ConcurrentLinkedQueue<>();

    /** Kept alive for the lifetime of the process: the native closure holds a global ref to it. */
    private static Object filter;

    private static boolean attempted;
    private static boolean available;

    private SdlFileDropWatcher() {}

    /**
     * Registers the SDL event watch, once. Safe to call repeatedly and safe to call when lwjgl3ify is
     * not installed.
     */
    public static synchronized void install() {
        if (attempted) {
            return;
        }

        attempted = true;

        try {
            ClassLoader loader = SdlFileDropWatcher.class.getClassLoader();

            Class<?> filterInterface = Class.forName("org.lwjgl.sdl.SDL_EventFilterI", true, loader);
            Class<?> sdlEvents = Class.forName("org.lwjgl.sdl.SDLEvents", true, loader);
            Class<?> sdlEvent = Class.forName("org.lwjgl.sdl.SDL_Event", true, loader);
            Class<?> dropEvent = Class.forName("org.lwjgl.sdl.SDL_DropEvent", true, loader);

            Method ndrop = sdlEvent.getMethod("ndrop", long.class);
            Method dropType = dropEvent.getMethod("type");
            Method dropData = dropEvent.getMethod("dataString");
            int dropFileType = sdlEvents.getField("SDL_EVENT_DROP_FILE")
                .getInt(null);
            Method addEventWatch = sdlEvents.getMethod("SDL_AddEventWatch", filterInterface, long.class);
            Object descriptor = filterInterface.getField("DESCRIPTOR")
                .get(null);

            // Proxy cannot inherit a default method, and the interface defaults are exactly what we
            // want: address() creates the native upcall through the descriptor, and callback() decodes
            // the native argument block before calling invoke(). So find them rather than
            // reimplementing either.
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(filterInterface, MethodHandles.lookup());
            MethodHandle addressDefault = lookup
                .findSpecial(filterInterface, "address", MethodType.methodType(long.class), filterInterface);
            MethodHandle callbackDefault = lookup.findSpecial(
                filterInterface,
                "callback",
                MethodType.methodType(void.class, long.class, long.class),
                filterInterface);

            InvocationHandler handler = (proxy, method, args) -> {
                switch (method.getName()) {
                    case "address":
                        return addressDefault.invoke(proxy);
                    case "callback":
                        callbackDefault.invoke(proxy, args[0], args[1]);
                        return null;
                    case "invoke":
                        // invoke(userdata, event) -> never remove the watch.
                        read(ndrop, dropType, dropData, dropFileType, (Long) args[1]);
                        return Boolean.FALSE;
                    case "getDescriptor":
                        return descriptor;
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == args[0];
                    case "toString":
                        return "ComputerCraft SDL file drop filter";
                    default:
                        return null;
                }
            };

            Object proxy = Proxy
                .newProxyInstance(filterInterface.getClassLoader(), new Class<?>[] { filterInterface }, handler);
            Object registered = addEventWatch.invoke(null, proxy, 0L);
            if (!Boolean.TRUE.equals(registered)) {
                throw new IllegalStateException("SDL_AddEventWatch returned false");
            }

            filter = proxy;
            available = true;
            ComputerCraft.logger.info("File drag-and-drop is available (SDL3 via lwjgl3ify)");
        } catch (Throwable t) {
            // No lwjgl3ify, an unsupported lwjgl3ify/lwjgl combination, or a changed internal API. The
            // feature is optional, so this is information, not an error.
            available = false;
            ComputerCraft.logger.info("File drag-and-drop is unavailable: {}", t.toString());
        }
    }

    /** Whether drops can be received at all on this client. */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Takes every path dropped since the last call.
     *
     * @return The dropped paths, in drop order.
     */
    public static String[] pollDrops() {
        if (drops.isEmpty()) {
            return new String[0];
        }

        java.util.List<String> paths = new java.util.ArrayList<>();
        String path;
        while ((path = drops.poll()) != null) {
            paths.add(path);
        }

        return paths.toArray(new String[0]);
    }

    /** Discards queued drops. Used by tests. */
    public static void clear() {
        drops.clear();
    }

    private static void read(Method ndrop, Method type, Method dataString, int dropFileType, long event) {
        try {
            Object drop = ndrop.invoke(null, event);
            if (!Integer.valueOf(dropFileType)
                .equals(type.invoke(drop))) {
                return;
            }

            String path = (String) dataString.invoke(drop);
            if (path != null && !path.isEmpty()) {
                drops.add(path);
            }
        } catch (Throwable t) {
            // Never throw into native code: an exception escaping an upcall takes the game down.
        }
    }
}
