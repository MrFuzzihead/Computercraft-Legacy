# Plan: Dialog Controller Peripheral

A new block peripheral (`dialog_controller`) that gives ComputerCraft computers read/write access
to the global CustomNPCs dialog registry via `IDialogHandler` / `IDialog`, and forwards dialog
lifecycle events (`dialog_open`, `dialog_option`, `dialog_closed`) to attached computers. Unlike
the NPC Interface peripheral, this peripheral is **not linked to a specific NPC** — dialogs are
global server data. It optionally filters events by a set of watched dialog IDs (or receives all
if `watchAll()` is active). Follows the manager + bridge pattern established in
`NPC_INTERFACE_PERIPHERAL_PLAN.md`.

---

## Architecture Overview

```
BlockDialogController → TileDialogController ←→ DialogControllerManager (global registry)
                               ↑                          ↑
                  DialogControllerPeripheral    DialogControllerBridge (Forge @SubscribeEvent)
                       (IPeripheral)             IDialogEvent.DialogOpen / DialogOption / DialogClosed
```

- `DialogControllerManager` — global singleton; maps dialog ID (or `WATCH_ALL = -1`) → set of
  tiles. `dispatch()` fans out to tiles watching the specific ID **plus** tiles watching `WATCH_ALL`,
  deduplicating via a `HashSet` union before iteration.
- `DialogControllerBridge` — registered on `AbstractNpcAPI.Instance().events()` at mod `init`.
  Dispatches all three dialog events to every matching tile.
- `TileDialogController` — holds a `Set<Integer> m_watchedIds` and a `boolean m_watchAll` in NBT.
  Receives events via `queueDialogEvent(String event, Object... params)`.

---

## Lua Method Table (38 methods)

Methods 0–5 manage watch state and **always work** (even without CustomNPCs installed).
Methods 6–37 require `AbstractNpcAPI.IsAvailable()` and run on the main thread via
`ILuaContext#executeMainThreadTask`.

### Watch management (0–5)

| # | Method          | Args         | Returns    | Notes                             |
|---|-----------------|--------------|------------|-----------------------------------|
| 0 | `watchDialog`   | `id: number` | —          | Add dialog ID to watch set        |
| 1 | `unwatchDialog` | `id: number` | —          | Remove dialog ID from watch set   |
| 2 | `watchAll`      | —            | —          | Receive events for every dialog   |
| 3 | `unwatchAll`    | —            | —          | Clear watchAll flag AND watch set |
| 4 | `isWatchingAll` | —            | `boolean`  |                                   |
| 5 | `getWatched`    | —            | `number[]` | List of watched dialog IDs        |

### Dialog data — read (6–17)

| #  | Method                 | Args                 | Returns            | Notes                        |
|----|------------------------|----------------------|--------------------|------------------------------|
| 6  | `getDialog`            | `id: number`         | `DialogTable\|nil` | Full dialog data table       |
| 7  | `getCategories`        | —                    | `CategoryTable[]`  | All dialog categories        |
| 8  | `getDialogsByCategory` | `categoryId: number` | `DialogTable[]`    | Dialogs in a category        |
| 9  | `getDialogOptions`     | `id: number`         | `OptionTable[]`    | All options for a dialog     |
| 10 | `getDialogOption`      | `id, slot: number`   | `OptionTable\|nil` | Single option by slot        |
| 11 | `getDialogText`        | `id: number`         | `string`           | `IDialog#getText()`          |
| 12 | `getDialogName`        | `id: number`         | `string`           | `IDialog#getName()`          |
| 13 | `getDialogColor`       | `id: number`         | `number`           | Body text color int          |
| 14 | `getDialogTitleColor`  | `id: number`         | `number`           | Title color int              |
| 15 | `getDialogSound`       | `id: number`         | `string`           | Open sound resource location |
| 16 | `getDialogCommand`     | `id: number`         | `string`           | Command fired on open        |
| 17 | `getDialogFlags`       | `id: number`         | `FlagsTable`       | All boolean rendering flags  |

### Dialog data — write (18–31)

| #  | Method                  | Args                  | Returns | Notes                               |
|----|-------------------------|-----------------------|---------|-------------------------------------|
| 18 | `setDialogName`         | `id, name: string`    | —       |                                     |
| 19 | `setDialogText`         | `id, text: string`    | —       |                                     |
| 20 | `setDialogColor`        | `id, color: number`   | —       |                                     |
| 21 | `setDialogTitleColor`   | `id, color: number`   | —       |                                     |
| 22 | `setDarkenScreen`       | `id, bool: boolean`   | —       |                                     |
| 23 | `setDisableEsc`         | `id, bool: boolean`   | —       |                                     |
| 24 | `setShowWheel`          | `id, bool: boolean`   | —       |                                     |
| 25 | `setHideNPC`            | `id, bool: boolean`   | —       |                                     |
| 26 | `setShowOptionLine`     | `id, bool: boolean`   | —       |                                     |
| 27 | `setShowPreviousBlocks` | `id, bool: boolean`   | —       |                                     |
| 28 | `setRenderGradual`      | `id, bool: boolean`   | —       |                                     |
| 29 | `setDialogSound`        | `id, sound: string`   | —       |                                     |
| 30 | `setDialogCommand`      | `id, command: string` | —       |                                     |
| 31 | `saveDialog`            | `id: number`          | —       | `IDialog#save()` — persists to disk |

