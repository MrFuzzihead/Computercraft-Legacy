# Plan: Implement `inventory` Generic Peripheral

> Tracks implementation of the CC:Tweaked
> [`inventory`](https://tweaked.cc/generic_peripheral/inventory.html) generic peripheral module
> for this 1.7.10 Forge codebase.
>
> Upstream reference:
> [`AbstractInventoryMethods.java`](https://github.com/cc-tweaked/CC-Tweaked/blob/db32ddfec5e8c2bdefb3232b471328a3e92cc43f/projects/common/src/main/java/dan200/computercraft/shared/peripheral/generic/methods/AbstractInventoryMethods.java)

---

## Overview

Wrap any `IInventory` tile entity as a new `"inventory"` peripheral type exposing the six
CC:Tweaked methods (`size`, `list`, `getItemDetail`, `getItemLimit`, `pushItems`, `pullItems`).
The cross-inventory transfer methods (`pushItems`/`pullItems`) need access to the wired-modem
cable network's peripheral name map, so `IComputerAccess` gains a `getAvailablePeripherals()`
default method, backed by `TileCable.RemotePeripheralWrapper`.

No reflection, no mixins, no access transformers — all changes are direct source edits.

---

## Files

| Action | File | Notes |
|--------|------|-------|
| **CREATE** | `src/main/java/dan200/computercraft/shared/peripheral/inventory/InventoryPeripheral.java` | New peripheral class |
| **MODIFY** | `src/main/java/dan200/computercraft/api/peripheral/IComputerAccess.java` | Add `getAvailablePeripherals()` default method |
| **MODIFY** | `src/main/java/dan200/computercraft/shared/peripheral/modem/TileCable.java` | Wire `getAvailablePeripherals()` in `RemotePeripheralWrapper` |
| **MODIFY** | `src/main/java/dan200/computercraft/shared/peripheral/common/DefaultPeripheralProvider.java` | Auto-wrap `IInventory` tiles |
| **CREATE** | `src/test/java/dan200/computercraft/shared/peripheral/inventory/InventoryPeripheralTest.java` | Unit tests |
| **UPDATE** | `TWEAKEDCC_COVERAGE.md` | Mark as ✅ Done |

---

## Step-by-Step

### Step 1 — `InventoryPeripheral.java` (new file)

**Package**: `dan200.computercraft.shared.peripheral.inventory`

**Implements**: `IPeripheralTargeted` (extends `IPeripheral` + `getTarget()`)

**Constructor**: `InventoryPeripheral(TileEntity tile)` — stores the `TileEntity` (which also
implements `IInventory`), plus its `worldObj`, `xCoord`, `yCoord`, `zCoord`.

**`getType()`**: returns `"inventory"`.

**`getTarget()`**: returns the stored `TileEntity` cast to `IInventory`.

**`getMethodNames()`**: returns the 6 names at the fixed indices below.

| Index | Lua name | Upstream link |
|-------|----------|---------------|
| 0 | `size` | [§ size](https://tweaked.cc/generic_peripheral/inventory.html#v:size) |
| 1 | `list` | [§ list](https://tweaked.cc/generic_peripheral/inventory.html#v:list) |
| 2 | `getItemDetail` | [§ getItemDetail](https://tweaked.cc/generic_peripheral/inventory.html#v:getItemDetail) |
| 3 | `getItemLimit` | [§ getItemLimit](https://tweaked.cc/generic_peripheral/inventory.html#v:getItemLimit) |
| 4 | `pushItems` | [§ pushItems](https://tweaked.cc/generic_peripheral/inventory.html#v:pushItems) |
| 5 | `pullItems` | [§ pullItems](https://tweaked.cc/generic_peripheral/inventory.html#v:pullItems) |

**`callMethod(computer, context, method, args)`**: all six cases use
`context.executeMainThreadTask(...)` so inventory reads/writes always occur on the server thread.

#### Case 0 — `size()`
```
IInventory inv = InventoryUtil.getInventory(worldObj, x, y, z, -1);
return new Object[]{ inv.getSizeInventory() };
```

#### Case 1 — `list()`
```
IInventory inv = InventoryUtil.getInventory(worldObj, x, y, z, -1);
Map<Object,Object> result = new HashMap<>();
for (int i = 0; i < inv.getSizeInventory(); i++) {
    ItemStack stack = inv.getStackInSlot(i);
    if (stack != null) {
        result.put(i + 1, makeBasicDetail(stack));  // {name, count, damage}
    }
}
return new Object[]{ result };
```

#### Case 2 — `getItemDetail(slot)` (1-indexed from Lua)
```
validate slot in [1, getSizeInventory()];
ItemStack stack = inv.getStackInSlot(slot - 1);
if (stack == null) return new Object[]{ null };
return new Object[]{ makeFullDetail(stack) };
// makeFullDetail: name, count, damage, maxCount, displayName
```

**Item detail fields** (match TurtleAPI pattern already in codebase):

| Field | Source |
|-------|--------|
| `name` | `Item.itemRegistry.getNameForObject(stack.getItem())` |
| `count` | `stack.stackSize` |
| `damage` | `stack.getItemDamage()` |
| `maxCount` | `stack.getMaxStackSize()` |
| `displayName` | `stack.getDisplayName()` |

#### Case 3 — `getItemLimit(slot)` (1-indexed from Lua)
```
validate slot in [1, getSizeInventory()];
return new Object[]{ inv.getInventoryStackLimit() };
```

Matches upstream which simply returns `getInventoryStackLimit()` regardless of what is in the slot.

#### Case 4 — `pushItems(toName, fromSlot [, limit [, toSlot]])`
```
String toName  = args[0] (String, required)
int fromSlot   = args[1] (Number, required, 1-indexed)
int limit      = args[2] (Number, optional, default = fromStack.stackSize)
int toSlot     = args[3] (Number, optional, 1-indexed, -1 = any)

IInventory from = InventoryUtil.getInventory(worldObj, x, y, z, -1);
IPeripheral toPeriph = computer.getAvailablePeripherals().get(toName);
→ if null or not IPeripheralTargeted → throw LuaException("Target '" + toName + "' does not exist")
Object target = ((IPeripheralTargeted) toPeriph).getTarget();
→ if not IInventory → throw LuaException("Target '" + toName + "' is not an inventory")
IInventory to = (IInventory) target;

int moved = moveItems(from, fromSlot - 1, to, toSlot - 1 (or -1), effectiveLimit);
return new Object[]{ moved };
```

#### Case 5 — `pullItems(fromName, fromSlot [, limit [, toSlot]])`
Symmetric to `pushItems`: find `fromName` peripheral, get its `IInventory`, call
`moveItems(from, fromSlot-1, thisInv, toSlot-1, limit)`.

#### Private helper — `moveItems(from, fromSlot, to, toSlot, limit)`
```
ItemStack source = from.getStackInSlot(fromSlot);
if (source == null || source.stackSize == 0) return 0;

int toMove = Math.min(limit, source.stackSize);
ItemStack moving = source.copy();
moving.stackSize = toMove;

int moved;
if (toSlot >= 0) {
    // Place into a specific destination slot
    ItemStack dest = to.getStackInSlot(toSlot);
    if (dest == null) {
        int space = to.getInventoryStackLimit();
        int actual = Math.min(toMove, space);
        moving.stackSize = actual;
        to.setInventorySlotContents(toSlot, moving);
        to.markDirty();
        moved = actual;
    } else if (InventoryUtil.areItemsStackable(dest, moving)) {
        int space = Math.min(dest.getMaxStackSize(), to.getInventoryStackLimit()) - dest.stackSize;
        int actual = Math.min(toMove, space);
        dest.stackSize += actual;
        to.setInventorySlotContents(toSlot, dest);
        to.markDirty();
        moved = actual;
    } else {
        moved = 0;
    }
} else {
    // Store into any available slot
    ItemStack remainder = InventoryUtil.storeItems(moving, to, 0, to.getSizeInventory(), 0);
    moved = toMove - (remainder != null ? remainder.stackSize : 0);
}

// Deduct from source
if (moved > 0) {
    source.stackSize -= moved;
    if (source.stackSize == 0) from.setInventorySlotContents(fromSlot, null);
    else from.setInventorySlotContents(fromSlot, source);
    from.markDirty();
}
return moved;
```

**`attach()` / `detach()`**: no-ops (no resources to acquire/release).

**`equals(IPeripheral other)`**: true if `other` is an `InventoryPeripheral` targeting the same
tile entity reference.

---

### Step 2 — `IComputerAccess.java` (modify)

Add a default method so existing `IComputerAccess` implementations do not need changes:

```java
import dan200.computercraft.api.peripheral.IPeripheral;
import java.util.Collections;
import java.util.Map;

// inside IComputerAccess:
default Map<String, IPeripheral> getAvailablePeripherals() {
    return Collections.emptyMap();
}
```

This keeps full backward compatibility with any custom implementations.

---

### Step 3 — `TileCable.RemotePeripheralWrapper` (modify)

`RemotePeripheralWrapper` is a `private static` inner class of `TileCable`. Being a static inner
class, it already has compiler-level access to `TileCable`'s private fields via a reference.

**Changes**:

1. Add field: `private TileCable m_entity;`
2. Change constructor from 3 args to 4: `RemotePeripheralWrapper(IPeripheral, IComputerAccess, String, TileCable)`.
3. Update the `attachPeripheral` call-site (inside `TileCable`) to pass `TileCable.this` as the 4th arg.
4. Override `getAvailablePeripherals()`:

```java
@Override
public Map<String, IPeripheral> getAvailablePeripherals() {
    synchronized (m_entity.m_peripheralsByName) {
        return Collections.unmodifiableMap(new HashMap<>(m_entity.m_peripheralsByName));
    }
}
```

The `synchronized` block and defensive copy prevent TOCTOU races if the cable network changes
while the inventory transfer is running.

---

### Step 4 — `DefaultPeripheralProvider.java` (modify)

Add an `IInventory` check **after** the existing `IPeripheralTile` and `TileComputerBase` checks:

```java
import net.minecraft.inventory.IInventory;
import dan200.computercraft.shared.peripheral.inventory.InventoryPeripheral;

// At the bottom of getPeripheral(), before `return null`:
if (tile instanceof IInventory) {
    return new InventoryPeripheral(tile);
}
```

**Why this ordering is safe**:
- `TilePrinter` implements both `IPeripheralTile` and `IInventory` — the `IPeripheralTile` guard
  fires first and returns a `PrinterPeripheral`, so printers are unaffected.
- `TileComputerBase` / `TileTurtle` are checked before `IInventory`, so computers and turtles
  are unaffected.
- Only tile entities with no existing peripheral that happen to implement `IInventory`
  (chests, furnaces, dispensers, droppers, hoppers, brewing stands, etc.) reach the new check.

---

### Step 5 — `InventoryPeripheralTest.java` (new file)

**Package**: `dan200.computercraft.shared.peripheral.inventory`

Tests that can run without a live Minecraft environment (no `Item.itemRegistry` calls):

| Test | Verifies |
|------|----------|
| `peripheralTypeIsInventory` | `getType()` == `"inventory"` |
| `methodCountIsSix` | `getMethodNames().length` == 6 |
| `sizeIsAtIndex0` | method name at index 0 is `"size"` |
| `listIsAtIndex1` | method name at index 1 is `"list"` |
| `getItemDetailIsAtIndex2` | method name at index 2 is `"getItemDetail"` |
| `getItemLimitIsAtIndex3` | method name at index 3 is `"getItemLimit"` |
| `pushItemsIsAtIndex4` | method name at index 4 is `"pushItems"` |
| `pullItemsIsAtIndex5` | method name at index 5 is `"pullItems"` |
| `sizeReturnsInventorySize` | stub 9-slot `IInventory` → `size()` returns `9` |
| `listReturnsEmptyTableForEmptyInventory` | all-null slots → `list()` returns empty map |
| `listSkipsEmptySlots` | slot 1 and 3 occupied, slot 2 null → result has keys 1, 3 only |
| `getItemDetailReturnsNullForEmptySlot` | null stack → `getItemDetail(1)` returns `null` |
| `getItemDetailThrowsForSlotZero` | slot 0 (below 1-indexed minimum) → `LuaException` |
| `getItemDetailThrowsForSlotBeyondSize` | slot > `getSizeInventory()` → `LuaException` |
| `getItemLimitThrowsForSlotZero` | slot 0 → `LuaException` |
| `getItemLimitThrowsForSlotBeyondSize` | slot > size → `LuaException` |
| `getItemLimitReturnsStackLimit` | stub limit 64 → `getItemLimit(1)` returns `64` |
| `pushItemsThrowsForMissingTarget` | `getAvailablePeripherals()` empty → `LuaException "does not exist"` |
| `pullItemsThrowsForMissingTarget` | same |
| `pushItemsReturnsZeroForEmptySourceSlot` | source slot empty → returns 0 |
| `pullItemsReturnsZeroForEmptySourceSlot` | source slot empty → returns 0 |

Tests for `moveItems` logic (successful transfers between two stub inventories), item detail
fields, and double-chest merging require `Item.itemRegistry` (live Minecraft) and are covered
by the in-game test script `run/saves/Test World/computer/37/test_inventory.lua`.

---

### Step 6 — `TWEAKEDCC_COVERAGE.md` (update)

- Add a new `### 21. inventory generic peripheral — ✅ Done` section.
- Add `inventory` peripheral row to the **Currently Implemented** table.
- Mark as ✅ Done in the **Prioritized Implementation Roadmap** table.

---

## Key Deviations from Upstream (Confirm or Override Before Implementing)

| # | Deviation | Reason | Action needed |
|---|-----------|--------|---------------|
| 1 | **Double-chest merging via `InventoryUtil.getInventory`**: `InventoryPeripheral` stores world + coordinates and resolves the live (possibly merged) `IInventory` on every main-thread task. Each half of a double chest resolves to the same 54-slot view. | Upstream handles this via Forge `IItemHandler` capabilities; we use `InventoryUtil` instead. | ✅ No objection expected — confirm if each half should appear as two 27-slot inventories instead. |
| 2 | **`IComputerAccess.getAvailablePeripherals()` added as a `default` method**: this is a non-breaking extension to the public API interface. | Upstream `IComputerAccess` (CC:Tweaked) carries this method; ours does not. | ✅ No reflection involved — confirm before adding to the API. |
| 3 | **`getItemDetail` minimal field set** (`name`, `count`, `damage`, `maxCount`, `displayName`): no `tags` table (1.7.10 has no tag system). | 1.7.10 predates the Minecraft tag system. | ✅ Acceptable; no action needed. |
| 4 | **No `ISidedInventory` side-check in `moveItems`**: items are inserted/extracted through any face (`side = -1`). Upstream CC:Tweaked also does not enforce sided access for inventory peripherals. | Consistent with upstream. | ✅ No action needed. |

---

## Testing Checklist (In-Game Script)

Create `run/saves/Test World/computer/37/test_inventory.lua`:

```lua
-- Requires a chest connected to a wired modem at the computer.
-- chest should contain: slot 1 = 5 dirt, slot 3 = 1 diamond

local inv = peripheral.find("inventory")
assert(inv, "no inventory peripheral found")

-- size
assert(type(inv.size()) == "number")

-- list
local items = inv.list()
assert(items[1] ~= nil, "slot 1 should be occupied")
assert(items[2] == nil, "slot 2 should be empty")
assert(items[3] ~= nil, "slot 3 should be occupied")

-- getItemDetail
local detail = inv.getItemDetail(1)
assert(detail.name == "minecraft:dirt")
assert(detail.count == 5)
assert(detail.damage == 0)
assert(detail.maxCount == 64)
assert(type(detail.displayName) == "string")

-- getItemLimit
assert(inv.getItemLimit(1) == 64)

-- error paths
local ok, err = pcall(inv.getItemDetail, 0)
assert(not ok)
local ok2, err2 = pcall(inv.getItemDetail, inv.size() + 1)
assert(not ok2)

-- pushItems / pullItems require a second inventory peripheral named "inventory_2"
-- (tested manually in-game)

print("test_inventory: all assertions passed")
```

---

## Complexity & Effort

| Dimension | Assessment |
|-----------|------------|
| Size | Medium — ~1 new class (~200 lines), 3 small modifications, 1 new test class (~150 lines) |
| Risk | Low — changes are additive; existing peripherals are unaffected by ordering in `DefaultPeripheralProvider` |
| Dependencies | No new external libraries; uses existing `InventoryUtil`, `IPeripheralTargeted`, `IComputerAccess` |

