# CustomNPCs Integration

Adds three new ComputerCraft peripherals and two turtle/pocket upgrades that interface with [CustomNPC+](https://www.curseforge.com/minecraft/mc-mods/custom-npcs) NPCs via Lua.

All features are fully gated on `isModLoaded("customnpcs")` and degrade gracefully if the mod is absent.

---

## Blocks

### NPC Detector (`npc_detector`)
Detects nearby CustomNPCs entities within a configurable radius. This peripheral is **purely poll-based** — no events are fired.

**Craft:** Stone border + normal Computer
**Lua API (`peripheral.wrap`):**

All scan methods accept an optional `radius` argument (number, capped to `getMaxRange()`). Results are sorted by distance ascending.

*NPC scanning:*
| Method | Returns | Description |
|---|---|---|
| `getNpcs([radius])` | table | All NPCs in range. Each entry: `name, title, uuid, x, y, z, distance, health, maxHealth, isAlive, isAttacking, target, movingType, factionName, factionId, jobType, roleType` |
| `getNpcsByName(name [, radius])` | table | NPCs whose `name` matches exactly |
| `getNpcsInFaction(factionName [, radius])` | table | NPCs whose faction name matches exactly |
| `getNearestNpc([radius])` | table\|nil | Single closest NPC entry, or `nil` |
| `countNpcs([radius])` | number | Count of NPCs in range |

*Player scanning:*
| Method | Returns | Description |
|---|---|---|
| `getPlayers([radius])` | table | All players in range. Each entry: `name, uuid, x, y, z, distance, health, maxHealth, gameMode` |
| `getNearestPlayer([radius])` | table\|nil | Single closest player entry, or `nil` |
| `countPlayers([radius])` | number | Count of players in range |

*Mixed scanning:*
| Method | Returns | Description |
|---|---|---|
| `getEntities([radius])` | table | All NPCs and players in range; each entry includes a `type` field (`"npc"` or `"player"`) |

*Configuration:*
| Method | Returns | Description |
|---|---|---|
| `getMaxRange()` | number | Server-configured maximum scan radius; works even if CustomNPCs is absent |

**Events:** None — use polling.

---

### NPC Interface (`npc_interface`)
Links to a specific NPC by **name** and exposes full read/write access to its properties.

**Craft:** Iron border + normal Computer
**Lua API (`peripheral.wrap`):**

*Link management (always available, no CustomNPCs required):*
| Method | Returns | Description |
|---|---|---|
| `link(npcName [, radius])` | boolean | Link to the nearest NPC with that exact name within `radius` (default 16). Returns `true` on success |
| `linkNearest([radius])` | boolean | Link to the nearest NPC of any name within `radius` (default 16). Returns `true` on success |
| `unlink()` | — | Remove link |
| `isLinked()` | boolean | `true` if currently linked |
| `getLinkedName()` | string\|nil | Display name of the linked NPC, or `nil` |

*NPC state read (require CustomNPCs + live linked NPC):*
| Method | Returns | Description |
|---|---|---|
| `getName()` | string | Display name |
| `getTitle()` | string | Title/subtitle |
| `getUUID()` | string | Unique ID string |
| `isAlive()` | boolean | `true` if the NPC is alive |
| `getHealth()` | number | Current HP |
| `getMaxHealth()` | number | Max HP |
| `getPosition()` | table | `{x, y, z}` |
| `getMovingType()` | number | 0 = standing, 1 = wandering, 2 = path |
| `isAttacking()` | boolean | `true` if attacking |
| `getTarget()` | string\|nil | Type name of attack target, or `nil` |
| `getFaction()` | string | Faction name, or `""` if none |
| `getJob()` | number | Job type ID, or `-1` if none |
| `getRole()` | number | Role type ID, or `-1` if none |

*NPC control (require CustomNPCs + live linked NPC):*
| Method | Description |
|---|---|
| `say(message)` | Make the NPC say a message |
| `setHome(x, y, z)` | Set NPC home position |
| `setMovingType(type)` | `"standing"`, `"wandering"`, or `"path"` |
| `navigateTo(x, y, z [, speed])` | Navigate to coordinates; `speed` defaults to `0.7` |
| `executeCommand(command)` | Run a command as the NPC |
| `kill()` | Kill the NPC |
| `reset()` | Reset the NPC |

*NPC state write (require CustomNPCs + live linked NPC):*
| Method | Description |
|---|---|
| `setName(name)` | Set display name |
| `setTitle(title)` | Set title/subtitle |
| `setHealth(health)` | Set current HP |
| `setMaxHealth(maxHealth)` | Set max HP |
| `setFaction(factionId)` | Set faction by numeric ID |
| `setJob(jobType)` | Set job type by numeric ID |
| `setRole(roleType)` | Set role type by numeric ID |

**Events** (queued to all computers attached to a peripheral linked to that NPC's UUID):
| Event | Parameters | Description |
|---|---|---|
| `npc_interact` | `playerName` | Player right-clicked the NPC |
| `npc_dialog` | `playerName, dialogId, optionId` | Player chose a dialog option |
| `npc_dialog_closed` | `playerName, dialogId, optionId` | Player closed a dialog |
| `npc_damaged` | `sourceName, damage, damageType` | NPC took damage |
| `npc_died` | `killerName, damageType` | NPC was killed |
| `npc_target` | `targetName` | NPC acquired an attack target |
| `npc_target_lost` | — | NPC lost its attack target |
| `npc_tick` | — | Fired every NPC AI tick |

---

### NPC Trader (`npc_trader`)
Links to an NPC with a **Trader** role (role type 1) and exposes full shop management. This peripheral is **purely poll-based** — CustomNPCs fires no Forge events for trades.

**Craft:** Gold border + normal Computer
**Lua API (`peripheral.wrap`):**

*Link management (always available, no CustomNPCs required):*
| Method | Returns | Description |
|---|---|---|
| `link(npcName [, radius])` | boolean | Link to the nearest NPC with that exact name within `radius` (default 16). Returns `true` on success |
| `linkNearest([radius])` | boolean | Link to the nearest NPC of any name within `radius` (default 16). Returns `true` on success |
| `unlink()` | — | Remove link |
| `isLinked()` | boolean | `true` if currently linked |
| `getLinkedName()` | string\|nil | Display name of the linked NPC, or `nil` |

*Trade slot operations (require CustomNPCs + live linked Trader NPC):*

Slots are **0-based** and range from **0–17**.

| Method | Returns | Description |
|---|---|---|
| `getSellOption(slot)` | table\|nil | Item being sold: `{name, displayName, stackSize, damage}`, or `nil` if empty |
| `getCurrency(slot)` | table, table | Two currency item tables (primary, secondary) for the given slot; either may be `nil` |
| `setSellOption(slot, soldName, soldCount, cur1Name, cur1Count [, cur2Name, cur2Count])` | — | Set a trade slot. Item names are Minecraft registry IDs (e.g. `"minecraft:diamond"`) |
| `removeSellOption(slot)` | — | Clear a trade slot |
| `isSlotEnabled(slot [, playerName])` | boolean | `true` if the slot is active (optionally per-player) |
| `enableSlot(slot [, playerName])` | — | Enable a slot (optionally per-player) |
| `disableSlot(slot [, playerName])` | — | Disable a slot (optionally per-player) |
| `getPurchaseNum(slot [, playerName])` | number | How many times the slot has been purchased (optionally per-player) |
| `resetPurchaseNum([slot [, playerName]])` | — | Reset purchase counter: all slots if `slot` omitted, one slot if provided, per-player if `playerName` provided |
| `getMarket()` | string | Market/shop name |
| `setMarket(name)` | — | Set market/shop name |

**Events:** None — use polling.

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