### Dialog creation & utility (32–37)

| #  | Method            | Args                 | Returns       | Notes                                                |
|----|-------------------|----------------------|---------------|------------------------------------------------------|
| 32 | `createDialog`    | `categoryId: number` | `number`      | Returns new dialog ID via `IDialogCategory#create()` |
| 33 | `getAllDialogIds` | —                    | `number[]`    | Flat list of all IDs across all categories           |
| 34 | `getCategoryName` | `categoryId: number` | `string\|nil` |                                                      |
| 35 | `getCategoryId`   | `name: string`       | `number\|nil` | Lookup by name                                       |
| 36 | `dialogExists`    | `id: number`         | `boolean`     | `IDialogHandler#get(id) != null`                     |
| 37 | `getDialogCount`  | —                    | `number`      | Total dialogs across all categories                  |

---

## Lua Table Schemas

### `DialogTable`

```lua
{
  id                 = number,
  name               = string,    -- IDialog#getName()
  text               = string,    -- IDialog#getText()
  color              = number,    -- body text color int
  titleColor         = number,
  sound              = string,
  textSound          = string,
  textPitch          = number,
  command            = string,
  darkenScreen       = boolean,
  disableEsc         = boolean,
  showWheel          = boolean,
  hideNPC            = boolean,
  showOptionLine     = boolean,
  showPreviousBlocks = boolean,
  renderGradual      = boolean,
  categoryId         = number,    -- IDialog#getCategory()#getId()
  optionCount        = number,    -- IDialog#getOptions()#size()
}
```

### `OptionTable`

```lua
{
  slot = number,    -- IDialogOption#getSlot()
  name = string,    -- IDialogOption#getName()
  type = number,    -- 0=Quit, 1=Dialog, 2=Disabled, 3=Role, 4=CommandBlock
}
```

### `CategoryTable`

```lua
{
  id          = number,
  name        = string,
  dialogCount = number,
}
```

### `FlagsTable`

```lua
{
  darkenScreen       = boolean,
  disableEsc         = boolean,
  showWheel          = boolean,
  hideNPC            = boolean,
  showOptionLine     = boolean,
  showPreviousBlocks = boolean,
  renderGradual      = boolean,
}
```

---

## CC Events Queued to Attached Computers

Events are only queued to tiles that **watch** the fired dialog's ID or have `watchAll` active.

| Event           | Parameters                                                                   | Source                      |
|-----------------|------------------------------------------------------------------------------|-----------------------------|
| `dialog_open`   | `playerName: string, dialogId: number, dialogName: string`                   | `IDialogEvent.DialogOpen`   |
| `dialog_option` | `playerName: string, dialogId: number, optionId: number, optionName: string` | `IDialogEvent.DialogOption` |
| `dialog_closed` | `playerName: string, dialogId: number`                                       | `IDialogEvent.DialogClosed` |

---

## Implementation Steps

### Step 1 — `DialogControllerManager` (new class)

**File:** `dan200.computercraft.compat.customnpcs.peripheral.dialogcontroller.DialogControllerManager`

Global registry. Uses `WATCH_ALL = -1` as a sentinel key in the map.
Each `dispatchDialog*(int dialogId, ...)` method fans out to tiles watching `dialogId` AND tiles
watching `WATCH_ALL`, taking a synchronized snapshot before iteration.

```java
package dan200.computercraft.compat.customnpcs.peripheral.dialogcontroller;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DialogControllerManager {

    /** Sentinel key for watchAll tiles */
    public static final int WATCH_ALL = -1;

    private static final Map<Integer, Set<TileDialogController>> BY_DIALOG_ID =
        new ConcurrentHashMap<>();

    private DialogControllerManager() {}

    public static void register(int dialogId, TileDialogController tile) {
        BY_DIALOG_ID.computeIfAbsent(dialogId,
            k -> Collections.synchronizedSet(new HashSet<>())).add(tile);
    }

    public static void unregister(int dialogId, TileDialogController tile) {
        Set<TileDialogController> set = BY_DIALOG_ID.get(dialogId);
        if (set != null) {
            set.remove(tile);
            if (set.isEmpty()) BY_DIALOG_ID.remove(dialogId);
        }
    }

    private static void dispatch(int dialogId, String event, Object... params) {
        Set<TileDialogController> combined = new HashSet<>();
        Set<TileDialogController> specific = BY_DIALOG_ID.get(dialogId);
        if (specific != null) synchronized (specific) { combined.addAll(specific); }
        Set<TileDialogController> all = BY_DIALOG_ID.get(WATCH_ALL);
        if (all != null) synchronized (all) { combined.addAll(all); }
        for (TileDialogController tile : combined) tile.queueDialogEvent(event, params);
    }

    public static void dispatchOpen(int dialogId, String playerName, String dialogName) {
        dispatch(dialogId, "dialog_open", playerName, dialogId, dialogName);
    }

    public static void dispatchOption(int dialogId, String playerName,
                                      int optionId, String optionName) {
        dispatch(dialogId, "dialog_option", playerName, dialogId, optionId, optionName);
    }

    public static void dispatchClosed(int dialogId, String playerName) {
        dispatch(dialogId, "dialog_closed", playerName, dialogId);
    }
}
```

