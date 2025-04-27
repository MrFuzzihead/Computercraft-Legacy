package dan200.computercraft.core.filesystem;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import dan200.computercraft.api.filesystem.IWritableMount;

public class FileMount implements IWritableMount {

    private static int MINIMUM_FILE_SIZE = 500;
    private File m_rootPath;
    private long m_capacity;
    private long m_usedSpace;

    public FileMount(File rootPath, long capacity) {
        this.m_rootPath = rootPath;
        this.m_capacity = capacity + MINIMUM_FILE_SIZE;
        this.m_usedSpace = this.created() ? this.measureUsedSpace(this.m_rootPath) : MINIMUM_FILE_SIZE;
    }

    @Override
    public boolean exists(String path) throws IOException {
        if (!this.created()) {
            return path.length() == 0;
        } else {
            File file = this.getRealPath(path);
            return file.exists();
        }
    }

    @Override
    public boolean isDirectory(String path) throws IOException {
        if (!this.created()) {
            return path.length() == 0;
        } else {
            File file = this.getRealPath(path);
            return file.exists() && file.isDirectory();
        }
    }

    @Override
    public void list(String path, List<String> contents) throws IOException {
        if (!this.created()) {
            if (path.length() != 0) {
                throw new IOException("Not a directory");
            }
        } else {
            File file = this.getRealPath(path);
            if (!file.exists() || !file.isDirectory()) {
                throw new IOException("Not a directory");
            }

            String[] paths = file.list();

            for (String subPath : paths) {
                if (new File(file, subPath).exists()) {
                    contents.add(subPath);
                }
            }
        }
    }

    @Override
    public long getSize(String path) throws IOException {
        if (!this.created()) {
            if (path.length() == 0) {
                return 0L;
            }
        } else {
            File file = this.getRealPath(path);
            if (file.exists()) {
                if (file.isDirectory()) {
                    return 0L;
                }

                return file.length();
            }
        }

        throw new IOException("No such file");
    }

    @Override
    public InputStream openForRead(String path) throws IOException {
        if (this.created()) {
            File file = this.getRealPath(path);
            if (file.exists() && !file.isDirectory()) {
                return new FileInputStream(file);
            }
        }

        throw new IOException("No such file");
    }

    @Override
    public void makeDirectory(String path) throws IOException {
        this.create();
        File file = this.getRealPath(path);
        if (file.exists()) {
            if (!file.isDirectory()) {
                throw new IOException("File exists");
            }
        } else {
            int dirsToCreate = 1;

            for (File parent = file.getParentFile(); !parent.exists(); parent = parent.getParentFile()) {
                dirsToCreate++;
            }

            if (this.getRemainingSpace() < dirsToCreate * MINIMUM_FILE_SIZE) {
                throw new IOException("Out of space");
            }

            boolean success = file.mkdirs();
            if (!success) {
                throw new IOException("Access denied");
            }

            this.m_usedSpace = this.m_usedSpace + dirsToCreate * MINIMUM_FILE_SIZE;
        }
    }

    @Override
    public void delete(String path) throws IOException {
        if (path.length() == 0) {
            throw new IOException("Access denied");
        } else {
            if (this.created()) {
                File file = this.getRealPath(path);
                if (file.exists()) {
                    this.deleteRecursively(file);
                }
            }
        }
    }

    private void deleteRecursively(File file) throws IOException {
        if (file.isDirectory()) {
            String[] children = file.list();

            for (int i = 0; i < children.length; i++) {
                this.deleteRecursively(new File(file, children[i]));
            }
        }

        long fileSize = file.isDirectory() ? 0L : file.length();
        boolean success = file.delete();
        if (success) {
            this.m_usedSpace = this.m_usedSpace - Math.max((long) MINIMUM_FILE_SIZE, fileSize);
        } else {
            throw new IOException("Access denied");
        }
    }

    @Override
    public OutputStream openForWrite(String path) throws IOException {
        this.create();
        File file = this.getRealPath(path);
        if (file.exists() && file.isDirectory()) {
            throw new IOException("Cannot write to directory");
        } else {
            if (!file.exists()) {
                if (this.getRemainingSpace() < MINIMUM_FILE_SIZE) {
                    throw new IOException("Out of space");
                }

                this.m_usedSpace = this.m_usedSpace + MINIMUM_FILE_SIZE;
            } else {
                this.m_usedSpace = this.m_usedSpace - Math.max(file.length(), (long) MINIMUM_FILE_SIZE);
                this.m_usedSpace = this.m_usedSpace + MINIMUM_FILE_SIZE;
            }

            return new FileMount.CountingOutputStream(new FileOutputStream(file, false), MINIMUM_FILE_SIZE);
        }
    }

    @Override
    public OutputStream openForAppend(String path) throws IOException {
        if (this.created()) {
            File file = this.getRealPath(path);
            if (!file.exists()) {
                throw new IOException("No such file");
            } else if (file.isDirectory()) {
                throw new IOException("Cannot write to directory");
            } else {
                return new FileMount.CountingOutputStream(
                    new FileOutputStream(file, true),
                    Math.max(MINIMUM_FILE_SIZE - file.length(), 0L));
            }
        } else {
            throw new IOException("No such file");
        }
    }

    @Override
    public long getRemainingSpace() throws IOException {
        return Math.max(this.m_capacity - this.m_usedSpace, 0L);
    }

    public File getRealPath(String path) {
        return new File(this.m_rootPath, path);
    }

    private boolean created() {
        return this.m_rootPath.exists();
    }

    private void create() throws IOException {
        if (!this.m_rootPath.exists()) {
            boolean success = this.m_rootPath.mkdirs();
            if (!success) {
                throw new IOException("Access denied");
            }
        }
    }

    private long measureUsedSpace(File file) {
        if (!file.exists()) {
            return 0L;
        } else if (!file.isDirectory()) {
            return Math.max(file.length(), (long) MINIMUM_FILE_SIZE);
        } else {
            long size = MINIMUM_FILE_SIZE;
            String[] contents = file.list();

            for (int i = 0; i < contents.length; i++) {
                size += this.measureUsedSpace(new File(file, contents[i]));
            }

            return size;
        }
    }

    private class CountingOutputStream extends OutputStream {

        private OutputStream m_innerStream;
        private long m_ignoredBytesLeft;

        public CountingOutputStream(OutputStream innerStream, long bytesToIgnore) {
            this.m_innerStream = innerStream;
            this.m_ignoredBytesLeft = bytesToIgnore;
        }

        @Override
        public void close() throws IOException {
            this.m_innerStream.close();
        }

        @Override
        public void flush() throws IOException {
            this.m_innerStream.flush();
        }

        @Override
        public void write(byte[] b) throws IOException {
            this.count(b.length);
            this.m_innerStream.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            this.count(len);
            this.m_innerStream.write(b, off, len);
        }

        @Override
        public void write(int b) throws IOException {
            this.count(1L);
            this.m_innerStream.write(b);
        }

        private void count(long n) throws IOException {
            this.m_ignoredBytesLeft -= n;
            if (this.m_ignoredBytesLeft < 0L) {
                long newBytes = -this.m_ignoredBytesLeft;
                this.m_ignoredBytesLeft = 0L;
                long bytesLeft = FileMount.this.m_capacity - FileMount.this.m_usedSpace;
                if (newBytes > bytesLeft) {
                    throw new IOException("Out of space");
                }

                FileMount.this.m_usedSpace += newBytes;
            }
        }
    }
}
