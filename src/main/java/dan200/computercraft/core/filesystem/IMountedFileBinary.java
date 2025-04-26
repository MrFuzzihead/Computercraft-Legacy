package dan200.computercraft.core.filesystem;

import java.io.IOException;

public interface IMountedFileBinary extends IMountedFile {
   int read() throws IOException;

   void write(int var1) throws IOException;

   @Override
   void close() throws IOException;

   void flush() throws IOException;
}