---

### Step 2 — `DialogControllerBridge` (new class)

**File:** `dan200.computercraft.compat.customnpcs.peripheral.dialogcontroller.DialogControllerBridge`

Subscribes to `IDialogEvent.DialogOpen`, `IDialogEvent.DialogOption`, `IDialogEvent.DialogClosed`.
These extend `IPlayerEvent` and are posted to the CustomNPCs `EventBus`.

> **Note:** Use the concrete `noppes.npcs.scripted.event.player.DialogEvent.*` classes (not the
> `IDialogEvent.*` interfaces). FML's EventBus dispatches by concrete class hierarchy only.

```java
package dan200.computercraft.compat.customnpcs.peripheral.dialogcontroller;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import noppes.npcs.scripted.event.player.DialogEvent;
import noppes.npcs.api.handler.data.IDialog;
import noppes.npcs.api.handler.data.IDialogOption;

public class DialogControllerBridge {

    @SubscribeEvent
    public void onDialogOpen(DialogEvent.DialogOpen e) {
        IDialog d = e.getDialog();
        String dialogName = d != null ? d.getName() : "";
        DialogControllerManager.dispatchOpen(
            e.getDialogId(), e.getPlayer().getName(), dialogName);
    }

    @SubscribeEvent
    public void onDialogOption(DialogEvent.DialogOption e) {
        IDialog d = e.getDialog();
        String optionName = "";
        if (d != null) {
            IDialogOption opt = d.getOption(e.getOptionId());
            if (opt != null) optionName = opt.getName();
        }
        DialogControllerManager.dispatchOption(
            e.getDialogId(), e.getPlayer().getName(), e.getOptionId(), optionName);
    }

    @SubscribeEvent
    public void onDialogClosed(DialogEvent.DialogClosed e) {
        DialogControllerManager.dispatchClosed(e.getDialogId(), e.getPlayer().getName());
    }
}
```

---

### Step 3 — `TileDialogController` (new tile entity)

**File:** `dan200.computercraft.compat.customnpcs.peripheral.dialogcontroller.TileDialogController`

Persists `m_watchAll` (boolean) and `m_watchedIds` (Set<Integer>) to NBT.
On `validate()`, re-registers all watch entries with `DialogControllerManager`.
On `invalidate()` / `onChunkUnload()`, unregisters all.

