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
import org.squiddev.cobalt.debug.DebugFrame;
import org.squiddev.cobalt.function.Dispatch;
import org.squiddev.cobalt.function.LibFunction;
import org.squiddev.cobalt.function.LuaFunction;
import org.squiddev.cobalt.function.ResumableVarArgFunction;
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
import dan200.computercraft.api.lua.MethodResult;
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
        // Must track the cobalt version declared in dependencies.gradle. Cobalt exposes no
        // runtime version constant and shadowing discards the jar's manifest metadata, so this
        // is a literal -- CobaltVersionTest reads dependencies.gradle and fails if the two
        // drift apart, which is how this said "0.6" while the dependency was on 0.9.9.
        globals.rawset("_COBALT_VERSION", valueOf("0.9.9"));
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
            // LoadState.load no longer throws IOException in Cobalt 0.9. The bios failing to
            // load leaves mainThread null, so isFinished() is true and the computer silently
            // refuses to start -- so this must not be swallowed.
            ComputerCraft.logger.error("Failed to load bios", e);
            if (mainThread != null) {
                close();
                mainThread = null;
            }
        }
    }

    @Override
    public void handleEvent(String eventName, Object[] arguments) {
        if (mainThread == null) return;

        // An API method parked on a MethodResult (a turtle movement, a scheduled task) is waiting
        // for exactly this kind of event, and this must be checked BEFORE the event filter below.
        // The filter is the Lua program's os.pullEvent filter and has no bearing on events a
        // peripheral is waiting for -- a computer that is mid turtle.forward() must still see
        // turtle_response even though the Lua filter is set to something else entirely.
        Varargs args = buildEventArgs(eventName, arguments);

        // A peripheral parked on a MethodResult is waiting for an event. Cobalt already knows how
        // to route an event to a suspended ResumableVarArgFunction -- the suspension recorded its
        // resumption point in the debug frame -- so delivery is just the ordinary run path. We only
        // need to decide whether this event is the one the peripheral is waiting for.
        if (pendingSuspend != null) {
            // A peripheral is parked waiting for an event. It gets every event, including
            // unrelated ones, and its MethodResult callback decides whether this is the one it
            // wanted -- if not, the call simply yields again. Filtering here rather than in the
            // callback would mean reimplementing the match, and the callback is what allows
            // cleanup (cancelling a timer, closing a resource) to run when the wait ends.
            runThread(args);
            return;
        }

        if (eventFilter == null || eventName == null
            || eventName.equals(eventFilter)
            || eventName.equals("terminate")) {
            runThread(args);
        }
    }

    /**
     * Run (or resume) the main thread with {@code args}, then apply the result to the machine.
     *
     * <p>
     * Mirrors CC: Tweaked's {@code CobaltLuaMachine.handleEvent}: the entry point is always
     * {@link LuaThread#run}, never {@link LuaThread#resume}. Cobalt records a suspension's
     * resumption point in the function's debug frame, so running a thread that is parked inside a
     * {@link ResumableVarArgFunction} delivers {@code args} straight to its {@code resume}. Using
     * resume() here instead makes Cobalt throw "cannot resume from a suspended thread".
     *
     * <p>
     * A null result means the thread suspended rather than finished, which is a pause.
     */
    private void runThread(Varargs args) {
        try {
            Varargs results = LuaThread.run(mainThread, args);
            if (hardAbort != null) {
                throw new HardAbortError();
            }

            // LuaThread.run returns null when the coroutine suspended rather than finished --
            // which is what os.pullEvent (coroutine.yield in bios.lua) does on the very first
            // event, and what a peripheral waiting on a MethodResult does. CC: Tweaked checks for
            // this and treats it as a pause. Without the check the following results.first()
            // throws NPE, which escapes handleEvent entirely and leaves the computer dead.
            if (results == null) {
                return;
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
        } catch (HardAbortError e) {
            // Expected when a computer is force-aborted or unloaded; not an error.
            close();
            mainThread = null;
        } catch (LuaError e) {
            // The only other signal is "Error resuming bios.lua" on the computer screen, which
            // is invisible from the log, so record it here too.
            ComputerCraft.logger.error("Error resuming bios.lua", e);
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

    // ==========================================================================
    // Coroutine suspension, replacing the old blocking ILuaContext#yield / #pullEvent.
    //
    // A peripheral that must wait (a turtle movement, a scheduled task) returns a
    // MethodResult from its callMethod. The wrapper below recognises that sentinel, records
    // what it is waiting for, and suspends the coroutine via LuaThread.yield -- which unwinds
    // to LuaThread.run in handleEvent and releases the computer's worker thread. When a
    // matching event arrives, handleEvent calls LuaThread.resume, which delivers the event to
    // CobaltCallback.resume below, and the original Lua call continues.
    //
    // This is a coroutine suspension, not a thread block. The old blocking design cannot work:
    // ComputerThread runs a computer's tasks serially on a shared worker pool, so a blocked
    // peripheral would block the very task that delivers the event it waits for.
    // ==========================================================================

    /**
     * The event {@link #issueMainThreadTask} results arrive on, as {@code (name, id, ok, ...)}.
     */
    private static final String TASK_COMPLETE_EVENT = "task_complete";

    /**
     * The suspension a waiting API method is currently parked on, or null.
     *
     * <p>
     * Only one API call can be outstanding per computer at a time: a Lua call is blocked inside
     * it, so it cannot have started another. Written by the computer thread while running the
     * machine and read by it again on the next event, so it does not need to be volatile -- the
     * lane is serial.
     */
    private MethodResult pendingSuspend;

    /**
     * Wraps one API method so it can suspend and be resumed.
     *
     * <p>
     * Cobalt treats every {@link ResumableVarArgFunction} as a potential suspension point, so the
     * retry loop that used to live in each peripheral is written once, here.
     */
    private final class CobaltCallback extends ResumableVarArgFunction<Object> {

        private final ILuaObject object;
        private final int method;

        CobaltCallback(ILuaObject object, int method) {
            this.object = object;
            this.method = method;
        }

        @Override
        protected Varargs invoke(LuaState state, DebugFrame frame, Varargs args) throws LuaError, UnwindThrowable {
            Object[] results;
            try {
                if (ComputerCraft.timeoutError) {
                    String message = softAbort;
                    if (message != null) {
                        softAbort = null;
                        hardAbort = null;
                        throw new LuaError(message);
                    }
                }

                results = ArgumentDelegator
                    .delegateLuaObject(object, CobaltMachine.this, method, new CobaltArguments(args));
            } catch (LuaException e) {
                throw new LuaError(e.getMessage(), e.getLevel());
            } catch (InterruptedException e) {
                throw new LuaError("Interrupted");
            } catch (Throwable e) {
                throw new LuaError("Java Exception Thrown: " + e.toString(), 0);
            }

            // The sentinel: a peripheral asking to wait hands back a single-element array
            // holding a MethodResult rather than values.
            if (results != null && results.length == 1 && results[0] instanceof MethodResult) {
                MethodResult pending = (MethodResult) results[0];
                if (!pending.isImmediate()) {
                    if (pendingSuspend != null) {
                        // Two concurrent waits on one machine is not representable; treat it as
                        // a programming error rather than silently dropping one of them.
                        throw new LuaError("Attempted to wait twice on the same computer");
                    }
                    pendingSuspend = pending;
                    // Suspends. handleEvent will either resume us with the matching event or
                    // leave us parked until one arrives.
                    return LuaThread.yield(state, NONE);
                }
                results = pending.getResults();
            }

            return toValues(results);
        }

        @Override
        public Varargs resume(LuaState state, Object token, Varargs value) throws LuaError, UnwindThrowable {
            MethodResult pending = pendingSuspend;
            if (pending == null) {
                return NONE;
            }

            // The callback decides: an immediate result finishes the call, another suspend means
            // this event was not the one it was waiting for and we keep waiting.
            MethodResult next;
            try {
                next = pending.resumeWith(toObjectArray(value));
            } catch (LuaException e) {
                pendingSuspend = null;
                throw new LuaError(e.getMessage(), e.getLevel());
            }

            if (next == null) {
                // Not for us. pendingSuspend is deliberately left set.
                return LuaThread.yield(state, NONE);
            }

            if (next.isImmediate()) {
                pendingSuspend = null;
                return toValues(next.getResults());
            }

            pendingSuspend = next;
            return LuaThread.yield(state, NONE);
        }

        @Override
        public Varargs resumeError(LuaState state, Object token, LuaError error) throws LuaError, UnwindThrowable {
            pendingSuspend = null;
            throw error;
        }
    }

    private LuaValue wrapLuaObject(final ILuaObject object) {
        String[] methods = object.getMethodNames();
        LuaTable result = new LuaTable(0, methods.length);

        for (int i = 0; i < methods.length; i++) {
            result.rawset(methods[i], new CobaltCallback(object, i));
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

    /**
     * Build the Cobalt varargs for an event, with the event name at index 0.
     */
    private Varargs buildEventArgs(String eventName, Object[] arguments) {
        if (eventName == null) {
            return Constants.NONE;
        }
        Varargs params = toValues(arguments);
        if (params.count() == 0) {
            return valueOf(eventName);
        }
        return varargsOf(valueOf(eventName), params);
    }

    /**
     * Convert a Cobalt {@link Varargs} into plain Java objects.
     *
     * <p>
     * Two things matter here, and both were bugs first:
     * <ul>
     * <li>{@link Varargs#arg(int)} is <b>1-based</b>. Using {@code arg(i)} with a 0-based loop
     * shifts every event by one, so index 0 comes back nil and the event name lands at index 1 --
     * which made every correlation check fail.</li>
     * <li>The LuaValues must be unwrapped to {@link String}/{@link Number}/{@link Boolean}, not
     * passed through: {@link MethodResult#matches(Object[])} compares against those Java types,
     * so a raw {@code LuaString} would never equal a {@code String}.</li>
     * </ul>
     */
    private static Object[] toObjectArray(Varargs args) {
        int count = args == null ? 0 : args.count();
        Object[] values = new Object[count];
        for (int i = 0; i < count; i++) {
            values[i] = toObject(args.arg(i + 1));
        }
        return values;
    }

    /**
     * Convert a Cobalt {@link LuaValue} into a plain Java object.
     *
     * <p>
     * This must test {@link LuaValue#type()} rather than {@link LuaValue#isString()}. Cobalt's
     * {@code isString()} is {@code type == TSTRING || type == TNUMBER} -- it answers "can this be
     * coerced to a string", which is true for numbers too. Checking it first turned every numeric
     * correlation id into the String {@code "1"}, which is not a {@link Number}, so every event
     * failed to match and suspended calls hung forever.
     */
    private static Object toObject(LuaValue value) {
        if (value == null || value.isNil()) {
            return null;
        }
        switch (value.type()) {
            case Constants.TNUMBER:
                return value.toDouble();
            case Constants.TSTRING:
                return value.toString();
            case Constants.TBOOLEAN:
                // LuaBoolean only exposes checkBoolean(), which throws on a non-boolean; the
                // type() check above already guarantees the type, so the rendered form is safe.
                return Boolean.valueOf(value.toString());
            default:
                return value;
        }
    }

    @Override
    public MethodResult executeMainThreadTask(final ILuaTask task) throws LuaException {
        long taskID = issueMainThreadTask(task);
        return MethodResult.task(TASK_COMPLETE_EVENT, taskID);
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
                // Standard Lua semantics are load(chunk [, chunkname [, mode [, env]]]) and
                // loadstring(string [, chunkname]), where chunk is a function OR a string.
                // bios.lua's loadfile calls load(<string>, name, "t", env), so both the string
                // form and the trailing mode/env arguments have to be accepted here.
                LuaValue chunk = args.arg(1);
                LuaValue target = opcode == 0 && args.arg(4)
                    .type() == Constants.TTABLE ? args.arg(4) : env;

                switch (opcode) {
                    case 0: // "load", // ( func|string [,chunkname [,mode [,env]]] ) -> chunk | nil, msg
                    {
                        LuaString chunkname = args.arg(2)
                            .isNil() ? FUNCTION_STR
                                : args.arg(2)
                                    .optLuaString(FUNCTION_STR);
                        if (!chunkname.startsWith(AT_PREFIX) && !chunkname.startsWith(EQ_PREFIX)) {
                            chunkname = prefixEquals(chunkname);
                        }
                        try {
                            InputStream stream = chunk.isString() ? chunk.checkLuaString()
                                .toInputStream() : new StringInputStream(state, chunk.checkFunction());
                            return LoadState.load(state, stream, chunkname, target);
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
