package dan200.computercraft.core.filesystem;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Pattern;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.core.lua.binfs.LuaExceptionStub;

public class FileSystem {

    private Map<String, FileSystem.MountWrapper> m_mounts = new HashMap<>();
    private Set<IMountedFile> m_openFiles = new HashSet<>();
    private int openFilesCount;

    public FileSystem(String rootLabel, IMount rootMount) throws FileSystemException {
        this.mount(rootLabel, "", rootMount);
    }

    public FileSystem(String rootLabel, IWritableMount rootMount) throws FileSystemException {
        this.mountWritable(rootLabel, "", rootMount);
    }

    public void unload() {
        synchronized (this.m_openFiles) {
            while (this.m_openFiles.size() > 0) {
                IMountedFile file = this.m_openFiles.iterator()
                    .next();

                try {
                    file.close();
                } catch (IOException var5) {
                    this.m_openFiles.remove(file);
                }
            }
        }
    }

    public synchronized void mount(String label, String location, IMount mount) throws FileSystemException {
        if (mount == null) {
            throw new NullPointerException();
        } else {
            location = sanitizePath(location);
            if (location.indexOf("..") != -1) {
                throw new FileSystemException("Cannot mount below the root");
            } else {
                this.mount(new FileSystem.MountWrapper(label, location, mount));
            }
        }
    }

    public synchronized void mountWritable(String label, String location, IWritableMount mount)
        throws FileSystemException {
        if (mount == null) {
            throw new NullPointerException();
        } else {
            location = sanitizePath(location);
            if (location.contains("..")) {
                throw new FileSystemException("Cannot mount below the root");
            } else {
                this.mount(new FileSystem.MountWrapper(label, location, mount));
            }
        }
    }

    private synchronized void mount(FileSystem.MountWrapper wrapper) throws FileSystemException {
        String location = wrapper.getLocation();
        if (this.m_mounts.containsKey(location)) {
            this.m_mounts.remove(location);
        }

        this.m_mounts.put(location, wrapper);
    }

    public synchronized void unmount(String path) {
        path = sanitizePath(path);
        if (this.m_mounts.containsKey(path)) {
            this.m_mounts.remove(path);
        }
    }

    public synchronized String combine(String path, String childPath) {
        path = sanitizePath(path, true);
        childPath = sanitizePath(childPath, true);
        if (path.isEmpty()) {
            return childPath;
        } else {
            return childPath.isEmpty() ? path : sanitizePath(path + '/' + childPath, true);
        }
    }

    public static String getDirectory(String path) {
        path = sanitizePath(path, true);
        if (path.isEmpty()) {
            return "..";
        } else {
            int lastSlash = path.lastIndexOf(47);
            return lastSlash >= 0 ? path.substring(0, lastSlash) : "";
        }
    }

    public static String getName(String path) {
        path = sanitizePath(path, true);
        if (path.isEmpty()) {
            return "root";
        } else {
            int lastSlash = path.lastIndexOf(47);
            return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        }
    }

    public synchronized long getSize(String path) throws FileSystemException {
        path = sanitizePath(path);
        FileSystem.MountWrapper mount = this.getMount(path);
        return mount.getSize(path);
    }

    public synchronized String[] list(String path) throws FileSystemException {
        path = sanitizePath(path);
        FileSystem.MountWrapper mount = this.getMount(path);
        List<String> list = new ArrayList<>();
        mount.list(path, list);

        for (FileSystem.MountWrapper otherMount : this.m_mounts.values()) {
            if (getDirectory(otherMount.getLocation()).equals(path)) {
                list.add(getName(otherMount.getLocation()));
            }
        }

        String[] array = new String[list.size()];
        list.toArray(array);
        return array;
    }

    private void findIn(String dir, List<String> matches, Pattern wildPattern) throws FileSystemException {
        String[] list = this.list(dir);

        for (int i = 0; i < list.length; i++) {
            String entry = list[i];
            String entryPath = dir.isEmpty() ? entry : dir + "/" + entry;
            if (wildPattern.matcher(entryPath)
                .matches()) {
                matches.add(entryPath);
            }

            if (this.isDir(entryPath)) {
                this.findIn(entryPath, matches, wildPattern);
            }
        }
    }