```java
package dan200.computercraft.compat.customnpcs.peripheral.dialogcontroller;

import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.common.TileGeneric;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.IPeripheralTile;
import dan200.computercraft.ComputerCraft;

public class TileDialogController extends TileGeneric implements IPeripheralTile {

    private boolean m_watchAll = false;
    private final Set<Integer> m_watchedIds = new HashSet<>();
    private final Set<IComputerAccess> m_computers = new HashSet<>();

    // -------------------------------------------------------------------------
    // Watch management
    // -------------------------------------------------------------------------

    public synchronized void watchDialog(int id) {
        if (m_watchedIds.add(id)) DialogControllerManager.register(id, this);
        markDirty();
    }

    public synchronized void unwatchDialog(int id) {
        if (m_watchedIds.remove(id)) DialogControllerManager.unregister(id, this);
        markDirty();
    }

    public synchronized void setWatchAll(boolean watch) {
        if (m_watchAll == watch) return;
        m_watchAll = watch;
        if (watch) {
            DialogControllerManager.register(DialogControllerManager.WATCH_ALL, this);
        } else {
            DialogControllerManager.unregister(DialogControllerManager.WATCH_ALL, this);
        }
        markDirty();
    }

    public synchronized void clearAllWatches() {
        for (int id : m_watchedIds) DialogControllerManager.unregister(id, this);
        m_watchedIds.clear();
        if (m_watchAll) {
            DialogControllerManager.unregister(DialogControllerManager.WATCH_ALL, this);
            m_watchAll = false;
        }
        markDirty();
    }

    public synchronized boolean isWatchingAll() { return m_watchAll; }

    public synchronized int[] getWatchedIds() {
        return m_watchedIds.stream().mapToInt(Integer::intValue).toArray();
    }

    // -------------------------------------------------------------------------
    // Event dispatch (called by DialogControllerManager)
    // -------------------------------------------------------------------------

    public void queueDialogEvent(String event, Object... params) {
        Set<IComputerAccess> snapshot;
        synchronized (this) { snapshot = new HashSet<>(m_computers); }
        for (IComputerAccess computer : snapshot) computer.queueEvent(event, params);
    }

    // -------------------------------------------------------------------------
    // IPeripheralTile
    // -------------------------------------------------------------------------

    @Override
    public PeripheralType getPeripheralType() { return PeripheralType.DialogController; }

    @Override
    public IPeripheral getPeripheral(int side) { return new DialogControllerPeripheral(this); }

    @Override public String getLabel() { return null; }
    @Override public int getDirection() { return 2; }
    @Override public void setDirection(int dir) {}

    // -------------------------------------------------------------------------
    // Computer tracking
    // -------------------------------------------------------------------------

    public synchronized void attachComputer(IComputerAccess computer) {
        m_computers.add(computer);
    }

    public synchronized void detachComputer(IComputerAccess computer) {
        m_computers.remove(computer);
    }

    // -------------------------------------------------------------------------
    // TileEntity lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void validate() {
        super.validate();
        synchronized (this) {
            for (int id : m_watchedIds) DialogControllerManager.register(id, this);
            if (m_watchAll) DialogControllerManager.register(
                DialogControllerManager.WATCH_ALL, this);
        }
    }

    @Override
    public void invalidate() {
        synchronized (this) {
            for (int id : m_watchedIds) DialogControllerManager.unregister(id, this);
            if (m_watchAll) DialogControllerManager.unregister(
                DialogControllerManager.WATCH_ALL, this);
        }
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        synchronized (this) {
            for (int id : m_watchedIds) DialogControllerManager.unregister(id, this);
            if (m_watchAll) DialogControllerManager.unregister(
                DialogControllerManager.WATCH_ALL, this);
        }
        super.onChunkUnload();
    }

    // -------------------------------------------------------------------------
    // NBT
    // -------------------------------------------------------------------------

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        m_watchAll = nbt.getBoolean("watchAll");
        m_watchedIds.clear();
        if (nbt.hasKey("watchedIds")) {
            NBTTagList list = nbt.getTagList("watchedIds", 3 /* TAG_Int */);
            for (int i = 0; i < list.tagCount(); i++) {
                m_watchedIds.add(list.getIntAt(i));
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("watchAll", m_watchAll);
        NBTTagList list = new NBTTagList();
        for (int id : m_watchedIds) {
            net.minecraft.nbt.NBTTagInt tag = new net.minecraft.nbt.NBTTagInt(id);
            list.appendTag(tag);
        }
        nbt.setTag("watchedIds", list);
    }

    // -------------------------------------------------------------------------
    // Block drops
    // -------------------------------------------------------------------------

    @Override
    public void getDroppedItems(java.util.List<ItemStack> drops, int fortune,
                                boolean creative, boolean silkTouch) {
        if (!creative) drops.add(new ItemStack(ComputerCraft.Blocks.dialogController));
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(ComputerCraft.Blocks.dialogController);
    }
}
```

---

### Step 4 — `DialogControllerPeripheral` (new peripheral)

**File:** `dan200.computercraft.compat.customnpcs.peripheral.dialogcontroller.DialogControllerPeripheral`

All methods calling `IDialogHandler` run on the main thread via `executeMainThreadTask`.
Helper `resolveDialog(id)` calls `AbstractNpcAPI.Instance().getDialogs().get(id)` and throws
`LuaException` if null.

