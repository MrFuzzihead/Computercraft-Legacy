package dan200.computercraft.client.upload;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDL_DropEvent;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_EventFilter;
import org.lwjgl.system.MemoryUtil;

import dan200.computercraft.ComputerCraft;

/**
 * Picks up files dropped onto the game window, using SDL3 while lwjgl3ify is installed.
 *
 * <p>
 * CC: Tweaked receives drops from Minecraft's own drag-and-drop hook
 * ({@code Screen#onFilesDropped}). Forge 1.7.10 has no such hook and LWJGL2 has no drop callback at
 * all, but the client runs on SDL3 through lwjgl3ify, which does: {@code SDL_AddEventWatch} reports
 * {@code SDL_EVENT_DROP_FILE} with the dropped path. So this is the legacy equivalent of the modern
 * hook - not an approximation of it.
 * </p>
 *
 * <p>
 * This compiles against {@code org.lwjgl:lwjgl-sdl} directly, which is a <em>hard</em> dependency:
 * the mod requires lwjgl3ify (see the {@code @Mod} annotation), and lwjgl3ify is what puts LWJGL3 and
 * its native libraries on the classpath. Nothing from LWJGL is shipped in this jar; it is
 * {@code compileOnly} and provided at runtime.
 * </p>
 */
public final class SdlFileDropWatcher {

    private static final Queue<String> drops = new ConcurrentLinkedQueue<>();

    /**
     * Kept for the lifetime of the process: the native closure holds a reference to it, and the
     * filter's memory is freed with it.
     */
    private static SDL_EventFilter filter;

    private static boolean installed;
    private static boolean available;

    private SdlFileDropWatcher() {}

    /**
     * Registers the SDL event watch, once. Safe to call repeatedly, and safe to call before SDL has
     * been initialised - a failure is reported once and disables drops rather than propagating into
     * the client tick.
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }

        installed = true;

        try {
            filter = new SDL_EventFilter() {

                @Override
                public boolean invoke(long userdata, long event) {
                    onEvent(event);
                    // Returning true would remove the watch, so this is always false.
                    return false;
                }
            };

            if (!SDLEvents.SDL_AddEventWatch(filter, 0L)) {
                throw new IllegalStateException("SDL_AddEventWatch returned false");
            }

            available = true;
            ComputerCraft.logger.info("File drag-and-drop is available (SDL3 via lwjgl3ify)");
        } catch (Throwable t) {
            // A broken SDL installation or an SDL that has not been initialised yet. lwjgl3ify is a
            // hard dependency, so this should not happen in a real client - but losing drops must not
            // take the game down with it.
            available = false;
            ComputerCraft.logger.error("Could not install the file drop handler", t);
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

        List<String> paths = new ArrayList<>();
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

    /** Reads one SDL event, queueing the path if it is a file drop. Runs on the SDL thread. */
    private static void onEvent(long event) {
        try {
            // This watch sees *every* event, so the type is read straight out of the union rather
            // than allocating a struct view for each mouse move and key press. SDL_Event.type is the
            // first 32-bit field, which is also SDL_DropEvent's.
            if (MemoryUtil.memGetInt(event + SDL_DropEvent.TYPE) != SDLEvents.SDL_EVENT_DROP_FILE) {
                // DROP_BEGIN/COMPLETE/POSITION carry no path; DROP_TEXT is text dragged from another
                // application, which is not a file.
                return;
            }

            String path = SDL_Event.ndrop(event)
                .dataString();
            if (path != null && !path.isEmpty()) {
                drops.add(path);
            }
        } catch (Throwable t) {
            // Never throw into native code: an exception escaping an upcall takes the game down.
        }
    }
}
