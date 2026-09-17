package dan200.computercraft.core.filesystem;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dan200.computercraft.api.filesystem.IMount;

public class JarMount implements IMount {

    private ZipFile m_zipFile;
    private JarMount.FileInZip m_root;
    private String m_rootPath;

    public JarMount(File jarFile, String subPath) throws IOException {
        if (jarFile.exists() && !jarFile.isDirectory()) {
            try {
                this.m_zipFile = new ZipFile(jarFile);
            } catch (Exception var7) {
                throw new IOException("Error loading zip file");
            }

            if (this.m_zipFile.getEntry(subPath) == null && this.m_zipFile.getEntry(subPath + "/") == null) {
                this.m_zipFile.close();
                throw new IOException("Zip does not contain path");
            } else {
                Enumeration<? extends ZipEntry> zipEntries = this.m_zipFile.entries();
                // Children can be enumerated before their parent directory
                // entry, so collect them and attach them once the root is known.
                List<JarMount.FileInZip> pendingChildren = new ArrayList<>();

                while (zipEntries.hasMoreElements()) {
                    ZipEntry entry = zipEntries.nextElement();
                    String entryName = entry.getName();
                    if (!isUnderPath(entryName, subPath)) {
                        continue;
                    }

                    entryName = FileSystem.toLocal(entryName, subPath);
                    if (entryName.isEmpty()) {
                        if (this.m_root == null) {
                            this.m_root = new JarMount.FileInZip(entryName, entry.isDirectory(), entry.getSize());
                            this.m_rootPath = subPath;
                        }
                        if (!this.m_root.isDirectory()) {
                            break;
                        }
                    } else {
                        pendingChildren.add(new JarMount.FileInZip(entryName, entry.isDirectory(), entry.getSize()));
                    }
                }

                if (this.m_root == null) {
                    // The sub-path exists as a zip entry but no entry resolved
                    // to the mount root: fail cleanly instead of NPE-ing on the
                    // first exists()/list() call (B7).
                    this.m_zipFile.close();
                    throw new IOException("Zip does not contain path");
                }

                if (this.m_root.isDirectory()) {
                    // Attach parents before children so intermediate
                    // directories exist in the tree.
                    pendingChildren.sort(
                        Comparator.comparingInt((JarMount.FileInZip file) -> pathDepth(file.getPath()))
                            .thenComparing(JarMount.FileInZip::getPath));
                    for (JarMount.FileInZip child : pendingChildren) {
                        JarMount.FileInZip parent = this.m_root.getParent(child.getPath());
                        if (parent != null) {
                            parent.insertChild(child);
                        }
                    }
                }
            }
        } else {
            throw new FileNotFoundException();
        }
    }

    /**
     * Returns {@code true} if {@code entryName} is the mount root or a genuine
     * descendant of it. A plain {@code startsWith} check would also match
     * unrelated prefix siblings (e.g. {@code "romario/x"} for {@code "rom"}).
     */
    private static boolean isUnderPath(String entryName, String subPath) {
        String base = subPath.endsWith("/") ? subPath.substring(0, subPath.length() - 1) : subPath;
        return entryName.equals(subPath) || entryName.equals(base) || entryName.startsWith(base + "/");
    }

    /** Number of path separators, used to attach parents before children. */
    private static int pathDepth(String path) {
        int depth = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') depth++;
        }

        return depth;
    }

    @Override
    public boolean exists(String path) throws IOException {
        JarMount.FileInZip file = this.m_root.getFile(path);
        return file != null;
    }

    @Override
    public boolean isDirectory(String path) throws IOException {
        JarMount.FileInZip file = this.m_root.getFile(path);
        return file != null ? file.isDirectory() : false;
    }

    @Override
    public void list(String path, List<String> contents) throws IOException {
        JarMount.FileInZip file = this.m_root.getFile(path);
        if (file != null && file.isDirectory()) {
            file.list(contents);
        } else {
            throw new IOException("Not a directory");
        }
    }

    @Override
    public long getSize(String path) throws IOException {
        JarMount.FileInZip file = this.m_root.getFile(path);
        if (file != null) {
            return file.getSize();
        } else {
            throw new IOException("No such file");
        }
    }

    @Override
    public InputStream openForRead(String path) throws IOException {
        JarMount.FileInZip file = this.m_root.getFile(path);
        if (file != null && !file.isDirectory()) {
            try {
                String fullPath = this.m_rootPath;
                if (path.length() > 0) {
                    fullPath = fullPath + "/" + path;
                }

                ZipEntry entry = this.m_zipFile.getEntry(fullPath);
                if (entry != null) {
                    return this.m_zipFile.getInputStream(entry);
                }
            } catch (Exception var5) {}
        }

        throw new IOException("No such file");
    }

    private class FileInZip {

        private String m_path;
        private boolean m_directory;
        private long m_size;
        private Map<String, JarMount.FileInZip> m_children;

        public FileInZip(String path, boolean directory, long size) {
            this.m_path = path;
            this.m_directory = directory;
            this.m_size = this.m_directory ? 0L : size;
            this.m_children = new TreeMap<>();
        }

        public String getPath() {
            return this.m_path;
        }

        public boolean isDirectory() {
            return this.m_directory;
        }

        public long getSize() {
            return this.m_size;
        }

        public void list(List<String> contents) {
            for (String child : this.m_children.keySet()) {
                contents.add(child);
            }
        }

        public void insertChild(JarMount.FileInZip child) {
            String localPath = FileSystem.toLocal(child.getPath(), this.m_path);
            this.m_children.put(localPath, child);
        }

        public JarMount.FileInZip getFile(String path) {
            if (path.equals(this.m_path)) {
                return this;
            } else {
                String localPath = FileSystem.toLocal(path, this.m_path);
                int slash = localPath.indexOf("/");
                if (slash >= 0) {
                    localPath = localPath.substring(0, slash);
                }

                JarMount.FileInZip subFile = this.m_children.get(localPath);
                return subFile != null ? subFile.getFile(path) : null;
            }
        }

        public JarMount.FileInZip getParent(String path) {
            if (path.length() == 0) {
                return null;
            } else {
                JarMount.FileInZip file = this.getFile(FileSystem.getDirectory(path));
                return file.isDirectory() ? file : null;
            }
        }
    }
}
