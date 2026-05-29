# Plan: NPC Detector Peripheral

A new **stateless, read-only block** peripheral (`npc_detector`) that scans nearby entities
on demand and returns structured Lua tables. No linking, no NBT state, no events — pure polling.
Uses `IWorld.getEntitiesNear(x, y, z, range)` to scan, filters for `ICustomNpc` and `IPlayer`
instances. Fully gated on `AbstractNpcAPI.IsAvailable()` (except `getMaxRange`).

---

## Architecture

```
BlockNpcDetector → TileNpcDetector → NpcDetectorPeripheral
                                           ↓
                               IWorld.getEntitiesNear() (main thread)
                               filter ICustomNpc / IPlayer
                               build Lua table arrays → sorted by distance
```

No manager class. No bridge. No event bus registration. Entirely pull-based.

---

## Lua Method Table

All methods except `getMaxRange` (method 9) throw
`LuaException("CustomNPCs is not installed")` when CNPC is absent.
All scan methods execute on the **main thread** via `ILuaContext#executeMainThreadTask`.
Radius is capped to `ComputerCraft.npc_detector_max_range` (default 64, config-driven).

| # | Method             | Args                                     | Returns                                             |
|---|--------------------|------------------------------------------|-----------------------------------------------------|
| 0 | `getNpcs`          | `[radius: number]`                       | `NpcTable[]` sorted by distance                     |
| 1 | `getNpcsByName`    | `name: string [, radius: number]`        | `NpcTable[]` sorted by distance                     |
| 2 | `getNpcsInFaction` | `factionName: string [, radius: number]` | `NpcTable[]` sorted by distance                     |
| 3 | `getNearestNpc`    | `[radius: number]`                       | `NpcTable\|nil`                                     |
| 4 | `countNpcs`        | `[radius: number]`                       | `number`                                            |
| 5 | `getPlayers`       | `[radius: number]`                       | `PlayerTable[]` sorted by distance                  |
| 6 | `getNearestPlayer` | `[radius: number]`                       | `PlayerTable\|nil`                                  |
| 7 | `countPlayers`     | `[radius: number]`                       | `number`                                            |
| 8 | `getEntities`      | `[radius: number]`                       | `EntityTable[]` (NPCs + players, with `type` field) |
| 9 | `getMaxRange`      | —                                        | `number` (config cap, works without CNPC)           |

---

## Lua Table Schemas

### `NpcTable`
```lua
{
  name        = string,     -- ICustomNpc#getName()
  title       = string,     -- ICustomNpc#getTitle()
  uuid        = string,     -- IEntity#getUniqueID()
  x           = number,     -- IEntity#getX()
  y           = number,     -- IEntity#getY()
  z           = number,     -- IEntity#getZ()
  distance    = number,     -- Euclidean distance from detector block
  health      = number,     -- IEntityLivingBase#getHealth()
  maxHealth   = number,     -- IEntityLivingBase#getMaxHealth()
  isAlive     = boolean,    -- !IEntity#isDead()
  isAttacking = boolean,    -- IEntityLivingBase#isAttacking()
  target      = string|nil, -- IEntityLivingBase#getAttackTarget()#getName() or nil
  movingType  = number,     -- ICustomNpc#getMovingType() (0=standing, 1=wandering, 2=path)
  factionName = string,     -- ICustomNpc#getFaction()#getName() or ""
  factionId   = number,     -- ICustomNpc#getFaction()#getId() or -1
  jobType     = number,     -- ICustomNpc#getJob()#getType() or -1
  roleType    = number,     -- ICustomNpc#getRole()#getType() or -1
}
```

### `PlayerTable`
```lua
{
  name      = string,
  uuid      = string,
  x         = number,
  y         = number,
  z         = number,
  distance  = number,
  health    = number,
  maxHealth = number,
  gameMode  = number,  -- IPlayer#getMode() (0=Survival, 1=Creative, 2=Adventure)
}
```

### `EntityTable` (from `getEntities`)
A `NpcTable` or `PlayerTable` with an extra field:
```lua
{ type = "npc",    ... }  -- all NpcTable fields
{ type = "player", ... }  -- all PlayerTable fields
```

---

## Implementation Steps

### Step 1 — Add config entry to `ComputerCraft.java`

```java
public static int npc_detector_max_range = 64;
```

