package dan200.computercraft.core.apis;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObject;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.filesystem.FileSystemException;
import dan200.computercraft.core.filesystem.IMountedFileBinary;
import dan200.computercraft.core.filesystem.IMountedFileNormal;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FSAPI implements ILuaAPI {
   private IAPIEnvironment m_env;
   private FileSystem m_fileSystem;

   public FSAPI(IAPIEnvironment _env) {
      this.m_env = _env;
      this.m_fileSystem = null;
   }

   @Override
   public String[] getNames() {
      return new String[]{"fs"};
   }

   @Override
   public void startup() {
      this.m_fileSystem = this.m_env.getFileSystem();
   }

   @Override
   public void advance(double _dt) {
   }

   @Override
   public void shutdown() {
      this.m_fileSystem = null;
   }

   @Override
   public String[] getMethodNames() {
      return new String[]{
         "list",
         "combine",
         "getName",
         "getSize",
         "exists",
         "isDir",
         "isReadOnly",
         "makeDir",
         "move",
         "copy",
         "delete",
         "open",
         "getDrive",
         "getFreeSpace",
         "find",
         "getDir"
      };
   }

   @Override
   public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
      switch (method) {
         case 0:
            if (args.length == 1 && args[0] != null && args[0] instanceof String) {
               String path = (String)args[0];

               try {
                  String[] results = this.m_fileSystem.list(path);
                  Map<Object, Object> table = new HashMap<>();

                  for (int i = 0; i < results.length; i++) {
                     table.put(i + 1, results[i]);
                  }

                  return new Object[]{table};
               } catch (FileSystemException var20) {
                  throw new LuaException(var20.getMessage());
               }
            } else {
               throw new LuaException("Expected string");
            }
         case 1:
            if (args.length == 2 && args[0] != null && args[0] instanceof String && args[1] != null && args[1] instanceof String) {
               String pathA = (String)args[0];
               String pathB = (String)args[1];
               return new Object[]{this.m_fileSystem.combine(pathA, pathB)};
            }

            throw new LuaException("Expected string, string");
         case 2:
            if (args.length == 1 && args[0] != null && args[0] instanceof String) {
               String path = (String)args[0];
               return new Object[]{FileSystem.getName(path)};
            }

            throw new LuaException("Expected string");
         case 3:
            if (args.length == 1 && args[0] != null && args[0] instanceof String) {
               String path = (String)args[0];

               try {
                  return new Object[]{this.m_fileSystem.getSize(path)};
               } catch (FileSystemException var18) {
                  throw new LuaException(var18.getMessage());
               }
            }

            throw new LuaException("Expected string");
         case 4:
            if (args.length == 1 && args[0] != null && args[0] instanceof String) {
               String path = (String)args[0];

               try {
                  return new Object[]{this.m_fileSystem.exists(path)};
               } catch (FileSystemException var17) {
                  return new Object[]{false};
               }
            }

            throw new LuaException("Expected string");
         case 5:
            if (args.length == 1 && args[0] != null && args[0] instanceof String) {
               String path = (String)args[0];

               try {
                  return new Object[]{this.m_fileSystem.isDir(path)};
               } catch (FileSystemException var16) {
                  return new Object[]{false};
               }
            }

            throw new LuaException("Expected string");
         case 6:
            if (args.length == 1 && args[0] != null && args[0] instanceof String) {
               String path = (String)args[0];

               try {
                  return new Object[]{this.m_fileSystem.isReadOnly(path)};
               } catch (FileSystemException var15) {
                  return new Object[]{false};
               }
            }

            throw new LuaException("Expected string");
         case 7:
            if (args.length == 1 && args[0] != null && args[0] instanceof String) {
               String path = (String)args[0];

               try {
                  this.m_fileSystem.makeDir(path);
                  return null;
               } catch (FileSystemException var14) {
                  throw new LuaException(var14.getMessage());
               }
            }

            throw new LuaException("Expected string");
         case 8:
            if (args.length == 2 && args[0] != null && args[0] instanceof String && args[1] != null && args[1] instanceof String) {
               String path = (String)args[0];
               String dest = (String)args[1];

               try {
                  this.m_fileSystem.move(path, dest);
                  return null;
               } catch (FileSystemException var13) {
                  throw new LuaException(var13.getMessage());
               }
            }

            throw new LuaException("Expected string, string");
         case 9:
            if (args.length == 2 && args[0] != null && args[0] instanceof String && args[1] != null && args[1] instanceof String) {
               String path = (String)args[0];
               String dest = (String)args[1];

               try {
                  this.m_fileSystem.copy(path, dest);
                  return null;
               } catch (FileSystemException var12) {
                  throw new LuaException(var12.getMessage());
               }
            }

            throw new LuaException("Expected string, string");
         case 10:
            if (args.length == 1 && args[0] != null && args[0] instanceof String) {
               String path = (String)args[0];

               try {
                  this.m_fileSystem.delete(path);
                  return null;
               } catch (FileSystemException var11) {
                  throw new LuaException(var11.getMessage());
               }
            }

            throw new LuaException("Expected string");
         case 11:
            if (args.length == 2 && args[0] != null && args[0] instanceof String && args[1] != null && args[1] instanceof String) {
               String path = (String)args[0];
               String mode = (String)args[1];

               try {
                  if (mode.equals("r")) {
                     IMountedFileNormal reader = this.m_fileSystem.openForRead(path);
                     return wrapBufferedReader(reader);
                  }

                  if (mode.equals("w")) {
                     IMountedFileNormal writer = this.m_fileSystem.openForWrite(path, false);
                     return wrapBufferedWriter(writer);
                  }

                  if (mode.equals("a")) {
                     IMountedFileNormal writer = this.m_fileSystem.openForWrite(path, true);
                     return wrapBufferedWriter(writer);
                  }

                  if (mode.equals("rb")) {
                     IMountedFileBinary reader = this.m_fileSystem.openForBinaryRead(path);
                     return wrapInputStream(reader);
                  }

                  if (mode.equals("wb")) {
                     IMountedFileBinary writer = this.m_fileSystem.openForBinaryWrite(path, false);
                     return wrapOutputStream(writer);
                  }

                  if (mode.equals("ab")) {
                     IMountedFileBinary writer = this.m_fileSystem.openForBinaryWrite(path, true);
                     return wrapOutputStream(writer);
                  }

                  throw new LuaException("Unsupported mode");
               } catch (FileSystemException var10) {
                  return null;
               }
            }

            throw new LuaException("Expected string, string");
         case 12:
            if (args.length == 1 && args[0] != null && args[0] instanceof String) {
               String path = (String)args[0];

               try {
                  if (!this.m_fileSystem.exists(path)) {
                     return null;
                  }

                  return new Object[]{this.m_fileSystem.getMountLabel(path)};
               } catch (FileSystemException var9) {
                  throw new LuaException(var9.getMessage());
               }
            }

            throw new LuaException("Expected string");
         case 13:
            if (args.length == 1 && args[0] != null && args[0] instanceof String) {
               String path = (String)args[0];

               try {
                  long freeSpace = this.m_fileSystem.getFreeSpace(path);
                  if (freeSpace >= 0L) {
                     return new Object[]{freeSpace};
                  }

                  return new Object[]{"unlimited"};
               } catch (FileSystemException var8) {
                  throw new LuaException(var8.getMessage());
               }
            }

            throw new LuaException("Expected string");
         case 14:
            if (args.length == 1 && args[0] != null && args[0] instanceof String) {
               String path = (String)args[0];

               try {
                  String[] results = this.m_fileSystem.find(path);
                  Map<Object, Object> table = new HashMap<>();

                  for (int i = 0; i < results.length; i++) {
                     table.put(i + 1, results[i]);
                  }

                  return new Object[]{table};
               } catch (FileSystemException var19) {
                  throw new LuaException(var19.getMessage());
               }
            }

            throw new LuaException("Expected string");
         case 15:
            if (args.length == 1 && args[0] != null && args[0] instanceof String) {
               String path = (String)args[0];
               return new Object[]{FileSystem.getDirectory(path)};
            }

            throw new LuaException("Expected string");
         default:
            assert false;

            return null;
      }
   }

   private static Object[] wrapBufferedReader(final IMountedFileNormal reader) {
      return new Object[]{new ILuaObject() {
         @Override
         public String[] getMethodNames() {
            return new String[]{"readLine", "readAll", "close"};
         }

         @Override
         public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
            switch (method) {
               case 0:
                  try {
                     String line = reader.readLine();
                     if (line != null) {
                        return new Object[]{line};
                     }

                     return null;
                  } catch (IOException var8) {
                     return null;
                  }
               case 1:
                  try {
                     StringBuilder result = new StringBuilder("");
                     String line = reader.readLine();

                     while (line != null) {
                        result.append(line);
                        line = reader.readLine();
                        if (line != null) {
                           result.append("\n");
                        }
                     }

                     return new Object[]{result.toString()};
                  } catch (IOException var7) {
                     return null;
                  }
               case 2:
                  try {
                     reader.close();
                     return null;
                  } catch (IOException var6) {
                     return null;
                  }
               default:
                  return null;
            }
         }
      }};
   }

   private static Object[] wrapBufferedWriter(final IMountedFileNormal writer) {
      return new Object[]{new ILuaObject() {
         @Override
         public String[] getMethodNames() {
            return new String[]{"write", "writeLine", "close", "flush"};
         }

         @Override
         public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
            switch (method) {
               case 0:
                  String text;
                  if (args.length > 0 && args[0] != null) {
                     text = args[0].toString();
                  } else {
                     text = "";
                  }

                  try {
                     writer.write(text, 0, text.length(), false);
                     return null;
                  } catch (IOException var9) {
                     throw new LuaException(var9.getMessage());
                  }
               case 1:
                  String text2;
                  if (args.length > 0 && args[0] != null) {
                     text2 = args[0].toString();
                  } else {
                     text2 = "";
                  }

                  try {
                     writer.write(text2, 0, text2.length(), true);
                     return null;
                  } catch (IOException var8) {
                     throw new LuaException(var8.getMessage());
                  }
               case 2:
                  try {
                     writer.close();
                     return null;
                  } catch (IOException var7) {
                     return null;
                  }
               case 3:
                  try {
                     writer.flush();
                     return null;
                  } catch (IOException var6) {
                     return null;
                  }
               default:
                  assert false;

                  return null;
            }
         }
      }};
   }

   private static Object[] wrapInputStream(final IMountedFileBinary reader) {
      return new Object[]{new ILuaObject() {
         @Override
         public String[] getMethodNames() {
            return new String[]{"read", "close"};
         }

         @Override
         public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
            switch (method) {
               case 0:
                  try {
                     int b = reader.read();
                     if (b != -1) {
                        return new Object[]{b};
                     }

                     return null;
                  } catch (IOException var6) {
                     return null;
                  }
               case 1:
                  try {
                     reader.close();
                     return null;
                  } catch (IOException var5) {
                     return null;
                  }
               default:
                  assert false;

                  return null;
            }
         }
      }};
   }

   private static Object[] wrapOutputStream(final IMountedFileBinary writer) {
      return new Object[]{new ILuaObject() {
         @Override
         public String[] getMethodNames() {
            return new String[]{"write", "close", "flush"};
         }

         @Override
         public Object[] callMethod(ILuaContext context, int method, Object[] args) throws LuaException {
            switch (method) {
               case 0:
                  try {
                     if (args.length > 0 && args[0] instanceof Number) {
                        int number = ((Number)args[0]).intValue();
                        writer.write(number);
                     }

                     return null;
                  } catch (IOException var7) {
                     throw new LuaException(var7.getMessage());
                  }
               case 1:
                  try {
                     writer.close();
                     return null;
                  } catch (IOException var6) {
                     return null;
                  }
               case 2:
                  try {
                     writer.flush();
                     return null;
                  } catch (IOException var5) {
                     return null;
                  }
               default:
                  assert false;

                  return null;
            }
         }
      }};
   }
}
