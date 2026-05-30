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
Links to one or more CustomNPCs entities by **UUID** and exposes full read/write access to their properties. The peripheral can hold links to multiple NPCs simultaneously.

**Craft:** Iron border + normal Computer
**Lua API (`peripheral.wrap`):**

*Link management (always available, no CustomNPCs required unless noted):*
| Method | Returns | Description |
|---|---|---|
| `link(uuid)` | `true` | Link to the NPC with this exact UUID string. Resolves display name from loaded entities (best-effort). Always succeeds. |
| `linkByName(name [, radius])` | `boolean, string\|nil` | Link to the nearest NPC with that display name within `radius` (default 16). Returns `true, uuid` on success, `false` on failure. Requires CustomNPCs. |
| `linkAll(name [, radius])` | number | Link to **all** NPCs named `name` within `radius`. Replaces the current link set. Returns the count of newly linked NPCs. Requires CustomNPCs. |
| `linkNearest([radius])` | `boolean, string\|nil` | Link to the nearest NPC of any name within `radius` (default 16). Returns `true, uuid` on success, `false` on failure. Requires CustomNPCs. |
| `unlink([uuid])` | — | Remove a specific UUID link, or clear all links if no argument given. |
| `isLinked([uuid])` | boolean | `true` if any NPC is linked. If a UUID string is given, checks that specific UUID. |
| `getLinkedNpcs()` | table | `{[uuid] = name}` map of all currently linked NPCs. |
| `getLinkedUUID()` | string\|nil | The first persisted linked UUID string, or `nil` if unlinked. Works without CustomNPCs. |
| `scanNpcs([radius])` | table | `[{name, uuid, distance}]` sorted nearest-first. Does **not** change the link state. Requires CustomNPCs. |

*NPC state read (require CustomNPCs + live linked NPC):*

All state-read methods accept an optional trailing `uuid` string. If omitted the peripheral must be linked to **exactly one** NPC; if linked to multiple, a UUID argument is required to disambiguate.

| Method | Returns | Description |
|---|---|---|
| `getName([uuid])` | string | Display name |
| `getTitle([uuid])` | string | Title/subtitle |
| `getUUID([uuid])` | string | Unique ID string |
| `isAlive([uuid])` | boolean | `true` if the NPC is alive |
| `getHealth([uuid])` | number | Current HP |
| `getMaxHealth([uuid])` | number | Max HP |
| `getPosition([uuid])` | table | `{x, y, z}` coordinate table |
| `getMovingType([uuid])` | string | `"standing"`, `"wandering"`, or `"path"` |
| `isAttacking([uuid])` | boolean | `true` if attacking |
| `getTarget([uuid])` | string\|nil | Type name of attack target, or `nil` |
| `getFaction([uuid])` | table | `{name: string, id: number}` — `name` is `""` and `id` is `-1` if no faction |
| `getJob([uuid])` | string | Job type name: `"none"`, `"bard"`, `"healer"`, `"guard"`, `"follower"`, `"itemgiver"`, `"spawner"`, `"conversation"`, `"puppet"` |
| `getRole([uuid])` | string | Role type name: `"none"`, `"trader"`, `"follower"`, `"bank"`, `"transporter"`, `"postman"`, `"companion"` |

*NPC control (require CustomNPCs + live linked NPC):*

Control methods without a UUID broadcast to **all** linked NPCs; with a UUID, only that NPC is targeted.

| Method | Description |
|---|---|
| `say(message [, uuid])` | Make the NPC say a message |
| `setHome(x, y, z [, uuid])` | Set NPC home position |
| `setMovingType(type [, uuid])` | `"standing"`, `"wandering"`, or `"path"` |
| `navigateTo(x, y, z [, speed [, uuid]])` | Navigate to coordinates; `speed` defaults to `0.7` |
| `executeCommand(command [, uuid])` | Run a command as the NPC |
| `kill([uuid])` | Kill the NPC |
| `reset([uuid])` | Reset the NPC |

*NPC state write (require CustomNPCs + live linked NPC):*
| Method | Description |
|---|---|
| `setName(name [, uuid])` | Set display name |
| `setTitle(title [, uuid])` | Set title/subtitle |
| `setHealth(health [, uuid])` | Set current HP |
| `setMaxHealth(maxHealth [, uuid])` | Set max HP |
| `setFaction(factionId [, uuid])` | Set faction by numeric ID |
| `setJob(jobType [, uuid])` | Set job by name (`"bard"`, etc.) **or** numeric ordinal |
| `setRole(roleType [, uuid])` | Set role by name (`"trader"`, etc.) **or** numeric ordinal |

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

> **Note:** These `npc_*` events are fired only for the **linked** NPC.
> The Chat Box fires a separate set of `cnpc_*` events (see below) for **all** NPCs globally.
> A computer attached to both peripherals will receive both namespaces without duplicates.

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

## Chat Box — Global NPC Events (`cnpc_*`)

When a **Chat Box** block or portable Chat Box is attached to a computer and CustomNPCs is loaded,
the following global events are fired for **any** NPC in the world (not linked to a specific UUID).
They use the `cnpc_` prefix to distinguish them from the linked-NPC `npc_*` events fired by the NPC Interface.

| Event | Parameters | Description |
|---|---|---|
| `cnpc_interact` | `playerName, npcName` | A player right-clicked any NPC |
| `cnpc_dialog` | `playerName, npcName, dialogId, optionId` | A player chose a dialog option with any NPC |
| `cnpc_dialog_closed` | `playerName, npcName, dialogId, optionId` | A player closed a dialog with any NPC |
| `cnpc_died` | `npcName, killerName, damageType` | Any NPC was killed |
| `cnpc_spawned` | `npcName` | Any NPC spawned |
| `cnpc_damaged` | `npcName, attackerName, damage, damageType` | Any NPC took damage |
| `cnpc_killed_entity` | `npcName, entityName, entityType` | Any NPC killed an entity |

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
└── peripheral/
    ├── npcdetector/          BlockNpcDetector, TileNpcDetector, NpcDetectorPeripheral
    ├── npcinterface/         BlockNpcInterface, TileNpcInterface, NpcInterfacePeripheral, NpcInterfaceManager, INpcInterfaceHolder
    └── traderrole/           BlockNpcTrader, TileNpcTrader, NpcTraderPeripheral
dan200.computercraft.shared
├── turtle/upgrades/          TurtleNpcDetector, TurtleNpcInterface
└── pocket/peripherals/       PocketNpcDetectorPeripheral, PocketNpcInterfacePeripheral
```

