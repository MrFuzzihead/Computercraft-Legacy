package dan200.computercraft.core.filesystem;

import dan200.computercraft.api.filesystem.IMount;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class SubMount implements IMount {
   private IMount m_parent;
   private String m_subPath;

   public SubMount(IMount parent, String subPath) {
      this.m_parent = parent;
      this.m_subPath = subPath;
   }

   @Override
   public boolean exists(String path) throws IOException {
      return this.m_parent.exists(this.getFullPath(path));
   }

   @Override
   public boolean isDirectory(String path) throws IOException {
      return this.m_parent.isDirectory(this.getFullPath(path));
   }

   @Override
   public void list(String path, List<String> contents) throws IOException {
      this.m_parent.list(this.getFullPath(path), contents);
   }

   @Override
   public long getSize(String path) throws IOException {
      return this.m_parent.getSize(this.getFullPath(path));
   }

   @Override
   public InputStream openForRead(String path) throws IOException {
      return this.m_parent.openForRead(this.getFullPath(path));
   }

   private String getFullPath(String path) {
      return path.length() == 0 ? this.m_subPath : this.m_subPath + "/" + path;
   }
}
