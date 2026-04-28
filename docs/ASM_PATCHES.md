# ASM Patch Tracking

This document tracks all ASM patches applied by the CCTweaks-Lua mod. For each patch, the source class that performs the patching and the classes affected are listed.

**Status Legend:**
- 🟢 Green — Patch is already implemented; code in the target is essentially equal to what the patch would produce.
- 🟡 Yellow — Patch is partially implemented; the target has related logic but does not fully match the patch's intention.
- 🔴 Red — Patch (or its intention) is not implemented at all in the target.

**Mixin Status Legend (second emoji):**
- ⬜ White — Mixin fully replaces patch logic; code is essentially identical.
- 🔵 Blue — Mixin implements some of the patch, or implements it with noticeably different code.
- 🟣 Purple — No mixin addresses this patch.

## CCTweaks Patchers

Registered and applied by `org.squiddev.cctweaks.core.asm.ASMTransformer` (the main FML class transformer).

| Status | Source Class                                                                 | Target(s)                                                                                              | Patch Type / Notes                                                                                      |
|--------|------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| 🔴 ⬜ | `org.squiddev.cctweaks.core.patch.TurtleRefuelCommand_Rewrite`               | `dan200.computercraft.shared.turtle.core.TurtleRefuelCommand`                                          | Replace (complete rewrite) — disabled-action check is present, but `ITurtleFuelRegistry`-based refuel logic is not implemented |
| 🟢 🟣 | `org.squiddev.cctweaks.core.patch.BlockPeripheral_Patch`                     | `dan200.computercraft.shared.peripheral.common.BlockPeripheral`                                        | Merge/Extend (adds monitor light emission) — `getLightValue` is implemented but reads from `ComputerCraft` fields rather than `Config.Misc` |
| 🟢 🟣 | `org.squiddev.cctweaks.core.patch.TurtleBrain_Patch`                         | `dan200.computercraft.shared.turtle.core.TurtleBrain`                                                  | Merge (adds upgrade changed handler) — `setUpgrade` already calls `IExtendedTurtleUpgrade.upgradeChanged` |
| 🟢 🟣 | `org.squiddev.cctweaks.core.asm.DisableTurtleCommand`                        | `dan200.computercraft.shared.turtle.core.Turtle*Command`                                               | ASM patch (disables turtle commands dynamically) — checks are present in each command via `ComputerCraft.turtleDisabledActions` array, but use a different data structure (`String[]` vs `Set`) and are hand-coded per-file rather than injected |
| 🟢 🟣 | `org.squiddev.cctweaks.core.asm.PatchTurtleRenderer`                         | `dan200.computercraft.client.render.TileEntityTurtleRenderer`                                          | Merge + ASM (ClassMerger, adds upside-down rendering for "Dinnerbone"/"Grumm" names) — not implemented |
| 🟢 🟣 | `org.squiddev.cctweaks.core.asm.TurtleBrainAlsoPeripheral`                   | `dan200.computercraft.shared.turtle.core.TurtleBrain`,<br>`dan200.computercraft.shared.turtle.blocks.TileTurtle` | ASM patch (allows upgrade to be both peripheral and tool) — `IExtendedTurtleUpgrade.alsoPeripheral()` check is already present in `TurtleBrain.updatePeripherals` |
| 🔴 ⬜ | `org.squiddev.cctweaks.core.patch.BlockCable_Patch`                          | `dan200.computercraft.shared.peripheral.common.BlockCable`                                             | Merge/Static patch (`isCable` method override) — `isCable` still uses original block-type logic, not `NetworkAPI.registry().isNode()` |
| 🔴 ⬜ | `org.squiddev.cctweaks.core.patch.TileCable_Patch`                           | `dan200.computercraft.shared.peripheral.modem.TileCable`                                               | Merge/Rename (`TileCable$Packet` inner class renamed to `org.squiddev.cctweaks.api.network.Packet`) — `TileCable` is the original; inner `Packet` class is not renamed and no `IWorldNetworkNodeHost` added |
| 🔴 ⬜ | `org.squiddev.cctweaks.core.patch.ItemCable_Patch`                           | `dan200.computercraft.shared.peripheral.common.ItemCable`                                              | Merge (multipart integration — **only applied when Forge Multipart is loaded**) — no multipart integration present |
| 🔴 ⬜ | `org.squiddev.cctweaks.core.patch.CableBlockRenderingHandler_Patch`          | `dan200.computercraft.client.proxy.ComputerCraftProxyClient$CableBlockRenderingHandler`                | Merge (network-aware cable rendering) — renderer still uses `BlockCable.isCable()` rather than `NetworkAPI.helpers().canConnect()` |
| 🔴 ⬜ | `org.squiddev.cctweaks.core.patch.PeripheralAPI_Patch`                       | `dan200.computercraft.core.apis.PeripheralAPI`                                                         | Merge (networked peripheral support) — `INetworkedPeripheral` attach/detach hooks not present |
| 🟢 🟣 | `org.squiddev.cctweaks.core.patch.targeted.ComputerPeripheral_Patch`         | `dan200.computercraft.shared.computer.blocks.ComputerPeripheral`                                       | Merge (adds `IPeripheralTargeted` support) — `ComputerPeripheral` already implements `IPeripheralTargeted` with `getTarget()` |
| 🟢 🟣 | `org.squiddev.cctweaks.core.patch.targeted.DiskDrivePeripheral_Patch`        | `dan200.computercraft.shared.peripheral.diskdrive.DiskDrivePeripheral`                                 | Merge (adds `IPeripheralTargeted` support) — already implemented |
| 🟢 🟣 | `org.squiddev.cctweaks.core.patch.targeted.PrinterPeripheral_Patch`          | `dan200.computercraft.shared.peripheral.printer.PrinterPeripheral`                                     | Merge (adds `IPeripheralTargeted` support) — already implemented |

