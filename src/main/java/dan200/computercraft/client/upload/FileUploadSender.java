package dan200.computercraft.client.upload;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.client.gui.IComputerGui;
import dan200.computercraft.shared.computer.upload.FileUpload;
import dan200.computercraft.shared.computer.upload.UploadState;
import dan200.computercraft.shared.network.FileUploadPacket;

/**
 * Turns dropped files into upload messages for whichever computer is open.
 *
 * <p>
 * Mirrors CC: Tweaked's client half ({@code AbstractComputerScreen#uploadFiles} and
 * {@code UploadFileMessage#send}, MPL-2.0; see NOTICE): read each file, digest it, refuse anything
 * over the limit, and send it split across messages.
 * </p>
 */
public final class FileUploadSender {

    /** Whether the drop hook has been attempted, and whether it worked. */
    private static boolean dropHookTried;
    private static boolean dropsAvailable;

    private FileUploadSender() {}

    /** Called once per client tick, after the screen and world have settled. */
    public static void tick(Minecraft mc) {
        if (!dropHookTried) {
            dropHookTried = true;
            dropsAvailable = installDropHook();
        }

        if (!dropsAvailable) {
            return;
        }

        String[] paths;
        try {
            paths = SdlFileDropWatcher.pollDrops();
        } catch (Throwable t) {
            // If the watcher's class could not be linked, every call into it throws - so stop asking.
            dropsAvailable = false;
            ComputerCraft.logger.error("File drag-and-drop has been disabled", t);
            return;
        }

        if (paths.length == 0) {
            return;
        }

        if (ComputerCraft.upload_size_limit <= 0) {
            ComputerCraft.logger.info("Ignoring dropped file(s): upload_size_limit is 0");
            return;
        }

        GuiScreen screen = mc.currentScreen;
        if (!(screen instanceof IComputerGui)) {
            // Drop targets are per screen, so a drop over an unrelated GUI does nothing - it must not
            // go to whatever computer happens to be behind it.
            ComputerCraft.logger.debug("Ignoring dropped file(s): no computer is open");
            return;
        }

        int instanceID = ((IComputerGui) screen).getComputerInstanceID();
        if (instanceID < 0) {
            return;
        }

        List<FileUpload> uploads;
        try {
            uploads = read(paths, ComputerCraft.upload_size_limit);
        } catch (IOException e) {
            ComputerCraft.logger.warn("Could not read dropped file(s): {}", e.getMessage());
            return;
        }

        if (uploads.isEmpty()) {
            return;
        }

        for (FileUploadPacket packet : FileUploadPacket.build(instanceID, uploads)) {
            ComputerCraft.sendRawToServer(packet);
        }
    }

    /**
     * Sets up the SDL drop hook, reporting failure instead of propagating it.
     *
     * <p>
     * The guard has to live here as well as inside the watcher: a linkage error is raised when the
     * watcher class is <em>verified</em>, before any of its own code - including its try/catch - can
     * run. That is exactly how this crashed once, on the first tick, when lwjgl3ify's namespace
     * rewrite sent {@code SDL_EventFilter} to a package that does not exist.
     * </p>
     */
    private static boolean installDropHook() {
        // Not at init: SDL only has a window to watch once the game is running, and installing
        // earlier would either fail or attach to nothing.
        try {
            SdlFileDropWatcher.install();
            return SdlFileDropWatcher.isAvailable();
        } catch (Throwable t) {
            ComputerCraft.logger.error("File drag-and-drop is unavailable", t);
            return false;
        }
    }

    /**
     * Reads the dropped paths into uploads, enforcing the size limit as it goes.
     *
     * <p>
     * Pure Java on purpose: every rejection below is a case a player can hit (a folder, a file that
     * vanished, a file too large, too many files) and can be tested without a client.
     * </p>
     *
     * @param paths   The dropped paths.
     * @param maxSize The largest total upload, in bytes. A single file may not exceed it either.
     * @return The files that will be sent. Empty if nothing usable was dropped.
     */
    public static List<FileUpload> read(String[] paths, int maxSize) throws IOException {
        List<FileUpload> uploads = new ArrayList<>();
        int total = 0;

        for (String path : paths) {
            if (uploads.size() >= UploadState.MAX_FILES) {
                throw new IOException("Too many files (at most " + UploadState.MAX_FILES + ")");
            }

            File file = new File(path);
            if (!file.isFile()) {
                // Directories are skipped rather than failing the whole drop: the OS happily hands us
                // folders, and upstream only ever transfers files too.
                continue;
            }

            long length = file.length();
            if (length > maxSize) {
                throw new IOException(file.getName() + " is larger than the upload limit (" + maxSize + " bytes)");
            }

            if (total + length > maxSize) {
                throw new IOException("The dropped files total more than the upload limit (" + maxSize + " bytes)");
            }

            byte[] bytes = Files.readAllBytes(file.toPath());
            if (total + bytes.length > maxSize) {
                throw new IOException("The dropped files total more than the upload limit (" + maxSize + " bytes)");
            }

            byte[] digest = FileUpload.getDigest(bytes);
            if (digest == null) {
                throw new IOException("Could not compute a checksum for " + file.getName());
            }

            uploads.add(new FileUpload(file.getName(), bytes, digest));
            total += bytes.length;
        }

        return uploads;
    }
}