In the config loading block (alongside existing peripheral settings):
```java
npc_detector_max_range = config.getInt(
    "npc_detector_max_range", "peripheral",
    64, 1, 256,
    "Maximum scan radius for the NPC Detector peripheral (blocks)"
);
```

---

### Step 2 — `BlockNpcDetector` (new block)

**File:** `dan200.computercraft.shared.peripheral.npcdetector.BlockNpcDetector`

Modeled after `BlockChatBox`. Two texture slots: `npcDetectorTop`, `npcDetectorSide`.
Hardness 2.0, material iron, creative tab main.

```java
// Mirrors BlockChatBox structure:
// - setBlockName("computercraft:npc_detector")
// - setHardness(2.0F), setCreativeTab(ComputerCraft.mainCreativeTab)
// - createTile(int metadata) → new TileNpcDetector()
// - Two icons: npcDetectorTop, npcDetectorSide
```

---

### Step 3 — `TileNpcDetector` (new tile entity)

**File:** `dan200.computercraft.shared.peripheral.npcdetector.TileNpcDetector`

Minimal — **no NBT state, no computer tracking** (no events to push). Purely a
`IPeripheralTile` factory and position holder.

```java
public class TileNpcDetector extends TileGeneric implements IPeripheralTile {

    @Override
    public PeripheralType getPeripheralType() { return PeripheralType.NpcDetector; }

    @Override
    public IPeripheral getPeripheral(int side) { return new NpcDetectorPeripheral(this); }

    @Override public String getLabel()        { return null; }
    @Override public int getDirection()       { return 2; }
    @Override public void setDirection(int d) {}

    @Override
    public void getDroppedItems(List<ItemStack> drops, int fortune,
                                boolean creative, boolean silkTouch) {
        if (!creative) drops.add(new ItemStack(ComputerCraft.Blocks.npcDetector));
    }

    @Override
    public ItemStack getPickedItem() {
        return new ItemStack(ComputerCraft.Blocks.npcDetector);
    }
}
```

---

### Step 4 — `NpcDetectorPeripheral` (new peripheral)

**File:** `dan200.computercraft.shared.peripheral.npcdetector.NpcDetectorPeripheral`

