package dan200.computercraft.core.lua.lib.cobalt;

import static org.squiddev.cobalt.Constants.NIL;
import static org.squiddev.cobalt.Constants.NONE;
import static org.squiddev.cobalt.ValueFactory.valueOf;
import static org.squiddev.cobalt.ValueFactory.varargsOf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaString;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaThread;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.OperationHelper;
import org.squiddev.cobalt.OrphanedThread;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.debug.DebugFrame;
import org.squiddev.cobalt.debug.DebugHandler;
import org.squiddev.cobalt.debug.DebugState;
import org.squiddev.cobalt.function.LibFunction;
import org.squiddev.cobalt.function.LuaFunction;
import org.squiddev.cobalt.function.VarArgFunction;
import org.squiddev.cobalt.lib.BaseLib;
import org.squiddev.cobalt.lib.CoroutineLib;
import org.squiddev.cobalt.lib.MathLib;
import org.squiddev.cobalt.lib.StringLib;
import org.squiddev.cobalt.lib.TableLib;
import org.squiddev.cobalt.lib.platform.AbstractResourceManipulator;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.ArgumentDelegator;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObject;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.IComputerEnvironment;
import dan200.computercraft.core.computer.ITask;
import dan200.computercraft.core.computer.MainThread;
import dan200.computercraft.core.lua.ILuaMachine;

/**
 * An rewrite of the Lua machine using cobalt
 *
 * @see dan200.computercraft.core.lua.LuaJLuaMachine
 */
public class CobaltMachine implements ILuaMachine, ILuaContext {

    private static final String[] ILLEGAL_NAMES = new String[] { "collectgarbage", "dofile", "loadfile", "print" };

    private static final Method getHost;

    static {
        Method host = null;
        try {
            host = IComputerEnvironment.class.getMethod("getHostString");
        } catch (NoSuchMethodException ignored) {} catch (RuntimeException e) {
            ComputerCraft.logger.error("Unknown error getting host string", e);
        }

        getHost = host;
    }

    private final Computer computer;
    private final LuaState state;
    private final LuaTable globals;
    private LuaThread mainThread;

    private String eventFilter = null;
    private String hardAbort = null;
    private String softAbort = null;

    public CobaltMachine(Computer computer) {
        this.computer = computer;

        final LuaState state = this.state = new LuaState(new AbstractResourceManipulator() {

            @Override
            public InputStream findResource(String s) {
                throw new IllegalStateException("Cannot open files");
            }
        });

        state.debug = new DebugHandler(state) {

            private int count = 0;
            private boolean hasSoftAbort;

            @Override
            public void onInstruction(DebugState ds, DebugFrame di, int pc, Varargs extras, int top) {
                int count = ++this.count;
                if (count > 100000) {
                    if (hardAbort != null) LuaThread.yield(state, NONE);
                    this.count = 0;
                } else if (ComputerCraft.timeoutError) {
                    handleSoftAbort();
                }

                super.onInstruction(ds, di, pc, extras, top);
            }

            @Override
            public void poll() {
                if (hardAbort != null) LuaThread.yield(state, NONE);
                if (ComputerCraft.timeoutError) handleSoftAbort();
            }

            public void handleSoftAbort() {
                // If the soft abort has been cleared then we can reset our flags and continue.
                String message = softAbort;
                if (message == null) {
                    hasSoftAbort = false;
                    return;
                }

                if (hasSoftAbort && hardAbort == null) {
                    // If we have been soft aborted but not hard aborted then everything is OK.
                    return;
                }

                hasSoftAbort = true;
                throw new LuaError(message);
            }
        };

        LuaTable globals = this.globals = new LuaTable();
        state.setupThread(globals);

        // Add basic libraries
        globals.load(state, new BaseLib());
        globals.load(state, new TableLib());
        globals.load(state, new StringLib());
        globals.load(state, new MathLib());
        globals.load(state, new CoroutineLib());

        LibFunction.bind(state, globals, PrefixLoader.class, new String[] { "load", "loadstring" });

        // if (Config.APIs.debug) globals.load(state, new DebugLib());
        // if (Config.APIs.profiler) globals.load(state, new ProfilerLib());
        if (ComputerCraft.bigInteger) BigIntegerValue.setup(globals);
        if (ComputerCraft.bitop) BitOpLib.setup(globals);

        for (String global : ILLEGAL_NAMES) {
            globals.rawset(global, Constants.NIL);
        }

        if (getHost != null) {
            try {
                // We have to use reflection for different CC versions
                globals.rawset(
                    "_HOST",
                    valueOf(
                        (String) getHost.invoke(
                            computer.getAPIEnvironment()
                                .getComputerEnvironment())));
            } catch (InvocationTargetException e) {
                ComputerCraft.logger.error("Cannot find getHostString", e);
            } catch (IllegalAccessException e) {
                ComputerCraft.logger.error("Cannot find getHostString", e);
            } catch (RuntimeException e) {
                ComputerCraft.logger.error("Unknown error with setting _HOST", e);
            }
        }

        globals.rawset("_CC_VERSION", valueOf(ComputerCraft.getVersion()));
        globals.rawset("_MC_VERSION", valueOf("1.7.10"));
        globals.rawset("_LUAJ_VERSION", valueOf("Cobalt 0.2"));
        if (ComputerCraft.disable_lua51_features) {
            globals.rawset("_CC_DISABLE_LUA51_FEATURES", Constants.TRUE);
        }
    }

