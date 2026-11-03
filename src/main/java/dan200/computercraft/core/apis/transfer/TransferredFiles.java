package dan200.computercraft.core.apis.transfer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObject;

/**
 * The list of files that have been transferred to this computer, as received by the
 * {@code file_transfer} event.
 *
 * <p>
 * A port of CC: Tweaked's {@code dan200.computercraft.core.apis.transfer.TransferredFiles}
 * (MPL-2.0; see NOTICE), minus the {@code @LuaFunction} machinery. Calling {@link #getFiles()}
 * marks the upload as consumed, which is what tells the client that queued it that the computer
 * actually took the files.
 * </p>
 */
public class TransferredFiles implements ILuaObject {

    public static final String EVENT = "file_transfer";

    private static final String[] METHOD_NAMES = { "getFiles" };

    private final List<TransferredFile> files;
    private final Runnable onConsumed;
    private final AtomicBoolean consumed = new AtomicBoolean(false);

    public TransferredFiles(List<TransferredFile> files, Runnable onConsumed) {
        this.files = files;
        this.onConsumed = onConsumed;
    }

    public TransferredFiles(List<TransferredFile> files) {
        this(files, null);
    }

    public List<TransferredFile> getFiles() {
        this.consumed();
        return this.files;
    }

    @Override
    public String[] getMethodNames() {
        return METHOD_NAMES;
    }

    @Override
    public Object[] callMethod(ILuaContext context, int method, Object[] args) {
        if (method != 0) {
            return null;
        }

        this.consumed();
        return new Object[] { toLuaList(this.files) };
    }

    /**
     * Converts the file list to the shape the legacy bridge understands. {@code CobaltMachine.toValue}
     * has no case for a {@code List}, so a list returned as-is becomes {@code nil} in Lua; a map with
     * 1-based numeric keys becomes a normal array-like table, which is what a Lua caller indexes and
     * what {@code #list} measures.
     */
    static Map<Integer, TransferredFile> toLuaList(List<TransferredFile> files) {
        Map<Integer, TransferredFile> list = new LinkedHashMap<>();
        for (int i = 0; i < files.size(); i++) {
            list.put(i + 1, files.get(i));
        }

        return list;
    }

    private void consumed() {
        if (this.consumed.getAndSet(true)) {
            return;
        }

        if (this.onConsumed != null) {
            this.onConsumed.run();
        }
    }
}