```java
public class NpcDetectorPeripheral implements IPeripheral {

    private static final String[] METHOD_NAMES = {
        "getNpcs",          // 0
        "getNpcsByName",    // 1
        "getNpcsInFaction", // 2
        "getNearestNpc",    // 3
        "countNpcs",        // 4
        "getPlayers",       // 5
        "getNearestPlayer", // 6
        "countPlayers",     // 7
        "getEntities",      // 8
        "getMaxRange"       // 9
    };

    private final TileNpcDetector m_tile;

    @Override public String getType()          { return "npc_detector"; }
    @Override public String[] getMethodNames() { return METHOD_NAMES; }
    @Override public void attach(IComputerAccess c) {}
    @Override public void detach(IComputerAccess c) {}
    @Override public boolean equals(IPeripheral o) {
        return o instanceof NpcDetectorPeripheral
            && ((NpcDetectorPeripheral) o).m_tile == m_tile;
    }

    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context,
                               int method, Object[] args)
        throws LuaException, InterruptedException {

        // getMaxRange works without CNPC
        if (method == 9) {
            return new Object[]{ (double) ComputerCraft.npc_detector_max_range };
        }

        requireCnpc();

        switch (method) {
            case 0: { // getNpcs([radius])
                double r = parseRadius(args, 0);
                return context.executeMainThreadTask(() ->
                    new Object[]{ scanNpcs(r, null, null) });
            }
            case 1: { // getNpcsByName(name [, radius])
                String name = requireString(args, 0, "name");
                double r = parseRadius(args, 1);
                return context.executeMainThreadTask(() ->
                    new Object[]{ scanNpcs(r, name, null) });
            }
            case 2: { // getNpcsInFaction(factionName [, radius])
                String faction = requireString(args, 0, "factionName");
                double r = parseRadius(args, 1);
                return context.executeMainThreadTask(() ->
                    new Object[]{ scanNpcs(r, null, faction) });
            }
            case 3: { // getNearestNpc([radius])
                double r = parseRadius(args, 0);
                return context.executeMainThreadTask(() -> {
                    Object[] all = scanNpcs(r, null, null);
                    return all.length == 0 ? new Object[]{ null } : new Object[]{ all[0] };
                });
            }
            case 4: { // countNpcs([radius])
                double r = parseRadius(args, 0);
                return context.executeMainThreadTask(() ->
                    new Object[]{ (double) scanNpcs(r, null, null).length });
            }
            case 5: { // getPlayers([radius])
                double r = parseRadius(args, 0);
                return context.executeMainThreadTask(() ->
                    new Object[]{ scanPlayers(r) });
            }
            case 6: { // getNearestPlayer([radius])
                double r = parseRadius(args, 0);
                return context.executeMainThreadTask(() -> {
                    Object[] all = scanPlayers(r);
                    return all.length == 0 ? new Object[]{ null } : new Object[]{ all[0] };
                });
            }
            case 7: { // countPlayers([radius])
                double r = parseRadius(args, 0);
                return context.executeMainThreadTask(() ->
                    new Object[]{ (double) scanPlayers(r).length });
            }
            case 8: { // getEntities([radius])
                double r = parseRadius(args, 0);
                return context.executeMainThreadTask(() ->
                    new Object[]{ scanAll(r) });
            }
            default: return null;
        }
    }

    // -------------------------------------------------------------------------
    // Scan helpers (all called on the main thread)
    // -------------------------------------------------------------------------

    private Object[] scanNpcs(double radius, String nameFilter, String factionFilter)
        throws LuaException {
        IWorld world = resolveWorld();
        IEntity<?>[] entities = world.getEntitiesNear(
            m_tile.xCoord + 0.5, m_tile.yCoord + 0.5, m_tile.zCoord + 0.5, radius);

        List<Map<String, Object>> result = new ArrayList<>();
        for (IEntity<?> e : entities) {
            if (!(e instanceof ICustomNpc)) continue;
            ICustomNpc<?> npc = (ICustomNpc<?>) e;
            if (nameFilter != null && !nameFilter.equals(npc.getName())) continue;
            if (factionFilter != null) {
                IFaction f = npc.getFaction();
                if (f == null || !factionFilter.equals(f.getName())) continue;
            }
            result.add(buildNpcTable(npc));
        }
        result.sort(Comparator.comparingDouble(t -> (Double) t.get("distance")));
        return result.toArray();
    }

    private Object[] scanPlayers(double radius) throws LuaException {
        IWorld world = resolveWorld();
        IEntity<?>[] entities = world.getEntitiesNear(
            m_tile.xCoord + 0.5, m_tile.yCoord + 0.5, m_tile.zCoord + 0.5, radius);
        List<Map<String, Object>> result = new ArrayList<>();
        for (IEntity<?> e : entities) {
            if (!(e instanceof IPlayer)) continue;
            result.add(buildPlayerTable((IPlayer<?>) e));
        }
        result.sort(Comparator.comparingDouble(t -> (Double) t.get("distance")));
        return result.toArray();
    }

    private Object[] scanAll(double radius) throws LuaException {
        IWorld world = resolveWorld();
        IEntity<?>[] entities = world.getEntitiesNear(
            m_tile.xCoord + 0.5, m_tile.yCoord + 0.5, m_tile.zCoord + 0.5, radius);
        List<Map<String, Object>> result = new ArrayList<>();
        for (IEntity<?> e : entities) {
            if (e instanceof ICustomNpc) {
                Map<String, Object> t = buildNpcTable((ICustomNpc<?>) e);
                t.put("type", "npc");
                result.add(t);
            } else if (e instanceof IPlayer) {
                Map<String, Object> t = buildPlayerTable((IPlayer<?>) e);
                t.put("type", "player");
                result.add(t);
            }
        }
        result.sort(Comparator.comparingDouble(t -> (Double) t.get("distance")));
        return result.toArray();
    }

    // -------------------------------------------------------------------------
    // Table builders
    // -------------------------------------------------------------------------

    private Map<String, Object> buildNpcTable(ICustomNpc<?> npc) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name",        npc.getName());
        t.put("title",       npc.getTitle());
        t.put("uuid",        npc.getUniqueID());
        t.put("x",           npc.getX());
        t.put("y",           npc.getY());
        t.put("z",           npc.getZ());
        t.put("distance",    distanceTo(npc));
        t.put("health",      (double) npc.getHealth());
        t.put("maxHealth",   npc.getMaxHealth());
        t.put("isAlive",     !npc.isDead());
        t.put("isAttacking", npc.isAttacking());
        IEntityLivingBase<?> tgt = npc.getAttackTarget();
        t.put("target",      tgt != null ? tgt.getName() : null);
        t.put("movingType",  npc.getMovingType());
        IFaction f = npc.getFaction();
        t.put("factionName", f != null ? f.getName() : "");
        t.put("factionId",   f != null ? (double) f.getId() : -1.0);
        IJob job = npc.getJob();
        t.put("jobType",     job != null ? (double) job.getType() : -1.0);
        IRole role = npc.getRole();
        t.put("roleType",    role != null ? (double) role.getType() : -1.0);
        return t;
    }

    private Map<String, Object> buildPlayerTable(IPlayer<?> player) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name",      player.getName());
        t.put("uuid",      player.getUniqueID());
        t.put("x",         player.getX());
        t.put("y",         player.getY());
        t.put("z",         player.getZ());
        t.put("distance",  distanceTo(player));
        t.put("health",    (double) player.getHealth());
        t.put("maxHealth", player.getMaxHealth());
        t.put("gameMode",  (double) player.getMode());
        return t;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private IWorld resolveWorld() throws LuaException {
        AbstractNpcAPI api = AbstractNpcAPI.Instance();
        if (api == null) throw new LuaException("CustomNPCs API unavailable");
        IWorld world = api.getIWorld(m_tile.getWorldObj());
        if (world == null) throw new LuaException("World not available");
        return world;
    }

    private double distanceTo(IEntity<?> e) {
        double dx = e.getX() - (m_tile.xCoord + 0.5);
        double dy = e.getY() - (m_tile.yCoord + 0.5);
        double dz = e.getZ() - (m_tile.zCoord + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double parseRadius(Object[] args, int i) throws LuaException {
        double max = ComputerCraft.npc_detector_max_range;
        if (args.length <= i || args[i] == null) return max;
        if (!(args[i] instanceof Number))
            throw new LuaException("Expected number for radius");
        return Math.min(((Number) args[i]).doubleValue(), max);
    }

    private void requireCnpc() throws LuaException {
        if (!AbstractNpcAPI.IsAvailable())
            throw new LuaException("CustomNPCs is not installed");
    }

    private static String requireString(Object[] args, int i, String name)
        throws LuaException {
        if (args.length <= i || !(args[i] instanceof String))
            throw new LuaException("Expected string for " + name);
        return (String) args[i];
    }
}
```