## Lua Patchers

Registered and applied by `org.squiddev.cctweaks.lua.asm.Tweaks` (via `org.squiddev.cctweaks.lua.launch.RewritingLoader`).

| Status | Source Class                                                                 | Affected Class(es) / Target(s)                                                                         | Description / Notes                                                                                     |
|--------|------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| 🟢 🟣 | `org.squiddev.cctweaks.lua.patch.HTTPAPI_Patch`                              | `dan200.computercraft.core.apis.HTTPAPI`<br>`dan200.computercraft.core.apis.HTTPRequest` (renamed)     | Merge — patches HTTP API, renames `HTTPRequest` to custom implementation; our HTTPAPI has its own HTTPRequest and extended features (websockets, `checkURL`) but not the CCTweaks `fetch` alias or renamed `HTTPRequest` |
| 🟢 🟣 | `org.squiddev.cctweaks.lua.patch.PeripheralAPI_Patch`                        | `dan200.computercraft.core.apis.PeripheralAPI`                                                         | Merge — adds `ILuaObjectWithArguments` support — already implemented |
| 🟢 🟣 | `org.squiddev.cctweaks.lua.patch.binfs.FileSystem_Patch`                     | `dan200.computercraft.core.filesystem.FileSystem`<br>`dan200.computercraft.api.lua.LuaException` (renamed)<br>`dan200.computercraft.core.filesystem.IMountedFileNormal` (renamed) | Merge — patches FileSystem for binary file support — `IMountedFileBinary` and binary modes exist, but class renaming and exact factory overrides are not applied via ASM |
| 🟢 🟣 | `org.squiddev.cctweaks.lua.patch.binfs.ReaderObject`                         | `org.squiddev.cctweaks.lua.patch.binfs.ReaderObject` (self-merge)                                     | ClassMerger self-merge — binary file reader — implemented at `dan200.computercraft.core.lua.binfs.ReaderObject` |
| 🟢 🟣 | `org.squiddev.cctweaks.lua.patch.binfs.WriterObject`                         | `org.squiddev.cctweaks.lua.patch.binfs.WriterObject` (self-merge)                                     | ClassMerger self-merge — binary file writer — implemented at `dan200.computercraft.core.lua.binfs.WriterObject` |
| 🟢 🟣 | `org.squiddev.cctweaks.lua.asm.CustomAPIs`                                   | `dan200.computercraft.core.computer.Computer`                                                          | ASM — injects custom APIs into `createAPIs` method — `LuaEnvironment.inject(this)` is called in `Computer.createAPIs()` |
| 🟢 🟣 | `org.squiddev.cctweaks.lua.asm.CustomTimeout`                                | `dan200.computercraft.core.computer.ComputerThread$1`                                                  | ASM — replaces hardcoded 7000ms timeout with configurable value — `ComputerThread` already uses `ComputerCraft.computerThreadTimeout` |
| 🟢 🟣 | `org.squiddev.cctweaks.lua.asm.CustomMachine`                                | `dan200.computercraft.core.computer.Computer`                                                          | ASM — replaces hardcoded `LuaJLuaMachine` construction with `LuaHelpers.createMachine` — already uses `LuaHelpers.createMachine(this)` |
| 🟢 🟣 | `org.squiddev.cctweaks.lua.asm.CustomBios`                                   | `dan200.computercraft.core.computer.Computer`                                                          | ASM — replaces hardcoded bios path with configurable `Config.Computer.biosPath` — `Computer.initLua()` already reads from `ComputerCraft.biosPath` |
| 🟢 🟣 | `org.squiddev.cctweaks.lua.asm.binary.BinaryFS`                              | `dan200.computercraft.core.apis.FSAPI`,<br>`dan200.computercraft.core.filesystem.IMountedFileNormal`   | ASM — overrides buffered reader/writer factories; extends `IMountedFileNormal` with binary methods — binary file modes (`rb`/`wb`) are implemented via `IMountedFileBinary` and `ReaderObject`/`WriterObject`, but factory overriding is done directly in source rather than via ASM |

## OpenPeripherals Patchers

| Status | Source Class                                                                 | Target(s)                                                                                              | Patch Type / Notes                                                                                      |
|--------|------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| 🔴 ⬜ | `org.squiddev.cctweaks.core.patch.op.PeripheralProxy_Patch`                  | `openperipheral.addons.peripheralproxy.WrappedPeripheral`                                              | Merge — adds `INetworkedPeripheral` support — N/A; OpenPeripherals is not part of this codebase |
| 🔴 ⬜ | `org.squiddev.cctweaks.core.asm.PatchOpenPeripheralAdapter`                  | `openperipheral.interfaces.cc.wrappers.AdapterPeripheral`                                              | Merge + ASM — extends environment with network access — N/A; OpenPeripherals is not part of this codebase |
| 🔴 ⬜ | `org.squiddev.cctweaks.core.asm.PatchOpenModule`                             | `openperipheral.interfaces.cc.ModuleComputerCraft`                                                     | ASM — registers `INetworkAccess` environment in ComputerCraft module — N/A; OpenPeripherals is not part of this codebase |

## Loader and Chain

- **`org.squiddev.cctweaks.core.asm.ASMTransformer`** — FML `IClassTransformer` that orchestrates all CCTweaks and Lua patches at runtime.
- **`org.squiddev.cctweaks.lua.launch.RewritingLoader`** — standalone class loader used in the CCEmu Redux launcher path; applies Lua patches via `CustomChain`.
- **`org.squiddev.cctweaks.lua.asm.CustomChain`** — extends `TransformationChain` to first attempt bytecode replacement from `/patch/` directory before falling back to the registered patcher chain.
