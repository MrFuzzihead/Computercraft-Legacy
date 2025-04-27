package dan200.computercraft.core.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import dan200.computercraft.api.filesystem.IMount;

public class EmptyMount implements IMount {

    @Override
    public boolean exists(String path) throws IOException {
        return path.isEmpty();
    }

    @Override
    public boolean isDirectory(String path) throws IOException {
        return path.isEmpty();
    }

    @Override
    public void list(String path, List<String> contents) throws IOException {}

    @Override
    public long getSize(String path) throws IOException {
        return 0L;
    }

    @Override
    public InputStream openForRead(String path) throws IOException {
        return null;
    }
}
