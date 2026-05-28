# Adding a New Peripheral to ComputerCraft-Legacy

There are two distinct patterns for adding a peripheral, depending on whether you own the block or are wrapping an existing third-party tile entity.

| Pattern | When to use | Example |
|---|---|---|
| **Block peripheral** | You are adding a new ComputerCraft block that acts as a peripheral | Speaker, ChatBox, Modem |
| **Generic peripheral** | You want any tile entity that implements a Forge capability (e.g. `IFluidHandler`, `IInventory`, energy API) to automatically appear as a peripheral | `GenericFluidPeripheral`, `EnergyStoragePeripheral`, `InventoryPeripheral` |

---

## Pattern A — Block Peripheral

Use this when you are adding a new ComputerCraft block. Reference: `shared/peripheral/speaker/`.

### A1. Create Java Classes

- **Package:** `dan200.computercraft.shared.peripheral.mything`
- **Files:**
  - `BlockMyThing.java` — extends `BlockGeneric`, registers textures, creates the tile entity, handles direction.
  - `TileMyThing.java` — extends `TileGeneric`, implements `IPeripheralTile`, holds state, drives server-tick logic in `updateEntity()`.
  - `MyThingPeripheral.java` — implements `IPeripheral`, declares `getType()`, `getMethodNames()`, `callMethod()`, `attach()`, `detach()`, and `equals()`.
  - *(Optional)* Any helper/data classes needed by the peripheral.

### A2. Register the Peripheral Type

Add a new constant (e.g. `MyThing`) to `PeripheralType.java`.

### A3. Register the Block and Tile

In `ComputerCraft.java`, add a `public static BlockMyThing myThing;` field in the `Blocks` inner class.

In `ComputerCraftProxyCommon.java`:
- Instantiate the block.
- Register with `GameRegistry.registerBlock(...)`.
- Add a crafting recipe with `GameRegistry.addRecipe(...)`.
- Register the tile entity: `GameRegistry.registerTileEntity(TileMyThing.class, "ccmything")`.

### A4. Add Assets and Localization

- Place textures in `assets/computercraft/textures/blocks/`.
- Add `tile.computercraft:my_thing.name=My Thing` to `assets/computercraft/lang/en_US.lang`.

### A5. Lua ROM Additions (If Needed)

- Add a help file under `assets/computercraft/lua/rom/help/` and/or a Lua wrapper under `rom/apis/`.
- If new term-surface methods are added, update `rom/apis/window` as well.
- For simple peripherals, no Lua-side changes are needed.

### A6. Networking (Optional)

If the peripheral needs to send data to clients, add packet type constants and handling to `ComputerCraftPacket.java` and the client proxy.

---

## Pattern B — Generic (Capability-Based) Peripheral

Use this when you want **any** tile entity that implements a Forge capability interface to automatically appear as a peripheral — without owning the block. No `BlockGeneric` or `TileGeneric` subclasses are needed.

The entry point is [`DefaultPeripheralProvider`](../src/main/java/dan200/computercraft/shared/peripheral/common/DefaultPeripheralProvider.java). When a computer is placed next to an unknown tile entity, this provider checks for known capabilities and builds a peripheral from matching modules. Multiple matching modules are merged into a [`GenericCombinedPeripheral`](../src/main/java/dan200/computercraft/shared/peripheral/generic/GenericCombinedPeripheral.java).

### B1. Create the Peripheral Module Class

Create a class in `dan200.computercraft.shared.peripheral.generic` implementing `IPeripheral` that wraps the capability interface:

```java
public class MyCapabilityPeripheral implements IPeripheral {

    private static final String[] METHOD_NAMES = { "getMyValue", "setMyValue" };

    private final IMyCapability m_capability;

    public MyCapabilityPeripheral(IMyCapability capability) {
        this.m_capability = capability;
    }

    @Override public String getType() { return "my_capability"; }
    @Override public String[] getMethodNames() { return METHOD_NAMES; }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] args)
        throws LuaException, InterruptedException {
        switch (method) {
            case 0: return new Object[]{ (double) m_capability.getMyValue() };
            case 1: /* ... */ return null;
            default: return null;
        }
    }

    @Override public void attach(IComputerAccess computer) {}
    @Override public void detach(IComputerAccess computer) {}

    @Override
    public boolean equals(IPeripheral other) {
        // Use capability instance identity.
        if (other instanceof MyCapabilityPeripheral o) return o.m_capability == this.m_capability;
        return false;
    }
}
```

If your peripheral needs to participate in cross-peripheral transfers (like fluid push/pull), also implement a marker interface (e.g. `IFluidHandlerPeripheral`) so other modules can detect it via `instanceof`. See `IFluidHandlerPeripheral` for the pattern.

### B2. Add the Module to `DefaultPeripheralProvider`

Open [`DefaultPeripheralProvider.java`](../src/main/java/dan200/computercraft/shared/peripheral/common/DefaultPeripheralProvider.java) and add a check in the capability-scanning block:

```java
// Build the list of applicable generic modules. Add new capability modules here.
if (tile instanceof IInventory) {
    modules.add(new InventoryPeripheral(tile));
}
if (tile instanceof IFluidHandler) {
    modules.add(new GenericFluidPeripheral((IFluidHandler) tile, face));
}
if (tile instanceof IMyCapability) {                              // ← add this
    modules.add(new MyCapabilityPeripheral((IMyCapability) tile));
}
```

`GenericCombinedPeripheral.create(modules, tile)` is called automatically when more than one module matches, merging their method lists. No further changes to `GenericCombinedPeripheral` are needed.

### B3. Energy-API Adapters (Special Case)

For energy APIs that are not a direct `instanceof` check (e.g. CoFH RF, which varies by mod version), the provider uses a factory list instead. Implement `IEnergyAdapterFactory` and `IEnergyStorageAdapter`, then call `defaultPeripheralProvider.addEnergyFactory(myFactory)` before the provider is registered:

```java
// IEnergyAdapterFactory — return null if the tile does not support your energy API
public class MyEnergyFactory implements IEnergyAdapterFactory {
    @Override
    public IEnergyStorageAdapter tryAdapt(TileEntity tile) {
        if (tile instanceof IMyEnergyStorage) {
            return new MyEnergyAdapter((IMyEnergyStorage) tile);
        }
        return null;
    }
}
```

### B4. No Block Registration or Assets Required

Generic peripherals require no new blocks, tile entities, textures, or lang entries. The peripheral type string (returned by `getType()`) is the only identifier visible to Lua.

### B5. Lua-Side Considerations

- No ROM API wrapper is typically needed for generic peripherals — Lua programs call methods directly via `peripheral.wrap("left")`.
- If the peripheral type is user-facing, add a help file at `assets/computercraft/lua/rom/help/my_capability`.

---

## Choosing Between Patterns

| Question | Answer → Pattern |
|---|---|
| Do you own the block and tile entity? | **A** |
| Is the capability already implemented by many third-party blocks? | **B** |
| Do you need custom block rendering, NBT, or crafting recipes? | **A** |
| Does your peripheral need to appear automatically on any compatible block? | **B** |

---

## Further Considerations

- **Crafting recipe** (Pattern A only): decide on ingredients for the new block.
- **Lua API scope**: determine if ROM helpers or documentation are needed.
- **`equals()` contract**: for block peripherals use tile-entity identity; for generic peripherals use capability-object identity.
- **Testing**: add or modify only tests directly related to the new peripheral. Reference: `GenericFluidPeripheralTest`, `InventoryPeripheralTest`.

---

*Block peripheral reference: `shared/peripheral/speaker/`. Generic peripheral references: `GenericFluidPeripheral`, `EnergyStoragePeripheral`, `DefaultPeripheralProvider`.*