```java
package dan200.computercraft.compat.customnpcs.peripheral.dialogcontroller;

import java.util.*;
import noppes.npcs.api.AbstractNpcAPI;
import noppes.npcs.api.handler.IDialogHandler;
import noppes.npcs.api.handler.data.IDialog;
import noppes.npcs.api.handler.data.IDialogCategory;
import noppes.npcs.api.handler.data.IDialogOption;
import dan200.computercraft.api.lua.*;
import dan200.computercraft.api.peripheral.*;

public class DialogControllerPeripheral implements IPeripheral {

    private static final String[] METHOD_NAMES = {
        // Watch management (0–5)
        "watchDialog", "unwatchDialog", "watchAll", "unwatchAll",
        "isWatchingAll", "getWatched",
        // Dialog read (6–17)
        "getDialog", "getCategories", "getDialogsByCategory",
        "getDialogOptions", "getDialogOption",
        "getDialogText", "getDialogName", "getDialogColor", "getDialogTitleColor",
        "getDialogSound", "getDialogCommand", "getDialogFlags",
        // Dialog write (18–31)
        "setDialogName", "setDialogText", "setDialogColor", "setDialogTitleColor",
        "setDarkenScreen", "setDisableEsc", "setShowWheel", "setHideNPC",
        "setShowOptionLine", "setShowPreviousBlocks", "setRenderGradual",
        "setDialogSound", "setDialogCommand", "saveDialog",
        // Creation & utility (32–37)
        "createDialog", "getAllDialogIds", "getCategoryName",
        "getCategoryId", "dialogExists", "getDialogCount"
    };

    private final TileDialogController m_tile;

    public DialogControllerPeripheral(TileDialogController tile) {
        this.m_tile = tile;
    }

    @Override public String getType() { return "dialog_controller"; }
    @Override public String[] getMethodNames() { return METHOD_NAMES; }

    @Override public void attach(IComputerAccess c) { m_tile.attachComputer(c); }
    @Override public void detach(IComputerAccess c) { m_tile.detachComputer(c); }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof DialogControllerPeripheral
            && ((DialogControllerPeripheral) other).m_tile == m_tile;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context,
                               int method, Object[] args)
        throws LuaException, InterruptedException {

        switch (method) {
            // ---- Watch management (no CNPC required) --------------------
            case 0: { m_tile.watchDialog(requireInt(args, 0, "id")); return null; }
            case 1: { m_tile.unwatchDialog(requireInt(args, 0, "id")); return null; }
            case 2: { m_tile.setWatchAll(true); return null; }
            case 3: { m_tile.clearAllWatches(); return null; }
            case 4: { return new Object[]{ m_tile.isWatchingAll() }; }
            case 5: {
                int[] ids = m_tile.getWatchedIds();
                Object[] result = new Object[ids.length];
                for (int i = 0; i < ids.length; i++) result[i] = ids[i];
                return new Object[]{ result };
            }

            // ---- Dialog read (CNPC required) ----------------------------
            case 6: {
                int id = requireInt(args, 0, "id");
                return dialogs(context, h -> {
                    IDialog d = h.get(id);
                    return new Object[]{ d != null ? dialogTable(d) : null };
                });
            }
            case 7: {
                return dialogs(context, h -> {
                    List<IDialogCategory> cats = h.categories();
                    Object[] result = new Object[cats.size()];
                    for (int i = 0; i < cats.size(); i++) result[i] = categoryTable(cats.get(i));
                    return new Object[]{ result };
                });
            }
            case 8: {
                int catId = requireInt(args, 0, "categoryId");
                return dialogs(context, h -> {
                    for (IDialogCategory cat : h.categories()) {
                        if (cat.getId() == catId) {
                            List<IDialog> ds = cat.dialogs();
                            Object[] result = new Object[ds.size()];
                            for (int i = 0; i < ds.size(); i++) result[i] = dialogTable(ds.get(i));
                            return new Object[]{ result };
                        }
                    }
                    return new Object[]{ new Object[0] };
                });
            }
            case 9: {
                int id = requireInt(args, 0, "id");
                return dialogs(context, h -> {
                    IDialog d = resolveDialog(h, id);
                    List<IDialogOption> opts = d.getOptions();
                    Object[] result = new Object[opts.size()];
                    for (int i = 0; i < opts.size(); i++) result[i] = optionTable(opts.get(i));
                    return new Object[]{ result };
                });
            }
            case 10: {
                int id = requireInt(args, 0, "id");
                int slot = requireInt(args, 1, "slot");
                return dialogs(context, h -> {
                    IDialogOption opt = resolveDialog(h, id).getOption(slot);
                    return new Object[]{ opt != null ? optionTable(opt) : null };
                });
            }
            case 11: {
                int id = requireInt(args, 0, "id");
                return dialogs(context, h -> new Object[]{ resolveDialog(h, id).getText() });
            }
            case 12: {
                int id = requireInt(args, 0, "id");
                return dialogs(context, h -> new Object[]{ resolveDialog(h, id).getName() });
            }
            case 13: {
                int id = requireInt(args, 0, "id");
                return dialogs(context, h -> new Object[]{ resolveDialog(h, id).getColor() });
            }
            case 14: {
                int id = requireInt(args, 0, "id");
                return dialogs(context, h -> new Object[]{ resolveDialog(h, id).getTitleColor() });
            }
            case 15: {
                int id = requireInt(args, 0, "id");
                return dialogs(context, h -> new Object[]{ resolveDialog(h, id).getSound() });
            }
            case 16: {
                int id = requireInt(args, 0, "id");
                return dialogs(context, h -> new Object[]{ resolveDialog(h, id).getCommand() });
            }
            case 17: {
                int id = requireInt(args, 0, "id");
                return dialogs(context, h -> new Object[]{ flagsTable(resolveDialog(h, id)) });
            }

            // ---- Dialog write -------------------------------------------
            case 18: {
                int id = requireInt(args, 0, "id");
                String v = requireString(args, 1, "name");
                return dialogs(context, h -> { resolveDialog(h, id).setName(v); return null; });
            }
            case 19: {
                int id = requireInt(args, 0, "id");
                String v = requireString(args, 1, "text");
                return dialogs(context, h -> { resolveDialog(h, id).setText(v); return null; });
            }
            case 20: {
                int id = requireInt(args, 0, "id");
                int v = requireInt(args, 1, "color");
                return dialogs(context, h -> { resolveDialog(h, id).setColor(v); return null; });
            }
            case 21: {
                int id = requireInt(args, 0, "id");
                int v = requireInt(args, 1, "color");
                return dialogs(context, h -> { resolveDialog(h, id).setTitleColor(v); return null; });
            }
            case 22: {
                int id = requireInt(args, 0, "id");
                boolean v = requireBoolean(args, 1, "darkenScreen");
                return dialogs(context, h -> { resolveDialog(h, id).setDarkenScreen(v); return null; });
            }
            case 23: {
                int id = requireInt(args, 0, "id");
                boolean v = requireBoolean(args, 1, "disableEsc");
                return dialogs(context, h -> { resolveDialog(h, id).setDisableEsc(v); return null; });
            }
            case 24: {
                int id = requireInt(args, 0, "id");
                boolean v = requireBoolean(args, 1, "showWheel");
                return dialogs(context, h -> { resolveDialog(h, id).setShowWheel(v); return null; });
            }
            case 25: {
                int id = requireInt(args, 0, "id");
                boolean v = requireBoolean(args, 1, "hideNPC");
                return dialogs(context, h -> { resolveDialog(h, id).setHideNPC(v); return null; });
            }
            case 26: {
                int id = requireInt(args, 0, "id");
                boolean v = requireBoolean(args, 1, "show");
                return dialogs(context, h -> { resolveDialog(h, id).showOptionLine(v); return null; });
            }
            case 27: {
                int id = requireInt(args, 0, "id");
                boolean v = requireBoolean(args, 1, "show");
                return dialogs(context, h -> { resolveDialog(h, id).showPreviousBlocks(v); return null; });
            }
            case 28: {
                int id = requireInt(args, 0, "id");
                boolean v = requireBoolean(args, 1, "gradual");
                return dialogs(context, h -> { resolveDialog(h, id).renderGradual(v); return null; });
            }
            case 29: {
                int id = requireInt(args, 0, "id");
                String v = requireString(args, 1, "sound");
                return dialogs(context, h -> { resolveDialog(h, id).setSound(v); return null; });
            }
            case 30: {
                int id = requireInt(args, 0, "id");
                String v = requireString(args, 1, "command");
                return dialogs(context, h -> { resolveDialog(h, id).setCommand(v); return null; });
            }
            case 31: {
                int id = requireInt(args, 0, "id");
                return dialogs(context, h -> { resolveDialog(h, id).save(); return null; });
            }

            // ---- Creation & utility -------------------------------------
            case 32: {
                int catId = requireInt(args, 0, "categoryId");
                return dialogs(context, h -> {
                    for (IDialogCategory cat : h.categories()) {
                        if (cat.getId() == catId) {
                            IDialog d = cat.create();
                            return new Object[]{ d.getId() };
                        }
                    }
                    throw new LuaException("No category found with ID " + catId);
                });
            }
            case 33: {
                return dialogs(context, h -> {
                    List<Integer> ids = new ArrayList<>();
                    for (IDialogCategory cat : h.categories())
                        for (IDialog d : cat.dialogs()) ids.add(d.getId());
                    return new Object[]{ ids.toArray() };
                });
            }
            case 34: {
                int catId = requireInt(args, 0, "categoryId");
                return dialogs(context, h -> {
                    for (IDialogCategory cat : h.categories())
                        if (cat.getId() == catId) return new Object[]{ cat.getName() };
                    return new Object[]{ null };
                });
            }
            case 35: {
                String name = requireString(args, 0, "name");
                return dialogs(context, h -> {
                    for (IDialogCategory cat : h.categories())
                        if (name.equals(cat.getName())) return new Object[]{ cat.getId() };
                    return new Object[]{ null };
                });
            }
            case 36: {
                int id = requireInt(args, 0, "id");
                return dialogs(context, h -> new Object[]{ h.get(id) != null });
            }
            case 37: {
                return dialogs(context, h -> {
                    int count = 0;
                    for (IDialogCategory cat : h.categories()) count += cat.dialogs().size();
                    return new Object[]{ count };
                });
            }

            default: return null;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @FunctionalInterface
    private interface DialogHandlerAction {
        Object[] run(IDialogHandler handler) throws LuaException;
    }

    private Object[] dialogs(ILuaContext ctx, DialogHandlerAction action)
        throws LuaException, InterruptedException {
        if (!AbstractNpcAPI.IsAvailable()) throw new LuaException("CustomNPCs is not installed");
        return ctx.executeMainThreadTask(() -> action.run(
            AbstractNpcAPI.Instance().getDialogs()));
    }

    private static IDialog resolveDialog(IDialogHandler h, int id) throws LuaException {
        IDialog d = h.get(id);
        if (d == null) throw new LuaException("No dialog found with ID " + id);
        return d;
    }

    private static Map<String, Object> dialogTable(IDialog d) {
        Map<String, Object> t = new HashMap<>();
        t.put("id", d.getId());
        t.put("name", d.getName());
        t.put("text", d.getText());
        t.put("color", d.getColor());
        t.put("titleColor", d.getTitleColor());
        t.put("sound", d.getSound());
        t.put("textSound", d.getTextSound());
        t.put("textPitch", (double) d.getTextPitch());
        t.put("command", d.getCommand());
        t.put("darkenScreen", d.getDarkenScreen());
        t.put("disableEsc", d.getDisableEsc());
        t.put("showWheel", d.getShowWheel());
        t.put("hideNPC", d.getHideNPC());
        t.put("showOptionLine", d.showOptionLine());
        t.put("showPreviousBlocks", d.showPreviousBlocks());
        t.put("renderGradual", d.renderGradual());
        t.put("categoryId", d.getCategory() != null ? d.getCategory().getId() : -1);
        t.put("optionCount", d.getOptions().size());
        return t;
    }

    private static Map<String, Object> optionTable(IDialogOption o) {
        Map<String, Object> t = new HashMap<>();
        t.put("slot", o.getSlot());
        t.put("name", o.getName());
        t.put("type", o.getType());
        return t;
    }

    private static Map<String, Object> categoryTable(IDialogCategory c) {
        Map<String, Object> t = new HashMap<>();
        t.put("id", c.getId());
        t.put("name", c.getName());
        t.put("dialogCount", c.dialogs().size());
        return t;
    }

    private static Map<String, Object> flagsTable(IDialog d) {
        Map<String, Object> t = new HashMap<>();
        t.put("darkenScreen", d.getDarkenScreen());
        t.put("disableEsc", d.getDisableEsc());
        t.put("showWheel", d.getShowWheel());
        t.put("hideNPC", d.getHideNPC());
        t.put("showOptionLine", d.showOptionLine());
        t.put("showPreviousBlocks", d.showPreviousBlocks());
        t.put("renderGradual", d.renderGradual());
        return t;
    }

    private static int requireInt(Object[] args, int i, String name) throws LuaException {
        if (args.length <= i || !(args[i] instanceof Number))
            throw new LuaException("Expected number for " + name);
        return ((Number) args[i]).intValue();
    }

    private static String requireString(Object[] args, int i, String name) throws LuaException {
        if (args.length <= i || !(args[i] instanceof String))
            throw new LuaException("Expected string for " + name);
        return (String) args[i];
    }

    private static boolean requireBoolean(Object[] args, int i, String name) throws LuaException {
        if (args.length <= i || !(args[i] instanceof Boolean))
            throw new LuaException("Expected boolean for " + name);
        return (Boolean) args[i];
    }
}
```

