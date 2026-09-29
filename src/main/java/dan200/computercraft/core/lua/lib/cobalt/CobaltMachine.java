package dan200.computercraft.core.lua.lib.cobalt;

import static org.squiddev.cobalt.Constants.NIL;
import static org.squiddev.cobalt.Constants.NONE;
import static org.squiddev.cobalt.ValueFactory.valueOf;
import static org.squiddev.cobalt.ValueFactory.varargsOf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.IdentityHashMap;
import java.util.Map;

import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaString;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaThread;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.UnwindThrowable;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.function.Dispatch;
import org.squiddev.cobalt.function.LibFunction;
import org.squiddev.cobalt.function.LuaFunction;
import org.squiddev.cobalt.function.VarArgFunction;
import org.squiddev.cobalt.interrupt.InterruptAction;
import org.squiddev.cobalt.lib.BaseLib;
import org.squiddev.cobalt.lib.CoroutineLib;
import org.squiddev.cobalt.lib.MathLib;
import org.squiddev.cobalt.lib.StringLib;
import org.squiddev.cobalt.lib.TableLib;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.ArgumentDelegator;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObject;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.core.apis.ILuaAPI;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.ITask;
import dan200.computercraft.core.computer.MainThread;
import dan200.computercraft.core.lua.ILuaMachine;

/**
 * The Cobalt-based Lua machine. This is the sole Lua runtime used by ComputerCraft.
 */
public class CobaltMachine implements ILuaMachine, ILuaContext {

    private static final String[] ILLEGAL_NAMES = new String[] { "collectgarbage", "dofile", "loadfile", "print" };

    private final Computer computer;
    private final LuaState state;
    private final LuaTable globals;
    private LuaThread mainThread;

    private String eventFilter = null;

    /**
     * Written by the computer thread and read by the VM thread, so both must be volatile --
     * this mirrors the {@code volatile} hard-abort flag in CC: Tweaked's TimeoutState.
     */
    private volatile String hardAbort = null;
    private volatile String softAbort = null;

    /** Whether the current soft abort has already been delivered, as a catchable LuaError. */
    private boolean thrownSoftAbort;

    /**
     * Set when the machine is being closed. Mirrors CC: Tweaked's {@code isDisposed}: the
     * interrupt handler turns it into a {@link HardAbortError} which unwinds the VM, replacing
     * Cobalt 0.6's {@code state.abandon()}.
     */
    private volatile boolean isDisposed;

