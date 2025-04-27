package dan200.computercraft.core.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dan200.computercraft.api.filesystem.IMount;

public class ComboMount implements IMount {

    private IMount[] m_parts;

    public ComboMount(IMount[] parts) {
        this.m_parts = parts;
    }

    @Override
    public boolean exists(String path) throws IOException {
        for (int i = this.m_parts.length - 1; i >= 0; i--) {
            IMount part = this.m_parts[i];
            if (part.exists(path)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isDirectory(String path) throws IOException {
        for (int i = this.m_parts.length - 1; i >= 0; i--) {
            IMount part = this.m_parts[i];
            if (part.isDirectory(path)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void list(String path, List<String> contents) throws IOException {
        List<String> foundFiles = null;
        int foundDirs = 0;

        for (int i = this.m_parts.length - 1; i >= 0; i--) {
            IMount part = this.m_parts[i];
            if (part.exists(path) && part.isDirectory(path)) {
                if (foundFiles == null) {
                    foundFiles = new ArrayList<>();
                }

                part.list(path, foundFiles);
                foundDirs++;
            }
        }

        if (foundDirs == 1) {
            contents.addAll(foundFiles);
        } else {
            if (foundDirs <= 1) {
                throw new IOException("Not a directory");
            }

            Set<String> seen = new HashSet<>();

            for (int ix = 0; ix < foundFiles.size(); ix++) {
                String file = foundFiles.get(ix);
                if (seen.add(file)) {
                    contents.add(file);
                }
            }
        }
    }

    @Override
    public long getSize(String path) throws IOException {
        for (int i = this.m_parts.length - 1; i >= 0; i--) {
            IMount part = this.m_parts[i];
            if (part.exists(path)) {
                return part.getSize(path);
            }
        }

        throw new IOException("No such file");
    }

    @Override
    public InputStream openForRead(String path) throws IOException {
        for (int i = this.m_parts.length - 1; i >= 0; i--) {
            IMount part = this.m_parts[i];
            if (part.exists(path) && !part.isDirectory(path)) {
                return part.openForRead(path);
            }
        }

        throw new IOException("No such file");
    }
}
