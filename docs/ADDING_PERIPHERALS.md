# Plan: Add a New Block Peripheral to ComputerCraft-Legacy

This plan outlines the steps to add a new block peripheral (e.g., "MyThing") to ComputerCraft-Legacy, using the Speaker peripheral as a reference. It covers all code, asset, and Lua-side changes required.

---

## 1. Create Java Classes

- **Package:** `dan200.computercraft.shared.peripheral.mything`
- **Files:**
  - `BlockMyThing.java`: Extends `BlockGeneric`, registers textures, creates the tile entity, handles direction.
  - `TileMyThing.java`: Extends `TileGeneric`, implements `IPeripheralTile`, holds state, drives server-tick logic in `updateEntity()`.
  - `MyThingPeripheral.java`: Implements `IPeripheral`, declares `getType()`, `getMethodNames()`, `callMethod()`, `attach()`, `detach()`, and `equals()`.
  - *(Optional)* Any helper/data classes needed by the peripheral.

## 2. Register the Peripheral Type

- Add a new constant (e.g., `MyThing`) to `PeripheralType.java`.

## 3. Register the Block and Tile

- In `ComputerCraft.java`, add a `public static BlockMyThing myThing;` field in the `Blocks` inner class, plus any config properties.
- In `ComputerCraftProxyCommon.java`:
  - Instantiate the block.
  - Register with `GameRegistry.registerBlock(...)`.
  - Add a crafting recipe with `GameRegistry.addRecipe(...)`.
  - Register the tile entity: `GameRegistry.registerTileEntity(TileMyThing.class, "ccmything")`.

## 4. Add Assets and Localization

- Place `myThingTop.png`, `myThingSide.png`, `myThingFront.png` in `assets/computercraft/textures/blocks/`.
- Add `tile.computercraft:my_thing.name=My Thing` to `assets/computercraft/lang/en_US.lang`.

## 5. Lua ROM Additions (If Needed)

- If the peripheral exposes complex functionality, add a help file under `assets/computercraft/lua/rom/help/` and/or a Lua wrapper under `rom/apis/`.
- If new term-surface methods are added, update `rom/apis/window` as well.
- For simple peripherals, no Lua-side changes are needed.

## 6. Networking (Optional)

- If the peripheral needs to send data to clients, add packet type constants and handling to `ComputerCraftPacket.java` and the client proxy.

---

## Further Considerations

- **Crafting Recipe:** Decide on ingredients for the new block.
- **Lua API Scope:** Determine if ROM helpers or documentation are needed.
- **Testing:** Add or modify only tests directly related to the new peripheral.

---

*Reference: Speaker peripheral implementation in `shared/peripheral/speaker/`.*