---

### Step 5 — `BlockDialogController` (new block)

**File:** `dan200.computercraft.compat.customnpcs.peripheral.dialogcontroller.BlockDialogController`

Modeled directly after `BlockChatBox`.

```java
// Mirrors BlockChatBox structure:
// createTile(int metadata) → new TileDialogController()
// setBlockName("computercraft:dialog_controller")
// Hardness 2.0, material iron, creative tab main
// Three texture slots: dialogControllerTop, dialogControllerSide, dialogControllerFront
```

---

### Step 6 — Wire into `ComputerCraft.java` and support files

**`PeripheralType.java`** — add `DialogController` to the enum.

**`ComputerCraft.java` — `Blocks` inner class:**
```java
public static BlockDialogController dialogController;
```

**`ComputerCraft.java` — `preInit`:**
```java
Blocks.dialogController = new BlockDialogController();
GameRegistry.registerBlock(Blocks.dialogController, "dialog_controller");
GameRegistry.registerTileEntity(TileDialogController.class, "computercraft:dialog_controller");
// Suggested recipe: Book + Advanced Computer + Redstone (shapeless)
GameRegistry.addShapelessRecipe(new ItemStack(Blocks.dialogController),
    new ItemStack(Items.book),
    new ItemStack(Items.redstone),
    new ItemStack(Blocks.computerAdvanced)
);
```

