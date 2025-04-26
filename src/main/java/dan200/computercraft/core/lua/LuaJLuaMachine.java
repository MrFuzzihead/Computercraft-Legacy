package dan200.computercraft.core.lua;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObject;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.ITask;
import dan200.computercraft.core.computer.MainThread;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.OrphanedThread;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

public class LuaJLuaMachine implements ILuaMachine {
   private Computer m_computer;
   private LuaValue m_globals;
   private LuaValue m_loadString;
   private LuaValue m_assert;
   private LuaValue m_coroutine_create;
   private LuaValue m_coroutine_resume;
   private LuaValue m_coroutine_yield;
   private LuaValue m_mainRoutine;
   private String m_eventFilter;
   private String m_softAbortMessage;
   private String m_hardAbortMessage;
   private Map<Object, LuaValue> m_valuesInProgress;
   private Map<LuaValue, Object> m_objectsInProgress;
   private static long s_nextUnusedTaskID = 0L;

   public LuaJLuaMachine(Computer computer) {
      this.m_computer = computer;
      this.m_globals = JsePlatform.debugGlobals();
      this.m_loadString = this.m_globals.get("loadstring");
      this.m_assert = this.m_globals.get("assert");
      LuaValue coroutine = this.m_globals.get("coroutine");
      final LuaValue native_coroutine_create = coroutine.get("create");
      LuaValue debug = this.m_globals.get("debug");
      final LuaValue debug_sethook = debug.get("sethook");
      coroutine.set("create", new OneArgFunction() {
         @Override
         public LuaValue call(LuaValue value) {
            LuaThread thread = native_coroutine_create.call(value).checkthread();
            debug_sethook.invoke(new LuaValue[]{thread, new ZeroArgFunction() {
               @Override
               public LuaValue call() {
                  String hardAbortMessage = LuaJLuaMachine.this.m_hardAbortMessage;
                  if (hardAbortMessage != null) {
                     LuaThread.yield(LuaValue.NIL);
                  }

                  return LuaValue.NIL;
               }
            }, LuaValue.NIL, LuaValue.valueOf(100000)});
            return thread;
         }
      });
      this.m_coroutine_create = coroutine.get("create");
      this.m_coroutine_resume = coroutine.get("resume");
      this.m_coroutine_yield = coroutine.get("yield");
      this.m_globals.set("collectgarbage", LuaValue.NIL);
      this.m_globals.set("dofile", LuaValue.NIL);
      this.m_globals.set("loadfile", LuaValue.NIL);
      this.m_globals.set("module", LuaValue.NIL);
      this.m_globals.set("require", LuaValue.NIL);
      this.m_globals.set("package", LuaValue.NIL);
      this.m_globals.set("io", LuaValue.NIL);
      this.m_globals.set("os", LuaValue.NIL);
      this.m_globals.set("print", LuaValue.NIL);
      this.m_globals.set("luajava", LuaValue.NIL);
      this.m_globals.set("debug", LuaValue.NIL);
      this.m_globals.set("newproxy", LuaValue.NIL);
      this.m_globals.set("_VERSION", "Lua 5.1");
      this.m_globals.set("_LUAJ_VERSION", "2.0.3");
      this.m_globals.set("_CC_VERSION", "1.75");
      this.m_globals.set("_MC_VERSION", "1.7.10");
      if (ComputerCraft.disable_lua51_features) {
         this.m_globals.set("_CC_DISABLE_LUA51_FEATURES", LuaValue.valueOf(true));
      }

      this.m_mainRoutine = null;
      this.m_eventFilter = null;
      this.m_softAbortMessage = null;
      this.m_hardAbortMessage = null;
   }

   @Override
   public void addAPI(ILuaAPI api) {
      LuaTable table = this.wrapLuaObject(api);
      String[] names = api.getNames();

      for (int i = 0; i < names.length; i++) {
         this.m_globals.set(names[i], table);
      }
   }