---

### Step 5 — Wire into `ComputerCraft.java` and support files

**`PeripheralType.java`** — add `NpcDetector` to the enum.

**`ComputerCraft.java` — `Blocks` inner class:**
```java
public static BlockNpcDetector npcDetector;
```

**`ComputerCraft.java` — `preInit`:**
```java
Blocks.npcDetector = new BlockNpcDetector();
GameRegistry.registerBlock(Blocks.npcDetector, "npc_detector");
GameRegistry.registerTileEntity(TileNpcDetector.class, "computercraft:npc_detector");
```

**`ComputerCraft.java` — config block:**
```java
npc_detector_max_range = config.getInt(
    "npc_detector_max_range", "peripheral",
    64, 1, 256,
    "Maximum scan radius for the NPC Detector peripheral (blocks)"
);
```

> **No bridge or manager registration needed.** Unlike the ChatBox and NPC Interface
> peripherals, the detector is pull-only and requires no Forge event listener.

**Suggested crafting recipe:**
```
S S S
E C E   → 1× npc_detector
S S S

C = Computer (basic), E = Ender Pearl, S = Stone
```

---

### Step 6 — Tests

**File:** `src/test/java/dan200/computercraft/shared/peripheral/npcdetector/NpcDetectorPeripheralTest.java`

| Test                       | Assertion                                                         |
|----------------------------|-------------------------------------------------------------------|
| `getMaxRange` without CNPC | Returns configured value — no exception                           |
| Methods 0–8 without CNPC   | Each throws `LuaException("CustomNPCs is not installed")`         |
| `parseRadius` above cap    | Clamped to `npc_detector_max_range`                               |
| `parseRadius` below cap    | Used as-is                                                        |
| `parseRadius` absent       | Defaults to `npc_detector_max_range`                              |
| `buildNpcTable`            | All 17 keys present with correct Java types                       |
| `buildPlayerTable`         | All 9 keys present with correct Java types                        |
| `scanNpcs` name filter     | Excludes NPCs whose `getName()` doesn't match                     |
| `scanNpcs` faction filter  | Excludes NPCs in other factions; null-faction NPC excluded        |
| `getNpcs` result order     | Sorted ascending by `distance` field                              |
| `getNearestNpc` no match   | Returns `nil` / `null`                                            |
| `getNearestNpc` one match  | Returns that NPC's table                                          |
| `countNpcs`                | Returns count equal to `getNpcs(radius).length`                   |
| `getEntities` type field   | NPC entries have `type = "npc"`, player entries `type = "player"` |