    public CobaltMachine(Computer computer) {
        this.computer = computer;

        final LuaState state = this.state = LuaState.builder()
            .interruptHandler(() -> {
                // Mirrors CC: Tweaked's CobaltLuaMachine interrupt handler. A hard abort is an
                // Error so Lua cannot pcall its way out of it; the soft abort is a catchable
                // LuaError, delivered at most once per abort.
                if (hardAbort != null || isDisposed) throw new HardAbortError();
                if (ComputerCraft.timeoutError && softAbort != null && !thrownSoftAbort) {
                    thrownSoftAbort = true;
                    throw new LuaError(softAbort);
                }
                // We have no compute-throttling concept, so there is never a reason to suspend.
                return InterruptAction.CONTINUE;
            })
            .build();

        // The state owns its global environment in Cobalt 0.9.
        LuaTable globals = this.globals = state.globals();

        // Add basic libraries. CC: Tweaked uses CoreLibraries.debugGlobals(state) here, but that
        // also installs DebugLib and Bit32Lib. ComputerCraft has always exposed just these five
        // and supplies its own bitop, so the individual add() methods keep behaviour identical.
        // These declare LuaError in Cobalt 0.9, but they cannot fail on a freshly built state.
        try {
            BaseLib.add(state);
            TableLib.add(state);
            StringLib.add(state);
            MathLib.add(state);
            CoroutineLib.add(state);
        } catch (LuaError e) {
            throw new RuntimeException("Failed to install standard libraries", e);
        }

        // LibFunction.bind and the VarArgFunction subclassing used by the old PrefixLoader are
        // both gone in Cobalt 0.9, so the two load variants are created through the public
        // LibFunction.createV factory and installed over the library versions.
        globals.rawset("load", PrefixLoader.create(0, globals));
        globals.rawset("loadstring", PrefixLoader.create(1, globals));

        // if (Config.APIs.debug) globals.load(state, new DebugLib());
        // if (Config.APIs.profiler) globals.load(state, new ProfilerLib());
        if (ComputerCraft.bigInteger) BigIntegerValue.setup(globals);
        if (ComputerCraft.bitop) BitOpLib.setup(globals);

        for (String global : ILLEGAL_NAMES) {
            globals.rawset(global, Constants.NIL);
        }

        // Always defined: IComputerEnvironment#getHostString() is a default method
        // returning "", so environments that do not identify themselves yield "".
        globals.rawset(
            "_HOST",
            valueOf(
                computer.getAPIEnvironment()
                    .getComputerEnvironment()
                    .getHostString()));

        globals.rawset("_CC_VERSION", valueOf(ComputerCraft.getVersion()));
        globals.rawset("_MC_VERSION", valueOf("1.7.10"));
        globals.rawset("_COBALT_VERSION", valueOf("0.6"));
        if (ComputerCraft.cc_default_settings != null && !ComputerCraft.cc_default_settings.isEmpty()) {
            globals.rawset("_CC_DEFAULT_SETTINGS", valueOf(ComputerCraft.cc_default_settings));
        }
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
            mainThread = new LuaThread(state, value);
        } catch (CompileException | LuaError e) {
            // LoadState.load no longer throws IOException in Cobalt 0.9.
            if (mainThread != null) {
                close();
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

                Varargs results = LuaThread.run(mainThread, args);
                if (hardAbort != null) {
                    throw new HardAbortError();
                }

                LuaValue filter = results.first();
                if (filter.isString()) {
                    eventFilter = filter.toString();
                } else {
                    eventFilter = null;
                }

                if (!mainThread.isAlive()) {
                    mainThread = null;
                }
            } catch (LuaError | HardAbortError e) {
                close();
                mainThread = null;
            } finally {
                softAbort = null;
                hardAbort = null;
                // Allows a subsequent soft abort to be delivered again, mirroring the
                // thrownSoftAbort reset in CC: Tweaked's updateTimeout().
                thrownSoftAbort = false;
            }

        }
    }

    @Override
    public void softAbort(String message) {
        softAbort = message;
        // Wake the VM so the abort is picked up at the next instruction boundary.
        state.interrupt();
    }

    @Override
    public void hardAbort(String message) {
        softAbort = message;
        hardAbort = message;
        state.interrupt();
    }

    /**
     * Forcibly stops a computer. Extends {@link Error} so that it cannot be caught by Lua's
     * {@code pcall}, mirroring CC: Tweaked's {@code HardAbortError}.
     */
    private static final class HardAbortError extends Error {

        private static final long serialVersionUID = 1L;

        private HardAbortError() {
            super("Hard Abort");
        }
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
        close();
        mainThread = null;
    }

    /**
     * Mirrors CC: Tweaked's {@code close()}: flag the machine as disposed and interrupt the VM
     * so the interrupt handler raises a {@link HardAbortError} and unwinds the thread.
     */
    private void close() {
        isDisposed = true;
        state.interrupt();
    }

    private LuaValue wrapLuaObject(final ILuaObject object) {
        String[] methods = object.getMethodNames();
        LuaTable result = new LuaTable(0, methods.length);

        for (int i = 0; i < methods.length; i++) {
            final int method = i;
            result.rawset(methods[i], new VarArgFunction() {

                @Override
                public Varargs invoke(LuaState state, Varargs args) throws LuaError {
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
                        throw new LuaError("Interrupted");
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
                    try {
                        table.rawset(key, value);
                    } catch (LuaError e) {
                        // LuaTable.rawset(LuaValue, LuaValue) is checked in Cobalt 0.9; with
                        // non-nil, non-NaN keys it cannot fail here.
                        throw new RuntimeException(e);
                    }
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

    // ==========================================================================
    // NOT YET PORTED -- TEMPORARY STUB. Every caller of this is currently broken.
    //
    // Cobalt 0.6's LuaThread.yieldBlocking() no longer exists in 0.9.9, and there is no
    // drop-in replacement: LuaThread.yield() always throws UnwindThrowable and carries no
    // resume value, and it explicitly refuses to yield the main thread. Suspending a
    // coroutine and later delivering a value now requires Cobalt's
    // ResumableVarArgFunction/Resumable protocol.
    //
    // That is exactly how CC: Tweaked does it, and it is a larger change than it looks: their
    // ILuaContext has no pullEvent/pullEventRaw/yield at all, because blocking APIs instead
    // return a MethodResult (see TaskCallback.make) which the Cobalt bridge turns into a
    // coroutine suspension. Porting that is the next piece of work.
    //
    // Consequence of this stub: ILuaContext.pullEvent / pullEventRaw / yield all fail, which
    // takes out LuaEnvironment.executeTask, TurtleBrain and WebSocketHandle. So any peripheral
    // that needs a blocking main-thread task, plus blocking HTTP/WebSocket calls, will throw
    // rather than work. The failure is loud on purpose -- it must not silently misbehave.
    // ==========================================================================
    @Override
    public Object[] yield(Object[] objects) {
        throw new UnsupportedOperationException(
            "Coroutine yield is not yet ported to Cobalt 0.9.9 -- see CobaltMachine.yield");
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

    private static class PrefixLoader {

        private static final LuaString FUNCTION_STR = valueOf("function");
        private static final byte EQ_PREFIX = (byte) '=';
        private static final byte AT_PREFIX = (byte) '@';

        private PrefixLoader() {}

        /** OperationHelper.concat is gone, so the "=" prefix is prepended byte-wise. */
        private static LuaString prefixEquals(LuaString chunkname) {
            int length = chunkname.length();
            byte[] joined = new byte[length + 1];
            joined[0] = EQ_PREFIX;
            for (int i = 0; i < length; i++) {
                joined[i + 1] = chunkname.byteAt(i);
            }
            return valueOf(joined);
        }

        static LuaValue create(final int opcode, final LuaValue env) {
            return LibFunction.createV((state, args) -> {
                switch (opcode) {
                    case 0: // "load", // ( func [,chunkname] ) -> chunk | nil, msg
                    {
                        LuaValue func = args.arg(1)
                            .checkFunction();
                        LuaString chunkname = args.arg(2)
                            .optLuaString(FUNCTION_STR);
                        if (!chunkname.startsWith(AT_PREFIX) && !chunkname.startsWith(EQ_PREFIX)) {
                            chunkname = prefixEquals(chunkname);
                        }
                        try {
                            return LoadState.load(state, new StringInputStream(state, func), chunkname, env);
                        } catch (Exception e) {
                            return varargsOf(NIL, valueOf(e.getMessage()));
                        }
                    }
                    case 1: // "loadstring", // ( string [,chunkname] ) -> chunk | nil, msg
                    {
                        LuaString script = args.arg(1)
                            .checkLuaString();
                        LuaString chunkname = args.arg(2)
                            .optLuaString(script);
                        if (!chunkname.startsWith(AT_PREFIX) && !chunkname.startsWith(EQ_PREFIX)) {
                            chunkname = prefixEquals(chunkname);
                        }
                        try {
                            return LoadState.load(state, script.toInputStream(), chunkname, env);
                        } catch (Exception e) {
                            return varargsOf(NIL, valueOf(e.getMessage()));
                        }
                    }
                    default:
                        return NONE;
                }
            });
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
                LuaValue s;
                try {
                    // OperationHelper.call is gone; Dispatch is the public call entry point.
                    s = Dispatch.invoke(state, func, Constants.NONE)
                        .first();
                } catch (LuaError | UnwindThrowable e) {
                    throw new IOException(e.getMessage());
                }
                if (s.isNil()) {
                    return -1;
                }
                LuaString ls;
                try {
                    ls = s.checkLuaString();
                } catch (LuaError e) {
                    throw new IOException(e.getMessage());
                }
                // LuaString's backing array is private in Cobalt 0.9.
                bytes = CobaltConverter.toByteArray(ls);
                offset = 0;
                remaining = bytes.length;
                if (remaining <= 0) {
                    return -1;
                }
            }
            --remaining;
            return bytes[offset++];
        }
    }
}