    @Override
    public void addAPI(ILuaAPI api) {
        LuaValue table = wrapLuaObject(api);
        for (String name : api.getNames()) {
            globals.rawset(name, table);
        }
    }

    @Override
    public void loadBios(InputStream bios) {
        if (mainThread != null) return;
        try {
            LuaFunction value = LoadState.load(state, bios, "@bios.lua", globals);
            mainThread = new LuaThread(state, value, globals);
        } catch (LuaError e) {
            if (mainThread != null) {
                state.abandon();
                mainThread = null;
            }
        } catch (IOException e) {
            if (mainThread != null) {
                state.abandon();
                mainThread = null;
            }
        }
    }

    @Override
    public void handleEvent(String eventName, Object[] arguments) {
        if (mainThread == null) return;

        if (eventFilter == null || eventName == null
            || eventName.equals(eventFilter)
            || eventName.equals("terminate")) {
            try {
                Varargs args = Constants.NONE;
                if (eventName != null) {
                    Varargs params = toValues(arguments);
                    if (params.count() == 0) {
                        args = valueOf(eventName);
                    } else {
                        args = varargsOf(valueOf(eventName), params);
                    }
                }

                Varargs results = mainThread.resume(args);
                if (hardAbort != null) {
                    throw new LuaError(hardAbort);
                }

                if (!results.first()
                    .checkBoolean()) {
                    throw new LuaError(
                        results.arg(2)
                            .checkString());
                }

                LuaValue filter = results.arg(2);
                if (filter.isString()) {
                    eventFilter = filter.toString();
                } else {
                    eventFilter = null;
                }

                if (mainThread.getStatus()
                    .equals("dead")) {
                    mainThread = null;
                }
            } catch (LuaError e) {
                state.abandon();
                mainThread = null;
            } finally {
                softAbort = null;
                hardAbort = null;
            }

        }
    }

    @Override
    public void softAbort(String message) {
        softAbort = message;
    }

    @Override
    public void hardAbort(String message) {
        softAbort = message;
        hardAbort = message;
    }

    @Override
    public boolean saveState(OutputStream outputStream) {
        return false;
    }

    @Override
    public boolean restoreState(InputStream inputStream) {
        return false;
    }

    @Override
    public boolean isFinished() {
        return mainThread == null;
    }

    @Override
    public void unload() {
        if (this.mainThread == null) return;
        state.abandon();
        mainThread = null;
    }

    private LuaValue wrapLuaObject(final ILuaObject object) {
        String[] methods = object.getMethodNames();
        LuaTable result = new LuaTable(0, methods.length);

        for (int i = 0; i < methods.length; i++) {
            final int method = i;
            result.rawset(methods[i], new VarArgFunction() {

                @Override
                public Varargs invoke(LuaState state, Varargs args) {
                    if (ComputerCraft.timeoutError) {
                        String message = softAbort;
                        if (message != null) {
                            softAbort = null;
                            hardAbort = null;
                            throw new LuaError(message);
                        }
                    }

                    try {
                        Object[] results = ArgumentDelegator
                            .delegateLuaObject(object, CobaltMachine.this, method, new CobaltArguments(args));
                        return toValues(results);
                    } catch (LuaException e) {
                        throw new LuaError(e.getMessage(), e.getLevel());
                    } catch (InterruptedException e) {
                        throw new OrphanedThread();
                    } catch (Throwable e) {
                        throw new LuaError("Java Exception Thrown: " + e.toString(), 0);
                    }
                }
            });
        }

        return result;
    }

    // region Conversion
    public LuaValue toValue(Object object, Map<Object, LuaValue> tables) {
        if (object == null) {
            return NIL;
        } else if (object instanceof Number) {
            return valueOf(((Number) object).doubleValue());
        } else if (object instanceof Boolean) {
            return valueOf((Boolean) object);
        } else if (object instanceof String) {
            return valueOf(object.toString());
        } else if (object instanceof byte[]) {
            return valueOf((byte[]) object);
        } else if (object instanceof Map) {
            if (tables == null) {
                tables = new IdentityHashMap<Object, LuaValue>();
            } else {
                LuaValue value = tables.get(object);
                if (value != null) return value;
            }

            LuaTable table = new LuaTable();
            tables.put(object, table);

            for (Map.Entry<?, ?> pair : ((Map<?, ?>) object).entrySet()) {
                LuaValue key = toValue(pair.getKey(), tables);
                LuaValue value = toValue(pair.getValue(), tables);
                if (!key.isNil() && !value.isNil()) {
                    table.rawset(key, value);
                }
            }

            return table;
        } else if (object instanceof ILuaObject) {
            return wrapLuaObject((ILuaObject) object);
        } else {
            return NIL;
        }
    }