**`ComputerCraft.java` — `init` (alongside existing bridges):**
```java
if (AbstractNpcAPI.IsAvailable()) {
    AbstractNpcAPI.Instance().events().register(new CustomNpcChatBoxBridge());
    AbstractNpcAPI.Instance().events().register(new NpcInterfaceBridge());
    AbstractNpcAPI.Instance().events().register(new DialogControllerBridge()); // ADD
    logger.info("[ComputerCraft] DialogControllerBridge registered.");
}
```

---

### Step 7 — Tests

**`DialogControllerManagerTest.java`**
- Register mock tile for ID 5. Dispatch `dispatchOpen(5, "Steve", "Greeting")`.
  Assert tile received `queueDialogEvent("dialog_open", "Steve", 5, "Greeting")`.
- Unregister tile. Dispatch again. Assert no call received.
- Register tile with `WATCH_ALL`. Dispatch `dispatchOpen(99, "Alex", "Quest")`.
  Assert tile received the event (ID 99 not explicitly watched).
- Register tile for ID 5 AND a different tile with `WATCH_ALL`. Dispatch ID 5.
  Assert both tiles receive the event exactly once (deduplication via `HashSet` union).
- Dispatch `dispatchOption` and `dispatchClosed` — assert correct event names and param ordering.

**`DialogControllerPeripheralTest.java`**
- `getMethodNames()` returns exactly 38 entries.
- Methods 0–5 operate correctly without CNPC installed.
- Methods 6–37 throw `LuaException("CustomNPCs is not installed")` when CNPC unavailable.
- `watchDialog(5)` → `getWatched()` returns `{5}`.
- `unwatchDialog(5)` → `getWatched()` returns `{}`.
- `watchAll()` → `isWatchingAll()` returns `true`.
- `unwatchAll()` → `isWatchingAll()` returns `false`, `getWatched()` is empty.
- `getDialog(id)` returns correct `DialogTable` fields from mocked `IDialog`.
- `dialogExists(id)` returns `false` when `IDialogHandler#get` returns null.
- `saveDialog(id)` calls `IDialog#save()` on the mock.
- `setDialogText(id, "Hello")` calls `IDialog#setText("Hello")` on the mock.
- `getCategories()` returns correctly shaped `CategoryTable[]` from mocked handler.

