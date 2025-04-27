package dan200.computercraft.api.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface IMount {

    boolean exists(String var1) throws IOException;

    boolean isDirectory(String var1) throws IOException;

    void list(String var1, List<String> var2) throws IOException;

    long getSize(String var1) throws IOException;

    InputStream openForRead(String var1) throws IOException;
}