    public Varargs toValues(Object[] objects) {
        if (objects != null && objects.length != 0) {
            LuaValue[] values = new LuaValue[objects.length];

            for (int i = 0; i < objects.length; ++i) {
                Object object = objects[i];
                values[i] = toValue(object, null);
            }

            return varargsOf(values);
        } else {
            return NONE;
        }
    }
    // endregion

    @Override
    public Object[] pullEvent(String filter) throws LuaException, InterruptedException {
        Object[] results = pullEventRaw(filter);
        if (results.length >= 1 && results[0].equals("terminate")) {
            throw new LuaException("Terminated", 0);
        } else {
            return results;
        }
    }

    @Override
    public Object[] pullEventRaw(String filter) throws InterruptedException {
        return this.yield(new Object[] { filter });
    }

    @Override
    public Object[] yield(Object[] objects) throws InterruptedException {
        try {
            Varargs results = LuaThread.yield(state, toValues(objects));
            return CobaltConverter.toObjects(results, 0, false);
        } catch (OrphanedThread e) {
            throw new InterruptedException();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object[] executeMainThreadTask(final ILuaTask task) throws LuaException, InterruptedException {
        long taskID = issueMainThreadTask(task);

        Object[] response;
        do {
            do {
                response = this.pullEvent("task_complete");
            } while (response.length < 3);
        } while (!(response[1] instanceof Number) || !(response[2] instanceof Boolean)
            || (long) ((Number) response[1]).intValue() != taskID);

        if (!(Boolean) response[2]) {
            if (response.length >= 4 && response[3] instanceof String) {
                throw new LuaException((String) response[3]);
            } else {
                throw new LuaException();
            }
        } else {
            Object[] returnValues = new Object[response.length - 3];
            System.arraycopy(response, 3, returnValues, 0, returnValues.length);
            return returnValues;
        }
    }

    @Override
    public long issueMainThreadTask(final ILuaTask task) throws LuaException {
        final long taskID = MainThread.getUniqueTaskID();
        ITask generatedTask = new ITask() {

            @Override
            public Computer getOwner() {
                return computer;
            }

            @Override
            public void execute() {
                try {
                    Object[] t = task.execute();
                    if (t != null) {
                        Object[] eventArguments = new Object[t.length + 2];
                        eventArguments[0] = taskID;
                        eventArguments[1] = true;
                        System.arraycopy(t, 0, eventArguments, 2, t.length);

                        computer.queueEvent("task_complete", eventArguments);
                    } else {
                        computer.queueEvent("task_complete", new Object[] { taskID, true });
                    }
                } catch (LuaException e) {
                    computer.queueEvent("task_complete", new Object[] { taskID, false, e.getMessage() });
                } catch (Throwable e) {
                    computer.queueEvent(
                        "task_complete",
                        new Object[] { taskID, false, "Java Exception Thrown: " + e.toString() });
                }
            }
        };
        if (MainThread.queueTask(generatedTask)) {
            return taskID;
        } else {
            throw new LuaException("Task limit exceeded");
        }
    }

    private static class PrefixLoader extends VarArgFunction {

        private static final LuaString FUNCTION_STR = valueOf("function");
        private static final LuaString EQ_STR = valueOf("=");

        @Override
        public Varargs invoke(LuaState state, Varargs args) {
            switch (opcode) {
                case 0: // "load", // ( func [,chunkname] ) -> chunk | nil, msg
                {
                    LuaValue func = args.arg(1)
                        .checkFunction();
                    LuaString chunkname = args.arg(2)
                        .optLuaString(FUNCTION_STR);
                    if (!chunkname.startsWith('@') && !chunkname.startsWith('=')) {
                        chunkname = OperationHelper.concat(EQ_STR, chunkname);
                    }
                    return BaseLib.loadStream(state, new StringInputStream(state, func), chunkname);
                }
                case 1: // "loadstring", // ( string [,chunkname] ) -> chunk | nil, msg
                {
                    LuaString script = args.arg(1)
                        .checkLuaString();
                    LuaString chunkname = args.arg(2)
                        .optLuaString(script);
                    if (!chunkname.startsWith('@') && !chunkname.startsWith('=')) {
                        chunkname = OperationHelper.concat(EQ_STR, chunkname);
                    }
                    return BaseLib.loadStream(state, script.toInputStream(), chunkname);
                }
            }

            return NONE;
        }
    }

    private static class StringInputStream extends InputStream {

        private final LuaState state;
        private final LuaValue func;
        private byte[] bytes;
        private int offset, remaining = 0;

        public StringInputStream(LuaState state, LuaValue func) {
            this.state = state;
            this.func = func;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                LuaValue s = OperationHelper.call(state, func);
                if (s.isNil()) {
                    return -1;
                }
                LuaString ls = s.strvalue();
                bytes = ls.bytes;
                offset = ls.offset;
                remaining = ls.length;
                if (remaining <= 0) {
                    return -1;
                }
            }
            --remaining;
            return bytes[offset++];
        }
    }
}