---

## Files Created / Changed

| File                                                                                      | Type                                        |
|-------------------------------------------------------------------------------------------|---------------------------------------------|
| `compat/customnpcs/peripheral/dialogcontroller/BlockDialogController.java`                | New                                         |
| `compat/customnpcs/peripheral/dialogcontroller/TileDialogController.java`                 | New                                         |
| `compat/customnpcs/peripheral/dialogcontroller/DialogControllerPeripheral.java`           | New                                         |
| `compat/customnpcs/peripheral/dialogcontroller/DialogControllerManager.java`              | New                                         |
| `compat/customnpcs/peripheral/dialogcontroller/DialogControllerBridge.java`               | New                                         |
| `shared/peripheral/PeripheralType.java`                                                   | Add `DialogController`                      |
| `ComputerCraft.java`                                                                      | Register block, tile entity, bridge, recipe |
| `assets/computercraft/textures/dialogController*.png`                                     | New (3 placeholder textures)                |
| `test/.../DialogControllerManagerTest.java`                                               | New                                         |
| `test/.../DialogControllerPeripheralTest.java`                                            | New                                         |

## Files NOT Changed

| File                          | Reason                          |
|-------------------------------|---------------------------------|
| `NpcInterfacePeripheral.java` | Separate peripheral; no overlap |
| `ChatBoxPeripheral.java`      | Unrelated                       |
| `TraderRolePeripheral.java`   | Unrelated                       |

---

## Open Questions / Decisions Before Implementation

1. **`DialogEvent` EventBus target**: Confirmed — `noppes.npcs.scripted.event.player.DialogEvent.*`
   classes extend `CustomNPCsEvent` which extends FML `Event`, and are posted to
   `AbstractNpcAPI.Instance().events()`. Use concrete `DialogEvent.*` classes in `@SubscribeEvent`
   handlers (not `IDialogEvent.*` interfaces — FML EventBus does not walk interfaces).

2. **`optionId` vs. slot**: `IDialog#getOption(int)` takes a slot index, but `IDialogEvent`
   exposes `getOptionId()`. Confirm whether `optionId` equals the slot index in CustomNPCs
   before using `d.getOption(e.getOptionId())` in the bridge; may need to iterate `getOptions()`
   and match by position instead.

3. **`IDialog#setTextHeight()` API typo**: The API declares `int setTextHeight()` (getter name is
   wrong — should be `getTextHeight`). Do not expose this via the peripheral until the upstream
   API is corrected. Document as a known gap.

4. **Deduplication correctness**: A tile watching ID 5 AND `watchAll` must receive each event
   exactly once. The `HashSet` union in `DialogControllerManager#dispatch` ensures this; the
   test suite must explicitly cover this case.

5. **No `createCategory` API**: `IDialogHandler` exposes no category creation method. Method 32
   (`createDialog`) creates a dialog within an *existing* category. Category creation is deferred
   until the CNPC API exposes it.

6. **Crafting recipe conflict**: Confirm the shapeless recipe (Book + Redstone + Advanced
   Computer) does not collide with an existing CC recipe before finalizing.

