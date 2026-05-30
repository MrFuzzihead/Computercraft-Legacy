# Plan: Player Sensor Peripheral — Quest & Faction Integration
A new **`player_sensor`** block peripheral that exposes quest and faction state for named
players, plus push events when quest/faction data changes globally. Pull methods are
on-demand; events are broadcast to all registered sensors (same pattern as ChatBox).
Fully gated on `AbstractNpcAPI.IsAvailable()`.
---
## Architecture
```
BlockPlayerSensor → TilePlayerSensor ←→ PlayerSensorManager (flat receiver set)
                         ↑                        ↑
               PlayerSensorPeripheral     PlayerSensorBridge (Forge @SubscribeEvent)
                  (IPeripheral)           IQuestEvent.* + IFactionEvent.FactionPoints
```
The bridge receives events globally (no UUID keying needed — events carry `getPlayer()`)
and fans out to all registered sensors identically to `ChatBoxManager`.
Lua scripts filter by `playerName` if needed.
---
## Lua Method Table
All methods throw `LuaException("CustomNPCs is not installed")` when CNPC is absent.
Player-specific methods additionally throw `LuaException("Player not found")` when the
name does not resolve to an online player.
All methods run on the main thread via `ILuaContext#executeMainThreadTask`.
### Quest Queries
| # | Method               | Args                                  | Returns            |
|---|----------------------|---------------------------------------|--------------------|
| 0 | `getActiveQuests`    | `playerName: string`                  | `QuestTable[]`     |
| 1 | `getFinishedQuests`  | `playerName: string`                  | `QuestTable[]`     |
| 2 | `hasActiveQuest`     | `playerName: string, questId: number` | `boolean`          |
| 3 | `hasFinishedQuest`   | `playerName: string, questId: number` | `boolean`          |
| 4 | `getQuestObjectives` | `playerName: string, questId: number` | `ObjectiveTable[]` |
| 5 | `getTrackedQuest`    | `playerName: string`                  | `QuestTable\|nil`  |
| 6 | `getTimeUntilRepeat` | `playerName: string, questId: number` | `number` (ms)      |
### Quest Control
| #  | Method        | Args                                  | Returns |
|----|---------------|---------------------------------------|---------|
| 7  | `startQuest`  | `playerName: string, questId: number` | —       |
| 8  | `stopQuest`   | `playerName: string, questId: number` | —       |
| 9  | `finishQuest` | `playerName: string, questId: number` | —       |
| 10 | `removeQuest` | `playerName: string, questId: number` | —       |
### Quest Registry (global, no player needed)
| #  | Method               | Args              | Returns                                               |
|----|----------------------|-------------------|-------------------------------------------------------|
| 11 | `getQuests`          | —                 | `QuestInfoTable[]` (all quests across all categories) |
| 12 | `getQuestCategories` | —                 | `CategoryTable[]`                                     |
| 13 | `getQuest`           | `questId: number` | `QuestInfoTable\|nil`                                 |
### Faction Queries
| #  | Method                | Args                                    | Returns                                    |
|----|-----------------------|-----------------------------------------|--------------------------------------------|
| 14 | `getFactionPoints`    | `playerName: string, factionId: number` | `number`                                   |
| 15 | `getFactionStatus`    | `playerName: string, factionId: number` | `number` (1=friendly, 0=neutral, -1=enemy) |
| 16 | `getAllFactionPoints` | `playerName: string`                    | `{["factionId"]=points, ...}`              |
### Faction Control
| #  | Method             | Args                                                    | Returns |
|----|--------------------|---------------------------------------------------------|---------|
| 17 | `setFactionPoints` | `playerName: string, factionId: number, points: number` | —       |
| 18 | `addFactionPoints` | `playerName: string, factionId: number, delta: number`  | —       |
### Faction Registry (global)
| #  | Method        | Args                | Returns             |
|----|---------------|---------------------|---------------------|
| 19 | `getFactions` | —                   | `FactionTable[]`    |
| 20 | `getFaction`  | `factionId: number` | `FactionTable\|nil` |
---
## Lua Table Schemas
### `QuestTable` (active/finished queries — player-contextual)
```lua
{
  id           = number,
  name         = string,
  type         = number,   -- 0=Item, 1=Dialog, 2=Kill, 3=Location, 4=AreaKill, 5=Manual
  categoryId   = number,
  categoryName = string,
  isRepeatable = boolean,
  repeatType   = number,   -- 0=None, 1=Instant, 2=Daily, 3=Weekly, 4=Custom
  logText      = string,
  completeText = string,
  npcName      = string,
}
```
### `ObjectiveTable`
```lua
{
  text           = string,
  additionalText = string|nil,
  progress       = number,
  maxProgress    = number,
  isCompleted    = boolean,
}
```
### `QuestInfoTable` (registry queries — no player-specific data)
```lua
{
  id           = number,
  name         = string,
  type         = number,
  categoryId   = number,
  categoryName = string,
  isRepeatable = boolean,
  repeatType   = number,
  logText      = string,
  npcName      = string,
}
```
### `CategoryTable`
```lua
{
  id         = number,
  name       = string,
  questCount = number,
}
```
### `FactionTable`
```lua
{
  id             = number,
  name           = string,
  defaultPoints  = number,   -- IFaction#getDefaultPoints()
  friendlyPoints = number,   -- IFaction#getFriendlyPoints()
  neutralPoints  = number,   -- IFaction#getNeutralPoints()
  isPassive      = boolean,
  isHidden       = boolean,
}
```
---
## CC Events Queued to Attached Computers
| Event             | Parameters                                                                                       |
|-------------------|--------------------------------------------------------------------------------------------------|
| `quest_started`   | `playerName: string, questId: number, questName: string`                                         |
| `quest_completed` | `playerName: string, questId: number, questName: string`                                         |
| `quest_turned_in` | `playerName: string, questId: number, questName: string`                                         |
| `faction_points`  | `playerName: string, factionId: number, factionName: string, points: number, decreased: boolean` |
> **Note:** `quest_completed` fires when all objectives are finished (`QuestCompletedEvent`).
> `quest_turned_in` fires when the player actually turns it in to the NPC (`QuestTurnedInEvent`).
> Both are distinct events in CustomNPCs.
---
## Implementation Steps
### Step 1 — `PlayerSensorManager` (new class)
**File:** `dan200.computercraft.shared.peripheral.playersensor.PlayerSensorManager`
Identical pattern to `ChatBoxManager` — synchronized flat set, fan-out on dispatch.
```java
package dan200.computercraft.shared.peripheral.playersensor;
import java.util.*;
public final class PlayerSensorManager {
    private static final Set<TilePlayerSensor> RECEIVERS =
        Collections.synchronizedSet(new HashSet<>());
    private PlayerSensorManager() {}
    public static void register(TilePlayerSensor tile)   { RECEIVERS.add(tile); }
    public static void unregister(TilePlayerSensor tile) { RECEIVERS.remove(tile); }
    public static void dispatchQuestStarted(String player, int questId, String questName) {
        dispatch("quest_started", player, questId, questName);
    }
    public static void dispatchQuestCompleted(String player, int questId, String questName) {
        dispatch("quest_completed", player, questId, questName);
    }
    public static void dispatchQuestTurnedIn(String player, int questId, String questName) {
        dispatch("quest_turned_in", player, questId, questName);
    }
    public static void dispatchFactionPoints(String player, int factionId,
                                             String factionName, int points, boolean decreased) {
        dispatch("faction_points", player, factionId, factionName, points, decreased);
    }
    private static void dispatch(String event, Object... params) {
        Set<TilePlayerSensor> snapshot;
        synchronized (RECEIVERS) { snapshot = new HashSet<>(RECEIVERS); }
        for (TilePlayerSensor tile : snapshot) tile.queueEvent(event, params);
    }
}
```
---
### Step 2 — `PlayerSensorBridge` (new class)
**File:** `dan200.computercraft.shared.peripheral.playersensor.PlayerSensorBridge`