    public synchronized String[] find(String wildPath) throws FileSystemException {
        wildPath = sanitizePath(wildPath, true);
        // Convert wildcard globs to a regex: '*' → any sequence, '?' → exactly one non-separator char.
        String regexBody = wildPath.replaceAll("\\*", "\\\\E[^\\\\/]*\\\\Q")
            .replaceAll("\\?", "\\\\E[^\\\\/]\\\\Q");
        Pattern wildPattern = Pattern.compile("^\\Q" + regexBody + "\\E$");
        List<String> matches = new ArrayList<>();
        this.findIn("", matches, wildPattern);
        String[] array = new String[matches.size()];
        matches.toArray(array);
        return array;
    }

    public synchronized boolean exists(String path) throws FileSystemException {
        path = sanitizePath(path);
        FileSystem.MountWrapper mount = this.getMount(path);
        return mount.exists(path);
    }

    public synchronized boolean isDir(String path) throws FileSystemException {
        path = sanitizePath(path);
        FileSystem.MountWrapper mount = this.getMount(path);
        return mount.isDirectory(path);
    }

    public synchronized boolean isReadOnly(String path) throws FileSystemException {
        path = sanitizePath(path);
        FileSystem.MountWrapper mount = this.getMount(path);
        return mount.isReadOnly(path);
    }

    public synchronized String getMountLabel(String path) throws FileSystemException {
        path = sanitizePath(path);
        FileSystem.MountWrapper mount = this.getMount(path);
        return mount.getLabel();
    }

    public synchronized void makeDir(String path) throws FileSystemException {
        path = sanitizePath(path);
        FileSystem.MountWrapper mount = this.getMount(path);
        mount.makeDirectory(path);
    }

    public synchronized void delete(String path) throws FileSystemException {
        path = sanitizePath(path);
        FileSystem.MountWrapper mount = this.getMount(path);
        mount.delete(path);
    }

    public synchronized void move(String sourcePath, String destPath) throws FileSystemException {
        sourcePath = sanitizePath(sourcePath);
        destPath = sanitizePath(destPath);
        if (this.isReadOnly(sourcePath) || this.isReadOnly(destPath)) {
            throw new FileSystemException("Access denied");
        } else if (!this.exists(sourcePath)) {
            throw new FileSystemException("No such file");
        } else if (this.exists(destPath)) {
            throw new FileSystemException("File exists");
        } else if (contains(sourcePath, destPath)) {
            throw new FileSystemException("Can't move a directory inside itself");
        } else {
            this.copy(sourcePath, destPath);
            this.delete(sourcePath);
        }
    }

    public synchronized void copy(String sourcePath, String destPath) throws FileSystemException {
        sourcePath = sanitizePath(sourcePath);
        destPath = sanitizePath(destPath);
        if (this.isReadOnly(destPath)) {
            throw new FileSystemException("Access denied");
        } else if (!this.exists(sourcePath)) {
            throw new FileSystemException("No such file");
        } else if (this.exists(destPath)) {
            throw new FileSystemException("File exists");
        } else if (contains(sourcePath, destPath)) {
            throw new FileSystemException("Can't copy a directory inside itself");
        } else {
            this.copyRecursive(sourcePath, this.getMount(sourcePath), destPath, this.getMount(destPath));
        }
    }

    private synchronized void copyRecursive(String sourcePath, FileSystem.MountWrapper sourceMount,
        String destinationPath, FileSystem.MountWrapper destinationMount) throws FileSystemException {
        if (sourceMount.exists(sourcePath)) {
            if (sourceMount.isDirectory(sourcePath)) {
                destinationMount.makeDirectory(destinationPath);
                List<String> sourceChildren = new ArrayList<>();
                sourceMount.list(sourcePath, sourceChildren);

                for (String child : sourceChildren) {
                    this.copyRecursive(
                        this.combine(sourcePath, child),
                        sourceMount,
                        this.combine(destinationPath, child),
                        destinationMount);
                }
            } else {
                InputStream source = null;
                OutputStream destination = null;

                try {
                    source = sourceMount.openForRead(sourcePath);
                    destination = destinationMount.openForWrite(destinationPath);
                    byte[] buffer = new byte[1024];

                    while (true) {
                        int bytesRead = source.read(buffer);
                        if (bytesRead < 0) {
                            break;
                        }

                        destination.write(buffer, 0, bytesRead);
                    }
                } catch (IOException var19) {
                    throw new FileSystemException(var19.getMessage());
                } finally {
                    if (source != null) {
                        try {
                            source.close();
                        } catch (IOException var18) {}
                    }

                    if (destination != null) {
                        try {
                            destination.close();
                        } catch (IOException var17) {}
                    }
                }
            }
        }
    }

