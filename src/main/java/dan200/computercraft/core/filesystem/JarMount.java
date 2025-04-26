package dan200.computercraft.core.filesystem;

import dan200.computercraft.api.filesystem.IMount;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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

         if (this.m_zipFile.getEntry(subPath) == null) {
            this.m_zipFile.close();
            throw new IOException("Zip does not contain path");
         } else {
            Enumeration<? extends ZipEntry> zipEntries = this.m_zipFile.entries();

            while (zipEntries.hasMoreElements()) {
               ZipEntry entry = zipEntries.nextElement();
               String entryName = entry.getName();
               if (entryName.startsWith(subPath)) {
                  entryName = FileSystem.toLocal(entryName, subPath);
                  if (this.m_root == null) {
                     if (entryName.equals("")) {
                        this.m_root = new JarMount.FileInZip(entryName, entry.isDirectory(), entry.getSize());
                        this.m_rootPath = subPath;
                        if (!this.m_root.isDirectory()) {
                           break;
                        }
                     }
                  } else {
                     JarMount.FileInZip parent = this.m_root.getParent(entryName);
                     if (parent != null) {
                        parent.insertChild(new JarMount.FileInZip(entryName, entry.isDirectory(), entry.getSize()));
                     }
                  }
               }
            }
         }
      } else {
         throw new FileNotFoundException();
      }
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
         } catch (Exception var5) {
         }
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
         this.m_children = new LinkedHashMap<>();
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