> **Note:** Use concrete `noppes.npcs.scripted.event.player.QuestEvent.*` and
> `noppes.npcs.scripted.event.player.FactionEvent.*` classes (not `IQuestEvent.*` / `IFactionEvent.*`
> interfaces). FML's EventBus dispatches by concrete class hierarchy only.

```java
package dan200.computercraft.shared.peripheral.playersensor;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import noppes.npcs.scripted.event.player.FactionEvent;
import noppes.npcs.scripted.event.player.QuestEvent;
public class PlayerSensorBridge {
    @SubscribeEvent
    public void onQuestStarted(QuestEvent.QuestStartEvent e) {
        PlayerSensorManager.dispatchQuestStarted(
            e.getPlayer().getName(), e.getQuest().getId(), e.getQuest().getName());
    }
    @SubscribeEvent
    public void onQuestCompleted(QuestEvent.QuestCompletedEvent e) {
        PlayerSensorManager.dispatchQuestCompleted(
            e.getPlayer().getName(), e.getQuest().getId(), e.getQuest().getName());
    }
    @SubscribeEvent
    public void onQuestTurnedIn(QuestEvent.QuestTurnedInEvent e) {
        PlayerSensorManager.dispatchQuestTurnedIn(
            e.getPlayer().getName(), e.getQuest().getId(), e.getQuest().getName());
    }
    @SubscribeEvent
    public void onFactionPoints(FactionEvent.FactionPoints e) {
        PlayerSensorManager.dispatchFactionPoints(
            e.getPlayer().getName(),
            e.getFaction().getId(),
            e.getFaction().getName(),
            e.getPoints(),
            e.decreased()
        );
    }
}
```
---
### Step 3 — `TilePlayerSensor` (new tile entity)
**File:** `dan200.computercraft.shared.peripheral.playersensor.TilePlayerSensor`
No NBT state. Registers/unregisters with `PlayerSensorManager` on lifecycle events.
```java
public class TilePlayerSensor extends TileGeneric implements IPeripheralTile {
    private final Set<IComputerAccess> m_computers = new HashSet<>();
    @Override
    public PeripheralType getPeripheralType() { return PeripheralType.PlayerSensor; }
    @Override
    public IPeripheral getPeripheral(int side) { return new PlayerSensorPeripheral(this); }
    @Override public String getLabel()        { return null; }
    @Override public int getDirection()       { return 2; }
    @Override public void setDirection(int d) {}
    public synchronized void attachComputer(IComputerAccess c) { m_computers.add(c); }
    public synchronized void detachComputer(IComputerAccess c) { m_computers.remove(c); }
    public void queueEvent(String event, Object... params) {
        Set<IComputerAccess> snapshot;
        synchronized (this) { snapshot = new HashSet<>(m_computers); }
        for (IComputerAccess c : snapshot) c.queueEvent(event, params);
    }
    @Override public void validate()      { super.validate();   PlayerSensorManager.register(this); }
    @Override public void invalidate()    { PlayerSensorManager.unregister(this); super.invalidate(); }
    @Override public void onChunkUnload() { PlayerSensorManager.unregister(this); super.onChunkUnload(); }
    @Override
    public void getDroppedItems(java.util.List<ItemStack> drops, int fortune,
                                boolean creative, boolean silkTouch) {
        if (!creative) drops.add(new ItemStack(ComputerCraft.Blocks.playerSensor));
    }
    @Override
    public ItemStack getPickedItem() { return new ItemStack(ComputerCraft.Blocks.playerSensor); }
}
```
---
### Step 4 — `PlayerSensorPeripheral` key implementation patterns
**File:** `dan200.computercraft.shared.peripheral.playersensor.PlayerSensorPeripheral`
```java
// Helper: resolve online player or throw
private IPlayer<?> resolvePlayer(String name) throws LuaException {
    IPlayer<?> player = AbstractNpcAPI.Instance().getPlayer(name);
    if (player == null) throw new LuaException("Player not found: " + name);
    return player;
}
// method 0: getActiveQuests(playerName)
IQuest[] quests = resolvePlayer(name).getData().getQuestData().getActiveQuests();
return new Object[]{ buildQuestTableArray(quests) };
// method 4: getQuestObjectives(playerName, questId)
IPlayer<?> p = resolvePlayer(name);
IQuest quest = AbstractNpcAPI.Instance().getQuests().get(questId);
if (quest == null) throw new LuaException("Quest not found: " + questId);
return new Object[]{ buildObjectiveTableArray(quest.getObjectives(p)) };
// method 6: getTimeUntilRepeat(playerName, questId)
IPlayer<?> p = resolvePlayer(name);
IQuest quest = AbstractNpcAPI.Instance().getQuests().get(questId);
if (quest == null) throw new LuaException("Quest not found: " + questId);
return new Object[]{ (double) quest.getTimeUntilRepeat(p) };
// method 11: getQuests() — global, no player
List<Map<String,Object>> result = new ArrayList<>();
for (IQuestCategory cat : AbstractNpcAPI.Instance().getQuests().categories())
    for (IQuest q : cat.quests()) result.add(buildQuestInfoTable(q, cat));
return new Object[]{ result.toArray() };
// method 14: getFactionPoints(playerName, factionId)
int pts = resolvePlayer(name).getData().getFactionData().getPoints(factionId);
return new Object[]{ (double) pts };
// method 15: getFactionStatus(playerName, factionId)
IPlayer<?> p = resolvePlayer(name);
IFaction f = AbstractNpcAPI.Instance().getFactions().get(factionId);
if (f == null) throw new LuaException("Faction not found: " + factionId);
return new Object[]{ (double) f.playerStatus(p) };
// method 16: getAllFactionPoints(playerName)
IPlayer<?> p = resolvePlayer(name);
IPlayerFactionData fd = p.getData().getFactionData();
Map<String,Object> map = new LinkedHashMap<>();
for (IFaction f : AbstractNpcAPI.Instance().getFactions().list())
    map.put(String.valueOf(f.getId()), (double) fd.getPoints(f.getId()));
return new Object[]{ map };
// method 17: setFactionPoints(playerName, factionId, points)
resolvePlayer(name).setFactionPoints(factionId, points);  // IPlayer#setFactionPoints(int,int)
// method 18: addFactionPoints(playerName, factionId, delta)
resolvePlayer(name).addFactionPoints(factionId, delta);   // IPlayer#addFactionPoints(int,int)
```
Table builders:
```java
private Map<String, Object> buildQuestTable(IQuest q) {
    Map<String, Object> t = new LinkedHashMap<>();
    t.put("id",           (double) q.getId());
    t.put("name",         q.getName());
    t.put("type",         (double) q.getType());
    IQuestCategory cat = q.getCategory();
    t.put("categoryId",   cat != null ? (double) cat.getId() : -1.0);
    t.put("categoryName", cat != null ? cat.getName() : "");
    t.put("isRepeatable", q.getIsRepeatable());
    t.put("repeatType",   (double) q.getRepeatType());
    t.put("logText",      q.getLogText());
    t.put("completeText", q.getCompleteText());
    t.put("npcName",      q.getNpcName());
    return t;
}
private Map<String, Object> buildObjectiveTable(IQuestObjective obj) {
    Map<String, Object> t = new LinkedHashMap<>();
    t.put("text",           obj.getText());
    t.put("additionalText", obj.getAdditionalText());
    t.put("progress",       (double) obj.getProgress());
    t.put("maxProgress",    (double) obj.getMaxProgress());
    t.put("isCompleted",    obj.isCompleted());
    return t;
}
private Map<String, Object> buildFactionTable(IFaction f) {
    Map<String, Object> t = new LinkedHashMap<>();
    t.put("id",             (double) f.getId());
    t.put("name",           f.getName());
    t.put("defaultPoints",  (double) f.getDefaultPoints());
    t.put("friendlyPoints", (double) f.getFriendlyPoints());
    t.put("neutralPoints",  (double) f.getNeutralPoints());
    t.put("isPassive",      f.isPassive());
    t.put("isHidden",       f.getIsHidden());
    return t;
}
```
---
### Step 5 — `BlockPlayerSensor` (new block)
**File:** `dan200.computercraft.shared.peripheral.playersensor.BlockPlayerSensor`
Same structure as `BlockChatBox`. Two texture icons: `playerSensorTop`, `playerSensorSide`.
`setBlockName("computercraft:player_sensor")`, hardness 2.0, material iron.
`createTile(int metadata)` returns `new TilePlayerSensor()`.
---
### Step 6 — Wire into `ComputerCraft.java` and support files
**`PeripheralType.java`** — add `PlayerSensor` to the enum.
**`ComputerCraft.java` — `Blocks` inner class:**
```java
public static BlockPlayerSensor playerSensor;
```
**`ComputerCraft.java` — `preInit`:**
```java
Blocks.playerSensor = new BlockPlayerSensor();
GameRegistry.registerBlock(Blocks.playerSensor, "player_sensor");
GameRegistry.registerTileEntity(TilePlayerSensor.class, "computercraft:player_sensor");
```
**`ComputerCraft.java` — `init` CNPC block** (alongside existing bridges):
```java
if (AbstractNpcAPI.IsAvailable()) {
    AbstractNpcAPI.Instance().events().register(new CustomNpcChatBoxBridge());
    AbstractNpcAPI.Instance().events().register(new NpcInterfaceBridge());
    AbstractNpcAPI.Instance().events().register(new PlayerSensorBridge());  // NEW
    logger.info("[ComputerCraft] CustomNPCs detected — Player Sensor peripheral enabled.");
}
```
**Suggested crafting recipe:**
```
G G G
G C G   -> 1x player_sensor
G G G
C = Computer (basic), G = Gold Ingot
```
---
### Step 7 — Tests
**`PlayerSensorManagerTest.java`**
| Test | Assertion |
|------|-----------|
| Register + `dispatchQuestStarted` | Both registered tiles receive `quest_started` event with correct params |
| Unregister one tile + dispatch | Only remaining tile fires |
| `dispatchQuestCompleted` | Tiles receive `quest_completed` with correct params |
| `dispatchQuestTurnedIn` | Tiles receive `quest_turned_in` with correct params |
| `dispatchFactionPoints` | Tiles receive `faction_points` with all 5 params |
**`PlayerSensorPeripheralTest.java`**
| Test | Assertion |
|------|-----------|
| All 21 methods without CNPC | Each throws `LuaException("CustomNPCs is not installed")` |
| Methods 0-10, 14-18 with unknown player | Throws `LuaException("Player not found: ...")` |
| `buildQuestTable` | 10 keys present with correct types |
| `buildObjectiveTable` | 5 keys present with correct types |
| `buildFactionTable` | 7 keys present with correct types |
| `getAllFactionPoints` | Returns table keyed by `"<factionId>"` strings |
| `getFactionStatus` | Returns `1`, `0`, or `-1` per mock `IFaction#playerStatus` |
| `getQuestObjectives` with unknown questId | Throws `LuaException("Quest not found: ...")` |
| `getQuests` | Returns one entry per quest across all categories |
---
## Files Created / Changed
| File                                                  | Type                                |
|-------------------------------------------------------|-------------------------------------|
| `peripheral/playersensor/BlockPlayerSensor.java`      | New                                 |
| `peripheral/playersensor/TilePlayerSensor.java`       | New                                 |
| `peripheral/playersensor/PlayerSensorPeripheral.java` | New                                 |
| `peripheral/playersensor/PlayerSensorManager.java`    | New                                 |
| `peripheral/playersensor/PlayerSensorBridge.java`     | New                                 |
| `peripheral/PeripheralType.java`                      | Add `PlayerSensor`                  |
| `ComputerCraft.java`                                  | Register block, tile entity, bridge |
| `assets/computercraft/textures/playerSensor*.png`     | New (2 placeholder textures)        |
| `test/.../PlayerSensorManagerTest.java`               | New                                 |
| `test/.../PlayerSensorPeripheralTest.java`            | New                                 |
## Files NOT Changed
| File                          | Reason                          |
|-------------------------------|---------------------------------|
| `ChatBoxPeripheral.java`      | No overlap; separate peripheral |
| `NpcInterfacePeripheral.java` | No overlap                      |
| `NpcDetectorPeripheral.java`  | No overlap                      |
---
## Open Questions / Decisions Before Implementation
1. **Offline player handling**: `AbstractNpcAPI.Instance().getPlayer(name)` returns `null`
   for offline players. Control methods (7-10, 17-18) cannot run without the player object.
   Decide: throw `LuaException("Player is offline")` (informative) vs. generic
   `"Player not found"` for all null cases.