   @Override
   public void loadBios(InputStream bios) {
      if (this.m_mainRoutine == null) {
         try {
            String biosText = null;

            try {
               BufferedReader reader = new BufferedReader(new InputStreamReader(bios));
               StringBuilder fileText = new StringBuilder("");
               String line = reader.readLine();

               while (line != null) {
                  fileText.append(line);
                  line = reader.readLine();
                  if (line != null) {
                     fileText.append("\n");
                  }
               }

               biosText = fileText.toString();
            } catch (IOException var6) {
               throw new LuaError("Could not read file");
            }

            LuaValue program = this.m_assert.call(this.m_loadString.call(LuaValue.valueOf(biosText), LuaValue.valueOf("bios")));
            this.m_mainRoutine = this.m_coroutine_create.call(program);
         } catch (LuaError var7) {
            if (this.m_mainRoutine != null) {
               ((LuaThread)this.m_mainRoutine).abandon();
               this.m_mainRoutine = null;
            }
         }
      }
   }

   @Override
   public void handleEvent(String eventName, Object[] arguments) {
      if (this.m_mainRoutine != null) {
         if (this.m_eventFilter == null || eventName == null || eventName.equals(this.m_eventFilter) || eventName.equals("terminate")) {
            try {
               LuaValue[] resumeArgs;
               if (eventName != null) {
                  resumeArgs = this.toValues(arguments, 2);
                  resumeArgs[0] = this.m_mainRoutine;
                  resumeArgs[1] = LuaValue.valueOf(eventName);
               } else {
                  resumeArgs = new LuaValue[1];
                  resumeArgs[0] = this.m_mainRoutine;
               }

               Varargs results = this.m_coroutine_resume.invoke(LuaValue.varargsOf(resumeArgs));
               if (this.m_hardAbortMessage != null) {
                  throw new LuaError(this.m_hardAbortMessage);
               }

               if (!results.arg1().checkboolean()) {
                  throw new LuaError(results.arg(2).checkstring().toString());
               }

               LuaValue filter = results.arg(2);
               if (filter.isstring()) {
                  this.m_eventFilter = filter.toString();
               } else {
                  this.m_eventFilter = null;
               }

               LuaThread mainThread = (LuaThread)this.m_mainRoutine;
               if (mainThread.getStatus().equals("dead")) {
                  this.m_mainRoutine = null;
               }
            } catch (LuaError var9) {
               ((LuaThread)this.m_mainRoutine).abandon();
               this.m_mainRoutine = null;
            } finally {
               this.m_softAbortMessage = null;
               this.m_hardAbortMessage = null;
            }
         }
      }
   }

   @Override
   public void softAbort(String abortMessage) {
      this.m_softAbortMessage = abortMessage;
   }

   @Override
   public void hardAbort(String abortMessage) {
      this.m_softAbortMessage = abortMessage;
      this.m_hardAbortMessage = abortMessage;
   }

   @Override
   public boolean saveState(OutputStream output) {
      return false;
   }

   @Override
   public boolean restoreState(InputStream input) {
      return false;
   }

   @Override
   public boolean isFinished() {
      return this.m_mainRoutine == null;
   }

   @Override
   public void unload() {
      if (this.m_mainRoutine != null) {
         LuaThread mainThread = (LuaThread)this.m_mainRoutine;
         mainThread.abandon();
         this.m_mainRoutine = null;
      }
   }

   private void tryAbort() throws LuaError {
      String abortMessage = this.m_softAbortMessage;
      if (abortMessage != null) {
         this.m_softAbortMessage = null;
         this.m_hardAbortMessage = null;
         throw new LuaError(abortMessage);
      }
   }

