package dan200.computercraft.core.filesystem;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;

public class FileSystem {

    private Map<String, FileSystem.MountWrapper> m_mounts = new HashMap<>();
    private Set<IMountedFile> m_openFiles = new HashSet<>();

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
        Pattern wildPattern = Pattern.compile("^\\Q" + wildPath.replaceAll("\\*", "\\\\E[^\\\\/]*\\\\Q") + "\\E$");
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
        FileSystem.MountWrapper mount = this.getMount(path);
        InputStream stream = mount.openForRead(path);
        if (stream != null) {
            final BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            IMountedFileNormal file = new IMountedFileNormal() {

                @Override
                public String readLine() throws IOException {
                    return reader.readLine();
                }

                @Override
                public void write(String s, int off, int len, boolean newLine) throws IOException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void close() throws IOException {
                    synchronized (FileSystem.this.m_openFiles) {
                        FileSystem.this.m_openFiles.remove(this);
                        reader.close();
                    }
                }

                @Override
                public void flush() throws IOException {
                    throw new UnsupportedOperationException();
                }
            };
            synchronized (this.m_openFiles) {
                this.m_openFiles.add(file);
                return file;
            }
        } else {
            return null;
        }
    }

    public synchronized IMountedFileNormal openForWrite(String path, boolean append) throws FileSystemException {
        path = sanitizePath(path);
        FileSystem.MountWrapper mount = this.getMount(path);
        OutputStream stream = append ? mount.openForAppend(path) : mount.openForWrite(path);
        if (stream != null) {
            final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream));
            IMountedFileNormal file = new IMountedFileNormal() {

                @Override
                public String readLine() throws IOException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void write(String s, int off, int len, boolean newLine) throws IOException {
                    writer.write(s, off, len);
                    if (newLine) {
                        writer.newLine();
                    }
                }

                @Override
                public void close() throws IOException {
                    synchronized (FileSystem.this.m_openFiles) {
                        FileSystem.this.m_openFiles.remove(this);
                        writer.close();
                    }
                }

                @Override
                public void flush() throws IOException {
                    writer.flush();
                }
            };
            synchronized (this.m_openFiles) {
                this.m_openFiles.add(file);
                return file;
            }
        } else {
            return null;
        }
    }

    public synchronized IMountedFileBinary openForBinaryRead(String path) throws FileSystemException {
        path = sanitizePath(path);
        FileSystem.MountWrapper mount = this.getMount(path);
        final InputStream stream = mount.openForRead(path);
        if (stream != null) {
            IMountedFileBinary file = new IMountedFileBinary() {

                @Override
                public int read() throws IOException {
                    return stream.read();
                }

                @Override
                public void write(int i) throws IOException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void close() throws IOException {
                    synchronized (FileSystem.this.m_openFiles) {
                        FileSystem.this.m_openFiles.remove(this);
                        stream.close();
                    }
                }

                @Override
                public void flush() throws IOException {
                    throw new UnsupportedOperationException();
                }
            };
            synchronized (this.m_openFiles) {
                this.m_openFiles.add(file);
                return file;
            }
        } else {
            return null;
        }
    }

    public synchronized IMountedFileBinary openForBinaryWrite(String path, boolean append) throws FileSystemException {
        path = sanitizePath(path);
        FileSystem.MountWrapper mount = this.getMount(path);
        final OutputStream stream = append ? mount.openForAppend(path) : mount.openForWrite(path);
        if (stream != null) {
            IMountedFileBinary file = new IMountedFileBinary() {

                @Override
                public int read() throws IOException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void write(int i) throws IOException {
                    stream.write(i);
                }

                @Override
                public void close() throws IOException {
                    synchronized (FileSystem.this.m_openFiles) {
                        FileSystem.this.m_openFiles.remove(this);
                        stream.close();
                    }
                }

                @Override
                public void flush() throws IOException {
                    stream.flush();
                }
            };
            synchronized (this.m_openFiles) {
                this.m_openFiles.add(file);
                return file;
            }
        } else {
            return null;
        }
    }

    public long getFreeSpace(String path) throws FileSystemException {
        path = sanitizePath(path);
        FileSystem.MountWrapper mount = this.getMount(path);
        return mount.getFreeSpace();
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
        char[] specialChars = new char[] { '"', ':', '<', '>', '?', '|' };
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

        private String toLocal(String path) {
            return FileSystem.toLocal(path, this.m_location);
        }
    }
}