    public synchronized IMountedFileNormal openForRead(String path) throws FileSystemException {
        path = sanitizePath(path);
        MountWrapper mount = getMount(path);
        InputStream stream = mount.openForRead(path);
        if (stream != null) {
            // Wrap with PushbackInputStream(BufferedInputStream) so that \r\n handling never
            // needs mark/reset (which BufferedInputStream can invalidate at buffer boundaries).
            final PushbackInputStream reader = new PushbackInputStream(new BufferedInputStream(stream));
            IMountedFileNormal file = new IMountedFileNormal() {

                @Override
                public byte[] readLine() throws IOException {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
                    int val;
                    while ((val = reader.read()) != -1) {
                        if (val == '\r') {
                            // Peek at the next byte to consume a \r\n pair.
                            int next = reader.read();
                            if (next != '\n' && next != -1) {
                                // Not a \n — push it back so the next readLine() sees it.
                                reader.unread(next);
                            }
                            return buffer.toByteArray();
                        } else if (val == '\n') {
                            return buffer.toByteArray();
                        } else {
                            buffer.write(val);
                        }
                    }
                    // Reached EOF — return remaining content if any, otherwise null.
                    return buffer.size() > 0 ? buffer.toByteArray() : null;
                }

                @Override
                public byte[] readAll() throws IOException {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
                    int nRead;
                    byte[] data = new byte[1024];
                    while ((nRead = reader.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }

                    return buffer.toByteArray();
                }

                @Override
                public void write(byte[] data, int start, int length, boolean newLine) throws IOException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void close() throws IOException {
                    removeFile(this, reader);
                }

                @Override
                public void flush() throws IOException {
                    throw new UnsupportedOperationException();
                }
            };
            addFile(file);
            return file;
        }
        return null;
    }

    public synchronized IMountedFileNormal openForWrite(String path, boolean append) throws FileSystemException {
        path = sanitizePath(path);
        MountWrapper mount = getMount(path);
        OutputStream stream = append ? mount.openForAppend(path) : mount.openForWrite(path);
        if (stream != null) {
            final BufferedOutputStream writer = new BufferedOutputStream(stream);
            IMountedFileNormal file = new IMountedFileNormal() {

                @Override
                public byte[] readLine() throws IOException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public byte[] readAll() throws IOException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void write(byte[] data, int start, int length, boolean newLine) throws IOException {
                    writer.write(data, start, length);
                    if (newLine) writer.write('\n');
                }

                @Override
                public void close() throws IOException {
                    removeFile(this, writer);
                }

                @Override
                public void flush() throws IOException {
                    writer.flush();
                }
            };
            addFile(file);
            return file;
        }
        return null;
    }

    public synchronized IMountedFileBinary openForBinaryRead(String path) throws FileSystemException {
        path = sanitizePath(path);
        MountWrapper mount = this.getMount(path);
        final InputStream stream = mount.openForRead(path);
        if (stream != null) {
            IMountedFileBinary file = new IMountedFileBinary() {

                public int read() throws IOException {
                    return stream.read();
                }

                public void write(int i) throws IOException {
                    throw new UnsupportedOperationException();
                }

                public void close() throws IOException {
                    removeFile(this, stream);
                }

                public void flush() throws IOException {
                    throw new UnsupportedOperationException();
                }
            };
            addFile(file);
            return file;
        } else {
            return null;
        }
    }

    public synchronized IMountedFileBinary openForBinaryWrite(String path, boolean append) throws FileSystemException {
        path = sanitizePath(path);
        MountWrapper mount = this.getMount(path);
        final OutputStream stream = append ? mount.openForAppend(path) : mount.openForWrite(path);
        if (stream != null) {
            IMountedFileBinary file = new IMountedFileBinary() {

                public int read() throws IOException {
                    throw new UnsupportedOperationException();
                }

                public void write(int i) throws IOException {
                    stream.write(i);
                }

                public void close() throws IOException {
                    removeFile(this, stream);
                }

                public void flush() throws IOException {
                    stream.flush();
                }
            };
            addFile(file);
            return file;
        } else {
            return null;
        }
    }

    public synchronized IMountedFileReadWrite openForReadWrite(String path, boolean truncate)
        throws FileSystemException {
        path = sanitizePath(path);
        MountWrapper mount = getMount(path);
        final RandomAccessFile raf = mount.openForReadWrite(path, truncate);
        if (raf == null) {
            return null;
        }
        IMountedFileReadWrite file = new IMountedFileReadWrite() {

            @Override
            public byte[] readLine() throws IOException {
                return readLine(false);
            }

            @Override
            public byte[] readLine(boolean withTrailing) throws IOException {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
                int val;
                while ((val = raf.read()) != -1) {
                    if (val == '\r') {
                        long pos = raf.getFilePointer();
                        int next = raf.read();
                        if (next != '\n' && next != -1) {
                            raf.seek(pos);
                        }
                        if (withTrailing) buffer.write('\n');
                        return buffer.toByteArray();
                    } else if (val == '\n') {
                        if (withTrailing) buffer.write('\n');
                        return buffer.toByteArray();
                    } else {
                        buffer.write(val);
                    }
                }
                return buffer.size() > 0 ? buffer.toByteArray() : null;
            }

            @Override
            public byte[] read(int count) throws IOException {
                if (count <= 0) return new byte[0];
                byte[] buf = new byte[count];
                int n = raf.read(buf, 0, count);
                if (n == -1) return null;
                return n == count ? buf : Arrays.copyOf(buf, n);
            }

            @Override
            public byte[] readAll() throws IOException {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
                int nRead;
                byte[] data = new byte[1024];
                while ((nRead = raf.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                return buffer.toByteArray();
            }

            @Override
            public void write(byte[] data, int start, int length, boolean newLine) throws IOException {
                raf.write(data, start, length);
                if (newLine) raf.write('\n');
            }

            @Override
            public long seek(String whence, long offset) throws IOException {
                long newPos;
                switch (whence) {
                    case "set":
                        newPos = offset;
                        break;
                    case "cur":
                        newPos = raf.getFilePointer() + offset;
                        break;
                    case "end":
                        newPos = raf.length() + offset;
                        break;
                    default:
                        throw new IOException("Invalid whence value");
                }
                if (newPos < 0) throw new IOException("Cannot seek before the beginning of the file");
                raf.seek(newPos);
                return newPos;
            }

            @Override
            public void flush() throws IOException {
                // RandomAccessFile writes are unbuffered; nothing to flush
            }

            @Override
            public void close() throws IOException {
                removeFile(this, raf);
            }
        };
        addFile(file);
        return file;
    }

    /**
     * Open a file for seekable text reading (mode {@code "r"}).
     *
     * <p>
     * Attempts to obtain a {@link RandomAccessFile} from the underlying mount for full seek
     * support. If the mount does not support random access (e.g. JarMount / ROM), falls back to
     * a stream-backed implementation; in that case the returned handle exposes
     * {@code readLine(boolean)}, {@code read(int)}, and {@code readAll()} but {@code seek} will
     * return {@code nil, msg} from Lua.
     * </p>
     *
     * @return the file handle, or {@code null} if the file does not exist
     */
    public synchronized IMountedFileNormal openForReadSeekable(String path) throws FileSystemException {
        path = sanitizePath(path);
        MountWrapper mount = getMount(path);

        // Attempt RAF (seek-capable) path first.
        RandomAccessFile raf = mount.openForReadRandom(path); // null → mount doesn't support RAF
        if (raf != null) {
            final RandomAccessFile rafFinal = raf;
            IMountedFileNormal file = new IMountedFileNormal() {

                @Override
                public byte[] readLine() throws IOException {
                    return readLine(false);
                }

                @Override
                public byte[] readLine(boolean withTrailing) throws IOException {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
                    int val;
                    while ((val = rafFinal.read()) != -1) {
                        if (val == '\r') {
                            long pos = rafFinal.getFilePointer();
                            int next = rafFinal.read();
                            if (next != '\n' && next != -1) {
                                rafFinal.seek(pos);
                            }
                            if (withTrailing) buffer.write('\n');
                            return buffer.toByteArray();
                        } else if (val == '\n') {
                            if (withTrailing) buffer.write('\n');
                            return buffer.toByteArray();
                        } else {
                            buffer.write(val);
                        }
                    }
                    return buffer.size() > 0 ? buffer.toByteArray() : null;
                }

                @Override
                public byte[] read(int count) throws IOException {
                    if (count <= 0) return new byte[0];
                    byte[] buf = new byte[count];
                    int n = rafFinal.read(buf, 0, count);
                    if (n == -1) return null;
                    return n == count ? buf : Arrays.copyOf(buf, n);
                }

                @Override
                public byte[] readAll() throws IOException {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
                    int nRead;
                    byte[] data = new byte[1024];
                    while ((nRead = rafFinal.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                    return buffer.toByteArray();
                }

                @Override
                public long seek(String whence, long offset) throws IOException {
                    long newPos;
                    switch (whence) {
                        case "set":
                            newPos = offset;
                            break;
                        case "cur":
                            newPos = rafFinal.getFilePointer() + offset;
                            break;
                        case "end":
                            newPos = rafFinal.length() + offset;
                            break;
                        default:
                            throw new IOException("Invalid whence value");
                    }
                    if (newPos < 0) throw new IOException("Cannot seek before the beginning of the file");
                    rafFinal.seek(newPos);
                    return newPos;
                }

                @Override
                public void write(byte[] data, int start, int length, boolean newLine) throws IOException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void flush() throws IOException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void close() throws IOException {
                    removeFile(this, rafFinal);
                }
            };
            addFile(file);
            return file;
        }

        // Fall back to stream-based reading (seek not supported by this mount).
        InputStream stream = mount.openForRead(path);
        if (stream == null) return null;
        final PushbackInputStream reader = new PushbackInputStream(new BufferedInputStream(stream));
        IMountedFileNormal file = new IMountedFileNormal() {

            @Override
            public byte[] readLine() throws IOException {
                return readLine(false);
            }

            @Override
            public byte[] readLine(boolean withTrailing) throws IOException {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
                int val;
                while ((val = reader.read()) != -1) {
                    if (val == '\r') {
                        int next = reader.read();
                        if (next != '\n' && next != -1) {
                            reader.unread(next);
                        }
                        if (withTrailing) buffer.write('\n');
                        return buffer.toByteArray();
                    } else if (val == '\n') {
                        if (withTrailing) buffer.write('\n');
                        return buffer.toByteArray();
                    } else {
                        buffer.write(val);
                    }
                }
                return buffer.size() > 0 ? buffer.toByteArray() : null;
            }

            @Override
            public byte[] read(int count) throws IOException {
                if (count <= 0) return new byte[0];
                byte[] buf = new byte[count];
                int n = reader.read(buf, 0, count);
                if (n == -1) return null;
                return n == count ? buf : Arrays.copyOf(buf, n);
            }

            @Override
            public byte[] readAll() throws IOException {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
                int nRead;
                byte[] data = new byte[1024];
                while ((nRead = reader.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                return buffer.toByteArray();
            }

            @Override
            public void write(byte[] data, int start, int length, boolean newLine) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void flush() throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() throws IOException {
                removeFile(this, reader);
            }
            // seek() inherits the default: throws IOException("seek not supported by this handle")
        };
        addFile(file);
        return file;
    }

    /**
     * Open a file for seekable text writing (mode {@code "w"} or {@code "a"}).
     *
     * <p>
     * The returned handle is backed by a {@link RandomAccessFile} and exposes
     * {@code write}, {@code seek}, {@code flush}, and {@code close}. Read methods
     * throw {@link UnsupportedOperationException}.
     * </p>
     *
     * @param append {@code true} for append mode ({@code "a"}), {@code false} for truncate ({@code "w"})
     * @return the file handle (never {@code null}; throws on error)
     */
    public synchronized IMountedFileNormal openForWriteSeekable(String path, boolean append)
        throws FileSystemException {
        path = sanitizePath(path);
        MountWrapper mount = getMount(path);
        final RandomAccessFile raf = mount.openForWriteRandom(path, append);
        IMountedFileNormal file = new IMountedFileNormal() {

            @Override
            public byte[] readLine() throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public byte[] readAll() throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void write(byte[] data, int start, int length, boolean newLine) throws IOException {
                raf.write(data, start, length);
                if (newLine) raf.write('\n');
            }

            @Override
            public long seek(String whence, long offset) throws IOException {
                long newPos;
                switch (whence) {
                    case "set":
                        newPos = offset;
                        break;
                    case "cur":
                        newPos = raf.getFilePointer() + offset;
                        break;
                    case "end":
                        newPos = raf.length() + offset;
                        break;
                    default:
                        throw new IOException("Invalid whence value");
                }
                if (newPos < 0) throw new IOException("Cannot seek before the beginning of the file");
                raf.seek(newPos);
                return newPos;
            }

            @Override
            public void flush() throws IOException {
                // RandomAccessFile writes are unbuffered; no-op
            }

            @Override
            public void close() throws IOException {
                removeFile(this, raf);
            }
        };
        addFile(file);
        return file;
    }

    public long getFreeSpace(String path) throws FileSystemException {
        path = sanitizePath(path);
        FileSystem.MountWrapper mount = this.getMount(path);
        return mount.getFreeSpace();
    }

    public synchronized long getCapacity(String path) throws FileSystemException {
        path = sanitizePath(path);
        MountWrapper mount = this.getMount(path);
        return mount.getCapacity();
    }

    public synchronized Map<String, Object> getAttributes(String path) throws FileSystemException {
        path = sanitizePath(path);
        MountWrapper mount = this.getMount(path);
        if (!mount.exists(path)) {
            throw new FileSystemException("No such file");
        }
        boolean isDir = mount.isDirectory(path);
        long size = isDir ? 0L : mount.getSize(path);
        boolean isReadOnly = mount.isReadOnly(path);
        long created = mount.getCreationTime(path);
        long modified = mount.getLastModified(path);

        Map<String, Object> result = new HashMap<>();
        result.put("size", size);
        result.put("isDir", isDir);
        result.put("isReadOnly", isReadOnly);
        result.put("created", created);
        result.put("modified", modified);
        result.put("modification", modified);
        return result;
    }

    /**
     * Returns {@code true} if {@code path} is the mount point of a drive (i.e. a path that has been
     * passed to {@link #mount} or {@link #mountWritable}). The root {@code ""} is always a drive root.
     *
     * @param path the path to test (will be sanitised)
     * @return {@code true} if the path is a drive-root mount point
     */
    public synchronized boolean isDriveRoot(String path) {
        path = sanitizePath(path);
        return m_mounts.containsKey(path);
    }

    private FileSystem.MountWrapper getMount(String path) throws FileSystemException {
        Iterator<FileSystem.MountWrapper> it = this.m_mounts.values()
            .iterator();
        FileSystem.MountWrapper match = null;
        int matchLength = 999;

        while (it.hasNext()) {
            FileSystem.MountWrapper mount = it.next();
            if (contains(mount.getLocation(), path)) {
                int len = toLocal(path, mount.getLocation()).length();
                if (match == null || len < matchLength) {
                    match = mount;
                    matchLength = len;
                }
            }
        }

        if (match == null) {
            throw new FileSystemException("Invalid Path");
        } else {
            return match;
        }
    }

    private static String sanitizePath(String path) {
        return sanitizePath(path, false);
    }

    private static String sanitizePath(String path, boolean allowWildcards) {
        path = path.replace('\\', '/');
        // When allowWildcards is true, '?' is also permitted as a single-character wildcard.
        char[] specialChars = allowWildcards ? new char[] { '"', ':', '<', '>', '|' }
            : new char[] { '"', ':', '<', '>', '?', '|' };
        StringBuilder cleanName = new StringBuilder();

        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c >= ' ' && Arrays.binarySearch(specialChars, c) < 0 && (allowWildcards || c != '*')) {
                cleanName.append(c);
            }
        }

        path = cleanName.toString();
        String[] parts = path.split("/");
        Stack<String> outputParts = new Stack<>();

        for (int n = 0; n < parts.length; n++) {
            String part = parts[n];
            if (part.length() != 0 && !part.equals(".")) {
                if (part.equals("..")) {
                    if (!outputParts.empty()) {
                        String top = outputParts.peek();
                        if (!top.equals("..")) {
                            outputParts.pop();
                        } else {
                            outputParts.push("..");
                        }
                    } else {
                        outputParts.push("..");
                    }
                } else if (part.length() >= 255) {
                    outputParts.push(part.substring(0, 255));
                } else {
                    outputParts.push(part);
                }
            }
        }

        StringBuilder result = new StringBuilder("");
        Iterator<String> it = outputParts.iterator();

        while (it.hasNext()) {
            String part = it.next();
            result.append(part);
            if (it.hasNext()) {
                result.append('/');
            }
        }

        return result.toString();
    }

    public static boolean contains(String pathA, String pathB) {
        pathA = sanitizePath(pathA);
        pathB = sanitizePath(pathB);
        if (pathB.equals("..")) {
            return false;
        } else if (pathB.startsWith("../")) {
            return false;
        } else if (pathB.equals(pathA)) {
            return true;
        } else {
            return pathA.isEmpty() ? true : pathB.startsWith(pathA + "/");
        }
    }

    public static String toLocal(String path, String location) {
        path = sanitizePath(path);
        location = sanitizePath(location);

        assert contains(location, path);

        String local = path.substring(location.length());
        return local.startsWith("/") ? local.substring(1) : local;
    }

    public void addFile(IMountedFile file) {
        synchronized (m_openFiles) {
            m_openFiles.add(file);
            if (++openFilesCount > ComputerCraft.maxFilesHandles) {
                // Ensure that we aren't over the open file limit
                // We throw Lua exceptions as FileSystemExceptions won't be handled by fs.open
                try {
                    file.close();
                } catch (IOException e) {
                    throw new LuaExceptionStub("Too many file handles: " + e.getMessage());
                }
                throw new LuaExceptionStub("Too many file handles");
            }
        }
    }

    public void removeFile(IMountedFile file, Closeable stream) throws IOException {
        synchronized (m_openFiles) {
            m_openFiles.remove(file);
            openFilesCount--;

            stream.close();
        }
    }

    private class MountWrapper {

        private String m_label;
        private String m_location;
        private IMount m_mount;
        private IWritableMount m_writableMount;

        public MountWrapper(String label, String location, IMount mount) {
            this.m_label = label;
            this.m_location = location;
            this.m_mount = mount;
            this.m_writableMount = null;
        }

        public MountWrapper(String label, String location, IWritableMount mount) {
            this(label, location, (IMount) mount);
            this.m_writableMount = mount;
        }

        public String getLabel() {
            return this.m_label;
        }

        public String getLocation() {
            return this.m_location;
        }

        public long getFreeSpace() {
            if (this.m_writableMount == null) {
                return 0L;
            } else {
                try {
                    return this.m_writableMount.getRemainingSpace();
                } catch (IOException var2) {
                    return 0L;
                }
            }
        }

        public long getCapacity() {
            if (this.m_writableMount == null) {
                return -1L;
            }
            try {
                return this.m_writableMount.getCapacity();
            } catch (IOException e) {
                return -1L;
            }
        }

        public long getCreationTime(String path) throws FileSystemException {
            path = this.toLocal(path);
            try {
                return this.m_mount.getCreationTime(path);
            } catch (IOException e) {
                throw new FileSystemException(e.getMessage());
            }
        }

        public long getLastModified(String path) throws FileSystemException {
            path = this.toLocal(path);
            try {
                return this.m_mount.getLastModified(path);
            } catch (IOException e) {
                throw new FileSystemException(e.getMessage());
            }
        }

        public boolean isReadOnly(String path) throws FileSystemException {
            return this.m_writableMount == null;
        }

        public boolean exists(String path) throws FileSystemException {
            path = this.toLocal(path);

            try {
                return this.m_mount.exists(path);
            } catch (IOException var3) {
                throw new FileSystemException(var3.getMessage());
            }
        }

        public boolean isDirectory(String path) throws FileSystemException {
            path = this.toLocal(path);

            try {
                return this.m_mount.exists(path) && this.m_mount.isDirectory(path);
            } catch (IOException var3) {
                throw new FileSystemException(var3.getMessage());
            }
        }

        public void list(String path, List<String> contents) throws FileSystemException {
            path = this.toLocal(path);

            try {
                if (this.m_mount.exists(path) && this.m_mount.isDirectory(path)) {
                    this.m_mount.list(path, contents);
                } else {
                    throw new FileSystemException("Not a directory");
                }
            } catch (IOException var4) {
                throw new FileSystemException(var4.getMessage());
            }
        }

        public long getSize(String path) throws FileSystemException {
            path = this.toLocal(path);

            try {
                if (this.m_mount.exists(path)) {
                    return this.m_mount.isDirectory(path) ? 0L : this.m_mount.getSize(path);
                } else {
                    throw new FileSystemException("No such file");
                }
            } catch (IOException var3) {
                throw new FileSystemException(var3.getMessage());
            }
        }

        public InputStream openForRead(String path) throws FileSystemException {
            path = this.toLocal(path);

            try {
                if (this.m_mount.exists(path) && !this.m_mount.isDirectory(path)) {
                    return this.m_mount.openForRead(path);
                } else {
                    throw new FileSystemException("No such file");
                }
            } catch (IOException var3) {
                throw new FileSystemException(var3.getMessage());
            }
        }

        public void makeDirectory(String path) throws FileSystemException {
            if (this.m_writableMount == null) {
                throw new FileSystemException("Access Denied");
            } else {
                try {
                    path = this.toLocal(path);
                    if (this.m_mount.exists(path)) {
                        if (!this.m_mount.isDirectory(path)) {
                            throw new FileSystemException("File exists");
                        }
                    } else {
                        this.m_writableMount.makeDirectory(path);
                    }
                } catch (IOException var3) {
                    throw new FileSystemException(var3.getMessage());
                }
            }
        }

        public void delete(String path) throws FileSystemException {
            if (this.m_writableMount == null) {
                throw new FileSystemException("Access Denied");
            } else {
                try {
                    path = this.toLocal(path);
                    if (this.m_mount.exists(path)) {
                        this.m_writableMount.delete(path);
                    }
                } catch (IOException var3) {
                    throw new FileSystemException(var3.getMessage());
                }
            }
        }

        public OutputStream openForWrite(String path) throws FileSystemException {
            if (this.m_writableMount == null) {
                throw new FileSystemException("Access Denied");
            } else {
                try {
                    path = this.toLocal(path);
                    if (this.m_mount.exists(path) && this.m_mount.isDirectory(path)) {
                        throw new FileSystemException("Cannot write to directory");
                    } else {
                        if (!path.isEmpty()) {
                            String dir = FileSystem.getDirectory(path);
                            if (!dir.isEmpty() && !this.m_mount.exists(path)) {
                                this.m_writableMount.makeDirectory(dir);
                            }
                        }

                        return this.m_writableMount.openForWrite(path);
                    }
                } catch (IOException var3) {
                    throw new FileSystemException(var3.getMessage());
                }
            }
        }

        public OutputStream openForAppend(String path) throws FileSystemException {
            if (this.m_writableMount == null) {
                throw new FileSystemException("Access Denied");
            } else {
                try {
                    path = this.toLocal(path);
                    if (!this.m_mount.exists(path)) {
                        if (!path.isEmpty()) {
                            String dir = FileSystem.getDirectory(path);
                            if (!dir.isEmpty() && !this.m_mount.exists(path)) {
                                this.m_writableMount.makeDirectory(dir);
                            }
                        }

                        return this.m_writableMount.openForWrite(path);
                    } else if (this.m_mount.isDirectory(path)) {
                        throw new FileSystemException("Cannot write to directory");
                    } else {
                        return this.m_writableMount.openForAppend(path);
                    }
                } catch (IOException var3) {
                    throw new FileSystemException(var3.getMessage());
                }
            }
        }

        public RandomAccessFile openForReadWrite(String path, boolean truncate) throws FileSystemException {
            if (this.m_writableMount == null) {
                throw new FileSystemException("Access Denied");
            }
            try {
                path = this.toLocal(path);
                if (!truncate && !this.m_mount.exists(path)) {
                    throw new FileSystemException("No such file");
                }
                if (this.m_mount.exists(path) && this.m_mount.isDirectory(path)) {
                    throw new FileSystemException("Cannot write to directory");
                }
                return this.m_writableMount.openForReadWrite(path, truncate);
            } catch (IOException e) {
                throw new FileSystemException(e.getMessage());
            }
        }

        /**
         * Attempt to open the file at {@code path} for seekable reading.
         *
         * @return the RAF, or {@code null} if random access is not supported by this mount
         * @throws FileSystemException if the file does not exist or another I/O error occurs
         */
        public RandomAccessFile openForReadRandom(String path) throws FileSystemException {
            String localPath = this.toLocal(path);
            try {
                if (!this.m_mount.exists(localPath) || this.m_mount.isDirectory(localPath)) {
                    throw new FileSystemException("No such file");
                }
                return this.m_mount.openForReadRandom(localPath);
            } catch (FileSystemException e) {
                throw e;
            } catch (IOException e) {
                // "seek not supported by this mount" from the default IMount implementation,
                // or any other IOException indicating RAF is unavailable. Return null so
                // the caller can fall back to the stream-based path.
                return null;
            }
        }

        /**
         * Open the file at {@code path} for seekable writing.
         *
         * @param append {@code true} for append mode, {@code false} to create/truncate
         * @throws FileSystemException if access is denied, the path is a directory, or an I/O error occurs
         */
        public RandomAccessFile openForWriteRandom(String path, boolean append) throws FileSystemException {
            if (this.m_writableMount == null) {
                throw new FileSystemException("Access Denied");
            }
            try {
                path = this.toLocal(path);
                if (this.m_mount.exists(path) && this.m_mount.isDirectory(path)) {
                    throw new FileSystemException("Cannot write to directory");
                }
                // Ensure parent directory exists for new files
                if (!path.isEmpty()) {
                    String dir = FileSystem.getDirectory(path);
                    if (!dir.isEmpty() && !this.m_mount.exists(path)) {
                        this.m_writableMount.makeDirectory(dir);
                    }
                }
                return this.m_writableMount.openForWriteRandom(path, append);
            } catch (FileSystemException e) {
                throw e;
            } catch (IOException e) {
                throw new FileSystemException(e.getMessage());
            }
        }

        private String toLocal(String path) {
            return FileSystem.toLocal(path, this.m_location);
        }
    }
}