   private LuaTable wrapLuaObject(final ILuaObject object) {
      LuaTable table = new LuaTable();
      String[] methods = object.getMethodNames();

      for (int i = 0; i < methods.length; i++) {
         if (methods[i] != null) {
             final int method = i;
            table.set(
               methods[i],
               new VarArgFunction() {
                  @Override
                  public Varargs invoke(Varargs _args) {
                     LuaJLuaMachine.this.tryAbort();
                     Object[] arguments = LuaJLuaMachine.this.toObjects(_args, 1);
                     Object[] results = null;

                     try {
                        results = object.callMethod(
                           new ILuaContext() {
                              @Override
                              public Object[] pullEvent(String filter) throws LuaException, InterruptedException {
                                 Object[] resultsx = this.pullEventRaw(filter);
                                 if (resultsx.length >= 1 && resultsx[0].equals("terminate")) {
                                    throw new LuaException("Terminated", 0);
                                 } else {
                                    return resultsx;
                                 }
                              }

                              @Override
                              public Object[] pullEventRaw(String filter) throws InterruptedException {
                                 return this.yield(new Object[]{filter});
                              }

                              @Override
                              public Object[] yield(Object[] yieldArgs) throws InterruptedException {
                                 try {
                                    LuaValue[] yieldValues = LuaJLuaMachine.this.toValues(yieldArgs, 0);
                                    Varargs resultsx = LuaJLuaMachine.this.m_coroutine_yield.invoke(LuaValue.varargsOf(yieldValues));
                                    return LuaJLuaMachine.this.toObjects(resultsx, 1);
                                 } catch (OrphanedThread var4) {
                                    throw new InterruptedException();
                                 }
                              }

                              @Override
                              public long issueMainThreadTask(final ILuaTask task) throws LuaException {
                                 final long taskID = MainThread.getUniqueTaskID();
                                 ITask iTask = new ITask() {
                                    @Override
                                    public Computer getOwner() {
                                       return LuaJLuaMachine.this.m_computer;
                                    }

                                    @Override
                                    public void execute() {
                                       try {
                                          Object[] resultsx = task.execute();
                                          if (resultsx != null) {
                                             Object[] eventArguments = new Object[resultsx.length + 2];
                                             eventArguments[0] = taskID;
                                             eventArguments[1] = true;

                                             for (int ix = 0; ix < resultsx.length; ix++) {
                                                eventArguments[ix + 2] = resultsx[ix];
                                             }

                                             LuaJLuaMachine.this.m_computer.queueEvent("task_complete", eventArguments);
                                          } else {
                                             LuaJLuaMachine.this.m_computer.queueEvent("task_complete", new Object[]{taskID, true});
                                          }
                                       } catch (LuaException var4) {
                                          LuaJLuaMachine.this.m_computer.queueEvent("task_complete", new Object[]{taskID, false, var4.getMessage()});
                                       } catch (Throwable var5x) {
                                          LuaJLuaMachine.this.m_computer
                                             .queueEvent("task_complete", new Object[]{taskID, false, "Java Exception Thrown: " + var5x.toString()});
                                       }
                                    }
                                 };
                                 if (MainThread.queueTask(iTask)) {
                                    return taskID;
                                 } else {
                                    throw new LuaException("Task limit exceeded");
                                 }
                              }

                              @Override
                              public Object[] executeMainThreadTask(ILuaTask task) throws LuaException, InterruptedException {
                                 long taskID = this.issueMainThreadTask(task);

                                 Object[] response;
                                 do {
                                    response = this.pullEvent("task_complete");
                                 } while (
                                    response.length < 3
                                       || !(response[1] instanceof Number)
                                       || !(response[2] instanceof Boolean)
                                       || ((Number)response[1]).intValue() != taskID
                                 );

                                 Object[] returnValues = new Object[response.length - 3];
                                 if ((Boolean)response[2]) {
                                    for (int ix = 0; ix < returnValues.length; ix++) {
                                       returnValues[ix] = response[ix + 3];
                                    }

                                    return returnValues;
                                 } else if (response.length >= 4 && response[3] instanceof String) {
                                    throw new LuaException((String)response[3]);
                                 } else {
                                    throw new LuaException();
                                 }
                              }
                           },
                           method,
                           arguments
                        );
                     } catch (InterruptedException var5) {
                        throw new OrphanedThread();
                     } catch (LuaException var6) {
                        throw new LuaError(var6.getMessage(), var6.getLevel());
                     } catch (Throwable var7) {
                        throw new LuaError("Java Exception Thrown: " + var7.toString(), 0);
                     }

                     return LuaValue.varargsOf(LuaJLuaMachine.this.toValues(results, 0));
                  }
               }
            );
         }
      }

      return table;
   }