2. **`getAllFactionPoints` key type**: Lua table keys that are sequential integers behave like
   array indices. Using `String.valueOf(factionId)` as the key avoids sparse-array confusion
   but means Lua code must do `points[tostring(id)]`. Confirm preferred key format.
3. **`quest_completed` vs `quest_turned_in` distinction**: `QuestCompletedEvent` fires when
   all objectives are finished; `QuestTurnedInEvent` fires when the player turns it in to the
   NPC and receives rewards. A server with auto-complete quests may only fire one.
   Document this distinction clearly in any API reference.
4. **`getQuestObjectives` for unstarted quests**: `IQuest#getObjectives(player)` behavior
   when the player has not started the quest is unspecified by the API. Test and handle
   gracefully (return empty array or throw a descriptive error).
5. **Turtle upgrade**: A read-only turtle variant could follow the `TurtleNpcDetector` pattern
   (abstract `NpcDetectorPeripheral`-style base with position/world overrides). Push events
   would not be meaningful on a moving turtle (no fixed tile to register with
   `PlayerSensorManager`), so the turtle upgrade would be pull-only (methods 0-6, 11-16,
   19-20). Defer to a follow-up plan. In the meantime, a stationary turtle can access a placed
   `quest_faction_sensor` block as an external peripheral via a wired modem.