---

## Files Created / Changed

| File                                                | Type                                      |
|-----------------------------------------------------|-------------------------------------------|
| `peripheral/npcdetector/BlockNpcDetector.java`      | New                                       |
| `peripheral/npcdetector/TileNpcDetector.java`       | New                                       |
| `peripheral/npcdetector/NpcDetectorPeripheral.java` | New (abstract; `forTile()` factory)       |
| `peripheral/PeripheralType.java`                    | Add `NpcDetector`                         |
| `turtle/upgrades/TurtleNpcDetector.java`            | New (upgrade ID 11; gated on customnpcs)  |
| `pocket/peripherals/PocketNpcDetectorPeripheral.java` | New (upgrade=5; gated on customnpcs)    |
| `pocket/recipes/PocketComputerNpcDetectorUpgradeRecipe.java` | New                            |
| `pocket/items/ItemPocketComputer.java`              | Add upgrade=5, getHasNpcDetector, onUpdate, getSubItems (gated) |
| `pocket/items/PocketComputerItemFactory.java`       | Add createWithNpcDetector                 |
| `ComputerCraft.java`                                | Register block, tile entity, config field; add Upgrades.npcDetector |
| `proxy/ComputerCraftProxyCommon.java`               | Block/recipe/pocket recipe (all gated on customnpcs mod ID) |
| `proxy/CCTurtleProxyCommon.java`                    | Register TurtleNpcDetector (gated on customnpcs) |
| `assets/computercraft/textures/npcDetector*.png`    | New (2 placeholder textures)              |
| `test/.../NpcDetectorPeripheralTest.java`           | New                                       |

## Files NOT Changed

| File                                 | Reason                                    |
|--------------------------------------|-------------------------------------------|
| `NpcInterfaceManager.java`           | No shared state needed                    |
| `NpcInterfaceBridge.java`            | No events; no bridge needed               |
| `ChatBoxPeripheral.java`             | Separate peripheral; no overlap           |
| `ComputerCraft.java` CNPC init block | No bridge to register; config change only |

---

## Open Questions / Decisions Before Implementation

1. **`getMaxRange()` without CNPC**: Method 9 is the sole method that works without CNPC
   installed — it reads a CC config value. Confirm this is desirable (lets Lua detect the
   peripheral's capability before calling into CNPC) vs. also throwing.

2. **Result size cap**: A radius of 64 in a busy world could return hundreds of entities.
   Consider a hard cap on list size (e.g., 128 entries, configurable) with a warning logged,
   or document that small radii are recommended.

3. **`getNpcsInFaction` by ID**: Currently filters by faction name string. Adding an overload
   `getNpcsInFaction(factionId: number [, radius])` would be more robust — names can change,
   IDs are stable. Could be method 10 or replace/augment method 2.

4. ~~**Turtle upgrade**~~: **Implemented.** `TurtleNpcDetector` (upgrade ID 11) extends the
   abstract `NpcDetectorPeripheral`, sourcing position and world from `ITurtleAccess`.
   Only registered when mod ID `customnpcs` is loaded. Crafting item is the
   `npc_detector` block.

5. ~~**Pocket computer upgrade**~~: **Implemented.** `PocketNpcDetectorPeripheral` extends
   the abstract `NpcDetectorPeripheral`, with `setLocation(World, x, y, z)` called each
   tick by `ItemPocketComputer#onUpdate`. Upgrade value `upgrade=5` in NBT.
   Recipe and `ImpostorRecipe` entries are registered gated on mod ID `customnpcs`.
   Creative tab shows the item only when `customnpcs` is loaded.

6. **Dimension filtering**: `getEntitiesNear` uses the tile's own world object, so it will
   only ever see NPCs in the same dimension as the block — correct and expected, but worth
   documenting in any API reference.

