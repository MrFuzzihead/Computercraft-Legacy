package dan200.computercraft.api.filesystem;

import java.io.IOException;
import java.io.OutputStream;

public interface IWritableMount extends IMount {

    void makeDirectory(String var1) throws IOException;

    void delete(String var1) throws IOException;

    OutputStream openForWrite(String var1) throws IOException;

    OutputStream openForAppend(String var1) throws IOException;

    long getRemainingSpace() throws IOException;
}
