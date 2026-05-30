# CustomNPCs Integration

Adds three new ComputerCraft peripherals and two turtle/pocket upgrades that interface with [CustomNPC+](https://www.curseforge.com/minecraft/mc-mods/custom-npcs) NPCs via Lua.

All features are fully gated on `isModLoaded("customnpcs")` and degrade gracefully if the mod is absent.

---

## Blocks

### NPC Detector (`npc_detector`)
Detects nearby CustomNPCs entities within a configurable radius.

**Craft:** Stone border + normal Computer
**Lua API (`peripheral.wrap`):**
| Method | Description |
|---|---|
| `getNpcsInRange()` | Returns a list of NPCs within range (name, UUID, distance) |
| `setRange(n)` | Set detection radius (1–64) |
| `getRange()` | Get current radius |

**Events:** Fires `npc_enter` / `npc_leave` when NPCs enter or exit range.

---

### NPC Interface (`npc_interface`)
Links to a specific NPC by UUID and exposes full read/write access to its properties.

**Craft:** Iron border + normal Computer
**Lua API (`peripheral.wrap`):**

*Link management:*
| Method | Description |
|---|---|
| `link(uuid)` | Link to an NPC by UUID |
| `linkNearest()` | Link to the nearest NPC |
| `unlink()` | Remove link |
| `getLinkedName()` | Get linked NPC's name |
| `getLinkedUUID()` | Get linked NPC's UUID |

*NPC properties (require CNPC + live linked NPC):*
| Method | Description |
|---|---|
| `getName()` / `setName(s)` | Display name |
| `getTitle()` / `setTitle(s)` | Title/subtitle |
| `getHealth()` / `setHealth(n)` | Current HP |
| `getMaxHealth()` / `setMaxHealth(n)` | Max HP |
| `getFaction()` / `setFaction(id)` | Faction ID |
| `getJob()` / `setJob(id)` | Job type ID |
| `getRole()` / `setRole(id)` | Role type ID |
| `getPosition()` | `{x, y, z}` |
| `getHomePosition()` | Configured home `{x, y, z}` |
| `isDead()` | Boolean |

**Events:** Fires `npc_interact`, `npc_death`, `npc_spawn` for the linked NPC.

---

### NPC Trader (`npc_trader`)
Links to an NPC with a **Trader** role (role type 1) and exposes full shop management.

**Craft:** Gold border + normal Computer
**Lua API (`peripheral.wrap`):**

*Link management:* same 5 methods as NPC Interface.

*Trade slot management (require CNPC + live linked Trader NPC):*
| Method | Description |
|---|---|
| `getSellOption(slot)` | Get item/price info for a trade slot (0–17) |
| `setSellOption(slot, item, meta, count, price)` | Set a trade slot |
| `removeSellOption(slot)` | Clear a trade slot |
| `isSlotEnabled(slot)` | Check if slot is active |
| `enableSlot(slot)` | Enable a slot |
| `disableSlot(slot)` | Disable a slot |
| `getPurchaseCount(slot)` | Times slot has been purchased |
| `resetPurchaseCount(slot)` | Reset purchase counter |
| `getMarket()` / `setMarket(name)` | Market/shop name |
| `getCurrency()` | Currency item info |

---

## Turtle Upgrades

### NPC Detector Turtle (`upgrade ID 11`)
Equip as a turtle side upgrade. Exposes the same `getNpcsInRange()` / `setRange()` API, centred on the turtle's current position.

**Craft:** NPC Detector block + turtle
**Localization:** "Sensing Turtle" / "Sensing Advanced Turtle"

### NPC Interface Turtle (`upgrade ID 12`)
Links to an NPC and persists the UUID in the turtle's upgrade NBT (survives reloads). Exposes the full NPC Interface API from the turtle's position.

**Craft:** NPC Interface block + turtle
**Localization:** "Linked Turtle" / "Linked Advanced Turtle"

---

## Pocket Computer Upgrades

### NPC Detector Pocket (`upgrade 5`)
Same detection API as the block, centred on the player holding the pocket computer.

**Craft:** NPC Detector block + Pocket Computer

### NPC Interface Pocket (`upgrade 6`)
Links to an NPC; UUID stored in the item's NBT tag. Exposes the full NPC Interface API from the player's position.

**Craft:** NPC Interface block + Pocket Computer
**Localization:** "Linked Pocket Computer" / "Linked Advanced Pocket Computer"

---

## Package Layout

```
dan200.computercraft.compat.customnpcs
├── peripheral/
│   ├── npcdetector/          Block, Tile, Peripheral
│   ├── npcinterface/         Block, Tile, Peripheral, Manager, INpcInterfaceHolder
│   └── traderrole/           Block, Tile, Peripheral
dan200.computercraft.shared
├── peripheral/npcdetector/   BlockNpcDetector, TileNpcDetector
├── turtle/upgrades/          TurtleNpcDetector, TurtleNpcInterface
└── pocket/peripherals/       PocketNpcDetectorPeripheral, PocketNpcInterfacePeripheral
```