   private LuaValue toValue(Object object) {
      if (object == null) {
         return LuaValue.NIL;
      } else if (object instanceof Number) {
         double d = ((Number)object).doubleValue();
         return LuaValue.valueOf(d);
      } else if (object instanceof Boolean) {
         boolean b = (Boolean)object;
         return LuaValue.valueOf(b);
      } else if (object instanceof String) {
         String s = object.toString();
         return LuaValue.valueOf(s);
      } else if (object instanceof Map) {
         boolean clearWhenDone = false;

         try {
            if (this.m_valuesInProgress == null) {
               this.m_valuesInProgress = new IdentityHashMap<>();
               clearWhenDone = true;
            } else if (this.m_valuesInProgress.containsKey(object)) {
               return this.m_valuesInProgress.get(object);
            }

            LuaValue table = new LuaTable();
            this.m_valuesInProgress.put(object, table);

            for (Map.Entry<?, ?> pair : ((Map<?, ?>) object).entrySet()) {
               LuaValue key = this.toValue(pair.getKey());
               LuaValue value = this.toValue(pair.getValue());
               if (!key.isnil() && !value.isnil()) {
                  table.set(key, value);
               }
            }

            return table;
         } finally {
            if (clearWhenDone) {
               this.m_valuesInProgress = null;
            }
         }
      } else if (object instanceof ILuaObject) {
         LuaValue table = this.wrapLuaObject((ILuaObject)object);
         return table;
      } else {
         return LuaValue.NIL;
      }
   }

   private LuaValue[] toValues(Object[] objects, int leaveEmpty) {
      if (objects != null && objects.length != 0) {
         LuaValue[] values = new LuaValue[objects.length + leaveEmpty];

         for (int i = 0; i < values.length; i++) {
            if (i < leaveEmpty) {
               values[i] = null;
            } else {
               Object object = objects[i - leaveEmpty];
               values[i] = this.toValue(object);
            }
         }

         return values;
      } else {
         return new LuaValue[leaveEmpty];
      }
   }

   private Object toObject(LuaValue value) {
      switch (value.type()) {
         case -2:
         case 3:
            return value.todouble();
         case -1:
         case 0:
            return null;
         case 1:
            return value.toboolean();
         case 2:
         default:
            return null;
         case 4:
            return value.toString();
         case 5:
            boolean clearWhenDone = false;

            try {
               if (this.m_objectsInProgress == null) {
                  this.m_objectsInProgress = new IdentityHashMap<>();
                  clearWhenDone = true;
               } else if (this.m_objectsInProgress.containsKey(value)) {
                  return this.m_objectsInProgress.get(value);
               }

               Map table = new HashMap();
               this.m_objectsInProgress.put(value, table);
               LuaValue k = LuaValue.NIL;

               while (true) {
                  Varargs keyValue = value.next(k);
                  k = keyValue.arg1();
                  if (k.isnil()) {
                     return table;
                  }

                  LuaValue v = keyValue.arg(2);
                  Object keyObject = this.toObject(k);
                  Object valueObject = this.toObject(v);
                  if (keyObject != null && valueObject != null) {
                     table.put(keyObject, valueObject);
                  }
               }
            } finally {
               if (clearWhenDone) {
                  this.m_objectsInProgress = null;
               }
            }
      }
   }

   private Object[] toObjects(Varargs values, int startIdx) {
      int count = values.narg();
      Object[] objects = new Object[count - startIdx + 1];

      for (int n = startIdx; n <= count; n++) {
         int i = n - startIdx;
         LuaValue value = values.arg(n);
         objects[i] = this.toObject(value);
      }

      return objects;
   }
}
